package net.corda.auctionhouse.contract

import net.corda.auctionhouse.*
import net.corda.auctionhouse.contract.AuctionContract.Companion.AUCTION_CONTRACT_ID
import net.corda.auctionhouse.contract.AuctionItemContract.Companion.AUCTION_ITEM_CONTRACT_ID
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
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

class AuctionEndTests {

    private val defaultIssuer = MEGACORP.ref(1.toByte())

    var ledgerServices = MockServices(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName))

    // TODO: Add tests for the auction end command
}
