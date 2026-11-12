# auction-service

Auction service for Project Nexus: creating and managing auctions, placing bids (by hand or with a
standing maximum), the auction life cycle, and deciding the winner. Extracted from the original monorepo
into its own standalone Spring Boot project, per the course's polyrepo requirement (each microservice is its
own repo). Port `8083`, database `auction_db` (PostgreSQL), registers with Eureka as `auction-service`.

## What it does

An auction goes `PENDING` → `ACTIVE` → `ENDED`, or `CANCELLED`. A job (`AuctionLifecycleJob`, every 10
seconds) starts auctions whose start time has come and ends the ones whose end time has passed. When an
auction ends, the highest bidder wins at the highest bid and has 24 hours to pay. If nobody bid, or the bids
never reached the seller's hidden reserve price, it ends without a winner.

### Bidding rules

- The first bid must reach the starting price; later bids must be at least the highest bid plus the bid
  increment.
- The seller cannot bid on their own auction, and the current highest bidder cannot raise their own bid by hand
  (`409 ALREADY_HIGHEST_BIDDER`). Once someone else outbids them they can bid again. Setting a standing maximum
  is still allowed for the leader.
- A bid placed in the last 5 minutes moves the end time 5 minutes later (anti-sniping), at most 12 times. Only a
  bid a person sends extends the auction, not the bids the system places for standing maximums.
- Two bids at the same moment queue up on a row lock, so the second is checked against the first.
- Money amounts have at most 10 digits before the point and 2 after (the database column is `NUMERIC(12,2)`);
  anything else is `400 VALIDATION_ERROR`.
- The reserve price is never returned to bidders.

## API

All paths are under `/api/v1/auctions`. Reads are public; writes need a JWT with the privilege shown.

| Method | Path | Privilege | Purpose |
|---|---|---|---|
| GET | `/` | public | list and search auctions |
| GET | `/{id}` | public | one auction |
| POST | `/` | `AUCTION.CREATE` | create an auction (starts `PENDING`) |
| PUT | `/{id}` | `AUCTION.UPDATE` | change a `PENDING` auction |
| DELETE | `/{id}` | `AUCTION.CANCEL` | seller cancels a pending auction, or an active one with no bids |
| POST | `/{id}/admin-cancel` | `AUCTION.ADMIN_CANCEL` | admin cancels any auction |
| POST | `/{auctionId}/bids` | `AUCTION.BID` | place a bid |
| GET | `/{auctionId}/bids` | public | bid history (bidders are masked) |
| PUT, GET | `/{auctionId}/auto-bid` | `AUCTION.BID` | set or read your standing maximum |
| GET | `/{auctionId}/stream` | public | live bids as server-sent events |
| GET | `/mine/bidding` | `AUCTION.BID` | auctions I bid on |
| GET | `/mine/selling` | `AUCTION.CREATE` | auctions I sell |

Errors use the shared `ApiResponse` shape with an `error.code` such as `AUCTION_NOT_ACTIVE`, `AUCTION_ENDED`,
`SELLER_CANNOT_BID_OWN_AUCTION`, `ALREADY_HIGHEST_BIDDER` or `PRODUCT_ALREADY_IN_AUCTION`.

## Events

Written to an outbox table and relayed to Kafka every 5 seconds: `AuctionCreated`, `AuctionStarted`,
`AuctionEnded`, `AuctionWon`, `AuctionFailed`, `AuctionCancelled`, `AuctionPaymentTimeout`, `BidPlaced`,
`Outbid`. The service also listens to `catalog-events`, `commerce-events` (`OrderPaid`, `OrderCancelled`) and
`fulfillment-events`. `PaymentDeadlineJob` runs every 30 seconds to handle winners who did not pay in time.

## Prerequisites

This project resolves `com.nexus:common-*` from
[antran19/common-libs](https://github.com/antran19/common-libs)'s GitHub Packages registry, declared in
`pom.xml`. Reading GitHub Packages needs authentication even for a public repo: add a Personal Access Token
(`read:packages`) to your own `~/.m2/settings.xml` under a server with id `github`. Without a token, clone
`common-libs` and run `mvn clean install` there; Maven checks the local `~/.m2` cache first.

## Build and test

```bash
mvn clean package
```

Runs the whole test suite, including Testcontainers-backed integration tests, so Docker must be running.

## Run

```bash
java -jar target/auction-service-0.1.0-SNAPSHOT.jar
```

It expects PostgreSQL on `localhost:5434` (`auction_db`), Kafka on `localhost:9092` and the Eureka
discovery-server. The `infra` repo has the docker-compose setup that runs the whole cluster. The `Dockerfile`
copies a pre-built jar, so run `mvn clean package` before `docker build`.
