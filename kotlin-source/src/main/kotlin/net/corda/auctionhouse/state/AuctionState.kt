package net.corda.auctionhouse.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.flow.AuctionSettleFlow
import net.corda.core.flows.FlowLogicRefFactory
import java.time.Instant
import java.util.*
import kotlin.math.exp

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [IOUStateTests] for
 * instructions on how to complete the [AuctionState] class.
 *
 * Remove the "val data: String = "data" property before starting the [AuctionState] tasks.
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
    fun bid(amount: Amount<Currency>, bidder: Party): AuctionState {
        return this.copy(price = amount, bidder = bidder)
    }

    override fun nextScheduledActivity(thisStateRef: StateRef, flowLogicRefFactory: FlowLogicRefFactory): ScheduledActivity? {
        val flowLogicRef = flowLogicRefFactory.create(AuctionSettleFlow::class.java, linearId)
        return ScheduledActivity(flowLogicRef, expiry)
    }

}