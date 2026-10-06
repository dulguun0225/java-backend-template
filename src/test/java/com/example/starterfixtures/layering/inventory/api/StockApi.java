package com.example.starterfixtures.layering.inventory.api;

/** Feature {@code inventory}'s public surface. */
public final class StockApi {
    private StockApi() {}

    public static String reserve() {
        return "reserved";
    }
}
