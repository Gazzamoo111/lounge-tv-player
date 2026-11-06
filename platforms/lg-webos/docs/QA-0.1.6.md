# QA 0.1.6: Artwork coverage, NZ HD startup and TVNZ diagnostics

## Findings

Golf, tennis, ice hockey and baseball had no packaged hero backgrounds. 0.1.5 deliberately avoided showing unrelated combat art, but left these sports without an image. The twelve unique primary discovery sports now all have a bundled background. New golf/tennis/hockey/baseball assets match the existing navy/cyan campaign style, with text-free dark left space. All primary and supplementary sport aliases are checked against real WebP files. A failed remote event image switches once to the matching local sport asset, without hiding it or repeatedly retrying it.

Read-only public HLS probes on 7 November 2026 NZ time found that Three, Whakaata Māori and Te Reo each advertise five renditions from 270p to 1080p. Their 1080p H.264 variants are around 5 Mbps. This proves HD is offered; it does not measure the rendition selected on the user's TV. The native LG adapter did not expose control of its adaptive selection.

## NZ quality change

Only the audited direct NZ Freeview sources enter the adaptive adapter. Three/Māori/Te Reo now use HLS MSE adaptation with an 8 Mbps initial bandwidth estimate and a 1080p maximum. Automatic adaptation remains enabled, so reduced bandwidth can lower quality. There is no permanent 1080p lock or minimum-resolution restriction. Source quality remains the upper bound. An adaptive HLS failure tears down that session and retries the same source through the former native path. Ordinary provider sports, Live and VOD remain native.

The diagnostic trace now records the video element's reported decoded width/height where available. Mock playback does not establish that these fields equal a real on-TV resolution.

## TVNZ remains an open device issue

TVNZ 1/2 retain the normal direct DASH/Widevine routes. The adapter now negotiates the documented hardware-secure Widevine option first, retaining the default robustness as a compatibility alternative. This is a compatibility change, not proof of a repair. The app shows a safe readable distinction between playlist, media-segment, DRM/license, and player capability failures. Numeric Shaka codes and HTTP status are retained; signed URLs, headers, response bodies and license content are excluded. A known permanent 401/403/capability refusal stops automatic retries promptly and leaves explicit Try again/Back controls.

The actual public TVNZ redirect was inspected from this cloud workspace. Its target returned HTTP 403 with a GEO_BLOCKED error, confirming this cloud environment cannot validate TVNZ playback. The response does not identify what failed on the user's New Zealand LG. LG's documented web-app User-Agent is fixed; the Android custom User-Agent is not silently ported or represented as working. No gateway proxy, DRM bypass, changed source account or shared backend deployment is included.

After a failed TVNZ attempt, the on-TV Playback details code is required to distinguish request, license and device problems. Successful TVNZ picture/audio is not claimed. A standalone real-source headless browser HLS probe was also blocked by the cloud browser's network (ERR_EMPTY_RESPONSE at the redirect host), so it did not decode video or validate the real HD selection.

## Validation

58 domain/native/adaptive tests and 39 mocked browser journeys pass. Checks cover all 31 discovery feeds' sport artwork mappings, real image decoding for all twelve sports, a broken remote-image fallback, direct DASH/Widevine and HLS setup, HD adaptation configuration, teardown before ordinary native playback, native HLS fallback, safe nested license-error extraction and readable permanent-error handling without retry loops. Existing EPG, startup, remote movement, Guide, VOD, Settings and native recovery regressions remain covered.

The native IPK is built and unpacked to verify application identity, version, all twelve hero images and the bundled player/license. Physical LG acceptance of new HD selection and TVNZ remains outstanding. User feedback on 0.1.5 confirms smooth ordinary streaming without buffering or delay.

## Device checks

1. Install over 0.1.5 without uninstalling. Browse golf, tennis, hockey and baseball hero events. Artwork must appear without OK. Check the other sports too.
2. Play Three, Whakaata Māori and Te Reo; compare the picture after startup. Confirm audio, smooth playback and switching to Sky Sport. Reduced bandwidth may still trigger a lower rendition.
3. Try TVNZ 1 and TVNZ 2. If either fails, report the exact readable code on the error screen or Playback details. Do not type a stream URL.
4. Retain the 15-minute native Live baseline, fullscreen drawers, Guide focus/idle behaviour and VOD resume.

Only the isolated LG client changes. Android, customer catalogue/order, provider database, shared gateway and production deployments are unchanged.
