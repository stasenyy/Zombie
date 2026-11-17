package de.zombiegame;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

public final class ZombieCommand implements TabExecutor {
    private final ZombiePlugin plugin;

    public ZombieCommand(ZombiePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zombie.admin")) {
            Fx.msg(sender, "<red>Dafür hast du keine Rechte.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("start")) {
            plugin.game().start(sender);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) plugin.menu().open(p);
            else Fx.msg(sender, "<gray>Nutze: /zombie <start|stop|reload|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> plugin.game().start(sender);
            case "stop" -> plugin.game().stop(sender);
            case "reload" -> {
                plugin.reloadConfig();
                Fx.msg(sender, "<green>Config neu geladen." + (plugin.settings().hasSkin() ? "" : " <red>(Skin fehlt noch!)"));
            }
            case "status" -> Fx.msg(sender, "<gray>Status: <white>" + plugin.game().state()
                    + " <gray>| Skin: " + (plugin.settings().hasSkin() ? "<green>ok" : "<red>fehlt"));
            default -> Fx.msg(sender, "<gray>Nutze: /zombie <start|stop|reload|status|menu>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("zombie") && args.length == 1) {
            return List.of("menu", "start", "stop", "reload", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
