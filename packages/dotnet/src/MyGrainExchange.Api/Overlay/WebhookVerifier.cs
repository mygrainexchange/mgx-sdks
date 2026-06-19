/* Hand-written overlay — not generated. Inbound webhook verification. */
using System;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json;
using MyGrainExchange.Api.Model;

namespace MyGrainExchange.Api.Overlay
{
    /// <summary>Thrown when an inbound webhook signature cannot be verified.</summary>
    public sealed class MgxSignatureError : Exception
    {
        public MgxSignatureError(string message) : base(message) { }
    }

    /// <summary>
    /// Verifies inbound MGX webhook signatures.
    ///
    /// The signature header is <c>MGX-Signature: t=&lt;unix&gt;,v1=&lt;hex hmac&gt;</c>.
    /// The HMAC is SHA-256 over <c>"{t}.{rawBody}"</c> keyed with the subscription's
    /// signing secret. Pass the EXACT raw request body (not a re-serialized object).
    /// </summary>
    public static class WebhookVerifier
    {
        /// <summary>Max age of the signed timestamp, in seconds. Default 300 (5 minutes).</summary>
        public const int DefaultToleranceSeconds = 300;

        /// <summary>
        /// Verifies an inbound MGX webhook and returns the typed event. Throws
        /// <see cref="MgxSignatureError"/> on a malformed header, a bad signature,
        /// or a stale timestamp.
        /// </summary>
        /// <param name="rawBody">The exact raw request body bytes as a UTF-8 string.</param>
        /// <param name="signatureHeader">The value of the <c>MGX-Signature</c> header.</param>
        /// <param name="secret">The subscription's signing secret.</param>
        /// <param name="toleranceSeconds">Max age of the signed timestamp, in seconds. Default 300.</param>
        /// <param name="now">Override "now" (unix seconds) — for testing.</param>
        public static WebhookEvent Verify(
            string rawBody,
            string signatureHeader,
            string secret,
            int toleranceSeconds = DefaultToleranceSeconds,
            long? now = null)
        {
            var (t, v1) = ParseSignatureHeader(signatureHeader);

            var nowSeconds = now ?? DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            if (Math.Abs(nowSeconds - t) > toleranceSeconds)
            {
                throw new MgxSignatureError("Webhook timestamp is outside the tolerance window.");
            }

            var expected = ComputeHexHmac(secret, $"{t}.{rawBody}");

            // Compare the hex digests as equal-length byte buffers in constant time.
            // Differing lengths short-circuit; FixedTimeEquals guards the contents.
            var expectedBytes = Encoding.ASCII.GetBytes(expected);
            var actualBytes = Encoding.ASCII.GetBytes(v1);
            if (expectedBytes.Length != actualBytes.Length ||
                !CryptographicOperations.FixedTimeEquals(expectedBytes, actualBytes))
            {
                throw new MgxSignatureError("Webhook signature does not match.");
            }

            try
            {
                var evt = JsonConvert.DeserializeObject<WebhookEvent>(rawBody);
                if (evt == null)
                {
                    throw new MgxSignatureError("Webhook body could not be parsed.");
                }
                return evt;
            }
            catch (JsonException ex)
            {
                throw new MgxSignatureError($"Webhook body could not be parsed: {ex.Message}");
            }
        }

        private static (long t, string v1) ParseSignatureHeader(string header)
        {
            string tRaw = null;
            string v1 = null;

            if (!string.IsNullOrEmpty(header))
            {
                foreach (var segment in header.Split(','))
                {
                    var idx = segment.IndexOf('=');
                    if (idx == -1) continue;
                    var key = segment.Substring(0, idx).Trim();
                    var value = segment.Substring(idx + 1).Trim();
                    if (key == "t") tRaw = value;
                    else if (key == "v1") v1 = value;
                }
            }

            if (string.IsNullOrEmpty(tRaw) || string.IsNullOrEmpty(v1) ||
                !long.TryParse(tRaw, NumberStyles.Integer, CultureInfo.InvariantCulture, out var t))
            {
                throw new MgxSignatureError("Malformed MGX-Signature header.");
            }

            return (t, v1);
        }

        private static string ComputeHexHmac(string secret, string message)
        {
            using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
            var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(message));
            var sb = new StringBuilder(hash.Length * 2);
            foreach (var b in hash)
            {
                sb.Append(b.ToString("x2", CultureInfo.InvariantCulture));
            }
            return sb.ToString();
        }
    }
}
