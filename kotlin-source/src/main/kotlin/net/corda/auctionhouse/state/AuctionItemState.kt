package net.corda.auctionhouse.state

import net.corda.core.contracts.*
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(AuctionItemContract::class)
data class AuctionItemState
(
        val description: String,
        val owner: Party,
        val listed: Boolean = false,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState {
    override val participants: List<AbstractParty> get() = listOf(owner)

    fun transfer(newOwner: Party): AuctionItemState { return copy(owner = newOwner, listed = false) }

    fun delist(): AuctionItemState { return copy(listed = false) }

    fun list(): AuctionItemState { return copy(listed = true) }
}