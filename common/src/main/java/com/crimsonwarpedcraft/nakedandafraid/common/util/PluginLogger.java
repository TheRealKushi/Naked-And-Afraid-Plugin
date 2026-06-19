package com.crimsonwarpedcraft.nakedandafraid.common.util;

/**
 * Minimal logging abstraction used by common utilities so they don't
 * depend on any specific version's NakedAndAfraid class.
 */
public interface PluginLogger {
    void debugLog(String message);
}