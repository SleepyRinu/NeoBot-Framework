package com.ragenc.core;

import com.ragenc.behavior.BotBehavior;
import com.ragenc.event.Event;
import com.ragenc.event.EventHandler;
import com.ragenc.event.impl.BotInitializeEvent;
import com.ragenc.event.impl.BotShutdownEvent;
import com.ragenc.event.impl.ConfigReloadEvent;
import com.ragenc.task.Task;
import com.ragenc.task.TaskChain;
import com.ragenc.tracker.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BotFramework - 机器人框架核心类
 * 
 * 作为框架的主控制器，协调所有子系统的工作。
 * 管理任务执行、事件分发、追踪器和命令执行。
 * 
 * 参考 Altoclef 的架构设计：
 * - TaskRunner: 任务调度和执行
 * - EventBus: 事件发布/订阅
 * - TrackerManager: 游戏状态追踪
 * - CommandExecutor: 用户命令处理
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public class BotFramework {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/Framework");
    
    /** 任务运行器 */
    private final TaskRunner taskRunner;
    
    /** 事件总线 */
    private final EventBus eventBus;
    
    /** 追踪器管理器 */
    private final TrackerManager trackerManager;
    
    /** 命令执行器 */
    private final CommandExecutor commandExecutor;
    
    /** 注册的行为列表 */
    private final List<BotBehavior> behaviors = new CopyOnWriteArrayList<>();
    
    /** 全局配置 */
    private final Map<String, Object> config = new ConcurrentHashMap<>();
    
    /** 框架状态 */
    private volatile boolean running = false;
    private volatile boolean paused = false;
    
    /** 机器人主控玩家引用 */
    private volatile LocalPlayer botPlayer;
    
    /**
     * 构造函数
     * 
     * @param taskRunner 任务运行器
     * @param eventBus 事件总线
     * @param trackerManager 追踪器管理器
     * @param commandExecutor 命令执行器
     */
    public BotFramework(TaskRunner taskRunner, EventBus eventBus, 
                        TrackerManager trackerManager, CommandExecutor commandExecutor) {
        this.taskRunner = Objects.requireNonNull(taskRunner, "TaskRunner cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus cannot be null");
        this.trackerManager = Objects.requireNonNull(trackerManager, "TrackerManager cannot be null");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "CommandExecutor cannot be null");
        
        // 注册内部事件处理器
        registerInternalHandlers();
        
        LOGGER.debug("BotFramework instance created");
    }
    
    /**
     * 注册内部事件处理器
     */
    private void registerInternalHandlers() {
        eventBus.subscribe(BotInitializeEvent.class, this::onInitialize);
        eventBus.subscribe(BotShutdownEvent.class, this::onShutdown);
        eventBus.subscribe(ConfigReloadEvent.class, this::onConfigReload);
    }
    
    /**
     * 初始化事件处理
     * 
     * @param event 初始化事件
     */
    @EventHandler(priority = EventHandler.Priority.HIGHEST)
    private void onInitialize(BotInitializeEvent event) {
        LOGGER.info("BotFramework initializing...");
        running = true;
        
        // 启动任务运行器
        taskRunner.start();
        
        // 初始化追踪器
        trackerManager.start();
        
        LOGGER.info("BotFramework initialized and running");
    }
    
    /**
     * 关闭事件处理
     * 
     * @param event 关闭事件
     */
    @EventHandler(priority = EventHandler.Priority.LOWEST)
    private void onShutdown(BotShutdownEvent event) {
        LOGGER.info("BotFramework shutting down...");
        running = false;
        
        // 停止任务运行器
        taskRunner.stop();
        
        // 停止追踪器
        trackerManager.stop();
        
        // 清理行为
        behaviors.forEach(BotBehavior::onDisable);
        behaviors.clear();
        
        LOGGER.info("BotFramework shutdown complete");
    }
    
    /**
     * 配置重载事件处理
     * 
     * @param event 配置重载事件
     */
    @EventHandler
    private void onConfigReload(ConfigReloadEvent event) {
        LOGGER.info("Configuration reloaded");
        behaviors.forEach(BotBehavior::onConfigReload);
    }
    
    // ==================== 行为管理 ====================
    
    /**
     * 注册一个行为
     * 
     * @param behavior 要注册的行为
     * @return 是否注册成功
     */
    public boolean registerBehavior(BotBehavior behavior) {
        Objects.requireNonNull(behavior, "Behavior cannot be null");
        
        if (behaviors.contains(behavior)) {
            LOGGER.warn("Behavior {} already registered", behavior.getName());
            return false;
        }
        
        behaviors.add(behavior);
        behavior.onEnable(this);
        LOGGER.info("Registered behavior: {}", behavior.getName());
        return true;
    }
    
    /**
     * 注销一个行为
     * 
     * @param behavior 要注销的行为
     * @return 是否注销成功
     */
    public boolean unregisterBehavior(BotBehavior behavior) {
        Objects.requireNonNull(behavior, "Behavior cannot be null");
        
        if (behaviors.remove(behavior)) {
            behavior.onDisable();
            LOGGER.info("Unregistered behavior: {}", behavior.getName());
            return true;
        }
        return false;
    }
    
    /**
     * 获取指定名称的行为
     * 
     * @param name 行为名称
     * @return 行为实例，如果不存在返回 Optional.empty()
     */
    public Optional<BotBehavior> getBehavior(String name) {
        return behaviors.stream()
                .filter(b -> b.getName().equals(name))
                .findFirst();
    }
    
    /**
     * 获取所有注册的行为
     * 
     * @return 行为列表的不可变副本
     */
    public List<BotBehavior> getBehaviors() {
        return Collections.unmodifiableList(new ArrayList<>(behaviors));
    }
    
    // ==================== 任务管理 ====================
    
    /**
     * 提交一个任务执行
     * 
     * @param task 要执行的任务
     * @return 任务 ID
     */
    public UUID submitTask(Task task) {
        checkRunning();
        return taskRunner.submit(task);
    }
    
    /**
     * 提交一个任务链执行
     * 
     * @param chain 要执行的任务链
     * @return 任务链 ID
     */
    public UUID submitChain(TaskChain chain) {
        checkRunning();
        return taskRunner.submitChain(chain);
    }
    
    /**
     * 取消指定任务
     * 
     * @param taskId 任务 ID
     * @return 是否取消成功
     */
    public boolean cancelTask(UUID taskId) {
        return taskRunner.cancel(taskId);
    }
    
    /**
     * 取消所有任务
     */
    public void cancelAllTasks() {
        taskRunner.cancelAll();
    }
    
    /**
     * 暂停所有任务执行
     */
    public void pause() {
        if (!paused) {
            paused = true;
            taskRunner.pause();
            LOGGER.info("BotFramework paused");
        }
    }
    
    /**
     * 恢复任务执行
     */
    public void resume() {
        if (paused) {
            paused = false;
            taskRunner.resume();
            LOGGER.info("BotFramework resumed");
        }
    }
    
    // ==================== 事件管理 ====================
    
    /**
     * 发布一个事件
     * 
     * @param event 要发布的事件
     */
    public void postEvent(Event event) {
        eventBus.post(event);
    }
    
    /**
     * 订阅一个事件类型
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     */
    public <T extends Event> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        eventBus.subscribe(eventType, handler);
    }
    
    // ==================== 追踪器管理 ====================
    
    /**
     * 注册一个追踪器
     * 
     * @param tracker 要注册的追踪器
     */
    public void registerTracker(Tracker tracker) {
        trackerManager.register(tracker);
    }
    
    /**
     * 获取指定类型的追踪器
     * 
     * @param trackerClass 追踪器类型
     * @param <T> 追踪器类型
     * @return 追踪器实例
     */
    public <T extends Tracker> Optional<T> getTracker(Class<T> trackerClass) {
        return trackerManager.getTracker(trackerClass);
    }
    
    // ==================== 配置管理 ====================
    
    /**
     * 注册全局配置
     */
    public void registerConfigurations() {
        // 默认配置
        setDefaultConfig("bot.tick_rate", 20);
        setDefaultConfig("bot.max_tasks", 100);
        setDefaultConfig("bot.timeout_ms", 30000);
        setDefaultConfig("pathfinding.max_distance", 128);
        setDefaultConfig("combat.target_range", 16);
        
        LOGGER.debug("Default configurations registered");
    }
    
    /**
     * 设置默认配置值（仅在不存在时设置）
     * 
     * @param key 配置键
     * @param value 配置值
     */
    private void setDefaultConfig(String key, Object value) {
        config.putIfAbsent(key, value);
    }
    
    /**
     * 获取配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, T defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }
    
    /**
     * 设置配置值
     * 
     * @param key 配置键
     * @param value 配置值
     */
    public void setConfig(String key, Object value) {
        config.put(key, value);
        eventBus.post(new ConfigReloadEvent(key, value));
    }
    
    // ==================== 客户端功能 ====================
    
    /**
     * 初始化客户端专用功能
     */
    public void initializeClientFeatures() {
        LOGGER.info("Initializing client features...");
        
        // 获取客户端玩家引用
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.botPlayer = mc.player;
            LOGGER.info("Bot player reference acquired: {}", botPlayer.getName().getString());
        }
        
        // 初始化所有行为
        behaviors.forEach(behavior -> {
            try {
                behavior.onClientInit();
            } catch (Exception e) {
                LOGGER.error("Failed to initialize behavior: {}", behavior.getName(), e);
            }
        });
    }
    
    /**
     * 更新机器人状态（每游戏刻调用）
     */
    public void onTick() {
        if (!running || paused) {
            return;
        }
        
        // 更新追踪器
        trackerManager.tick();
        
        // 更新所有行为
        behaviors.forEach(BotBehavior::tick);
        
        // 处理任务运行器
        taskRunner.tick();
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查框架是否正在运行
     */
    private void checkRunning() {
        if (!running) {
            throw new IllegalStateException("BotFramework is not running");
        }
    }
    
    /**
     * 获取框架运行状态
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 获取框架暂停状态
     * 
     * @return 是否暂停
     */
    public boolean isPaused() {
        return paused;
    }
    
    /**
     * 获取机器人玩家实例
     * 
     * @return 机器人玩家，可能为 null
     */
    public LocalPlayer getBotPlayer() {
        return botPlayer;
    }
    
    /**
     * 更新机器人玩家引用
     * 
     * @param player 新的玩家实例
     */
    public void setBotPlayer(LocalPlayer player) {
        this.botPlayer = player;
        if (player != null) {
            LOGGER.info("Bot player updated: {}", player.getName().getString());
        }
    }
}
