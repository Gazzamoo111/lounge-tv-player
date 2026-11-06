# LG acceptance checklist

Device: `192.168.68.57`. Existing ares device alias: `loungetv-lg`. QA app ID: `nz.loungetv.webos.qa`.

Install the provided `.ipk`; sign in with an existing Lounge TV customer login. The app supplies the gateway automatically. Do not type a media URL.

1. Login, restart app and verify the selected login/profile behaviour.
2. Home: compare against Fire TV at 1920×1080. Check original artwork, hero, carousel, focus and all shelves with actual account data.
3. Live: choose NZ Sports, select Sky Sport 1, confirm first video frame and audio, then channels 2/3. Back should return to the selected row. Test category switching and return position. Verify actual now/next data.
4. Hold OK on a channel, save, open Saved, play and remove it. Test on another local profile; state must remain isolated.
5. Movie: open details, play, seek forward/back, select any exposed audio/subtitles, leave after 30 seconds and resume from Keep watching. Confirm no second concurrent video session.
6. Series: open two seasons, play an episode, resume, reach the end and test next episode.
7. Sport Lounge: select a current event, inspect backend watch options and play a candidate. Record whether it carries the exact advertised fixture; matching labels alone are not proof.
8. Guide: compare programme title/times to Fire TV. Missing data stays missing, not invented. Confirm that cached full-guide entries show where the source supplies them; check fallback and the remaining timeline limits in PARITY.md.
9. Network: interrupt connectivity, restore and observe bounded recovery and error controls. Switch quickly between channels; old loads must not take over new video.
10. Long session: 60 minutes Live, 60 minutes VOD, repeated navigation and app background/relaunch. Check memory, audio sync, remote focus and overlays.

Playback diagnostics: Settings → Playback diagnostics. It retains up to 180 local native-event/state/error entries with timing, position, decoded-frame counters and recovery attempts, without stream URLs or credentials. An error code or successful HTML play promise is not proof of a decoded frame.

## Current validation record

- TypeScript build and native `.ipk` packaging validated in the workspace.
- QA 0.1.1: 23 domain/native-lifecycle checks and 15 mocked browser journeys pass. They cover full XMLTV cache, restart reuse, startup readiness, a fixed persistent sidebar, accordion retention and virtual-card reuse.
- Real gateway unauthenticated denial/CORS and real calendar/metadata JSON checked read-only.
- User testing of 0.1.0 confirmed installation/launch and exposed severe navigation/loading problems and failing channels.
- User testing of 0.1.1 confirmed substantially faster startup/navigation, but Live stopped after 2–3 minutes on five channels.
- QA 0.1.2: 31 domain/native-player tests and 19 mocked browser journeys pass. Sustained physical playback remains unverified. This workspace cannot reach the LAN television.

## 0.1.2 priority device gate

1. Install over 0.1.1 without uninstalling. Play one of the previously failing channels for at least 10 minutes without touching the remote. Check video, audio, any interruption and elapsed time. Repeat on the other four reported channels.
2. If playback stops, open Playback details or Settings → Playback diagnostics. Record the last native events, error code and retry reason. The trace is retained after restart; do not clear application data before collecting it.
3. While fullscreen Live plays, press Left → Left → Back → Back. Expect channels → categories → channels → video. Browse another category; the current stream must continue until OK chooses a different channel.
4. Open a parent heading then another. Only the second stays expanded. Confirm intentional pause/resume, channel switching, return focus and launcher re-entry.
5. Sport Lounge's limited sports coverage remains deferred by user instruction.

## 0.1.3 priority device gate

User confirmed 0.1.2 played for 15 minutes without the former early stop. Keep this as the playback baseline.

1. Enter Movies and Series from the sidebar: All is focused, Search stays unfocused. Hold D-pad across multiple rows and reverse direction. There must be no multi-second catch-up. Open a movie: Play appears immediately. Open a long series season and move to later episodes; Back restores the poster.
2. Time Recess 24/7 from OK to visible moving picture. Test several other channels and compare with the reported 6–10 seconds. The client removes duplicate resets, but provider/GOP/native buffering can still dominate. Check Playback diagnostics for first-decoded-frame timing if exposed.
3. Browse Live categories without focusing every channel. Visible rows must load guide information themselves. Settings → Sources & guide → EPG sources reports matched channels/programmes; refresh, inspect time offset and exact matching. Some upstream channels have no supplied programme data and must remain missing.
4. Check all twelve Settings groups, search and Quick pinning. Test source refresh, an additional HTTPS XMLTV feed, local playlist switching, profile edits, backup/restore and exposed playback preferences. Unavailable controls must not pretend to work. Source changes apply on the next refresh.
5. Sport Lounge: compare Today/Upcoming with Android across available sports. Fixtures load progressively from primary feeds; no invented matches for empty leagues. Existing stream recommendations remain confidence-labelled, not guaranteed event verification.
6. Keep a Live channel playing for at least 15 minutes while also checking the fullscreen Left/Left/Back/Back regression. Confirm no player restart from drawer browsing.
