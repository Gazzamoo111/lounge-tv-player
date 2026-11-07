# QA 0.1.8: Six-second splash and TVNZ license transport

## Device evidence

User testing now identifies TVNZ 1 as Widevine error 6007 with HTTP 502. TVNZ 1/2 are the two failing TVNZ rows; working HLS channels are not evidence that their protected-license path works. This establishes license-request failure rather than a general player startup failure. HTTP 502 does not by itself identify whether the ultimate cause is the endpoint, request transport, device approval or an upstream outage.

The splash was oversized and too brief. It now occupies a centred 940×530 artwork box in the 1920×1080 canvas, approximately half the old width. The application waits six seconds after the logo finishes decoding before progressing. Remembered login, QR login and the current preparation screen are preserved.

## Public source audit

The current public NZ Kodi TV playlist still declares the existing TVNZ 1/2 DASH and Widevine aliases. A read-only GET audit showed each license alias issuing HTTP 307 to the same Switch playback-license service with service/version/channel and server-issued authorization query parameters. The target does not support GET and returned 404. This does not establish success or failure for a genuine Widevine license POST. The audit does not support a stale-URL claim or a claim that 307 discards the POST body.

The published Android integration explicitly sends its source-declared client User-Agent, whereas a webOS browser's User-Agent is fixed. The former Shaka setup did not explicitly set a binary license Content-Type. These are concrete transport differences, not proof of the cause of the 502. Cloud TVNZ manifests remain geo-blocked, and no physical TV CDM challenge is available in this workspace.

## Client change

For only TVNZ 1/2, a registered Shaka networking scheme sends the TV's genuine CDM challenge through the existing bundled LG JavaScript service. It forwards the opaque bytes as a normal HTTPS POST with application/octet-stream and the published source client header. Validated redirects retain the method and bytes. The target server still decides whether to issue a license; the response stays opaque and returns to the TV's CDM. No keys, entitlement changes, alternate decryption or weakened certificate validation are involved.

The service accepts requests only from this app. Callers select one of two fixed channels rather than supplying a URL. Only the observed/official license hosts and exact paths are allowed; HTTP, unexpected hosts, URL credentials, alternate ports, excessive request/response bodies and long redirect chains are rejected. A global 12-second deadline bounds the server operation. The web operation can cancel its bridge and returns typed Shaka errors, preserving actual upstream HTTP failures. Signed URLs, challenge/license bodies and server tokens are not included in the app's trace.

Browsers without the LG bridge retain the standard license path, now with an explicit application/octet-stream header. Video manifests, segments, ordinary provider playback, the working NZ HLS streams and channel membership are unchanged.

## Verification

65 domain/native/service checks pass, including unchanged bytes across a simulated 307, binary request headers, redirect restrictions, opaque response handling and truthful HTTP 502 reporting. 44 browser journeys pass across the full suite and focused reruns. They cover normal application flows plus the exact six-second splash duration, reduced artwork size and unchanged challenge/response bytes across the simulated LG bridge. An additional test exercises the native scheme through the actual bundled Shaka NetworkingEngine and verifies that HTTP 502 becomes a typed network failure rather than a generic rejected promise.

The remembered-login restart check explicitly allows the requested six-second splash; it still verifies that catalogue and guide downloads are not repeated. The install package contains the updated companion service and license transport file, and is unpacked to verify identity, assets and notices.

These tests do not establish real TVNZ license issuance or moving picture/audio. Physical LG acceptance is required. A continued HTTP 502 from the native path remains a real license-service failure, not a fixed or successful stream.

## Device acceptance

1. Install over 0.1.7 without uninstalling. Relaunch. Check the smaller logo remains visible for at least six seconds, then continues into the existing loading screen.
2. Play TVNZ 1 and TVNZ 2. Confirm actual picture and audio, then retain the 15-minute Live baseline. If either fails, record the latest Error log summary and actual HTTP code.
3. Switch to Three, Whakaata Māori, Te Reo and Sky Sport; verify their prior picture/playback behaviour remains.
4. Check QR phone sign-in and stored-login relaunch remain functional. The complete IPK updates both the TV app and bundled service.
