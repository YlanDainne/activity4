package edu.cit.soldano.shop;

import java.util.List;

public record ShopOrderCommand(List<ShopOrderLine> items) {}
