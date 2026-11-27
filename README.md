# The Piaz for Android

Kotlin and Jetpack Compose. One Gradle project:

| Module | What it is |
|---|---|
| `app` | Phone, tablet and Chromebook app (Material 3) |
| `tv` | Android TV / Google TV app (Compose for TV, Leanback launcher) |
| `shared` | Library used by both: feeds, channel search, logos, live lineup, live wall, podcast playback, privacy WebViews |

The phone app is `news.piaz` and the TV app is `news.piaz.tv`. Separate IDs stop app stores that pick the highest version code (F-Droid, IzzyOnDroid) from offering the TV build to phones. On Google Play they are two listings.

## License

GPL-3.0-only (see `LICENSE`), © IStudios Media LLC. The license covers the code. The Piaz name, mark and app icon are trademarks of IStudios Media LLC and are not licensed for use in forks. Playfair Display is under the SIL Open Font License.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35). Android Studio provides both.

```bash
cd Android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug
```

The APKs are written to `app/build/outputs/apk/debug/` and `tv/build/outputs/apk/debug/`.

Release builds: `./gradlew assembleRelease` makes minified APKs (about 3.7 MB each); `:app:bundleRelease :tv:bundleRelease` makes Play bundles. To sign them, create `keystore.properties` next to this file (it is git-ignored):

```
storeFile=piaz-release.jks
storePassword=…
keyAlias=piaz
keyPassword=…
```

Or set `PIAZ_STOREFILE`, `PIAZ_STOREPASSWORD`, `PIAZ_KEYALIAS` and `PIAZ_KEYPASSWORD`. Without a key, release APKs are built unsigned, which is what F-Droid expects. Keep the keystore and its passwords backed up somewhere safe: IzzyOnDroid and Play updates must be signed with the same key forever.

## Checked on emulators (November 26, 2026)

`screenshots/` holds captures from a Pixel 7 emulator (Android 15) and an Android TV 1080p emulator (Android 14):

- **Phone:** Today, Shorts, Watch, Listen, an article page, a YouTube video playing in the app, the 4-feed live wall, Add a source (suggestions, and a name search with no API key), and the Daily Brief playing in the background with system media controls.
- **TV:** Today, Live, the 4-feed wall (the focused feed is the one heard), Watch with YouTube playing in the app, My sources, and Add with the first suggestion focused.
- **TV add flow:** from the My sources tab, adding a suggested channel takes 3 presses (Down, Center, Center). From the Live and Watch tabs, the first card is "Add a channel".
- **Release build:** the minified phone release APK was also run (signed with a debug key for testing). Feeds, logos, video and the live wall all work.
- **Not tested:** voice search needs a device with Google speech services, so try it on real TV hardware. Real-device testing is still needed in general.

## What to edit

- **Live channels:** `shared/src/main/java/news/piaz/shared/LiveChannels.kt`. The built-in lineup and the suggestions shown in Add. Press TV, RT and RT Arabic are left out for now (US sanctions). Their entries stay in that file; to ship them, set `piaz.shipSanctioned=true` in `gradle.properties`, or build once with `-Ppiaz.shipSanctioned=true`.
- **Built-in YouTube channels:** `Channel.builtIn` in `Models.kt`. **Suggested channels:** `Directory.kt`.
- **Tracker block list:** `Privacy` in `Web.kt`. It matches `Piaz/Views/Web.swift` on iOS.

## Privacy, as built

- No accounts, ads or analytics SDKs. OkHttp keeps no cookies.
- WebViews block Facebook, Google Analytics, Tag Manager and DoubleClick. Cookies and site storage are cleared when a view closes and at launch, which matches the non-persistent store on iOS.
- YouTube plays in YouTube's IFrame player on `youtube-nocookie.com`, inside the app, on phone and on TV.
- Memberships, accounts and the shop open in the browser. Nothing is sold in the app.
- Saved stories, channel choices and podcast positions stay on the device. Backup is turned off.
- Plain-http streams are allowed, because some broadcasters and iptv-org entries still use http.

## Open-source app stores

Store listings (titles, descriptions, icons, screenshots, TV banner, changelogs) are in `app/fastlane/` and `tv/fastlane/`. F-Droid and IzzyOnDroid both read them from the public repo.

**F-Droid** builds the app from source and signs it with its own key.
1. Publish this folder as a public repo and tag the release: `git tag v3.1 && git push --tags`.
2. The recipes in `fdroid/` already point at https://github.com/istudiosmedia/piaz-android.
3. Open a merge request adding both files to `metadata/` in https://gitlab.com/fdroid/fdroiddata (this needs a GitLab account). Or open a "Request For Packaging" issue in https://gitlab.com/fdroid/rfp with the repo link.
4. F-Droid will mark the app **NonFreeNet**, because videos come from YouTube and live streams from broadcasters. That is expected and does not block inclusion. Review usually takes a few weeks.

**GitHub releases** carry APKs signed with the IStudios Media key (kept outside the repo, in `~/piaz-signing`). Build them with `./gradlew assembleRelease` and attach them to a release named after the tag, as in https://github.com/istudiosmedia/piaz-android/releases/tag/v3.1.

**IzzyOnDroid** lists APKs from GitHub releases. **Not submitted.** IzzyOnDroid's inclusion policy (https://izzyondroid.org/docs/general/AppInclusionPolicy/) rejects apps whose code was written by generative AI tools, and this app's code was. Requests are filed at https://codeberg.org/IzzyOnDroid/repodata/issues if that changes.

F-Droid builds and GitHub release builds are signed with different keys, so a user cannot update from one to the other without reinstalling. The release notes say so.

## Google Play launch checklist

1. **Developer account.** Register at play.google.com/console as an organization (IStudios Media LLC). This needs a D-U-N-S number, a one-time USD 25 fee, identity verification, and a public developer email and website. Organization accounts take days to verify, so start early.
2. **Closed testing requirement.** This only applies to *personal* developer accounts created after November 13, 2023. They must run a closed test with at least 12 testers who stay opted in for 14 days in a row before they can apply for production. An organization account does not have this requirement, but an internal or closed test is still worth running. For TV, also opt the TV form factor in under Release > Advanced settings > Form factors.
3. **Store listing.** Name, short and full description (adapt from `LAUNCH.md`), icon (512×512), feature graphic (1024×500), phone and tablet screenshots, a TV banner (1280×720), and TV screenshots.
4. **Data safety form.** Suggested answers, to be confirmed with counsel:
   - Data collected by the app: none sent to IStudios Media. Stories, videos and podcasts are fetched from piaz.news, Supabase, YouTube and iptv-org without identifiers.
   - Third parties: embedded YouTube players and piaz.news pages (Google Analytics is blocked in the app). Declare them as your counsel advises.
   - Data is encrypted in transit: yes, except plain-http live streams the viewer adds.
   - Users can request deletion: not applicable, because nothing is stored on a server. Clearing app data removes everything.
   - Privacy policy URL: https://piaz.news/privacy (required). Align the site's advertising wording first; see `LAUNCH.md`.
5. **Content rating.** Complete the IARC questionnaire under Policy > App content. Category: News. Expect news content showing violence and conflict, no user-generated content, and no purchases. Also answer the News app declaration: The Piaz is a news publisher, so list its ownership and contact details.
6. **Other App content declarations.** Ads: no. Target audience: 18+ (avoids Families policies). Government app: no. Financial features: none. Health: none.
7. **TV quality.** Google reviews TV apps separately. They need the Leanback launcher, a banner, no required touchscreen, full D-pad navigation and working Back. All of these are built in; check them on real hardware.
8. **Rights and sanctions.** Google may ask for proof that the app may carry each live stream (Metadata and Intellectual property policies). Press TV and RT are under US sanctions: they are left out of the build until you have legal advice (see `piaz.shipSanctioned` in `gradle.properties`). Viewer-added iptv-org streams belong to their broadcasters.
9. **Release.** Upload both bundles to internal testing, then closed testing, then production with a staged rollout. Nothing has been published, and no accounts have been created.
