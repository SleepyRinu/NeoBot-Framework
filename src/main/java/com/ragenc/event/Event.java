package com.ragenc.event;

/**
 * Event - 事件基类
 * 
 * 所有框架事件的基类。
 * 
 * 事件特性：
 * - 可取消：监听器可以取消事件
 * - 可设置是否忽略已取消事件
 * - 记录创建时间和来源
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class Event {
    
    /** 事件是否已取消 */
    protected volatile boolean cancelled = false;
    
    /** 是否忽略已取消的事件（继续传递给其他监听器） */
    protected volatile boolean ignoreCancelled = false;
    
    /** 事件创建时间 */
    protected final long createdTime;
    
    /** 事件来源 */
    protected final Object source;
    
    /**
     * 构造函数
     */
    public Event() {
        this(null);
    }
    
    /**
     * 构造函数
     * 
     * @param source 事件来源
     */
    public Event(Object source) {
        this.createdTime = System.currentTimeMillis();
        this.source = source;
    }
    
    /**
     * 获取事件名称
     * 
     * @return 事件名称
     */
    public String getName() {
        return getClass().getSimpleName();
    }
    
    /**
     * 检查事件是否已取消
     * 
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * 取消事件
     */
    public void cancel() {
        this.cancelled = true;
    }
    
    /**
     * 恢复事件
     */
    public void uncancel() {
        this.cancelled = false;
    }
    
    /**
     * 检查是否忽略已取消事件
     * 
     * @return 是否忽略已取消事件
     */
    public boolean isIgnoreCancelled() {
        return ignoreCancelled;
    }
    
    /**
     * 设置是否忽略已取消事件
     * 
     * @param ignore 是否忽略
     */
    public void setIgnoreCancelled(boolean ignore) {
        this.ignoreCancelled = ignore;
    }
    
    /**
     * 获取事件创建时间
     * 
     * @return 创建时间戳
     */
    public long getCreatedTime() {
        return createdTime;
    }
    
    /**
     * 获取事件来源
     * 
     * @return 事件来源
     */
    public Object getSource() {
        return source;
    }
    
    /**
     * 检查是否为异步事件
     * 
     * @return 是否异步
     */
    public boolean isAsync() {
        return false;
    }
    
    @Override
    public String toString() {
        return String.format("%s{cancelled=%s, source=%s}",
                getName(), cancelled, source != null ? source.getClass().getSimpleName() : "null");
    }
}
