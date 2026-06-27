package fun.sunrisemc.commandscheduler.scheduler;

import java.time.LocalDateTime;

import org.bukkit.Bukkit;

import fun.sunrisemc.commandscheduler.CommandSchedulerPlugin;
import fun.sunrisemc.commandscheduler.scheduledcommand.CommandConfiguration;
import fun.sunrisemc.commandscheduler.scheduledcommand.CommandConfigurationManager;

public class CronCommandExecutionTask {

    private static final int INTERVAL_TICKS = 20; // 1 Second

    private static final int MAX_CATCH_UP_SECONDS = 60; // Don't replay more than this after a long pause

    private static int id = -1;

    private static LocalDateTime lastCheck = null;

    public static void start() {
        if (id != -1) {
            return;
        }
        lastCheck = null;
        id = Bukkit.getScheduler().runTaskTimerAsynchronously(CommandSchedulerPlugin.getInstance(), () -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);

            if (lastCheck == null) {
                lastCheck = now; // First run: nothing to catch up
            }

            // Evaluate every whole second elapsed since the last check (lastCheck, now], so a
            // matching second is never skipped when the task fires late (server lag / GC pause).
            LocalDateTime checkSecond = lastCheck.plusSeconds(1);
            LocalDateTime earliest = now.minusSeconds(MAX_CATCH_UP_SECONDS);
            if (checkSecond.isBefore(earliest)) {
                checkSecond = earliest; // Cap catch-up after a long pause
            }

            while (!checkSecond.isAfter(now)) {
                final LocalDateTime evalTime = checkSecond;
                for (CommandConfiguration commandConfiguration : CommandConfigurationManager.getAll()) {
                    if (commandConfiguration.shouldRunFromCron(evalTime)) {
                        // Run on main thread
                        Bukkit.getScheduler().runTask(CommandSchedulerPlugin.getInstance(), () -> {
                            commandConfiguration.execute();
                        });
                    }
                }
                checkSecond = checkSecond.plusSeconds(1);
            }

            lastCheck = now;
        }, INTERVAL_TICKS, INTERVAL_TICKS).getTaskId();
    }

    public static void stop() {
        if (id == -1) {
            return;
        }
        Bukkit.getScheduler().cancelTask(id);
        id = -1;
        lastCheck = null;
    }
}