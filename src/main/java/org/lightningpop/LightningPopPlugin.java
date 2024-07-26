package org.lightningpop;

import org.lightningpop.modules.LightningPopModule;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.core.event.IEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LightningPopPlugin extends Plugin {
    private static final Logger LOGGER = LogManager.getLogger("LightningPopPlugin");

    private final LightningPopModule lightningPopModule = new LightningPopModule();

    @Override
    public void onLoad() {
        LOGGER.info("Loading Lightning Pop Plugin...");

        // Register the LightningPopModule
        RusherHackAPI.getModuleManager().registerFeature(lightningPopModule);

        // Subscribe the module to the event bus
        IEventBus eventBus = RusherHackAPI.getEventBus();
        eventBus.subscribe(lightningPopModule);

        LOGGER.info("Lightning Pop Plugin loaded successfully.");
    }

    @Override
    public void onUnload() {
        LOGGER.info("Unloading Lightning Pop Plugin...");

        // Unsubscribe the module from the event bus
        IEventBus eventBus = RusherHackAPI.getEventBus();
        eventBus.unsubscribe(lightningPopModule);

        LOGGER.info("Lightning Pop Plugin unloaded.");
    }
}