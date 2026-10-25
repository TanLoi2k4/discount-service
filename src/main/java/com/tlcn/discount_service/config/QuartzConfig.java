package com.tlcn.discount_service.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {

    /**
     * Quartz creates Job instances via reflection (no-arg ctor). This JobFactory lets Spring autowire
     * dependencies (fields/setters) into those instances.
     */
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(AutowireCapableBeanFactory beanFactory) {
        return (factory) -> factory.setJobFactory(new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
