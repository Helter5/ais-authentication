package sk.gkanocz.aisauth.automod;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring-managed replacement for the hand-rolled {@code Executors.newSingleThreadScheduledExecutor()}
 * {@link AutoDeleteListener} used to own directly - that had no shutdown hook and put every guild's
 * delayed-delete tasks on one worker thread, so a single slow/blocked deletion (e.g. JDA
 * backpressure) stalled every other guild's pending auto-deletes behind it. A small pool plus
 * Spring's lifecycle management (clean shutdown on context close) fixes both.
 */
@Configuration
public class AutoDeleteSchedulerConfig {

    @Bean
    public TaskScheduler autoDeleteTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("autodelete-");
        scheduler.initialize();
        return scheduler;
    }
}
