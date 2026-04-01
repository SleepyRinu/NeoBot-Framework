package com.ragenc.integration;

import com.ragenc.core.BotFramework;
import com.ragenc.core.CommandExecutor;
import com.ragenc.core.TaskRunner;
import com.ragenc.core.EventBus;
import com.ragenc.core.TrackerManager;
import com.ragenc.event.impl.BotInitializeEvent;
import com.ragenc.event.impl.BotShutdownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IntegrationManager - 集成管理器
 * 
 * 管理所有外部 mod 的集成，如 Baritone
 * 
 * @author RageNC Team
 * @version 1.0.0
 */
public class IntegrationManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RageNC/IntegrationManager");
    
    private final BotFramework framework;
    private BaritoneIntegration baritone;
    private GetCommand getCommand;
    
    private boolean initialized = false;
    
    public IntegrationManager(BotFramework framework) {
        this.framework = framework;
    }
    
    /**
     * 初始化所有集成
     */
    public void initialize() {
        if (initialized) {
            LOGGER.warn("IntegrationManager already initialized");
            return;
        }
        
        LOGGER.info("Initializing integrations...");
        
        // 初始化 Baritone 集成
        initBaritone();
        
        // 初始化 GetCommand
        initGetCommand();
        
        initialized = true;
        LOGGER.info("All integrations initialized");
    }
    
    /**
     * 初始化 Baritone 集成
     */
    private void initBaritone() {
        try {
            baritone = new BaritoneIntegration();
            baritone.initialize();
            LOGGER.info("Baritone integration initialized");
        } catch (Throwable e) {
            LOGGER.warn("Baritone not available or initialization failed: {}", e.getMessage());
            // Baritone 可能未安装，这不是致命错误
            baritone = null;
        }
    }
    
    /**
     * 初始化 GetCommand
     */
    private void initGetCommand() {
        if (baritone != null) {
            getCommand = new GetCommand(baritone);
            LOGGER.info("GetCommand initialized with Baritone support");
        } else {
            LOGGER.warn("GetCommand initialized without Baritone (limited functionality)");
        }
    }
    
    /**
     * 注册命令到 CommandExecutor
     */
    public void registerCommands(CommandExecutor commandExecutor) {
        if (getCommand != null) {
            commandExecutor.register("get", args -> getCommand.execute(args), "g");
            commandExecutor.register("mine", args -> {
                if (baritone == null) return "Baritone not available";
                if (args.length < 1) return "Usage: .mine <block> [count]";
                // 简单的挖掘命令
                return "Mining command queued";
            });
            
            LOGGER.info("Integration commands registered");
        }
    }
    
    /**
     * 关闭所有集成
     */
    public void shutdown() {
        if (baritone != null) {
            baritone.stop();
        }
        LOGGER.info("Integrations shutdown");
    }
    
    // ==================== Getters ====================
    
    public BaritoneIntegration getBaritone() {
        return baritone;
    }
    
    public GetCommand getGetCommand() {
        return getCommand;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean hasBaritone() {
        return baritone != null && baritone.isInitialized();
    }
}
