package com.tlcn.discount_service.service;

import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.discount_service.client.ProductClient;
import com.tlcn.discount_service.dto.FlashSaleRequest;
import com.tlcn.discount_service.dto.ProductSummary;
import com.tlcn.discount_service.dto.kafka.FlashSaleEvent;
import com.tlcn.discount_service.enums.FlashSaleStatus;
import com.tlcn.discount_service.exception.BusinessException;
import com.tlcn.discount_service.exception.ForbiddenException;
import com.tlcn.discount_service.exception.ResourceNotFoundException;
import com.tlcn.discount_service.kafka.FlashSaleEventProducer;
import com.tlcn.discount_service.model.FlashSale;
import com.tlcn.discount_service.repository.FlashSaleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleSchedulerService schedulerService;
    private final FlashSaleEventProducer flashSaleEventProducer;
    private final ProductClient productClient;

    @Transactional
    public FlashSale createFlashSale(FlashSaleRequest request, String keycloakId) {
        if (request == null) {
            throw new BusinessException("Request is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getFlashSalePrice() == null) {
            throw new BusinessException("Flash sale price is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("Start time and end time are required", HttpStatus.BAD_REQUEST);
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException("Start time must be before end time", HttpStatus.BAD_REQUEST);
        }

        ProductSummary product = productClient.getProduct(request.getProductId());
        if (product.isDeleted()) {
            throw new BusinessException("Product is not available", HttpStatus.BAD_REQUEST);
        }
        if (product.getKeycloakId() != null && keycloakId != null && !product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("You do not have permission to create flash sale for this product");
        }
        if (product.getPrice() == null) {
            throw new BusinessException("Product price is unavailable", HttpStatus.BAD_REQUEST);
        }
        if (request.getFlashSalePrice() >= product.getPrice()) {
            throw new BusinessException(
                "Flash sale price must be lower than the product's current price",
                HttpStatus.BAD_REQUEST
            );
        }

        // Check for overlapping flash sales for the same product.
        if (flashSaleRepository.existsOverlappingFlashSale(request.getStartTime(), request.getEndTime(), request.getProductId())) {
            throw new BusinessException("Flash sale time window for this product overlaps with an existing campaign", HttpStatus.BAD_REQUEST);
        }

        FlashSale flashSale = FlashSale.builder()
            .name(request.getName())
            .productId(request.getProductId())
            .flashSalePrice(request.getFlashSalePrice())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .status(FlashSaleStatus.PENDING)
            .keycloakId(keycloakId)
            .build();

        FlashSale saved = flashSaleRepository.save(flashSale);

        // Stable job id for Quartz keys
        saved.setQuartzJobId("flashSale:" + saved.getId());
        saved = flashSaleRepository.save(saved);

        schedulerService.scheduleStartAndEnd(saved);
        log.info("FlashSale created and scheduled: id={} productId={} start={} end={}",
            saved.getId(), saved.getProductId(), saved.getStartTime(), saved.getEndTime());

        return saved;
    }

    @Transactional(readOnly = true)
    public FlashSale getByIdForVendor(Long id, String keycloakId) {
        FlashSale flashSale = flashSaleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FlashSale not found: " + id));

        if (flashSale.getKeycloakId() != null && keycloakId != null && !flashSale.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("You do not have permission to access this flash sale");
        }
        return flashSale;
    }

    @Transactional(readOnly = true)
    public List<FlashSale> getActiveForVendor(String keycloakId) {
        return flashSaleRepository.findActiveFlashSalesByVendor(Instant.now(), keycloakId);
    }

    @Transactional(readOnly = true)
    public List<FlashSale> listForVendor(String keycloakId) {
        return flashSaleRepository.findByKeycloakIdOrderByStartTimeDesc(keycloakId);
    }

    @Transactional
    public void delete(Long id, String keycloakId) {
        FlashSale flashSale = flashSaleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FlashSale not found: " + id));

        if (flashSale.getKeycloakId() != null && keycloakId != null && !flashSale.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("You do not have permission to delete this flash sale");
        }

        schedulerService.deleteJobs(flashSale);

        if (flashSale.getStatus() == FlashSaleStatus.ACTIVE) {
            FlashSaleEvent event = FlashSaleEvent.builder()
                .flashSaleId(flashSale.getId())
                .productId(flashSale.getProductId())
                .salePrice(flashSale.getFlashSalePrice())
                .endTime(flashSale.getEndTime())
                .eventType("CANCELLED")
                .build();
            flashSaleEventProducer.sendFlashSaleEvent(event);
        }

        flashSaleRepository.delete(flashSale);
        log.info("FlashSale deleted: id={} productId={}", id, flashSale.getProductId());
    }

    /**
     * On restart, re-schedule START/END jobs from DB so Quartz (memory store) won't lose schedules.
     */
    @EventListener
    @Transactional
    public void onAppReady(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        List<FlashSale> pending = flashSaleRepository.findByStatus(FlashSaleStatus.PENDING);
        for (FlashSale fs : pending) {
            schedulerService.scheduleStartAndEnd(fs);
        }

        List<FlashSale> active = flashSaleRepository.findByStatus(FlashSaleStatus.ACTIVE);
        for (FlashSale fs : active) {
            schedulerService.scheduleEnd(fs);
        }

        log.info("FlashSale Quartz reschedule completed: pending={} active={}", pending.size(), active.size());
    }
}
