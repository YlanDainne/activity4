package edu.cit.soldano.shop;

public interface ShopOrderPort {
    ShopOrderResult place(ShopOrderCommand command);
    void cancel(String orderId);
}
