package net.corda.auctionhouse.contract

import net.corda.auctionhouse.state.AuctionItemState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.auctionhouse.state.AuctionState
import java.security.PublicKey

/**
 * Transactions involving one or more AuctionStates will use this contract to
 * verify that the transactions are valid.
 *
 */
class AuctionContract : Contract {
    companion object {
        @JvmStatic
        val AUCTION_CONTRACT_ID = "net.corda.auctionhouse.contract.AuctionContract"
    }

    /**
     * Adding more commands will require implementation of the corresponding
     * verification method.
     */
    interface Commands : CommandData {
        fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>)

        class List : TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    "Only one input should be consumed when listing an auction" using (1 == tx.inputStates.size)
                    "The input state type should be AuctionItemState" using (tx.inputStates.single() is AuctionItemState)
                    "Only two output states should be created when listing an auction" using (2 == tx.outputStates.size)
                    val auctionState = tx.outputsOfType(AuctionState::class.java).single()
                    val auctionItemStateIn = tx.inputsOfType(AuctionItemState::class.java).single()
                    "A newly issued auction must have a starting price greater than zero" using (auctionState.price.quantity > 0)
                    val time = timeWindow?.fromTime
                                        ?: throw IllegalArgumentException("Auction listings must be timestamped")
                    "The expiry date cannot be in the past" using (time < auctionState.expiry)
                    "Only the seller needs to sign the transaction" using (signers == setOf(auctionState.seller.owningKey))
                    "The auction must have no bidder when listed" using (auctionState.bidder == null)
                    "Only the owner of the auction item can list it in an auction" using (auctionItemStateIn.owner == auctionState.seller)
                }
            }
        }

        class Bid: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Auction bids must be timestamped")
                    "A bid transaction should only have one input state" using (1 == tx.inputStates.size)
                    "A bid transaction should only have one output state" using (1 == tx.outputStates.size)
                    "The input state must be an AuctionState" using (tx.inputStates.single() is AuctionState)
                    "The output state must be an AuctionState" using (tx.outputStates.single() is AuctionState)
                    val inputState = tx.inputStates.single() as AuctionState
                    val outputState = tx.outputStates.single() as AuctionState
                    "The auction must not be expired" using (time < inputState.expiry)
                    "The bidder cannot be 'null'" using (outputState.bidder != null)
                    "Only the 'bidder' and 'price' may change" using (inputState == outputState.copy(bidder = inputState.bidder, price = inputState.price))
                    "The 'bidder' property must change in a bid" using (outputState.bidder != inputState.bidder)
                    "The 'price' property must change in a bid" using (outputState.price != inputState.price)
                    "The new bid price must be greater than the current bid price" using (outputState.price > inputState.price)
                    "The seller, previous bidder and new bidder only must sign a bid transaction" using (signers == listOfNotNull(outputState.seller.owningKey,
                            requireNotNull(outputState.bidder).owningKey, inputState.bidder?.owningKey).toSet())
                }
            }
        }

        class Settle: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    "There must be only one AuctionState input" using (tx.inputsOfType(AuctionState::class.java).size == 1)
                    val inputAuction = tx.inputsOfType(AuctionState::class.java).single()
                    "There must not be any AuctionState outputs" using tx.outputsOfType(AuctionState::class.java).none()
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Auction settlements must be timestamped")
                    "Auction cannot be settled before it expires" using (time > inputAuction.expiry)
                    if (inputAuction.bidder != null) {
                        val cashOutputs = tx.outputsOfType(Cash.State::class.java)
                        "There must be output cash." using cashOutputs.isNotEmpty()
                        val sellerCashAmount = cashOutputs.filter { it.owner == inputAuction.seller }
                        "There must be output cash paid to the seller" using (sellerCashAmount.isNotEmpty())
                        val settled = sellerCashAmount.sumCash().withoutIssuer()
                        val total = inputAuction.price
                        "The amount settled must be equal to the price of the auction" using (total.compareTo(settled) == 0)
                        "Both seller and bidder only must sign the auction settlement transaction" using
                                (signers == setOf(inputAuction.seller.owningKey, inputAuction.bidder.owningKey))
                    } else {
                        "Only the seller must sign the auction settlement transaction" using (signers == setOf(inputAuction.seller.owningKey))
                    }
                }
            }
        }

        class End: TypeOnlyCommandData(), Commands {
            override fun verifyCommand(tx: LedgerTransaction, signers: Set<PublicKey>) {
                val timeWindow: TimeWindow? = tx.timeWindow
                requireThat {
                    "There must be only one AuctionState input" using (tx.inputsOfType(AuctionState::class.java).size == 1)
                    val inputAuction = tx.inputsOfType(AuctionState::class.java).single()
                    "There must not be any AuctionState outputs" using tx.outputsOfType(AuctionState::class.java).none()
                    timeWindow?.fromTime ?: throw IllegalArgumentException("Transaction must be timestamped")
                    if (inputAuction.bidder != null) {
                        "Both seller and bidder only must sign the auction settlement transaction" using
                                (signers == listOf(inputAuction.seller.owningKey, inputAuction.bidder.owningKey).toSet())
                    } else {
                        "Only the seller must sign the auction settlement transaction" using (signers == setOf(inputAuction.seller.owningKey))
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