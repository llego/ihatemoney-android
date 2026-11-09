# MoneyBuster2 for Android

A simplicity-first, Material-style fork of [MoneyBuster](https://gitlab.com/eneiluj/moneybuster), focused on fast expense entry and IHateMoney syncing.

Source and development: [llego/moneybuster2](https://github.com/llego/moneybuster2).
The original history, authorship, and GPL license are preserved. Existing local and Cospend project compatibility remains, but new development focuses on IHateMoney.

## Fork status

The redesigned expense entry, expense list, and project setup are implemented. No MoneyBuster2 APK release is available yet. The unavailable upstream cert4android dependency has been replaced with a pinned revision using the in-process certificate-store API. Device testing is still pending.

## Building MoneyBuster2

Use JDK 21, Android SDK platform 36, and Android build tools 35.0.0, or open the project in Android Studio.

On NixOS, the development shell supplies the JDK and Android SDK, including a Nix-compatible AAPT2:

```sh
nix-shell --run './gradlew :app:assembleDevDebug :app:testDevDebugUnitTest'
```

With your own Android SDK configured, run the same Gradle tasks without `nix-shell`.
The debug APK is written to `app/build/outputs/apk/dev/debug/app-dev-debug.apk`.
The `dev` flavor installs alongside the original MoneyBuster app, with separate app data.

Dependency signature/checksum verification remains enabled in `gradle/verification-metadata.xml`.
When updating dependencies, review new publisher keys and artifact checksums rather than disabling verification.

### Sync performance

Full IHateMoney refreshes still download the complete expense list so edits and deletions made elsewhere are detected. A refresh with no pending member additions uses two GET requests (project metadata/members and expenses), rather than fetching members separately first.

Expense reconciliation is committed in one database transaction, and expenses plus their participants load in two queries instead of one query per expense. Lookup indexes are added automatically without removing data. Upload-only requests skip the remote refresh, and verified HTTPS connections can be reused.

For on-device profiling, Android logs tagged `MoneyBusterServerSyncHelper` include `Sync phases (ms)` timings for upload and download/reconciliation. Actual sync time still depends on server performance, project size, and network latency.

The documentation and installation links below describe the original MoneyBuster app, not MoneyBuster2 releases.

## Original project

Shared budget manager able to sync with [IHateMoney](https://github.com/spiral-project/ihatemoney/) and [Nextcloud Cospend](https://github.com/julien-nc/cospend-nc).
As an iOS client you can use [PayForMe](https://github.com/mayflower/PayForMe).

[![Crowdin](https://d322cqt584bo4o.cloudfront.net/moneybuster/localized.svg)](https://crowdin.com/project/moneybuster)

This app is originally a fork of [PhoneTrack-Android](https://gitlab.com/eneiluj/phonetrack-android/) which is itself a fork of
[Nextcloud Notes for Android](https://github.com/stefan-niedermann/nextcloud-notes).
Many thanks to their developers :heart: !

What's different from other shared budget managers?

You can keep your project local or make it synchronize with an IHateMoney or Nextcloud Cospend instance.
This means you can choose where your data is going and preserve your privacy.

## Features

* manage projects (add/remove/create/delete/edit)
* manage members (add/remove/edit)
* manage bills (add/remove/edit)
* search bills (by payer, name, amount, date)
* project statistics
* project settlement plan
* share statistics and settlement plan
* dark theme and customizable main app color
* share/import projects with link/QRCode
* multi-lingual user-interface (translated on Crowdin: https://crowdin.com/project/moneybuster)

## :link: Requirements

* Android >= 8.0
* If you want to host a project in IHateMoney: an IHateMoney instance
* If you want to host a project in Nextcloud Cospend: a Nextcloud instance with the Cospend app installed
* If you want to be able to create remote projects from MoneyBuster: enable public project creation on your IHateMoney or Nextcloud Cospend instance

## User documentation

[Over there](https://gitlab.com/eneiluj/moneybuster/wikis/userdoc)

## Install

* F-Droid : [![MoneyBuster App on fdroid.org](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/12078870063ba70ddae219b6187bfcb7/fd.png)](https://f-droid.org/packages/net.eneiluj.moneybuster/)
* Play Store [<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/26dba6c5f776bab761cebf4e9543bf67/play.png" width="200"/>](https://play.google.com/store/apps/details?id=net.eneiluj.moneybuster)
* APK Direct download : [builds in Gitlab CI jobs artifacts](https://gitlab.com/eneiluj/moneybuster/pipelines)

## Build

If you want to build this app yourself, clone this repository :

``` bash
git clone --recurse-submodules https://gitlab.com/eneiluj/moneybuster
```

or download [master branch latest archive](https://gitlab.com/eneiluj/moneybuster/-/archive/master/moneybuster-master.zip).

Then open/import the project in Android studio and build it.

## Donate

* [Donate with Liberapay : ![Donate with Liberapay](https://liberapay.com/assets/widgets/donate.svg)](https://liberapay.com/eneiluj/donate)
* [Donate with Paypal : <img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/2344c25f3f8bbb30b1527c5ad16872f3/paypal-donate-button.png" width="80"/>](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=66PALMY8SF5JE)

## :eyes: Screenshots

[1<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/169e0f613b705486b4e9c1a9ebb00ac5/1.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/169e0f613b705486b4e9c1a9ebb00ac5/1.png)
[2<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/0d615cbd7542968ea049d8dfa9e29f66/2.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/0d615cbd7542968ea049d8dfa9e29f66/2.png)

[3<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/89abab095d65b4582d18164dbc0d04a3/3.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/89abab095d65b4582d18164dbc0d04a3/3.png)
[4<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/25db391ad49b66ddc771872849b48241/4.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/25db391ad49b66ddc771872849b48241/4.png)

[5<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/22c1f04901aef50272d2211b6f542cfe/5.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/22c1f04901aef50272d2211b6f542cfe/5.png)
[6<img src="https://gitlab.com/eneiluj/moneybuster/wikis/uploads/dae06d01b9053188d08127b96e12f4aa/6.png" width="300"/>](https://gitlab.com/eneiluj/moneybuster/wikis/uploads/dae06d01b9053188d08127b96e12f4aa/6.png)

## :notebook: License

This project is licensed under the [GNU GENERAL PUBLIC LICENSE](/LICENSE).

## :twisted_rightwards_arrows: Alternatives

There is no alternative to MoneyBuster, you should try it !

Tricount, Cospender, Splitwise etc... are doing the same job but they are not
Free or Open Source and they don't let you choose where to store your personal data.

If you care about your privacy, using FOSS is not enough,
services accessed by some software might use or sell your personal data.
