package dev.leonetic.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.leonetic.Homovore;
import dev.leonetic.features.commands.Command;
import dev.leonetic.features.modules.player.InstantRekitModule;
import dev.leonetic.manager.CommandManager;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class LoadKitCommand extends Command {
    public LoadKitCommand() {
        super("loadkit");
        setDescription("Selects which saved kit InstantRekit will apply");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes(ctx -> load(InstantRekitModule.DEFAULT_KIT));
        builder.then(argument("name", word())
                .executes(ctx -> load(getString(ctx, "name"))));
    }

    private int load(String name) {
        InstantRekitModule module = Homovore.moduleManager.getModuleByClass(InstantRekitModule.class);
        if (module == null) return fail("InstantRekit module is not registered.");
        if (!module.loadKit(name)) {
            if (module.listKits().isEmpty()) {
                return fail("No kit named %s. No kits saved yet — use .savekit <name>.", name);
            }
            return fail("No kit named %s. Saved kits: %s", name, String.join(", ", module.listKits()));
        }
        return success("Loaded kit {green} %s {reset}. Open a container to apply it.", name);
    }
}
