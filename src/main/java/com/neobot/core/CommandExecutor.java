package com.neobot.core;

import com.neobot.event.EventBus;
import com.neobot.task.Task;
import com.neobot.task.TaskChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CommandExecutor - 命令执行器
 * 
 * 处理用户命令，将命令转换为任务执行。
 * 
 * 支持：
 * - 命令注册和注销
 * - 命令别名
 * - 参数解析
 * - 权限检查
 * - 命令帮助
 * 
 * 参考 Altoclef 的命令系统：
 * - 使用 . 前缀的命令
 * - 支持命令参数
 * - 支持命令补全
 * 
 * @author NeoBot Team
 * @version 1.0.0
 */
public class CommandExecutor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoBot/CommandExecutor");
    
    /** 命令前缀 */
    private static final String COMMAND_PREFIX = ".";
    
    /** 任务运行器 */
    private final TaskRunner taskRunner;
    
    /** 事件总线 */
    private final EventBus eventBus;
    
    /** 命令映射：命令名 -> Command */
    private final Map<String, Command> commands = new ConcurrentHashMap<>();
    
    /** 别名映射：别名 -> 命令名 */
    private final Map<String, String> aliases = new ConcurrentHashMap<>();
    
    /** 命令历史 */
    private final List<String> commandHistory = Collections.synchronizedList(new ArrayList<>());
    
    /** 最大历史记录数 */
    private static final int MAX_HISTORY = 100;
    
    /**
     * Command - 命令接口
     */
    @FunctionalInterface
    public interface Command {
        /**
         * 执行命令
         * 
         * @param args 命令参数
         * @return 执行结果消息
         */
        String execute(String[] args);
    }
    
    /**
     * 构造函数
     * 
     * @param taskRunner 任务运行器
     * @param eventBus 事件总线
     */
    public CommandExecutor(TaskRunner taskRunner, EventBus eventBus) {
        this.taskRunner = Objects.requireNonNull(taskRunner, "TaskRunner cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "EventBus cannot be null");
        
        LOGGER.debug("CommandExecutor initialized");
    }
    
    /**
     * 注册默认命令
     */
    public void registerDefaultCommands() {
        LOGGER.info("Registering default commands...");
        
        // 帮助命令
        register("help", args -> {
            StringBuilder sb = new StringBuilder("Available commands:\n");
            commands.keySet().stream()
                    .sorted()
                    .forEach(cmd -> sb.append("  ").append(COMMAND_PREFIX).append(cmd).append("\n"));
            return sb.toString();
        }, "?", "h");
        
        // 停止命令
        register("stop", args -> {
            taskRunner.cancelAll();
            return "All tasks cancelled";
        }, "cancel", "abort");
        
        // 暂停命令
        register("pause", args -> {
            taskRunner.pause();
            return "TaskRunner paused";
        });
        
        // 恢复命令
        register("resume", args -> {
            taskRunner.resume();
            return "TaskRunner resumed";
        });
        
        // 状态命令
        register("status", args -> {
            int active = taskRunner.getActiveTaskCount();
            int queued = taskRunner.getQueuedTaskCount();
            return String.format("Tasks: %d active, %d queued", active, queued);
        }, "info");
        
        // 版本命令
        register("version", args -> "NeoBot Framework v1.0.0", "v");
        
        LOGGER.info("Default commands registered");
    }
    
    // ==================== 命令注册 ====================
    
    /**
     * 注册命令
     * 
     * @param name 命令名
     * @param command 命令实现
     * @return 注册的命令名
     */
    public String register(String name, Command command) {
        return register(name, command, new String[0]);
    }
    
    /**
     * 注册命令（带别名）
     * 
     * @param name 命令名
     * @param command 命令实现
     * @param aliasList 别名列表
     * @return 注册的命令名
     */
    public String register(String name, Command command, String... aliasList) {
        Objects.requireNonNull(name, "Command name cannot be null");
        Objects.requireNonNull(command, "Command cannot be null");
        
        String normalizedName = name.toLowerCase().trim();
        
        // 注册命令
        commands.put(normalizedName, command);
        
        // 注册别名
        for (String alias : aliasList) {
            if (alias != null && !alias.isEmpty()) {
                aliases.put(alias.toLowerCase().trim(), normalizedName);
            }
        }
        
        LOGGER.debug("Registered command: {} with {} aliases", normalizedName, aliasList.length);
        
        return normalizedName;
    }
    
    /**
     * 注销命令
     * 
     * @param name 命令名
     * @return 是否注销成功
     */
    public boolean unregister(String name) {
        String normalizedName = name.toLowerCase().trim();
        
        // 移除命令
        Command removed = commands.remove(normalizedName);
        
        if (removed != null) {
            // 移除相关别名
            aliases.entrySet().removeIf(e -> e.getValue().equals(normalizedName));
            LOGGER.debug("Unregistered command: {}", normalizedName);
            return true;
        }
        
        return false;
    }
    
    // ==================== 命令执行 ====================
    
    /**
     * 执行命令字符串
     * 
     * @param input 命令输入
     * @return 执行结果
     */
    public String execute(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // 检查命令前缀
        if (!input.startsWith(COMMAND_PREFIX)) {
            return "Unknown command. Use " + COMMAND_PREFIX + "help for available commands.";
        }
        
        // 移除前缀
        String commandPart = input.substring(COMMAND_PREFIX.length()).trim();
        
        if (commandPart.isEmpty()) {
            return "";
        }
        
        // 记录历史
        addToHistory(input);
        
        // 解析命令和参数
        String[] parts = commandPart.split("\\s+", 2);
        String commandName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];
        
        // 查找命令
        Command command = resolveCommand(commandName);
        
        if (command == null) {
            return "Unknown command: " + commandName + ". Use " + COMMAND_PREFIX + "help";
        }
        
        // 执行命令
        try {
            LOGGER.debug("Executing command: {} with {} args", commandName, args.length);
            return command.execute(args);
        } catch (Exception e) {
            LOGGER.error("Error executing command: {}", commandName, e);
            return "Error executing command: " + e.getMessage();
        }
    }
    
    /**
     * 解析命令名
     * 
     * @param name 命令名或别名
     * @return 命令实现
     */
    private Command resolveCommand(String name) {
        // 先尝试直接查找
        Command command = commands.get(name);
        
        // 再尝试别名
        if (command == null) {
            String actualName = aliases.get(name);
            if (actualName != null) {
                command = commands.get(actualName);
            }
        }
        
        return command;
    }
    
    // ==================== 任务命令辅助 ====================
    
    /**
     * 注册任务命令
     * 
     * @param name 命令名
     * @param taskSupplier 任务提供者
     */
    public void registerTaskCommand(String name, java.util.function.Function<String[], Task> taskSupplier) {
        register(name, args -> {
            try {
                Task task = taskSupplier.apply(args);
                taskRunner.submit(task);
                return "Task submitted: " + task.getName();
            } catch (Exception e) {
                return "Failed to create task: " + e.getMessage();
            }
        });
    }
    
    /**
     * 注册任务链命令
     * 
     * @param name 命令名
     * @param chainSupplier 任务链提供者
     */
    public void registerChainCommand(String name, java.util.function.Function<String[], TaskChain> chainSupplier) {
        register(name, args -> {
            try {
                TaskChain chain = chainSupplier.apply(args);
                taskRunner.submitChain(chain);
                return "Task chain submitted: " + chain.getName();
            } catch (Exception e) {
                return "Failed to create chain: " + e.getMessage();
            }
        });
    }
    
    // ==================== 历史管理 ====================
    
    /**
     * 添加到历史
     */
    private void addToHistory(String command) {
        commandHistory.add(command);
        while (commandHistory.size() > MAX_HISTORY) {
            commandHistory.remove(0);
        }
    }
    
    /**
     * 获取命令历史
     * 
     * @return 命令历史列表
     */
    public List<String> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(commandHistory));
    }
    
    // ==================== 查询方法 ====================
    
    /**
     * 获取所有命令名
     * 
     * @return 命令名集合
     */
    public Set<String> getCommandNames() {
        return Collections.unmodifiableSet(commands.keySet());
    }
    
    /**
     * 获取所有别名
     * 
     * @return 别名映射
     */
    public Map<String, String> getAliases() {
        return Collections.unmodifiableMap(new HashMap<>(aliases));
    }
    
    /**
     * 检查命令是否存在
     * 
     * @param name 命令名
     * @return 是否存在
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name.toLowerCase()) || aliases.containsKey(name.toLowerCase());
    }
    
    /**
     * 获取命令数量
     * 
     * @return 命令数量
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * 获取命令前缀
     * 
     * @return 命令前缀
     */
    public static String getCommandPrefix() {
        return COMMAND_PREFIX;
    }
}
