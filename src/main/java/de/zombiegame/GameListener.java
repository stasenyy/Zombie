package de.zombiegame;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class GameListener implements Listener {
    private final ZombiePlugin plugin;

    public GameListener(ZombiePlugin plugin) { this.plugin = plugin; }

    private GameManager game() { return plugin.game(); }

    private Player shooter(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player sp) return sp;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !game().isActive() || !game().isParticipant(victim)) return;
        // waehrend Blind-Phase / Verwandlung unverwundbar
        if (game().state() == GameManager.State.INFECTING || game().isConverting(victim)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !game().isActive()) return;
        Player attacker = shooter(e.getDamager());
        if (attacker == null || !game().isParticipant(victim) || !game().isParticipant(attacker)) return;

        GameManager.State s = game().state();
        if (s != GameManager.State.GRACE && s != GameManager.State.HUNT) { e.setCancelled(true); return; }

        boolean aZ = game().isZombie(attacker), vZ = game().isZombie(victim);
        if (aZ && vZ) { e.setCancelled(true); return; }
        if (aZ && s == GameManager.State.GRACE) {
            e.setCancelled(true);
            if (game().shouldWarn(attacker)) {
                attacker.sendActionBar(Fx.c("<dark_red>⚔ <red><bold>Du kannst noch nicht angreifen! <dark_red>⚔"));
                Fx.sound(attacker, "entity.zombie.attack_iron_door", 0.8f, 0.6f);
            }
            return;
        }
        if (aZ && s == GameManager.State.HUNT && !vZ) {
            // toedlicher Treffer -> nicht sterben, sondern epische Verwandlung
            if (e.getFinalDamage() >= victim.getHealth() + victim.getAbsorptionAmount()) {
                e.setCancelled(true);
                game().startInfection(victim, attacker);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        if (!game().isActive() || !game().isParticipant(dead)) return;
        e.deathMessage(null);
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (game().isZombie(dead)) {
            Player killer = dead.getKiller();
            if (killer != null && game().isParticipant(killer)) game().onZombieKilled(killer);
        } else {
            game().markPending(dead);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!game().isActive() || !game().isParticipant(p)) return;
        if (game().respawnLocation() != null) e.setRespawnLocation(game().respawnLocation());
        Bukkit.getScheduler().runTaskLater(plugin, () -> game().onRespawn(p), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (game().isActive()) game().handleQuit(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (game().isActive() && plugin.settings().bool("display.disable-chat") && game().isParticipant(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Fx.c("<dark_red>✖ <gray>Der Chat ist während der Runde stumm."));
        }
    }
}
