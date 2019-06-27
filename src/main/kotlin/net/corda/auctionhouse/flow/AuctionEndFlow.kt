package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.ProgressTracker
import java.lang.IllegalArgumentException

/**
 * Flow that ends an Auction. Only the seller can initiate this flow.
 * @param auctionId The unique id the auction to end.
 * @return A message describing the outcome of the auction settlement.
 */
@InitiatingFlow
@StartableByRPC
class AuctionEndFlow(val auctionId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val auctionStates = serviceHub.vaultService.queryBy(AuctionState::class.java)
        val input = requireNotNull(auctionStates.states.find { it.state.data.linearId == auctionId })
        val state = input.state.data

        if (state.seller != ourIdentity) {
            throw IllegalArgumentException("Only the seller can end this auction")
        }

        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val auctionItems = serviceHub.vaultService.queryBy(AuctionItemState::class.java)
        val itemStateAndRef = requireNotNull(auctionItems.states.find { it.state.data.linearId == state.auctionItem })
        val auctionEndCmd = Command(AuctionContract.Commands.End(), state.participants.map { it.owningKey })
        val auctionItemDelistCmd = Command(AuctionItemContract.Commands.Delist(), state.participants.map { it.owningKey })
        val outputItemState = itemStateAndRef.state.data.delist()
        builder.addInputState(input)
                .addInputState(itemStateAndRef)
                .addCommand(auctionEndCmd)
                .addCommand(auctionItemDelistCmd)
                .addOutputState(outputItemState, AUCTION_ITEM_CONTRACT_ID)
                .setTimeWindow(TimeWindow.fromOnly(serviceHub.clock.instant()))

        val everyoneElse = state.participants - ourIdentity
        val flowSessions = everyoneElse.map { initiateFlow(it) }
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, flowSessions))
        return subFlow(FinalityFlow(stx, flowSessions)).also {
            val broadcastToParties =
                    serviceHub.networkMapCache.allNodes.map { node -> node.legalIdentities.first() } - state.participants
            subFlow(BroadcastTransactionFlow(it, broadcastToParties))
        }
    }
}


@InitiatedBy(AuctionEndFlow::class)
class AuctionEndFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                // TODO: Add additional checks here
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}