# Lounge TV screenshot gallery

The GitHub README features six authentic **1920 × 1080** Fire TV captures taken on 6 November 2026, not mockups.

| File | Screen | Format |
| --- | --- | --- |
| [home.webp](screenshots/home.webp) | Home and major sports hero | Full HD WebP |
| [live-tv.png](screenshots/live-tv.png) | Live TV channel browser and preview | Full HD PNG |
| [tv-guide.png](screenshots/tv-guide.png) | Electronic programme guide | Full HD PNG |
| [sport-lounge.png](screenshots/sport-lounge.png) | Sport Lounge fixtures overview | Full HD PNG |
| [movies.webp](screenshots/movies.webp) | Movies catalogue | Full HD WebP |
| [series.webp](screenshots/series.webp) | Series catalogue | Full HD WebP |

The three WebP files are high-quality exports at the original pixel dimensions, chosen to reduce download size while keeping the UI appearance. The other captures remain original PNG files.

## Editorial policy

- Display actual Lounge TV interfaces only. Never substitute upstream OwnTV screenshots or AI-generated mockups as product captures.
- The individual-match QA screen was deliberately excluded because it showed **0 verified streams** and unverified channel discovery.
- Captured schedules, channel listings and fixture information reflect a moment in time, not a guarantee of current playback or EPG accuracy.
- The screenshots show TV status information, including a local weather location and profile label. Review these before reusing screenshots in customer-facing marketing beyond GitHub.

## Updating screenshots

From a connected Fire TV, navigate to the target screen and run:

```bash
adb -s 192.168.68.50:5555 exec-out screencap -p > screenshot.png
```

Replace the relevant image in `docs/screenshots/`, retaining a landscape 16:9 capture and appropriate file extension. Run a final visual check of the README rendering before merging a screenshot update.

This gallery is documentation only; it does not modify the Android app, the backend or release workflow.
