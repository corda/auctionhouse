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
import net.corda.finance.contracts.asset.PartyAndAmount
import net.corda.finance.workflows.asset.CashUtils
import java.lang.IllegalArgumentException

@InitiatingFlow
@SchedulableFlow
class AuctionSettleFlow(val linearId: UniqueIdentifier): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val auctions = serviceHub.vaultService.queryBy(AuctionState::class.java)
        val auctionStateAndRef = requireNotNull(auctions.states.find { it.state.data.linearId == linearId })
        val state = auctionStateAndRef.state.data
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        if (state.bidder != ourIdentity) {
            throw IllegalArgumentException("Only the winning bidder should initiate this flow")
        }

        val token = state.price.token
        val currency = token.currencyCode
        val bidderBalance = serviceHub.getCashBalance(token)
        if (bidderBalance.quantity <= 0) {
            throw IllegalArgumentException("Bidder has no $currency to settle.")
        }

        if (bidderBalance < state.price) {
            throw IllegalArgumentException("Bidder has only ${bidderBalance.toDecimal()} $currency but needs ${state.price.toDecimal()} $currency to settle.")
        }

        CashUtils.generateSpend(serviceHub, builder, listOf(PartyAndAmount(state.seller, state.price)), ourIdentityAndCert)

        val auctionItems = serviceHub.vaultService.queryBy(AuctionItemState::class.java)
        val itemStateAndRef = requireNotNull(auctionItems.states.find { it.state.data.linearId == state.auctionItem })
        val outputItemState = itemStateAndRef.state.data.transfer(state.bidder)

        builder.withItems(
                auctionStateAndRef, // input auction
                itemStateAndRef, // input auction item
                Command(AuctionContract.Commands.Settle(), state.participants.map { it.owningKey }), // settle command
                Command(AuctionItemContract.Commands.Transfer(), state.participants.map { it.owningKey }), // transfer command
                StateAndContract(outputItemState, AUCTION_ITEM_CONTRACT_ID)) // output auction item

        val session = initiateFlow(state.seller)
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(session)))
        return subFlow(FinalityFlow(stx, session)).also {
            // sends to everyone in the network
            val broadcastToParties =
                    serviceHub.networkMapCache.allNodes.map { node -> node.legalIdentities.first() } - state.participants
            subFlow(BroadcastTransactionFlow(it, broadcastToParties))
        }
    }
}

/**
 * This is the flow which signs IOU settlements.
 * The signing is handled by the [SignTransactionFlow].
 */
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
        subFlow(ReceiveFinalityFlow(flowSession))
    }
}