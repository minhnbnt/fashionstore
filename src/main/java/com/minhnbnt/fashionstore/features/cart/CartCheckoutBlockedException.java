package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.dto.cart.CheckoutBlockedItem;

import java.util.List;

public class CartCheckoutBlockedException extends RuntimeException {

    private final List<CheckoutBlockedItem> items;

    public CartCheckoutBlockedException(List<CheckoutBlockedItem> items) {
        super("Checkout blocked, remove failed items and retry");
        this.items = List.copyOf(items);
    }

    public List<CheckoutBlockedItem> getItems() {
        return items;
    }
}
