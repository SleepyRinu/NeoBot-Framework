package com.neobot.event.impl;

import com.neobot.event.Event;
import com.neobot.task.Task;

/**
 * TaskCompleteEvent - 任务完成事件
 * 
 * 在任务完成（成功或失败）时发布。
 * 
 * @author NeoBot Team
 */
public class TaskCompleteEvent extends Event {
    
    private final Task task;
    private final boolean success;
    
    public TaskCompleteEvent(Task task, boolean success) {
        super(task);
        this.task = task;
        this.success = success;
    }
    
    public Task getTask() {
        return task;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
