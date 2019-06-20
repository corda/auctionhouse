package net.corda.auctionhouse.contract

import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class AuctionItemContract : Contract {
    companion object {
        @JvmStatic
        val AUCTION_ITEM_CONTRACT_ID = "net.corda.auctionhouse.contract.AuctionItemContract"
    }

    /**
     * Adding more commands will require implementation of the corresponding
     * verification method. Designed this way to avoid changes to the actual [verify] method.
     */
    interface Commands : CommandData {
        fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>)

        class Issue : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    "No inputs should be consumed when issuing an auction item." using tx.inputStates.isEmpty()
                    "Only one output state should be created when issuing an auction item." using (1 == tx.outputStates.size)
                    "Output state must be an AuctionItemState" using (tx.outputStates.single() is AuctionItemState)
                    val state = tx.outputStates.single() as AuctionItemState
                    "Only the owner needs to sign the transaction" using (signers == setOf(state.owner.owningKey))
                }
            }
        }

        class List : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    "An List transaction should only consume one input state." using (1 == tx.inputStates.size)
                    "An List should create two output states." using (2 == tx.outputStates.size)
                    "There are no AuctionState inputs" using (tx.inputsOfType(AuctionState::class.java).none())
                    "There is one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                    "There is one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                    val auctionItemStateOut = tx.outputsOfType(AuctionItemState::class.java).single()
                    val auctionItemStateIn = tx.inputsOfType(AuctionItemState::class.java).single()
                    "The 'listed' property must be false in the input state" using (!auctionItemStateIn.listed)
                    "The 'listed' property must be true in the output state" using (auctionItemStateOut.listed)
                    "Only the 'listed' property can change" using (auctionItemStateOut.copy(listed = false) == auctionItemStateIn)
                    "Only the owner needs to sign the transaction" using (signers == setOf(auctionItemStateIn.owner.owningKey))
                }
            }
        }

        class Transfer : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    "There must be only one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                    "There must be only one AuctionState input" using (tx.inputsOfType(AuctionState::class.java).size == 1)
                    "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                    "There must not be any AuctionState outputs" using tx.outputsOfType(AuctionState::class.java).none()
                    val inputItem = tx.inputsOfType(AuctionItemState::class.java).single()
                    val outputItem = tx.outputsOfType(AuctionItemState::class.java).single()
                    "The 'owner' and 'listed' properties can change." using (inputItem == outputItem.copy(owner = inputItem.owner, listed = inputItem.listed))
                    "The owner property must change" using (outputItem.owner != inputItem.owner)
                    "The listed property must change" using (outputItem.listed != inputItem.listed)
                    "The 'listed' property must be false" using (!outputItem.listed)
                    "The previous and new owner only must sign a transfer transaction" using (signers == setOf(outputItem.owner.owningKey, inputItem.owner.owningKey))
                }
            }
        }

        class Delist : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                "There must be only one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                "There must be only one AuctionState input" using (tx.inputsOfType(AuctionState::class.java).size == 1)
                "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                "There must not be any AuctionState outputs" using tx.outputsOfType(AuctionState::class.java).none()
                val inputItem = tx.inputsOfType(AuctionItemState::class.java).single()
                val outputItem = tx.outputsOfType(AuctionItemState::class.java).single()
                "The 'listed' property can change." using (inputItem == outputItem.copy(listed = inputItem.listed))
                "The 'listed' property must change" using (outputItem.listed != inputItem.listed)
                "The 'listed' property must be false" using (!outputItem.listed)
                "Only the owner must sign a delist transaction" using (signers == setOf(inputItem.owner.owningKey))
            }
        }
    }


    /**
     * The contract code for the [AuctionContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        command.value.verifyCommand(tx, command.signers.toSet())
    }
}