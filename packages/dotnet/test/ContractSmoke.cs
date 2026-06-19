// Contract smoke test for the MGX .NET SDK overlay.
//
// NOTE: This was authored on a machine without the .NET toolchain installed, so
// it was verified by inspection, not compiled. It is intended to run against the
// shared Prism mock:
//
//   node /Users/connor/Code/MGX/tooling/sdk/contract/serve-mock.mjs --port 4016 &
//   dotnet run --project test    # (after adding a matching .csproj that references the package)
//
// The mock serves the OpenAPI examples and does not enforce OAuth, so a static
// AccessToken is sufficient. It exercises: a paginated list (IAsyncEnumerable),
// a singular get (envelope .Data unwrap), a list endpoint (.Items unwrap), and
// the typed-error path.
using System;
using System.Threading.Tasks;
using MyGrainExchange.Api.Model;
using MyGrainExchange.Api.Overlay;

internal static class ContractSmoke
{
    private static async Task<int> Main()
    {
        var baseUrl = Environment.GetEnvironmentVariable("MGX_MOCK_URL") ?? "http://127.0.0.1:4016/v1";
        var mgx = new MgxClient(new MgxClientOptions
        {
            BaseUrl = baseUrl,
            AccessToken = "mock-token", // mock does not enforce OAuth
        });

        var failures = 0;

        try
        {
            var count = 0;
            await foreach (var lot in mgx.Inventory.ListAsync(new InventoryListFilters { Commodity = "wheat" }))
            {
                count++;
                if (count >= 5) break; // guard against an unbounded mock
            }
            Console.WriteLine($"[ok] inventory.list yielded {count} item(s)");
        }
        catch (Exception e)
        {
            failures++;
            Console.Error.WriteLine($"[fail] inventory.list: {e.Message}");
        }

        try
        {
            var commodities = await mgx.Market.CommoditiesAsync();
            Console.WriteLine($"[ok] market.commodities returned {commodities.Count} item(s)");
        }
        catch (Exception e)
        {
            failures++;
            Console.Error.WriteLine($"[fail] market.commodities: {e.Message}");
        }

        try
        {
            var teams = await mgx.Teams.ListAsync();
            Console.WriteLine($"[ok] teams.list returned {teams.Count} item(s)");
        }
        catch (Exception e)
        {
            failures++;
            Console.Error.WriteLine($"[fail] teams.list: {e.Message}");
        }

        // Error path: a non-existent id should surface as a typed MgxApiError.
        try
        {
            await mgx.Inventory.GetAsync("inv_does_not_exist_000000");
            Console.WriteLine("[ok] inventory.get returned (mock may not 404)");
        }
        catch (MgxApiError e)
        {
            Console.WriteLine($"[ok] typed error: {e.Status} {e.Code} {e.Message}");
        }
        catch (Exception e)
        {
            failures++;
            Console.Error.WriteLine($"[fail] inventory.get raised non-typed error: {e.GetType().Name}");
        }

        Console.WriteLine(failures == 0 ? "SMOKE PASS" : $"SMOKE FAIL ({failures})");
        return failures == 0 ? 0 : 1;
    }
}
