<?php

/**
 * MGX PHP SDK — quickstart. Run: `php examples/quickstart.php`.
 * These snippets are embedded in the developer portal.
 */

declare(strict_types=1);

require __DIR__ . '/../vendor/autoload.php';

use MyGrainExchange\Mgx\Overlay\MgxApiError;
use MyGrainExchange\Mgx\Overlay\MgxClient;

$mgx = new MgxClient(
    clientId: getenv('MGX_CLIENT_ID') ?: null,
    clientSecret: getenv('MGX_CLIENT_SECRET') ?: null,
    scopes: ['inventory.read', 'market.read'],
    // baseUrl: 'https://dashboard.mgx.test/v1', // for local development
);

// Browse anonymized inventory — auto-paginates across the items/next envelope.
foreach ($mgx->inventory()->list(['commodity' => 'wheat', 'minQuantity' => 50]) as $lot) {
    echo $lot->getId(), ' ', $lot->getQuantityMt(), ' ', $lot->getAskingPrice()?->getAmount(), PHP_EOL;
}

// Current market prices.
$prices = $mgx->market()->prices(['commodity' => 'wheat,canola']);
foreach ($prices as $p) {
    echo $p->getCommodity()?->getSlug(), ': ', $p->getPrice()?->getAmount(), PHP_EOL;
}

// Place a bid (requires a user-context token with bids.write). The SDK adds an
// Idempotency-Key automatically.
try {
    $bid = $mgx->inventory()->placeBid('inv_3Kd9aZ', [
        'quantity_mt' => 50,
        'price' => ['amount' => 312.5],
        'delivery' => ['from' => '2026-08-01', 'to' => '2026-09-30'],
    ]);
    echo 'placed ', $bid?->getId(), ' ', $bid?->getStatus(), PHP_EOL;
} catch (MgxApiError $e) {
    fwrite(STDERR, sprintf(
        "%d %s %s %s\n",
        $e->status(),
        $e->code(),
        $e->getMessage(),
        json_encode($e->fieldErrors()),
    ));
}
