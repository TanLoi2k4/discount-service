package com.tlcn.discount_service.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.discount_service.dto.DiscountRequest;
import com.tlcn.discount_service.enums.DiscountType;
import com.tlcn.discount_service.exception.BusinessException;
import com.tlcn.discount_service.exception.ResourceNotFoundException;
import com.tlcn.discount_service.model.Discount;
import com.tlcn.discount_service.repository.DiscountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;

    @Transactional
    public Discount create(DiscountRequest request) {
        String normalizedCode = normalizeCode(request.getCode());

        if (discountRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessException("Discount code already exists", HttpStatus.CONFLICT);
        }

        validateBusinessRules(request);

        Discount discount = Discount.builder()
                .code(normalizedCode)
                .description(request.getDescription())
                .type(request.getType())
                .discountValue(request.getDiscountValue())
                .minOrderValue(defaultIfNull(request.getMinOrderValue(), 0.0))
                .maxDiscount(request.getMaxDiscount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .usageLimit(request.getUsageLimit())
                .usedCount(0)
                .build();

        return discountRepository.save(discount);
    }

    @Transactional
    public Discount update(Long id, DiscountRequest request) {
        Discount existing = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));

        String normalizedCode = normalizeCode(request.getCode());
        if (!existing.getCode().equalsIgnoreCase(normalizedCode)
                && discountRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessException("Discount code already exists", HttpStatus.CONFLICT);
        }

        validateBusinessRules(request);

        existing.setCode(normalizedCode);
        existing.setDescription(request.getDescription());
        existing.setType(request.getType());
        existing.setDiscountValue(request.getDiscountValue());
        existing.setMinOrderValue(defaultIfNull(request.getMinOrderValue(), 0.0));
        existing.setMaxDiscount(request.getMaxDiscount());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setUsageLimit(request.getUsageLimit());

        Integer usageLimit = existing.getUsageLimit();
        Integer usedCount = existing.getUsedCount();
        if (usageLimit != null && usedCount != null && usedCount > usageLimit) {
            throw new BusinessException("usageLimit cannot be less than usedCount", HttpStatus.BAD_REQUEST);
        }

        return discountRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Discount existing = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
        discountRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public Discount getById(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Discount getByCode(String code) {
        String normalizedCode = normalizeCode(code);
        return discountRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with code: " + normalizedCode));
    }

    @Transactional(readOnly = true)
    public Discount getActiveByCode(String code) {
        String normalizedCode = normalizeCode(code);
        Instant now = Instant.now();
        return discountRepository.findActiveByCode(normalizedCode, now)
                .orElseThrow(() -> new BusinessException("Discount is expired or not active", HttpStatus.BAD_REQUEST));
    }

    @Transactional(readOnly = true)
    public Page<Discount> list(Pageable pageable) {
        return discountRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Discount> listActive(Pageable pageable) {
        return discountRepository.findActive(Instant.now(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Discount> listExpired(Pageable pageable) {
        return discountRepository.findExpired(Instant.now(), pageable);
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private static Double defaultIfNull(Double value, Double defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static void validateBusinessRules(DiscountRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException("startDate must be <= endDate", HttpStatus.BAD_REQUEST);
        }

        if (request.getType() == DiscountType.PERCENTAGE) {
            if (request.getDiscountValue() != null && request.getDiscountValue() > 100.0) {
                throw new BusinessException("Percentage discountValue must be <= 100", HttpStatus.BAD_REQUEST);
            }
        }

        if (request.getUsageLimit() != null && request.getUsageLimit() < 0) {
            throw new BusinessException("usageLimit must be >= 0", HttpStatus.BAD_REQUEST);
        }
    }
}
