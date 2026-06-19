<?php

/** Unit test for webhook signature verification (no network). */

declare(strict_types=1);

require __DIR__ . '/../vendor/autoload.php';

use MyGrainExchange\Mgx\Overlay\MgxClient;
use MyGrainExchange\Mgx\Overlay\MgxSignatureError;

use function MyGrainExchange\Mgx\Overlay\verifyWebhook;

$secret = 'whsec_test';
$body = json_encode([
    'id' => 'evt_6Yh2',
    'type' => 'trade.created',
    'created_at' => '2026-06-18T00:00:00Z',
    'data' => ['id' => 'trd_1'],
]);
$t = time();

$sign = static fn (int $ts, string $b, string $s): string =>
    "t={$ts},v1=" . hash_hmac('sha256', "{$ts}.{$b}", $s);

$failures = 0;

/**
 * @param callable(): void $fn
 */
function check(string $name, callable $fn): void
{
    global $failures;
    try {
        $fn();
        echo "  ok  {$name}\n";
    } catch (\Throwable $e) {
        $failures++;
        echo "  FAIL {$name}: " . $e->getMessage() . "\n";
    }
}

/**
 * Assert that the callable throws an MgxSignatureError.
 *
 * @param callable(): mixed $fn
 */
function assertRejects(callable $fn): void
{
    try {
        $fn();
    } catch (MgxSignatureError $e) {
        return;
    }
    throw new \RuntimeException('expected MgxSignatureError to be thrown');
}

check('valid signature returns the typed event', function () use ($body, $secret, $t, $sign) {
    $evt = verifyWebhook($body, $sign($t, $body, $secret), $secret);
    if (!$evt instanceof \MyGrainExchange\Mgx\Model\WebhookEvent) {
        throw new \RuntimeException('not a WebhookEvent');
    }
    if ((string) $evt->getType() !== 'trade.created') {
        throw new \RuntimeException('wrong type: ' . $evt->getType());
    }
    if ($evt->getId() !== 'evt_6Yh2') {
        throw new \RuntimeException('wrong id: ' . $evt->getId());
    }
});

check('client.webhooks().verify works', function () use ($body, $secret, $t, $sign) {
    $mgx = new MgxClient(accessToken: 'x');
    $evt = $mgx->webhooks()->verify($body, $sign($t, $body, $secret), $secret);
    if ((string) $evt->getType() !== 'trade.created') {
        throw new \RuntimeException('wrong type: ' . $evt->getType());
    }
});

check('tampered body is rejected', function () use ($body, $secret, $t, $sign) {
    assertRejects(fn () => verifyWebhook($body . ' ', $sign($t, $body, $secret), $secret));
});

check('wrong secret is rejected', function () use ($body, $secret, $t, $sign) {
    assertRejects(fn () => verifyWebhook($body, $sign($t, $body, $secret), 'whsec_wrong'));
});

check('stale timestamp is rejected', function () use ($body, $secret, $t, $sign) {
    assertRejects(fn () => verifyWebhook($body, $sign($t - 9999, $body, $secret), $secret));
});

check('malformed header is rejected', function () use ($body, $secret) {
    assertRejects(fn () => verifyWebhook($body, 'garbage', $secret));
});

echo $failures === 0 ? "\nAll webhook-verify checks passed.\n" : "\n{$failures} failed.\n";
exit($failures === 0 ? 0 : 1);
