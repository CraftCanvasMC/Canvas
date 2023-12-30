package dev.etil.mirai;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class MiraiCommand extends Command {

    public MiraiCommand() {
        super("mirai");
        this.description = "Mirai related commands";
        this.usageMessage = "/mirai [reload | version]";
        this.setPermission("bukkit.command.mirai");
    }

    public static void init() {
        MinecraftServer.getServer().server.getCommandMap().register("mirai", "Mirai", new MiraiCommand());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException {
        if (args.length == 1) {
            return Stream.of("reload", "version")
              .filter(arg -> arg.startsWith(args[0].toLowerCase()))
              .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;
        net.kyori.adventure.text.Component prefix = net.kyori.adventure.text.Component.text("Mirai » ")
            .color(net.kyori.adventure.text.format.TextColor.color(0x12fff6))
            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);

        if (args.length != 1) {
            sender.sendMessage(net.kyori.adventure.text.Component.text()
                .append(prefix)
                .color(net.kyori.adventure.text.format.TextColor.color(0xe8f9f9))
                .append(net.kyori.adventure.text.Component.text("Usage: ")
                .append(net.kyori.adventure.text.Component.text(usageMessage)))
                .build());
            args = new String[]{"version"};
        }

        if (args[0].equalsIgnoreCase("reload")) {
            MinecraftServer console = MinecraftServer.getServer();
            try {
                MiraiConfig.load();
            } catch (IOException e) {
                sender.sendMessage(net.kyori.adventure.text.Component.text("Failed to reload.", net.kyori.adventure.text.format.NamedTextColor.RED));
                e.printStackTrace();
                return true;
            }
            console.server.reloadCount++;

            Command.broadcastCommandMessage(sender, net.kyori.adventure.text.Component.text()
                .append(prefix)
                .append(net.kyori.adventure.text.Component.text("Mirai configuration has been reloaded.", net.kyori.adventure.text.format.TextColor.color(0xe8f9f9)))
                .build());
        } else if (args[0].equalsIgnoreCase("version")) {
            Command.broadcastCommandMessage(sender, net.kyori.adventure.text.Component.text()
                .append(prefix)
                .append(net.kyori.adventure.text.Component.text("This server is running " + Bukkit.getName() + " version " + Bukkit.getVersion() + " (Implementing API version " + Bukkit.getBukkitVersion() + ")", net.kyori.adventure.text.format.TextColor.color(0xe8f9f9)))
                .build());
        }

        return true;
    }
}
