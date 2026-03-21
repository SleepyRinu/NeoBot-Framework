package com.neobot.core;

import com.neobot.event.EventBus;
import com.neobot.event.impl.TrackerUpdateEvent;
import com.neobot.tracker.Tracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * TrackerManager - 追踪器管理器
 * 
 * 管理所有追踪器，负责追踪器的注册、更新和数据分发。
 * 追踪器用于监控游戏状态，如：
 * - 实体追踪（玩家、怪物、动物）
 * - 方块追踪（矿石、容器、陷阱）
 * - 物品追踪（掉落物、容器物品）
 * - 位置追踪（路径点、安全区域）
 * 
 * 参考 Altoclef 的追踪器模型：
 * - 每个追踪器专注于一种数据类型
 * - 追踪器定期更新数据
 * - 数据变化时发布事件
 * - 其他组件可以查询追踪器数据
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public class TrackerManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/TrackerManager");
    
    /** 更新间隔（毫秒） */
    private static final long UPDATE_INTERVAL_MS = 50; // 每游戏刻
    
    /** 追踪器映射：trackerId -> Tracker */
    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();
    
    /** 类型索引：trackerClass -> Set<Tracker> */
    private final Map<Class<?>, Set<Tracker>> typeIndex = new ConcurrentHashMap<>();
    
    /** 事件总线 */
    private EventBus eventBus;
    
    /** 运行状态 */
    private volatile boolean running = false;
    
    /** 更新调度器 */
    private final ScheduledExecutorService scheduler;
    
    /** 更新任务 */
    private ScheduledFuture<?> updateTask;
    
    /**
     * 构造函数
     */
    public TrackerManager() {
        // 创建单线程调度器
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NeoBot-Tracker-Update");
            t.setDaemon(true);
            return t;
        });
        
        LOGGER.debug("TrackerManager initialized");
    }
    
    // ==================== 生命周期管理 ====================
    
    /**
     * 初始化默认追踪器
     */
    public void initializeDefaultTrackers() {
        LOGGER.info("Initializing default trackers...");
        
        // TODO: 根据需要注册默认追踪器
        // 例如：实体追踪器、方块追踪器等
        
        LOGGER.info("Default trackers initialized");
    }
    
    /**
     * 启动追踪器管理器
     */
    public void start() {
        if (running) {
            LOGGER.warn("TrackerManager is already running");
            return;
        }
        
        running = true;
        LOGGER.info("TrackerManager started");
        
        // 启动定期更新
        startUpdates();
    }
    
    /**
     * 停止追踪器管理器
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        LOGGER.info("TrackerManager stopping...");
        
        // 停止更新任务
        stopUpdates();
        
        // 清理追踪器
        for (Tracker tracker : new ArrayList<>(trackers.values())) {
            try {
                tracker.onDisable();
            } catch (Exception e) {
                LOGGER.error("Error disabling tracker: {}", tracker.getName(), e);
            }
        }
        
        LOGGER.info("TrackerManager stopped");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stop();
        
        trackers.clear();
        typeIndex.clear();
        
        scheduler.shutdown();
    }
    
    // ==================== 更新调度 ====================
    
    /**
     * 启动定期更新
     */
    private void startUpdates() {
        if (updateTask != null && !updateTask.isDone()) {
            return;
        }
        
        updateTask = scheduler.scheduleAtFixedRate(
                this::updateAll,
                0,
                UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        
        LOGGER.debug("Tracker update task started");
    }
    
    /**
     * 停止定期更新
     */
    private void stopUpdates() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
        
        // 等待正在运行的更新完成
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        LOGGER.debug("Tracker update task stopped");
    }
    
    /**
     * 更新所有追踪器
     */
    private void updateAll() {
        if (!running) {
            return;
        }
        
        for (Tracker tracker : new ArrayList<>(trackers.values())) {
            try {
                if (tracker.isEnabled()) {
                    boolean changed = tracker.update();
                    
                    // 如果数据有变化，发布事件
                    if (changed && eventBus != null) {
                        eventBus.post(new TrackerUpdateEvent(tracker));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error updating tracker: {}", tracker.getName(), e);
            }
        }
    }
    
    /**
     * 每游戏刻调用（同步更新）
     */
    public void tick() {
        // 用于需要同步更新的追踪器
        for (Tracker tracker : new ArrayList<>(trackers.values())) {
            try {
                if (tracker.needsSyncUpdate() && tracker.isEnabled()) {
                    tracker.syncUpdate();
                }
            } catch (Exception e) {
                LOGGER.error("Error in sync update for tracker: {}", tracker.getName(), e);
            }
        }
    }
    
    // ==================== 追踪器注册 ====================
    
    /**
     * 注册一个追踪器
     * 
     * @param tracker 要注册的追踪器
     */
    public void register(Tracker tracker) {
        Objects.requireNonNull(tracker, "Tracker cannot be null");
        
        String trackerId = tracker.getId();
        
        if (trackers.containsKey(trackerId)) {
            LOGGER.warn("Tracker {} already registered, replacing", trackerId);
            unregister(trackerId);
        }
        
        trackers.put(trackerId, tracker);
        
        // 添加到类型索引
        typeIndex.computeIfAbsent(tracker.getClass(), k -> ConcurrentHashMap.newKeySet())
                 .add(tracker);
        
        // 如果正在运行，立即启用
        if (running) {
            tracker.onEnable();
        }
        
        LOGGER.info("Registered tracker: {} ({})", tracker.getName(), trackerId);
    }
    
    /**
     * 注销一个追踪器
     * 
     * @param trackerId 追踪器 ID
     * @return 是否注销成功
     */
    public boolean unregister(String trackerId) {
        Tracker tracker = trackers.remove(trackerId);
        
        if (tracker == null) {
            return false;
        }
        
        // 从类型索引移除
        Set<Tracker> typeSet = typeIndex.get(tracker.getClass());
        if (typeSet != null) {
            typeSet.remove(tracker);
        }
        
        // 禁用追踪器
        try {
            tracker.onDisable();
        } catch (Exception e) {
            LOGGER.error("Error disabling tracker: {}", trackerId, e);
        }
        
        LOGGER.info("Unregistered tracker: {}", trackerId);
        return true;
    }
    
    /**
     * 注销所有指定类型的追踪器
     * 
     * @param trackerClass 追踪器类型
     */
    public void unregisterAll(Class<?> trackerClass) {
        Set<Tracker> typeSet = typeIndex.get(trackerClass);
        if (typeSet != null) {
            for (Tracker tracker : new ArrayList<>(typeSet)) {
                unregister(tracker.getId());
            }
        }
    }
    
    // ==================== 追踪器查询 ====================
    
    /**
     * 获取指定 ID 的追踪器
     * 
     * @param trackerId 追踪器 ID
     * @return 追踪器实例
     */
    public Optional<Tracker> getTracker(String trackerId) {
        return Optional.ofNullable(trackers.get(trackerId));
    }
    
    /**
     * 获取指定类型的追踪器
     * 
     * @param trackerClass 追踪器类型
     * @param <T> 追踪器类型
     * @return 第一个匹配的追踪器
     */
    @SuppressWarnings("unchecked")
    public <T extends Tracker> Optional<T> getTracker(Class<T> trackerClass) {
        Set<Tracker> typeSet = typeIndex.get(trackerClass);
        if (typeSet != null && !typeSet.isEmpty()) {
            return Optional.of((T) typeSet.iterator().next());
        }
        return Optional.empty();
    }
    
    /**
     * 获取所有指定类型的追踪器
     * 
     * @param trackerClass 追踪器类型
     * @param <T> 追踪器类型
     * @return 追踪器集合
     */
    @SuppressWarnings("unchecked")
    public <T extends Tracker> Set<T> getTrackers(Class<T> trackerClass) {
        Set<Tracker> typeSet = typeIndex.get(trackerClass);
        if (typeSet == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet((Set<T>) typeSet);
    }
    
    /**
     * 获取所有追踪器
     * 
     * @return 追踪器集合的不可变副本
     */
    public Collection<Tracker> getAllTrackers() {
        return Collections.unmodifiableCollection(new ArrayList<>(trackers.values()));
    }
    
    /**
     * 获取启用的追踪器数量
     * 
     * @return 启用的追踪器数量
     */
    public long getEnabledTrackerCount() {
        return trackers.values().stream()
                .filter(Tracker::isEnabled)
                .count();
    }
    
    /**
     * 获取追踪器总数
     * 
     * @return 追踪器总数
     */
    public int getTrackerCount() {
        return trackers.size();
    }
    
    // ==================== 事件总线设置 ====================
    
    /**
     * 设置事件总线
     * 
     * @param eventBus 事件总线
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }
}
