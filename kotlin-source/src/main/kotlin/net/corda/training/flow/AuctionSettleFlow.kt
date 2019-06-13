package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.internal.requiredContractClassName
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.workflows.getCashBalance
import net.corda.training.contract.AuctionContract
import net.corda.training.state.AuctionState
import java.lang.IllegalArgumentException
import java.util.*

/**
 * This is the flow which handles the (partial) settlement of existing IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class AuctionSettleFlow(val linearId: UniqueIdentifier, val amount: Amount<Currency>): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val ious = serviceHub.vaultService.queryBy(AuctionState::class.java)
        val stateAndRef = requireNotNull(ious.states.find { it.state.data.linearId == linearId })
        val state = stateAndRef.state.data
        val builder = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        if (state.seller != ourIdentity) {
            throw IllegalArgumentException()
        }

        val token = state.price.token
        val currency = token.currencyCode
        val borrowerBalance = serviceHub.getCashBalance(token)
        if (borrowerBalance.quantity <= 0) {
            throw IllegalArgumentException("Borrower has no $currency to settle.")
        }

        if (borrowerBalance < amount) {
            throw IllegalArgumentException("Borrower has only ${borrowerBalance.toDecimal()} $currency but needs ${amount.toDecimal()} $currency to settle.")
        }

        //CashUtils.generateSpend(serviceHub, builder, listOf(PartyAndAmount(state.bidder, amount)), ourIdentityAndCert)

        val outputState = state

        builder.withItems(stateAndRef, StateAndContract(outputState, requireNotNull(outputState.requiredContractClassName)), Command(AuctionContract.Commands.Settle(), state.participants.map { it.owningKey }))

        val session = initiateFlow(state.seller)
        val ptx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(session)))
        return subFlow(FinalityFlow(stx, session))
    }
}

/**
 * This is the flow which signs IOU settlements.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(AuctionSettleFlow::class)
class IOUSettleFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val outputStates = stx.tx.outputs.map { it.data::class.java.name }.toList()
                "There must be an IOU transaction." using (outputStates.contains(AuctionState::class.java.name))
            }
        }

        subFlow(signedTransactionFlow)
        subFlow(ReceiveFinalityFlow(flowSession))
    }
}

@InitiatingFlow
@StartableByRPC
/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/training purposes!
 */
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {
    @Suspendable
    override fun call(): Cash.State {
        /** Create the cash issue command. */
        val issueRef = OpaqueBytes.of(0)
        /** Note: ongoing work to support multiple notary identities is still in progress. */
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        /** Create the cash issuance transaction. */
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, notary))
        /** Return the cash output. */
        return cashIssueTransaction.stx.tx.outputs.single().data as Cash.State
    }
}