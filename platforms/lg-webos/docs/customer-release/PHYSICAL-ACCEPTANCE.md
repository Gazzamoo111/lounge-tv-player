# Physical LG acceptance — 1.0.0

All boxes below are UNTESTED on the production-identity package until completed on the TV. Existing QA success is recorded separately and is not substituted for these checks.

Device: __________  webOS: __________  Date: __________  Tester: __________

- [ ] Launch using the Lounge TV icon from LG Home, without a terminal launch command.
- [ ] Logo is centred, clean and correctly sized; in-app splash remains for at least six seconds.
- [ ] Login offers QR and remote entry. Incorrect details show a useful message and allow retry.
- [ ] Scan QR with a real phone on the same home Wi-Fi; sign in successfully. A used/expired QR cannot sign in another TV.
- [ ] Home appears after Live, Movies, Series and EPG preparation. Navigate immediately without a frozen cursor.
- [ ] Close and reopen the app: saved login works without asking for the password again when Remember login is on.
- [ ] Play TVNZ1 and TVNZ2; play a normal provider channel. Confirm first frame, sound and channel switching.
- [ ] Leave three different channels playing for at least 30 minutes each. No reconnect loop or 2–3 minute stop.
- [ ] Play one movie; seek; resume after leaving. Open a series and play an episode.
- [ ] From fullscreen Live, Left opens channels; Left again opens categories; Back unwinds correctly.
- [ ] On Home and Login, Back returns to LG Home. Nested views, dialogs and playback Back do not exit prematurely.
- [ ] Test both directional navigation and the Magic Remote pointer across Home, Live, Guide, VOD, Saved and Settings.
- [ ] EPG refresh completes; NZ and representative US/UK examples display correctly. Do not count continuous-stream labels as episode schedules.
- [ ] Disconnect Wi-Fi while browsing/playing, then reconnect. The app stays controllable and presents recoverable errors.
- [ ] Sign out and switch accounts. No prior account's saved credentials, catalogue or profile is exposed.
- [ ] About displays Lounge TV 1.0.0. No QA branding appears in the customer flow.
- [ ] Reinstall the same production ID over the current version; login/preferences persist. For future update acceptance, repeat with a higher version and the identical App ID.
- [ ] Repeat key checks on a second supported LG model/webOS version before broad launch.

Decision: PASS / FAIL / LIMITED PILOT ONLY
Issues: __________
