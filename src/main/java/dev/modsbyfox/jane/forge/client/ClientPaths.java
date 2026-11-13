package dev.modsbyfox.jane.forge.client;

import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

final class ClientPaths {
    private ClientPaths() { }

    static Path gameDir() { return FMLPaths.GAMEDIR.get(); }
}
