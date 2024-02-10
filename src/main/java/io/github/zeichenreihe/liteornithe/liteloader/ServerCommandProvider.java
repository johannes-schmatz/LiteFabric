package io.github.zeichenreihe.liteornithe.liteloader;

import net.minecraft.server.command.handler.CommandManager;

public interface ServerCommandProvider extends LiteMod {
    void provideCommands(CommandManager manager);
}
