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

class AuctionSettleTests {

    private val defaultIssuer = MEGACORP.ref(1.toByte())

    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    @Test
    fun onlyOneAuctionStateInput() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "There must be only one AuctionState input"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this.verifies()
            }
        }
    }

    @Test
    fun noAuctionStateOutputs() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
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

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "Auction settlements must be timestamped"
            }
        }
    }

    @Test
    fun mustBeExpired() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "Auction cannot be settled before it expires"
            }
        }
    }

    @Test
    fun ifNoWinnerNoCashOrItemTransfer() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, null, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(ALICE.publicKey, AuctionContract.Commands.Settle())
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
                command(ALICE.publicKey, AuctionContract.Commands.Settle())
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
                output(AUCTION_ITEM_CONTRACT_ID, item.delist())
                command(ALICE.publicKey, AuctionContract.Commands.Settle())
                command(ALICE.publicKey, AuctionItemContract.Commands.Delist())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this.verifies()
            }
        }
    }

    @Test
    fun whenBidderNotNull_thereMustBeOutputCash() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "There must be output cash"
            }
        }
    }

    @Test
    fun whenBidderNotNull_outputCashPaidToSeller() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, CHARLIE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "There must be output cash paid to the seller"
            }
        }
    }

    @Test
    fun whenBidderNotNull_paidAmountEqualToBid() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(4000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "The amount settled must be equal to the price of the auction"
            }
        }
    }

    @Test
    fun whenBidderNotNull_sellerAndBidderMustSign() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(ALICE.publicKey, AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "Both seller and bidder only must sign the auction settlement transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "Both seller and bidder only must sign the auction settlement transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(ALICE.publicKey, AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "The previous and new owner only must sign a transfer transaction"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this `fails with` "The previous and new owner only must sign a transfer transaction"
            }
        }
    }

}