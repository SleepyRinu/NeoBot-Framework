package com.neobot.task;

import com.neobot.tracker.TrackerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task - 任务基类
 * 
 * 所有机器人任务的基础抽象类。
 * 定义了任务的生命周期、状态管理和执行接口。
 * 
 * 参考 Altoclef 的任务模型：
 * - 任务有明确的状态（PENDING, RUNNING, COMPLETED, FAILED, CANCELLED）
 * - 任务可以设置优先级、超时时间和重试次数
 * - 任务可以依赖其他任务
 * - 任务执行时可以访问 TrackerManager 获取世界信息
 * 
 * 用法示例：
 * <pre>{@code
 * Task moveTask = new Task("Move to spawn", 10) {
 *     @Override
 *     protected boolean execute(TrackerManager trackerManager) {
 *         // 移动逻辑
 *         return true; // 成功返回 true
 *     }
 * };
 * }</pre>
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class Task {
    
    /** 任务 ID */
    private final UUID id;
    
    /** 任务名称 */
    private final String name;
    
    /** 任务描述 */
    private String description;
    
    /** 优先级（数值越高优先级越高） */
    private volatile int priority;
    
    /** 超时时间（毫秒，0 表示无限制） */
    private volatile long timeout;
    
    /** 最大重试次数 */
    private volatile int maxRetries;
    
    /** 当前状态 */
    private final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.PENDING);
    
    /** 依赖的任务 ID 列表 */
    private final Set<UUID> dependencies = ConcurrentHashMap.newKeySet();
    
    /** 所属任务链 ID */
    private volatile UUID chainId;
    
    /** 创建时间 */
    private final long createdTime;
    
    /** 开始时间 */
    private volatile long startTime;
    
    /** 结束时间 */
    private volatile long endTime;
    
    /** 任务元数据 */
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     * 
     * @param name 任务名称
     * @param priority 任务优先级
     */
    protected Task(String name, int priority) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "Task name cannot be null");
        this.priority = priority;
        this.timeout = 30000; // 默认 30 秒超时
        this.maxRetries = 0;  // 默认不重试
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 构造函数（默认优先级）
     * 
     * @param name 任务名称
     */
    protected Task(String name) {
        this(name, TaskPriority.NORMAL);
    }
    
    // ==================== 核心方法 ====================
    
    /**
     * 执行任务 - 内部方法，由 TaskRunner 调用
     * 
     * @param trackerManager 追踪器管理器
     * @return 任务是否成功完成
     */
    public final boolean run(TrackerManager trackerManager) {
        Objects.requireNonNull(trackerManager, "TrackerManager cannot be null");
        
        // 检查状态
        if (!state.compareAndSet(TaskState.PENDING, TaskState.RUNNING)) {
            return false;
        }
        
        startTime = System.currentTimeMillis();
        
        try {
            // 调用子类实现
            boolean success = execute(trackerManager);
            
            endTime = System.currentTimeMillis();
            
            // 更新状态
            if (success) {
                state.set(TaskState.COMPLETED);
            } else {
                state.set(TaskState.FAILED);
            }
            
            return success;
            
        } catch (Exception e) {
            endTime = System.currentTimeMillis();
            state.set(TaskState.FAILED);
            
            // 重新抛出异常，让 TaskRunner 处理
            throw new RuntimeException("Task execution failed: " + name, e);
        }
    }
    
    /**
     * 执行任务 - 子类实现
     * 
     * 这是任务的核心执行逻辑，子类必须实现此方法。
     * 
     * @param trackerManager 追踪器管理器，用于获取世界信息
     * @return 任务是否成功完成
     */
    protected abstract boolean execute(TrackerManager trackerManager);
    
    // ==================== 生命周期钩子 ====================
    
    /**
     * 任务开始前调用
     * 子类可以覆盖此方法进行初始化
     */
    protected void onStart() {
        // 默认空实现
    }
    
    /**
     * 任务完成后调用
     * 子类可以覆盖此方法进行清理
     * 
     * @param success 是否成功完成
     */
    protected void onComplete(boolean success) {
        // 默认空实现
    }
    
    /**
     * 任务失败时调用
     * 子类可以覆盖此方法处理失败
     * 
     * @param error 失败原因
     */
    protected void onFailure(Exception error) {
        // 默认空实现
    }
    
    /**
     * 任务被取消时调用
     * 子类可以覆盖此方法处理取消
     */
    protected void onCancel() {
        // 默认空实现
    }
    
    // ==================== 依赖管理 ====================
    
    /**
     * 添加任务依赖
     * 
     * @param taskId 依赖的任务 ID
     */
    public void addDependency(UUID taskId) {
        dependencies.add(taskId);
    }
    
    /**
     * 移除任务依赖
     * 
     * @param taskId 依赖的任务 ID
     */
    public void removeDependency(UUID taskId) {
        dependencies.remove(taskId);
    }
    
    /**
     * 清除所有依赖
     */
    public void clearDependencies() {
        dependencies.clear();
    }
    
    // ==================== Getters & Setters ====================
    
    /**
     * 获取任务 ID
     * 
     * @return 任务 ID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * 获取任务名称
     * 
     * @return 任务名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取任务描述
     * 
     * @return 任务描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 设置任务描述
     * 
     * @param description 任务描述
     */
    public void setDescription(String description) {
        this.description = description;
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
     * 获取超时时间
     * 
     * @return 超时时间（毫秒）
     */
    public long getTimeout() {
        return timeout;
    }
    
    /**
     * 设置超时时间
     * 
     * @param timeout 超时时间（毫秒，0 表示无限制）
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    
    /**
     * 获取最大重试次数
     * 
     * @return 最大重试次数
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * 设置最大重试次数
     * 
     * @param maxRetries 最大重试次数
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    /**
     * 获取当前状态
     * 
     * @return 任务状态
     */
    public TaskState getState() {
        return state.get();
    }
    
    /**
     * 设置状态
     * 
     * @param newState 新状态
     */
    public void setState(TaskState newState) {
        state.set(newState);
    }
    
    /**
     * 获取依赖列表
     * 
     * @return 依赖的任务 ID 集合
     */
    public Set<UUID> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }
    
    /**
     * 获取所属任务链 ID
     * 
     * @return 任务链 ID，如果不在链中返回 null
     */
    public UUID getChainId() {
        return chainId;
    }
    
    /**
     * 设置所属任务链 ID
     * 
     * @param chainId 任务链 ID
     */
    public void setChainId(UUID chainId) {
        this.chainId = chainId;
    }
    
    /**
     * 获取创建时间
     * 
     * @return 创建时间戳
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * 获取开始时间
     * 
     * @return 开始时间戳，如果未开始返回 0
     */
    public long getStartTime() {
        return startTime;
    }
    
    /**
     * 获取结束时间
     * 
     * @return 结束时间戳，如果未结束返回 0
     */
    public long getEndTime() {
        return endTime;
    }
    
    /**
     * 获取执行时长
     * 
     * @return 执行时长（毫秒），如果未完成返回 -1
     */
    public long getDuration() {
        if (endTime > 0 && startTime > 0) {
            return endTime - startTime;
        }
        return -1;
    }
    
    /**
     * 检查任务是否完成
     * 
     * @return 是否完成（成功或失败）
     */
    public boolean isDone() {
        TaskState currentState = state.get();
        return currentState == TaskState.COMPLETED || 
               currentState == TaskState.FAILED || 
               currentState == TaskState.CANCELLED;
    }
    
    /**
     * 检查任务是否成功完成
     * 
     * @return 是否成功完成
     */
    public boolean isSuccessful() {
        return state.get() == TaskState.COMPLETED;
    }
    
    // ==================== 元数据管理 ====================
    
    /**
     * 设置元数据
     * 
     * @param key 元数据键
     * @param value 元数据值
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * 获取元数据
     * 
     * @param key 元数据键
     * @param <T> 元数据类型
     * @return 元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }
    
    /**
     * 获取元数据
     * 
     * @param key 元数据键
     * @param defaultValue 默认值
     * @param <T> 元数据类型
     * @return 元数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    // ==================== 工具方法 ====================
    
    @Override
    public String toString() {
        return String.format("Task{id=%s, name='%s', state=%s, priority=%d}",
                id.toString().substring(0, 8), name, state.get(), priority);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return id.equals(task.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
