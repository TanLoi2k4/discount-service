package com.tlcn.discount_service.service;

import java.time.Instant;
import java.util.Date;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import com.tlcn.discount_service.job.FlashSaleEndJob;
import com.tlcn.discount_service.job.FlashSaleStartJob;
import com.tlcn.discount_service.model.FlashSale;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlashSaleSchedulerService {

    private static final String GROUP = "flash-sale";

    private final Scheduler scheduler;

    public void scheduleStartAndEnd(FlashSale flashSale) {
        scheduleStart(flashSale);
        scheduleEnd(flashSale);
    }

    public void scheduleStart(FlashSale flashSale) {
        if (flashSale == null || flashSale.getId() == null) return;
        if (flashSale.getStartTime() == null) return;

        scheduleOne(
            startJobKey(flashSale),
            startTriggerKey(flashSale),
            FlashSaleStartJob.class,
            flashSale.getId(),
            flashSale.getStartTime()
        );
    }

    public void scheduleEnd(FlashSale flashSale) {
        if (flashSale == null || flashSale.getId() == null) return;
        if (flashSale.getEndTime() == null) return;

        scheduleOne(
            endJobKey(flashSale),
            endTriggerKey(flashSale),
            FlashSaleEndJob.class,
            flashSale.getId(),
            flashSale.getEndTime()
        );
    }

    public void deleteJobs(FlashSale flashSale) {
        if (flashSale == null) return;
        if (flashSale.getId() == null) return;

        try {
            scheduler.deleteJob(startJobKey(flashSale));
            scheduler.deleteJob(endJobKey(flashSale));
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to delete Quartz jobs for flashSaleId=" + flashSale.getId(), e);
        }
    }

    private void scheduleOne(
        JobKey jobKey,
        TriggerKey triggerKey,
        Class<? extends org.quartz.Job> jobClass,
        Long flashSaleId,
        Instant runAt
    ) {
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
            .withIdentity(jobKey)
            .usingJobData(FlashSaleStartJob.FLASH_SALE_ID, flashSaleId)
            .storeDurably(false)
            .build();

        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(runAt))
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withMisfireHandlingInstructionFireNow())
            .forJob(jobDetail)
            .build();

        try {
            // Idempotent re-schedule: delete and create fresh.
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (ObjectAlreadyExistsException ignored) {
            // safe to ignore
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule Quartz job " + jobKey, e);
        }
    }

    private String baseId(FlashSale flashSale) {
        String quartzJobId = flashSale.getQuartzJobId();
        if (quartzJobId != null && !quartzJobId.isBlank()) {
            return quartzJobId;
        }
        return "flashSale:" + flashSale.getId();
    }

    private JobKey startJobKey(FlashSale flashSale) {
        return JobKey.jobKey(baseId(flashSale) + ":start", GROUP);
    }

    private TriggerKey startTriggerKey(FlashSale flashSale) {
        return TriggerKey.triggerKey(baseId(flashSale) + ":start", GROUP);
    }

    private JobKey endJobKey(FlashSale flashSale) {
        return JobKey.jobKey(baseId(flashSale) + ":end", GROUP);
    }

    private TriggerKey endTriggerKey(FlashSale flashSale) {
        return TriggerKey.triggerKey(baseId(flashSale) + ":end", GROUP);
    }
}
