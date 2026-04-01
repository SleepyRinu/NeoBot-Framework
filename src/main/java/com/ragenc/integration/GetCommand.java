package com.ragenc.integration;

import com.mojang.brigadier.StringReader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GetCommand - 物品获取命令
 * 
 * 用法: .get <物品> [数量]
 * 
 * 支持的物品来源（可扩展）：
 * - 挖掘方块
 * - 从箱子获取
 * - 合成
 * - 交易
 * - 击杀生物
 * 
 * 物品来源通过注册 ItemProvider 实现，不硬编码
 * 
 * @author RageNC Team
 * @version 1.0.0
 */
public class GetCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RageNC/GetCommand");
    
    private final BaritoneIntegration baritone;
    private final Minecraft mc;
    
    /** 物品提供者注册表 */
    private final Map<Item, List<ItemProvider>> providers = new ConcurrentHashMap<>();
    
    /** 按类型索引的提供者 */
    private final Map<String, ItemProviderFactory> providerFactories = new ConcurrentHashMap<>();
    
    /**
     * 物品提供者接口
     * 定义如何获取某种物品
     */
    public interface ItemProvider {
        /** 获取来源类型 */
        String getType();
        
        /** 获取目标物品 */
        Item getItem();
        
        /** 预计可获取数量（-1 表示未知/无限） */
        int getEstimatedCount();
        
        /** 执行获取，返回是否成功开始 */
        boolean execute(int count);
        
        /** 获取优先级（数字越小优先级越高） */
        default int getPriority() { return 100; }
        
        /** 是否当前可用 */
        default boolean isAvailable() { return true; }
    }
    
    /**
     * 物品提供者工厂接口
     * 用于动态创建 ItemProvider
     */
    public interface ItemProviderFactory {
        /** 创建提供者 */
        ItemProvider create(Item item, Map<String, Object> params);
        
        /** 工厂类型名 */
        String getType();
    }
    
    // ==================== 内置提供者 ====================
    
    /**
     * 挖掘提供者 - 通过挖掘方块获取物品
     */
    public static class MiningProvider implements ItemProvider {
        private final Item item;
        private final ItemProviderFactory factory;
        
        public MiningProvider(Item item) {
            this.item = item;
            // 尝试找到对应的方块
        }
        
        @Override
        public String getType() { return "mining"; }
        
        @Override
        public Item getItem() { return item; }
        
        @Override
        public int getEstimatedCount() { return -1; } // 无限
        
        @Override
        public boolean execute(int count) {
            // 将物品转换为方块并挖掘
            // 这里的逻辑会在 BaritoneIntegration 中实现
            return true;
        }
        
        @Override
        public int getPriority() { return 10; }
    }
    
    /**
     * 箱子提供者 - 从箱子获取物品
     */
    public static class ChestProvider implements ItemProvider {
        private final Item item;
        private final int count;
        
        public ChestProvider(Item item, int count) {
            this.item = item;
            this.count = count;
        }
        
        @Override
        public String getType() { return "chest"; }
        
        @Override
        public Item getItem() { return item; }
        
        @Override
        public int getEstimatedCount() { return count; }
        
        @Override
        public boolean execute(int count) {
            // 寻找并从箱子获取物品
            return true;
        }
        
        @Override
        public int getPriority() { return 5; } // 优先从箱子拿
    }
    
    /**
     * 合成提供者 - 通过合成获取物品
     */
    public static class CraftingProvider implements ItemProvider {
        private final Item item;
        
        public CraftingProvider(Item item) {
            this.item = item;
        }
        
        @Override
        public String getType() { return "crafting"; }
        
        @Override
        public Item getItem() { return item; }
        
        @Override
        public int getEstimatedCount() { return -1; }
        
        @Override
        public boolean execute(int count) {
            // 执行合成
            return true;
        }
        
        @Override
        public int getPriority() { return 20; }
    }
    
    // ==================== 构造和初始化 ====================
    
    public GetCommand(BaritoneIntegration baritone) {
        this.baritone = baritone;
        this.mc = Minecraft.getInstance();
        
        // 注册默认提供者工厂
        registerDefaultFactories();
        
        LOGGER.info("GetCommand initialized");
    }
    
    private void registerDefaultFactories() {
        // 注册挖掘工厂
        registerProviderFactory("mining", (item, params) -> new MiningProvider(item));
        
        // 注册箱子工厂
        registerProviderFactory("chest", (item, params) -> {
            int count = params.containsKey("count") ? (int) params.get("count") : -1;
            return new ChestProvider(item, count);
        });
        
        // 注册合成工厂
        registerProviderFactory("crafting", (item, params) -> new CraftingProvider(item));
    }
    
    // ==================== 命令执行 ====================
    
    /**
     * 执行 .get 命令
     * 
     * @param args 命令参数 [物品名] [数量]
     * @return 结果消息
     */
    public String execute(String[] args) {
        if (args.length == 0) {
            return "Usage: .get <item> [count]\n" +
                   "Example: .get diamond 32\n" +
                   "         .get iron_ingot";
        }
        
        // 解析物品名
        String itemName = args[0].toLowerCase();
        
        // 解析数量
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
                if (count <= 0) {
                    return "Count must be positive";
                }
            } catch (NumberFormatException e) {
                return "Invalid count: " + args[1];
            }
        }
        
        // 查找物品
        Item item = findItem(itemName);
        if (item == null || item == Items.AIR) {
            return "Unknown item: " + itemName;
        }
        
        // 检查当前背包
        int currentCount = countInInventory(item);
        if (currentCount >= count) {
            return String.format("Already have %d x %s in inventory", currentCount, itemName);
        }
        
        int needed = count - currentCount;
        
        // 查找可用的提供者
        List<ItemProvider> availableProviders = findProviders(item);
        
        if (availableProviders.isEmpty()) {
            // 没有注册的提供者，尝试自动挖掘
            return tryAutoMine(item, itemName, needed);
        }
        
        // 按优先级执行
        for (ItemProvider provider : availableProviders) {
            if (!provider.isAvailable()) continue;
            
            int estimated = provider.getEstimatedCount();
            if (estimated == -1 || estimated >= needed) {
                LOGGER.info("Using {} provider for {} x{}", provider.getType(), itemName, needed);
                if (provider.execute(needed)) {
                    return String.format("Getting %d x %s (via %s)", needed, itemName, provider.getType());
                }
            }
        }
        
        return "Could not find a way to get " + itemName;
    }
    
    /**
     * 尝试自动挖掘物品
     */
    private String tryAutoMine(Item item, String itemName, int count) {
        // 检查是否可以通过挖掘获得
        // 这里需要物品-方块映射
        
        if (baritone.mine(net.minecraft.world.level.block.Block.byItem(item), count)) {
            return String.format("Mining %d x %s", count, itemName);
        }
        
        return "Don't know how to get: " + itemName + "\n" +
               "Register a provider with .register_provider <item> <type>";
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 查找物品
     */
    private Item findItem(String name) {
        try {
            // 使用 Minecraft 的注册表查找
            return mc.level.registryAccess()
                .registryOrThrow(Registries.ITEM)
                .getOptional(new net.minecraft.resources.ResourceLocation(name))
                .orElse(null);
        } catch (Exception e) {
            LOGGER.debug("Failed to find item: {}", name);
            return null;
        }
    }
    
    /**
     * 计算背包中的物品数量
     */
    private int countInInventory(Item item) {
        if (mc.player == null) return 0;
        
        int count = 0;
        for (var stack : mc.player.getInventory().items) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    /**
     * 查找物品的所有提供者
     */
    private List<ItemProvider> findProviders(Item item) {
        List<ItemProvider> result = new ArrayList<>(providers.getOrDefault(item, Collections.emptyList()));
        result.sort(Comparator.comparingInt(ItemProvider::getPriority));
        return result;
    }
    
    // ==================== 注册方法 ====================
    
    /**
     * 注册物品提供者
     */
    public void registerProvider(ItemProvider provider) {
        providers.computeIfAbsent(provider.getItem(), k -> new ArrayList<>()).add(provider);
        providers.get(provider.getItem()).sort(Comparator.comparingInt(ItemProvider::getPriority));
        LOGGER.debug("Registered {} provider for {}", provider.getType(), provider.getItem());
    }
    
    /**
     * 注册提供者工厂
     */
    public void registerProviderFactory(String type, ItemProviderFactory factory) {
        providerFactories.put(type, factory);
        LOGGER.debug("Registered provider factory: {}", type);
    }
    
    /**
     * 使用工厂创建并注册提供者
     */
    public boolean createProvider(String type, Item item, Map<String, Object> params) {
        ItemProviderFactory factory = providerFactories.get(type);
        if (factory == null) {
            LOGGER.warn("Unknown provider type: {}", type);
            return false;
        }
        
        ItemProvider provider = factory.create(item, params);
        registerProvider(provider);
        return true;
    }
}
