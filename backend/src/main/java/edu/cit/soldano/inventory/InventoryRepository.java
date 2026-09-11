package edu.cit.soldano.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}