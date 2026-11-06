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

Playback diagnostics: Settings → Playback diagnostics. It retains up to 160 local state/error entries with timing and position, without stream URLs or credentials. An error code or successful HTML play promise is not proof of a decoded frame.

## Current validation record

- TypeScript build and native `.ipk` packaging validated in the workspace.
- QA 0.1.1: 23 domain/native-lifecycle checks and 15 mocked browser journeys pass. They cover full XMLTV cache, restart reuse, startup readiness, a fixed persistent sidebar, accordion retention and virtual-card reuse.
- Real gateway unauthenticated denial/CORS and real calendar/metadata JSON checked read-only.
- User testing of 0.1.0 confirmed installation/launch and exposed severe navigation/loading problems and failing channels.
- QA 0.1.1 physical launcher re-entry, startup, responsiveness, playback, audio/track selection and long sessions: **awaiting LG testing**. This workspace cannot reach the LAN television.
