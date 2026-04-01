package com.ragenc;

import com.ragenc.core.BotFramework;
import com.ragenc.core.CommandExecutor;
import com.ragenc.core.EventBus;
import com.ragenc.core.TaskRunner;
import com.ragenc.core.TrackerManager;
import com.ragenc.event.impl.BotInitializeEvent;
import com.ragenc.event.impl.BotShutdownEvent;
import com.ragenc.integration.IntegrationManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoBot Framework - 主入口类
 * 
 * 基于 NeoForge 的 Minecraft 机器人框架
 * 参考 Altoclef 的架构设计，提供灵活的任务系统和事件驱动架构
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
@Mod(NeoBot.MOD_ID)
public class NeoBot {
    
    /** 模组 ID */
    public static final String MOD_ID = "neobot";
    
    /** 日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /** 单例实例 */
    private static NeoBot INSTANCE;
    
    /** 框架核心 */
    private BotFramework framework;
    
    /** 任务运行器 */
    private TaskRunner taskRunner;
    
    /** 事件总线 */
    private EventBus eventBus;
    
    /** 追踪器管理器 */
    private TrackerManager trackerManager;
    
    /** 命令执行器 */
    private CommandExecutor commandExecutor;
    
    /** 集成管理器 */
    private IntegrationManager integrationManager;
    
    /**
     * 构造函数 - NeoForge 入口点
     * 
     * @param modEventBus NeoForge 模组事件总线
     * @param container 模组容器
     */
    public NeoBot(IEventBus modEventBus, ModContainer container) {
        INSTANCE = this;
        
        LOGGER.info("╔═══════════════════════════════════════╗");
        LOGGER.info("║      NeoBot Framework v1.0.0          ║");
        LOGGER.info("║   Minecraft Bot Framework for 1.21.1  ║");
        LOGGER.info("╚═══════════════════════════════════════╝");
        
        // 注册生命周期事件监听器
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
        
        // 初始化核心组件
        initializeCore(modEventBus);
        
        // 客户端/服务端分发
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            LOGGER.info("Running on client side - Initializing bot capabilities...");
        });
        
        DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> () -> {
            LOGGER.warn("Running on server side - Bot features are limited on dedicated servers");
        });
    }
    
    /**
     * 初始化核心组件
     * 
     * @param modEventBus 模组事件总线
     */
    private void initializeCore(IEventBus modEventBus) {
        LOGGER.info("Initializing core components...");
        
        // 创建事件总线（最先初始化，其他组件依赖它）
        eventBus = new EventBus();
        eventBus.register(modEventBus);
        LOGGER.debug("EventBus initialized");
        
        // 创建追踪器管理器
        trackerManager = new TrackerManager();
        LOGGER.debug("TrackerManager initialized");
        
        // 创建任务运行器
        taskRunner = new TaskRunner(eventBus, trackerManager);
        LOGGER.debug("TaskRunner initialized");
        
        // 创建命令执行器
        commandExecutor = new CommandExecutor(taskRunner, eventBus);
        LOGGER.debug("CommandExecutor initialized");
        
        // 创建框架核心（最后初始化，依赖其他组件）
        framework = new BotFramework(taskRunner, eventBus, trackerManager, commandExecutor);
        LOGGER.debug("BotFramework initialized");

        // 创建集成管理器
        integrationManager = new IntegrationManager(framework);
        LOGGER.debug("IntegrationManager initialized");
        
        // 发送初始化事件
        eventBus.post(new BotInitializeEvent(framework));
        
        LOGGER.info("Core components initialized successfully");
    }
    
    /**
     * 通用设置事件
     * 在模组加载的早期阶段调用，用于注册通用内容
     * 
     * @param event 通用设置事件
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Common setup phase started");
        
        event.enqueueWork(() -> {
            // 注册全局配置
            framework.registerConfigurations();
            
            // 初始化追踪器
            trackerManager.initializeDefaultTrackers();
            
            LOGGER.info("Common setup completed");
        });
    }
    
    /**
     * 客户端设置事件
     * 仅在客户端调用，用于注册客户端专用内容
     * 
     * @param event 客户端设置事件
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Client setup phase started");
        
        event.enqueueWork(() -> {
            // 初始化客户端专用功能
            framework.initializeClientFeatures();
            
            // 注册命令
            commandExecutor.registerDefaultCommands();

            // 初始化集成并注册集成命令
            integrationManager.initialize();
            integrationManager.registerCommands(commandExecutor);
            
            LOGGER.info("Client setup completed");
        });
    }
    
    /**
     * 关闭框架
     * 清理所有资源，停止所有任务
     */
    public void shutdown() {
        LOGGER.info("Shutting down NeoBot Framework...");
        
        // 发送关闭事件
        eventBus.post(new BotShutdownEvent());
        
        // 停止任务运行器
        if (taskRunner != null) {
            taskRunner.stop();
        }
        
        // 清理追踪器
        if (trackerManager != null) {
            trackerManager.cleanup();
        }
        
        // 清理事件总线
        if (eventBus != null) {
            eventBus.shutdown();
        }
        
        LOGGER.info("NeoBot Framework shutdown complete");
    }
    
    /**
     * 获取单例实例
     * 
     * @return NeoBot 实例
     */
    public static NeoBot getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取框架核心
     * 
     * @return BotFramework 实例
     */
    public BotFramework getFramework() {
        return framework;
    }
    
    /**
     * 获取任务运行器
     * 
     * @return TaskRunner 实例
     */
    public TaskRunner getTaskRunner() {
        return taskRunner;
    }
    
    /**
     * 获取事件总线
     * 
     * @return EventBus 实例
     */
    public EventBus getEventBus() {
        return eventBus;
    }
    
    /**
     * 获取追踪器管理器
     * 
     * @return TrackerManager 实例
     */
    public TrackerManager getTrackerManager() {
        return trackerManager;
    }
    
    /**
     * 获取命令执行器
     * 
     * @return CommandExecutor 实例
     */
    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }
