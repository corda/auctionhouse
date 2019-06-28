package net.corda.auctionhouse.api

import net.corda.auctionhouse.flow.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.toX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.NodeInfo
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.workflows.getCashBalances
import net.corda.auctionhouse.state.AuctionItemState
import net.corda.auctionhouse.state.AuctionState
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.time.Instant
import java.util.Currency
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * This API is accessible from /api/auction. The endpoint paths specified below are relative to it.
 * We've defined a bunch of endpoints to deal with Auctions, Auction Items, Cash and the various
 * operations you can perform with them.
 */
@Path("auction")
class AuctionHouseApi(val rpcOps: CordaRPCOps) {
    private val me = rpcOps.nodeInfo().legalIdentities.first().name

    private fun X500Name.toDisplayString(): String = BCStyle.INSTANCE.toString(this)

    /** Helpers for filtering the network map cache. */
    private fun isNotary(nodeInfo: NodeInfo) = rpcOps.notaryIdentities().any { nodeInfo.isLegalIdentity(it) }

    private fun isMe(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.first().name == me
    private fun isNetworkMap(nodeInfo: NodeInfo) = nodeInfo.legalIdentities.single().name.organisation == "Network Map Service"

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to me.toString())

    /**
     * Returns all parties registered with the Network Map Service. These names can be used to look up identities
     * using the Identity Service.
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<String>> {
        return mapOf("peers" to rpcOps.networkMapSnapshot()
                .filter { isNotary(it).not() && isMe(it).not() && isNetworkMap(it).not() }
                .map { it.legalIdentities.first().name.toX500Name().toDisplayString() })
    }

    /**
     * Returns all active auctions.
     */
    @GET
    @Path("auctions")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAuctions(): List<StateAndRef<ContractState>> {
        return rpcOps.vaultQueryBy<AuctionState>().states
    }

    /**
     * Returns all auction items visible to the node. Auction
     * items are visible to the owner or have been listed in
     * a public auction.
     */
    @GET
    @Path("items")
    @Produces(MediaType.APPLICATION_JSON)
    fun getAuctionItems(): List<StateAndRef<ContractState>> {
        return rpcOps.vaultQueryBy<AuctionItemState>().states
    }


    /**
     * Displays all cash states that exist in the node's vault.
     */
    @GET
    @Path("cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCash(): List<StateAndRef<ContractState>> {
        // Filter by state type: Cash.
        return rpcOps.vaultQueryBy<Cash.State>().states
    }

    /**
     * Displays all cash states that exist in the node's vault.
     */
    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    // Display cash balances.
    fun getCashBalances() = rpcOps.getCashBalances()

    /**
     * Issues an auction item to the node and commits it to the node's vault.
     */
    @GET
    @Path("self-issue-item")
    fun issueItem(@QueryParam(value = "description") description: String): Response {
        // Create a new Auction state using the parameters given.
        try {
            // Start the AuctionListFlow. We block and waits for the flow to return.
            val result = rpcOps.startFlow(::AuctionItemSelfIssueFlow, description).returnValue.get()
            // Return the response.
            return Response
                    .status(Response.Status.CREATED)
                    .entity("Auction item id ${result.id} committed to the ledger.")
                    .build()
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }


    /**
     * List an item in a public auction.
     * @param item The item's unique id
     * @param amount The starting value of the auction
     * @param currency The currency of the [amount]
     * @param expiry The expiry date and time of the auction (e.g. 2019-06-25T09:00:00Z)
     */
    @GET
    @Path("list")
    fun listAuction(@QueryParam(value = "item") item: String,
                 @QueryParam(value = "amount") amount: Int,
                 @QueryParam(value = "currency") currency: String,
                 @QueryParam(value = "expiry") expiry: String): Response {

        val linearId = UniqueIdentifier.fromString(item)
        // Create a new Auction state using the parameters given.
        try {
            // Start the AuctionListFlow. We block and wait for the flow to return.
            val result = rpcOps.startFlow(::AuctionListFlow, linearId,
                    Amount(amount.toLong() * 100, Currency.getInstance(currency)), Instant.parse(expiry)).returnValue.get()
            val auctionId = result.tx.outputsOfType(AuctionState::class.java).single().linearId
            // Return the response.
            return Response
                    .status(Response.Status.CREATED)
                    .entity("Auction id $auctionId committed to the ledger.")
                    .build()
            // For the purposes of this demo app, we do not differentiate by exception type.
        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }

    /**
     * Bid on an active auction.
     * @param id The unique id of the auction.
     * @param amount The bidding amount.
     * @param currency The bidding currency.
     */
    @GET
    @Path("bid")
    fun bidOnAuction(@QueryParam(value = "id") id: String,
                     @QueryParam(value = "amount") amount: Int,
                     @QueryParam(value = "currency") currency: String): Response {
        val linearId = UniqueIdentifier.fromString(id)
        val bidPrice = Amount(amount.toLong() * 100, Currency.getInstance(currency))
        try {
            rpcOps.startFlow(::AuctionBidFlow, linearId, bidPrice).returnValue.get()
            return Response
                    .status(Response.Status.OK)
                    .entity("Bid of $bidPrice on auction $id committed to the ledger.")
                    .build()
        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }

    /**
     * Remove an auction.
     * @param id The unique id of the auction.
     */
    @GET
    @Path("remove")
    fun bidOnAuction(@QueryParam(value = "id") id: String): Response {
        val linearId = UniqueIdentifier.fromString(id)
        try {
            rpcOps.startFlow(::AuctionEndFlow, linearId).returnValue.get()
            return Response
                    .status(Response.Status.OK)
                    .entity("Auction $id removed from the ledger.")
                    .build()
        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }



    /**
     * Helper end-point to issue some cash to ourselves.
     */
    @GET
    @Path("self-issue-cash")
    fun selfIssueCash(@QueryParam(value = "amount") amount: Int,
                      @QueryParam(value = "currency") currency: String): Response {
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        try {
            val cashState = rpcOps.startFlow(::SelfIssueCashFlow, issueAmount).returnValue.get()
            return Response.status(Response.Status.CREATED).entity(cashState.toString()).build()

        } catch (e: Exception) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(e.message)
                    .build()
        }
    }
}