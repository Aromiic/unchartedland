package pt.joaoveiga.aroresources.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import pt.joaoveiga.aroresources.managers.ModelRegistry;
import pt.joaoveiga.aroresources.managers.ResourcePackManager;

public interface AroResourcesApi {

    ModelRegistry getModelRegistry();

    ResourcePackManager getResourcePackManager();

    ItemStack createItem(String category, String id, int amount);

    ItemStack createItem(Material material, String modelPath, int amount);

    Integer getModelData(String category, String id);

    String getSkullId(String path, String fallback);

    void reload();
}
