package edu.cit.soldano.shop;

import java.util.List;

public record OrderRequest(List<OrderRequestItem> items) {}