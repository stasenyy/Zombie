package de.zombiegame;

public final class Settings {
    private final ZombiePlugin plugin;

    public Settings(ZombiePlugin plugin) { this.plugin = plugin; }

    public int getInt(String path) { return plugin.getConfig().getInt(path); }
    public boolean bool(String path) { return plugin.getConfig().getBoolean(path); }
    public String str(String path) { return plugin.getConfig().getString(path, ""); }

    public void set(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    public boolean toggle(String path) {
        boolean n = !bool(path);
        set(path, n);
        return n;
    }

    public boolean hasSkin() { return !str("skin.value").isBlank() && !str("skin.signature").isBlank(); }
}
