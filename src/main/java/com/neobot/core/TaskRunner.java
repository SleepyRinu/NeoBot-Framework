package com.neobot.core;

import com.neobot.event.EventBus;
import com.neobot.event.impl.TaskCompleteEvent;
import com.neobot.event.impl.TaskFailureEvent;
import com.neobot.event.impl.TaskStartEvent;
import com.neobot.task.Task;
import com.neobot.task.TaskChain;
import com.neobot.task.TaskState;
import com.neobot.tracker.TrackerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TaskRunner - 任务执行器
 * 
 * 负责任务的调度、执行和管理。
 * 支持任务的优先级排序、超时控制、失败重试等特性。
 * 
 * 参考 Altoclef 的任务执行模型：
 * - 任务有优先级，高优先级任务先执行
 * - 任务可以被打断和恢复
 * - 支持任务链（TaskChain）顺序执行
 * - 任务执行结果通过事件发布
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public class TaskRunner {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/TaskRunner");
    
    /** 任务超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    
    /** 最大并发任务数 */
    private static final int MAX_CONCURRENT_TASKS = 10;
    
    /** 事件总线 */
    private final EventBus eventBus;
    
    /** 追踪器管理器 */
    private final TrackerManager trackerManager;
    
    /** 活跃任务映射：taskId -> TaskContext */
    private final Map<UUID, TaskContext> activeTasks = new ConcurrentHashMap<>();
    
    /** 任务队列（按优先级排序） */
    private final PriorityBlockingQueue<TaskContext> taskQueue = 
            new PriorityBlockingQueue<>(100, 
                    Comparator.comparingInt(ctx -> -ctx.task.getPriority()));
    
    /** 任务执行线程池 */
    private final ExecutorService executorService;
    
    /** 运行状态 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    /** 任务计数器 */
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    
    /** 任务完成回调映射 */
    private final Map<UUID, Consumer<Task>> completionCallbacks = new ConcurrentHashMap<>();
    
    /**
     * 任务上下文 - 包装任务及其执行状态
     */
    private static class TaskContext {
        final Task task;
        final long submitTime;
        final long timeout;
        volatile TaskState state;
        volatile Thread executionThread;
        volatile int retryCount;
        final int maxRetries;
        
        TaskContext(Task task) {
            this.task = task;
            this.submitTime = System.currentTimeMillis();
            this.timeout = task.getTimeout() > 0 ? task.getTimeout() : DEFAULT_TIMEOUT_MS;
            this.state = TaskState.PENDING;
            this.retryCount = 0;
            this.maxRetries = task.getMaxRetries();
        }
    }
    
    /**
     * 构造函数
     * 
     * @param eventBus 事件总线
     * @param trackerManager 追踪器管理器
     */
    public TaskRunner(EventBus eventBus, TrackerManager trackerManager) {
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus cannot be null");
        this.trackerManager = Objects.requireNonNull(trackerManager, "TrackerManager cannot be null");
        
        // 创建线程池
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_TASKS, r -> {
            Thread t = new Thread(r, "NeoBot-TaskWorker-" + taskCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        
        LOGGER.debug("TaskRunner initialized");
    }
    
    // ==================== 生命周期管理 ====================
    
    /**
     * 启动任务运行器
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("TaskRunner started");
            
            // 启动任务调度线程
            executorService.submit(this::taskScheduler);
        }
    }
    
    /**
     * 停止任务运行器
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("TaskRunner stopping...");
            
            // 取消所有活跃任务
            activeTasks.values().forEach(ctx -> {
                ctx.state = TaskState.CANCELLED;
                if (ctx.executionThread != null) {
                    ctx.executionThread.interrupt();
                }
            });
            
            // 清空队列
            taskQueue.clear();
            activeTasks.clear();
            
            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            LOGGER.info("TaskRunner stopped");
        }
    }
    
    /**
     * 暂停任务执行
     */
    public void pause() {
        if (paused.compareAndSet(false, true)) {
            LOGGER.info("TaskRunner paused");
        }
    }
    
    /**
     * 恢复任务执行
     */
    public void resume() {
        if (paused.compareAndSet(true, false)) {
            LOGGER.info("TaskRunner resumed");
        }
    }
    
    // ==================== 任务调度 ====================
    
    /**
     * 任务调度器 - 从队列获取任务并执行
     */
    private void taskScheduler() {
        while (running.get()) {
            try {
                // 检查暂停状态
                if (paused.get()) {
                    Thread.sleep(100);
                    continue;
                }
                
                // 从队列获取任务
                TaskContext ctx = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (ctx == null) {
                    continue;
                }
                
                // 检查是否可以执行
                if (activeTasks.size() >= MAX_CONCURRENT_TASKS) {
                    // 放回队列
                    taskQueue.offer(ctx);
                    Thread.sleep(50);
                    continue;
                }
                
                // 检查任务依赖
                if (!checkDependencies(ctx.task)) {
                    // 依赖未满足，放回队列
                    taskQueue.offer(ctx);
                    Thread.sleep(50);
                    continue;
                }
                
                // 执行任务
                executeTask(ctx);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in task scheduler", e);
            }
        }
    }
    
    /**
     * 执行单个任务
     * 
     * @param ctx 任务上下文
     */
    private void executeTask(TaskContext ctx) {
        ctx.state = TaskState.RUNNING;
        ctx.task.setState(TaskState.RUNNING);
        
        activeTasks.put(ctx.task.getId(), ctx);
        
        // 发布任务开始事件
        eventBus.post(new TaskStartEvent(ctx.task));
        
        executorService.submit(() -> {
            ctx.executionThread = Thread.currentThread();
            
            try {
                // 检查超时
                long startTime = System.currentTimeMillis();
                
                // 执行任务
                boolean success = ctx.task.run(trackerManager);
                
                // 检查是否超时
                if (System.currentTimeMillis() - startTime > ctx.timeout) {
                    LOGGER.warn("Task {} exceeded timeout: {}ms", 
                            ctx.task.getName(), ctx.timeout);
                }
                
                if (success) {
                    onTaskComplete(ctx);
                } else {
                    onTaskFailure(ctx, new TaskExecutionException("Task returned false"));
                }
                
            } catch (Exception e) {
                onTaskFailure(ctx, e);
            } finally {
                ctx.executionThread = null;
                activeTasks.remove(ctx.task.getId());
            }
        });
    }
    
    /**
     * 任务完成处理
     */
    private void onTaskComplete(TaskContext ctx) {
        ctx.state = TaskState.COMPLETED;
        ctx.task.setState(TaskState.COMPLETED);
        
        LOGGER.debug("Task completed: {}", ctx.task.getName());
        
        // 发布完成事件
        eventBus.post(new TaskCompleteEvent(ctx.task, true));
        
        // 执行完成回调
        Consumer<Task> callback = completionCallbacks.remove(ctx.task.getId());
        if (callback != null) {
            callback.accept(ctx.task);
        }
    }
    
    /**
     * 任务失败处理
     */
    private void onTaskFailure(TaskContext ctx, Exception error) {
        LOGGER.warn("Task failed: {} - {}", ctx.task.getName(), error.getMessage());
        
        // 检查是否需要重试
        if (ctx.retryCount < ctx.maxRetries) {
            ctx.retryCount++;
            ctx.state = TaskState.PENDING;
            ctx.task.setState(TaskState.PENDING);
            
            LOGGER.info("Retrying task {} (attempt {}/{})", 
                    ctx.task.getName(), ctx.retryCount, ctx.maxRetries);
            
            // 重新加入队列
            taskQueue.offer(ctx);
            return;
        }
        
        // 失败
        ctx.state = TaskState.FAILED;
        ctx.task.setState(TaskState.FAILED);
        
        // 发布失败事件
        eventBus.post(new TaskFailureEvent(ctx.task, error));
        
        // 执行完成回调
        Consumer<Task> callback = completionCallbacks.remove(ctx.task.getId());
        if (callback != null) {
            callback.accept(ctx.task);
        }
    }
    
    /**
     * 检查任务依赖是否满足
     */
    private boolean checkDependencies(Task task) {
        for (UUID depId : task.getDependencies()) {
            TaskContext depCtx = activeTasks.get(depId);
            if (depCtx != null && depCtx.state != TaskState.COMPLETED) {
                return false;
            }
        }
        return true;
    }
    
    // ==================== 任务提交 ====================
    
    /**
     * 提交一个任务
     * 
     * @param task 要执行的任务
     * @return 任务 ID
     */
    public UUID submit(Task task) {
        Objects.requireNonNull(task, "Task cannot be null");
        
        TaskContext ctx = new TaskContext(task);
        taskQueue.offer(ctx);
        
        LOGGER.debug("Task submitted: {} (priority: {})", task.getName(), task.getPriority());
        
        return task.getId();
    }
    
    /**
     * 提交一个任务，并设置完成回调
     * 
     * @param task 要执行的任务
     * @param callback 完成回调
     * @return 任务 ID
     */
    public UUID submit(Task task, Consumer<Task> callback) {
        UUID taskId = submit(task);
        completionCallbacks.put(taskId, callback);
        return taskId;
    }
    
    /**
     * 提交一个任务链
     * 
     * @param chain 要执行的任务链
     * @return 任务链 ID
     */
    public UUID submitChain(TaskChain chain) {
        Objects.requireNonNull(chain, "TaskChain cannot be null");
        
        // 获取任务链中的所有任务
        List<Task> tasks = chain.getTasks();
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("TaskChain is empty");
        }
        
        // 设置任务依赖关系
        UUID previousTaskId = null;
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            
            if (previousTaskId != null) {
                task.addDependency(previousTaskId);
            }
            
            // 最后一个任务使用链的 ID
            if (i == tasks.size() - 1) {
                task.setChainId(chain.getId());
            }
            
            submit(task);
            previousTaskId = task.getId();
        }
        
        LOGGER.debug("TaskChain submitted: {} ({} tasks)", chain.getName(), tasks.size());
        
        return chain.getId();
    }
    
    // ==================== 任务管理 ====================
    
    /**
     * 取消指定任务
     * 
     * @param taskId 任务 ID
     * @return 是否取消成功
     */
    public boolean cancel(UUID taskId) {
        TaskContext ctx = activeTasks.get(taskId);
        if (ctx != null) {
            ctx.state = TaskState.CANCELLED;
            ctx.task.setState(TaskState.CANCELLED);
            
            if (ctx.executionThread != null) {
                ctx.executionThread.interrupt();
            }
            
            activeTasks.remove(taskId);
            LOGGER.info("Task cancelled: {}", ctx.task.getName());
            return true;
        }
        
        // 尝试从队列移除
        return taskQueue.removeIf(ctx -> ctx.task.getId().equals(taskId));
    }
    
    /**
     * 取消所有任务
     */
    public void cancelAll() {
        LOGGER.info("Cancelling all tasks...");
        
        // 清空队列
        taskQueue.clear();
        
        // 取消活跃任务
        for (TaskContext ctx : new ArrayList<>(activeTasks.values())) {
            cancel(ctx.task.getId());
        }
        
        activeTasks.clear();
    }
    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务 ID
     * @return 任务状态，如果不存在返回 null
     */
    public TaskState getState(UUID taskId) {
        TaskContext ctx = activeTasks.get(taskId);
        return ctx != null ? ctx.state : null;
    }
    
    /**
     * 获取活跃任务数
     * 
     * @return 活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * 获取队列中的任务数
     * 
     * @return 队列任务数
     */
    public int getQueuedTaskCount() {
        return taskQueue.size();
    }
    
    /**
     * 每游戏刻更新
     */
    public void tick() {
        // 检查超时任务
        long now = System.currentTimeMillis();
        for (TaskContext ctx : new ArrayList<>(activeTasks.values())) {
            if (ctx.state == TaskState.RUNNING && 
                now - ctx.submitTime > ctx.timeout) {
                
                LOGGER.warn("Task timed out: {}", ctx.task.getName());
                onTaskFailure(ctx, new TimeoutException("Task timed out"));
            }
        }
    }
    
    /**
     * 检查运行状态
     * 
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 检查暂停状态
     * 
     * @return 是否暂停
     */
    public boolean isPaused() {
        return paused.get();
    }
    
    // ==================== 异常定义 ====================
    
    /**
     * 任务执行异常
     */
    public static class TaskExecutionException extends RuntimeException {
        public TaskExecutionException(String message) {
            super(message);
        }
        
        public TaskExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
