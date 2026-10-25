package com.tlcn.discount_service.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tlcn.discount_service.model.Discount;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, Long> {
    Optional<Discount> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Discount d WHERE lower(d.code) = lower(:code)")
    Optional<Discount> findByCodeIgnoreCaseForUpdate(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT d FROM Discount d " +
        "WHERE (d.startDate IS NULL OR d.startDate <= :now) " +
        "AND (d.endDate IS NULL OR d.endDate >= :now) " +
        "AND (d.usageLimit IS NULL OR d.usedCount < d.usageLimit)")
    Page<Discount> findActive(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT d FROM Discount d " +
        "WHERE d.endDate IS NOT NULL AND d.endDate < :now")
    Page<Discount> findExpired(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT d FROM Discount d " +
        "WHERE lower(d.code) = lower(:code) " +
        "AND (d.startDate IS NULL OR d.startDate <= :now) " +
        "AND (d.endDate IS NULL OR d.endDate >= :now) " +
        "AND (d.usageLimit IS NULL OR d.usedCount < d.usageLimit)")
    Optional<Discount> findActiveByCode(@Param("code") String code, @Param("now") Instant now);
}
