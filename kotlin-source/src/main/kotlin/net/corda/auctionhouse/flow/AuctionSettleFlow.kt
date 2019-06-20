package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.workflows.getCashBalance
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.node.StatesToRecord
import net.corda.finance.contracts.asset.PartyAndAmount
import net.corda.finance.workflows.asset.CashUtils
import java.time.Instant

@InitiatingFlow
@SchedulableFlow
class AuctionSettleFlow(private val stateRef: StateRef) : FlowLogic<String?>() {
    @Suspendable
    override fun call(): String? {

        var message: String? = null
        val input = serviceHub.toStateAndRef<AuctionState>(stateRef)
        val state = input.state.data

        if (state.bidder != ourIdentity) {
            return message
        }

        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val auctionItems = serviceHub.vaultService.queryBy(AuctionItemState::class.java)
        val itemStateAndRef = requireNotNull(auctionItems.states.find { it.state.data.linearId == state.auctionItem })
        val auctionSettleCmd = Command(AuctionContract.Commands.Settle(), state.participants.map { it.owningKey })
        val token = state.price.token
        val bidderBalance = serviceHub.getCashBalance(token)
        if (bidderBalance.quantity <= 0 || bidderBalance < state.price) {
            val outputItemState = itemStateAndRef.state.data.delist()
            builder.addInputState(input)
                    .addInputState(itemStateAndRef)
                    .addCommand(auctionSettleCmd)
                    .addCommand(Command(AuctionItemContract.Commands.Delist(), state.participants.map { it.owningKey }))
                    .addOutputState(outputItemState, AUCTION_ITEM_CONTRACT_ID)
                    .setTimeWindow(TimeWindow.fromOnly(Instant.now()))
            message = "Insufficient balance!"

        }
        else {
            val outputItemState = itemStateAndRef.state.data.transfer(state.bidder)
            val auctionItemTransferCmd = Command(AuctionItemContract.Commands.Transfer(), state.participants.map { it.owningKey })
            builder.addInputState(input)
                    .addInputState(itemStateAndRef)
                    .addCommand(auctionSettleCmd)
                    .addCommand(auctionItemTransferCmd)
                    .addOutputState(outputItemState, AUCTION_ITEM_CONTRACT_ID)
                    .setTimeWindow(TimeWindow.fromOnly(Instant.now()))

            CashUtils.generateSpend(serviceHub, builder, listOf(PartyAndAmount(state.seller, state.price)), ourIdentityAndCert)
            message = "You won a '${itemStateAndRef.state.data.description}' for '${state.price}"
        }

        val session = initiateFlow(state.seller)
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(session)))
        subFlow(FinalityFlow(stx, session)).also {
            // sends to everyone in the network
            val broadcastToParties =
                    serviceHub.networkMapCache.allNodes.map { node -> node.legalIdentities.first() } - state.participants
            subFlow(BroadcastTransactionFlow(it, broadcastToParties))
        }

        return message
    }
}


@InitiatedBy(AuctionSettleFlow::class)
class AuctionSettleFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
              // val outputStates = stx.tx.outputs.map { it.data::class.java.name }.toList()
              //"There must be an IOU transaction." using (outputStates.contains(AuctionState::class.java.name))
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}