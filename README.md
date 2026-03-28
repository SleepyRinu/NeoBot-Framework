# RageNC

基于 NeoForge 的 Minecraft 机器人框架，参考 Altoclef 的架构设计。

## 特性

- **任务系统**: 灵活的任务调度和执行
- **事件驱动**: 解耦的事件发布/订阅机制
- **追踪器**: 游戏状态监控和数据收集
- **命令系统**: 可扩展的命令处理
- **行为控制**: 模块化的机器人行为

## 环境要求

- Minecraft 1.21.1
- NeoForge 21.1.77
- Java 21

## 项目结构

```
ragenc/
├── src/main/java/com/ragenc/
│   ├── RageNC.java           # 主入口
│   ├── core/
│   │   ├── BotFramework.java # 框架核心
│   │   ├── TaskRunner.java   # 任务执行器
│   │   ├── TrackerManager.java # 追踪器管理
│   │   ├── EventBus.java     # 事件总线
│   │   └── CommandExecutor.java # 命令执行器
│   ├── task/
│   │   ├── Task.java         # 任务基类
│   │   ├── TaskChain.java    # 任务链
│   │   ├── TaskState.java    # 任务状态
│   │   ├── TaskPriority.java # 任务优先级
│   │   └── impl/
│   │       ├── MovementTask.java # 移动任务
│   │       ├── InteractTask.java # 交互任务
│   │       └── CombatTask.java   # 战斗任务
│   ├── behavior/
│   │   └── BotBehavior.java  # 行为基类
│   ├── tracker/
│   │   └── Tracker.java      # 追踪器基类
│   ├── event/
│   │   ├── Event.java        # 事件基类
│   │   ├── EventHandler.java # 事件处理器
│   │   └── impl/             # 事件实现
│   └── util/
│       └── PositionUtils.java # 位置工具
└── src/main/resources/
    └── META-INF/
        ├── neoforge.mods.toml # 模组配置
        └── accesstransformer.cfg
```

## 核心组件

### BotFramework

框架核心，协调所有子系统：

```java
BotFramework framework = new BotFramework(...);
framework.submitTask(new MyTask());
framework.pause();
framework.resume();
```

### TaskRunner

任务调度和执行：

```java
// 提交任务
UUID taskId = taskRunner.submit(task);

// 提交任务链
taskRunner.submitChain(chain);

// 取消任务
taskRunner.cancel(taskId);
```

### EventBus

事件发布/订阅：

```java
// 订阅事件
eventBus.subscribe(MyEvent.class, event -> {
    // 处理事件
});

// 发布事件
eventBus.post(new MyEvent());
```

### TrackerManager

状态追踪：

```java
// 注册追踪器
trackerManager.register(myTracker);

// 获取追踪器
Optional<MyTracker> tracker = trackerManager.getTracker(MyTracker.class);
```

## 扩展开发

### 创建自定义任务

```java
public class MyTask extends Task {
    public MyTask() {
        super("my_task", TaskPriority.NORMAL);
    }

    @Override
    protected boolean execute(TrackerManager trackerManager) {
        // 任务逻辑
        return true;
    }
}
```

### 创建自定义行为

```java
public class MyBehavior extends BotBehavior {
    public MyBehavior() {
        super("my_behavior", "我的行为");
    }

    @Override
    protected void enable() {
        // 启用逻辑
    }

    @Override
    protected void disable() {
        // 禁用逻辑
    }

    @Override
    protected void update() {
        // 每刻更新
    }
}
```

### 创建自定义追踪器

```java
public class MyTracker extends Tracker {
    public MyTracker() {
        super("my_tracker", "我的追踪器", true);
    }

    @Override
    protected void enable() {}

    @Override
    protected void disable() {}

    @Override
    protected boolean updateData() {
        // 更新数据
        return false;
    }
}
```

## 构建

```bash
./gradlew build
```

## 许可证

MIT License

## 参考

- [Altoclef](https://github.com/gaucho-matrero/altoclef) - 参考架构设计
- [NeoForge](https://neoforged.net/) - Minecraft 模组加载器
