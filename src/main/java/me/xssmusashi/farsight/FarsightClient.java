package me.xssmusashi.farsight;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FarsightClient implements ClientModInitializer {
    public static final String MOD_ID = "farsight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Farsight client initialising — alpha skeleton");
    }
}
