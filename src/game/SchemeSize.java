package mindustry.game;

import arc.*;
import arc.util.*;
import arc.input.*;
import arc.struct.*;
import arc.KeyBinds.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.ui.dialogs.*;
import mindustry.mod.*;
import mindustry.game.EventType.*;
import mindustry.input.*;
import mindustry.Vars;

import java.util.*;

public class SchemeSize extends Mod{

    public SchemeSize(){
        Events.on(ClientLoadEvent.class, e -> {
            // wait 10 secs, because... idk
            Time.runTask(10f, () -> {
                // Change schematics
                Vars.schematics = new Schematics512();
                Vars.schematics.loadSync();

                // Change input
                if(!Vars.mobile){
                    Vars.control.setInput(new DesktopInput512());
                }

                // Add settings
                var game = Vars.ui.settings.game;
                game.checkPref("secret", false);
                game.sliderPref("maxzoommul", 4, 4, 8, 1, i -> i / 4f + "x");
                game.sliderPref("minzoommul", 4, 4, 8, 1, i -> i / 4f + "x");
                game.sliderPref("copysize", 512, 32, 512, 32, i -> Core.bundle.format("setting.blocks", i));
                game.sliderPref("breaksize", 512, 32, 512, 32, i -> Core.bundle.format("setting.blocks", i));
                game.checkPref("copyshow", true);
                game.checkPref("breakshow", true);
                game.getCells().get(11).visible(false); // Hide secret

                // Add zoom scale
                Stack elementMax = (Stack)game.getCells().get(12).get();
                Stack elementMin = (Stack)game.getCells().get(13).get();
                Slider sliderMax = (Slider)elementMax.getChildren().get(0);
                Slider sliderMin = (Slider)elementMin.getChildren().get(0);
                sliderMax.changed(() -> { Vars.renderer.maxZoom = sliderMax.getValue() / 4f * 6f; });
                sliderMin.changed(() -> { Vars.renderer.minZoom = 1 / (sliderMin.getValue() / 4f) * 1.5f; });
                Vars.renderer.maxZoom = sliderMax.getValue() / 4f * 6f; // Apply zoom
                Vars.renderer.minZoom = 1f / (sliderMin.getValue() / 4f) * 1.5f;

                // Add keybinds
                KeyBind[] keybinds = (KeyBind[])Binding.values();
                KeyBind[] keymoded = (KeyBind[])ModBinding.values();
                // Seq<KeyBind> binds = new Seq<KeyBind>(keybinds);
                // Seq<KeyBind> moded = new Seq<KeyBind>(keymoded);
                // binds.insert(51, (KeyBind)moded.get(0));
                // binds.add((KeyBind)moded.get(0));
                // binds.addAll(moded);
                System.arraycopy(keymoded, 0, keybinds, 58, 1)
                Core.keybinds.setDefaults(keybinds);
                Vars.ui.controls = new KeybindDialog(); // Update dialog

                // Seq<KeyBind> binds = new Seq((KeyBind[])Binding.values());
                // Seq<KeyBind> moded = new Seq((KeyBind[])ModBinding.values());
                // binds.insert(51, (KeyBind)moded.get(0));
                // Core.keybinds.setDefaults((KeyBind[])binds.items);
                // Vars.ui.controls = new KeybindDialog(); // Update dialog

                // KeyBind[] binds = (KeyBind[])Binding.values();
                // KeyBind[] moded = (KeyBind[])ModBinding.values();
                // binds.splice(3, 0, moded[0]); // Core Items
                // Core.keybinds.setDefaults(binds.concat(moded));
                // Vars.ui.controls = new KeybindDialog(); // Update dialog

                // Core.keybinds.setDefaults(ExBinding.values());
                // KeybindLoader.load(); // copy of Core.keybinds.load()
                // Vars.ui.controls = new KeybindDialog(); // Update dialog

                // Add logs
                // Log.info(Vars.schematics);
                // Log.info(Vars.control.input);
            });
        });
    }
}