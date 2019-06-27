package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.ProgressTracker
import net.corda.serialization.internal.byteArrayOutput
import java.util.*

/**
 * Flow that facilitates bidding on a auction. The party that intiates this flow is considered to be
 * the bidding party. The seller cannot bid on their own auction.
 * @param auctionId The unique id the auction to bid on.
 * @param amount The amount (and currency) to bid.
 * @return The signed bid transaction.
 */
@InitiatingFlow
@StartableByRPC
class AuctionBidFlow(val auctionId: UniqueIdentifier, val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val auctionStates = serviceHub.vaultService.queryBy(AuctionState::class.java)
        val auctionStateAndRef = requireNotNull(auctionStates.states.find { it.state.data.linearId == auctionId })
        val state = auctionStateAndRef.state.data

        if (ourIdentity == state.seller) {
            throw IllegalArgumentException("The seller cannot bid on their own auction")
        }

        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
                .withItems(
                    auctionStateAndRef,
                    StateAndContract(state.bid(amount, ourIdentity), AUCTION_CONTRACT_ID),
                    Command(AuctionContract.Commands.Bid(), state.participants.map { it.owningKey } + ourIdentity.owningKey),
                    TimeWindow.between(serviceHub.clock.instant(), state.expiry)
                )
        builder.verify(serviceHub)

        val everyoneElse = state.participants - ourIdentity
        val flowSessions = everyoneElse.map { initiateFlow(it) }
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, flowSessions))
        return subFlow(FinalityFlow(stx, flowSessions)).also {
            val broadcastToParties =
                    serviceHub.networkMapCache.allNodes.map { node -> node.legalIdentities.first() } - state.participants - ourIdentity
            subFlow(BroadcastTransactionFlow(it, broadcastToParties))
        }
    }
}

@InitiatedBy(AuctionBidFlow::class)
class AuctionBidFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val input = stx.tx.outputs.single().data
                "This must be a single AuctionState input" using (input is AuctionState)
                val output = stx.tx.outputs.single().data
                "This must be a single AuctionState output" using (output is AuctionState)
                // TODO: Add additional checks here
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}