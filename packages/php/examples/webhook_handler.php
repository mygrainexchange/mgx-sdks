<?php

/**
 * Verifying an inbound MGX webhook.
 *
 * MGX signs every webhook delivery with the header
 *   MGX-Signature: t=<unix>,v1=<hex hmac>
 * where the HMAC is SHA-256 over "{t}.{rawBody}" keyed with your subscription's
 * signing secret. Always verify the signature against the EXACT raw request body
 * before trusting the event.
 *
 * Run as a tiny endpoint, or adapt to your framework's request object.
 */

declare(strict_types=1);

require __DIR__ . '/../vendor/autoload.php';

use MyGrainExchange\Mgx\Overlay\MgxClient;
use MyGrainExchange\Mgx\Overlay\MgxSignatureError;

use function MyGrainExchange\Mgx\Overlay\verifyWebhook;

// Your subscription's signing secret (store it in config / an env var).
$secret = getenv('MGX_WEBHOOK_SECRET') ?: 'whsec_example';

// The EXACT raw request body — do NOT use a re-serialized array.
$rawBody = file_get_contents('php://input') ?: '';
$signatureHeader = $_SERVER['HTTP_MGX_SIGNATURE'] ?? '';

try {
    // Option A: the standalone helper.
    $event = verifyWebhook($rawBody, $signatureHeader, $secret);

    // Option B: via a client instance (same result).
    // $mgx = new MgxClient(accessToken: getenv('MGX_TOKEN') ?: '');
    // $event = $mgx->webhooks()->verify($rawBody, $signatureHeader, $secret);

    // The event is a typed \MyGrainExchange\Mgx\Model\WebhookEvent.
    switch ((string) $event->getType()) {
        case 'trade.created':
            error_log('New trade: ' . json_encode($event->getData()));
            break;
        case 'cashbid.offer_received':
            error_log('Offer received: ' . json_encode($event->getData()));
            break;
        default:
            error_log('Unhandled event: ' . $event->getType());
    }

    http_response_code(200);
    echo 'ok';
} catch (MgxSignatureError $e) {
    // Reject anything we cannot verify.
    http_response_code(400);
    error_log('Webhook rejected: ' . $e->getMessage());
    echo 'invalid signature';
}
