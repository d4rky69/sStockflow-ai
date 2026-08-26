# StockFlow AI — Ownership and Provenance

## Creator

**Veyjval B**

StockFlow AI is designed and engineered by Veyjval B.

Copyright © 2026 Veyjval B. All rights reserved.

## Cryptographic signing identity

Important commits and release tags are signed using a dedicated Ed25519 key.

```text
Key type: Ed25519
Fingerprint: SHA256:qfPhHHcViPjJa3XXpzawAExBOCjRe583reh712D7nes
Identity: Veyjval B - StockFlow AI signing key
```

The public verification key is stored at:

```text
docs/provenance/stockflow-signing-key.pub
```

The public key may be shared for verification. The corresponding private key must never be committed, uploaded or disclosed.

## What this proves

- A valid signature proves that the holder of the StockFlow private signing key approved the signed commit or tag.
- The Git commit ID identifies the exact version of every tracked file.
- A SHA-256 release checksum can verify that a distributed archive has not changed.
- A public repository timestamp provides an external record of when the signed version was published.

A hash alone does not establish ownership. Ownership evidence comes from combining the hash with a protected private key, signed Git history, account identity and dated records.

## Verify a signed commit

```powershell
git log --show-signature -1
```

On GitHub, a correctly registered signing key should cause signed commits and tags to display a **Verified** badge.

## Verify a release archive

```powershell
Get-FileHash ".\StockFlow-AI-v1.0.0.zip" -Algorithm SHA256
```

Compare the output with the checksum published alongside that release. Matching values confirm that the archive is byte-for-byte identical to the signed release artifact.

## Security rules

- Never place the private signing key in this repository.
- Never share the private signing key through email, chat or cloud storage.
- Only the `.pub` public key is intended for publication.
- Rotate the signing key and publish a revocation notice if the private key may have been exposed.
- Keep credentials, service-role keys and database passwords outside source control.

## Scope

This record concerns StockFlow AI project provenance. Third-party libraries, frameworks and dependencies retain their respective licences and copyright ownership.
