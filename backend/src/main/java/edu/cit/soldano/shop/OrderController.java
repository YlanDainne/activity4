package edu.cit.soldano.shop;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:5173")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        if (request.quantity() <= 0) {
            return ResponseEntity.badRequest()
                .body(new OrderResponse("REJECTED", "Quantity must be greater than zero", null));
        }

        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }
}