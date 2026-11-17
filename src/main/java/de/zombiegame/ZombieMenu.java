package de.zombiegame;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /zombie – Steuerungsmenue im Zombie-Look. Links-Klick = +, Rechts-Klick = -, Shift = x5. */
public final class ZombieMenu implements Listener {
    private final ZombiePlugin plugin;

    private record Num(int slot, String path, Material mat, String name, int step, int min, int max, String unit) {}
    private record Tog(int slot, String path, Material mat, String name, String desc) {}

    private static final List<Num> NUMS = List.of(
            new Num(19, "game.zombies", Material.ROTTEN_FLESH, "Anzahl Zombies", 1, 1, 10, ""),
            new Num(20, "game.prep-time-seconds", Material.CLOCK, "Roter Timer (bis Infektion)", 15, 15, 900, "s"),
            new Num(21, "game.grace-seconds", Material.IRON_SWORD, "Schutzphase (Zombie kann nicht angreifen)", 30, 0, 1800, "s"),
            new Num(22, "game.round-seconds", Material.RECOVERY_COMPASS, "Rundenzeit der Jagd", 60, 60, 3600, "s"),
            new Num(23, "game.lobby-border-radius", Material.IRON_BARS, "Start-Border (Radius)", 10, 10, 200, " Blöcke"),
            new Num(24, "game.game-border-radius", Material.BARRIER, "Spielfeld-Border (Radius, 0 = aus)", 50, 0, 5000, " Blöcke"),
            new Num(25, "game.extra-hearts-per-kill", Material.GOLDEN_APPLE, "Extra-Herzen pro Zombie-Kill", 1, 0, 10, " ♥"));

    private static final List<Tog> TOGS = List.of(
            new Tog(28, "display.hide-names", Material.NAME_TAG, "Namen verstecken", "Nametags aller Spieler weg"),
            new Tog(29, "display.hide-tablist", Material.PLAYER_HEAD, "Tab-Liste verstecken", "Verhindert Skin-Spoiler im Tab"),
            new Tog(30, "display.disable-chat", Material.WRITABLE_BOOK, "Chat sperren", "Keine Absprachen während der Runde"),
            new Tog(37, "features.heartbeat", Material.SCULK_SENSOR, "1 · Herzschlag", "Pulsiert schneller, je näher ein Zombie ist"),
            new Tog(38, "features.blood-night", Material.CRYING_OBSIDIAN, "2 · Blutnacht", "Ewige Nacht, Gewitter und Blitze"),
            new Tog(39, "features.fog", Material.COBWEB, "3 · Nebel des Grauens", "Dunkelheits-Pulse für Überlebende"),
            new Tog(40, "features.blood-rush", Material.REDSTONE, "4 · Blutrausch", "Zombie heilt sich nach einer Infektion"),
            new Tog(41, "features.groans", Material.ZOMBIE_HEAD, "5 · Zombie-Gebrüll", "Geräusche verraten Zombies in der Nähe"),
            new Tog(42, "features.last-survivor", Material.TOTEM_OF_UNDYING, "6 · Letzter Überlebender", "Der Letzte leuchtet für alle"),
            new Tog(43, "features.supply-drops", Material.CHEST, "7 · Nachschub-Kisten", "Kisten mit Loot, Koordinaten nur für Überlebende"));

    static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public ZombieMenu(ZombiePlugin plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Fx.c("<dark_red><bold>☠ ZOMBIE <red>CONTROL ☠"));
        render(h.inv);
        p.openInventory(h.inv);
    }

    private ItemStack item(Material m, String name, boolean glow, String... lore) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Fx.c("<!italic>" + name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) l.add(Fx.c("<!italic><gray>" + s));
            meta.lore(l);
            if (glow) meta.setEnchantmentGlintOverride(true);
        });
        return it;
    }

    private void render(Inventory inv) {
        Settings s = plugin.settings();
        for (int i = 0; i < 54; i++)
            inv.setItem(i, item(i < 9 || i >= 45 ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " ", false));

        inv.setItem(10, item(Material.LIME_CONCRETE, "<green><bold>▶ RUNDE STARTEN", true,
                "Start-Border, Countdown, dann", "Timer & Infektion", "", "<yellow>Klick zum Starten"));
        inv.setItem(12, item(Material.RED_CONCRETE, "<red><bold>■ RUNDE STOPPEN", false,
                "Bricht ab und setzt alles zurück", "", "<yellow>Klick zum Stoppen"));
        inv.setItem(14, item(Material.SKELETON_SKULL, "<dark_red><bold>☣ STATUS", false,
                "Phase: <white>" + plugin.game().state(),
                "Spieler: <white>" + plugin.game().participantCount(),
                "Zombie-Skin: " + (s.hasSkin() ? "<green>hinterlegt" : "<red>FEHLT (config.yml)")));
        inv.setItem(16, item(Material.COMPARATOR, "<gold><bold>⟳ CONFIG NEU LADEN", false, "Liest config.yml neu ein"));

        for (Num n : NUMS) {
            int v = s.getInt(n.path());
            String shown = n.unit().equals("s") ? Fx.fmt(v) + " min" : v + n.unit();
            inv.setItem(n.slot(), item(n.mat(), "<red><bold>" + n.name(), false,
                    "Aktuell: <white>" + shown, "", "<yellow>Linksklick <gray>+" + n.step(), "<yellow>Rechtsklick <gray>-" + n.step(), "<yellow>Shift <gray>x5"));
        }
        for (Tog t : TOGS) {
            boolean on = s.bool(t.path());
            inv.setItem(t.slot(), item(t.mat(), (on ? "<green><bold>" : "<red><bold>") + t.name() + (on ? " <green>AN" : " <red>AUS"), on,
                    t.desc(), "", "<yellow>Klick zum Umschalten"));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("zombie.admin")) return;
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof Holder)) return;
        Settings s = plugin.settings();
        int slot = e.getSlot();

        switch (slot) {
            case 10 -> { p.closeInventory(); plugin.game().start(p); return; }
            case 12 -> { plugin.game().stop(p); }
            case 16 -> { plugin.reloadConfig(); Fx.msg(p, "<green>Config neu geladen."); }
            default -> { }
        }
        for (Num n : NUMS) {
            if (n.slot() != slot) continue;
            int step = n.step() * (e.isShiftClick() ? 5 : 1) * (e.isRightClick() ? -1 : 1);
            s.set(n.path(), Math.max(n.min(), Math.min(n.max(), s.getInt(n.path()) + step)));
        }
        for (Tog t : TOGS) if (t.slot() == slot) s.toggle(t.path());
        Fx.sound(p, "ui.button.click", 0.6f, 0.8f);
        render(e.getClickedInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
