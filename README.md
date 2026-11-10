# IHateMoney for Android

A simple, Material-style Android expense app focused on fast entry and IHateMoney syncing.

IHateMoney for Android is a **fork** of the excellent [MoneyBuster](https://gitlab.com/eneiluj/moneybuster) app. Huge thanks to its maintainers and contributors for all their work. MoneyBuster was originally a fork of [PhoneTrack-Android](https://gitlab.com/eneiluj/phonetrack-android/), itself a fork of [Nextcloud Notes for Android](https://github.com/stefan-niedermann/nextcloud-notes). Many thanks to their developers!

IHateMoney for Android is **completely vibe-coded**: every change is produced by AI, and is unapologetically a love letter to fast, nice expense entry rather than a carefully engineered project.

## What changed

- Simplified expense entry, list, and project setup for fast, easy expense tracking.
- Reorganized the sidebar and renamed everything to "expense" consistently.
- Removed receipt QR scanning and the redundant Manage-project chooser.
- Fixed IHateMoney expense ordering and optimized synchronization.
- Full Swedish translations (plus a few locale fixes elsewhere).
- Cleaned up unused code/strings and improved naming and layout details.

## Testing

- **IHateMoney:** tested against a self-hosted [IHateMoney](https://ihatemoney.org) instance.
- **Nextcloud Cospend:** not tested at all.

## Building

```sh
nix-shell --run './gradlew :app:assembleDevDebug :app:testDevDebugUnitTest'
```

Requires JDK 21 and Android SDK platform 36. The debug APK is written to `app/build/outputs/apk/dev/debug/app-dev-debug.apk`.

## Releases

Releases use GitHub's release flow: tag `vX.Y.Z`, then publish (GitHub UI or `gh release create vX.Y.Z --generate-notes`). A GitHub Actions workflow ([`.github/workflows/release.yml`](.github/workflows/release.yml)) automatically builds the **signed release APK** (`:app:assembleNormalRelease`) on publish and attaches it to the release as `IHateMoney-<tag>.apk`.

The release APK is signed with the project's keystore, stored outside the repo as a GitHub secret. Pull requests are checked by [`.github/workflows/ci.yml`](.github/workflows/ci.yml): lint + unit tests + dev APK build.

## License

[GNU GPL v3](/LICENSE), inherited from the upstream project.
