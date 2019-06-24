package net.corda.auctionhouse.plugin

import net.corda.core.messaging.CordaRPCOps
import net.corda.auctionhouse.api.AuctionHouseApi
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class AuctionHousePlugin : WebServerPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::AuctionHouseApi))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     * The template's web frontend is accessible at /web/auction.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the auctionWeb directory in resources to /web/template
            "auction" to requireNotNull(javaClass.classLoader.getResource("auctionWeb")).toExternalForm()
    )
}