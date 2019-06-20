package net.corda.auctionhouse.contract

import net.corda.auctionhouse.state.AuctionItemState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import net.corda.auctionhouse.contract.AuctionContract.Commands as Commands

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

        class Transfer : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    "An Transfer should only consume one input state." using (1 == tx.inputStates.size)
                    "An Transfer should only create one output state." using (1 == tx.outputStates.size)
                    "Input state must be an AuctionItemState" using (tx.inputStates.single() is AuctionItemState)
                    "Output state must be an AuctionItemState" using (tx.outputStates.single() is AuctionItemState)
                    val inputState = tx.inputStates.single() as AuctionItemState
                    val outputState = tx.outputStates.single() as AuctionItemState
                    "Only the owner may change." using (inputState == outputState.copy(owner = inputState.owner))
                    "The owner property must change" using (outputState.owner != inputState.owner)
                    "The previous and new owner only must sign a transfer transaction" using (signers == listOfNotNull(outputState.owner.owningKey, inputState.owner.owningKey).toSet())
                }
            }
        }

        class List : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                requireThat {
                    //"An List should only consume one input state." using (1 == tx.inputStates.size)
                    //"An List should only create one output state." using (1 == tx.outputStates.size)
                    //"Input state must be an AuctionItemState" using (tx.inputStates.single() is AuctionItemState)
                    //"Output state must be an AuctionItemState" using (tx.outputStates.single() is AuctionItemState)
                    //val inputState = tx.inputStates.single() as AuctionItemState
                    //val outputState = tx.outputStates.single() as AuctionItemState
                    //"Only the owner may change." using (inputState == outputState.copy(isListed = inputState.isListed))
                    //"The isListed property must be false in the input state" using (!inputState.isListed)
                    //"The isListed property must be true in the output state" using (outputState.isListed)
                    //"Only the owner needs to sign the transaction" using (signers == setOf(inputState.owner.owningKey))
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