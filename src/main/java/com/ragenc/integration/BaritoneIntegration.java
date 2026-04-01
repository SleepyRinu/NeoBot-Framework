package com.ragenc.integration;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.*;
import baritone.api.process.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baritone 集成类
 * 
 * 提供 Baritone API 的统一封装，用于：
 * - 寻路控制
 * - 方块挖掘
 * - 物品拾取
 * - 任务管理
 * 
 * @author RageNC Team
 * @version 1.0.0
 */
public class BaritoneIntegration {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RageNC/BaritoneIntegration");
    
    /** Baritone 实例 */
    private final IBaritone baritone;
    
    /** Minecraft 客户端实例 */
    private final Minecraft mc;
    
    /** 当前任务 */
    private IBaritoneProcess currentProcess;
    
    /** 是否已初始化 */
    private boolean initialized = false;
    
    /** 物品来源注册表 */
    private final Map<Item, List<ItemSource>> itemSources = new ConcurrentHashMap<>();
    
    /**
     * 物品来源接口
     * 用于定义不同的物品获取方式
     */
    public interface ItemSource {
        /** 获取来源类型 */
        String getType();
        
        /** 获取物品 */
        Item getItem();
        
        /** 获取预计数量 */
        int getEstimatedCount();
        
        /** 执行获取（返回是否成功） */
        boolean execute();
        
        /** 获取优先级（数字越小优先级越高） */
        default int getPriority() { return 100; }
    }
    
    // ==================== 初始化 ====================
    
    public BaritoneIntegration() {
        this.mc = Minecraft.getInstance();
        this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        LOGGER.info("BaritoneIntegration created");
    }
    
    /**
     * 初始化 Baritone 集成
     */
    public void initialize() {
        if (initialized) {
            LOGGER.warn("BaritoneIntegration already initialized");
            return;
        }
        
        try {
            // 配置 Baritone 设置
            configureBaritone();
            
            initialized = true;
            LOGGER.info("BaritoneIntegration initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize BaritoneIntegration", e);
        }
    }
    
    /**
     * 配置 Baritone 设置
     */
    private void configureBaritone() {
        // 基本设置
        baritone.getSettings().allowSprint.value = true;
        baritone.getSettings().allowPlace.value = true;
        baritone.getSettings().allowBreak.value = true;
        baritone.getSettings().allowInventory.value = true;
        
        // 优化搭路设置
        baritone.getSettings().blockPlacementPenalty.value = 10.0;
        
        LOGGER.debug("Baritone settings configured");
    }
    
    // ==================== 寻路控制 ====================
    
    /**
     * 移动到指定坐标
     * 
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @return 是否成功开始移动
     */
    public boolean goTo(int x, int y, int z) {
        return goTo(new BlockPos(x, y, z));
    }
    
    /**
     * 移动到指定坐标
     * 
     * @param pos 目标坐标
     * @return 是否成功开始移动
     */
    public boolean goTo(BlockPos pos) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        try {
            Goal goal = new GoalBlock(pos);
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            LOGGER.info("Going to {}", pos);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to go to {}", pos, e);
            return false;
        }
    }
    
    /**
     * 移动到指定坐标附近
     * 
     * @param pos 目标坐标
     * @param range 范围
     * @return 是否成功开始移动
     */
    public boolean goNear(BlockPos pos, int range) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        try {
            Goal goal = new GoalNear(pos, range);
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            LOGGER.info("Going near {} (range: {})", pos, range);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to go near {}", pos, e);
            return false;
        }
    }
    
    // ==================== 挖掘控制 ====================
    
    /**
     * 挖掘指定方块
     * 
     * @param block 方块类型
     * @param count 需要的数量
     * @return 是否成功开始挖掘
     */
    public boolean mine(Block block, int count) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        try {
            // 将方块转换为物品 ID
            String blockName = block.builtInRegistryHolder().key().location().getPath();
            baritone.getMineProcess().mineByName(count, blockName);
            LOGGER.info("Mining {} x{}", blockName, count);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to mine {}", block, e);
            return false;
        }
    }
    
    /**
     * 挖掘指定方块位置
     * 
     * @param pos 方块位置
     * @return 是否成功开始挖掘
     */
    public boolean mineBlock(BlockPos pos) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        try {
            baritone.getGetToBlockProcess().getToBlock(pos);
            LOGGER.info("Mining block at {}", pos);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to mine block at {}", pos, e);
            return false;
        }
    }
    
    // ==================== 物品获取 ====================
    
    /**
     * 注册物品来源
     * 
     * @param source 物品来源
     */
    public void registerItemSource(ItemSource source) {
        itemSources.computeIfAbsent(source.getItem(), k -> new ArrayList<>()).add(source);
        // 按优先级排序
        itemSources.get(source.getItem()).sort(Comparator.comparingInt(ItemSource::getPriority));
        LOGGER.debug("Registered item source: {} -> {} ({})", 
            source.getItem(), source.getType(), source.getPriority());
    }
    
    /**
     * 获取指定物品
     * 
     * @param item 物品类型
     * @param count 需要的数量
     * @return 是否成功开始获取
     */
    public boolean getItem(Item item, int count) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        // 检查当前背包中是否已有足够数量
        int currentCount = countItemInInventory(item);
        if (currentCount >= count) {
            LOGGER.info("Already have {} x{} in inventory", item, count);
            return true;
        }
        
        int needed = count - currentCount;
        
        // 查找可用的来源
        List<ItemSource> sources = itemSources.get(item);
        if (sources == null || sources.isEmpty()) {
            // 没有注册的来源，尝试自动寻找
            return autoFindItem(item, needed);
        }
        
        // 使用优先级最高的来源
        for (ItemSource source : sources) {
            if (source.getEstimatedCount() >= needed) {
                LOGGER.info("Using {} source for {} x{}", source.getType(), item, needed);
                return source.execute();
            }
        }
        
        // 所有来源数量都不够，尝试所有来源
        for (ItemSource source : sources) {
            LOGGER.info("Using {} source for {} (partial)", source.getType(), item);
            if (source.execute()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 自动查找物品来源
     */
    private boolean autoFindItem(Item item, int count) {
        // 尝试作为方块挖掘
        Block block = Block.byItem(item);
        if (block != null && !block.defaultBlockState().isAir()) {
            return mine(block, count);
        }
        
        LOGGER.warn("No known source for item: {}", item);
        return false;
    }
    
    /**
     * 计算背包中物品数量
     */
    private int countItemInInventory(Item item) {
        if (mc.player == null) return 0;
        
        int count = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    // ==================== 控制方法 ====================
    
    /**
     * 停止当前任务
     */
    public void stop() {
        if (!initialized) return;
        
        baritone.getPathingControlManager().cancelEverything();
        LOGGER.info("Stopped all tasks");
    }
    
    /**
     * 暂停当前任务
     */
    public void pause() {
        if (!initialized) return;
        
        baritone.getPathingControlManager().pause();
        LOGGER.info("Paused");
    }
    
    /**
     * 恢复任务
     */
    public void resume() {
        if (!initialized) return;
        
        baritone.getPathingControlManager().resume();
        LOGGER.info("Resumed");
    }
    
    /**
     * 检查是否正在执行任务
     */
    public boolean isBusy() {
        if (!initialized) return false;
        return baritone.getPathingControlManager().mostRecentInControl().isPresent();
    }
    
    // ==================== 搭路优化 ====================
    
    /**
     * 执行优化的搭路
     * 放完方块后后退再放，而不是立刻转头
     * 
     * @param startPos 起始位置
     * @param direction 方向（0=X+, 1=Z+, 2=X-, 3=Z-）
     * @param length 长度
     * @return 是否成功
     */
    public boolean bridge(BlockPos startPos, int direction, int length) {
        if (!initialized || mc.player == null) {
            return false;
        }
        
        // 搭路逻辑将在后续实现
        // 需要配合 Baritone 的自定义 Goal
        LOGGER.info("Bridging from {} direction {} length {}", startPos, direction, length);
        return true;
    }
    
    // ==================== Getter ====================
    
    public IBaritone getBaritone() {
        return baritone;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
}
