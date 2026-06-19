# Releasing the MGX SDKs

All packages are versioned together (one coordinated version, currently `1.0.0`).
Two workflows drive everything:

- **`contract`** — runs on every push to `main` and every PR: builds each SDK and
  contract-tests Node/Python/PHP against a Prism mock booted from `spec/openapi.json`.
- **`release`** — runs on a `v*` tag (or a manual **Run workflow**): builds and
  publishes each package to its registry. Each registry **skips gracefully if its
  secret isn't set**, so you can turn them on one at a time.

## 1. Add the publish secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**.
Add only the ones you're ready to use; the rest are skipped.

| Registry | Package | Secret(s) | Where to get it |
| --- | --- | --- | --- |
| **npm** | `@mygrainexchange/sdk` | `NPM_TOKEN` | npmjs.com → Access Tokens → **Automation** token (publish to the `@mygrainexchange` org) |
| **PyPI** | `mgx` | `PYPI_TOKEN` | pypi.org → Account → API tokens (scope it to the `mgx` project) |
| **NuGet** | `MyGrainExchange.Api` | `NUGET_API_KEY` | nuget.org → API Keys → push scope |
| **Packagist** | `mygrainexchange/mgx-php` | `PACKAGIST_TOKEN` | packagist.org → Profile → Show API Token (and register the package once, below) |
| **Maven Central** | `com.mygrainexchange:mgx-sdk` | `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `MAVEN_GPG_KEY`, `MAVEN_GPG_PASSPHRASE` | Sonatype Central account + a GPG key (most setup — see below) |

### Packagist (one-time)
Submit the repo once at packagist.org/packages/submit (URL
`https://github.com/mygrainexchange/mgx-sdks`). After that the `release` job's
API ping tells Packagist to re-read tags. Packagist serves the `packages/php`
package straight from the Git tag — no artifact upload.

### Maven Central (most involved)
Maven Central requires a verified Sonatype namespace for `com.mygrainexchange`
**and** GPG-signed artifacts. Until that's set up, the `maven` job soft-fails
(`continue-on-error`) and never blocks the other four registries. To enable it:
1. Create a Sonatype Central account and verify the `com.mygrainexchange` namespace.
2. Generate a GPG key; add the public key to a keyserver.
3. Add `OSSRH_USERNAME` / `OSSRH_PASSWORD`, the armored private key as `MAVEN_GPG_KEY`,
   and its passphrase as `MAVEN_GPG_PASSPHRASE`.
4. Add a `<distributionManagement>` block (server id `ossrh`) to `packages/java/pom.xml`.

> Simpler alternative: publish the Java package to **GitHub Packages** instead of
> Maven Central (no Sonatype/GPG). Ask and we'll switch the `maven` job over.

## 2. Cut a release

Versions live in each package manifest (`packages/node/package.json`,
`packages/python/pyproject.toml`, `packages/java/pom.xml`,
`packages/dotnet/src/MyGrainExchange.Api/*.csproj`, `packages/php/composer.json`).
Keep them identical.

```bash
# 1. bump every package to the new version (e.g. 1.0.1), commit
git commit -am "release: v1.0.1"

# 2. tag and push — this triggers the release workflow
git tag v1.0.1
git push origin main --tags
```

Or trigger **Actions → release → Run workflow** manually (publishes whatever
versions are currently committed; registries that already have that version are
skipped via `--skip-duplicate` / registry idempotency).

## 3. Verify

Watch **Actions → release**. Each job logs either a publish or a
`::notice:: … skipping` line when its secret is absent.
