package com.neobot;

import com.neobot.task.Task;
import com.neobot.task.TaskChain;
import com.neobot.task.TaskPriority;
import com.neobot.task.TaskState;
import com.neobot.tracker.TrackerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 测试类
 */
class TaskTest {
    
    private TrackerManager trackerManager;
    
    @BeforeEach
    void setUp() {
        trackerManager = new TrackerManager();
    }
    
    @Test
    void testTaskCreation() {
        Task task = new TestTask("test", TaskPriority.NORMAL);
        
        assertNotNull(task.getId());
        assertEquals("test", task.getName());
        assertEquals(TaskPriority.NORMAL, task.getPriority());
        assertEquals(TaskState.PENDING, task.getState());
    }
    
    @Test
    void testTaskPriority() {
        Task lowTask = new TestTask("low", TaskPriority.LOW);
        Task highTask = new TestTask("high", TaskPriority.HIGH);
        
        assertTrue(highTask.getPriority() > lowTask.getPriority());
    }
    
    @Test
    void testTaskChainBuilder() {
        TaskChain chain = new TaskChain.Builder("test_chain")
                .then(new TestTask("task1", TaskPriority.NORMAL))
                .then(new TestTask("task2", TaskPriority.NORMAL))
                .build();
        
        assertEquals("test_chain", chain.getName());
        assertEquals(2, chain.getTaskCount());
        assertEquals(0, chain.getCurrentIndex());
    }
    
    @Test
    void testTaskMetadata() {
        Task task = new TestTask("test", TaskPriority.NORMAL);
        
        task.setMetadata("key1", "value1");
        task.setMetadata("key2", 123);
        
        assertEquals("value1", task.getMetadata("key1"));
        assertEquals(123, task.getMetadata("key2"));
        assertEquals("default", task.getMetadata("nonexistent", "default"));
    }
    
    @Test
    void testTaskDependencies() {
        Task task1 = new TestTask("task1", TaskPriority.NORMAL);
        Task task2 = new TestTask("task2", TaskPriority.NORMAL);
        
        task2.addDependency(task1.getId());
        
        assertTrue(task2.getDependencies().contains(task1.getId()));
        
        task2.removeDependency(task1.getId());
        assertFalse(task2.getDependencies().contains(task1.getId()));
    }
    
    /**
     * 测试任务实现
     */
    private static class TestTask extends Task {
        
        public TestTask(String name, int priority) {
            super(name, priority);
        }
        
        @Override
        protected boolean execute(TrackerManager trackerManager) {
            return true;
        }
    }
}
