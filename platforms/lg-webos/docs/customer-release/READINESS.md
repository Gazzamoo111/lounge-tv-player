# Lounge TV for LG — customer release candidate 1.0.0

Status: packaged candidate for physical acceptance and LG submission. This is not a claim that LG has approved the app or that it is available in LG Apps.

## Fixed release identity

- App ID: `nz.loungetv.webos`
- Display name: Lounge TV
- Version: 1.0.0
- Companion: `nz.loungetv.webos.companion`
- Package: `nz.loungetv.webos_1.0.0_all.ipk`
- Builds separately from QA `nz.loungetv.webos.qa` and can be installed alongside it.
- The permanent App ID must match the Seller Lounge registration before submission. If a different ID has already been registered, rebuild using `LOUNGE_CUSTOMER_APP_ID` before any customer distribution. Do not change it after customer launch.

## What is prepared

Customer branding and versioning; separate service identity with owner-only access; existing QR and remote login; local native launch image and six-second in-app splash; existing playlist/EPG preparation; supported LG settings; versioned update package and checksum. Root Home/Login Back returns to the TV system. Existing native and TVNZ playback paths are preserved except their service address now follows the release identity.

Shared catalogue, sports services and EPG are the same services used by the QA build. Nothing in this package provisions a special Gary-only catalogue. Customers still need an active account and receive their account entitlements. LG presentation features are specific to the installed LG version; Android UI features do not automatically change with this release.

## Customer installation and updates

Recommended public route: LG Apps through Seller Lounge approval. A packaged IPK is not a public app-store listing. A customer cannot install it merely by scanning the sign-in QR code or opening a download link on the TV.

Developer Mode installation is suitable for the owner's acceptance TV and supervised pilots. It requires developer setup and the development session can expire, uninstalling development apps. Do not describe it as the standard public customer installation flow.

App updates require a new package through the installation source. Store releases follow LG's update approval process. Do not promise an Android-style self-updater on LG. Shared EPG and catalogue changes reach clients on refresh without replacing the app.

## Release gates that remain

1. Install this exact production-identity package on the physical LG. QA playback success does not prove a renamed service is accepted on the device.
2. Complete PHYSICAL-ACCEPTANCE.md, especially launcher click, QR on a real phone, TVNZ 1/2, long Live playback, VOD and Back/Home.
3. Confirm Seller Lounge App ID, seller/contact details, target countries and supported webOS versions. The source targets Chrome 87, but this is not proof of device/media compatibility. Set the supported range from actual device tests. Existing physical QA was on the reported LG 65UR78006LL / webOS 10.3.2; the customer identity and additional models still need acceptance.
4. Verify a public privacy policy URL and customer support contact. Public website/privacy URLs could not be verified by the web lookup in this session. Do not enter an invented address or mark that check passed.
5. Add real LG screenshots, review credentials and the official LG self-checklist with actual device results. No customer login is bundled here. The included UX scenario and listing text are drafts for submission, not evidence of approval.
6. Submit the package and required assets/documents to Seller Lounge. Wait for approval and verify the public listing before giving customers store-install instructions.

## Sources

- https://webostv.developer.lge.com/distribute/app-approval-process
- https://webostv.developer.lge.com/distribute/app-self-checklist
- https://webostv.developer.lge.com/develop/getting-started/app-resources
- https://webostv.developer.lge.com/develop/getting-started/developer-mode-app
