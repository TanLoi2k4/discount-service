package com.tlcn.discount_service.job;

import java.time.Instant;

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
public class FlashSaleStartJob implements Job {

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
            log.warn("FlashSaleStartJob: flashSale not found: {}", flashSaleId);
            return;
        }

        if (flashSale.getStatus() != FlashSaleStatus.PENDING) {
            log.info("FlashSaleStartJob: skip (status={}) flashSaleId={}", flashSale.getStatus(), flashSaleId);
            return;
        }

        Instant now = Instant.now();
        if (flashSale.getEndTime() != null && !now.isBefore(flashSale.getEndTime())) {
            // Start fired after end time => mark expired.
            flashSale.setStatus(FlashSaleStatus.EXPIRED);
            flashSaleRepository.save(flashSale);
            log.warn("FlashSaleStartJob: fired after endTime, set EXPIRED flashSaleId={}", flashSaleId);
            return;
        }

        flashSale.setStatus(FlashSaleStatus.ACTIVE);
        flashSaleRepository.save(flashSale);

        FlashSaleEvent event = FlashSaleEvent.builder()
            .flashSaleId(flashSale.getId())
            .productId(flashSale.getProductId())
            .salePrice(flashSale.getFlashSalePrice())
            .endTime(flashSale.getEndTime())
            .eventType("START")
            .build();

        log.info("FlashSaleStartJob: publish START flashSaleId={} productId={}", flashSale.getId(), flashSale.getProductId());
        flashSaleEventProducer.sendFlashSaleEvent(event);
    }
}
