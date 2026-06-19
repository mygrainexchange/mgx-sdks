<?php

/**
 * Contract smoke test against a Prism mock of the OpenAPI spec.
 *
 * Run:
 *   node tooling/sdk/contract/serve-mock.mjs --port 4015 &
 *   MGX_MOCK_URL=http://127.0.0.1:4015 php test/contract_smoke.php
 */

declare(strict_types=1);

require __DIR__ . '/../vendor/autoload.php';

use MyGrainExchange\Mgx\Overlay\MgxClient;

$failures = 0;

/**
 * @param callable(): mixed $fn
 */
function check(string $label, callable $fn): void
{
    global $failures;
    try {
        $fn();
        echo "ok   {$label}\n";
    } catch (\Throwable $e) {
        $failures++;
        echo "FAIL {$label}: " . get_class($e) . ' ' . $e->getMessage() . "\n";
    }
}

$baseUrl = getenv('MGX_MOCK_URL') ?: 'http://127.0.0.1:4015';

$mgx = new MgxClient(
    accessToken: 'mock',
    baseUrl: $baseUrl,
);

check('inventory.list (take 1)', function () use ($mgx) {
    foreach ($mgx->inventory()->list() as $lot) {
        if (!method_exists($lot, 'getId')) {
            throw new \RuntimeException('item missing getId()');
        }
        break; // take 1
    }
});

check('inventory.get', function () use ($mgx) {
    $mgx->inventory()->get('inv_3Kd9aZ');
});

check('inventory.filters', function () use ($mgx) {
    $filters = $mgx->inventory()->filters();
    if (!is_object($filters)) {
        throw new \RuntimeException('filters not an object');
    }
});

check('market.commodities', function () use ($mgx) {
    $items = $mgx->market()->commodities();
    if (!is_array($items)) {
        throw new \RuntimeException('commodities not an array');
    }
});

check('market.prices', function () use ($mgx) {
    $items = $mgx->market()->prices(['commodity' => 'wheat']);
    if (!is_array($items)) {
        throw new \RuntimeException('prices not an array');
    }
});

check('market.history', function () use ($mgx) {
    $history = $mgx->market()->history('wheat');
    if (!is_object($history)) {
        throw new \RuntimeException('history not an object');
    }
});

check('teams.list', function () use ($mgx) {
    $items = $mgx->teams()->list();
    if (!is_array($items)) {
        throw new \RuntimeException('teams not an array');
    }
});

if ($failures > 0) {
    echo "\n{$failures} check(s) FAILED\n";
    exit(1);
}

echo "\nall checks passed\n";
exit(0);
