# Security Policy

This document describes the security controls implemented in the current PostSync Pro source tree. It is not a warranty, a claim of absolute security, or evidence of an independent security audit.

## Supported versions

Security fixes are applied to the current development branch and the latest published release. Older releases may not receive backports; reproduce a report against the latest version when practical.

## Implemented security posture

### Local mock server

- The mock server binds to `127.0.0.1` only. Configuration that requests a non-loopback host is rejected.
- CORS is disabled by default. It is installed only when explicit allowed origins are supplied; there is no wildcard `anyHost` policy.
- Request-body limits are enforced while streaming, including when `Content-Length` is missing or inaccurate. The default limit is 1 MiB and configured values are capped at 10 MiB.
- Request logs are bounded (500 entries by default, at most 10,000), and stored paths and bodies are length-limited.
- Known credential fields, query parameters, and Bearer tokens are redacted from mock-server logs. Redaction is defense in depth and may not recognize every application-specific secret name.
- Generated mock data is depth-, property-, numeric-, and string-bounded to limit pathological schema expansion.
- Starting the server waits for the socket bind to succeed. Stop and view-model cleanup close the server lifecycle rather than leaving a background listener behind.

The mock server is intended for local development data, not as an Internet-facing service or a production API gateway.

### Secrets and API keys

- Cloud API keys are encrypted at rest with AES-256-GCM using a fresh random nonce for each encryption operation and authenticated additional data derived from local machine properties.
- The local encryption key is created under the application data directory using a locked, same-directory temporary file and atomic rename where supported.
- On POSIX file systems, the application attempts to restrict the key and settings files to owner read/write (`0600`) and their directory to owner-only access (`0700`). Symbolic links are rejected for these sensitive files.
- Encrypted values, key files, and ciphertext are size- and format-validated. A corrupt existing key is rejected rather than silently replaced.
- Clearing all settings also removes the file-backed encryption key.
- Gemini credentials are sent in the `x-goog-api-key` request header, not in URL query parameters. Groq credentials use the `Authorization` header.

The key is file-backed; it is **not** stored in macOS Keychain, Windows Credential Manager, a Linux secret service, hardware-backed storage, or a TPM/Secure Enclave. A process running as the same OS user, malware, a compromised account, or an unprotected backup may be able to obtain both the encrypted settings and the key. OS account security, disk encryption, careful backup handling, and prompt credential rotation remain important.

### Workbench v2 environments, saved requests, and assertions

- Named environments persist their active selection and reusable variables locally. Secret variable values—and values whose names are classified as credential-like—are encrypted with the same AES-256-GCM file-backed provider described above. Environment names, variable names, enabled flags, and non-secret values are not encrypted.
- Variable resolution also treats credential-shaped names as sensitive for response and diagnostic redaction even when imported or scratch metadata omitted the secret flag.
- If a single environment secret cannot be decrypted, it is returned as empty and marked as requiring restoration; healthy variables remain available. Environment selection preserves the original ciphertext byte-for-byte, and an update that still marks the secret unavailable preserves that ciphertext rather than replacing it with an empty value.
- Collections, folders, and saved requests use a separate local store. Before serialization, authentication values and secret variables are emptied; sensitive URL/query/header/body values are redacted; sensitive declarative assertion operands are removed while their enabled intent is retained. Safe metadata records which secret locations must be restored, and execution is blocked until enabled sensitive checks are restored.
- The environment and saved-request repositories validate file size, serialization version, IDs, counts, field bounds, timestamps, and collection/folder/request references. Folder self-parenting, cycles, missing parents, and cross-collection links are rejected.
- A malformed, unsupported, oversized, or structurally invalid existing store raises a corruption error. Normal mutations load and validate the existing bytes first, so they do not silently replace corruption with an empty workspace. Recovery is explicit: the raw store is moved without parsing to a unique sibling `.corrupt-…bak` path before an empty valid envelope is published. If reset publication fails, restoration is attempted and any retained backup remains available for support.
- Both stores serialize same-path operations with a mutex, reject symbolic-link files/directories, apply owner-only permissions where supported, flush completed same-directory temporary files, and use atomic replacement where supported. Capacity failures leave the previous file intact.
- Contract validation and custom assertions evaluate only the bounded transient raw response. UI state and history receive the executor's separate redacted response plus bounded generic assertion messages; actual and expected assertion values are not copied into diagnostics.
- Custom assertions are declarative and bounded: exact/ranged status, maximum duration, header existence/equality/containment, body containment, and typed equality/existence over a restricted JSON path. Count, body, header, operand, JSON nesting/token, path, and segment budgets are enforced with cancellation checks and linear-time containment. Regular-expression assertions, JSONPath filters/wildcards/recursive descent, arbitrary scripts, and a script sandbox are not implemented.
- HTTP(S) URL user-info such as `https://user:password@example.test` is rejected by the executor. Defense-in-depth redaction replaces the entire `user:password` section before drafts, failed exchanges, history, or diagnostics cross a persistence/display boundary.

This protects local files against accidental plaintext persistence and some forms of corruption; it does not turn PostSync Pro into a secret manager. A same-user process that can read both the ciphertext and the file-backed key remains inside the trust boundary.

### Files, settings, history, and exports

- Settings updates are serialized with a coroutine mutex, size-limited, flushed, and replaced through a same-directory temporary file. A previous valid settings file is retained as a backup, and unrecoverable corruption is reported rather than silently resetting configuration.
- Workbench history is capacity- and file-size-bounded, sanitized before persistence, protected against symbolic-link targets, and written through a flushed temporary file plus atomic replacement where supported.
- Workbench environment, saved-request, and history mutations use sibling OS locks in addition to in-process locking. Environment and saved-request recovery preserves corrupt bytes as described above.
- Merged-collection export names are Unicode-normalized and sanitized, Windows reserved names are handled, and the canonical target must remain inside the user-selected directory. These exports use a flushed temporary file and atomic replacement where supported.
- Model filenames are constrained to safe children of the model directory.
- Workbench environment, saved-request, and history stores fail closed when the file system cannot provide atomic replacement. Settings and merged-collection exports fall back to replacing from a completed same-directory temporary file; that fallback reduces partial-write risk but cannot provide the same guarantee as a native atomic rename.
- AutoFlow result exports redact known authorization headers, cookies, API keys, token/password fields, URL credentials, sensitive query values, request/response bodies, and extracted variables. Exported values are bounded. AutoFlow exports are currently written directly rather than through the atomic merged-collection export helper.

Redaction is best effort. Review generated exports and history before sharing them, especially when an API uses custom credential names or embeds secrets in unstructured content.

### Resource and network bounds

- Fuzz execution uses a bounded worker pool: requested concurrency is clamped to `1..32`. Cancellation is scoped to active runs, and captured response bodies are limited to 2,000 bytes.
- Workbench response capture is bounded (2 MiB by default, with a 50 MiB configuration ceiling), and request timeouts are restricted to a finite supported range.
- Saved-request bodies larger than 2 MiB are rejected before redaction or publication; the previous library bytes remain unchanged instead of storing a truncated draft.
- Cloud-AI requirement input, target endpoint text, and provider response bodies are bounded. Provider error bodies are not copied into user-visible exception messages.
- Settings, workbench history, mock logs, generated schema values, and AutoFlow export values have explicit storage or size ceilings.
- Long-running model downloads and fuzz runs are cancellable, and their owned HTTP clients expose lifecycle cleanup.
- HTTP header names and values used by the workbench are validated to reject control-character/header-injection attempts. Credentials embedded in URLs are rejected in favor of explicit authentication settings.

PostSync Pro is an API development client, so the workbench and testing features intentionally connect to user-supplied HTTP and HTTPS destinations. They do not block private, loopback, link-local, or cloud-metadata address ranges. Treat imported collections/specifications as untrusted and review their destinations before executing requests.

### Verified model downloads

- Bundled downloadable models include pinned exact byte sizes and SHA-256 digests.
- Downloads use a separate `.part` file, validate HTTP status, `Content-Range`, declared length when present, total bytes, and the configured SHA-256 digest before publishing the final filename.
- Resume restarts safely when a server ignores the Range request, rejects mismatched ranges, uses identity encoding, and prevents concurrent writers for the same destination.
- The verified file and checksum marker are published with same-directory atomic moves where supported. Integrity failures do not publish a final model and remove the poisoned partial file.

Checksum verification detects corruption or content different from the pinned digest; it does not replace TLS, establish publisher identity by itself, or make an untrusted model safe to execute. Custom model metadata without a configured SHA-256 digest receives size checks but not equivalent content authentication.

## Reporting a vulnerability

Please do not open a public issue containing exploit details, credentials, private API data, or a working proof of concept.

1. Use the repository's **Security** tab and **Report a vulnerability** private-reporting flow when it is available.
2. If private vulnerability reporting is unavailable, contact the repository maintainers through a private contact method listed on the repository or maintainer profile and request a secure channel. Initially provide only enough information to establish the affected component and severity.
3. Include the affected version or commit, platform, impact, minimal reproduction steps, and any relevant logs with secrets removed.
4. Give maintainers a reasonable opportunity to validate and remediate the issue before public disclosure. Coordinate publication timing when possible.

During research, avoid accessing data that is not yours, disrupting third-party services, persistence, social engineering, denial-of-service testing against public systems, or sharing discovered secrets. Use local or explicitly authorized test targets.

## Residual risks and non-goals

- The application is a developer desktop tool, not a hardened multi-user service, sandbox, WAF, proxy isolation boundary, or secret manager.
- Redaction relies on known field names, patterns, and explicitly marked sensitive values; novel or encoded secret formats can escape detection.
- Local files remain within the trust boundary of the logged-in OS account and its backups.
- Saved non-secret request content, collection/folder metadata, environment metadata, and non-secret environment variables are local plaintext. Mark sensitive assertion operands and variables explicitly; review restoration markers before rerunning a saved request.
- Workbench v2 has no arbitrary script execution or team-collaboration trust boundary. It should not be described as a sandboxed scripting host or shared secret workspace.
- User-selected remote endpoints receive the requests and data the user asks PostSync Pro to send. Cloud-AI providers receive submitted requirement text when cloud mode is used.
- TLS and certificate validation rely on the JVM, Ktor, the operating system trust store, and remote services. Certificate pinning is not implemented.
- Dependency provenance and runtime compromise are not solved solely by the controls described here. Keep the JDK, dependencies, OS, and PostSync Pro version current.
- Resource limits reduce accidental and malicious exhaustion but do not guarantee availability for every input or machine size.

## Local verification

Run the complete automated suite and compilation checks from the repository root:

```bash
./gradlew clean test
./gradlew compileKotlin
```

For a quicker security-focused regression pass:

```bash
./gradlew test --tests '*SecurityTest' --tests '*ConcurrencyTest'
```

These checks validate known regression cases; passing them is not a substitute for threat modeling, dependency review, penetration testing, or an independent audit.
