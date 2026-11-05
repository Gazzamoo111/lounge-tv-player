# Capturing official Lounge TV screenshots for the README

The existing `extras/screenshots/` assets originated with the OwnTV foundation and are **not** verified customer-facing Lounge TV screenshots. Do not relabel them as Lounge TV.

To add a real four-image gallery to the public README, capture these screens from the **latest approved Lounge TV build** on a Fire TV / Android TV device:

1. **Home** with Lounge TV branding and the current hero/rails.
2. **Live TV** showing the curated categories, with non-sensitive content.
3. **TV Guide** with a clear, correctly matched programme grid.
4. **Sport Lounge** showing a current event listing, but not private account details or unverified playback claims.

Use the same 16:9 capture format for all images.

## Capture over ADB

With Android platform tools installed and the device already connected over ADB:

```bash
mkdir -p docs/screenshots
adb exec-out screencap -p > docs/screenshots/home.png
```

Repeat after navigating to each screen and save as `live-tv.png`, `tv-guide.png` and `sport-lounge.png`.

Review the images before committing: hide account names, access credentials, private IPs, device identifiers, notifications and customer-specific content. Check that all branding and programme listings are accurate.

## Add the gallery to README.md

Insert the following after the highlights section **only after all four verified image files are committed**:

```html
## Screenshots

<table>
<tr>
<td><img src="docs/screenshots/home.png" alt="Lounge TV Home" width="100%"><br><sub>Home</sub></td>
<td><img src="docs/screenshots/live-tv.png" alt="Lounge TV Live" width="100%"><br><sub>Live TV</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/tv-guide.png" alt="Lounge TV Guide" width="100%"><br><sub>TV Guide</sub></td>
<td><img src="docs/screenshots/sport-lounge.png" alt="Lounge TV Sport Lounge" width="100%"><br><sub>Sport Lounge</sub></td>
</tr>
</table>
```

Screenshots document the customer experience, not a guarantee of channel availability or matching accuracy.
