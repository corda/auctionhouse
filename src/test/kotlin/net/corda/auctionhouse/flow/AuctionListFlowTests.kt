package net.corda.auctionhouse.flow

import net.corda.auctionhouse.contract.AuctionContract
import net.corda.auctionhouse.contract.AuctionItemContract
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.core.singleIdentityAndCert
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.*
import org.junit.*
import java.lang.IllegalArgumentException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions Flows part 1.
 * Uncomment the unit tests and use the hints + unit test body to complete the Flows such that the unit tests pass.
 * Note! These tests rely on Quasar to be loaded, set your run configuration to "-ea -javaagent:lib/quasar.jar"
 * Run configuration can be edited in IntelliJ under Run -> Edit Configurations -> VM options
 * On some machines/configurations you may have to provide a full path to the quasar.jar file.
 * On some machines/configurations you may have to use the "JAR manifest" option for shortening the command line.
 */
class AuctionListFlowTests {
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

    @Test
    fun flowReturnsCorrectlyFormedSignedTransaction() {
        val owner = a
        val item = issueAuctionItem(owner)
        val flow = AuctionListFlow(item, 10.POUNDS, Instant.now().plusSeconds(3600))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        // Check the transaction is well formed...
        // No outputs, one input AuctionState and a command with the right properties.
        assert(stx.tx.inputs.size == 1)
        assert(stx.tx.outputs.size == 2)
        assert(stx.tx.outputs[0].data is AuctionItemState || stx.tx.outputs[1].data is AuctionItemState)
        assert(stx.tx.outputs[0].data is AuctionState || stx.tx.outputs[1].data is AuctionState)
        assert(stx.tx.commands.size == 2)
        assert(stx.tx.commands[0].value is AuctionContract.Commands.List || stx.tx.commands[1].value is AuctionContract.Commands.List)
        assert(stx.tx.commands[0].value is AuctionItemContract.Commands.List || stx.tx.commands[1].value is AuctionItemContract.Commands.List)
        stx.tx.commands.forEach { assert(it.signers.toSet() == setOf(a.info.singleIdentityAndCert().owningKey))}
        stx.verifyRequiredSignatures()
    }


    @Test
    fun flowReturnsVerifiedSignedTransaction() {
        val owner = a
        val someoneElse = b
        val item = issueAuctionItem(owner)
        // Check that only the owner of an item can list it in an auction
        val futureOne = someoneElse.startFlow(AuctionListFlow(item, 10.POUNDS, Instant.now().plusSeconds(3600)))
        mockNetwork.runNetwork()
        assertFailsWith<IllegalArgumentException> { futureOne.getOrThrow() }
        // Check that an Auction with expiry in the past fails.
        val futureTwo = owner.startFlow(AuctionListFlow(item, 10.POUNDS, Instant.now().minusSeconds(3600)))
        mockNetwork.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureTwo.getOrThrow() }
        val futureThree = owner.startFlow(AuctionListFlow(item, 10.POUNDS, Instant.now().plusSeconds(3600)))
        mockNetwork.runNetwork()
        futureThree.getOrThrow()
    }

    @Test
    fun flowRecordsTheTransactionInTheSellersVault() {
        val owner = a
        val item = issueAuctionItem(owner)
        val flow = AuctionListFlow(item, 10.POUNDS, Instant.now().plusSeconds(3600))
        val future = a.startFlow(flow)
        mockNetwork.runNetwork()
        val stx = future.getOrThrow()
        listOf(owner).map {
            it.services.validatedTransactions.getTransaction(stx.id)
        }.forEach {
                    val txHash = (it as SignedTransaction).id
                    println("$txHash == ${stx.id}")
                    assertEquals(stx.id, txHash)
        }
    }
}
