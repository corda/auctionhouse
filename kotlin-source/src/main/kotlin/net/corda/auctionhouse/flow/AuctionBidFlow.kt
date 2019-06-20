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
import java.time.Instant
import java.util.*

@InitiatingFlow
@StartableByRPC
class AuctionBidFlow(val auctionId: UniqueIdentifier, val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val auctionStates = serviceHub.vaultService.queryBy(AuctionState::class.java)
        val auctionStateAndRef = requireNotNull(auctionStates.states.find { it.state.data.linearId == auctionId })
        val state = auctionStateAndRef.state.data

        if (ourIdentity == state.seller) {
            throw IllegalArgumentException("The seller cannot bid on an auction")
        }

        val builder = TransactionBuilder(notary = auctionStateAndRef.state.notary)
                .withItems(
                    auctionStateAndRef,
                    StateAndContract(state.bid(amount, ourIdentity), AUCTION_CONTRACT_ID),
                    Command(AuctionContract.Commands.Bid(), state.participants.map { it.owningKey } + ourIdentity.owningKey),
                    TimeWindow.fromOnly(Instant.now())
                )
        builder.verify(serviceHub)

        val flowSessions = state.participants.map { initiateFlow(it) }
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, flowSessions))
        return subFlow(FinalityFlow(stx, flowSessions)).also {
            // sends to everyone in the network
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
                val output = stx.tx.outputs.single().data
                "This must be an AuctionState transaction" using (output is AuctionState)
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}