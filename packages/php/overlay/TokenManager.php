<?php

/* Hand-written overlay — not generated. */

declare(strict_types=1);

namespace MyGrainExchange\Mgx\Overlay;

use GuzzleHttp\Client as HttpClient;
use GuzzleHttp\ClientInterface;
use GuzzleHttp\Exception\GuzzleException;

/**
 * Acquires, caches, and refreshes OAuth2 access tokens against
 * `<host>/oauth/token`.
 *
 * - **client_credentials** when `clientId` + `clientSecret` are given (read scopes);
 * - **refresh_token** when a `refreshToken` is given (user-context tokens);
 * - otherwise uses the static `accessToken` as-is.
 *
 * The returned token is the raw access token — the generated client prepends the
 * `Bearer ` prefix itself when building the Authorization header.
 */
class TokenManager
{
    private ?string $token;
    private ?string $refreshToken;
    private float $expiresAt = 0.0;
    private ClientInterface $http;

    /**
     * @param array{
     *   clientId?: string|null,
     *   clientSecret?: string|null,
     *   scopes?: list<string>,
     *   accessToken?: string|null,
     *   refreshToken?: string|null,
     *   tokenUrl?: string|null
     * } $options
     */
    public function __construct(
        private readonly array $options,
        private readonly string $baseUrl,
        ?ClientInterface $http = null,
    ) {
        $this->token = $options['accessToken'] ?? null;
        $this->refreshToken = $options['refreshToken'] ?? null;
        $this->http = $http ?? new HttpClient();

        // A caller-supplied token with no refresh path is treated as long-lived.
        if (!empty($options['accessToken']) && empty($options['refreshToken'])) {
            $this->expiresAt = INF;
        }
    }

    /**
     * Return a valid raw access token, acquiring or refreshing one if needed.
     *
     * @throws MgxApiError when no credentials are configured or the grant fails
     */
    public function accessToken(): string
    {
        if ($this->token !== null && microtime(true) < $this->expiresAt) {
            return $this->token;
        }

        if ($this->refreshToken !== null) {
            return $this->grant([
                'grant_type' => 'refresh_token',
                'refresh_token' => $this->refreshToken,
                'client_id' => $this->options['clientId'] ?? '',
                'client_secret' => $this->options['clientSecret'] ?? '',
            ]);
        }

        if (!empty($this->options['clientId']) && !empty($this->options['clientSecret'])) {
            return $this->grant([
                'grant_type' => 'client_credentials',
                'client_id' => $this->options['clientId'],
                'client_secret' => $this->options['clientSecret'],
                'scope' => implode(' ', $this->options['scopes'] ?? []),
            ]);
        }

        if ($this->token !== null) {
            return $this->token;
        }

        throw new MgxApiError(
            401,
            'no_credentials',
            'MGX SDK: no credentials configured. Provide clientId + clientSecret, or an accessToken.',
        );
    }

    private function tokenUrl(): string
    {
        if (!empty($this->options['tokenUrl'])) {
            return $this->options['tokenUrl'];
        }

        // Strip a trailing /v1 (with optional slash) and append the OAuth path.
        $base = (string) preg_replace('#/v1/?$#', '', $this->baseUrl);

        return $base . '/oauth/token';
    }

    /**
     * @param array<string, string> $body
     *
     * @throws MgxApiError
     */
    private function grant(array $body): string
    {
        try {
            $response = $this->http->request('POST', $this->tokenUrl(), [
                'headers' => [
                    'Content-Type' => 'application/x-www-form-urlencoded',
                    'Accept' => 'application/json',
                ],
                'form_params' => $body,
                'http_errors' => false,
            ]);
        } catch (GuzzleException $e) {
            throw new MgxApiError(0, 'oauth_request_failed', 'MGX SDK: OAuth2 token request failed.', [], $e);
        }

        $status = $response->getStatusCode();
        if ($status < 200 || $status >= 300) {
            throw new MgxApiError(
                $status,
                'oauth_token_failed',
                "MGX SDK: OAuth2 token request failed ({$status}).",
            );
        }

        /** @var array<string, mixed>|null $data */
        $data = json_decode((string) $response->getBody(), true);
        if (!is_array($data) || !isset($data['access_token']) || !is_string($data['access_token'])) {
            throw new MgxApiError(500, 'oauth_token_malformed', 'MGX SDK: OAuth2 token response was malformed.');
        }

        $this->token = $data['access_token'];
        $expiresIn = isset($data['expires_in']) ? (int) $data['expires_in'] : 3600;
        $this->expiresAt = microtime(true) + max(0, $expiresIn - 60);
        if (isset($data['refresh_token']) && is_string($data['refresh_token'])) {
            $this->refreshToken = $data['refresh_token'];
        }

        return $this->token;
    }
}
