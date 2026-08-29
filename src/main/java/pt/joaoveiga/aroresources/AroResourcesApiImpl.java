package pt.joaoveiga.aroresources;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import pt.joaoveiga.aroresources.api.AroResourcesApi;
import pt.joaoveiga.aroresources.managers.ModelRegistry;
import pt.joaoveiga.aroresources.managers.ResourcePackManager;

public class AroResourcesApiImpl implements AroResourcesApi {

    private final AroResources plugin;
    private final ModelRegistry modelRegistry;
    private final ResourcePackManager resourcePackManager;

    public AroResourcesApiImpl(AroResources plugin, ModelRegistry modelRegistry, ResourcePackManager resourcePackManager) {
        this.plugin = plugin;
        this.modelRegistry = modelRegistry;
        this.resourcePackManager = resourcePackManager;
    }

    @Override
    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    @Override
    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    @Override
    public ItemStack createItem(String category, String id, int amount) {
        return modelRegistry.createItem(category, id, amount);
    }

    @Override
    public ItemStack createItem(Material material, String modelPath, int amount) {
        return modelRegistry.createItem(material, modelPath, amount);
    }

    @Override
    public Integer getModelData(String category, String id) {
        return modelRegistry.getModelData(category, id);
    }

    @Override
    public String getSkullId(String path, String fallback) {
        return modelRegistry.getSkullId(path, fallback);
    }

    @Override
    public void reload() {
        plugin.reloadAll();
    }
}
