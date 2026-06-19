// Webhook signature verification test for the MGX .NET SDK overlay.
//
// NOTE: Authored on a machine without the .NET toolchain installed, so it was
// verified by inspection, not compiled. To run it, add a .csproj that references
// the package and includes this file, then:
//
//   dotnet run --project test
//
// It mirrors the cross-SDK webhook test cases: a valid signature yields the typed
// event; a tampered body, a wrong secret, a stale timestamp, and a malformed
// header are all rejected with MgxSignatureError.
using System;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using MyGrainExchange.Api.Model;
using MyGrainExchange.Api.Overlay;

internal static class WebhookVerify
{
    private const string Secret = "whsec_test_secret";

    // A fixed reference "now" so the signed timestamp is deterministic.
    private const long Now = 1_750_000_000;

    private static readonly string Body =
        "{\"id\":\"evt_6Yh2\",\"type\":\"trade.created\",\"created_at\":\"2026-06-18T12:00:00Z\",\"data\":{\"id\":\"trd_1\"}}";

    private static int _failures;

    private static async System.Threading.Tasks.Task<int> Main()
    {
        // 1. Valid signature -> typed event.
        Run("valid signature yields typed event", () =>
        {
            var header = SignHeader(Now, Body, Secret);
            var evt = WebhookVerifier.Verify(Body, header, Secret, now: Now);
            Expect(evt.Id == "evt_6Yh2", $"unexpected id {evt.Id}");
            Expect(evt.Type == WebhookEventType.TradeCreated, $"unexpected type {evt.Type}");
        });

        // 2. Tampered body -> rejected.
        ExpectRejected("tampered body is rejected", () =>
        {
            var header = SignHeader(Now, Body, Secret);
            var tampered = Body.Replace("trd_1", "trd_HACKED");
            WebhookVerifier.Verify(tampered, header, Secret, now: Now);
        });

        // 3. Wrong secret -> rejected.
        ExpectRejected("wrong secret is rejected", () =>
        {
            var header = SignHeader(Now, Body, Secret);
            WebhookVerifier.Verify(Body, header, "whsec_WRONG", now: Now);
        });

        // 4. Stale timestamp -> rejected.
        ExpectRejected("stale timestamp is rejected", () =>
        {
            var staleT = Now - 10_000; // well beyond the 300s default tolerance
            var header = SignHeader(staleT, Body, Secret);
            WebhookVerifier.Verify(Body, header, Secret, now: Now);
        });

        // 5. Malformed header -> rejected.
        ExpectRejected("malformed header is rejected", () =>
        {
            WebhookVerifier.Verify(Body, "not-a-valid-header", Secret, now: Now);
        });

        await System.Threading.Tasks.Task.CompletedTask;
        Console.WriteLine(_failures == 0 ? "WEBHOOK VERIFY PASS" : $"WEBHOOK VERIFY FAIL ({_failures})");
        return _failures == 0 ? 0 : 1;
    }

    // Reproduces the signing scheme so the test is self-contained:
    // MGX-Signature: t=<unix>,v1=<hex hmac-sha256 of "{t}.{body}">.
    private static string SignHeader(long t, string body, string secret)
    {
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes($"{t}.{body}"));
        var sb = new StringBuilder(hash.Length * 2);
        foreach (var b in hash) sb.Append(b.ToString("x2", CultureInfo.InvariantCulture));
        return $"t={t},v1={sb}";
    }

    private static void Run(string name, Action body)
    {
        try
        {
            body();
            Console.WriteLine($"[ok] {name}");
        }
        catch (Exception e)
        {
            _failures++;
            Console.Error.WriteLine($"[fail] {name}: {e.Message}");
        }
    }

    private static void ExpectRejected(string name, Action body)
    {
        try
        {
            body();
            _failures++;
            Console.Error.WriteLine($"[fail] {name}: expected MgxSignatureError but none was thrown");
        }
        catch (MgxSignatureError)
        {
            Console.WriteLine($"[ok] {name}");
        }
        catch (Exception e)
        {
            _failures++;
            Console.Error.WriteLine($"[fail] {name}: threw {e.GetType().Name} instead of MgxSignatureError");
        }
    }

    private static void Expect(bool condition, string message)
    {
        if (!condition) throw new Exception(message);
    }
}
