package de.zombiegame;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class GameManager {
    public enum State { IDLE, COUNTDOWN, PREP, INFECTING, GRACE, HUNT, ENDED }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final ZombiePlugin plugin;
    private final Features features;
    private final Random rnd = new Random();

    private State state = State.IDLE;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> zombies = new HashSet<>();
    private final Set<UUID> pending = new HashSet<>();      // gestorben -> wird beim Respawn infiziert
    private final Set<UUID> converting = new HashSet<>();   // gerade in der Verwandlungs-Animation
    private final List<UUID> chosen = new ArrayList<>();    // heimlich ausgeloste Start-Zombies
    private final Map<UUID, PlayerProfile> originals = new HashMap<>();
    private final Map<UUID, Double> extraHp = new HashMap<>();
    private final Map<UUID, Long> attackWarn = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    private World world;
    private Location center;
    private double origSize;
    private Location origCenter;
    private long origTime;
    private boolean origStorm, origThunder;
    private int remaining, total;
    private BossBar mainBar, zombieBar;
    private Team hideTeam;
    private boolean weatherTouched;

    public GameManager(ZombiePlugin plugin) {
        this.plugin = plugin;
        this.features = new Features(plugin, this);
    }

    private Settings cfg() { return plugin.settings(); }

    // ------------------------------------------------------------------ Abfragen

    public State state() { return state; }
    public boolean isActive() { return state != State.IDLE; }
    public World world() { return world; }
    public Location center() { return center; }
    public boolean isParticipant(Player p) { return participants.contains(p.getUniqueId()); }
    public boolean isZombie(Player p) { return zombies.contains(p.getUniqueId()); }
    public boolean isConverting(Player p) { return converting.contains(p.getUniqueId()); }
    public int participantCount() { return participants.size(); }

    public List<Player> online() {
        List<Player> l = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) l.add(p);
        }
        return l;
    }

    public List<Player> survivors() {
        List<Player> l = new ArrayList<>();
        for (Player p : online()) if (!zombies.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) l.add(p);
        return l;
    }

    public List<Player> zombiePlayers() {
        List<Player> l = new ArrayList<>();
        for (Player p : online()) if (zombies.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) l.add(p);
        return l;
    }

    /** Cooldown, damit der "Angriff gesperrt"-Hinweis nicht spammt. */
    public boolean shouldWarn(Player p) {
        long now = System.currentTimeMillis();
        long last = attackWarn.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 1500) return false;
        attackWarn.put(p.getUniqueId(), now);
        return true;
    }

    private void later(long ticks, Runnable r) { tasks.add(Bukkit.getScheduler().runTaskLater(plugin, r, ticks)); }

    private void cancelTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    private void broadcast(String msg) {
        for (Player p : online()) Fx.msg(p, msg);
    }

    // ------------------------------------------------------------------ Start / Stop

    public void start(CommandSender sender) {
        if (state != State.IDLE) {
            Fx.msg(sender, "<red>Es läuft bereits eine Runde. <gray>(/zombie stop)");
            return;
        }
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR) players.add(p);
        if (players.size() < 2) {
            Fx.msg(sender, "<red>Es werden mindestens 2 Spieler benötigt.");
            return;
        }
        if (!cfg().hasSkin()) Fx.msg(sender, "<yellow>Achtung: Kein Zombie-Skin hinterlegt (config.yml → skin).");

        Location base = sender instanceof Player sp ? sp.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
        world = base.getWorld();
        center = base.clone();
        origTime = world.getTime();
        origStorm = world.hasStorm();
        origThunder = world.isThundering();

        // Start-Border
        WorldBorder wb = world.getWorldBorder();
        origSize = wb.getSize();
        origCenter = wb.getCenter();
        int lobbyRadius = cfg().getInt("game.lobby-border-radius");
        wb.setCenter(center);
        wb.setSize(lobbyRadius * 2.0);

        participants.clear(); zombies.clear(); pending.clear(); converting.clear(); chosen.clear();
        for (Player p : players) participants.add(p.getUniqueId());

        // Zombies heimlich auslosen (erst bei der Enthuellung aktiv)
        Collections.shuffle(players, rnd);
        int zc = Math.max(1, Math.min(cfg().getInt("game.zombies"), players.size() - 1));
        for (int i = 0; i < zc; i++) chosen.add(players.get(i).getUniqueId());

        mainBar = BossBar.bossBar(Fx.c("<dark_red>☣ <red><bold>ZOMBIE-EVENT <dark_red>☣"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        zombieBar = BossBar.bossBar(Fx.c("<dark_red>⚔ <red><bold>ANGRIFF GESPERRT <dark_red>⚔"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);

        for (Player p : players) {
            double ang = rnd.nextDouble() * Math.PI * 2, r = 3 + rnd.nextDouble() * Math.min(12, lobbyRadius - 5);
            int x = center.getBlockX() + (int) (Math.cos(ang) * r), z = center.getBlockZ() + (int) (Math.sin(ang) * r);
            int y = world.getHighestBlockYAt(x, z) + 1;
            p.teleportAsync(new Location(world, x + 0.5, y, z + 0.5, p.getLocation().getYaw(), 0));
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setSaturation(20);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            p.showBossBar(mainBar);
            Fx.title(p, "<dark_red><bold>☠ ZOMBIE-EVENT ☠", "<red>Einer von euch ist bereits infiziert...", 10, 50, 10);
            Fx.sound(p, "entity.zombie.ambient", 1f, 0.5f);
        }

        state = State.COUNTDOWN;
        remaining = cfg().getInt("game.lobby-countdown");
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L));
        Fx.msg(sender, "<green>Runde gestartet mit <white>" + players.size() + " <green>Spielern.");
    }

    public void stop(CommandSender sender) {
        if (state == State.IDLE) {
            Fx.msg(sender, "<gray>Es läuft keine Runde.");
            return;
        }
        cleanup();
        Fx.msg(sender, "<yellow>Runde abgebrochen und alles zurückgesetzt.");
    }

    public void shutdown() {
        if (state != State.IDLE) cleanup();
    }

    // ------------------------------------------------------------------ Haupt-Tick (1x pro Sekunde)

    private void tick() {
        switch (state) {
            case COUNTDOWN -> tickCountdown();
            case PREP -> tickPrep();
            case GRACE, HUNT -> tickPhase();
            default -> { }
        }
    }

    private void tickCountdown() {
        if (remaining > 0) {
            mainBar.name(Fx.c("<dark_red>☣ <red><bold>BORDER FÄLLT IN <white><bold>" + remaining + " <dark_red>☣"));
            for (Player p : online()) {
                Fx.title(p, "<dark_red><bold>" + remaining, "<red>Die Grenze fällt...", 0, 16, 4);
                Fx.sound(p, "block.note_block.bass", 1f, 0.5f + (5 - Math.min(5, remaining)) * 0.1f);
                Fx.sound(p, "entity.zombie.ambient", 0.6f, 0.5f);
            }
            remaining--;
        } else {
            releaseBorder();
            beginPrep();
        }
    }

    private void releaseBorder() {
        int gr = cfg().getInt("game.game-border-radius");
        double target = gr > 0 ? gr * 2.0 : 59999968.0;
        world.getWorldBorder().setSize(target, gr > 0 ? 6L : 20L);
        for (Player p : online()) {
            Fx.title(p, "<dark_red><bold>☠ ÜBERLEBT ☠", "<red>Die Grenze ist gefallen. Verteilt euch.", 0, 50, 15);
            Fx.sound(p, "entity.ender_dragon.growl", 1f, 0.7f);
            Fx.sound(p, "entity.lightning_bolt.thunder", 1f, 0.8f);
        }
        world.strikeLightningEffect(center);
    }

    private void beginPrep() {
        state = State.PREP;
        total = remaining = cfg().getInt("game.prep-time-seconds");
        broadcast("<red>Der Timer läuft. <gray>Verteilt euch – wenn er abläuft, bricht die Seuche aus.");
    }

    private void tickPrep() {
        if (remaining <= 0) { beginInfection(); return; }
        mainBar.progress(Math.max(0f, Math.min(1f, (float) remaining / total)));
        mainBar.name(Fx.c("<dark_red>☣ <red><bold>INFEKTION IN <white><bold>" + Fx.fmt(remaining) + " <dark_red>☣"));
        mainBar.color(remaining <= 10 && remaining % 2 == 0 ? BossBar.Color.WHITE : BossBar.Color.RED);
        if (remaining <= 10) {
            for (Player p : online()) {
                p.sendActionBar(Fx.c("<dark_red><bold>☠ <red><bold>" + remaining + " <dark_red><bold>☠"));
                Fx.sound(p, "block.note_block.bass", 1.2f, 0.5f + (10 - remaining) * 0.05f);
                if (remaining <= 5) Fx.sound(p, "entity.warden.heartbeat", 1.4f, 0.9f);
            }
        }
        remaining--;
    }

    private void tickPhase() {
        if (remaining <= 0) {
            if (state == State.GRACE) beginHunt(); else end(false);
            return;
        }
        float prog = Math.max(0f, Math.min(1f, (float) remaining / total));
        if (state == State.GRACE) {
            mainBar.progress(prog);
            mainBar.name(Fx.c("<yellow>⏳ <gold><bold>SCHUTZPHASE <gray>- Zombies noch geschwächt <white><bold>" + Fx.fmt(remaining)));
            zombieBar.progress(prog);
            zombieBar.name(Fx.c("<dark_red>⚔ <red><bold>ANGRIFF GESPERRT <white><bold>" + Fx.fmt(remaining) + " <dark_red>⚔"));
        } else {
            mainBar.progress(prog);
            mainBar.color(remaining <= 30 && remaining % 2 == 0 ? BossBar.Color.WHITE : BossBar.Color.RED);
            mainBar.name(Fx.c("<dark_red>☠ <red><bold>RUNDE ENDET IN <white><bold>" + Fx.fmt(remaining)
                    + " <dark_gray>| <green>Überlebende: <white>" + survivors().size() + " <dark_red>☠"));
        }
        remaining--;
    }

    // ------------------------------------------------------------------ Infektion (Start)

    private void beginInfection() {
        state = State.INFECTING;
        int blind = cfg().getInt("game.blind-seconds") * 20;
        for (Player p : online()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blind, 0, false, false, false));
            Fx.sound(p, "entity.warden.heartbeat", 1.6f, 0.7f);
            Fx.sound(p, "ambient.cave", 1.2f, 0.6f);
        }
        hideNames();
        mainBar.name(Fx.c("<dark_red>☣ <red><bold>DIE SEUCHE BRICHT AUS <dark_red>☣"));
        mainBar.progress(1f);

        later(40L, () -> {                                   // unter Schwarzbild: Skin-Wechsel
            for (UUID id : chosen) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && participants.contains(id)) infectInitial(p);
            }
        });
        later(60L, this::reveal);
        later(blind, this::beginGrace);
    }

    private void infectInitial(Player p) {
        zombies.add(p.getUniqueId());
        applySkin(p);
        giveZombieEffects(p);
    }

    private void reveal() {
        for (Player p : online()) {
            if (isZombie(p)) {
                Animations.glitch(plugin, p, "☠ INFIZIERT ☠", "<dark_red>", "<red>Du bist der Zombie <dark_gray>• <gray>Niemand kennt dich. Jage sie.", 80);
                Fx.sound(p, "entity.zombie_villager.converted", 1.2f, 0.6f);
            } else {
                Animations.glitch(plugin, p, "✔ NICHT INFIZIERT ✔", "<green>", "<dark_green>Überlebe <dark_gray>• <gray>Vertraue niemandem.", 80);
                Fx.sound(p, "block.beacon.activate", 1f, 0.6f);
            }
        }
    }

    private void beginGrace() {
        state = State.GRACE;
        total = remaining = cfg().getInt("game.grace-seconds");
        refreshAudiences();
        for (Player p : zombiePlayers())
            Fx.msg(p, "<red>Du kannst die ersten <white>" + Fx.fmt(remaining) + " <red>Minuten noch nicht angreifen. <gray>Dein Kompass zeigt dir die Beute.");
        for (Player p : survivors()) Fx.msg(p, "<gold>Schutzphase: <gray>Die Zombies sind noch geschwächt – nutzt die Zeit!");
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::hud, 0L, 5L));
        if (remaining <= 0) beginHunt();
    }

    private void beginHunt() {
        state = State.HUNT;
        total = remaining = cfg().getInt("game.round-seconds");
        refreshAudiences();
        for (Player p : online()) {
            Fx.title(p, "<dark_red><bold>☠ DIE JAGD BEGINNT ☠", "<red>Die Zombies dürfen jetzt angreifen", 5, 60, 20);
            Fx.sound(p, "entity.ender_dragon.growl", 1.2f, 0.6f);
            Fx.sound(p, "entity.lightning_bolt.thunder", 1.2f, 0.7f);
        }
        world.strikeLightningEffect(center);
        features.onHuntStart();
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, features::tick, 10L, 10L));
    }

    private void refreshAudiences() {
        for (Player p : online()) {
            p.hideBossBar(mainBar);
            p.hideBossBar(zombieBar);
            if (state == State.GRACE && isZombie(p)) p.showBossBar(zombieBar); else p.showBossBar(mainBar);
        }
    }

    // ------------------------------------------------------------------ Zombie-Kompass

    private void hud() {
        if (state != State.GRACE && state != State.HUNT) return;
        List<Player> surv = survivors();
        for (Player z : zombiePlayers()) {
            Player target = null;
            double best = Double.MAX_VALUE;
            for (Player s : surv) {
                if (!s.getWorld().equals(z.getWorld())) continue;
                double d = s.getLocation().distanceSquared(z.getLocation());
                if (d < best) { best = d; target = s; }
            }
            if (target == null) {
                z.sendActionBar(Fx.c("<dark_red>☣ <gray>Keine Witterung..."));
                continue;
            }
            Location a = z.getLocation(), b = target.getLocation();
            double yawT = Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
            double rel = ((yawT - a.getYaw()) % 360 + 360) % 360;
            int idx = (int) Math.round(rel / 45.0) % 8;
            int dist = (int) Math.sqrt(best);
            double dy = b.getY() - a.getY();
            String vert = dy > 6 ? " <gray>▲" : (dy < -6 ? " <gray>▼" : "");
            z.sendActionBar(Fx.c("<dark_red>☣ <red>WITTERUNG <dark_gray>» <red><bold>" + ARROWS[idx] + " <white>" + dist + "m" + vert
                    + " <dark_gray>« <dark_red>☣ <gray>(" + surv.size() + " Überlebende)"));
        }
    }

    // ------------------------------------------------------------------ Infektion durch Kill (episch)

    public void startInfection(Player victim, Player killer) {
        UUID id = victim.getUniqueId();
        if (!participants.contains(id) || zombies.contains(id) || !converting.add(id)) return;
        pending.remove(id);
        removeExtra(victim);
        victim.setHealth(maxHealth(victim));
        victim.setFoodLevel(20);
        for (Player p : online()) {
            Fx.sound(p, "entity.wither.spawn", 0.5f, 1.8f);
            p.sendActionBar(Fx.c("<dark_red>☠ <red><bold>Ein Überlebender wird infiziert... <dark_red>☠"));
        }
        Animations.infection(plugin, victim, () -> {
            converting.remove(id);
            zombies.add(id);
            applySkin(victim);
            giveZombieEffects(victim);
            if (killer != null && killer.isOnline() && cfg().bool("features.blood-rush")) features.bloodRush(killer);
            refreshAudiences();
        }, () -> {
            Animations.glitch(plugin, victim, "☠ INFIZIERT ☠", "<dark_red>", "<red>Du gehörst jetzt zur Horde <dark_gray>• <gray>Jage die Überlebenden", 70);
            broadcast("<dark_red>☠ <red>Ein weiterer Überlebender wurde infiziert! <gray>Noch <white>" + survivors().size() + " <gray>Überlebende.");
            checkWin();
        });
    }

    /** Ein Ueberlebender hat einen Zombie getoetet -> permanente Extra-Herzen. */
    public void onZombieKilled(Player killer) {
        if ((state != State.GRACE && state != State.HUNT) || !participants.contains(killer.getUniqueId()) || isZombie(killer)) return;
        int hearts = cfg().getInt("game.extra-hearts-per-kill");
        if (hearts <= 0) return;
        if (addExtraHealth(killer, hearts)) Animations.lifeGain(plugin, killer, hearts);
        else Fx.msg(killer, "<gold>Du hast das Maximum an Extra-Leben erreicht.");
    }

    public void markPending(Player p) { if (!isZombie(p)) pending.add(p.getUniqueId()); }

    /** 1 Tick nach dem Respawn aufgerufen. */
    public void onRespawn(Player p) {
        if (!isParticipant(p) || (state != State.GRACE && state != State.HUNT)) return;
        UUID id = p.getUniqueId();
        if (pending.contains(id)) {
            startInfection(p, null);
        } else if (zombies.contains(id)) {
            int secs = cfg().getInt("game.zombie-respawn-seconds");
            p.setGameMode(GameMode.SPECTATOR);
            Fx.title(p, "<dark_red><bold>☠ ERLEGT ☠", "<gray>Du erwachst in <red>" + secs + "s <gray>wieder...", 5, secs * 20, 10);
            later(secs * 20L, () -> {
                if (!p.isOnline() || !zombies.contains(id) || (state != State.GRACE && state != State.HUNT)) return;
                p.setGameMode(GameMode.SURVIVAL);
                p.teleportAsync(randomSpot(Math.max(30, Math.min(100, cfg().getInt("game.game-border-radius") > 0 ? cfg().getInt("game.game-border-radius") : 100))));
                giveZombieEffects(p);
                Fx.sound(p, "entity.zombie_villager.converted", 1f, 0.6f);
            });
        }
    }

    public Location respawnLocation() { return center == null ? null : randomSpot(10); }

    private Location randomSpot(int radius) {
        double ang = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * radius;
        int x = center.getBlockX() + (int) (Math.cos(ang) * r), z = center.getBlockZ() + (int) (Math.sin(ang) * r);
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    private void giveZombieEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    // ------------------------------------------------------------------ Extra-Leben

    private double maxHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20.0 : a.getValue();
    }

    private boolean addExtraHealth(Player p, int hearts) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return false;
        UUID id = p.getUniqueId();
        double cur = extraHp.getOrDefault(id, 0.0);
        double cap = cfg().getInt("game.max-extra-hearts") * 2.0;
        double add = Math.min(hearts * 2.0, cap - cur);
        if (add <= 0) return false;
        a.setBaseValue(a.getBaseValue() + add);
        extraHp.put(id, cur + add);
        p.setHealth(a.getValue());
        return true;
    }

    private void removeExtra(Player p) {
        Double e = extraHp.remove(p.getUniqueId());
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (e == null || a == null) return;
        a.setBaseValue(Math.max(2.0, a.getBaseValue() - e));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    // ------------------------------------------------------------------ Skin + Namen

    private void applySkin(Player p) {
        if (!cfg().hasSkin()) return;
        originals.putIfAbsent(p.getUniqueId(), p.getPlayerProfile());
        PlayerProfile prof = Bukkit.createProfile(p.getUniqueId(), p.getName());
        prof.setProperty(new ProfileProperty("textures", cfg().str("skin.value"), cfg().str("skin.signature")));
        p.setPlayerProfile(prof);
    }

    private void restoreSkin(Player p) {
        PlayerProfile o = originals.remove(p.getUniqueId());
        if (o != null) p.setPlayerProfile(o);
    }

    private void hideNames() {
        if (cfg().bool("display.hide-names")) {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team old = sb.getTeam("zi_hide");
            if (old != null) old.unregister();
            hideTeam = sb.registerNewTeam("zi_hide");
            hideTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            hideTeam.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, Team.OptionStatus.NEVER);
            for (Player p : online()) hideTeam.addEntry(p.getName());
        }
        if (cfg().bool("display.hide-tablist")) {
            List<Player> ps = online();
            for (Player a : ps) for (Player b : ps) if (a != b) a.unlistPlayer(b);
        }
    }

    private void revealNames() {
        if (hideTeam != null) {
            try { hideTeam.unregister(); } catch (IllegalStateException ignored) { }
            hideTeam = null;
        }
        List<Player> ps = online();
        for (Player a : ps) for (Player b : ps) if (a != b) a.listPlayer(b);
    }

    /** Alle heilen: Skins zurueck, Zombie-Effekte weg, Namen sichtbar (Ueberlebenden-Sieg). */
    public void revealAll() {
        for (Player p : online()) {
            restoreSkin(p);
            p.removePotionEffect(PotionEffectType.STRENGTH);
            p.removePotionEffect(PotionEffectType.SPEED);
        }
        zombies.clear();
        revealNames();
    }

    // ------------------------------------------------------------------ Ende

    public void checkWin() {
        if (state != State.GRACE && state != State.HUNT) return;
        if (survivors().isEmpty() && converting.isEmpty()) end(true);
    }

    public void end(boolean zombiesWin) {
        if (state == State.IDLE || state == State.ENDED) return;
        state = State.ENDED;
        weatherTouched = true;
        cancelTasks();
        List<Player> ps = online();
        for (Player p : ps) {
            p.hideBossBar(mainBar);
            p.hideBossBar(zombieBar);
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : zombies) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            if (names.length() > 0) names.append("<dark_gray>, ");
            names.append("<white>").append(op.getName() == null ? "?" : op.getName());
        }
        for (Player p : ps) Fx.msg(p, "<gray>Die Zombies waren: " + names);

        long delay;
        if (zombiesWin) {
            Animations.zombieTakeover(plugin, this, ps);
            delay = 20L * 17;
        } else {
            Animations.survivorVictory(plugin, this, ps);
            delay = 20L * 22;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, delay);
    }

    private void cleanup() {
        cancelTasks();
        boolean night = features.nightActive();
        features.cleanup();
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            restoreSkin(p);
            removeExtra(p);
            for (PotionEffectType t : new PotionEffectType[]{PotionEffectType.STRENGTH, PotionEffectType.SPEED, PotionEffectType.GLOWING,
                    PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS, PotionEffectType.LEVITATION, PotionEffectType.SLOWNESS})
                p.removePotionEffect(t);
            p.setInvulnerable(false);
            p.setWorldBorder(null);
            if (mainBar != null) p.hideBossBar(mainBar);
            if (zombieBar != null) p.hideBossBar(zombieBar);
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
        revealNames();
        if (world != null) {
            WorldBorder wb = world.getWorldBorder();
            wb.setCenter(origCenter);
            wb.setSize(origSize);
            if (night || weatherTouched) {
                world.setTime(origTime);
                world.setStorm(origStorm);
                world.setThundering(origThunder);
            }
        }
        participants.clear(); zombies.clear(); pending.clear(); converting.clear(); chosen.clear();
        originals.clear(); extraHp.clear(); attackWarn.clear();
        weatherTouched = false;
        state = State.IDLE;
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        if (!participants.contains(id)) return;
        restoreSkin(p);
        removeExtra(p);
        p.setInvulnerable(false);
        p.setWorldBorder(null);
        participants.remove(id); zombies.remove(id); pending.remove(id); converting.remove(id); chosen.remove(id);
        if (state == State.GRACE || state == State.HUNT) {
            if (zombies.isEmpty()) end(false); else checkWin();
        }
    }
}
