package net.corda.auctionhouse.state

import net.corda.core.contracts.*
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * A [LinearState] describing an auction item.
 * @param description Textual description of the auction item.
 * @param owner The Party who owns the auction item.
 * @param listed Whether or not the auction item is listed in an active auction.
 * @param linearId Unique identifier of a AuctionItemState object.
 */
@BelongsToContract(AuctionItemContract::class)
data class AuctionItemState
(
        val description: String,
        val owner: Party,
        val listed: Boolean = false,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState {
    override val participants: List<AbstractParty> get() = listOf(owner)

    /**
     * Returns a copy of this AuctionItemState which has a new owner and is not listed.
     */
    fun transfer(newOwner: Party): AuctionItemState { return copy(owner = newOwner, listed = false) }

    /**
     * Returns a copy of this AuctionItemState which is not listed.
     */
    fun delist(): AuctionItemState { return copy(listed = false) }

    /**
     * Returns a copy of this AuctionItemState which is listed.
     */
    fun list(): AuctionItemState { return copy(listed = true) }
}