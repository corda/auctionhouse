package net.corda.auctionhouse.flow

import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

/**
 * Practical exercise instructions Flows part 2.
 * Uncomment the unit tests and use the hints + unit test body to complete the Flows such that the unit tests pass.
 */
class AuctionBidFlowTests {
    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    lateinit var c: StartedMockNode

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("net.corda.auctionhouse"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        c = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b, c)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(AuctionListFlowResponder::class.java) }
        startedNodes.forEach { it.registerInitiatedFlow(AuctionBidFlowResponder::class.java) }
        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

//    /**
//     * Issue an IOU on the ledger, we need to do this before we can transfer one.
//     */
//    private fun issueIou(auction: AuctionState): SignedTransaction {
//        val flow = AuctionListFlow(auction.amount, auction.lender, auction.borrower)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        return future.getOrThrow()
//    }
//
//    /**
//     * Task 1.
//     * Build out the beginnings of [AuctionBidFlow]!
//     * TODO: Implement the [AuctionBidFlow] flow which builds and returns a partially [SignedTransaction].
//     * Hint:
//     * - This flow will look similar to the [AuctionListFlow].
//     * - This time our transaction has an input state, so we need to retrieve it from the vault!
//     * - You can use the [serviceHub.vaultService.queryBy] method to get the latest linear states of a particular
//     *   type from the vault. It returns a list of states matching your query.
//     * - Use the [UniqueIdentifier] which is passed into the flow to retrieve the correct [AuctionState].
//     * - Use the [AuctionState.withNewLender] method to create a copy of the state with a new lender.
//     * - Create a Command - we will need to use the Transfer command.
//     * - Remember, as we are involving three parties we will need to collect three signatures, so need to add three
//     *   [PublicKey]s to the Command's signers list. We can get the signers from the input IOU and the new IOU you
//     *   have just created with the new lender.
//     * - Verify and sign the transaction as you did with the [AuctionListFlow].
//     * - Return the partially signed transaction.
//     */
//    @Test
//    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val stx = issueIou(AuctionState(10.POUNDS, lender, borrower))
//        val inputIou = stx.tx.outputs.single().data as AuctionState
//        val flow = AuctionBidFlow(inputIou.auctionId, c.info.chooseIdentityAndCert().party)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        val ptx = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output AuctionState, one input state reference and a Transfer command with the right properties.
//        assert(ptx.tx.inputs.size == 1)
//        assert(ptx.tx.outputs.size == 1)
//        assert(ptx.tx.inputs.single() == StateRef(stx.id, 0))
//        println("Input state ref: ${ptx.tx.inputs.single()} == ${StateRef(stx.id, 0)}")
//        val outputIou = ptx.tx.outputs.single().data as AuctionState
//        println("Output state: $outputIou")
//        val command = ptx.tx.commands.single()
//        assert(command.value == AuctionContract.Commands.Transfer())
//        ptx.verifySignaturesExcept(b.info.chooseIdentityAndCert().party.owningKey, c.info.chooseIdentityAndCert().party.owningKey,
//                mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
//    }
//
//    /**
//     * Task 2.
//     * We need to make sure that only the current lender can execute this flow.
//     * TODO: Amend the [AuctionBidFlow] to only allow the current lender to execute the flow.
//     * Hint:
//     * - Remember: You can use the node's identity and compare it to the [Party] object within the [IOUstate] you
//     *   retrieved from the vault.
//     * - Throw an [IllegalArgumentException] if the wrong party attempts to run the flow!
//     */
//    @Test
//    fun flowCanOnlyBeRunByCurrentLender() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val stx = issueIou(AuctionState(10.POUNDS, lender, borrower))
//        val inputIou = stx.tx.outputs.single().data as AuctionState
//        val flow = AuctionBidFlow(inputIou.auctionId, c.info.chooseIdentityAndCert().party)
//        val future = b.startFlow(flow)
//        mockNetwork.runNetwork()
//        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
//    }
//
//    /**
//     * Task 3.
//     * Check that an [AuctionState] cannot be transferred to the same lender.
//     * TODO: You shouldn't have to do anything additional to get this test to pass. Belts and Braces!
//     */
//    @Test
//    fun iouCannotBeTransferredToSameParty() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val stx = issueIou(AuctionState(10.POUNDS, lender, borrower))
//        val inputIou = stx.tx.outputs.single().data as AuctionState
//        val flow = AuctionBidFlow(inputIou.auctionId, lender)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        // Check that we can't transfer an IOU to ourselves.
//        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
//    }
//
//    /**
//     * Task 4.
//     * Get the borrowers and the new lenders signatures.
//     * TODO: Amend the [AuctionBidFlow] to handle collecting signatures from multiple parties.
//     * Hint: use [initiateFlow] and the [CollectSignaturesFlow] in the same way you did for the [AuctionListFlow].
//     */
//    @Test
//    fun flowReturnsTransactionSignedByAllParties() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val stx = issueIou(AuctionState(10.POUNDS, lender, borrower))
//        val inputIou = stx.tx.outputs.single().data as AuctionState
//        val flow = AuctionBidFlow(inputIou.auctionId, c.info.chooseIdentityAndCert().party)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        future.getOrThrow().verifySignaturesExcept(mockNetwork.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
//    }
//
//    /**
//     * Task 5.
//     * We need to get the transaction signed by the notary service
//     * TODO: Use a subFlow call to the [FinalityFlow] to get a signature from the lender.
//     */
//    @Test
//    fun flowReturnsTransactionSignedByAllPartiesAndNotary() {
//        val lender = a.info.chooseIdentityAndCert().party
//        val borrower = b.info.chooseIdentityAndCert().party
//        val stx = issueIou(AuctionState(10.POUNDS, lender, borrower))
//        val inputIou = stx.tx.outputs.single().data as AuctionState
//        val flow = AuctionBidFlow(inputIou.auctionId, c.info.chooseIdentityAndCert().party)
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        future.getOrThrow().verifyRequiredSignatures()
//    }
}
