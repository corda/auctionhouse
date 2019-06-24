package net.corda.auctionhouse.contract

import net.corda.auctionhouse.ALICE
import net.corda.auctionhouse.BOB
import net.corda.auctionhouse.CHARLIE
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.packageName
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.MockServices
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.TimeWindow
import net.corda.finance.POUNDS
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant

class AuctionBidTests {
    // A pre-made dummy state we may need for some of the tests.
    class DummyState : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }
    // A dummy command.
    class DummyCommand : CommandData
    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    @Test
    fun mustHandleBidCommands() {

        val expiry = Instant.now().plusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "Required net.corda.auctionhouse.contract.AuctionContract.Commands command"
            }
            transaction {
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
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
                input(AuctionContract::class.java.name, auctionIn)
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey),  AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "A Bid transaction should only consume one input state."
            }

            transaction {
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
                output(AuctionContract::class.java.name, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey),  AuctionContract.Commands.Bid())
                timeWindow(TimeWindow.between(Instant.now(), expiry))
                this `fails with` "A Bid transaction should only create one output state."
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
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "Bids must be timestamped"
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
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "The auction is already expired!"
            }
        }
    }

    @Test
    fun inputStateTypeMustBeAuctionState() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AuctionContract::class.java.name, DummyState())
                output(AuctionContract::class.java.name, auctionOut)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "Input state must be an AuctionState"
            }
        }
    }


    @Test
    fun outputStateTypeMustBeAuctionState() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party)
        val auctionIn = AuctionState(item.linearId, 4000.POUNDS, ALICE.party, null, expiry)
        val auctionOut = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry, auctionIn.linearId)

        ledgerServices.ledger {
            transaction {
                input(AuctionContract::class.java.name, auctionIn)
                output(AuctionContract::class.java.name, DummyState())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Bid())
                this `fails with` "Output state must be an AuctionState"
            }
        }
    }

//
//    /**
//     * Task 2.
//     * The transfer transaction should only have one input state and one output state.
//     * TODO: Add constraints to the contract code to ensure a transfer transaction has only one input and output state.
//     * Hint:
//     * - Look at the contract code for "Issue".
//     */
//    @Test
//    fun mustHaveOneInputAndOneOutput() {
//        val iou = AuctionState(10.POUNDS, ALICE.party, BOB.party)
//        ledgerServices.ledger {
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                input(AuctionContract::class.java.name, DummyState())
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "An IOU transfer transaction should only consume one input state."
//            }
//            transaction {
//                output(AuctionContract::class.java.name, iou)
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "An IOU transfer transaction should only consume one input state."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "An IOU transfer transaction should only create one output state."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                output(AuctionContract::class.java.name, DummyState())
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "An IOU transfer transaction should only create one output state."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this.verifies()
//            }
//        }
//    }
//
//    /**
//     * Task 3.
//     * TODO: Add a constraint to the contract code to ensure only the lender property can change when transferring IOUs.
//     * Hint:
//     * - You can use the [AuctionState.copy] method.
//     * - You can compare a copy of the input to the output with the lender of the output as the lender of the input.
//     * - You'll need references to the input and output ious.
//     * - Remember you need to cast the [ContractState]s to [AuctionState]s.
//     * - It's easier to take this approach then check all properties other than the lender haven't changed, including
//     *   the [auctionId] and the [contract]!
//     */
//    @Test
//    fun onlyTheLenderMayChange() {
//        val iou = AuctionState(10.POUNDS, ALICE.party, BOB.party)
//        ledgerServices.ledger {
//            transaction {
//                input(AuctionContract::class.java.name, AuctionState(10.DOLLARS, ALICE.party, BOB.party))
//                output(AuctionContract::class.java.name, AuctionState(1.DOLLARS, ALICE.party, BOB.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, AuctionState(10.DOLLARS, ALICE.party, BOB.party))
//                output(AuctionContract::class.java.name, AuctionState(10.DOLLARS, ALICE.party, CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, AuctionState(10.DOLLARS, ALICE.party, BOB.party, 5.DOLLARS))
//                output(AuctionContract::class.java.name, AuctionState(10.DOLLARS, ALICE.party, BOB.party, 10.DOLLARS))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this.verifies()
//            }
//        }
//    }
//
//    /**
//     * Task 4.
//     * It is fairly obvious that in a transfer IOU transaction the lender must change.
//     * TODO: Add a constraint to check the lender has changed in the output IOU.
//     */
//    @Test
//    fun theLenderMustChange() {
//        val iou = AuctionState(10.POUNDS, ALICE.party, BOB.party)
//        ledgerServices.ledger {
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou)
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The lender property must change in a transfer."
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this.verifies()
//            }
//        }
//    }
//
//    /**
//     * Task 5.
//     * All the participants in a transfer IOU transaction must sign.
//     * TODO: Add a constraint to check the old lender, the new lender and the recipient have signed.
//     */
//    @Test
//    fun allParticipantsMustSign() {
//        val iou = AuctionState(10.POUNDS, ALICE.party, BOB.party)
//        ledgerServices.ledger {
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, MINICORP.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey, MINICORP.publicKey), AuctionContract.Commands.Transfer())
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input(AuctionContract::class.java.name, iou)
//                output(AuctionContract::class.java.name, iou.withNewLender(CHARLIE.party))
//                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLIE.publicKey), AuctionContract.Commands.Transfer())
//                this.verifies()
//            }
//        }
//    }
}