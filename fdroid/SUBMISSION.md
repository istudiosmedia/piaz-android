# Store request texts

Replace istudiosmedia/piaz-android with the public repo before sending.

## F-Droid: Request For Packaging (https://gitlab.com/fdroid/rfp/-/issues/new)

Title: The Piaz (news.piaz) and The Piaz for TV (news.piaz.tv)

- Source code: https://github.com/istudiosmedia/piaz-android
- License: GPL-3.0-only
- Category: Multimedia, Reading
- Summary: Independent news on West Asia, Iran and US foreign policy: articles, independent YouTube channels, live TV and the Daily Brief podcast. No account, ads or analytics.
- Build: Gradle, modules `app` (phone) and `tv` (Android TV). Recipes are in `fdroid/` in the repo. Dependencies are Jetpack, Media3, OkHttp, Coil and Guava from Google Maven and Maven Central, with no Google Play Services.
- Anti-features: NonFreeNet (YouTube embeds, broadcaster live streams).
- Metadata: fastlane, in `app/fastlane` and `tv/fastlane`.

## IzzyOnDroid (https://github.com/IzzyOnDroid/repodata/issues/new)

Title: Inclusion request: The Piaz (news.piaz, news.piaz.tv)

Repo: https://github.com/istudiosmedia/piaz-android
Releases with signed APKs: https://github.com/istudiosmedia/piaz-android/releases (`app-release.apk` for phones, `tv-release.apk` for Android TV)
License: GPL-3.0-only
Description: Independent news on West Asia, Iran and US foreign policy. No account, ads or analytics. Fastlane metadata is in `app/fastlane` and `tv/fastlane`.
