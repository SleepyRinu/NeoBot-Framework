package com.ragenc.task;

import java.util.*;
import java.util.function.Consumer;

/**
 * TaskChain - 任务链抽象类
 * 
 * 用于定义一组顺序执行的任务。
 * 任务链中的任务会按照顺序依次执行，
 * 前一个任务完成后才开始下一个任务。
 * 
 * 参考 Altoclef 的任务链模型：
 * - 支持顺序执行多个任务
 * - 支持任务间的数据传递
 * - 支持任务链级别的回调
 * - 支持条件执行（基于上一个任务的结果）
 * 
 * 用法示例：
 * <pre>{@code
 * TaskChain chain = new TaskChain.Builder("Move and attack")
 *     .then(new MovementTask(targetPos))
 *     .then(new CombatTask(enemy))
 *     .onComplete(task -> LOGGER.info("Chain completed!"))
 *     .build();
 * }</pre>
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class TaskChain {
    
    /** 任务链 ID */
    protected final UUID id;
    
    /** 任务链名称 */
    protected final String name;
    
    /** 任务列表 */
    protected final List<Task> tasks;
    
    /** 当前任务索引 */
    protected volatile int currentIndex;
    
    /** 完成回调 */
    protected Consumer<TaskChain> onCompleteCallback;
    
    /** 失败回调 */
    protected Consumer<TaskChain> onFailureCallback;
    
    /** 任务链状态 */
    protected volatile TaskState state;
    
    /** 任务链元数据（用于任务间数据传递） */
    protected final Map<String, Object> context;
    
    /**
     * 构造函数
     * 
     * @param name 任务链名称
     * @param tasks 任务列表
     */
    protected TaskChain(String name, List<Task> tasks) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "Chain name cannot be null");
        this.tasks = new ArrayList<>(Objects.requireNonNull(tasks, "Tasks cannot be null"));
        this.currentIndex = 0;
        this.state = TaskState.PENDING;
        this.context = new HashMap<>();
        
        if (this.tasks.isEmpty()) {
            throw new IllegalArgumentException("Task chain cannot be empty");
        }
    }
    
    // ==================== 核心方法 ====================
    
    /**
     * 获取下一个要执行的任务
     * 
     * @return 下一个任务，如果没有更多任务返回 Optional.empty()
     */
    public Optional<Task> getNextTask() {
        if (currentIndex < tasks.size()) {
            Task task = tasks.get(currentIndex);
            
            // 设置任务链上下文
            task.setMetadata("chain_context", context);
            
            // 传递上一个任务的结果（如果有）
            if (currentIndex > 0) {
                Task previousTask = tasks.get(currentIndex - 1);
                task.setMetadata("previous_task_result", previousTask.isSuccessful());
                task.setMetadata("previous_task", previousTask);
            }
            
            currentIndex++;
            return Optional.of(task);
        }
        return Optional.empty();
    }
    
    /**
     * 处理任务完成
     * 
     * @param task 完成的任务
     */
    public void onTaskComplete(Task task) {
        // 检查是否所有任务都完成
        if (currentIndex >= tasks.size()) {
            state = TaskState.COMPLETED;
            
            if (onCompleteCallback != null) {
                onCompleteCallback.accept(this);
            }
        }
    }
    
    /**
     * 处理任务失败
     * 
     * @param task 失败的任务
     * @param error 失败原因
     */
    public void onTaskFailure(Task task, Exception error) {
        state = TaskState.FAILED;
        
        if (onFailureCallback != null) {
            onFailureCallback.accept(this);
        }
    }
    
    /**
     * 重置任务链
     */
    public void reset() {
        currentIndex = 0;
        state = TaskState.PENDING;
        context.clear();
        tasks.forEach(task -> {
            task.setState(TaskState.PENDING);
            task.clearDependencies();
        });
    }
    
    // ==================== 上下文管理 ====================
    
    /**
     * 设置上下文数据
     * 
     * @param key 数据键
     * @param value 数据值
     */
    public void setContext(String key, Object value) {
        context.put(key, value);
    }
    
    /**
     * 获取上下文数据
     * 
     * @param key 数据键
     * @param <T> 数据类型
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key) {
        return (T) context.get(key);
    }
    
    /**
     * 获取上下文数据
     * 
     * @param key 数据键
     * @param defaultValue 默认值
     * @param <T> 数据类型
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, T defaultValue) {
        Object value = context.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    // ==================== Getters ====================
    
    /**
     * 获取任务链 ID
     * 
     * @return 任务链 ID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * 获取任务链名称
     * 
     * @return 任务链名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取所有任务
     * 
     * @return 任务列表的不可变副本
     */
    public List<Task> getTasks() {
        return Collections.unmodifiableList(new ArrayList<>(tasks));
    }
    
    /**
     * 获取当前任务索引
     * 
     * @return 当前索引
     */
    public int getCurrentIndex() {
        return currentIndex;
    }
    
    /**
     * 获取当前任务
     * 
     * @return 当前任务，如果没有返回 Optional.empty()
     */
    public Optional<Task> getCurrentTask() {
        if (currentIndex > 0 && currentIndex <= tasks.size()) {
            return Optional.of(tasks.get(currentIndex - 1));
        }
        return Optional.empty();
    }
    
    /**
     * 获取任务链状态
     * 
     * @return 状态
     */
    public TaskState getState() {
        return state;
    }
    
    /**
     * 获取任务总数
     * 
     * @return 任务总数
     */
    public int getTaskCount() {
        return tasks.size();
    }
    
    /**
     * 获取已完成的任务数
     * 
     * @return 已完成的任务数
     */
    public int getCompletedTaskCount() {
        return currentIndex;
    }
    
    /**
     * 检查是否完成
     * 
     * @return 是否完成
     */
    public boolean isComplete() {
        return state == TaskState.COMPLETED;
    }
    
    // ==================== Builder ====================
    
    /**
     * TaskChain 构建器
     */
    public static class Builder {
        private final String name;
        private final List<Task> tasks = new ArrayList<>();
        private Consumer<TaskChain> onCompleteCallback;
        private Consumer<TaskChain> onFailureCallback;
        
        /**
         * 创建构建器
         * 
         * @param name 任务链名称
         */
        public Builder(String name) {
            this.name = name;
        }
        
        /**
         * 添加一个任务
         * 
         * @param task 任务
         * @return this
         */
        public Builder then(Task task) {
            tasks.add(Objects.requireNonNull(task, "Task cannot be null"));
            return this;
        }
        
        /**
         * 添加多个任务
         * 
         * @param tasks 任务列表
         * @return this
         */
        public Builder thenAll(List<Task> tasks) {
            this.tasks.addAll(Objects.requireNonNull(tasks, "Tasks cannot be null"));
            return this;
        }
        
        /**
         * 条件添加任务
         * 
         * @param condition 条件
         * @param task 任务
         * @return this
         */
        public Builder thenIf(boolean condition, Task task) {
            if (condition) {
                tasks.add(task);
            }
            return this;
        }
        
        /**
         * 设置完成回调
         * 
         * @param callback 回调函数
         * @return this
         */
        public Builder onComplete(Consumer<TaskChain> callback) {
            this.onCompleteCallback = callback;
            return this;
        }
        
        /**
         * 设置失败回调
         * 
         * @param callback 回调函数
         * @return this
         */
        public Builder onFailure(Consumer<TaskChain> callback) {
            this.onFailureCallback = callback;
            return this;
        }
        
        /**
         * 构建任务链
         * 
         * @return 任务链实例
         */
        public TaskChain build() {
            return new SimpleTaskChain(name, tasks, onCompleteCallback, onFailureCallback);
        }
    }
    
    /**
     * 简单任务链实现
     */
    private static class SimpleTaskChain extends TaskChain {
        
        SimpleTaskChain(String name, List<Task> tasks, 
                        Consumer<TaskChain> onCompleteCallback,
                        Consumer<TaskChain> onFailureCallback) {
            super(name, tasks);
            this.onCompleteCallback = onCompleteCallback;
            this.onFailureCallback = onFailureCallback;
        }
    }
    
    @Override
    public String toString() {
        return String.format("TaskChain{id=%s, name='%s', state=%s, tasks=%d/%d}",
                id.toString().substring(0, 8), name, state, currentIndex, tasks.size());
    }
}
