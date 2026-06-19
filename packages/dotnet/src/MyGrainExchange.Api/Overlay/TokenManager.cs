/* Hand-written overlay — not generated. */
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace MyGrainExchange.Api.Overlay
{
    /// <summary>Authentication inputs for <see cref="MgxClient"/>. Not sealed: MgxClientOptions extends it.</summary>
    public class AuthOptions
    {
        /// <summary>OAuth2 client id (client-credentials or authorization-code app).</summary>
        public string ClientId { get; set; }

        /// <summary>OAuth2 client secret.</summary>
        public string ClientSecret { get; set; }

        /// <summary>Scopes to request on a client-credentials grant.</summary>
        public IReadOnlyList<string> Scopes { get; set; }

        /// <summary>A pre-obtained access token (e.g. from a Login-with-MGX authorization-code flow).</summary>
        public string AccessToken { get; set; }

        /// <summary>A refresh token to renew an authorization-code access token.</summary>
        public string RefreshToken { get; set; }

        /// <summary>Override the token endpoint. Defaults to <c>&lt;host&gt;/oauth/token</c>.</summary>
        public string TokenUrl { get; set; }
    }

    /// <summary>
    /// Acquires, caches, and refreshes OAuth2 access tokens.
    ///
    /// <list type="bullet">
    /// <item>client-credentials when <see cref="AuthOptions.ClientId"/> + <see cref="AuthOptions.ClientSecret"/> are given;</item>
    /// <item>refresh_token when a <see cref="AuthOptions.RefreshToken"/> is given;</item>
    /// <item>otherwise the static <see cref="AuthOptions.AccessToken"/> is used as-is.</item>
    /// </list>
    /// </summary>
    public sealed class TokenManager
    {
        private readonly AuthOptions _opts;
        private readonly string _baseUrl;
        private readonly HttpClient _http;
        private readonly SemaphoreSlim _gate = new SemaphoreSlim(1, 1);

        private string _token;
        private string _refreshToken;
        private DateTimeOffset _expiresAt = DateTimeOffset.MinValue;

        public TokenManager(AuthOptions opts, string baseUrl, HttpClient http = null)
        {
            _opts = opts ?? throw new ArgumentNullException(nameof(opts));
            _baseUrl = baseUrl;
            _http = http ?? new HttpClient();

            _token = opts.AccessToken;
            _refreshToken = opts.RefreshToken;
            // A caller-supplied token with no refresh path is treated as long-lived.
            if (!string.IsNullOrEmpty(opts.AccessToken) && string.IsNullOrEmpty(opts.RefreshToken))
                _expiresAt = DateTimeOffset.MaxValue;
        }

        private string TokenUrl
        {
            get
            {
                if (!string.IsNullOrEmpty(_opts.TokenUrl)) return _opts.TokenUrl;
                // strip a trailing "/v1" (or "/v1/") then append the OAuth path.
                var host = _baseUrl ?? string.Empty;
                if (host.EndsWith("/")) host = host.Substring(0, host.Length - 1);
                if (host.EndsWith("/v1")) host = host.Substring(0, host.Length - 3);
                return host + "/oauth/token";
            }
        }

        /// <summary>Returns a valid bearer token, refreshing or fetching one if needed.</summary>
        public async Task<string> GetAccessTokenAsync(CancellationToken cancellationToken = default)
        {
            if (!string.IsNullOrEmpty(_token) && DateTimeOffset.UtcNow < _expiresAt)
                return _token;

            await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
            try
            {
                // Re-check after acquiring the lock (another caller may have refreshed).
                if (!string.IsNullOrEmpty(_token) && DateTimeOffset.UtcNow < _expiresAt)
                    return _token;

                if (!string.IsNullOrEmpty(_refreshToken))
                {
                    return await GrantAsync(new Dictionary<string, string>
                    {
                        ["grant_type"] = "refresh_token",
                        ["refresh_token"] = _refreshToken,
                        ["client_id"] = _opts.ClientId ?? string.Empty,
                        ["client_secret"] = _opts.ClientSecret ?? string.Empty,
                    }, cancellationToken).ConfigureAwait(false);
                }

                if (!string.IsNullOrEmpty(_opts.ClientId) && !string.IsNullOrEmpty(_opts.ClientSecret))
                {
                    return await GrantAsync(new Dictionary<string, string>
                    {
                        ["grant_type"] = "client_credentials",
                        ["client_id"] = _opts.ClientId,
                        ["client_secret"] = _opts.ClientSecret,
                        ["scope"] = _opts.Scopes != null ? string.Join(" ", _opts.Scopes) : string.Empty,
                    }, cancellationToken).ConfigureAwait(false);
                }

                if (!string.IsNullOrEmpty(_token))
                    return _token;

                throw new InvalidOperationException(
                    "MGX SDK: no credentials configured. Provide ClientId + ClientSecret, or an AccessToken.");
            }
            finally
            {
                _gate.Release();
            }
        }

        private async Task<string> GrantAsync(Dictionary<string, string> body, CancellationToken cancellationToken)
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, TokenUrl)
            {
                Content = new FormUrlEncodedContent(body),
            };
            req.Headers.Accept.ParseAdd("application/json");

            using var res = await _http.SendAsync(req, cancellationToken).ConfigureAwait(false);
            if (!res.IsSuccessStatusCode)
                throw new InvalidOperationException(
                    $"MGX SDK: OAuth2 token request failed ({(int)res.StatusCode}).");

            var json = await res.Content.ReadAsStringAsync().ConfigureAwait(false);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            _token = root.TryGetProperty("access_token", out var at) ? at.GetString() : null;
            if (string.IsNullOrEmpty(_token))
                throw new InvalidOperationException("MGX SDK: OAuth2 token response had no access_token.");

            var expiresIn = root.TryGetProperty("expires_in", out var ei) && ei.ValueKind == JsonValueKind.Number
                ? ei.GetInt32() : 3600;
            // Renew 60s early to avoid edge-of-expiry races.
            _expiresAt = DateTimeOffset.UtcNow.AddSeconds(Math.Max(expiresIn - 60, 0));

            if (root.TryGetProperty("refresh_token", out var rt) && rt.ValueKind == JsonValueKind.String)
                _refreshToken = rt.GetString();

            return _token;
        }
    }
}
