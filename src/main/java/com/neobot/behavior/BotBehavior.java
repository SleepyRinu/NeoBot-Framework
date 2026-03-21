package com.neobot.behavior;

import com.neobot.NeoBot;
import com.neobot.core.BotFramework;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BotBehavior - 机器人行为基类
 * 
 * 定义了机器人行为的基本结构。
 * 行为是机器人可以执行的高级动作，如：
 * - 自动寻路
 * - 自动战斗
 * - 自动收集
 * - 自动建造
 * 
 * 每个行为都可以被启用/禁用，
 * 并且可以访问 BotFramework 获取需要的服务。
 * 
 * 参考 Altoclef 的行为模型：
 * - 行为是独立的功能单元
 * - 可以与其他行为并行运行
 * - 有明确的生命周期（enable/disable/tick）
 * - 可以注册到框架或动态加载
 * 
 * 用法示例：
 * <pre>{@code
 * public class AutoWalkBehavior extends BotBehavior {
 *     public AutoWalkBehavior() {
 *         super("autowalk", "自动行走");
 *     }
 *     
 *     @Override
 *     protected void onEnable() {
 *         // 初始化
 *     }
 *     
 *     @Override
 *     protected void tick() {
 *         // 每游戏刻执行
 *     }
 *     
 *     @Override
 *     protected void onDisable() {
 *         // 清理
 *     }
 * }
 * }</pre>
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class BotBehavior {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/Behavior");
    
    /** 行为 ID */
    private final String id;
    
    /** 行为名称 */
    private final String name;
    
    /** 行为描述 */
    private final String description;
    
    /** 行为优先级 */
    private volatile int priority;
    
    /** 是否启用 */
    protected final AtomicBoolean enabled = new AtomicBoolean(false);
    
    /** 是否激活（正在执行） */
    protected final AtomicBoolean active = new AtomicBoolean(false);
    
    /** 框架引用 */
    protected BotFramework framework;
    
    /** Minecraft 客户端引用 */
    protected Minecraft mc;
    
    /** 上一次 tick 时间 */
    protected volatile long lastTickTime;
    
    /** 累计 tick 次数 */
    protected volatile long tickCount;
    
    /**
     * 构造函数
     * 
     * @param id 行为 ID（唯一标识符）
     * @param name 行为名称（显示名称）
     */
    protected BotBehavior(String id, String name) {
        this(id, name, "");
    }
    
    /**
     * 构造函数
     * 
     * @param id 行为 ID
     * @param name 行为名称
     * @param description 行为描述
     */
    protected BotBehavior(String id, String name, String description) {
        this.id = Objects.requireNonNull(id, "Behavior ID cannot be null");
        this.name = Objects.requireNonNull(name, "Behavior name cannot be null");
        this.description = description != null ? description : "";
        this.priority = 50; // 默认优先级
    }
    
    // ==================== 生命周期方法 ====================
    
    /**
     * 启用行为
     * 
     * @param framework 框架引用
     */
    public final void onEnable(BotFramework framework) {
        if (enabled.compareAndSet(false, true)) {
            this.framework = Objects.requireNonNull(framework, "Framework cannot be null");
            this.mc = Minecraft.getInstance();
            
            LOGGER.info("Enabling behavior: {}", name);
            
            try {
                enable();
            } catch (Exception e) {
                LOGGER.error("Failed to enable behavior: {}", name, e);
                enabled.set(false);
                throw e;
            }
        }
    }
    
    /**
     * 禁用行为
     */
    public final void onDisable() {
        if (enabled.compareAndSet(true, false)) {
            LOGGER.info("Disabling behavior: {}", name);
            
            active.set(false);
            
            try {
                disable();
            } catch (Exception e) {
                LOGGER.error("Error while disabling behavior: {}", name, e);
            }
        }
    }
    
    /**
     * 每游戏刻调用
     */
    public final void tick() {
        if (!enabled.get() || !canTick()) {
            return;
        }
        
        lastTickTime = System.currentTimeMillis();
        tickCount++;
        
        try {
            update();
        } catch (Exception e) {
            LOGGER.error("Error in behavior tick: {}", name, e);
        }
    }
    
    /**
     * 配置重载时调用
     */
    public void onConfigReload() {
        // 默认空实现，子类可以覆盖
        LOGGER.debug("Behavior {} config reloaded", name);
    }
    
    /**
     * 客户端初始化时调用
     */
    public void onClientInit() {
        // 默认空实现，子类可以覆盖
        LOGGER.debug("Behavior {} client initialized", name);
    }
    
    // ==================== 子类实现方法 ====================
    
    /**
     * 启用行为 - 子类实现
     */
    protected abstract void enable();
    
    /**
     * 禁用行为 - 子类实现
     */
    protected abstract void disable();
    
    /**
     * 每游戏刻更新 - 子类实现
     */
    protected abstract void update();
    
    /**
     * 检查是否可以执行 tick
     * 
     * @return 是否可以执行
     */
    protected boolean canTick() {
        // 默认：行为启用且有玩家在世界中
        return enabled.get() && mc != null && mc.player != null && mc.level != null;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 获取机器人玩家
     * 
     * @return 机器人玩家实例
     */
    protected LocalPlayer getBotPlayer() {
        return mc != null ? mc.player : null;
    }
    
    /**
     * 获取当前世界
     * 
     * @return 当前世界实例
     */
    protected Level getLevel() {
        return mc != null ? mc.level : null;
    }
    
    /**
     * 检查机器人是否在世界中
     * 
     * @return 是否在世界中
     */
    protected boolean isInWorld() {
        return mc != null && mc.player != null && mc.level != null;
    }
    
    /**
     * 发送聊天消息
     * 
     * @param message 消息内容
     */
    protected void sendChatMessage(String message) {
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(message), 
                false
            );
        }
    }
    
    /**
     * 发送状态栏消息
     * 
     * @param message 消息内容
     */
    protected void sendActionBar(String message) {
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(message), 
                true
            );
        }
    }
    
    // ==================== Getters & Setters ====================
    
    /**
     * 获取行为 ID
     * 
     * @return 行为 ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取行为名称
     * 
     * @return 行为名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取行为描述
     * 
     * @return 行为描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 获取优先级
     * 
     * @return 优先级
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * 设置优先级
     * 
     * @param priority 优先级
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    /**
     * 检查是否启用
     * 
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled.get();
    }
    
    /**
     * 检查是否激活
     * 
     * @return 是否激活
     */
    public boolean isActive() {
        return active.get();
    }
    
    /**
     * 激活行为
     */
    protected void activate() {
        active.set(true);
    }
    
    /**
     * 停用行为
     */
    protected void deactivate() {
        active.set(false);
    }
    
    /**
     * 获取上一次 tick 时间
     * 
     * @return 上一次 tick 时间戳
     */
    public long getLastTickTime() {
        return lastTickTime;
    }
    
    /**
     * 获取累计 tick 次数
     * 
     * @return tick 次数
     */
    public long getTickCount() {
        return tickCount;
    }
    
    @Override
    public String toString() {
        return String.format("BotBehavior{id='%s', name='%s', enabled=%s, active=%s}",
                id, name, enabled.get(), active.get());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BotBehavior that = (BotBehavior) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
