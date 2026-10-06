package com.example.starterfixtures.layering.orders;

import com.example.starterfixtures.layering.inventory.api.StockApi;

/** Calls {@code inventory} through its {@code api} package over an allowed edge; half of a cycle. */
final class OrdersReservesStock {
    String reserve() {
        return StockApi.reserve();
    }
}
