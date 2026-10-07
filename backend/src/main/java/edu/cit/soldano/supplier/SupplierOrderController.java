package edu.cit.soldano.supplier;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-orders")
@CrossOrigin(origins = "http://localhost:5173")
class SupplierOrderController {

    private final SupplierOrderRepository repository;

    SupplierOrderController(SupplierOrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    List<SupplierOrderDto> getSupplierOrders() {
        return repository.findAll().stream()
                .map(order -> new SupplierOrderDto(
                        order.getId(),
                        order.getProductId(),
                        order.getBuyerRef(),
                        order.getRequestId(),
                        order.getPoNumber(),
                        order.getCases(),
                        order.getUnits(),
                        order.getStatus(),
                        order.getCreatedAt(),
                        order.getUpdatedAt()
                ))
                .toList();
    }
}
