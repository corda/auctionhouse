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
 * An Auction contract commits that:
 *   - Listings, Bids and settlements must be timestamped.
 *   - The seller owns the item listed in the auction
 *   - The seller has not listed the item more than once
 *   - The seller cannot bid on their own auction
 *   - The bidder must bid higher than the current highest bid
 *   - The bidder cannot outbid themselves.
 *   - The bidder cannot bid on an expired auction.
 *   - The seller cannot list an expired auction.
 *   - The highest bidder must have enough cash in the correct currency to
 *     pay the seller in full.
 *   - The auctioned item's ownership must be transferred to the highest bidder
 *     upon settlement.
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
                    "One input should be consumed when listing an auction." using (1 == tx.inputStates.size)
                    "The input state type should be AuctionItemState" using (tx.inputStates.single() is AuctionItemState)
                    "Only two output states should be created when listing an auction." using (2 == tx.outputStates.size)
                    val auctionState = tx.outputsOfType(AuctionState::class.java).single()
                    val auctionItemStateOut = tx.outputsOfType(AuctionItemState::class.java).single()
                    val auctionItemStateIn = tx.inputsOfType(AuctionItemState::class.java).single()
                    "The input item state must not be listed" using (!auctionItemStateIn.listed)
                    "The output item state must be listed" using (auctionItemStateOut.listed)
                    "A newly issued auction must have a positive price." using (auctionState.price.quantity > 0)
                    val time = timeWindow?.fromTime
                                        ?: throw IllegalArgumentException("Listings must be timestamped")
                    "The expiry date cannot be in the past" using (time < auctionState.expiry)
                    "Only the seller needs to sign the transaction" using (signers == setOf(auctionState.seller.owningKey))
                    "The auction must have no bidder on creation" using (auctionState.bidder == null)
                    "Only the 'listed' property of the auction item has changed" using (auctionItemStateOut.copy(listed = false) == auctionItemStateIn)
                     "Only the owner of the auction item can list it in an auction" using (auctionItemStateIn.owner == auctionState.seller)
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
                    "There must be only one AuctionItemState input" using (tx.inputsOfType(AuctionItemState::class.java).size == 1)
                    "There must be only one AuctionState input" using (tx.inputsOfType(AuctionState::class.java).size == 1)
                    "There must be only one AuctionItemState output" using (tx.outputsOfType(AuctionItemState::class.java).size == 1)
                    val inputAuction = tx.inputsOfType(AuctionState::class.java).single()
                    "There must not be any AuctionState outputs" using tx.outputsOfType(AuctionState::class.java).none()
                    val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Settlements must be timestamped")
                    "Cannot settle an auction before it expires" using (time > inputAuction.expiry)
                    if (inputAuction.bidder != null) {
                        val cashOutputs = tx.outputsOfType(Cash.State::class.java)
                        "There must be output cash." using cashOutputs.isNotEmpty()
                        val sellerCashAmount = cashOutputs.filter { it.owner == inputAuction.seller }
                        "There must be output cash paid to the seller." using (sellerCashAmount.isNotEmpty())
                        val settled = sellerCashAmount.sumCash().withoutIssuer()
                        val total = inputAuction.price
                        "The amount settled must be equal to the price of the auction." using (total.compareTo(settled) == 0)
                        "Both seller and bidder together only must sign the auction settlement transaction." using
                                (signers == listOf(inputAuction.seller.owningKey, inputAuction.bidder.owningKey).toSet())
                    } else {
                        "Only the seller must sign the auction settlement transaction." using (signers == setOf(inputAuction.seller.owningKey))
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