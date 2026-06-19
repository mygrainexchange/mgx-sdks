# MGX SDKs

Official client libraries for the [MGX Enterprise API](https://developers.mygrainexchange.com)
— browse anonymized grain inventory, place bids, read your team's trades, manage
cash bids, and pull market prices from your own systems.

All clients are **generated from the published OpenAPI contract** and share the
same surface; each adds a thin, hand-written ergonomic layer (OAuth2, auto-
pagination, idempotency, typed errors).

| Language | Package | Install |
| --- | --- | --- |
| TypeScript / Node | [`@mygrainexchange/sdk`](https://www.npmjs.com/package/@mygrainexchange/sdk) | `npm i @mygrainexchange/sdk` |
| Python | [`mgx`](https://pypi.org/project/mgx/) | `pip install mgx` |
| C# / .NET | [`MyGrainExchange.Api`](https://www.nuget.org/packages/MyGrainExchange.Api) | `dotnet add package MyGrainExchange.Api` |
| Java | `com.mygrainexchange:mgx-sdk` | Maven / Gradle |
| PHP | [`mygrainexchange/mgx-php`](https://packagist.org/packages/mygrainexchange/mgx-php) | `composer require mygrainexchange/mgx-php` |

## Layout

```
packages/
  node/     python/     dotnet/     java/     php/
    <generated client>      ← regenerated from the spec; do not hand-edit
    overlay/                ← hand-written ergonomics (auth, pagination, errors)
    examples/               ← runnable examples, also embedded in the docs
```

## Staying in line with the API

These clients are not hand-maintained against the live API. They are regenerated
from `openapi.json`, and CI fails on any drift:

1. **Regenerate + diff** — `node generate.mjs` (in the MGX monorepo) followed by
   `git diff --exit-code` here. A spec change that isn't regenerated fails CI.
2. **Contract tests** — each SDK runs its methods against a Prism mock booted
   from the spec, asserting spec-valid requests and responses.

Do not edit generated files by hand. Change the API + spec, regenerate, review
the diff, then release.

## License

MIT — see [LICENSE](./LICENSE).
