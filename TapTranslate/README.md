# Tap & Translate

Photograph Turkish text (or pick a photo), then tap any word to see it in English. Tapping also
shows the whole line it sits in, and a button translates the whole paragraph. Turkish suffixes
make single-word translation hit-and-miss, so the line gives you context.

Tapping a word shows:

- the **translation** (ML Kit, on the phone; the Turkish → English pack downloads once, then works offline)
- **all its meanings**, grouped by part of speech (noun, verb, ...), from English Wiktionary. This part
  needs internet. For inflected forms like *evim*, Wiktionary only says "possessive of ev", so a
  **Look up "ev"** button fetches the base word's meanings
- the **whole line** translated, and a button for the whole paragraph

Text recognition is also on-device (ML Kit, bundled).

Meanings come from Wiktionary's `page/definition` endpoint
(`https://en.wiktionary.org/api/rest_v1/page/definition/<word>`), text under CC BY-SA, credited in the card.
If that endpoint ever changes or disappears, the translation still works and the meanings area shows
an error. All the code for it is in `DictionaryEngine.kt`.

## Run it

1. Open this folder in Android Studio and let Gradle sync (it creates `local.properties` for you).
2. Plug in a phone (USB debugging on) or start an emulator, then press Run.
   Or from a terminal: `./gradlew installDebug`

First launch needs internet once, to fetch the language pack.

## Versions used

| Thing | Version |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin + Compose plugin | 2.3.20 |
| Compose BOM | 2026.09.00 |
| activity-compose | 1.13.0 |
| lifecycle-viewmodel-compose | 2.11.0 |
| ML Kit text-recognition (Latin) | 16.0.1 |
| ML Kit translate | 17.0.3 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |

If Gradle says a dependency needs a newer `compileSdk`, raise `compileSdk` in `app/build.gradle.kts`
and accept Android Studio's offer to update the Android Gradle Plugin.

## How it works

- `ImageLoader` decodes the photo (downsampled, EXIF rotation applied).
- `OcrEngine` runs ML Kit on it and keeps each word's tilted outline plus its line and paragraph.
- `TappableImage` draws the photo with word outlines; pinch/drag to zoom, tap to hit-test against
  the outlines (with ~10dp of finger slack).
- `TranslationEngine` wraps ML Kit's Turkish → English translator; `MainViewModel` ties it together.

## Notes

- Photos are taken with your normal camera app, so the app needs no camera permission.
- The package name is `com.example.taptranslate`; change it in `app/build.gradle.kts` (and the
  `package` lines) if you want your own.
- Handwriting, stylised fonts and curved text will OCR poorly. Flat, well-lit print works best.
- This project was written without being compiled (no Android SDK where it was made), so expect to
  fix a small thing or two on the first Gradle sync. It also wasn't tested against live Wiktionary
  responses.
