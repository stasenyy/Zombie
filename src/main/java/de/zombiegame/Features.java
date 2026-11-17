package de.zombiegame;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Die 7 Zusatz-Features (alle im /zombie-Menue schaltbar). Tick = alle 0,5 Sekunden, nur in der Jagd-Phase. */
public final class Features {
    private final ZombiePlugin plugin;
    private final GameManager game;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> nextGroan = new HashMap<>();
    private final List<Location> chests = new ArrayList<>();
    private int t, nextFog, nextBolt, nextDrop;
    private boolean lastTriggered, nightActive;

    public Features(ZombiePlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    private boolean on(String key) { return plugin.settings().bool("features." + key); }

    public boolean nightActive() { return nightActive; }

    void onHuntStart() {
        t = 0; nextFog = 120; nextBolt = 20; nextDrop = 60;
        lastTriggered = false;
        nextGroan.clear();
        if (on("blood-night")) {                                        // 2 Blutnacht
            nightActive = true;
            World w = game.world();
            w.setTime(18000);
            w.setStorm(true);
            w.setThundering(true);
            w.setWeatherDuration(72000);
            w.setThunderDuration(72000);
        }
    }

    void tick() {
        t++;
        heartbeat();
        groans();
        fog();
        bolts();
        lastSurvivor();
        supplyDrop();
        chestBeams();
    }

    // 1 Herzschlag: je naeher ein Zombie, desto schneller
    private void heartbeat() {
        if (!on("heartbeat")) return;
        List<Player> zs = game.zombiePlayers();
        if (zs.isEmpty()) return;
        for (Player p : game.survivors()) {
            double best = Double.MAX_VALUE;
            for (Player z : zs) {
                if (!z.getWorld().equals(p.getWorld())) continue;
                best = Math.min(best, z.getLocation().distance(p.getLocation()));
            }
            if (best > 25) continue;
            int period = best < 8 ? 1 : best < 15 ? 2 : 3;
            if (t % period != 0) continue;
            Fx.sound(p, "entity.warden.heartbeat", 1.2f, (float) (0.7 + (1 - best / 25.0) * 0.6));
            if (best < 8) p.sendActionBar(Fx.c("<dark_red>♥ <red><bold>Etwas ist ganz nah... <dark_red>♥"));
        }
    }

    // 5 Zombie-Gebruell: Geraeusche in der Naehe verraten Zombies
    private void groans() {
        if (!on("groans")) return;
        for (Player z : game.zombiePlayers()) {
            int due = nextGroan.computeIfAbsent(z.getUniqueId(), k -> t + 10 + rnd.nextInt(20));
            if (t >= due) {
                z.getWorld().playSound(z.getLocation(), "entity.zombie.ambient", SoundCategory.HOSTILE, 1.6f, 0.55f);
                nextGroan.put(z.getUniqueId(), t + 16 + rnd.nextInt(16));
            }
        }
    }

    // 3 Nebel des Grauens: Dunkelheits-Pulse fuer Ueberlebende
    private void fog() {
        if (!on("fog") || t < nextFog) return;
        nextFog = t + 180;
        for (Player p : game.survivors()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0, false, false, false));
            Fx.sound(p, "ambient.cave", 1.2f, 0.5f);
            p.sendActionBar(Fx.c("<dark_gray>☁ <gray>Der Nebel des Grauens zieht auf... <dark_gray>☁"));
        }
    }

    // 2 Blutnacht: Blitze um die Ueberlebenden
    private void bolts() {
        if (!nightActive || t < nextBolt) return;
        nextBolt = t + 30 + rnd.nextInt(40);
        List<Player> s = game.survivors();
        if (s.isEmpty()) return;
        Player p = s.get(rnd.nextInt(s.size()));
        p.getWorld().strikeLightningEffect(p.getLocation().add(rnd.nextInt(71) - 35, 0, rnd.nextInt(71) - 35));
    }

    // 4 Blutrausch: wird von GameManager nach einer Infektion fuer den Killer aufgerufen
    void bloodRush(Player killer) {
        killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
        killer.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 1, false, true, true));
        Fx.title(killer, "", "<dark_red><bold>BLUTRAUSCH", 0, 30, 10);
        Fx.sound(killer, "entity.ravager.roar", 1f, 0.8f);
        killer.getWorld().spawnParticle(Particle.DUST, killer.getLocation().add(0, 1, 0), 50, 0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(Color.RED, 1.8f));
    }

    // 6 Letzter Ueberlebender leuchtet fuer alle
    private void lastSurvivor() {
        if (!on("last-survivor") || lastTriggered || game.participantCount() < 3) return;
        List<Player> s = game.survivors();
        if (s.size() != 1) return;
        lastTriggered = true;
        Player last = s.get(0);
        last.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        last.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        for (Player p : game.online()) {
            if (p.equals(last)) Fx.title(p, "<green><bold>DU BIST DER LETZTE", "<gray>Sie sehen dich jetzt...", 5, 60, 20);
            else Fx.title(p, "<gold><bold>LETZTER ÜBERLEBENDER", "<gray>Er leuchtet. Die Jagd ist eröffnet.", 5, 60, 20);
            Fx.sound(p, "entity.wither.spawn", 0.8f, 1.2f);
        }
    }

    // 7 Nachschub-Kisten fuer die Ueberlebenden
    private void supplyDrop() {
        if (!on("supply-drops") || t < nextDrop) return;
        nextDrop = t + 240;
        World w = game.world();
        Location c = game.center();
        int gr = plugin.settings().getInt("game.game-border-radius");
        double r = (gr > 0 ? Math.min(gr, 250) : 250) * 0.8;
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = c.getBlockX() + (int) ((rnd.nextDouble() * 2 - 1) * r);
            int z = c.getBlockZ() + (int) ((rnd.nextDouble() * 2 - 1) * r);
            Block ground = w.getHighestBlockAt(x, z);
            Block spot = ground.getRelative(BlockFace.UP);
            if (ground.isLiquid() || !spot.getType().isAir()) continue;
            spot.setType(Material.CHEST);
            if (spot.getState() instanceof Chest chest) {
                chest.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.COOKED_BEEF, 8),
                        new ItemStack(Material.ENDER_PEARL, 2), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16),
                        new ItemStack(Material.SHIELD));
            }
            chests.add(spot.getLocation());
            w.strikeLightningEffect(spot.getLocation());
            for (Player p : game.survivors()) {
                p.sendMessage(Fx.c(Fx.PREFIX + "<gold>✦ NACHSCHUB gelandet bei <white>X " + x + " <gray>/ <white>Z " + z));
                Fx.sound(p, "entity.firework_rocket.blast", 1f, 0.8f);
            }
            return;
        }
    }

    private void chestBeams() {
        for (Location l : chests) {
            for (int y = 0; y < 30; y += 2)
                l.getWorld().spawnParticle(Particle.END_ROD, l.clone().add(0.5, y, 0.5), 1, 0, 0, 0, 0);
        }
    }

    void cleanup() {
        for (Location l : chests) {
            Block b = l.getBlock();
            if (b.getState() instanceof Chest ch) {
                ch.getInventory().clear();
                b.setType(Material.AIR);
            }
        }
        chests.clear();
        nextGroan.clear();
        nightActive = false;
    }
}
