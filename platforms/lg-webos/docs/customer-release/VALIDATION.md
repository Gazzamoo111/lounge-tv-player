# Customer candidate validation

- 70 domain, parsing, native-service and ownership unit checks passed.
- Production IPK identity, service, bundled assets, vendor notices and credential scan passed.
- QA packaging also passed with the separate QA identity, preserving the original build path.
- Full customer browser suite: 45 passed.
- Final focused customer checks: 2 passed, covering the updated Settings wording and the additional production identity/native-assets/Login-Back check. This gives 46 distinct browser scenarios.
- Total distinct automated checks: 70 unit + 46 browser = 116.
- Native launch image 1920×1080 and store icon 400×400 were rendered from existing branded assets and visually inspected.
- Final customer package SHA256: 078a8fe0769742e076b90077855190dcd1f19efbabae0ee2e8454e66bf574a09.
- Physical customer-identity acceptance: NOT YET TESTED.

Browser fixtures exercise UI and API contracts. They do not prove real customer authentication, a phone on the home network, LG launcher integration or physical TV playback.

Earlier physical QA: user confirmed TVNZ1/2 playback, transparent splash appearance, faster startup/navigation and improved playback stability on QA 0.1.8–0.1.11. The production App ID and matching service still require the physical acceptance checklist.
