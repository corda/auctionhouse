package net.corda.auctionhouse.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.flow.AuctionSettleFlow
import net.corda.core.flows.FlowLogicRefFactory
import java.time.Instant
import java.util.*

/**
 * A state describing an auction.
 * @param auctionItem The unique id of the auction item.
 * @param price The current price of the auction.
 * @param seller The selling party.
 * @param bidder The bidding party (there may not be one!).
 * @param expiry The expiry date/time of the auction as a time instant.
 *
 * The [LinearState] interface is implemented such that every auction has a unique
 * identifier by which it can be referred to and looked-up with that does not change
 * as the state evolves.
 *
 * The [SchedulableState] interface allows us to schedule 'activities' at a specific time instant.
 * In the case of the AuctionState, settlement of the auction is scheduled upon expiry.
 */
@BelongsToContract(AuctionContract::class)
data class AuctionState(
        val auctionItem: UniqueIdentifier,
        val price: Amount<Currency>,
        val seller: Party,
        val bidder: Party?,
        val expiry: Instant,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState, SchedulableState {

    override val participants: List<Party> get() = listOfNotNull(seller, bidder)

    /**
     * Returns a copy the auction state with a new bidding party and price.
     */
    fun bid(amount: Amount<Currency>, bidder: Party): AuctionState {
        return this.copy(price = amount, bidder = bidder)
    }

    /**
     * See [SchedulableState.nextScheduledActivity]
     */
    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        return ScheduledActivity(flowLogicRefFactory.create(AuctionSettleFlow::class.java, thisStateRef), expiry)
    }

}