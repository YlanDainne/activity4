# LegacySupply Reflection

## 1. 503 after LegacySupply created PO-100367

The adapter sent the request with the persisted `X-Request-Id` `056e829c-547e-4aba-a86a-a10f7b729f84`. The first response was a 503 even though LegacySupply had already created PO-100367, so the adapter kept the internal row pending and retried the same request identity with exponential backoff. LegacySupply's idempotency handling recognized the repeated request ID and returned the existing purchase order instead of creating another one. The adapter then stored PO-100367 and changed the internal status to `PLACED`, so the retry did not create a second order.

## 2. Unknown status code 90

I compared the status response for PO-100223 with the documented codes and found that code 90 was not one of the normal accepted, picking, shipped, or delivered states. The supplier traffic and the order's terminal behavior showed that it represented a supplier-side failure/cancellation where the shipment would never arrive. Our adapter records this as internal `UNKNOWN`, leaves the stock unchanged, and does not publish `SupplierOrderDeliveredEvent`. That prevents inventory from being restocked for goods that will never arrive and leaves the exception visible in the self-check page for manual follow-up.

## 3. Session lifetime

The session remained accepted for approximately 15 minutes in the contract-discovery logs before LegacySupply returned an authentication failure. The adapter does not rely on a fixed expiry timer because the supplier does not publish a guaranteed lifetime. It caches the session in memory, clears it when a request receives HTTP 401, signs in again, and replays the same request with the same request ID. Login and request calls also use a three-second timeout and the request operation has at most three attempts with backoff.
