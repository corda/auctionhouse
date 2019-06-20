package net.corda.auctionhouse.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState

/**
 * This is the flow which handles self-issuing of Auction Items on the ledger.
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AuctionItemSelfIssueFlow(val description: String, val owner: Party) : FlowLogic<AuctionItemState>() {
    @Suspendable
    override fun call(): AuctionItemState {
        if (ourIdentity != owner) {
            throw IllegalArgumentException("Only the Node can issue auction items to itself")
        }
        val state = AuctionItemState(description, owner)
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val command = Command(AuctionItemContract.Commands.Issue(), listOf(ourIdentity.owningKey))
        builder.withItems(StateAndContract(state, AUCTION_ITEM_CONTRACT_ID), command)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList())).tx.outputsOfType(AuctionItemState::class.java).single()
    }
}
