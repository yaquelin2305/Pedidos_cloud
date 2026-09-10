package cl.duoc.pedidos360.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.pedidos360.catalog.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    boolean existsBySkuAndActiveTrue(String sku);
}
