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
3. Merge the tested PR to `main`. In Actions, run **Build and approve Lounge TV release** with `version_name` such as `5.2.1.0` (Android versionCode is computed automatically as `5020100`).
4. Download the **signed candidate APK** from the candidate job and test installing it over a previously signed `com.loungetv.player` version with `adb install -r`, confirming playlists/data survive. A **QA `.qa` package is a different app**, not an upgrade test.
5. Only after passing the real-device test, **approve the pending production deployment** in GitHub Actions. Production has a required reviewer configured during bootstrap.
6. Existing production clients can then use the built-in app updater to receive the APK from this repository and approve installation on their TV.

The Android `versionCode` must always increase, even for hotfixes. The release workflow stores the code in release notes to gate future releases.

## Signing and infrastructure

**Never commit signing secrets, BEST panel credentials, provider passwords, private URLs, or provisioning tokens.** `setup-loungetv.sh` creates a new signing key at `~/Library/Application Support/LoungeTV/production-signing.keystore`, saves its password to macOS Keychain, and uploads necessary signing values into GitHub Actions Secrets. Keep an independent encrypted backup of this keystore. Losing it prevents in-place upgrades to existing customers under the same application ID.

The production repository deliberately does not touch the older public Lounge TV APK in `Gazzamoo111/lounge-tv-releases`. There is no automatic customer release. The bootstrap configures a required-reviewer production Environment. If GitHub refuses that step, bootstrap stops and publishing must remain disabled.

**Temporary QA note:** The existing branded v5.2.0 source required excluding `:app:verifyI18nLiterals` to build. Workflows carry that known exclusion explicitly, but run unit tests and package verification. Remove the exclusion after the localisation validator is resolved upstream.

### Compatible release testing

```bash
adb install -r LoungeTV.apk
adb shell monkey -p com.loungetv.player 1
```

If the source has modified assets, `branding/asset-sha256.json` pins image hashes so upstream merges cannot silently overwrite those brand files. Any change requires updating the manifest intentionally and a manual visual check of the app.
