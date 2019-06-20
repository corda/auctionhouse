package net.corda.auctionhouse.contract

import net.corda.auctionhouse.state.AuctionItemState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.auctionhouse.state.AuctionState
import java.security.PublicKey
import net.corda.auctionhouse.contract.AuctionContract.Commands as Commands

class AuctionContract : Contract {
    companion object {
        @JvmStatic
        val AUCTION_CONTRACT_ID = "net.corda.auctionhouse.contract.AuctionContract"
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
                    "One input should be consumed when listing an auction." using (1 == tx.inputStates.size)
                    "The input state type should be AuctionItemState" using (tx.inputStates.single() is AuctionItemState)
                    "Only two output states should be created when listing an auction." using (2 == tx.outputStates.size)
                    "Output states have different types" using (tx.outputStates[0]::class != tx.outputStates[1]::class)
                    "Output state must be an AuctionState or AuctionItemState" using ((tx.outputStates[0] is AuctionItemState || tx.outputStates[1] is AuctionItemState)
                            && (tx.outputStates[0] is AuctionState || tx.outputStates[1] is AuctionState))

                    val auctionState = if (tx.outputStates[0] is AuctionState) tx.outputStates[0] as AuctionState else tx.outputStates[1] as AuctionState
                    val auctionItemState = if (tx.outputStates[0] is AuctionItemState) tx.outputStates[0] as AuctionItemState else tx.outputStates[1] as AuctionItemState
                    "A newly issued auction must have a positive price." using (auctionState.price.quantity > 0)
                    val time = timeWindow?.fromTime
                                        ?: throw IllegalArgumentException("Listings must be timestamped")
                    "The expiry date cannot be in the past" using (time < auctionState.expiry)
                    "Only the seller needs to sign the transaction" using (signers == setOf(auctionState.seller.owningKey))
                    "The auction must have no bidder on creation" using (auctionState.bidder == null)

                     "Only the owner of the auction item can list it in an auction" using (auctionItemState.owner == auctionState.seller)
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
                    "The new bid price must be greater than the existing one" using (outputState.price > inputState.price)
                    "A bidder party must exist" using (outputState.bidder != null)
                    "The seller, old bidder and new bidder only must sign a bid transaction" using (signers == listOfNotNull(outputState.seller.owningKey,
                            requireNotNull(outputState.bidder).owningKey, inputState.bidder?.owningKey).toSet())
                }
            }
        }

        class Settle: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    val states = tx.groupStates<AuctionState, UniqueIdentifier> { it.linearId }.single()
                    "There must be one input state." using (1 == states.inputs.size)
                    "Input state must be an AuctionState" using (tx.inputStates.single() is AuctionState)
                    val inputState = tx.inputStates.single() as AuctionState
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Settlements must be timestamped")
                    "Cannot settle an auction before it expires" using (time > inputState.expiry)
                    if (inputState.bidder != null) {
                        val cashOutputs = tx.outputsOfType(Cash.State::class.java)
                        "There must be output cash." using cashOutputs.isNotEmpty()
                        val sellerCashAmount = cashOutputs.filter { it.owner == inputState.seller }
                        "There must be output cash paid to the seller." using (sellerCashAmount.isNotEmpty())
                        val settled = sellerCashAmount.sumCash().withoutIssuer()
                        val total = inputState.price
                        "The amount settled must be equal to the price of the auction." using (total.compareTo(settled) == 0)
                        "There must be no output state as it has been settled." using (states.outputs.isEmpty())
                        "Both seller and bidder together only must sign the auction settlement transaction." using
                                (signers == listOfNotNull(inputState.seller.owningKey,
                                        inputState.bidder?.owningKey).toSet())
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