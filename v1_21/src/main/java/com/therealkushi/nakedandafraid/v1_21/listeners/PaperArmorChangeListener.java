package com.therealkushi.nakedandafraid.v1_21.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Handles Paper's PlayerArmorChangeEvent. Isolated for the same reason as
 * PaperAsyncChatListener - see ArmorDamageListener's javadoc.
 */
public class PaperArmorChangeListener implements Listener {

    private final ArmorDamageListener armorDamageListener;

    public PaperArmorChangeListener(ArmorDamageListener armorDamageListener) {
        this.armorDamageListener = armorDamageListener;
    }

    @EventHandler
    public void onPlayerArmorChange(PlayerArmorChangeEvent event) {
        armorDamageListener.handleArmorOrOffhandChange(event.getPlayer());
    }
}