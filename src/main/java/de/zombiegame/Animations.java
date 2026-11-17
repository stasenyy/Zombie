package de.zombiegame;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/** Alle grossen Inszenierungen: Infektion, Extra-Leben, Zombie-Uebernahme, Ueberlebenden-Sieg. */
final class Animations {
    private static final Random RND = new Random();
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(130, 0, 0), 1.6f);
    private static final Particle.DustOptions FOG = new Particle.DustOptions(Color.fromRGB(110, 0, 0), 3.0f);
    private static final String[] SCARY = {
            "entity.zombie.ambient", "entity.enderman.scream", "entity.warden.roar",
            "entity.wither.ambient", "ambient.cave", "entity.ender_dragon.growl", "entity.zombie_villager.ambient"};

    private Animations() {}

    /** Glitch-Titel: erst flackernder Text, dann der echte Titel. */
    static void glitch(ZombiePlugin plugin, Player p, String plain, String color, String sub, int stay) {
        int[] f = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline()) { task.cancel(); return; }
            f[0]++;
            if (f[0] <= 8) {
                String col = f[0] % 2 == 0 ? "<dark_red>" : "<red>";
                Fx.title(p, col + "<bold><obfuscated>" + plain, "", 0, 6, 0);
                Fx.sound(p, "block.note_block.bit", 0.6f, 0.5f + f[0] * 0.1f);
            } else {
                Fx.title(p, color + "<bold>" + plain, sub, 0, stay, 20);
                task.cancel();
            }
        }, 0L, 2L);
    }

    static void firework(Location loc, boolean instant, Color... colors) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(colors).withFade(Color.WHITE).trail(true).flicker(true).build());
            m.setPower(instant ? 0 : 1);
            f.setFireworkMeta(m);
        });
        if (instant) fw.detonate();
    }

    // ------------------------------------------------------------------ Infektion (Opfer)

    /** Epische Verwandlung: Blitz, Levitation, Blut-Spirale, dann (onConvert) Skin-Wechsel + Explosion. */
    static void infection(ZombiePlugin plugin, Player v, Runnable onConvert, Runnable onDone) {
        v.setInvulnerable(true);
        v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 9, false, false, false));
        v.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 90, 0, false, false, false));
        v.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 36, 0, false, false, false));
        v.getWorld().strikeLightningEffect(v.getLocation());
        v.getWorld().playSound(v.getLocation(), "entity.zombie_villager.converted", SoundCategory.HOSTILE, 4f, 0.5f);
        v.getWorld().playSound(v.getLocation(), "entity.wither.spawn", SoundCategory.HOSTILE, 1.2f, 1.6f);

        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!v.isOnline()) { task.cancel(); return; }
            int i = t[0]++;
            World w = v.getWorld();
            Location l = v.getLocation();
            for (int k = 0; k < 3; k++) {
                double a = i * 0.45 + k * (Math.PI * 2 / 3);
                double r = Math.max(0.25, 1.8 - i * 0.03);
                Location pl = l.clone().add(Math.cos(a) * r, 0.1 + (i % 25) * 0.08, Math.sin(a) * r);
                w.spawnParticle(Particle.DUST, pl, 2, 0.05, 0.05, 0.05, 0, BLOOD);
                w.spawnParticle(Particle.SOUL, pl, 1, 0, 0, 0, 0.01);
            }
            if (i % 8 == 0) w.playSound(l, "entity.zombie.ambient", SoundCategory.HOSTILE, 1.5f, 0.5f + i * 0.01f);
            if (i == 24) {
                onConvert.run();
                Location c = l.clone().add(0, 1, 0);
                w.spawnParticle(Particle.LARGE_SMOKE, c, 70, 0.5, 0.8, 0.5, 0.05);
                w.spawnParticle(Particle.DUST, c, 90, 0.6, 0.9, 0.6, 0, BLOOD);
                w.strikeLightningEffect(l);
                w.playSound(l, "entity.zombie.infect", SoundCategory.HOSTILE, 3f, 0.6f);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, false));
            }
            if (i >= 50) {
                task.cancel();
                v.setInvulnerable(false);
                v.removePotionEffect(PotionEffectType.SLOWNESS);
                onDone.run();
            }
        }, 0L, 1L);
    }

    // ------------------------------------------------------------------ Extra-Leben (Killer)

    static void lifeGain(ZombiePlugin plugin, Player p, int hearts) {
        Fx.title(p, "<green><bold>+" + hearts + " ♥ <dark_green>LEBEN", "<gray>Zombie erlegt – deine Lebenskraft wächst für immer", 0, 55, 15);
        Fx.sound(p, "item.totem.use", 1f, 1f);
        Fx.sound(p, "entity.player.levelup", 1f, 0.6f);
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline()) { task.cancel(); return; }
            int i = t[0]++;
            World w = p.getWorld();
            Location l = p.getLocation();
            for (int k = 0; k < 2; k++) {
                double a = i * 0.5 + k * Math.PI;
                w.spawnParticle(Particle.HEART, l.clone().add(Math.cos(a) * 1.2, i * 0.07, Math.sin(a) * 1.2), 1, 0, 0, 0, 0);
            }
            if (i % 4 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, l.clone().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.2);
            if (i == 0 || i == 20) firework(l.clone().add(0, 1, 0), true, Color.LIME, Color.YELLOW);
            if (i >= 40) task.cancel();
        }, 0L, 1L);
    }

    // ------------------------------------------------------------------ Zombies gewinnen

    static void zombieTakeover(ZombiePlugin plugin, GameManager game, List<Player> players) {
        World w = game.world();
        w.setTime(18000);
        w.setStorm(true);
        w.setThundering(true);
        for (Player p : players) {
            p.setInvulnerable(true);
            WorldBorder wb = Bukkit.createWorldBorder();   // nur fuer den roten Rand-Nebel am Bildschirm
            wb.setCenter(p.getLocation());
            wb.setSize(40);
            wb.setWarningDistance(120);
            wb.setDamageAmount(0);
            wb.setDamageBuffer(1000);
            p.setWorldBorder(wb);
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 15, 0, false, false, false));
            Fx.sound(p, "entity.wither.spawn", 1f, 0.5f);
            Fx.sound(p, "entity.warden.roar", 1.2f, 0.6f);
        }
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int i = t[0]++;
            if (i == 10) for (Player p : players)
                if (p.isOnline()) Fx.title(p, "<dark_red><bold>☠ DIE SEUCHE HAT GESIEGT ☠", "<red>Es gibt keine Überlebenden mehr...", 10, 80, 10);
            if (i == 110) for (Player p : players)
                if (p.isOnline()) glitch(plugin, p, "DIESER SERVER WURDE", "<dark_red>", "<red><bold>VON EUCH ÜBERNOMMEN", 130);
            if (i % 5 == 0) for (Player p : players) {
                if (!p.isOnline()) continue;
                Location c = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.DUST, c, 35, 6, 2.5, 6, 0, FOG);   // roter Nebel
                p.getWorld().spawnParticle(Particle.SOUL, c, 4, 4, 2, 4, 0.02);
            }
            if (i % 10 == 0) for (Player p : players) if (p.isOnline()) Fx.sound(p, "entity.warden.heartbeat", 1.5f, 0.8f);
            if (i % 20 == 0 && i > 0 && !players.isEmpty()) {
                Player rp = players.get(RND.nextInt(players.size()));
                if (rp.isOnline()) {
                    w.strikeLightningEffect(rp.getLocation().add(RND.nextInt(25) - 12, 0, RND.nextInt(25) - 12));
                    for (Player p : players) if (p.isOnline())
                        Fx.sound(p, SCARY[RND.nextInt(SCARY.length)], 1.2f, 0.5f + RND.nextFloat() * 0.4f);
                }
            }
            if (i >= 300) {
                task.cancel();
                for (Player p : players) if (p.isOnline()) {
                    p.setInvulnerable(false);
                    p.setWorldBorder(null);
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                }
            }
        }, 0L, 1L);
    }

    // ------------------------------------------------------------------ Ueberlebende gewinnen

    static void survivorVictory(ZombiePlugin plugin, GameManager game, List<Player> players) {
        game.revealAll(); // alle geheilt: Skins zurueck, Namen wieder da
        World w = game.world();
        w.setStorm(false);
        w.setThundering(false);
        w.setTime(23000); // Sonnenaufgang
        Location c = game.center().clone();
        c.setY(w.getHighestBlockYAt(c.getBlockX(), c.getBlockZ()) + 1);
        int n = Math.max(1, players.size());
        double radius = 3.5 + n * 0.25;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            double a = Math.PI * 2 * i / n;
            Location dest = c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            double dx = c.getX() - dest.getX(), dz = c.getZ() - dest.getZ();
            dest.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
            dest.setPitch(-15f);
            p.teleportAsync(dest);
            p.setInvulnerable(true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0, false, false, false));
        }
        Color[] pal = {Color.LIME, Color.YELLOW, Color.AQUA, Color.ORANGE, Color.FUCHSIA, Color.WHITE};
        int[] t = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int i = t[0]++;
            if (i == 0) for (Player p : players) {
                Fx.title(p, "<green><bold>DIE SEUCHE IST BESIEGT", "<gray>Alle werden zusammengerufen...", 10, 70, 10);
                Fx.sound(p, "block.end_portal.spawn", 0.6f, 1.4f);
            }
            if (i == 40) for (Player p : players) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 70, 1, false, false, false));
                Fx.sound(p, "entity.firework_rocket.launch", 1f, 1f);
            }
            if (i == 100) for (Player p : players) {
                Fx.title(p, "<gold><bold>IHR HABT MINECRAFTS", "<yellow><bold>SCHLIMMSTE APOKALYPSE ÜBERLEBT", 10, 150, 30);
                Fx.sound(p, "ui.toast.challenge_complete", 1f, 1f);
                Fx.sound(p, "item.totem.use", 1f, 1f);
            }
            if (i == 110) for (Player p : players)
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false, false));

            // rotierender Lichtring + Lichtsaeulen ueber jedem Spieler
            double rot = i * 0.12;
            for (int k = 0; k < 24; k++) {
                double a = rot + k * Math.PI * 2 / 24;
                w.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(a) * radius, 0.4 + Math.sin(i * 0.1 + k) * 0.3, Math.sin(a) * radius), 1, 0, 0, 0, 0);
            }
            for (Player p : players) {
                if (!p.isOnline()) continue;
                Location pl = p.getLocation();
                for (int y = 0; y <= 10; y += 2) w.spawnParticle(Particle.END_ROD, pl.clone().add(0, y, 0), 1, 0.1, 0, 0.1, 0.01);
                if (i >= 40 && i % 10 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, pl.clone().add(0, 1, 0), 8, 0.4, 0.8, 0.4, 0.25);
            }
            int every = i >= 100 ? 3 : 6;
            if (i >= 40 && i % every == 0 && !players.isEmpty()) {
                Player p = players.get(RND.nextInt(players.size()));
                if (p.isOnline())
                    firework(p.getLocation().add(RND.nextInt(7) - 3, 2, RND.nextInt(7) - 3), false,
                            pal[RND.nextInt(pal.length)], pal[RND.nextInt(pal.length)]);
            }
            if (i >= 420) {
                task.cancel();
                for (Player p : players) if (p.isOnline()) {
                    p.setInvulnerable(false);
                    p.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }, 0L, 1L);
    }
}
