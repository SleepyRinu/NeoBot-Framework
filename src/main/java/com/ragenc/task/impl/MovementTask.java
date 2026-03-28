package com.ragenc.task.impl;

import com.ragenc.task.Task;
import com.ragenc.task.TaskPriority;
import com.ragenc.tracker.TrackerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

/**
 * MovementTask - 移动任务基类
 * 
 * 处理机器人移动相关的基础任务。
 * 提供路径查找和移动控制功能。
 * 
 * 参考 Altoclef 的移动系统：
 * - 使用 Minecraft 原生寻路
 * - 支持路径优化
 * - 支持移动中断和恢复
 * 
 * 子类可以覆盖：
 * - findTarget() - 确定目标位置
 * - onPathComplete() - 路径完成时的回调
 * - onPathFail() - 路径失败时的回调
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class MovementTask extends Task {
    
    /** 目标位置 */
    protected BlockPos targetPos;
    
    /** 当前路径 */
    protected Path currentPath;
    
    /** 移动速度 */
    protected float moveSpeed = 0.2f;
    
    /** 跳跃高度 */
    protected float jumpHeight = 1.0f;
    
    /** 路径更新间隔（ticks） */
    protected int pathUpdateInterval = 20;
    
    /** 上次路径更新时间 */
    protected long lastPathUpdate;
    
    /** 是否正在移动 */
    protected volatile boolean isMoving = false;
    
    /** 移动超时距离（格子） */
    protected int stuckDistance = 1;
    
    /** 上次位置（用于检测卡住） */
    protected BlockPos lastPosition;
    
    /** 卡住检测计时 */
    protected int stuckTimer = 0;
    
    /** 最大卡住时间（ticks） */
    protected static final int MAX_STUCK_TIME = 60; // 3 秒
    
    /**
     * 构造函数
     * 
     * @param name 任务名称
     * @param target 目标位置
     */
    protected MovementTask(String name, BlockPos target) {
        super(name, TaskPriority.NORMAL);
        this.targetPos = target;
        this.timeout = 60000; // 默认 60 秒超时
    }
    
    /**
     * 构造函数（指定优先级）
     * 
     * @param name 任务名称
     * @param target 目标位置
     * @param priority 优先级
     */
    protected MovementTask(String name, BlockPos target, int priority) {
        super(name, priority);
        this.targetPos = target;
        this.timeout = 60000;
    }
    
    // ==================== 任务执行 ====================
    
    @Override
    protected boolean execute(TrackerManager trackerManager) {
        // 获取玩家引用
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        
        // 确定目标位置（子类可以覆盖）
        BlockPos target = findTarget(trackerManager);
        if (target == null) {
            return false;
        }
        
        this.targetPos = target;
        
        // 检查是否已到达
        if (hasReachedTarget(player)) {
            onPathComplete(true);
            return true;
        }
        
        // 更新路径
        if (needsPathUpdate()) {
            currentPath = calculatePath(player, target);
            lastPathUpdate = System.currentTimeMillis();
            
            if (currentPath == null) {
                onPathFail("No path found");
                return false;
            }
        }
        
        // 执行移动
        isMoving = true;
        boolean success = doMovement(player);
        
        // 检测是否卡住
        if (isStuck(player)) {
            onPathFail("Stuck detected");
            return false;
        }
        
        return success;
    }
    
    // ==================== 子类实现方法 ====================
    
    /**
     * 确定目标位置
     * 子类可以覆盖此方法实现动态目标
     * 
     * @param trackerManager 追踪器管理器
     * @return 目标位置
     */
    protected BlockPos findTarget(TrackerManager trackerManager) {
        return targetPos;
    }
    
    /**
     * 执行移动逻辑
     * 子类需要实现具体的移动控制
     * 
     * @param player 玩家实例
     * @return 是否成功移动
     */
    protected abstract boolean doMovement(Player player);
    
    /**
     * 路径完成时调用
     * 
     * @param success 是否成功到达
     */
    protected void onPathComplete(boolean success) {
        isMoving = false;
    }
    
    /**
     * 路径失败时调用
     * 
     * @param reason 失败原因
     */
    protected void onPathFail(String reason) {
        isMoving = false;
    }
    
    // ==================== 路径相关 ====================
    
    /**
     * 检查是否需要更新路径
     * 
     * @return 是否需要更新
     */
    protected boolean needsPathUpdate() {
        return currentPath == null || 
               currentPath.isDone() ||
               System.currentTimeMillis() - lastPathUpdate > pathUpdateInterval * 50;
    }
    
    /**
     * 计算路径
     * 
     * @param player 玩家实例
     * @param target 目标位置
     * @return 计算的路径
     */
    protected Path calculatePath(Player player, BlockPos target) {
        // 使用 Minecraft 原生寻路
        // 实际实现需要访问 Navigation
        return null; // 子类实现
    }
    
    /**
     * 检查是否已到达目标
     * 
     * @param player 玩家实例
     * @return 是否到达
     */
    protected boolean hasReachedTarget(Player player) {
        if (targetPos == null || player == null) {
            return false;
        }
        
        double distance = player.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5
        );
        
        // 到达距离阈值（考虑碰撞箱）
        return distance < 2.25; // 1.5^2
    }
    
    /**
     * 检测是否卡住
     * 
     * @param player 玩家实例
     * @return 是否卡住
     */
    protected boolean isStuck(Player player) {
        if (player == null) {
            return false;
        }
        
        BlockPos currentPos = player.blockPosition();
        
        if (lastPosition != null && currentPos.equals(lastPosition)) {
            stuckTimer++;
            
            if (stuckTimer >= MAX_STUCK_TIME) {
                return true;
            }
        } else {
            stuckTimer = 0;
            lastPosition = currentPos;
        }
        
        return false;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 获取玩家实例
     * 需要在子类中实现
     * 
     * @return 玩家实例
     */
    protected Player getPlayer() {
        // 子类需要从 Minecraft.getInstance() 获取
        return null;
    }
    
    /**
     * 获取到目标的距离
     * 
     * @param player 玩家实例
     * @return 距离
     */
    protected double getDistanceToTarget(Player player) {
        if (targetPos == null || player == null) {
            return Double.MAX_VALUE;
        }
        
        return Math.sqrt(player.distanceToSqr(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5
        ));
    }
    
    /**
     * 获取目标位置
     * 
     * @return 目标位置
     */
    public BlockPos getTargetPos() {
        return targetPos;
    }
    
    /**
     * 设置目标位置
     * 
     * @param pos 新目标位置
     */
    public void setTargetPos(BlockPos pos) {
        this.targetPos = pos;
        this.currentPath = null; // 清除旧路径
    }
    
    /**
     * 获取移动速度
     * 
     * @return 移动速度
     */
    public float getMoveSpeed() {
        return moveSpeed;
    }
    
    /**
     * 设置移动速度
     * 
     * @param speed 速度值
     */
    public void setMoveSpeed(float speed) {
        this.moveSpeed = Math.max(0.05f, Math.min(1.0f, speed));
    }
    
    /**
     * 是否正在移动
     * 
     * @return 是否移动中
     */
    public boolean isMoving() {
        return isMoving;
    }
    
    @Override
    public String toString() {
        return String.format("MovementTask{name='%s', target=%s, moving=%s}",
                getName(), targetPos, isMoving);
    }
}
