package com.ragenc.event.impl;

import com.ragenc.event.Event;
import com.ragenc.task.Task;

/**
 * TaskStartEvent - 任务开始事件
 * 
 * 在任务开始执行时发布。
 * 
 * @author NeoBot Team
 */
public class TaskStartEvent extends Event {
    
    private final Task task;
    
    public TaskStartEvent(Task task) {
        super(task);
        this.task = task;
    }
    
    public Task getTask() {
        return task;
    }
}
