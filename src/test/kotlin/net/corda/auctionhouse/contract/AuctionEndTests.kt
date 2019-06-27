package net.corda.auctionhouse.contract

import net.corda.auctionhouse.*
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.TimeWindow
import net.corda.core.internal.packageName
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.ledger
import net.corda.testing.node.MockServices

import org.junit.Test

import java.time.Instant

class AuctionEndTests {

    private val defaultIssuer = MEGACORP.ref(1.toByte())

    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    @Test
    fun onlyOneAuctionStateInput() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "There must be only one AuctionState input"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this.verifies()
            }
        }
    }

    @Test
    fun noAuctionStateOutputs() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "There must not be any AuctionState outputs"
            }
        }
    }

    @Test
    fun mustBeTimestamped() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "Transaction must be timestamped"
            }
        }
    }

    @Test
    fun doesNotHaveToBeExpired() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this.verifies()
            }
        }
    }

    @Test
    fun noCashOrItemTransfer() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(ALICE.publicKey, AuctionContract.Commands.End())
                command(ALICE.publicKey, AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                input(Cash.PROGRAM_ID, inCashState)
                this `fails with` "There should be no cash inputs"
            }
        }

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(ALICE.publicKey, AuctionContract.Commands.End())
                command(ALICE.publicKey, AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                output(Cash.PROGRAM_ID, outCashState)
                this `fails with` "There should be no cash outputs"
            }
        }

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "Item must be delisted"
            }
        }

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this.verifies()
            }
        }
    }

    @Test
    fun sellerAndBidderMustSign() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(ALICE.publicKey, AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "Both seller and bidder only must sign the auction end transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "Both seller and bidder only must sign the auction end transaction"
            }
        }
    }

    @Test
    fun onlyOneAuctionItemState() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "There must be only one AuctionItemState input"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "There must be only one AuctionItemState output"
            }
        }
    }

    @Test
    fun ifNoBidderOnlyOwnerMustSign() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(ALICE.publicKey, AuctionContract.Commands.End())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Delist())
                this `fails with` "Only the owner must sign a de-list transaction"
            }
        }
    }}
