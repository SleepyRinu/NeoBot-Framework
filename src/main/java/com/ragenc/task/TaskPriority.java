package com.ragenc.task;

/**
 * TaskPriority - 任务优先级常量
 * 
 * 定义常用的任务优先级数值。
 * 数值越高优先级越高。
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public final class TaskPriority {
    
    private TaskPriority() {} // 防止实例化
    
    /** 最低优先级 */
    public static final int LOWEST = 0;
    
    /** 低优先级 */
    public static final int LOW = 25;
    
    /** 低于正常优先级 */
    public static final int BELOW_NORMAL = 40;
    
    /** 正常优先级（默认） */
    public static final int NORMAL = 50;
    
    /** 高于正常优先级 */
    public static final int ABOVE_NORMAL = 60;
    
    /** 高优先级 */
    public static final int HIGH = 75;
    
    /** 最高优先级 */
    public static final int HIGHEST = 100;
    
    /** 紧急优先级（立即执行） */
    public static final int URGENT = 1000;
    
    /**
     * 获取优先级名称
     * 
     * @param priority 优先级数值
     * @return 优先级名称
     */
    public static String getName(int priority) {
        if (priority >= URGENT) return "URGENT";
        if (priority >= HIGHEST) return "HIGHEST";
        if (priority >= HIGH) return "HIGH";
        if (priority >= ABOVE_NORMAL) return "ABOVE_NORMAL";
        if (priority >= NORMAL) return "NORMAL";
        if (priority >= BELOW_NORMAL) return "BELOW_NORMAL";
        if (priority >= LOW) return "LOW";
        return "LOWEST";
    }
    
    /**
     * 比较两个优先级
     * 
     * @param p1 第一个优先级
     * @param p2 第二个优先级
     * @return p1 比 p2 高返回正数，相等返回 0，低返回负数
     */
    public static int compare(int p1, int p2) {
        return Integer.compare(p1, p2);
    }
}
