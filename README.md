# Zenith Providers Repository

Official community media provider repository and distribution catalog for the [Zenith](https://github.com/ZenithAnimeORG) application ecosystem.

---

## Catalog URL

To add this repository to Zenith:
1. Open **Zenith** -> **Settings** -> **Extensions & Providers** (or follow the Onboarding wizard).
2. Add repository:
   ```
   https://zenithanimeorg.github.io/zenith-providers/index.json
   ```
   Or open via deep link on your device:
   ```
   zenith://repo/add?url=https%3A%2F%2Fzenithanimeorg.github.io%2Fzenith-providers%2Findex.json
   ```

---

## Included Providers

| Provider | Type | Capabilities |
|---|---|---|
| **AniLibria** | Media Source | `MEDIA_SOURCE`, `TRACKER` |
| **AnimeGo** | Media Source | `MEDIA_SOURCE` |
| **AnimeSkip** | Timecodes | `SKIP_TIMINGS` |
| **AniSkip** | Timecodes | `SKIP_TIMINGS` |
| **Anitype** | Media Source | `MEDIA_SOURCE` |
| **HDRezka** | Media Source | `MEDIA_SOURCE` |
| **Kodik** | Media Source | `MEDIA_SOURCE` |
| **Lift** | Media Source & Subtitles | `MEDIA_SOURCE`, `SUBTITLES` |
| **Nyaa** | Torrent Tracker | `TORRENT_SOURCE` |
| **OpenSubtitles** | Subtitles | `SUBTITLES` |
| **RuTor** | Torrent Tracker | `TORRENT_SOURCE` |
| **RuTracker** | Torrent Tracker | `TORRENT_SOURCE` |
| **TheIntroDb** | Timecodes | `SKIP_TIMINGS` |
| **YummyAnime** | Media Source | `MEDIA_SOURCE` |

---

## Packaging & Building

To package all provider `.zpk` bundles and build the repository `index.json`:
```bash
./gradlew assembleAllZpk generateRepoIndex
```
The output files will be generated in `build/distributions/`.

---

## Contributing

To create a new provider, use the official starter template:
[zenith-provider-template](https://github.com/ZenithAnimeORG/zenith-provider-template)

---

## License

MIT License. See [LICENSE](LICENSE) for details.
