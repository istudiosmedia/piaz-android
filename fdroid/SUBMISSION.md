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

## IzzyOnDroid (https://codeberg.org/IzzyOnDroid/repodata/issues)

**Not submitted.** IzzyOnDroid's inclusion policy (https://izzyondroid.org/docs/general/AppInclusionPolicy/) rejects apps whose code was written by generative AI tools, and this app's code was. Requests are filed at https://codeberg.org/IzzyOnDroid/repodata/issues if that changes.

Title: Inclusion request: The Piaz (news.piaz, news.piaz.tv)

Repo: https://github.com/istudiosmedia/piaz-android
Releases with signed APKs: https://github.com/istudiosmedia/piaz-android/releases (`ThePiaz-<version>-phone.apk` for phones, `ThePiaz-<version>-tv.apk` for Android TV). Signing certificate SHA-256: 26198705275434933733558b4712aaa3e0d2247c3f70a767cab0d347bd789667
License: GPL-3.0-only
Description: Independent news on West Asia, Iran and US foreign policy. No account, ads or analytics. Fastlane metadata is in `app/fastlane` and `tv/fastlane`.
