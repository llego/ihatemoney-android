# Project Rules

## Identity

- **IHateMoney for Android** — a fork of [MoneyBuster](https://gitlab.com/eneiluj/moneybuster); a simple Material-style Android client for self-hosted [IHateMoney](https://ihatemoney.org). Completely vibe-coded.
- Repo: `llego/ihatemoney-android` (origin). Upstream `eneiluj/moneybuster.git` is `upstream`.
- Credit and link MoneyBuster / PhoneTrack-Android / Nextcloud Notes in README and docs. Never impersonate either upstream brand.

## Environment (NixOS)

- Build/test via the Nix shell; no apt/brew/pip/home-manager.

## Build & test

```sh
nix-shell --run './gradlew :app:assembleDevDebug :app:testDevDebugUnitTest --no-daemon --console=plain'
```

- `dev` flavor installs as "IHateMoney for Android Dev" (side-by-side); `normal` is the standalone release app.

## CI & releases

- GitHub Actions is the CI. The upstream `.gitlab-ci.yml` was removed; do not reintroduce GitLab CI.
- `.github/workflows/ci.yml` — lint + unit tests + `assembleDevDebug` on every PR and push to `master`.
- `.github/workflows/release.yml` — on `release: published`, builds and signs `:app:assembleNormalRelease` and attaches `IHateMoney-<tag>.apk` to the release's assets.
- Release APK is signed with `-Pandroid.injected.signing.*` Gradle properties (the keystore is never committed). GitHub secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- The release keystore (`~/.android/ihatemoney-release.jks`, alias `ihatemoney`) is a must-never-lose credential — back it up with the passwords, since Android updates must be signed with the same key.
- Release flow: bump **`versionCode`** (must increase; drives update ordering) and **`versionName`** (must match the tag; user-facing) in `app/build.gradle`'s `defaultConfig`, tag `vX.Y.Z`, publish the release; the signed APK attaches automatically. Do not change the signing setup or keystore without asking.

## State & scope

- **IHateMoney syncing is the priority.** IHateMoney is tested against a self-hosted instance.
- **Cospend / Nextcloud is NOT tested**; pending removal as its own effort. Preserve existing data and locales.
- Keep it simple: fast expense entry, minimal navigation churn. English UI must use "expense" (never "bill"); Swedish translations maintained.
- Package/applicationId is `net.llego.ihatemoney` (renamed from the upstream `net.eneiluj.moneybuster`). Installing it now counts as a fresh app, not an update of an older build.

## Workflow

- Do **not** push to any branch (`master` or a feature branch) without explicit user confirmation. Commit locally, then ask the user before pushing.
- Do **not** create pull requests spontaneously. Only open a branch/PR when the user explicitly asks for one (e.g. "push", "open a PR", "set up a branch").
- Use a **branch → pull request → merge** flow on `origin`, not direct pushes to `master`.
- A self-hosted **PR Agent** (GitHub Action) reviews pull requests. Keep PRs focused; use `push` to create a branch and open a PR for review before merging.

## Code conventions learned

- Use View Binding where touched; small single-purpose classes favored over god-activities (see `EmptyStateController`, `MemberDialogs`, `ColorPickerDialog`).
- Do not edit `gradle/verification-metadata.xml` wholesale; run `--write-verification-metadata` only for reviewed changes, and never disable verification.

## Gotchas

- Dependency signing/checksum verification blocks new deps; update the metadata selectively.
