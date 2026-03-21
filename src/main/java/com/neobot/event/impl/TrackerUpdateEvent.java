package com.neobot.event.impl;

import com.neobot.event.Event;
import com.neobot.tracker.Tracker;

/**
 * TrackerUpdateEvent - 追踪器更新事件
 * 
 * 在追踪器数据更新后发布。
 * 
 * @author NeoBot Team
 */
public class TrackerUpdateEvent extends Event {
    
    private final Tracker tracker;
    
    public TrackerUpdateEvent(Tracker tracker) {
        super(tracker);
        this.tracker = tracker;
    }
    
    public Tracker getTracker() {
        return tracker;
    }
}
