<?php

/* Hand-written overlay — not generated. Inbound webhook verification. */

declare(strict_types=1);

namespace MyGrainExchange\Mgx\Overlay;

use MyGrainExchange\Mgx\Model\WebhookEvent;
use MyGrainExchange\Mgx\ObjectSerializer;

/** Thrown when an inbound webhook signature cannot be verified. */
class MgxSignatureError extends \Exception
{
}

/** Default max age of the signed timestamp, in seconds (5 minutes). */
const DEFAULT_WEBHOOK_TOLERANCE_SECONDS = 300;

/**
 * Verifies an inbound MGX webhook and returns the typed event.
 *
 * The signature header is `MGX-Signature: t=<unix>,v1=<hex hmac>`. The HMAC is
 * SHA-256 over `"{t}.{rawBody}"` keyed with the subscription's signing secret.
 * Pass the EXACT raw request body (not a re-serialized object). Throws
 * {@see MgxSignatureError} on a bad signature or a stale timestamp.
 *
 * @param string   $rawBody          The raw, unparsed request body.
 * @param string   $signatureHeader  The value of the `MGX-Signature` header.
 * @param string   $secret           The subscription's signing secret.
 * @param int      $toleranceSeconds Max age of the signed timestamp, in seconds.
 * @param int|null $now              Override "now" (unix seconds) — for testing.
 *
 * @throws MgxSignatureError on a malformed header, stale timestamp, or mismatch.
 */
function verifyWebhook(
    string $rawBody,
    string $signatureHeader,
    string $secret,
    int $toleranceSeconds = DEFAULT_WEBHOOK_TOLERANCE_SECONDS,
    ?int $now = null,
): WebhookEvent {
    [$t, $v1] = parseSignatureHeader($signatureHeader);

    $now = $now ?? time();
    if (abs($now - $t) > $toleranceSeconds) {
        throw new MgxSignatureError('Webhook timestamp is outside the tolerance window.');
    }

    $expected = hash_hmac('sha256', "{$t}.{$rawBody}", $secret);
    // Constant-time comparison to avoid leaking the signature via timing.
    if (!hash_equals($expected, $v1)) {
        throw new MgxSignatureError('Webhook signature does not match.');
    }

    /** @var WebhookEvent $event */
    $event = ObjectSerializer::deserialize($rawBody, '\MyGrainExchange\Mgx\Model\WebhookEvent');

    return $event;
}

/**
 * Parse a `t=<unix>,v1=<hex hmac>` signature header into its components.
 *
 * @return array{0: int, 1: string} The `[t, v1]` pair.
 *
 * @throws MgxSignatureError when the header is missing required fields.
 *
 * @internal
 */
function parseSignatureHeader(string $header): array
{
    $parts = [];
    foreach (explode(',', $header) as $segment) {
        $idx = strpos($segment, '=');
        if ($idx === false) {
            continue;
        }
        $key = trim(substr($segment, 0, $idx));
        $parts[$key] = trim(substr($segment, $idx + 1));
    }

    $t = isset($parts['t']) ? filter_var($parts['t'], FILTER_VALIDATE_INT) : false;
    if (empty($parts['t']) || $t === false || empty($parts['v1'])) {
        throw new MgxSignatureError('Malformed MGX-Signature header.');
    }

    return [$t, $parts['v1']];
}
