package net.corda.auctionhouse.contract

import net.corda.auctionhouse.ALICE
import net.corda.auctionhouse.BOB
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TimeWindow
import net.corda.core.internal.packageName
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.ledger
import net.corda.testing.node.MockServices

import org.junit.Test

import java.time.Instant

class AuctionListTests {

    // A dummy command.
    class DummyCommand : CommandData

    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    @Test
    fun mustHandleListCommands() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                command(ALICE.publicKey, DummyCommand())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "Required net.corda.auctionhouse.contract.AuctionContract.Commands command"
            }

            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, DummyCommand())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "Required net.corda.auctionhouse.contract.AuctionItemContract.Commands command"
            }

            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this.verifies()
            }
        }
    }

    @Test
    fun mustConsumeOnlyOneInput() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(AUCTION_ITEM_CONTRACT_ID, AuctionItemState("Sofa bed", BOB.party))
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))

                this `fails with` "There must be only one AuctionItemState input"
            }

            transaction {
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Only one input should be consumed when listing an auction"
            }
        }
    }

    @Test
    fun inputStateMustBeOfCorrectType() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "The input state type should be AuctionItemState"
            }
        }
    }

    @Test
    fun mustProduceExactlyTwoOutputs() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Only two output states should be created when listing an auction"
            }

            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "There must be only one AuctionItemState output"
            }

            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "There must be only one AuctionItemState output"
            }
        }
    }

    @Test
    fun listingPriceMustBeGreaterThanZero() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 0.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "A newly issued auction must have a starting price greater than zero"
            }
        }
    }

    @Test
    fun auctionListingsMustBeTimestamped() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Auction listings must be timestamped"
            }
        }
    }

    @Test
    fun expiryDateMustBeInTheFuture() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "The expiry date cannot be in the past"
            }
        }
    }

    @Test
    fun onlyTheSellerNeedsToSignTx() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Only the seller needs to sign the transaction"
            }

            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.List())
                this `fails with` "Only the owner needs to sign the transaction"
            }
        }
    }

    @Test
    fun auctionMustHaveNoBidder() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, BOB.party, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(ALICE.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "The auction must have no bidder when listed"
            }
        }
    }

    @Test
    fun onlyOwnerCanListItem() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", BOB.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(BOB.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Only the owner of the auction item can list it in an auction"
            }
        }
    }

    @Test
    fun itemNotAlreadyListed() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", BOB.party, true)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.list())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(BOB.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "The 'listed' property must be false in the input state"
            }
        }
    }

    @Test
    fun outputItemMustBeListed() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", BOB.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item)
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(BOB.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "The 'listed' property must be true in the output state"
            }
        }
    }

    @Test
    fun onlyListedPropertyShouldChange() {
        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", BOB.party)
        val auction = AuctionState(item.linearId, 10.POUNDS, ALICE.party, null, expiry)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_ITEM_CONTRACT_ID, item)
                output(AUCTION_CONTRACT_ID, auction)
                output(AUCTION_ITEM_CONTRACT_ID, item.copy(listed = true, description = "copper ring"))
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                command(ALICE.publicKey, AuctionContract.Commands.List())
                command(BOB.publicKey, AuctionItemContract.Commands.List())
                this `fails with` "Only the 'listed' property can change"
            }
        }
    }

}