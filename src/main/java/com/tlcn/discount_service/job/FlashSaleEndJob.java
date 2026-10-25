package com.tlcn.discount_service.job;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tlcn.discount_service.dto.kafka.FlashSaleEvent;
import com.tlcn.discount_service.enums.FlashSaleStatus;
import com.tlcn.discount_service.kafka.FlashSaleEventProducer;
import com.tlcn.discount_service.model.FlashSale;
import com.tlcn.discount_service.repository.FlashSaleRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@DisallowConcurrentExecution
public class FlashSaleEndJob implements Job {

    public static final String FLASH_SALE_ID = "flashSaleId";

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private FlashSaleEventProducer flashSaleEventProducer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long flashSaleId = context.getMergedJobDataMap().getLong(FLASH_SALE_ID);

        FlashSale flashSale = flashSaleRepository.findById(flashSaleId).orElse(null);
        if (flashSale == null) {
            log.warn("FlashSaleEndJob: flashSale not found: {}", flashSaleId);
            return;
        }

        if (flashSale.getStatus() == FlashSaleStatus.CANCELLED || flashSale.getStatus() == FlashSaleStatus.EXPIRED) {
            log.info("FlashSaleEndJob: skip (status={}) flashSaleId={}", flashSale.getStatus(), flashSaleId);
            return;
        }

        // Mark expired regardless of current state (PENDING/ACTIVE)
        flashSale.setStatus(FlashSaleStatus.EXPIRED);
        flashSaleRepository.save(flashSale);

        FlashSaleEvent event = FlashSaleEvent.builder()
            .flashSaleId(flashSale.getId())
            .productId(flashSale.getProductId())
            .salePrice(flashSale.getFlashSalePrice())
            .endTime(flashSale.getEndTime())
            .eventType("END")
            .build();

        log.info("FlashSaleEndJob: publish END flashSaleId={} productId={}", flashSale.getId(), flashSale.getProductId());
        flashSaleEventProducer.sendFlashSaleEvent(event);
    }
}
