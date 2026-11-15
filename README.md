# IHateMoney for Android

A simple, Material-style Android expense app focused on fast entry and IHateMoney syncing.

IHateMoney for Android is a **fork** of the excellent [MoneyBuster](https://gitlab.com/eneiluj/moneybuster) app. Huge thanks to its maintainers and contributors for all their work. MoneyBuster was originally a fork of [PhoneTrack-Android](https://gitlab.com/eneiluj/phonetrack-android/), itself a fork of [Nextcloud Notes for Android](https://github.com/stefan-niedermann/nextcloud-notes). Many thanks to their developers!

IHateMoney for Android is **completely vibe-coded**: every change is produced by AI, and is unapologetically a love letter to fast, nice expense entry rather than a carefully engineered project.

## What changed

- Simplified expense entry, list, and project setup for fast, easy expense tracking.
- Reorganized the sidebar and renamed everything to "expense" consistently.
- Removed receipt QR scanning and the redundant Manage-project chooser.
- Fixed IHateMoney expense ordering and optimized synchronization.
- Visible per-project sync phases/counts for IHateMoney and Spliit, with pull-to-refresh available on empty remote projects too.
- Full Swedish translations (plus a few locale fixes elsewhere).
- Cleaned up unused code/strings and improved naming and layout details.

## Testing

- **IHateMoney:** tested against a self-hosted [IHateMoney](https://ihatemoney.org) instance.
- **Spliit:** expense sync has unit coverage; live two-way sync still needs validation against a disposable group.
- **Nextcloud Cospend:** not tested at all.

## Spliit expense sync

Add an existing group using its shared link. The app can create, edit and delete evenly split expenses, including offline changes. Sync once after upgrading to populate full expense metadata. Participants and group settings remain managed on Spliit.

- Conflicting edits and edit/delete conflicts require a choice: keep the device edit, use Spliit, or decide later. Pending changes survive failed syncs and app restarts.
- Categories, reimbursements and existing attachments are preserved when editing. Advanced splits, recurring expenses and currency conversions remain read-only; advanced split allocations are not represented by the app’s shared expense model.
- An interrupted create is checked against the server before retrying. Older servers may ignore client-generated IDs, so an uncertain create requires checking Spliit and confirming any retry rather than risking an automatic duplicate.
- Spliit does not provide conditional updates: a final pre-write check narrows, but cannot eliminate, simultaneous web/app edit races. Its tRPC interface is not a versioned public API, so server-version compatibility needs testing.

Group links grant access to the group; treat them as secrets.

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
