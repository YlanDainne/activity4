# LegacySupply Integration

## Product mapping

These mappings match the product IDs and names used by the Lab 2 inventory seed.

| Product ID | Product name | LegacySupply SupplierSku | PackSize | Supplier unit |
|---|---|---|---:|---|
| `P100` | Mechanical Keyboard | `VQS-8242` | 12 | `CS` (case) |
| `P200` | Wireless Mouse | `VQS-5456` | 24 | `CS` (case) |
| `P300` | USB-C Hub | `VQS-9391` | 12 | `CS` (case) |

The mapping is private to the supplier adapter. Order and Inventory use only the internal product ID and unit count.

## Sessions

The adapter sends `POST /auth/token` with the client ID and `LS_API_KEY`, then sends the returned token in `X-LS-Session`. A session is cached in memory. When LegacySupply returns `401`, the adapter discards the token, signs in again, and retries the same request.

During contract discovery, a token remained accepted for approximately 15 minutes without activity. The implementation does not depend on that duration; it renews only when the server rejects the session.

## Observed errors

| Code / HTTP status | Cause | Handling |
|---|---|---|
| `E-AUTH-01` / `401` | Credentials were rejected. | Configuration error; authentication is retried by the normal bounded retry policy. |
| `E-AUTH-02`, `E-AUTH-03`, `E-AUTH-07` / `401` | Session header missing, unknown, or expired. | Clear the cached session, sign in again, and replay the request. |
| `E-FMT-01` / `415` | Request did not use `application/xml`. | The adapter sends XML content type on requests with bodies. |
| `E-FMT-02` / `400` | Malformed XML. | Adapter XML records are used for serialization. |
| `E-REF-05` / `400` | Buyer reference was invalid. | Buyer references are generated with the `RO-` prefix and are under 40 characters. |
| `E-SKU-02` / `422` | Supplier SKU was not in the partner catalog. | Only the local catalog mapping is submitted. |
| `E-QTY-11` / `422` | Quantity was zero, negative, fractional, or outside the supplier range. | Internal units are rounded up to a positive whole number of cases. |
| `E-IDEM-04` / `409` | The same request ID was reused with different content. | A request ID is generated once and persisted before dispatch; retries reuse it. |
| `E-PO-04` / `404` | The requested PO did not exist. | The polling failure leaves the internal order `PLACED`; the next scheduled cycle tries again. |
| `E-RATE-03` / `429` | Supplier request quota was exceeded. | Calls are bounded to three attempts and status polling is scheduled every 30 seconds. |
| `E-SYS-50`, `E-SYS-99` / `503` | Supplier processing error or outage. | The adapter retries the same request ID up to three times with 500 ms and 1 s backoff; the order remains `PENDING` and the scheduled dispatcher retries it later. |

## Qty and Uom

`Qty` is the number of supplier cases, not the number of individual inventory units. `Uom` identifies that case unit; LegacySupply returns `CS` in its acknowledgement. For example, if Inventory needs 15 keyboards and the keyboard pack size is 12, the adapter sends `Qty = ceil(15 / 12) = 2` and receives 24 units when those cases are delivered.

## Status handling

Supplier status codes `10`, `20`, and `30` (`Accepted`, `Picking`, and `Shipped`) keep the internal order `PLACED`. Code `40` marks it `DELIVERED` and publishes `SupplierOrderDeliveredEvent`, which restocks Inventory. A textual cancellation or rejection marks the order `REJECTED`. Any status outside the known set is recorded as internal `UNKNOWN` and is not retried or restocked automatically; this prevents an unfamiliar supplier state from causing duplicate inventory or purchase orders.

## Persistence and idempotency

Every reorder is inserted into `supplier_orders` as `PENDING` before the external request. Its generated request ID and unique buyer reference are reused by all retries and scheduled restarts. The adapter allows only one `PENDING` or `PLACED` order per product, and the supplier's `X-Request-Id` idempotency behavior prevents a request that was accepted before a timeout from being created twice.

The self-check page reads `/api/supplier-orders` and shows every internal order, PO number, buyer reference, request ID, case quantity, unit quantity, and internal status. A duplicate request-ID check is shown beside the order count so repeated low-stock events and retry behavior are visible.

The table is initialized by `backend/src/main/resources/schema.sql` for local or fresh deployments.