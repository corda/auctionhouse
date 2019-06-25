package net.corda.auctionhouse

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.TestIdentity

val ALICE = TestIdentity(CordaX500Name(organisation = "Alice", locality = "TestLand", country = "US"))
val BOB = TestIdentity(CordaX500Name(organisation = "Bob", locality = "TestCity", country = "US"))
var CHARLIE = TestIdentity(CordaX500Name(organisation = "Charlie", locality = "TestVillage", country = "US"))
val MINICORP = TestIdentity(CordaX500Name(organisation = "MiniCorp", locality = "MiniLand", country = "US"))
val MEGACORP = TestIdentity(CordaX500Name(organisation = "MegaCorp", locality = "MiniLand", country = "US"))
val DUMMY = TestIdentity(CordaX500Name(organisation = "Dummy", locality = "FakeLand", country = "US"))

class DummyContract : Contract {

    companion object {
        @JvmStatic
        val DUMMY_CONTRACT_ID = "net.corda.auctionhouse.contract.DummyContract"
    }

    override fun verify(tx: LedgerTransaction) {
        // Accept all
    }

    interface Commands : CommandData {
        class DummyCommand : TypeOnlyCommandData(), Commands
    }

    @BelongsToContract(DummyContract::class)
    data class DummyState(
            val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : ContractState {
        override val participants: List<AbstractParty>
            get() = listOf()
    }

}