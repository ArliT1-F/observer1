package com.icy404.observer;

import com.icy404.observer.util.LogUtil;
import net.fabricmc.api.ClientModInitializer;

public final class ObserverClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LogUtil.info("Observer client initializing.");
    }
}
