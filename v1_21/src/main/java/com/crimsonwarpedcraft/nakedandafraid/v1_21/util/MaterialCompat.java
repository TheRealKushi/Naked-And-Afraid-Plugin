package com.crimsonwarpedcraft.nakedandafraid.v1_21.util;

import org.bukkit.Material;

public class MaterialCompat {
    /**
     * Gets a Material by its modern (1.13+) or legacy (pre-1.13) name, returning null if unavailable.
     * @param modernName The 1.13+ material name (e.g., GOLD_HELMET).
     * @param legacyName The pre-1.13 material name (e.g., GOLDEN_HELMET).
     * @return The Material or null if not found.
     */
    public static Material getMaterial(String modernName, String legacyName) {
        try {
            return Material.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            try {
                return Material.valueOf(legacyName);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    /**
     * Gets a Material by name, trying modern and legacy variants.
     * @param name The material name (case-insensitive).
     * @return The Material or null if not found.
     */
    public static Material getMaterial(String name) {
        if (name == null) return null;
        String upperName = name.toUpperCase();
        try {
            return Material.valueOf(upperName);
        } catch (IllegalArgumentException e) {
            return switch (upperName) {
                case "GOLDEN_HELMET" -> getMaterial("GOLD_HELMET", upperName);
                case "GOLDEN_CHESTPLATE" -> getMaterial("GOLD_CHESTPLATE", upperName);
                case "GOLDEN_LEGGINGS" -> getMaterial("GOLD_LEGGINGS", upperName);
                case "GOLDEN_BOOTS" -> getMaterial("GOLD_BOOTS", upperName);
                case "TOTEM_OF_UNDYING" -> getMaterial("TOTEM", upperName);
                case "LODESTONE" -> getMaterial("LODESTONE", upperName);
                default -> null;
            };
        }
    }
}