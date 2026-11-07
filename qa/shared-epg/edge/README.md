# Shared EPG streaming delivery

Live guide function v24 streams the published XMLTV with backpressure. It retains only a 512-character closing tail, a 4096-character tag-scan carry and distinct channel/programme IDs. Existing UK movie and Sky NZ supplements are added before the final closing tag. Programme metadata is forwarded unchanged. Incomplete upstream data errors the stream so clients retain the previous valid guide.

Gateway v50 changes only the XMLTV response from buffered text to the upstream body stream. No login, catalogue, media, license or playback route is changed. The minimal gateway diff is included as gateway-xmltv.patch; index.ts and stream-xmltv.ts are the complete guide function files.

The former full-string route exceeded 256 MB on a roughly 65 MB guide and produced HTTP 546 (confirmed Memory shutdown). The replacement completed a full 64.9 MB guide fetch after deployment.

Run `node --test stream-xmltv.test.ts` on Node 24. Tests cover split UTF-8, tag boundaries, bounded ID indexing on a large guide, exact preservation and truncated data.
