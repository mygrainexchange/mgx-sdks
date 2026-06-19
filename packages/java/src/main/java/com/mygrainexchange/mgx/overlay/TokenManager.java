/* Hand-written overlay — not generated. */
package com.mygrainexchange.mgx.overlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Acquires, caches, and refreshes OAuth2 access tokens against {@code <host>/oauth/token}.
 *
 * <ul>
 *   <li>client-credentials when {@code clientId} + {@code clientSecret} are given (read scopes);</li>
 *   <li>refresh_token when a {@code refreshToken} is given (user-context tokens);</li>
 *   <li>otherwise uses the static {@code accessToken} as-is.</li>
 * </ul>
 *
 * <p>Returns the <em>raw</em> token (no {@code Bearer } prefix); the client adds the prefix.
 */
public final class TokenManager {

    private final String clientId;
    private final String clientSecret;
    private final String scopes;
    private final String tokenUrl;
    private final OkHttpClient http;

    private String token;
    private String refreshToken;
    private long expiresAtMillis;

    public TokenManager(MgxClient.AuthOptions opts, String baseUrl, OkHttpClient http) {
        this.clientId = opts.clientId;
        this.clientSecret = opts.clientSecret;
        this.scopes = opts.scopes == null ? "" : String.join(" ", opts.scopes);
        this.tokenUrl = opts.tokenUrl != null ? opts.tokenUrl : deriveTokenUrl(baseUrl);
        this.http = http;
        this.token = opts.accessToken;
        this.refreshToken = opts.refreshToken;
        // A caller-supplied token with no refresh path is treated as long-lived.
        if (opts.accessToken != null && opts.refreshToken == null) {
            this.expiresAtMillis = Long.MAX_VALUE;
        }
    }

    private static String deriveTokenUrl(String baseUrl) {
        String host = baseUrl.replaceFirst("/v1/?$", "");
        return host + "/oauth/token";
    }

    /** Returns a valid raw access token, acquiring or refreshing as needed. */
    public synchronized String getAccessToken() {
        if (token != null && System.currentTimeMillis() < expiresAtMillis) {
            return token;
        }
        if (refreshToken != null) {
            FormBody.Builder form = new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken);
            if (clientId != null) {
                form.add("client_id", clientId);
            }
            if (clientSecret != null) {
                form.add("client_secret", clientSecret);
            }
            return grant(form.build());
        }
        if (clientId != null && clientSecret != null) {
            FormBody.Builder form = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("scope", scopes);
            return grant(form.build());
        }
        if (token != null) {
            return token;
        }
        throw new IllegalStateException(
                "MGX SDK: no credentials configured. Provide clientId + clientSecret, or an accessToken.");
    }

    private String grant(FormBody body) {
        Request request = new Request.Builder()
                .url(tokenUrl)
                .header("Accept", "application/json")
                .post(body)
                .build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            String text = respBody != null ? respBody.string() : "";
            if (!response.isSuccessful()) {
                throw new MgxApiError(
                        response.code(),
                        "oauth_error",
                        "MGX SDK: OAuth2 token request failed (" + response.code() + ").",
                        null);
            }
            JsonObject data = JsonParser.parseString(text).getAsJsonObject();
            this.token = data.get("access_token").getAsString();
            long expiresIn = data.has("expires_in") && !data.get("expires_in").isJsonNull()
                    ? data.get("expires_in").getAsLong() : 3600L;
            // Refresh 60s early to avoid edge-of-expiry races.
            this.expiresAtMillis = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            if (data.has("refresh_token") && !data.get("refresh_token").isJsonNull()) {
                this.refreshToken = data.get("refresh_token").getAsString();
            }
            return this.token;
        } catch (IOException e) {
            throw new UncheckedIOException("MGX SDK: OAuth2 token request failed.", e);
        }
    }
}
