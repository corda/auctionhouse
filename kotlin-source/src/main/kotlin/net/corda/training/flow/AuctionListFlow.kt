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
import java.time.Instant
import java.util.*

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AuctionListFlow(val amount: Amount<Currency>,
                      val lender: Party,
                      val borrower: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val state = AuctionState(amount, lender, borrower, Instant.MAX)
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
                .withItems(Command(AuctionContract.Commands.List(), state.participants.map { it.owningKey }),
                        StateAndContract(state, requireNotNull(state.requiredContractClassName)))
        builder.verify(serviceHub)
        val us = ourIdentity
        val everyoneElse = state.participants.filterNot { it == us }

        val flowSessions = everyoneElse.map { initiateFlow(it) }

        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, flowSessions))
        val signed = subFlow(FinalityFlow(stx, flowSessions))
        return signed
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(AuctionListFlow::class)
class IOUIssueFlowResponder(val flowSession: FlowSession) : FlowLogic<Unit>() {
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