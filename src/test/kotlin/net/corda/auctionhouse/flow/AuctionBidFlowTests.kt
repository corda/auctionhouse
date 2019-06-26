package net.corda.auctionhouse.flow

import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalArgumentException
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith

class AuctionBidFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.auctionhouse"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(AuctionListFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(AuctionBidFlowResponder::class.java) }
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
    private fun listAuction(node: StartedMockNode, amount: Amount<Currency>, expiry: Instant): UniqueIdentifier {
        val auctionItem = issueAuctionItem(node)
        val flow = AuctionListFlow(auctionItem, amount, expiry)
        val future = node.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }


    @Test
    fun flowReturnsCorrectlyFormedSignedTransaction() {
        val seller = a
        val bidder = b
        val auctionId = listAuction(seller, 1000.POUNDS, Instant.now().plusSeconds(3600))
        val flow = AuctionBidFlow(auctionId, 1200.POUNDS)
        val future = bidder.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        // Check the transaction is well formed...
        // One output AuctionState, one input state reference and a Transfer command with the right properties.
        assert(stx.tx.inputs.size == 1)
        assert(stx.tx.outputs.size == 1)
        val inputAuction = a.services.toStateAndRef<AuctionState>(stx.tx.inputs.single()).state
        println("Input state: ${inputAuction.data}")
        val outputAuction = stx.tx.outputs.single().data as AuctionState
        println("Output state: $outputAuction")
        val command = stx.tx.commands.single()
        assert(command.value == AuctionContract.Commands.Bid())
        stx.verifyRequiredSignatures()
    }

    @Test
    fun flowCannotBeRunByCurrentBidder() {
        val seller = a
        val bidder = b
        val auctionId = listAuction(seller, 1000.POUNDS, Instant.now().plusSeconds(3600))
        var flow = AuctionBidFlow(auctionId, 1200.POUNDS)
        var future = bidder.startFlow(flow)
        mockNetwork.runNetwork()
        future.getOrThrow()
        // Now the bidder will try and initiate the flow again and fail
        flow = AuctionBidFlow(auctionId, 1300.POUNDS)
        future = bidder.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun flowCannotBeRunBySeller() {
        val seller = a
        val auctionId = listAuction(seller, 1000.POUNDS, Instant.now().plusSeconds(3600))
        val flow = AuctionBidFlow(auctionId, 1200.POUNDS)
        val future = seller.startFlow(flow)
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

}
