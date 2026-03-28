package com.ragenc.task.impl;

import com.ragenc.task.Task;
import com.ragenc.task.TaskPriority;
import com.ragenc.tracker.TrackerManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * CombatTask - 战斗任务基类
 * 
 * 处理机器人战斗相关的基础任务。
 * 支持目标选择、攻击控制和战斗策略。
 * 
 * 参考 Altoclef 的战斗系统：
 * - 支持自动目标选择
 * - 支持武器切换
 * - 支持攻击冷却优化
 * - 支持躲避攻击
 * 
 * 子类可以覆盖：
 * - selectTarget() - 选择攻击目标
 * - getAttackDamage() - 计算伤害
 * - shouldAttack() - 判断是否应该攻击
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public abstract class CombatTask extends Task {
    
    /** 战斗状态 */
    public enum CombatState {
        IDLE,           // 空闲
        TARGETING,      // 寻找目标
        APPROACHING,    // 接近目标
        ATTACKING,      // 攻击中
        EVADING,        // 躲避
        FINISHED        // 完成
    }
    
    /** 当前目标 */
    protected LivingEntity target;
    
    /** 战斗状态 */
    protected CombatState combatState = CombatState.IDLE;
    
    /** 攻击范围 */
    protected float attackRange = 3.0f;
    
    /** 目标搜索范围 */
    protected float targetSearchRange = 16.0f;
    
    /** 攻击冷却（ticks） */
    protected int attackCooldown = 10;
    
    /** 上次攻击时间 */
    protected long lastAttackTime;
    
    /** 是否自动选择目标 */
    protected boolean autoTarget = true;
    
    /** 是否攻击怪物 */
    protected boolean attackMonsters = true;
    
    /** 是否攻击玩家 */
    protected boolean attackPlayers = false;
    
    /** 最小攻击间隔（毫秒） */
    protected long minAttackInterval = 500;
    
    /** 战斗持续时间（毫秒，0 表示无限） */
    protected long combatDuration = 0;
    
    /** 战斗开始时间 */
    protected long combatStartTime;
    
    /**
     * 构造函数
     * 
     * @param name 任务名称
     */
    protected CombatTask(String name) {
        super(name, TaskPriority.HIGH);
        this.timeout = 120000; // 默认 2 分钟超时
    }
    
    /**
     * 构造函数（指定目标）
     * 
     * @param name 任务名称
     * @param target 目标实体
     */
    protected CombatTask(String name, LivingEntity target) {
        super(name, TaskPriority.HIGH);
        this.target = target;
        this.autoTarget = false;
        this.timeout = 120000;
    }
    
    /**
     * 构造函数（指定优先级）
     * 
     * @param name 任务名称
     * @param target 目标实体
     * @param priority 优先级
     */
    protected CombatTask(String name, LivingEntity target, int priority) {
        super(name, priority);
        this.target = target;
        this.autoTarget = false;
        this.timeout = 120000;
    }
    
    // ==================== 任务执行 ====================
    
    @Override
    protected boolean execute(TrackerManager trackerManager) {
        LocalPlayer player = getPlayer();
        if (player == null) {
            return false;
        }
        
        // 记录战斗开始时间
        if (combatState == CombatState.IDLE) {
            combatStartTime = System.currentTimeMillis();
            combatState = CombatState.TARGETING;
        }
        
        // 检查战斗持续时间
        if (combatDuration > 0 && System.currentTimeMillis() - combatStartTime > combatDuration) {
            combatState = CombatState.FINISHED;
            return true;
        }
        
        // 状态机处理
        switch (combatState) {
            case IDLE:
                combatState = CombatState.TARGETING;
                break;
                
            case TARGETING:
                if (autoTarget) {
                    target = selectTarget(player);
                }
                
                if (target != null && target.isAlive()) {
                    combatState = CombatState.APPROACHING;
                }
                break;
                
            case APPROACHING:
                if (target == null || !target.isAlive()) {
                    combatState = CombatState.TARGETING;
                    break;
                }
                
                // 检查距离
                double distance = player.distanceTo(target);
                if (distance <= attackRange) {
                    combatState = CombatState.ATTACKING;
                }
                
                // 接近目标
                approachTarget(player);
                break;
                
            case ATTACKING:
                if (target == null || !target.isAlive()) {
                    combatState = CombatState.FINISHED;
                    break;
                }
                
                // 执行攻击
                if (canAttack(player) && shouldAttack(player, target)) {
                    performAttack(player, target);
                }
                
                // 检查是否需要躲避
                if (shouldEvade(player, target)) {
                    combatState = CombatState.EVADING;
                }
                break;
                
            case EVADING:
                evadeTarget(player);
                
                // 检查是否安全
                if (!shouldEvade(player, target)) {
                    combatState = CombatState.APPROACHING;
                }
                break;
                
            case FINISHED:
                return true;
        }
        
        // 检查目标是否死亡
        if (target != null && !target.isAlive()) {
            combatState = CombatState.FINISHED;
            return true;
        }
        
        return false;
    }
    
    // ==================== 子类实现方法 ====================
    
    /**
     * 选择攻击目标
     * 
     * @param player 玩家实例
     * @return 选择的目标
     */
    protected LivingEntity selectTarget(LocalPlayer player) {
        if (player == null || player.level() == null) {
            return null;
        }
        
        // 搜索范围内的实体
        AABB searchBox = player.getBoundingBox().inflate(targetSearchRange);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox, this::isValidTarget
        );
        
        if (entities.isEmpty()) {
            return null;
        }
        
        // 按距离排序，选择最近的
        entities.sort((a, b) -> Double.compare(
                player.distanceToSqr(a),
                player.distanceToSqr(b)
        ));
        
        return entities.get(0);
    }
    
    /**
     * 检查实体是否为有效目标
     * 
     * @param entity 要检查的实体
     * @return 是否为有效目标
     */
    protected boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        
        // 检查怪物
        if (attackMonsters && entity instanceof Monster) {
            return true;
        }
        
        // 检查玩家
        if (attackPlayers && entity instanceof LocalPlayer && entity != getPlayer()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 接近目标
     * 
     * @param player 玩家实例
     */
    protected abstract void approachTarget(LocalPlayer player);
    
    /**
     * 执行攻击
     * 
     * @param player 玩家实例
     * @param target 目标实体
     */
    protected void performAttack(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) {
            return;
        }
        
        // 看向目标
        lookAtTarget(player, target);
        
        // 尝试使用最佳武器
        equipBestWeapon(player);
        
        // 执行攻击（实际实现需要调用 Minecraft 客户端）
        // player.attack(target);
        
        lastAttackTime = System.currentTimeMillis();
    }
    
    /**
     * 判断是否应该攻击
     * 
     * @param player 玩家实例
     * @param target 目标实体
     * @return 是否应该攻击
     */
    protected boolean shouldAttack(LocalPlayer player, LivingEntity target) {
        return target != null && target.isAlive() && canAttack(player);
    }
    
    /**
     * 判断是否需要躲避
     * 
     * @param player 玩家实例
     * @param target 目标实体
     * @return 是否需要躲避
     */
    protected boolean shouldEvade(LocalPlayer player, LivingEntity target) {
        // 子类可以覆盖实现躲避逻辑
        return false;
    }
    
    /**
     * 躲避目标
     * 
     * @param player 玩家实例
     */
    protected void evadeTarget(LocalPlayer player) {
        // 子类可以覆盖实现躲避逻辑
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查是否可以攻击（冷却检查）
     * 
     * @param player 玩家实例
     * @return 是否可以攻击
     */
    protected boolean canAttack(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        
        // 检查攻击冷却
        return System.currentTimeMillis() - lastAttackTime >= minAttackInterval;
    }
    
    /**
     * 看向目标
     * 
     * @param player 玩家实例
     * @param target 目标实体
     */
    protected void lookAtTarget(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) {
            return;
        }
        
        Vec3 eyePos = player.getEyePosition();
        Vec3 targetVec = target.position().add(0, target.getEyeHeight() / 2, 0);
        Vec3 lookVec = targetVec.subtract(eyePos).normalize();
        
        float yaw = (float) Math.toDegrees(Math.atan2(-lookVec.x, lookVec.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(lookVec.y, Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z)));
        
        // 实际实现需要设置玩家视角
        // player.setYRot(yaw);
        // player.setXRot(pitch);
    }
    
    /**
     * 装备最佳武器
     * 
     * @param player 玩家实例
     */
    protected void equipBestWeapon(LocalPlayer player) {
        if (player == null) {
            return;
        }
        
        // 找到最强的武器
        ItemStack bestWeapon = null;
        float bestDamage = 0;
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SwordItem sword) {
                float damage = sword.getDamage();
                if (bestWeapon == null || damage > bestDamage) {
                    bestWeapon = stack;
                    bestDamage = damage;
                }
            }
        }
        
        // 切换到最佳武器
        if (bestWeapon != null) {
            // player.getInventory().selected = 找到 bestWeapon 的槽位
        }
    }
    
    /**
     * 获取玩家实例
     * 
     * @return 玩家实例
     */
    protected LocalPlayer getPlayer() {
        // 子类需要从 Minecraft.getInstance() 获取
        return null;
    }
    
    /**
     * 计算对目标的预期伤害
     * 
     * @param player 玩家实例
     * @param target 目标实体
     * @return 预期伤害
     */
    protected float getExpectedDamage(LocalPlayer player, LivingEntity target) {
        if (player == null || target == null) {
            return 0;
        }
        
        float baseDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ItemStack weapon = player.getMainHandItem();
        
        // 简化计算，实际需要考虑护甲、附魔等
        return baseDamage;
    }
    
    // ==================== Getters & Setters ====================
    
    public LivingEntity getTarget() {
        return target;
    }
    
    public void setTarget(LivingEntity target) {
        this.target = target;
        this.autoTarget = false;
    }
    
    public CombatState getCombatState() {
        return combatState;
    }
    
    public void setAttackRange(float range) {
        this.attackRange = Math.max(1.0f, Math.min(6.0f, range));
    }
    
    public void setTargetSearchRange(float range) {
        this.targetSearchRange = Math.max(1.0f, Math.min(64.0f, range));
    }
    
    public void setAutoTarget(boolean auto) {
        this.autoTarget = auto;
    }
    
    public void setAttackMonsters(boolean attack) {
        this.attackMonsters = attack;
    }
    
    public void setAttackPlayers(boolean attack) {
        this.attackPlayers = attack;
    }
    
    @Override
    public String toString() {
        return String.format("CombatTask{name='%s', state=%s, target=%s}",
                getName(), combatState, target != null ? target.getName().getString() : "none");
    }
}
