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
- Released/signed configs come later — do not change signing without asking.

## State & scope

- **IHateMoney syncing is the priority.** IHateMoney is tested against a self-hosted instance.
- **Cospend / Nextcloud is NOT tested**; pending removal as its own effort. Preserve existing data and locales.
- Keep it simple: fast expense entry, minimal navigation churn. English UI must use "expense" (never "bill"); Swedish translations maintained.
- Package/applicationId is `net.llego.ihatemoney` (renamed from the upstream `net.eneiluj.moneybuster`). Installing it now counts as a fresh app, not an update of an older build.

## Workflow

- Use a **branch → pull request → merge** flow on `origin`, not direct pushes to `master`.
- **Qodo PR Agent** is enabled on this repo to review pull requests. Keep PRs focused; use `push` to create a branch and open a PR for review before merging.

## Code conventions learned

- Use View Binding where touched; small single-purpose classes favored over god-activities (see `EmptyStateController`, `MemberDialogs`, `ColorPickerDialog`).
- Do not edit `gradle/verification-metadata.xml` wholesale; run `--write-verification-metadata` only for reviewed changes, and never disable verification.

## Gotchas

- Dependency signing/checksum verification blocks new deps; update the metadata selectivally.
