package com.neobot.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracker - 追踪器基类
 * 
 * 追踪器用于监控游戏状态，定期更新数据。
 * 
 * 子类需要实现：
 * - update() - 更新追踪数据
 * - syncUpdate() - 同步更新（可选，在主线程调用）
 * - onEnable() - 启用时初始化
 * - onDisable() - 禁用时清理
 * 
 * 参考 Altoclef 的追踪器模型：
 * - 追踪器专注于单一数据类型
 * - 数据更新返回是否发生变化
 * - 可以设置更新间隔
 * - 可以标记为需要同步更新
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class Tracker {
    
    /** 追踪器 ID */
    private final String id;
    
    /** 追踪器名称 */
    private final String name;
    
    /** 追踪器描述 */
    private final String description;
    
    /** 是否启用 */
    protected final AtomicBoolean enabled = new AtomicBoolean(false);
    
    /** 是否需要同步更新（在主线程） */
    protected final boolean needsSyncUpdate;
    
    /** 更新间隔（毫秒，0 表示使用默认） */
    protected long updateInterval = 0;
    
    /** 上次更新时间 */
    protected volatile long lastUpdateTime;
    
    /** 累计更新次数 */
    protected volatile long updateCount;
    
    /** Minecraft 客户端引用 */
    protected Minecraft mc;
    
    /**
     * 构造函数
     * 
     * @param id 追踪器 ID
     * @param name 追踪器名称
     */
    protected Tracker(String id, String name) {
        this(id, name, false);
    }
    
    /**
     * 构造函数
     * 
     * @param id 追踪器 ID
     * @param name 追踪器名称
     * @param needsSyncUpdate 是否需要同步更新
     */
    protected Tracker(String id, String name, boolean needsSyncUpdate) {
        this.id = Objects.requireNonNull(id, "Tracker ID cannot be null");
        this.name = Objects.requireNonNull(name, "Tracker name cannot be null");
        this.needsSyncUpdate = needsSyncUpdate;
        this.description = "";
        this.mc = Minecraft.getInstance();
    }
    
    // ==================== 生命周期方法 ====================
    
    /**
     * 启用追踪器
     */
    public void onEnable() {
        if (enabled.compareAndSet(false, true)) {
            mc = Minecraft.getInstance();
            enable();
        }
    }
    
    /**
     * 禁用追踪器
     */
    public void onDisable() {
        if (enabled.compareAndSet(true, false)) {
            disable();
        }
    }
    
    /**
     * 更新追踪器数据
     * 
     * @return 数据是否发生变化
     */
    public final boolean update() {
        if (!enabled.get()) {
            return false;
        }
        
        // 检查更新间隔
        if (updateInterval > 0) {
            long now = System.currentTimeMillis();
            if (now - lastUpdateTime < updateInterval) {
                return false;
            }
            lastUpdateTime = now;
        }
        
        updateCount++;
        
        try {
            return updateData();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 同步更新（在主线程调用）
     */
    public final void syncUpdate() {
        if (!enabled.get() || !needsSyncUpdate) {
            return;
        }
        
        try {
            updateSyncData();
        } catch (Exception e) {
            // 忽略同步更新错误
        }
    }
    
    // ==================== 子类实现方法 ====================
    
    /**
     * 启用追踪器 - 子类实现
     */
    protected abstract void enable();
    
    /**
     * 禁用追踪器 - 子类实现
     */
    protected abstract void disable();
    
    /**
     * 更新数据 - 子类实现
     * 
     * @return 数据是否发生变化
     */
    protected abstract boolean updateData();
    
    /**
     * 同步更新数据 - 子类可选实现
     */
    protected void updateSyncData() {
        // 默认空实现
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
    
    // ==================== Getters ====================
    
    /**
     * 获取追踪器 ID
     * 
     * @return 追踪器 ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * 获取追踪器名称
     * 
     * @return 追踪器名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取追踪器描述
     * 
     * @return 追踪器描述
     */
    public String getDescription() {
        return description;
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
     * 检查是否需要同步更新
     * 
     * @return 是否需要同步更新
     */
    public boolean needsSyncUpdate() {
        return needsSyncUpdate;
    }
    
    /**
     * 获取更新间隔
     * 
     * @return 更新间隔（毫秒）
     */
    public long getUpdateInterval() {
        return updateInterval;
    }
    
    /**
     * 设置更新间隔
     * 
     * @param interval 更新间隔（毫秒）
     */
    public void setUpdateInterval(long interval) {
        this.updateInterval = interval;
    }
    
    /**
     * 获取上次更新时间
     * 
     * @return 上次更新时间戳
     */
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * 获取累计更新次数
     * 
     * @return 更新次数
     */
    public long getUpdateCount() {
        return updateCount;
    }
    
    @Override
    public String toString() {
        return String.format("Tracker{id='%s', name='%s', enabled=%s}",
                id, name, enabled.get());
    }
}
