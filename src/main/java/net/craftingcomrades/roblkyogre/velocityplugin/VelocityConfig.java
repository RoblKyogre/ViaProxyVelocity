package net.craftingcomrades.roblkyogre.velocityplugin;

import java.io.File;
import net.lenni0451.optconfig.ConfigLoader;
import net.lenni0451.optconfig.annotations.Description;
import net.lenni0451.optconfig.annotations.OptConfig;
import net.lenni0451.optconfig.annotations.Option;
import net.lenni0451.optconfig.provider.ConfigProvider;
import net.raphimc.viaproxy.util.logging.Logger;

@OptConfig
public class VelocityConfig {

    @Option("ForwardingSecret")
    @Description(
        "The secret that will be used to verify the connection between the proxy and the server"
    )
    public static String forwardingSecret = "";

    public static void load() {
        try {
            ConfigLoader<VelocityConfig> configLoader = new ConfigLoader<>(
                VelocityConfig.class
            );
            configLoader.getConfigOptions().setResetInvalidOptions(true);
            configLoader.loadStatic(
                ConfigProvider.file(new File("velocity.yml"))
            );
        } catch (Throwable t) {
            Logger.LOGGER.error(
                "Failed to load the velocity configuration!",
                t
            );
            System.exit(-1);
        }
    }
}
