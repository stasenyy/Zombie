package de.zombiegame;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Kleine Helfer: Text, Titel, Sounds im Zombie-Look. */
public final class Fx {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_red><bold>☠ <red>ZOMBIE <dark_gray>» <gray>";

    private Fx() {}

    public static Component c(String s) { return MM.deserialize(s); }

    public static void msg(CommandSender s, String m) { s.sendMessage(c(PREFIX + m)); }

    public static void sound(Player p, String key, float vol, float pitch) {
        p.playSound(p.getLocation(), key, SoundCategory.MASTER, vol, pitch);
    }

    /** Zeiten in Ticks. */
    public static void title(Player p, String title, String sub, int in, int stay, int out) {
        p.showTitle(Title.title(c(title), c(sub), Title.Times.times(
                Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L))));
    }

    public static String fmt(int sec) {
        return String.format("%02d:%02d", Math.max(0, sec) / 60, Math.max(0, sec) % 60);
    }
}
