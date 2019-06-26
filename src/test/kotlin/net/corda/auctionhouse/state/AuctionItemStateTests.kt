package net.corda.auctionhouse.state

import net.corda.auctionhouse.ALICE
import net.corda.auctionhouse.BOB
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AuctionItemStateTests {

    @Test
    fun hasFieldsOfCorrectType() {
        AuctionItemState::class.java.getDeclaredField("description")
        assertEquals(AuctionItemState::class.java.getDeclaredField("description").type, String::class.java)

        AuctionItemState::class.java.getDeclaredField("owner")
        assertEquals(AuctionItemState::class.java.getDeclaredField("owner").type, Party::class.java)

        AuctionItemState::class.java.getDeclaredField("listed")
        assertEquals(AuctionItemState::class.java.getDeclaredField("listed").type, Boolean::class.java)
    }

    @Test
    fun ownerIsParticipant() {
        val auctionState = AuctionItemState("diamond ring", ALICE.party)
        assertNotEquals(auctionState.participants.indexOf(ALICE.party), -1)
    }

    @Test
    fun isLinearState() {
        assert(LinearState::class.java.isAssignableFrom(AuctionItemState::class.java))
    }

    @Test
    fun hasLinearIdFieldOfCorrectType() {
        // Does the linearId field exist?
        AuctionItemState::class.java.getDeclaredField("linearId")
        // Is the auctionId field of the correct type?
        assertEquals(AuctionItemState::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
    }


    @Test
    fun checkParameterOrdering() {
        val fields = AuctionItemState::class.java.declaredFields
        val descriptionIdx = fields.indexOf(AuctionItemState::class.java.getDeclaredField("description"))
        val ownerIdx = fields.indexOf(AuctionItemState::class.java.getDeclaredField("owner"))
        val listedIdx = fields.indexOf(AuctionItemState::class.java.getDeclaredField("listed"))
        val linearIdIdx = fields.indexOf(AuctionItemState::class.java.getDeclaredField("linearId"))

        assert(descriptionIdx < ownerIdx)
        assert(ownerIdx < listedIdx)
        assert(listedIdx < linearIdIdx)
    }

    @Test
    fun checkListHelperMethod() {
        val auctionItem = AuctionItemState("diamond ring", ALICE.party)
        val listedAuctionItem = auctionItem.list()
        assertEquals(true, listedAuctionItem.listed)
        assertEquals(auctionItem, listedAuctionItem.copy(listed = false))
    }

    @Test
    fun checkTransferHelperMethod() {
        val auctionItem = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val newAuctionItem = auctionItem.transfer(BOB.party)
        assertEquals(BOB.party, newAuctionItem.owner)
        assertEquals(false, newAuctionItem.listed)
        assertEquals(auctionItem, newAuctionItem.copy(owner = auctionItem.owner, listed = true))
    }

    @Test
    fun checkDelistHelperMethod() {
        val auctionItem = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val newAuctionItem = auctionItem.delist()
        assertEquals(false, newAuctionItem.listed)
        assertEquals(auctionItem, newAuctionItem.copy(listed = true))
    }
}
