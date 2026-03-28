package com.ragenc.core;

import com.ragenc.event.Event;
import com.ragenc.event.EventHandler;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * EventBus - 事件总线
 * 
 * 实现发布-订阅模式，用于组件间解耦通信。
 * 
 * 特性：
 * - 支持事件优先级
 * - 支持事件取消
 * - 支持异步事件处理
 * - 自动异常处理，单个监听器异常不影响其他监听器
 * 
 * 参考 Altoclef 的事件模型：
 * - 使用事件传递状态变化
 * - 监听器可以取消事件
 * - 支持监听器优先级
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public class EventBus {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/EventBus");
    
    /** 事件监听器映射：eventType -> List<HandlerEntry> */
    private final Map<Class<?>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();
    
    /** NeoForge 事件总线（可选） */
    private IEventBus neoforgeBus;
    
    /** 异步执行器 */
    private final ExecutorService asyncExecutor;
    
    /** 运行状态 */
    private volatile boolean running = true;
    
    /**
     * 监听器条目
     */
    private static class HandlerEntry<T extends Event> {
        final EventHandler<T> handler;
        final int priority;
        final boolean async;
        
        HandlerEntry(EventHandler<T> handler, int priority, boolean async) {
            this.handler = handler;
            this.priority = priority;
            this.async = async;
        }
    }
    
    /**
     * 构造函数
     */
    public EventBus() {
        this.asyncExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "NeoBot-Event-Async");
            t.setDaemon(true);
            return t;
        });
        
        LOGGER.debug("EventBus initialized");
    }
    
    // ==================== 生命周期管理 ====================
    
    /**
     * 注册 NeoForge 事件总线
     * 
     * @param bus NeoForge 事件总线
     */
    public void register(IEventBus bus) {
        this.neoforgeBus = bus;
        LOGGER.debug("Registered NeoForge event bus");
    }
    
    /**
     * 关闭事件总线
     */
    public void shutdown() {
        running = false;
        
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        handlers.clear();
        
        LOGGER.debug("EventBus shutdown");
    }
    
    // ==================== 订阅管理 ====================
    
    /**
     * 订阅事件
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     */
    public <T extends Event> void subscribe(Class<T> eventType, EventHandler<T> handler) {
        subscribe(eventType, handler, EventHandler.Priority.NORMAL, false);
    }
    
    /**
     * 订阅事件（带优先级）
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param priority 优先级
     * @param <T> 事件类型
     */
    public <T extends Event> void subscribe(Class<T> eventType, EventHandler<T> handler, int priority) {
        subscribe(eventType, handler, priority, false);
    }
    
    /**
     * 订阅事件（完整参数）
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param priority 优先级
     * @param async 是否异步处理
     * @param <T> 事件类型
     */
    public <T extends Event> void subscribe(Class<T> eventType, EventHandler<T> handler, 
                                            int priority, boolean async) {
        Objects.requireNonNull(eventType, "Event type cannot be null");
        Objects.requireNonNull(handler, "Handler cannot be null");
        
        HandlerEntry<T> entry = new HandlerEntry<>(handler, priority, async);
        
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(entry);
        
        // 按优先级排序（优先级高的先执行）
        handlers.get(eventType).sort((a, b) -> Integer.compare(b.priority, a.priority));
        
        LOGGER.debug("Subscribed handler for event: {}", eventType.getSimpleName());
    }
    
    /**
     * 取消订阅
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @param <T> 事件类型
     */
    public <T extends Event> void unsubscribe(Class<T> eventType, EventHandler<T> handler) {
        List<HandlerEntry<?>> handlerList = handlers.get(eventType);
        if (handlerList != null) {
            handlerList.removeIf(entry -> entry.handler.equals(handler));
        }
    }
    
    /**
     * 取消某类型的所有订阅
     * 
     * @param eventType 事件类型
     */
    public void unsubscribeAll(Class<? extends Event> eventType) {
        handlers.remove(eventType);
    }
    
    // ==================== 事件发布 ====================
    
    /**
     * 发布事件
     * 
     * @param event 事件实例
     */
    @SuppressWarnings("unchecked")
    public void post(Event event) {
        if (!running) {
            return;
        }
        
        Objects.requireNonNull(event, "Event cannot be null");
        
        Class<?> eventType = event.getClass();
        List<HandlerEntry<?>> handlerList = handlers.get(eventType);
        
        if (handlerList == null || handlerList.isEmpty()) {
            return;
        }
        
        // 同时发布到 NeoForge 总线
        if (neoforgeBus != null && event instanceof net.neoforged.bus.api.Event neoforgeEvent) {
            try {
                neoforgeBus.post(neoforgeEvent);
            } catch (Exception e) {
                LOGGER.error("Error posting to NeoForge bus: {}", eventType.getSimpleName(), e);
            }
        }
        
        // 处理同步监听器
        for (HandlerEntry<?> entry : handlerList) {
            if (event.isCancelled() && !event.isIgnoreCancelled()) {
                break;
            }
            
            try {
                HandlerEntry<Event> typedEntry = (HandlerEntry<Event>) entry;
                
                if (entry.async) {
                    // 异步处理
                    asyncExecutor.submit(() -> {
                        try {
                            typedEntry.handler.handle(event);
                        } catch (Exception e) {
                            LOGGER.error("Error in async handler for {}: {}",
                                    eventType.getSimpleName(), e.getMessage());
                        }
                    });
                } else {
                    // 同步处理
                    typedEntry.handler.handle(event);
                }
            } catch (Exception e) {
                LOGGER.error("Error in handler for {}: {}",
                        eventType.getSimpleName(), e.getMessage());
            }
        }
    }
    
    /**
     * 异步发布事件
     * 
     * @param event 事件实例
     */
    public void postAsync(Event event) {
        asyncExecutor.submit(() -> post(event));
    }
    
    // ==================== 查询方法 ====================
    
    /**
     * 获取指定事件类型的监听器数量
     * 
     * @param eventType 事件类型
     * @return 监听器数量
     */
    public int getHandlerCount(Class<? extends Event> eventType) {
        List<HandlerEntry<?>> handlerList = handlers.get(eventType);
        return handlerList != null ? handlerList.size() : 0;
    }
    
    /**
     * 获取所有注册的事件类型
     * 
     * @return 事件类型集合
     */
    public Set<Class<?>> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
    
    /**
     * 检查是否有指定事件类型的监听器
     * 
     * @param eventType 事件类型
     * @return 是否有监听器
     */
    public boolean hasHandlers(Class<? extends Event> eventType) {
        List<HandlerEntry<?>> handlerList = handlers.get(eventType);
        return handlerList != null && !handlerList.isEmpty();
    }
}
