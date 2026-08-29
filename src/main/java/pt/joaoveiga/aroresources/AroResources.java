package pt.joaoveiga.aroresources;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import pt.joaoveiga.aroresources.api.AroResourcesApi;
import pt.joaoveiga.aroresources.commands.AroResourcesCommand;
import pt.joaoveiga.aroresources.listeners.ResourcePackListener;
import pt.joaoveiga.aroresources.managers.ModelRegistry;
import pt.joaoveiga.aroresources.managers.ResourcePackManager;

import java.io.File;
import java.io.InputStreamReader;

public class AroResources extends JavaPlugin {

    private static AroResources instance;

    private ModelRegistry modelRegistry;
    private ResourcePackManager resourcePackManager;
    private AroResourcesApi api;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ensureConfigDefaults();
        ensureDataFiles();

        this.modelRegistry = new ModelRegistry(this);
        this.modelRegistry.reload();
        this.resourcePackManager = new ResourcePackManager(this);
        this.resourcePackManager.initialize();
        this.api = new AroResourcesApiImpl(this, modelRegistry, resourcePackManager);

        Bukkit.getServicesManager().register(AroResourcesApi.class, api, this, ServicePriority.Normal);
        registerCommand();
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this), this);

        if (getConfig().getBoolean("resource-pack.apply-on-join", true) && resourcePackManager.isEnabled()) {
            long delayTicks = resourcePackManager.shouldPreloadBeforeJoin()
                    ? 0L
                    : Math.max(1L, getConfig().getLong("resource-pack.join-delay-ticks", 20L));
            if (delayTicks <= 0L) {
                getServer().getOnlinePlayers().forEach(resourcePackManager::applyTo);
            } else {
                getServer().getScheduler().runTaskLater(this, new Runnable() {
                    @Override
                    public void run() {
                        getServer().getOnlinePlayers().forEach(resourcePackManager::applyTo);
                    }
                }, delayTicks);
            }
        }

        getLogger().info("AroResources ativado com sucesso.");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            Bukkit.getServicesManager().unregister(AroResourcesApi.class, api);
        }
        if (resourcePackManager != null) {
            resourcePackManager.shutdown();
        }
    }

    public static AroResources getInstance() {
        return instance;
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    public AroResourcesApi getApi() {
        return api;
    }

    public void reloadAll() {
        reloadConfig();
        ensureConfigDefaults();
        ensureDataFiles();
        if (resourcePackManager != null) {
            resourcePackManager.reload();
        }
        if (modelRegistry != null) {
            modelRegistry.reload();
        }
    }

    private void registerCommand() {
        AroResourcesCommand executor = new AroResourcesCommand(this);
        registerCommand("txtpack", executor, "Comando /txtpack nao encontrado no plugin.yml.");
        registerCommand("aroresources", executor, "Comando /aroresources nao encontrado no plugin.yml.");
    }

    private void registerCommand(String name, AroResourcesCommand executor, String warningMessage) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning(warningMessage);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void ensureDataFiles() {
        File models = new File(getDataFolder(), getConfig().getString("models-file", "models.yml"));
        File skulls = new File(getDataFolder(), getConfig().getString("skulls-file", "skulls.yml"));
        if (!models.exists()) {
            saveResource("models.yml", false);
        }
        if (!skulls.exists()) {
            saveResource("skulls.yml", false);
        }
    }

    private void ensureConfigDefaults() {
        try (InputStreamReader reader = new InputStreamReader(getResource("config.yml"))) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            YamlConfiguration current = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
            boolean changed = false;

            for (String key : defaults.getKeys(true)) {
                if (!current.contains(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }

            if (changed) {
                current.save(new File(getDataFolder(), "config.yml"));
                reloadConfig();
            }
        } catch (Exception exception) {
            getLogger().warning("Falha ao atualizar defaults da config: " + exception.getMessage());
        }
    }
}
