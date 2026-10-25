package com.tlcn.discount_service.repository;

import com.tlcn.discount_service.model.FlashSale;
import com.tlcn.discount_service.enums.FlashSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    // Tìm các chiến dịch theo trạng thái (ví dụ: lấy tất cả bản PENDING để lập lịch khi restart server)
    List<FlashSale> findByStatus(FlashSaleStatus status);

    // Tìm các chiến dịch đang diễn ra dựa trên thời gian thực tế
    @Query("SELECT f FROM FlashSale f WHERE f.startTime <= :now AND f.endTime >= :now AND f.status = 'ACTIVE'")
    List<FlashSale> findActiveFlashSales(@Param("now") Instant now);

    @Query("SELECT f FROM FlashSale f WHERE f.startTime <= :now AND f.endTime >= :now AND f.status = 'ACTIVE' AND f.keycloakId = :keycloakId")
    List<FlashSale> findActiveFlashSalesByVendor(@Param("now") Instant now, @Param("keycloakId") String keycloakId);

    List<FlashSale> findByKeycloakIdOrderByStartTimeDesc(String keycloakId);

    // Kiểm tra xem có chiến dịch nào bị trùng khung giờ không (để tránh overlap)
    @Query("SELECT COUNT(f) > 0 FROM FlashSale f WHERE f.productId = :productId AND f.status != 'CANCELLED' " +
           "AND (f.startTime < :end AND f.endTime > :start)")
    boolean existsOverlappingFlashSale(@Param("start") Instant start, @Param("end") Instant end, @Param("productId") Long productId);
}