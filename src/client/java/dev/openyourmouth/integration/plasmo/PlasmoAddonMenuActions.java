package dev.openyourmouth.integration.plasmo;

import su.plo.voice.client.gui.settings.tab.AddonsTabWidget;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;

/** Bridges AddonConfig's static widget list with the currently open Add-ons tab. */
public final class PlasmoAddonMenuActions {
    private static AddonsTabWidget openTab;
    private static VoiceSettingsScreen settingsScreen;

    private PlasmoAddonMenuActions() {}

    public static void setOpenTab(AddonsTabWidget tab) {
        openTab = tab;
    }

    public static void setSettingsScreen(VoiceSettingsScreen screen) { settingsScreen = screen; }

    public static void refreshOpenTab() {
        if (openTab != null) openTab.init();
    }

    public static void refreshSettings() {
        if (settingsScreen != null) settingsScreen.init();
    }
}
