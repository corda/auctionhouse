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

    // TODO
}
