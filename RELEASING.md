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
| **PyPI** | `mgx` | *(none — OIDC)* | **Trusted publishing**, no token. Add a GitHub publisher on PyPI: project `mgx`, owner `mygrainexchange`, repo `mgx-sdks`, workflow `release.yml`, environment `pypi`. Create a matching GitHub **Environment** named `pypi` (repo → Settings → Environments). |
| **NuGet** | `MyGrainExchange.Api` | *(none — OIDC)* | **Trusted publishing**, no key. On nuget.org create a trusted-publishing policy (owner `mygrainexchange`, repo `mgx-sdks`, workflow `release.yml`). Add a repo **Variable** `NUGET_USER` = your nuget.org username, and a GitHub **Environment** named `nuget`. |
| **Packagist** | `mygrainexchange/mgx-php` | `PACKAGIST_TOKEN`, `MGX_PHP_SPLIT_TOKEN` | `packages/php` is split into the standalone repo `mygrainexchange/mgx-php` on each tag (Packagist can't read a subdir). See below. |
| **Maven Central** | `io.github.mygrainexchange:mgx-sdk` | `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `MAVEN_GPG_KEY`, `MAVEN_GPG_PASSPHRASE` | Sonatype **Central Portal** token (server id `central`) + a GPG key. Namespace `io.github.mygrainexchange` (GitHub-verified). Publishes via `central-publishing-maven-plugin` in the pom. |

### Packagist + PHP split repo (one-time)
Packagist requires `composer.json` at the repo root and can't read a monorepo
subdirectory. So the `release` workflow's `split-php` job mirrors `packages/php`
into a standalone repo **`mygrainexchange/mgx-php`** (composer.json at root) on each
`v*` tag, and Packagist points at that repo.
1. Create an empty GitHub repo `mygrainexchange/mgx-php`.
2. Create a PAT with **write** access to `mgx-php`, store it as secret `MGX_PHP_SPLIT_TOKEN`.
3. After the first tag (which populates `mgx-php`), submit **`https://github.com/mygrainexchange/mgx-php`**
   at packagist.org/packages/submit. Add `PACKAGIST_TOKEN` so later tags auto-refresh it.

### Maven Central (most involved)
Publishes to the Sonatype **Central Portal** under the GitHub-verified namespace
`io.github.mygrainexchange`, via the `central-publishing-maven-plugin` already wired
into `packages/java/pom.xml` (server id `central`; `.github/maven-settings.xml` reads
the token from env). The `maven` job soft-fails (`continue-on-error`) so it never
blocks the other registries. To enable it, add four secrets:
1. **`OSSRH_USERNAME` / `OSSRH_PASSWORD`** — a Central Portal **user token** (Portal →
   account → Generate User Token). *Not* your login password. ✅ done
2. **`MAVEN_GPG_KEY`** — armored private key: `gpg --armor --export-secret-keys <KEYID>`
   (and publish the public key: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`).
3. **`MAVEN_GPG_PASSPHRASE`** — that key's passphrase.

The release job imports the key and runs `mvn -P sign-artifacts deploy`, which signs,
uploads, and (with `autoPublish`) releases once Central validates.

> Simpler alternative: publish the Java package to **GitHub Packages** instead of
> the Central Portal (no GPG). Ask and we'll switch the `maven` job over.

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
