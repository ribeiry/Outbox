package com.ribeiry.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerConfigTest {

    private final SchedulerConfig config = new SchedulerConfig();

    @Test
    void taskScheduler_deveRetornarSimpleAsyncTaskScheduler() {
        TaskScheduler scheduler = config.taskScheduler();
        assertThat(scheduler).isInstanceOf(SimpleAsyncTaskScheduler.class);
    }

    @Test
    void taskScheduler_deveUsarVirtualThreads() {
        SimpleAsyncTaskScheduler scheduler = (SimpleAsyncTaskScheduler) config.taskScheduler();
        Object virtualThreadDelegate = ReflectionTestUtils.getField(scheduler, "virtualThreadDelegate");
        assertThat(virtualThreadDelegate).isNotNull();
    }
}
