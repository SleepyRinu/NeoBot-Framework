package com.neobot.event;

import java.lang.annotation.*;

/**
 * EventHandler - 事件处理器注解
 * 
 * 用于标记事件处理方法。
 * 
 * 参考 Altoclef 的事件处理器设计。
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    
    /** 优先级 - 数值越大越先执行 */
    int priority() default Priority.NORMAL;
    
    /** 是否忽略已取消的事件 */
    boolean ignoreCancelled() default false;
    
    /** 是否异步执行 */
    boolean async() default false;
    
    /**
     * 优先级常量
     */
    class Priority {
        public static final int LOWEST = 0;
        public static final int LOW = 25;
        public static final int NORMAL = 50;
        public static final int HIGH = 75;
        public static final int HIGHEST = 100;
        public static final int MONITOR = 150;
        
        private Priority() {}
    }
    
    /**
     * 函数式接口 - 事件处理器
     */
    @FunctionalInterface
    interface Handler<T extends Event> {
        void handle(T event);
    }
}
