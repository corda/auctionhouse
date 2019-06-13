package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.requiredContractClassName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.AuctionContract
import net.corda.training.state.AuctionState
import java.util.*

/**
 * This is the flow which handles transfers of existing IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AuctionBidFlow(val linearId: UniqueIdentifier, val newBidder: Party, val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val ious = serviceHub.vaultService.queryBy(AuctionState::class.java)

        val stateAndRef = requireNotNull(ious.states.find { it.state.data.linearId == linearId })

        val state = stateAndRef.state.data

        if (ourIdentity != state.seller) {
            throw IllegalArgumentException()
        }

        val newState = state.bid(amount, newBidder)

        val everyoneElse = newState.participants.filterNot { it == ourIdentity }
        val flowSessions = everyoneElse.map { initiateFlow(it) }


        val builder = TransactionBuilder(notary = stateAndRef.state.notary)
        val command = Command(AuctionContract.Commands.Bid(), state.participants.map { it.owningKey } + newBidder.owningKey)
        builder.withItems(stateAndRef, StateAndContract(newState, requireNotNull(newState.requiredContractClassName)), command)
        builder.verify(serviceHub)

        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, flowSessions))
        return subFlow(FinalityFlow(stx, flowSessions))
    }
}

/**
 * This is the flow which signs IOU transfers.
 * The signing is handled by the [SignTransactionFlow].
 */
//@InitiatedBy(AuctionBidFlow::class)
class IOUTransferFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is AuctionState)
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession))
    }
}