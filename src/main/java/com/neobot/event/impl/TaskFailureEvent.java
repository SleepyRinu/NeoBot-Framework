package com.neobot.event.impl;

import com.neobot.event.Event;
import com.neobot.task.Task;

/**
 * TaskFailureEvent - 任务失败事件
 * 
 * 在任务失败时发布。
 * 
 * @author NeoBot Team
 */
public class TaskFailureEvent extends Event {
    
    private final Task task;
    private final Exception error;
    
    public TaskFailureEvent(Task task, Exception error) {
        super(task);
        this.task = task;
        this.error = error;
    }
    
    public Task getTask() {
        return task;
    }
    
    public Exception getError() {
        return error;
    }
    
    public String getErrorMessage() {
        return error != null ? error.getMessage() : "Unknown error";
    }
}
