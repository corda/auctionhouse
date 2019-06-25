package net.corda.auctionhouse.contract

import net.corda.auctionhouse.*
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.state.*
import net.corda.core.contracts.*
import net.corda.core.internal.packageName
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.ledger
import net.corda.testing.node.MockServices

import org.junit.Test

import java.time.Instant

class AuctionBidTests {

    var ledgerServices = MockServices(
            listOf("net.corda.auctionhouse",
                   "net.corda.finance.contracts.asset",
                    CashSchemaV1::class.packageName))

    @Test
    fun mustHandleBidCommands() {

        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyContract.Commands.DummyCommand())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "Required net.corda.auctionhouse.contract.AuctionContract.Commands command"
            }
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this.verifies()
            }
        }
    }

    @Test
    fun mustHaveOneInputOneOutputState() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "A bid transaction should only have one input state"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "A bid transaction should only have one output state"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this.verifies()
            }
        }
    }

    @Test
    fun mustBeTimestamped() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "Auction bids must be timestamped"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this.verifies()
            }
        }
    }

    @Test
    fun auctionMustNotBeExpired() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The auction must not be expired"
            }
        }
    }

    @Test
    fun inputStateTypeMustBeAuctionState() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(DummyContract.DUMMY_CONTRACT_ID, DummyContract.DummyState())
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The input state must be an AuctionState"
            }
        }
    }


    @Test
    fun outputStateTypeMustBeAuctionState() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(DummyContract.DUMMY_CONTRACT_ID, DummyContract.DummyState())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The output state must be an AuctionState"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this.verifies()
            }
        }
    }

    @Test
    fun bidderAndPriceMustChange() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, CHARLIE.party, expiry)

        var auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)
        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "Only the 'bidder' and 'price' may change"
            }

            auctionOut = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The 'price' property must change in a bid"
            }


            auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, CHARLIE.party, expiry, auctionIn.linearId)
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The 'bidder' property must change in a bid"
            }
        }
    }

    @Test
    fun newBidMustBeHigherThanPreviousBid() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, CHARLIE.party, expiry)
        val auctionOut = AuctionState(item.linearId, 3000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The new bid price must be greater than the current bid price"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut.copy(price = 4000.POUNDS))
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The 'price' property must change in a bid"
            }
        }
    }

    @Test
    fun bidderMustNotBeNull() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)

        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, CHARLIE.party, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, null, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The bidder cannot be 'null'"
            }
        }
    }

    @Test
    fun prevBidderAndSellerAndNewBidderMustSign() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, CHARLIE.party, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "The seller, previous bidder and new bidder only must sign a bid transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(CHARLIE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "The seller, previous bidder and new bidder only must sign a bid transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(CHARLIE.publicKey, ALICE.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "The seller, previous bidder and new bidder only must sign a bid transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                command(listOf(ALICE.publicKey), AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "The seller, previous bidder and new bidder only must sign a bid transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auctionIn)
                output(AUCTION_CONTRACT_ID, auctionOut)
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Bid())
                this.verifies()
            }
        }
    }
}