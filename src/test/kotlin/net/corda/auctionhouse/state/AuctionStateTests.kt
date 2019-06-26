package net.corda.auctionhouse.state

import net.corda.auctionhouse.ALICE
import net.corda.auctionhouse.BOB
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.SchedulableState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AuctionStateTests {

    @Test
    fun hasFieldsOfCorrectType() {
        AuctionState::class.java.getDeclaredField("price")
        assertEquals(AuctionState::class.java.getDeclaredField("price").type, Amount::class.java)

        AuctionState::class.java.getDeclaredField("auctionItem")
        assertEquals(AuctionState::class.java.getDeclaredField("auctionItem").type, UniqueIdentifier::class.java)

        AuctionState::class.java.getDeclaredField("expiry")
        assertEquals(AuctionState::class.java.getDeclaredField("expiry").type, Instant::class.java)

        AuctionState::class.java.getDeclaredField("seller")
        assertEquals(AuctionState::class.java.getDeclaredField("seller").type, Party::class.java)

        AuctionState::class.java.getDeclaredField("bidder")
        assertEquals(AuctionState::class.java.getDeclaredField("bidder").type, Party::class.java)
    }

    @Test
    fun sellerIsParticipant() {
        val auctionState = AuctionState(UniqueIdentifier(), 10.POUNDS, ALICE.party, BOB.party, Instant.now())
        assertNotEquals(auctionState.participants.indexOf(ALICE.party), -1)
    }

    @Test
    fun bidderIsParticipantIfNotNull() {
        var auctionState = AuctionState(UniqueIdentifier(), 10.POUNDS, ALICE.party, BOB.party, Instant.now())
        assertNotEquals(auctionState.participants.indexOf(BOB.party), -1)

        auctionState = AuctionState(UniqueIdentifier(), 10.POUNDS, ALICE.party, null, Instant.now())
        assertEquals(auctionState.participants.indexOf(BOB.party), -1)
    }

    @Test
    fun isLinearState() {
        assert(LinearState::class.java.isAssignableFrom(AuctionState::class.java))
    }

    @Test
    fun isSchedulableState() {
        assert(SchedulableState::class.java.isAssignableFrom(AuctionState::class.java))
    }

    @Test
    fun hasLinearIdFieldOfCorrectType() {
        // Does the linearId field exist?
        AuctionState::class.java.getDeclaredField("linearId")
        // Is the auctionId field of the correct type?
        assertEquals(AuctionState::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
    }


    @Test
    fun checkParameterOrdering() {
        val fields = AuctionState::class.java.declaredFields
        val auctionItemIdx = fields.indexOf(AuctionState::class.java.getDeclaredField("auctionItem"))
        val priceIdx = fields.indexOf(AuctionState::class.java.getDeclaredField("price"))
        val sellerIdx = fields.indexOf(AuctionState::class.java.getDeclaredField("seller"))
        val bidderIdx = fields.indexOf(AuctionState::class.java.getDeclaredField("bidder"))
        val linearIdIdx = fields.indexOf(AuctionState::class.java.getDeclaredField("linearId"))

        assert(auctionItemIdx < priceIdx)
        assert(priceIdx < sellerIdx)
        assert(sellerIdx < bidderIdx)
        assert(bidderIdx < linearIdIdx)
    }

    @Test
    fun checkBidHelperMethod() {
        val auction = AuctionState(UniqueIdentifier(), 10.DOLLARS, ALICE.party, null, Instant.now())
        val newAction = auction.bid(15.DOLLARS, BOB.party)
        assertEquals(15.DOLLARS, newAction.price)
        assertEquals(BOB.party, newAction.bidder)
        assertEquals(auction, newAction.copy(price = auction.price, bidder = auction.bidder))
    }
}
