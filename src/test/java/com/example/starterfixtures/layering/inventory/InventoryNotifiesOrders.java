package com.example.starterfixtures.layering.inventory;

import com.example.starterfixtures.layering.orders.api.OrdersApi;

/** Calls {@code orders} through its {@code api} package over an allowed edge; the other half of the cycle. */
final class InventoryNotifiesOrders {
    String notifyOrders() {
        return OrdersApi.confirm();
    }
}
