package de.zombiegame;

import org.bukkit.plugin.java.JavaPlugin;

public final class ZombiePlugin extends JavaPlugin {
    private Settings settings;
    private GameManager game;
    private ZombieMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("zombie_skin.png", false); // liegt dann in plugins/ZombieInfection/ zum Hochladen auf mineskin.org
        settings = new Settings(this);
        game = new GameManager(this);
        menu = new ZombieMenu(this);

        ZombieCommand cmd = new ZombieCommand(this);
        getCommand("zombie").setExecutor(cmd);
        getCommand("zombie").setTabCompleter(cmd);
        getCommand("start").setExecutor(cmd);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(menu, this);

        if (!settings.hasSkin()) {
            getLogger().warning("Kein Zombie-Skin hinterlegt! Siehe README / config.yml (skin.value + skin.signature).");
        }
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
    }

    public Settings settings() { return settings; }
    public GameManager game() { return game; }
    public ZombieMenu menu() { return menu; }
}
