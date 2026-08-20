# Product Roadmap

The product wedge is a **contract-aware API workbench**: fast request execution with OpenAPI context, truthful validation, safe local history, and QA automation that can reuse the same execution core.

## Delivered in this milestone

- REST Workbench with method, URL, query, headers, body, auth, variables, timeout, cancellation, response metadata, and replay.
- OpenAPI JSON/YAML import, operation prefill, bounded `$ref` resolution, and live status/content-type/schema validation.
- Named local environments with persistent selection, AES-256-GCM encryption for secret variable values, explicit restoration state for unavailable secrets, and a scratch/no-environment mode.
- Local collections, saved requests, and metadata search with deterministic ordering, bounded atomic storage, and secret restoration markers instead of persisted credentials. The repository layer also validates its folder-capable hierarchy so orphan, cyclic, or cross-collection records cannot be accepted.
- Safe declarative assertions for status, response time, headers, body containment, and a bounded JSON-path subset. Assertion evaluation uses the transient raw response while only generic redacted results cross into state and history.
- Central secret redaction with raw contract/assertion input kept transient, persisted history sanitized, and HTTP(S) URL user-info removed from persistence and diagnostics.
- Fail-safe environment and saved-request stores: corrupt, invalid, oversized, or unsupported envelopes are not silently reset or overwritten by normal mutations; recovery explicitly preserves the raw file as a sibling backup before reset.
- Keyboard command palette, back/forward navigation, responsive compact/wide layouts, RTL isolation for technical text, light/dark themes, and accessible semantics.
- Local mock, downloader, settings, export, AI-key, and fuzz hardening; bounded work and owned lifecycle cleanup.
- Preview labels on screens that still use simulated Pro data.

Workbench v2 remains a local desktop workflow. Arbitrary executable request scripts and shared/team collaboration are not delivered features.

## Next: one shared execution engine

1. Adapt Test Chain, Fuzz, and Auto Flow to the Workbench executor behind a feature flag.
2. Preserve legacy behavior with golden collections, cancellation tests, and request/response contract fixtures.
3. Extend the declarative assertion catalog and add cookies, client certificates, proxies, and repeatable environment-run scenarios without introducing an unbounded script runtime.
4. Add an OS-keychain-backed secret provider while retaining an explicit portable file-backed mode.
5. Add import/export round trips for Postman collections and OpenAPI examples without leaking secrets.

## Then: reliable automation

- Turn Scheduler, Monitor, multi-environment runs, notifications, and issue creation from marked previews into real integrations with durable queues, retry policy, audit events, and explicit ownership.
- Add CLI/headless execution and CI-native machine-readable reports.
- Add performance scenarios with controlled concurrency, percentile latency, thresholds, and reproducible datasets.
- Add workspace-level contract drift, breaking-change gates, and baseline comparison.

## Protocol and future workspace expansion

After the REST core and automation paths share one tested engine:

- GraphQL queries/subscriptions.
- WebSocket sessions.
- gRPC reflection and protobuf workspaces.
- Any future shared workspace, review, or role-based secret model must be designed as a separate trust boundary; none is implied by the local Workbench v2 stores.
- A signed extension SDK with explicit permissions and a stable request/response API.

## Definition of done

A feature is only “working” when it has real backing behavior, cancellation and failure states, persistence/redaction rules, keyboard and screen-reader semantics, RTL and light/dark coverage, regression tests, and no simulated success indicators. Preview screens stay labeled until they satisfy that bar.
