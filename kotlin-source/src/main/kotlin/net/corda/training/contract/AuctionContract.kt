package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.training.state.AuctionState
import java.security.PublicKey
import net.corda.training.contract.AuctionContract.Commands as Commands

class AuctionContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "net.corda.training.contract.AuctionContract"
    }

    /**
     * Adding more commands will require implementation of the corresponding
     * verification method. Designed this way to avoid changes to the actual [verify] method.
     */
    interface Commands : CommandData {
        fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>)

        class List : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    "No inputs should be consumed when listing an auction." using tx.inputStates.isEmpty()
                    "Only one output state should be created when listing an auction." using (1 == tx.outputStates.size)
                    "Output state must be an AuctionState" using (tx.outputStates.single() is AuctionState)
                    val state = tx.outputStates.single() as AuctionState
                    "A newly issued auction must have a positive price." using (state.price.quantity > 0)
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Listings must be timestamped")
                    "The expiry date cannot be in the past" using (time < state.expiry)
                    "Only the seller needs to sign the transaction" using (signers == setOf(state.seller.owningKey))
                }
            }
        }

        class Bid: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Bids must be timestamped")
                    "An Bid transaction should only consume one input state." using (1 == tx.inputStates.size)
                    "An Bid transaction should only create one output state." using (1 == tx.outputStates.size)
                    "Input state must be an AuctionState" using (tx.inputStates.single() is AuctionState)
                    "Output state must be an AuctionState" using (tx.outputStates.single() is AuctionState)
                    val inputState = tx.inputStates.single() as AuctionState
                    val outputState = tx.outputStates.single() as AuctionState
                    "The auction is already expired!" using (time < inputState.expiry)
                    "Only the bidder and price may change." using (inputState == outputState.copy(bidder = inputState.bidder, price = inputState.price))
                    "The bidder property must change in a bid." using (outputState.bidder != inputState.bidder)
                    "The price property must change in a bid." using (outputState.price != inputState.price)
                    "A bidder party must exist" using (outputState.bidder != null)
                    "The seller, old bidder and new bidder only must sign a bid transaction" using (signers == listOfNotNull(outputState.seller.owningKey, requireNotNull(outputState.bidder).owningKey, inputState.bidder?.owningKey).toSet())
                }            }
        }

        class Settle: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    val states = tx.groupStates<AuctionState, UniqueIdentifier> { it.linearId }.single()
                    "There must be one input auction." using (1 == states.inputs.size)
                    "Input state must be an AuctionState" using (tx.inputStates.single() is AuctionState)
                    val inputState = tx.inputStates.single() as AuctionState
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Bids must be timestamped")
                    "The auction is already expired!" using (time < inputState.expiry)
                    if (inputState.bidder != null) {
                        val cashOutputs = tx.outputsOfType(Cash.State::class.java)
                        "There must be output cash." using cashOutputs.isNotEmpty()
                        val ownerCashAmount = cashOutputs.filter { it.owner == inputState.seller }
                        "There must be output cash paid to the seller." using (ownerCashAmount.isNotEmpty())
                        val settled = ownerCashAmount.sumCash().withoutIssuer()
                        val total = inputState.price
                        "The amount settled must be equal to the price of the auction." using (total.compareTo(settled) == 0)
                        "There must be no output auction as it has been settled." using (states.outputs.isEmpty())
                        "Both seller and bidder together only must sign the auction settlement transaction." using
                                (signers == listOfNotNull(inputState.seller.owningKey,
                                        inputState.bidder.owningKey).toSet())
                    } else {
                        "There must be no output states" using tx.outputStates.isEmpty()
                        "Only the seller must sign the auction settlement transaction." using
                                (signers == setOf(inputState.seller.owningKey))
                    }
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