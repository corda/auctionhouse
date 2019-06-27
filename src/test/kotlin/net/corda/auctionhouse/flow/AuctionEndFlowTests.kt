package net.corda.auctionhouse.flow

import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.*
import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.core.internal.packageName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CashSchemaV1
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.*
import java.lang.Long.max
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions Flows part 3.
 * Uncomment the unit tests and use the hints + unit test body to complete the FLows such that the unit tests pass.
 */
class AuctionEndFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.auctionhouse", "net.corda.finance.contracts.asset", CashSchemaV1::class.packageName),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(AuctionListFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(AuctionSettleFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    /**
     * Issue an auction item on the ledger. We need to do this before we can list an auction.
     */
    private fun issueAuctionItem(node: StartedMockNode): UniqueIdentifier {
        val flow = AuctionItemSelfIssueFlow("diamond ring")
        val future = node.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * List an auction on the ledger. We need to do this before we can bid on it.
     */
    private fun listAuction(node: StartedMockNode, amount: Amount<Currency>, expiry: Instant): SignedTransaction {
        val auctionItem = issueAuctionItem(node)
        val flow = AuctionListFlow(auctionItem, amount, expiry)
        val future = node.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Issue some on-ledger cash to ourselves, we need to do this before we can Settle an IOU.
     */
    private fun issueCash(node: StartedMockNode, amount: Amount<Currency>): Cash.State {
        val flow = SelfIssueCashFlow(amount)
        val future = node.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    @Test
    fun flowReturnsCorrectlyFormedSignedTransaction() {
        val seller = a
        val bidder = b
        val auctionId = listAuction(seller, 1.POUNDS, Instant.now().plusSeconds(3600)).tx.outputsOfType<AuctionState>().single().linearId
        val futureBid = bidder.startFlow(AuctionBidFlow(auctionId, 2.POUNDS))
        mockNetwork.runNetwork()
        val stx = futureBid.getOrThrow()
        assert(stx.tx.outputs.single().data is AuctionState)
        val flow = AuctionEndFlow(auctionId)
        val futureEnd = seller.startFlow(flow)
        mockNetwork.runNetwork()
        val endResult = futureEnd.getOrThrow()
        seller.transaction {
            val ledgerTx = endResult.toLedgerTransaction(bidder.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 1)
            val inputAuctionItem = ledgerTx.inputsOfType(AuctionItemState::class.java).single()
            assert(ledgerTx.inputsOfType(AuctionState::class.java).size == 1)
            val outputAuctionItem = ledgerTx.outputsOfType(AuctionItemState::class.java).single()
            assertEquals(outputAuctionItem, inputAuctionItem.delist())
            val auctionCommand = ledgerTx.commands.requireSingleCommand<AuctionContract.Commands>()
            assert(auctionCommand.value == AuctionContract.Commands.End())
            val auctionItemCommand = ledgerTx.commands.requireSingleCommand<AuctionItemContract.Commands>()
            assert(auctionItemCommand.value == AuctionItemContract.Commands.Delist())
            endResult.verifyRequiredSignatures()
        }
    }

    @Test
    fun flowWithoutBidderReturnsCorrectlyFormedSignedTransaction() {
        val seller = a
        val auctionId = listAuction(seller, 1.POUNDS, Instant.now().plusSeconds(3600)).tx.outputsOfType<AuctionState>().single().linearId
        val flow = AuctionEndFlow(auctionId)
        val futureEnd = seller.startFlow(flow)
        mockNetwork.runNetwork()
        val endResult = futureEnd.getOrThrow()
        seller.transaction {
            val ledgerTx = endResult.toLedgerTransaction(seller.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 1)
            val inputAuctionItem = ledgerTx.inputsOfType(AuctionItemState::class.java).single()
            assert(ledgerTx.inputsOfType(AuctionState::class.java).size == 1)
            val outputAuctionItem = ledgerTx.outputsOfType(AuctionItemState::class.java).single()
            assertEquals(outputAuctionItem, inputAuctionItem.delist())
            val auctionCommand = ledgerTx.commands.requireSingleCommand<AuctionContract.Commands>()
            assert(auctionCommand.value == AuctionContract.Commands.End())
            val auctionItemCommand = ledgerTx.commands.requireSingleCommand<AuctionItemContract.Commands>()
            assert(auctionItemCommand.value == AuctionItemContract.Commands.Delist())
            endResult.verifyRequiredSignatures()
        }
    }

    @Test
    fun endFlowCanOnlyBeRunByTheSeller() {
        val seller = a
        val bidder = b
        val someoneElse = c
        val auctionId = listAuction(seller, 1.POUNDS, Instant.now().plusSeconds(3600)).tx.outputsOfType<AuctionState>().single().linearId
        val futureBid = bidder.startFlow(AuctionBidFlow(auctionId, 2.POUNDS))
        mockNetwork.runNetwork()
        futureBid.getOrThrow()
        setOf(bidder, someoneElse).forEach {
            val flow = AuctionEndFlow(auctionId)
            val future = it.startFlow(flow)
            mockNetwork.runNetwork()
            assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
        }
    }

    @Test
    fun endFlowCanBeRunAfterAuctionExpires() {
        val seller = a
        val bidder = b
        val auctionId = listAuction(seller, 1.POUNDS, Instant.now().plusSeconds(30)).tx.outputsOfType<AuctionState>().single().linearId
        val futureBid = bidder.startFlow(AuctionBidFlow(auctionId, 2.POUNDS))
        mockNetwork.runNetwork()
        val stx = futureBid.getOrThrow()
        assert(stx.tx.outputs.single().data is AuctionState)
        val inputAuction = stx.tx.outputsOfType(AuctionState::class.java).single()
        val wait = inputAuction.expiry.toEpochMilli() - Instant.now().toEpochMilli()
        Thread.sleep(max(wait, 0L))
        val flow = AuctionEndFlow(auctionId)
        val futureEnd = seller.startFlow(flow)
        mockNetwork.runNetwork()
        val endResult = futureEnd.getOrThrow()
        seller.transaction {
            val ledgerTx = endResult.toLedgerTransaction(bidder.services, false)
            assert(ledgerTx.inputs.size == 2)
            assert(ledgerTx.outputs.size == 1)
            val inputAuctionItem = ledgerTx.inputsOfType(AuctionItemState::class.java).single()
            assert(ledgerTx.inputsOfType(AuctionState::class.java).size == 1)
            val outputAuctionItem = ledgerTx.outputsOfType(AuctionItemState::class.java).single()
            assertEquals(outputAuctionItem, inputAuctionItem.delist())
            val auctionCommand = ledgerTx.commands.requireSingleCommand<AuctionContract.Commands>()
            assert(auctionCommand.value == AuctionContract.Commands.End())
            val auctionItemCommand = ledgerTx.commands.requireSingleCommand<AuctionItemContract.Commands>()
            assert(auctionItemCommand.value == AuctionItemContract.Commands.Delist())
            endResult.verifyRequiredSignatures()
        }
    }
}
