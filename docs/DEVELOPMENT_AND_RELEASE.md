# Lounge TV player

Lounge TV is a branded, GPLv3-modified Android TV / Fire TV build of [OwnTV](https://github.com/ahXN00/OwnTV). It uses OwnTV's native playback engine and retains Lounge TV artwork and app identity. OwnTV and the Lounge TV subscription service are separate organisations/products.

## Development and source

The base of this fork is OwnTV `v5.2.0`. Our upstream version is tracked in `.loungetv/upstream-version`. `UPSTREAM_README.md` and `LICENSE` preserve the upstream documentation and GPLv3 licence. The corresponding source and build instructions are made available in this repository to recipients of the APK.

- App ID: `com.loungetv.player`
- Updates: Releases from `Gazzamoo111/lounge-tv-player`, **not** `ahXN00/OwnTV` and **not** the older `Gazzamoo111/lounge-tv-releases` repo.
- CI: `.github/workflows/ci.yml` checks branding, runs JVM tests, and compiles a QA APK.
- Sync: `.github/workflows/upstream-sync.yml` checks OwnTV daily and opens a review PR if the merge is clean; merge conflicts create an issue.
- Release: `.github/workflows/publish.yml` is *manual only*; prepares a signed candidate, then pauses at a protected production environment before releasing that exact APK.

## Approving an update

1. Review the automated upstream PR and CI checks. Do not auto-merge it.
2. Test on an actual Fire TV with the BEST test playlist, programme guide, favourites, artwork, audio, playback switching, navigation, and settings.
3. Merge the tested PR to `main`. In Actions, run **Build and approve Lounge TV release** with a version greater than the current `5.2.2.0`, such as `5.2.2.1` (Android versionCode `5020201`).
4. Download the **signed candidate APK** from the candidate job and test installing it over a previously signed `com.loungetv.player` version with `adb install -r`, confirming playlists/data survive. A **QA `.qa` package is a different app**, not an upgrade test.
5. Only after passing the real-device test, **approve the pending production deployment** in GitHub Actions. Production has a required reviewer configured during bootstrap.
6. Existing production clients can then use the built-in app updater to receive the APK from this repository and approve installation on their TV.

The Android `versionCode` must always increase, even for hotfixes. The release workflow stores the code in release notes, compares the candidate with the previous release APK, and refuses a changed package, signer, or non-increasing code.

## Signing and infrastructure

**Never commit signing secrets, BEST panel credentials, provider passwords, private URLs, or provisioning tokens.** `setup-loungetv.sh` creates a new signing key at `~/Library/Application Support/LoungeTV/production-signing.keystore`, saves its password to macOS Keychain, and uploads necessary signing values into GitHub Actions Secrets. Keep an independent encrypted backup of this keystore. Losing it prevents in-place upgrades to existing customers under the same application ID.

Production and QA have separate signing identities:

- Production uses `com.loungetv.player` and the existing production certificate. GitHub Secrets provide `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. The public SHA-256 certificate fingerprint is pinned in the `PRODUCTION_CERT_SHA256` repository variable.
- QA uses `com.loungetv.player.qa` and a persistent, independent QA certificate. GitHub Secrets provide `QA_KEYSTORE_BASE64`, `QA_KEYSTORE_PASSWORD`, `QA_KEY_ALIAS`, and `QA_KEY_PASSWORD`. Its public fingerprint is pinned in `QA_CERT_SHA256` and must differ from production.
- CI QA codes use a reserved monotonic range derived from the GitHub run number and attempt. Local QA falls back to `5020201`, one above production `5020200`.
- A production release task refuses implicit versions, incomplete credentials, missing key files, and any code at or below `5020200`. A normal local debug build can still use Android's developer debug key, but it cannot update a CI-signed QA installation.

Before enabling either workflow, populate and verify the matching certificate fingerprint. Fingerprints are public identifiers; keystore files and passwords remain secret. A missing secret or fingerprint fails the build instead of emitting an ambiguously signed artifact.

The production repository deliberately does not touch the older public Lounge TV APK in `Gazzamoo111/lounge-tv-releases`. There is no automatic customer release. The bootstrap configures a required-reviewer production Environment. If GitHub refuses that step, bootstrap stops and publishing must remain disabled.

### Compatible release testing

```bash
adb install -r LoungeTV.apk
adb shell monkey -p com.loungetv.player 1
```

The workflow performs an offline compatibility check against the previous release APK before it uploads a candidate. The same check can be run locally without installing either APK:

```bash
python3 scripts/verify_update_compatibility.py candidate.apk \
  --previous previous-production.apk \
  --expected-package com.loungetv.player \
  --expected-cert-sha256 "$PRODUCTION_CERT_SHA256" \
  --expected-version-code 5020201
```

This proves package, signer, and Android version ordering. Preservation of real playlists, favourites, profiles, settings, and history still requires the approved `adb install -r` device test.

If the source has modified assets, `branding/asset-sha256.json` pins image hashes so upstream merges cannot silently overwrite those brand files. Any change requires updating the manifest intentionally and a manual visual check of the app.
