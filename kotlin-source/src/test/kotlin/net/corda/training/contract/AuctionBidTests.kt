package net.corda.training.contract

import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.packageName
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.training.ALICE
import net.corda.training.BOB
import net.corda.training.CHARLIE
import net.corda.training.MINICORP
import net.corda.training.state.AuctionState
import org.junit.Test

/**
 * Practical exercise instructions for Contracts Part 2.
 * The objective here is to write some contract code that verifies a transaction to issue an [AuctionState].
 * As with the [IOUIssueTests] uncomment each unit test and run them one at a time. Use the body of the tests and the
 * task description to determine how to get the tests to pass.
 */
class IOUTransferTests {
    // A pre-made dummy state we may need for some of the tests.
    class DummyState : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }
    // A dummy command.
    class DummyCommand : CommandData
    var ledgerServices = MockServices(listOf("net.corda.training", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))
//
//    /**
//     * Task 1.
//     * Now things are going to get interesting!
//     * We need the [AuctionContract] to not only handle Issues of IOUs but now also Transfers.
//     * Of course, we'll need to add a new Command and add some additional contract code to handle Transfers.
//     * TODO: Add a "Transfer" command to the AuctionState and update the verify() function to handle multiple commands.
//     * Hint:
//     * - As with the [Issue] command, add the [Transfer] command within the [AuctionContract.Commands].
//     * - Again, we only care about the existence of the [Transfer] command in a transaction, therefore it should
//     *   subclass the [TypeOnlyCommandData].
//     * - You can use the [requireSingleCommand] function to check for the existence of a command which implements a
//     *   specified interface. Instead of using
//     *
//     *       tx.commands.requireSingleCommand<Commands.Issue>()
//     *
//     *   You can instead use:
//     *
//     *       tx.commands.requireSingleCommand<Commands>()
//     *
//     *   To match any command that implements [AuctionContract.Commands]
//     * - We then need to switch on the type of [Command.value], in Kotlin you can do this using a "when" block
//     * - For each "when" block case, you can check the type of [Command.value] using the "is" keyword:
//     *
//     *       val command = ...
//     *       when (command.value) {
//     *           is Commands.X -> doSomething()
//     *           is Commands.Y -> doSomethingElse()
//     *       }
//     * - The [requireSingleCommand] function will handle unrecognised types for you (see first unit test).
//     */
//    @Test
//    fun mustHandleMultipleCommandValues() {
//        val iou = AuctionState(10.POUNDS, ALICE.party, BOB.party)
//        ledgerServices.ledger {
//            transaction {
//                output(AuctionContract::class.java.name, iou)
//                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand())
//                this `fails with` "Required net.corda.training.contract.AuctionContract.Commands command"
//            }
//            transaction {
//                output(AuctionContract::class.java.name, iou)
//                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Issue())
//                this.verifies()
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
//     *   the [linearId] and the [contract]!
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