package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.requiredContractClassName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.Cash
import net.corda.nodeapi.exceptions.InternalNodeException.Companion.message
import java.lang.IllegalArgumentException
import java.time.Clock
import java.time.Instant
import java.util.*

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AuctionListFlow(val itemId: UniqueIdentifier,
                      val price: Amount<Currency>,
                      val expiry: Instant,
                      val seller: Party) : FlowLogic<UniqueIdentifier>() {
    @Suspendable
    override fun call(): UniqueIdentifier {

        val auctionItems = serviceHub.vaultService.queryBy(AuctionItemState::class.java)
        val stateAndRef = auctionItems.states.find { it.state.data.linearId == itemId } ?:
                    throw IllegalArgumentException("State with auctionId '$itemId' not found in the Node's vault.")

        if (stateAndRef.state.data.listed) {
            throw IllegalArgumentException("The auction item is already listed")
        }

        val state = AuctionState(itemId, price, seller, null, expiry)
        val outputItemState = stateAndRef.state.data.list()
        val listAuctionCmd = Command(AuctionContract.Commands.List(), state.participants.map { it.owningKey })
        val listAuctionItemCmd = Command(AuctionItemContract.Commands.List(), state.participants.map { it.owningKey })
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
                .withItems(
                        stateAndRef,
                        listAuctionCmd,     StateAndContract(state,           AUCTION_CONTRACT_ID     ),
                        listAuctionItemCmd, StateAndContract(outputItemState, AUCTION_ITEM_CONTRACT_ID),
                        TimeWindow.fromOnly(Instant.now(Clock.systemUTC()))
                )
        builder.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(ptx, emptyList())).also {
            // sends to everyone in the network
            val broadcastToParties =
                    serviceHub.networkMapCache.allNodes.map { node -> node.legalIdentities.first() } - state.seller
            subFlow(BroadcastTransactionFlow(it, broadcastToParties))
        }
        return state.linearId
    }

    companion object {
        fun tracker() = ProgressTracker()
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(AuctionListFlow::class)
class AuctionListFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                //val output = stx.tx.outputs.single().data
                //"This must be an Auction state transaction" using (output is AuctionState)
            }
        }
        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession))
    }
}