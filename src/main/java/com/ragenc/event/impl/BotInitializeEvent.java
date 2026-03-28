package com.ragenc.event.impl;

import com.ragenc.core.BotFramework;
import com.ragenc.event.Event;

/**
 * BotInitializeEvent - 机器人初始化事件
 * 
 * 在框架初始化完成时发布。
 * 用于通知其他组件初始化完成。
 * 
 * @author NeoBot Team
 */
public class BotInitializeEvent extends Event {
    
    private final BotFramework framework;
    
    public BotInitializeEvent(BotFramework framework) {
        super(framework);
        this.framework = framework;
    }
    
    public BotFramework getFramework() {
        return framework;
    }
}
