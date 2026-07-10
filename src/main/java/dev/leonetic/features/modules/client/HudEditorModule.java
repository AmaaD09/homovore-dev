package dev.leonetic.features.modules.client;

import dev.leonetic.features.gui.HudEditor;
import dev.leonetic.features.modules.Module;

public class HudEditorModule extends Module {

    public HudEditorModule() {
        super("HudEditor", "Drag & snap HUD elements into place", Category.CLIENT);
    }

    @Override
    public void onEnable() {
        mc.setScreen(new HudEditor());
    }

    @Override
    public void onTick() {
        if (!(mc.screen instanceof HudEditor)) {
            disable();
        }
    }
}
