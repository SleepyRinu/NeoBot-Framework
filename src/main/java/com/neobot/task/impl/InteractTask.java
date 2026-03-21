package com.neobot.task.impl;

import com.neobot.task.Task;
import com.neobot.task.TaskPriority;
import com.neobot.tracker.TrackerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * InteractTask - 交互任务基类
 * 
 * 处理机器人与世界交互的基础任务。
 * 支持方块交互、实体交互和物品使用。
 * 
 * 参考 Altoclef 的交互系统：
 * - 支持右键点击方块/实体
 * - 支持放置和使用物品
 * - 支持交互冷却
 * 
 * 子类可以覆盖：
 * - findTarget() - 确定交互目标
 * - onInteractSuccess() - 交互成功回调
 * - onInteractFail() - 交互失败回调
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class InteractTask extends Task {
    
    /** 交互类型 */
    public enum InteractType {
        BLOCK,      // 方块交互
        ENTITY,     // 实体交互
        ITEM_USE,   // 使用物品
        ITEM_PLACE  // 放置物品
    }
    
    /** 目标位置（方块交互） */
    protected BlockPos targetBlock;
    
    /** 目标实体（实体交互） */
    protected Entity targetEntity;
    
    /** 交互类型 */
    protected InteractType interactType;
    
    /** 交互手 */
    protected InteractionHand interactHand = InteractionHand.MAIN_HAND;
    
    /** 交互方向 */
    protected Direction interactDirection = Direction.NORTH;
    
    /** 交互距离 */
    protected float interactRange = 4.5f;
    
    /** 交互冷却（ticks） */
    protected int interactCooldown = 4;
    
    /** 上次交互时间 */
    protected long lastInteractTime;
    
    /** 最大交互尝试次数 */
    protected int maxAttempts = 3;
    
    /** 当前尝试次数 */
    protected int currentAttempt = 0;
    
    /** 是否需要看向目标 */
    protected boolean lookAtTarget = true;
    
    /**
     * 构造函数（方块交互）
     * 
     * @param name 任务名称
     * @param target 目标方块位置
     */
    protected InteractTask(String name, BlockPos target) {
        super(name, TaskPriority.NORMAL);
        this.targetBlock = target;
        this.interactType = InteractType.BLOCK;
        this.timeout = 10000; // 默认 10 秒超时
    }
    
    /**
     * 构造函数（实体交互）
     * 
     * @param name 任务名称
     * @param target 目标实体
     */
    protected InteractTask(String name, Entity target) {
        super(name, TaskPriority.NORMAL);
        this.targetEntity = target;
        this.interactType = InteractType.ENTITY;
        this.timeout = 10000;
    }
    
    /**
     * 构造函数（物品使用）
     * 
     * @param name 任务名称
     * @param type 交互类型
     */
    protected InteractTask(String name, InteractType type) {
        super(name, TaskPriority.NORMAL);
        this.interactType = type;
        this.timeout = 5000;
    }
    
    // ==================== 任务执行 ====================
    
    @Override
    protected boolean execute(TrackerManager trackerManager) {
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        
        // 检查冷却
        if (!canInteract()) {
            return false;
        }
        
        // 根据类型执行交互
        boolean success = false;
        
        switch (interactType) {
            case BLOCK:
                success = interactWithBlock(player);
                break;
            case ENTITY:
                success = interactWithEntity(player);
                break;
            case ITEM_USE:
                success = useItem(player);
                break;
            case ITEM_PLACE:
                success = placeItem(player);
                break;
        }
        
        // 处理结果
        if (success) {
            lastInteractTime = System.currentTimeMillis();
            currentAttempt = 0;
            onInteractSuccess();
        } else {
            currentAttempt++;
            
            if (currentAttempt >= maxAttempts) {
                onInteractFail("Max attempts reached");
                return false;
            }
        }
        
        return success;
    }
    
    // ==================== 子类实现方法 ====================
    
    /**
     * 交互成功时调用
     */
    protected void onInteractSuccess() {
        // 子类可以覆盖
    }
    
    /**
     * 交互失败时调用
     * 
     * @param reason 失败原因
     */
    protected void onInteractFail(String reason) {
        // 子类可以覆盖
    }
    
    // ==================== 交互实现 ====================
    
    /**
     * 与方块交互
     * 
     * @param player 玩家实例
     * @return 是否成功
     */
    protected boolean interactWithBlock(Player player) {
        if (targetBlock == null) {
            return false;
        }
        
        // 检查距离
        if (!isInRange(player, targetBlock)) {
            return false;
        }
        
        // 看向目标
        if (lookAtTarget) {
            lookAt(player, targetBlock);
        }
        
        // 构造命中结果
        Vec3 hitVec = Vec3.atCenterOf(targetBlock);
        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                interactDirection,
                targetBlock,
                false
        );
        
        // 执行交互
        // 实际实现需要调用 player.gameMode.interactBlockAt()
        
        return true;
    }
    
    /**
     * 与实体交互
     * 
     * @param player 玩家实例
     * @return 是否成功
     */
    protected boolean interactWithEntity(Player player) {
        if (targetEntity == null || !targetEntity.isAlive()) {
            return false;
        }
        
        // 检查距离
        if (!isInRange(player, targetEntity)) {
            return false;
        }
        
        // 看向目标
        if (lookAtTarget) {
            lookAt(player, targetEntity);
        }
        
        // 构造命中结果
        EntityHitResult hitResult = new EntityHitResult(targetEntity);
        
        // 执行交互
        // 实际实现需要调用 player.interactOn()
        
        return true;
    }
    
    /**
     * 使用物品
     * 
     * @param player 玩家实例
     * @return 是否成功
     */
    protected boolean useItem(Player player) {
        // 实际实现需要调用 player.gameMode.useItem()
        return false;
    }
    
    /**
     * 放置物品
     * 
     * @param player 玩家实例
     * @return 是否成功
     */
    protected boolean placeItem(Player player) {
        if (targetBlock == null) {
            return false;
        }
        
        // 检查距离
        if (!isInRange(player, targetBlock)) {
            return false;
        }
        
        // 检查是否可以放置
        BlockState state = player.level().getBlockState(targetBlock);
        if (!state.canBeReplaced()) {
            return false;
        }
        
        return false;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查是否可以交互（冷却检查）
     * 
     * @return 是否可以交互
     */
    protected boolean canInteract() {
        return System.currentTimeMillis() - lastInteractTime > interactCooldown * 50;
    }
    
    /**
     * 检查方块是否在交互范围内
     * 
     * @param player 玩家实例
     * @param pos 方块位置
     * @return 是否在范围内
     */
    protected boolean isInRange(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        
        double distance = player.distanceToSqr(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );
        
        return distance <= interactRange * interactRange;
    }
    
    /**
     * 检查实体是否在交互范围内
     * 
     * @param player 玩家实例
     * @param entity 实体
     * @return 是否在范围内
     */
    protected boolean isInRange(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        
        return player.distanceTo(entity) <= interactRange;
    }
    
    /**
     * 看向方块
     * 
     * @param player 玩家实例
     * @param pos 方块位置
     */
    protected void lookAt(Player player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        
        Vec3 eyePos = player.getEyePosition();
        Vec3 targetVec = Vec3.atCenterOf(pos);
        Vec3 lookVec = targetVec.subtract(eyePos).normalize();
        
        float yaw = (float) Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(lookVec.y, Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z)));
        
        // 实际实现需要设置玩家视角
        // player.setYRot(yaw);
        // player.setXRot(pitch);
    }
    
    /**
     * 看向实体
     * 
     * @param player 玩家实例
     * @param entity 目标实体
     */
    protected void lookAt(Player player, Entity entity) {
        if (player == null || entity == null) {
            return;
        }
        
        Vec3 eyePos = player.getEyePosition();
        Vec3 targetVec = entity.position().add(0, entity.getEyeHeight() / 2, 0);
        Vec3 lookVec = targetVec.subtract(eyePos).normalize();
        
        float yaw = (float) Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(lookVec.y, Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z)));
    }
    
    /**
     * 获取玩家实例
     * 
     * @return 玩家实例
     */
    protected Player getPlayer() {
        // 子类需要从 Minecraft.getInstance() 获取
        return null;
    }
    
    // ==================== Getters & Setters ====================
    
    public BlockPos getTargetBlock() {
        return targetBlock;
    }
    
    public void setTargetBlock(BlockPos targetBlock) {
        this.targetBlock = targetBlock;
        this.interactType = InteractType.BLOCK;
    }
    
    public Entity getTargetEntity() {
        return targetEntity;
    }
    
    public void setTargetEntity(Entity targetEntity) {
        this.targetEntity = targetEntity;
        this.interactType = InteractType.ENTITY;
    }
    
    public InteractType getInteractType() {
        return interactType;
    }
    
    public void setInteractHand(InteractionHand hand) {
        this.interactHand = hand;
    }
    
    public void setInteractRange(float range) {
        this.interactRange = Math.max(1.0f, Math.min(6.0f, range));
    }
    
    public void setMaxAttempts(int attempts) {
        this.maxAttempts = Math.max(1, attempts);
    }
    
    @Override
    public String toString() {
        return String.format("InteractTask{name='%s', type=%s, target=%s}",
                getName(), interactType, 
                interactType == InteractType.ENTITY ? targetEntity : targetBlock);
    }
}
