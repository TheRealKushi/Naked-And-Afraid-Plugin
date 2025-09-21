// V1_8/SpawnData

package com.crimsonwarpedcraft.nakedandafraid.v1_8.spawn;

import org.bukkit.Location;

public class SpawnData {
    private final Location location;
    private final String targetPlayerName;

    public SpawnData(Location location, String targetPlayerName) {
        this.location = location;
        this.targetPlayerName = targetPlayerName;
    }

    public Location getLocation() {
        return location;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }
}