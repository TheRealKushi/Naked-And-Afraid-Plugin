package com.therealkushi.nakedandafraid.v1_21.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Folia-safe scheduling helper.
 * <p>
 * Bukkit/Spigot only have the classic {@link org.bukkit.scheduler.BukkitScheduler}.
 * Paper 1.20.1+ (and Folia) additionally expose {@code Entity#getScheduler()},
 * {@code Bukkit#getRegionScheduler()} and {@code Bukkit#getGlobalRegionScheduler()}.
 * On Folia the classic scheduler throws {@code UnsupportedOperationException} for
 * most entity/world-bound work, so that work MUST go through the new API there.
 * On non-Folia Paper the new API is present but simply delegates to the same
 * global tick loop, so it's always safe to prefer it when available.
 * <p>
 * Every lookup here is done purely via {@code getMethod}/{@code Class.forName}
 * against Strings/{@code Object} - nothing in this class's own bytecode
 * references a Paper/Folia-only type directly, so loading this class is safe
 * on plain Bukkit/Spigot even though the feature it wraps isn't available there.
 */
public final class SchedulerCompat {

    private SchedulerCompat() { }

    private static Boolean foliaSchedulerApiAvailable;

    private static Method entityGetScheduler;
    private static Method entitySchedulerRun;
    private static Method entitySchedulerRunAtFixedRate;

    private static Method getGlobalRegionScheduler;
    private static Method globalRegionSchedulerRunAtFixedRate;

    private static boolean isFoliaSchedulerApiAvailable() {
        if (foliaSchedulerApiAvailable == null) {
            try {
                entityGetScheduler = Entity.class.getMethod("getScheduler");

                Class<?> entitySchedulerClass =
                        Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                entitySchedulerRun = entitySchedulerClass.getMethod(
                        "run", Plugin.class, Consumer.class, Runnable.class);
                entitySchedulerRunAtFixedRate = entitySchedulerClass.getMethod(
                        "runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

                getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Class<?> globalRegionSchedulerClass =
                        Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                globalRegionSchedulerRunAtFixedRate = globalRegionSchedulerClass.getMethod(
                        "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

                foliaSchedulerApiAvailable = true;
            } catch (Throwable t) {
                foliaSchedulerApiAvailable = false;
            }
        }
        return foliaSchedulerApiAvailable;
    }

    /** Cancel handle abstracting over BukkitTask and Folia's ScheduledTask. */
    public interface TaskHandle {
        void cancel();
    }

    private static TaskHandle wrap(BukkitTask task) {
        return task::cancel;
    }

    private static TaskHandle wrapFoliaTask(Object scheduledTask) {
        return () -> {
            try {
                scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
            } catch (Exception ignored) { }
        };
    }

    /** Runs {@code task} once, delayed, on {@code entity}'s owning region if Folia's scheduler API is present. */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFoliaSchedulerApiAvailable()) {
            try {
                Object scheduler = entityGetScheduler.invoke(entity);
                Consumer<Object> wrapped = st -> task.run();
                entitySchedulerRun.invoke(scheduler, plugin, wrapped, (Runnable) null);
                return;
            } catch (Throwable ignored) {
                // fall through to classic scheduler
            }
        }
        if (delayTicks <= 0) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /** Runs {@code task} repeatedly, bound to {@code entity}'s region if Folia's scheduler API is present. */
    public static TaskHandle runForEntityRepeating(Plugin plugin, Entity entity, Runnable task,
                                                   long delayTicks, long periodTicks) {
        if (isFoliaSchedulerApiAvailable()) {
            try {
                Object scheduler = entityGetScheduler.invoke(entity);
                Consumer<Object> wrapped = st -> task.run();
                Object scheduledTask = entitySchedulerRunAtFixedRate.invoke(
                        scheduler, plugin, wrapped, (Runnable) null,
                        Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                return wrapFoliaTask(scheduledTask);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return wrap(bukkitTask);
    }

    /** Runs global, non-entity-bound repeating housekeeping work. */
    public static TaskHandle runGlobalRepeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFoliaSchedulerApiAvailable()) {
            try {
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                Consumer<Object> wrapped = st -> task.run();
                Object scheduledTask = globalRegionSchedulerRunAtFixedRate.invoke(
                        scheduler, plugin, wrapped, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                return wrapFoliaTask(scheduledTask);
            } catch (Throwable ignored) {
                // fall through
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return wrap(bukkitTask);
    }
}