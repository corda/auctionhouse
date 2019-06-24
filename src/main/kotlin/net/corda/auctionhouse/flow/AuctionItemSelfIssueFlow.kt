package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState

/**
 * This is the flow which handles self-issuing of Auction Items on the ledger.
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [UniqueIdentifier] of the auction item that was committed to the ledger.
 * @param description A descriptive text of what the item is.
 */
@InitiatingFlow
@StartableByRPC
class AuctionItemSelfIssueFlow(val description: String) : FlowLogic<UniqueIdentifier>() {
    @Suspendable
    override fun call(): UniqueIdentifier {
        val state = AuctionItemState(description, ourIdentity)
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val command = Command(AuctionItemContract.Commands.Issue(), listOf(ourIdentity.owningKey))
        builder.withItems(StateAndContract(state, AUCTION_ITEM_CONTRACT_ID), command)
        builder.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(builder)
        subFlow(FinalityFlow(stx, emptyList())).tx.outputsOfType(AuctionItemState::class.java).single()
        return state.linearId
    }
}
