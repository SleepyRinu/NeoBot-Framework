package com.neobot.task;

/**
 * TaskState - 任务状态枚举
 * 
 * 定义任务在生命周期中的各种状态。
 * 
 * 状态转换图：
 * <pre>
 * PENDING -> RUNNING -> COMPLETED
 *          |         |
 *          v         v
 *       CANCELLED  FAILED
 * </pre>
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public enum TaskState {
    
    /**
     * 等待状态 - 任务已创建，等待执行
     */
    PENDING("等待中"),
    
    /**
     * 运行状态 - 任务正在执行
     */
    RUNNING("运行中"),
    
    /**
     * 完成状态 - 任务成功完成
     */
    COMPLETED("已完成"),
    
    /**
     * 失败状态 - 任务执行失败
     */
    FAILED("失败"),
    
    /**
     * 取消状态 - 任务被取消
     */
    CANCELLED("已取消"),
    
    /**
     * 暂停状态 - 任务被暂停（保留，暂未使用）
     */
    PAUSED("已暂停");
    
    private final String displayName;
    
    TaskState(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * 获取显示名称
     * 
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 检查是否为终态
     * 
     * @return 是否为终态（COMPLETED, FAILED, CANCELLED）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
    
    /**
     * 检查是否可以转换到目标状态
     * 
     * @param target 目标状态
     * @return 是否可以转换
     */
    public boolean canTransitionTo(TaskState target) {
        switch (this) {
            case PENDING:
                return target == RUNNING || target == CANCELLED;
            case RUNNING:
                return target == COMPLETED || target == FAILED || 
                       target == CANCELLED || target == PAUSED;
            case PAUSED:
                return target == RUNNING || target == CANCELLED;
            default:
                return false; // 终态不能再转换
        }
    }
}
