# Lounge TV official screenshots

Seven real **1920 x 1080** Fire TV screenshots were captured for the GitHub showcase. They are **not** emulator or phone-camera photographs.

## Recommended README gallery

| Filename | Screen | Public README |
| --- | --- | --- |
| `home.png` | Home screen and event hero | Yes |
| `live-tv.png` | Live TV list with playback preview | Yes |
| `tv-guide.png` | TV Guide with populated listings | Yes |
| `movies.png` | Movies browsing | Yes |
| `series.png` | Series browsing | Yes |
| `sport-lounge.png` | Sport Lounge fixture overview | Yes |
| `match-centre-qa.png` | Individual fixture and channel matching | **No, QA only** |

The separate Match Centre screenshot visibly says **0 verified streams** and **Unverified discovery**. It should **not** be promoted as evidence of reliable match-to-stream recommendations.

## Upload process

1. Open the branch `docs/loungetv-showcase-20261105` on GitHub.
2. Navigate into `docs/screenshots/`.
3. Upload the seven supplied PNGs without changing their pixel dimensions.
4. Review the captures before publishing. The original top-right TV status display includes a profile name, time and locality. Confirm those details are acceptable for a **public** GitHub repository.

After upload, add the gallery below to the root README, ideally after the feature descriptions:

```html
## Screenshots

<table>
<tr>
<td align="center"><img src="docs/screenshots/home.png" alt="Lounge TV Home"><br><sub>Home</sub></td>
<td align="center"><img src="docs/screenshots/live-tv.png" alt="Lounge TV Live TV"><br><sub>Live TV</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/tv-guide.png" alt="Lounge TV Guide"><br><sub>TV Guide</sub></td>
<td align="center"><img src="docs/screenshots/sport-lounge.png" alt="Lounge TV Sport Lounge"><br><sub>Sport Lounge</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/movies.png" alt="Lounge TV Movies"><br><sub>Movies</sub></td>
<td align="center"><img src="docs/screenshots/series.png" alt="Lounge TV Series"><br><sub>Series</sub></td>
</tr>
</table>
```

## Quality checks

- Keep all screenshots 1920 x 1080 and avoid stretching or aggressive cropping.
- Confirm there are no exposed passwords, access tokens, login details or personal data.
- Preserve screenshots as genuine app captures. Do not invent or alter UI features.
- The Live TV and guide screenshots are examples of the interface, not verified playback guarantees.
- QA and screenshots do not merge or deploy the Android app.
