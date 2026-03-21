package com.neobot.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PositionUtils - 位置工具类
 * 
 * 提供位置相关的计算工具方法。
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public final class PositionUtils {
    
    private PositionUtils() {} // 防止实例化
    
    /**
     * 计算两点间的水平距离
     * 
     * @param a 点 A
     * @param b 点 B
     * @return 水平距离
     */
    public static double horizontalDistance(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * 计算两点间的水平距离
     * 
     * @param a 点 A
     * @param b 点 B
     * @return 水平距离
     */
    public static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * 计算两点间的直线距离
     * 
     * @param a 点 A
     * @param b 点 B
     * @return 直线距离
     */
    public static double distance(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }
    
    /**
     * 计算两点间的直线距离
     * 
     * @param a 点 A
     * @param b 点 B
     * @return 直线距离
     */
    public static double distance(Vec3 a, Vec3 b) {
        return a.distanceTo(b);
    }
    
    /**
     * 获取实体到方块的距离
     * 
     * @param entity 实体
     * @param pos 方块位置
     * @return 距离
     */
    public static double distanceToBlock(Entity entity, BlockPos pos) {
        return entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
    
    /**
     * 获取方块中心位置
     * 
     * @param pos 方块位置
     * @return 中心坐标
     */
    public static Vec3 getCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
    
    /**
     * 获取方块底部中心位置
     * 
     * @param pos 方块位置
     * @return 底部中心坐标
     */
    public static Vec3 getBottomCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
    
    /**
     * 计算方向向量
     * 
     * @param from 起点
     * @param to 终点
     * @return 归一化的方向向量
     */
    public static Vec3 getDirection(Vec3 from, Vec3 to) {
        return to.subtract(from).normalize();
    }
    
    /**
     * 计算朝向目标的角度（偏航）
     * 
     * @param from 起点
     * @param to 终点
     * @return 偏航角（度）
     */
    public static float getYaw(Vec3 from, Vec3 to) {
        Vec3 direction = getDirection(from, to);
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
    
    /**
     * 计算朝向目标的角度（俯仰）
     * 
     * @param from 起点
     * @param to 终点
     * @return 俯仰角（度）
     */
    public static float getPitch(Vec3 from, Vec3 to) {
        Vec3 direction = getDirection(from, to);
        return (float) Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));
    }
    
    /**
     * 获取附近所有位置（曼哈顿距离）
     * 
     * @param center 中心位置
     * @param radius 半径
     * @return 附近位置列表
     */
    public static List<BlockPos> getNearbyPositions(BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= radius) {
                        positions.add(center.offset(x, y, z));
                    }
                }
            }
        }
        
        return positions;
    }
    
    /**
     * 获取球范围内的位置
     * 
     * @param center 中心位置
     * @param radius 半径
     * @return 范围内的位置列表
     */
    public static List<BlockPos> getPositionsInSphere(BlockPos center, double radius) {
        List<BlockPos> positions = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (distance(center, pos) <= radius) {
                        positions.add(pos);
                    }
                }
            }
        }
        
        return positions;
    }
    
    /**
     * 按距离排序位置
     * 
     * @param positions 位置列表
     * @param reference 参考点
     * @return 排序后的列表
     */
    public static List<BlockPos> sortByDistance(List<BlockPos> positions, BlockPos reference) {
        List<BlockPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(p -> p.distSqr(reference)));
        return sorted;
    }
    
    /**
     * 获取最近的位置
     * 
     * @param positions 位置列表
     * @param reference 参考点
     * @return 最近的位置
     */
    public static BlockPos getClosest(List<BlockPos> positions, BlockPos reference) {
        if (positions.isEmpty()) {
            return null;
        }
        
        BlockPos closest = null;
        double minDist = Double.MAX_VALUE;
        
        for (BlockPos pos : positions) {
            double dist = pos.distSqr(reference);
            if (dist < minDist) {
                minDist = dist;
                closest = pos;
            }
        }
        
        return closest;
    }
    
    /**
     * 检查两个位置是否相邻
     * 
     * @param a 位置 A
     * @param b 位置 B
     * @return 是否相邻
     */
    public static boolean isAdjacent(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 &&
               Math.abs(a.getY() - b.getY()) <= 1 &&
               Math.abs(a.getZ() - b.getZ()) <= 1;
    }
    
    /**
     * 将 BlockPos 转换为整数元组
     * 
     * @param pos 方块位置
     * @return 整数元组 [x, y, z]
     */
    public static int[] toIntArray(BlockPos pos) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ()};
    }
    
    /**
     * 从整数数组创建 BlockPos
     * 
     * @param arr 整数数组 [x, y, z]
     * @return 方块位置
     */
    public static BlockPos fromIntArray(int[] arr) {
        if (arr.length < 3) {
            throw new IllegalArgumentException("Array must have at least 3 elements");
        }
        return new BlockPos(arr[0], arr[1], arr[2]);
    }
}
