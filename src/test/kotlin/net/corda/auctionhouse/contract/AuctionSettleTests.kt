package net.corda.auctionhouse.contract

import net.corda.auctionhouse.ALICE
import net.corda.auctionhouse.BOB
import net.corda.auctionhouse.DummyContract
import net.corda.auctionhouse.MEGACORP
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TimeWindow
import net.corda.core.internal.packageName
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.issuedBy
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.node.ledger
import net.corda.testing.node.MockServices

import org.junit.Test

import java.time.Instant

class AuctionSettleTests {

    private val defaultIssuer = MEGACORP.ref(1.toByte())

    // A dummy command.
    class DummyCommand : CommandData

    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    @Test
    fun onlyOneAuctionStateInput() {
        val expiry = Instant.now().minusSeconds(3600)
        val item = AuctionItemState("diamond ring", ALICE.party, listed = true)
        val auction = AuctionState(item.linearId, 5000.POUNDS, ALICE.party, BOB.party, expiry)

        val inCashState = Cash.State(POUNDS(5000).issuedBy(defaultIssuer), BOB.party)
        val outCashState = Cash.State(inCashState.amount, ALICE.party)

        ledgerServices.ledger {
            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_CONTRACT_ID, auction)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                this `fails with` "There must be only one AuctionState input"
            }

            transaction {
                input(AUCTION_CONTRACT_ID, auction)
                input(AUCTION_ITEM_CONTRACT_ID, item)
                input(Cash.PROGRAM_ID, inCashState)
                output(AUCTION_ITEM_CONTRACT_ID, item.transfer(BOB.party))
                output(Cash.PROGRAM_ID, outCashState)
                timeWindow(TimeWindow.fromOnly(Instant.now()))
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionContract.Commands.Settle())
                command(listOf(ALICE.publicKey, BOB.publicKey), AuctionItemContract.Commands.Transfer())
                command(listOf(ALICE.publicKey, BOB.publicKey), Cash.Commands.Move())
                this.verifies()
            }


        }
    }

}