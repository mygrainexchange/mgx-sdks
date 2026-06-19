/**
 * MGX Java SDK — quickstart. These snippets are embedded in the developer docs.
 *
 * Compile against the built mgx-sdk jar (and its okhttp-gson runtime deps), e.g.
 *   mvn -q -DskipTests package
 * then run with the dependency classpath on the command line.
 */
import com.mygrainexchange.mgx.model.DateWindow;
import com.mygrainexchange.mgx.model.Inventory;
import com.mygrainexchange.mgx.model.Bid;
import com.mygrainexchange.mgx.model.MarketPrice;
import com.mygrainexchange.mgx.model.Money;
import com.mygrainexchange.mgx.model.PlaceBid;
import com.mygrainexchange.mgx.overlay.MgxApiError;
import com.mygrainexchange.mgx.overlay.MgxClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class Quickstart {

    public static void main(String[] args) {
        MgxClient mgx = MgxClient.builder()
                .clientId(System.getenv("MGX_CLIENT_ID"))
                .clientSecret(System.getenv("MGX_CLIENT_SECRET"))
                .scopes("inventory.read", "market.read")
                // .baseUrl("https://dashboard.mgx.test/v1") // for local development
                .build();

        // Browse anonymized inventory — auto-paginates across the items/next envelope.
        MgxClient.InventoryListFilters filters = new MgxClient.InventoryListFilters()
                .commodity("wheat")
                .minQuantity(new BigDecimal("50"));
        for (Inventory lot : mgx.inventory().list(filters)) {
            Money asking = lot.getAskingPrice();
            System.out.println(lot.getId() + " " + lot.getQuantityMt()
                    + " " + (asking != null ? asking.getAmount() : null));
        }

        // Current market prices.
        List<MarketPrice> prices = mgx.market().prices("wheat,canola", null);
        for (MarketPrice p : prices) {
            System.out.println(
                    (p.getCommodity() != null ? p.getCommodity().getSlug() : null)
                    + ": " + (p.getPrice() != null ? p.getPrice().getAmount() : null));
        }

        // Place a bid (requires a user-context token with bids.write). The SDK adds
        // an Idempotency-Key automatically.
        try {
            PlaceBid body = new PlaceBid()
                    .quantityMt(new BigDecimal("50"))
                    .price(new Money().amount(new BigDecimal("312.5")))
                    .delivery(new DateWindow()
                            .from(LocalDate.parse("2026-08-01"))
                            .to(LocalDate.parse("2026-09-30")));
            Bid bid = mgx.inventory().placeBid("inv_3Kd9aZ", body);
            System.out.println("placed " + (bid != null ? bid.getId() : null)
                    + " " + (bid != null ? bid.getStatus() : null));
        } catch (MgxApiError e) {
            System.err.println(e.getStatus() + " " + e.getCode() + " " + e.getMessage());
            for (MgxApiError.FieldError fe : e.getFieldErrors()) {
                System.err.println("  " + fe.getField() + ": " + fe.getMessage());
            }
        }
    }
}
