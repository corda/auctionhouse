package net.corda.auctionhouse.contract

import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * Transactions involving one or more AuctionItemState will use this contract to
 * verify that the transactions are valid.
 */
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
                    "No inputs should be consumed when issuing an auction item" using tx.inputStates.isEmpty()
                    "Only one output state should be created when issuing an auction item" using (1 == tx.outputStates.size)
                    "The output state must be an AuctionItemState" using (tx.outputStates.single() is AuctionItemState)
                    val state = tx.outputStates.single() as AuctionItemState
                    "Only the owner needs to sign the transaction" using (signers == setOf(state.owner.owningKey))
                }
            }
        }

        class List : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    "There must be only one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                    "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
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
                    "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                    val inputItem = tx.inputsOfType(AuctionItemState::class.java).single()
                    val outputItem = tx.outputsOfType(AuctionItemState::class.java).single()
                    "Only the 'owner' and 'listed' properties can change" using (inputItem == outputItem.copy(owner = inputItem.owner, listed = inputItem.listed))
                    "The 'owner' property must change" using (outputItem.owner != inputItem.owner)
                    "The 'listed' property must change" using (outputItem.listed != inputItem.listed)
                    "The 'listed' property must be 'false'" using (!outputItem.listed)
                    "The previous and new owner only must sign a transfer transaction" using (signers == setOf(outputItem.owner.owningKey, inputItem.owner.owningKey))
                }
            }
        }

        class Delist : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                "There must be only one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                val inputItem = tx.inputsOfType(AuctionItemState::class.java).single()
                val outputItem = tx.outputsOfType(AuctionItemState::class.java).single()
                val inputAuction = tx.inputsOfType(AuctionState::class.java).single()
                "Only the 'listed' property can change" using (inputItem == outputItem.copy(listed = inputItem.listed))
                "The 'listed' property must change" using (outputItem.listed != inputItem.listed)
                "The 'listed' property must be 'false'" using (!outputItem.listed)
                if (inputAuction.bidder != null) {
                    "Only the owner and bidder must sign a de-list transaction" using (signers == setOf(inputItem.owner.owningKey, inputAuction.bidder.owningKey))
                } else {
                    "Only the owner must sign a de-list transaction" using (signers == setOf(inputItem.owner.owningKey))
                }
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