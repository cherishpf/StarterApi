package com.starter.job;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("job.quartz")
public class QuartzConfig {
    private String cron;

    @Bean
    public JobDetail quartzJob() {
        return JobBuilder.newJob(QuartzJob.class)
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger quartzTrigger() {
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule(cron)
                .withMisfireHandlingInstructionDoNothing();

        return TriggerBuilder.newTrigger()
                .forJob(quartzJob())
                .withSchedule(scheduleBuilder)
                .build();
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }
}
