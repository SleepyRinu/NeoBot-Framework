package com.neobot.event.impl;

import com.neobot.event.Event;

/**
 * ConfigReloadEvent - 配置重载事件
 * 
 * 在配置更改时发布。
 * 用于通知组件重新加载配置。
 * 
 * @author NeoBot Team
 */
public class ConfigReloadEvent extends Event {
    
    private final String configKey;
    private final Object newValue;
    
    public ConfigReloadEvent(String configKey, Object newValue) {
        this.configKey = configKey;
        this.newValue = newValue;
    }
    
    public String getConfigKey() {
        return configKey;
    }
    
    public Object getNewValue() {
        return newValue;
    }
}
