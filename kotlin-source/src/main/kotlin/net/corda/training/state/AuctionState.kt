package net.corda.training.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.training.contract.AuctionContract
import java.time.Instant
import java.util.*

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [IOUStateTests] for
 * instructions on how to complete the [AuctionState] class.
 *
 * Remove the "val data: String = "data" property before starting the [AuctionState] tasks.
 */
@BelongsToContract(AuctionContract::class)
data class AuctionState(
        val price: Amount<Currency>,
        val seller: Party,
        val bidder: Party?,
        val expiry: Instant,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState {
    override val participants: List<Party> get() = listOfNotNull(seller, bidder)

    fun bid(amount: Amount<Currency>, bidder: Party): AuctionState {
        //Date.from(Instant.now(Clock.systemUTC()))
        return this.copy(price = amount, bidder = bidder)
    }

}