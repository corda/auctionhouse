![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Auction House CorDapp

This repo contains an example CorDapp that implements a decentralised auction house. Nodes can issue auction items
themselves which they can then list in an auction. All auctions are public and are registered in all node's vaults.
Nodes other than the seller node can bid on the auction before it expires. When it expires the auction will
automatically settle. If settlement is not possible due to contract verification failure or another issue then
the seller has the authority to end the auction in which case the auction item will be de-listed and the auction
itself will be removed.

# Setup

### Tools 
* JDK 1.8 latest version
* IntelliJ latest version (2017.1 or newer)
* git

After installing the required tools, clone or download a zip of this repository, and place it in your desired 
location.

### IntelliJ setup
* From the main menu, click `open` (not `import`!) then navigate to where you placed this repository.
* Click `File->Project Structure`, and set the `Project SDK` to be the JDK you downloaded (by clicking `new` and 
navigating to where the JDK was installed). Click `Okay`.
* Next, click `import` on the `Import Gradle Project` popup, leaving all options as they are. 
* If you do not see the popup: Navigate back to `Project Structure->Modules`, clicking the `+ -> Import` button,
navigate to and select the repository folder, select `Gradle` from the next menu, and finally click `Okay`, 
again leaving all options as they are.


### Running the tests
* Select `Unit tests` from the drop-down run configuration menu, and click the green play button.
* Individual tests can be run by clicking the green arrow in the line number column next to each test.
* When running flow tests you must add the following to your run / debug configuration in the VM options field. This enables us to use
* Quasar - a library that provides high-performance, lightweight threads.
* "-javaagent: /PATH_TO_FILE_FROM_ROOT_DIR/quasar.jar"

# CorDapp Files

### States

* Source: 
   * `src/main/kotlin/net/corda/auctionhouse/state/AuctionState.kt`
   * `src/main/kotlin/net/corda/auctionhouse/state/AuctionItemState.kt`

* Tests:
   * `src/test/kotlin/net/corda/auctionhouse/state/AuctionStateTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/state/AuctionItemStateTests.kt`

### Contracts

* Source: 
   * `src/main/kotlin/net/corda/auctionhouse/contract/AuctionContract.kt`
   * `src/main/kotlin/net/corda/auctionhouse/contract/AuctionItemContract.kt`

* Tests:
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionListTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionBidTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionEndTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionSettleTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionItemIssueTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionItemListTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionItemTransferTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/contract/AuctionItemDelistTests.kt`

### Flows

* Source:
   * `src/main/kotlin/net/corda/auctionhouse/flow/AuctionListFlow.kt`
   * `src/main/kotlin/net/corda/auctionhouse/flow/AuctionBidFlow.kt`
   * `src/main/kotlin/net/corda/auctionhouse/flow/AuctionEndFlow.kt`
   * `src/main/kotlin/net/corda/auctionhouse/flow/AuctionSettleFlow.kt`
   * `src/main/kotlin/net/corda/auctionhouse/flow/AuctionItemSelfIssueFlow.kt`

* Tests:
   * `src/test/kotlin/net/corda/auctionhouse/flow/AuctionListFlowTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/flow/AuctionBidFlowTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/flow/AuctionEndFlowTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/flow/AuctionSettleFlowTests.kt`
   * `src/test/kotlin/net/corda/auctionhouse/flow/AuctionItemSelfIssueFlowTests.kt`

# Running the CorDapp

### Terminal
Navigate to the root project folder and run `./gradlew deployNodes`, followed by `./build/node/runnodes`

### IntelliJ
With the project open, select `Node driver` from the drop-down run configuration menu, and click the green play button.

### Interacting with the CorDapp
Once all the three nodes have started up (look for `Webserver started up in XXX sec` in the terminal or IntelliJ ),
you can interact with the app via a web browser. 
* From a Node Driver configuration, look for `Starting webserver on address localhost:100XX` for the addresses. 

* From the terminal: Node A: `localhost:10009`, Node B: `localhost:10012`, Node C: `localhost:10015`.

To access the front-end gui for each node, navigate to `localhost:XXXX/web/auction/`

A Postman collection `Auction House.postman_collection.json` and environment `Auction House.postman_environment.json`
is provided with example requests.

## Troubleshooting
When running the flow tests, if you get a Quasar instrumentation error then add:

```-ea -javaagent:lib/quasar.jar```

to the VM args property in the default run configuration for JUnit in IntelliJ.
