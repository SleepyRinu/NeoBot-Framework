package com.ragenc.event.impl;

import com.ragenc.event.Event;

/**
 * BotShutdownEvent - 机器人关闭事件
 * 
 * 在框架关闭时发布。
 * 用于通知其他组件清理资源。
 * 
 * @author NeoBot Team
 */
public class BotShutdownEvent extends Event {
    
    private final String reason;
    
    public BotShutdownEvent() {
        this("Normal shutdown");
    }
    
    public BotShutdownEvent(String reason) {
        this.reason = reason;
    }
    
    public String getReason() {
        return reason;
    }
}
