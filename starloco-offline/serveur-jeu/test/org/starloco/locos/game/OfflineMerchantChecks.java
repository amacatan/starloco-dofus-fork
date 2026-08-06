package org.starloco.locos.game;

public final class OfflineMerchantChecks {
    private OfflineMerchantChecks() {
    }

    public static void run() {
        requestedQuantityIsClampedBeforePricing();
        largeTotalsUseLongArithmetic();
        invalidQuotesAreRejected();
    }

    private static void requestedQuantityIsClampedBeforePricing() {
        GameClient.OfflineMerchantQuote quote =
                GameClient.quoteOfflineMerchantPurchase(100_000, 30_000, 1);

        check(quote != null, "A valid merchant purchase must produce a quote");
        check(quote.quantity == 1,
                "The billed quantity must be capped to the merchant stock");
        check(quote.totalPrice == 100_000L,
                "The price must be calculated after the quantity is capped");
    }

    private static void largeTotalsUseLongArithmetic() {
        GameClient.OfflineMerchantQuote quote = GameClient.quoteOfflineMerchantPurchase(
                Integer.MAX_VALUE, 100_000, 100_000);

        check(quote != null, "The largest protocol purchase must remain representable");
        check(quote.totalPrice == (long) Integer.MAX_VALUE * 100_000L,
                "A merchant total must not overflow 32-bit arithmetic");
        check(quote.totalPrice > Integer.MAX_VALUE,
                "The regression check must exercise a total larger than an int");
    }

    private static void invalidQuotesAreRejected() {
        check(GameClient.quoteOfflineMerchantPurchase(0, 1, 1) == null,
                "A free or corrupted merchant price must be rejected");
        check(GameClient.quoteOfflineMerchantPurchase(-1, 1, 1) == null,
                "A negative merchant price must be rejected");
        check(GameClient.quoteOfflineMerchantPurchase(1, 0, 1) == null,
                "A zero purchase quantity must be rejected");
        check(GameClient.quoteOfflineMerchantPurchase(1, 100_001, 1) == null,
                "A purchase above the protocol limit must be rejected");
        check(GameClient.quoteOfflineMerchantPurchase(1, 1, 0) == null,
                "An empty merchant stack must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
