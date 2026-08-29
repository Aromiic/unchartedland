package pt.joaoveiga.aroresources.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import pt.joaoveiga.aroresources.AroResources;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ModelRegistry {

    private final AroResources plugin;
    private YamlConfiguration modelsConfig;
    private YamlConfiguration skullsConfig;

    private final Map<String, Integer> modelDataCache = new HashMap<String, Integer>();
    private final Map<String, String> skullCache = new HashMap<String, String>();

    public ModelRegistry(AroResources plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.modelsConfig = loadYaml(plugin.getConfig().getString("models-file", "models.yml"));
        this.skullsConfig = loadYaml(plugin.getConfig().getString("skulls-file", "skulls.yml"));
        this.modelDataCache.clear();
        this.skullCache.clear();
    }

    public YamlConfiguration getModelsConfig() {
        return modelsConfig;
    }

    public YamlConfiguration getSkullsConfig() {
        return skullsConfig;
    }

    public Integer getModelData(String category, String id) {
        if (category == null || id == null) {
            return null;
        }
        String key = normalize(category) + "." + normalize(id);
        if (modelDataCache.containsKey(key)) {
            return modelDataCache.get(key);
        }

        Integer value = null;
        if (modelsConfig != null) {
            value = readInt(modelsConfig, "special-items.models." + normalize(category) + "." + normalize(id));
            if (value == null) {
                value = readInt(modelsConfig, "special-items.models." + normalize(category) + "." + normalize(id).replace('-', '_'));
            }
        }

        if (value == null) {
            value = legacyDefault(category, id);
        }

        modelDataCache.put(key, value);
        return value;
    }

    public String getSkullId(String path, String fallback) {
        if (path != null && skullCache.containsKey(path)) {
            return skullCache.get(path);
        }

        String value = null;
        if (skullsConfig != null && path != null && !path.trim().isEmpty()) {
            value = skullsConfig.getString(path);
        }

        if (value == null || value.trim().isEmpty()) {
            value = fallback;
        }

        if (path != null) {
            skullCache.put(path, value);
        }
        return value;
    }

    public ItemStack createItem(Material material, String modelPath, int amount) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (modelPath != null && !modelPath.trim().isEmpty()) {
                Integer cmd = getModelDataFromPath(modelPath);
                if (cmd != null) {
                    try {
                        meta.setCustomModelData(cmd);
                    } catch (Throwable ignored) {
                    }
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "model_path"), PersistentDataType.STRING, modelPath);
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createItem(String category, String id, int amount) {
        Material material = resolveMaterial(category, id);
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + prettyName(id));
            Integer cmd = getModelData(category, id);
            if (cmd != null) {
                try {
                    meta.setCustomModelData(cmd);
                } catch (Throwable ignored) {
                }
            }
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "resource_category"), PersistentDataType.STRING, normalize(category));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "resource_id"), PersistentDataType.STRING, normalize(id));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Integer getModelDataFromPath(String modelPath) {
        if (modelPath == null || modelPath.trim().isEmpty() || modelsConfig == null) {
            return null;
        }
        String normalized = modelPath.trim();
        Integer value = readInt(modelsConfig, normalized);
        if (value != null) {
            return value;
        }
        return readInt(modelsConfig, normalized.replace('-', '_'));
    }

    private Integer legacyDefault(String category, String id) {
        String cat = normalize(category);
        String key = normalize(id);
        if ("artifact".equals(cat)) {
            if ("chunk-loader".equals(key)) return 7101;
            if ("explorer-clock".equals(key)) return 7102;
            if ("traveler-boots".equals(key)) return 7103;
            if ("smelting-totem".equals(key)) return 7104;
        }
        if ("tool".equals(cat)) {
            if ("seismic-pickaxe".equals(key)) return 7201;
            if ("lumber-axe".equals(key)) return 7202;
            if ("excavator-shovel".equals(key)) return 7203;
            if ("farmer-hoe".equals(key)) return 7204;
        }
        if ("armor".equals(cat)) {
            if (key.contains("vampiric")) return 7301;
            if (key.contains("guardian")) return 7302;
            if (key.contains("explorer")) return 7303;
        }
        if ("set".equals(cat)) {
            if (key.contains("miner")) return 7401;
            if (key.contains("fisher")) return 7402;
            if (key.contains("vampiric")) return 7403;
            if (key.contains("guardian")) return 7404;
            if (key.contains("explorer")) return 7405;
        }
        if ("scroll".equals(cat)) {
            if ("repair".equals(key)) return 7501;
            if ("protection".equals(key)) return 7502;
            if ("enchantment".equals(key)) return 7503;
        }
        if ("evolution".equals(cat) && "ancient-sword".equals(key)) {
            return 7601;
        }
        return null;
    }

    private Material resolveMaterial(String category, String id) {
        String cat = normalize(category);
        String key = normalize(id);
        if ("artifact".equals(cat)) {
            if ("chunk-loader".equals(key)) return Material.BEACON;
            if ("explorer-clock".equals(key)) return Material.CLOCK;
            if ("traveler-boots".equals(key)) return Material.LEATHER_BOOTS;
            if ("smelting-totem".equals(key)) return Material.SMOKER;
        }
        if ("tool".equals(cat)) {
            if ("seismic-pickaxe".equals(key)) return Material.DIAMOND_PICKAXE;
            if ("lumber-axe".equals(key)) return Material.DIAMOND_AXE;
            if ("excavator-shovel".equals(key)) return Material.NETHERITE_SHOVEL;
            if ("farmer-hoe".equals(key)) return Material.DIAMOND_HOE;
        }
        if ("armor".equals(cat)) {
            if (key.contains("boots")) return Material.LEATHER_BOOTS;
            return Material.DIAMOND_CHESTPLATE;
        }
        if ("evolution".equals(cat)) {
            return Material.DIAMOND_SWORD;
        }
        if ("scroll".equals(cat) || "amulet".equals(cat) || "relic".equals(cat) || "gem".equals(cat)) {
            return Material.PAPER;
        }
        return Material.PAPER;
    }

    private String prettyName(String value) {
        if (value == null || value.isEmpty()) {
            return "Desconhecido";
        }
        String[] parts = value.split("[:_\\-]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }

    private Integer readInt(YamlConfiguration yaml, String path) {
        if (yaml == null || path == null || path.isEmpty()) {
            return null;
        }
        if (yaml.isInt(path)) {
            return yaml.getInt(path);
        }
        if (yaml.contains(path)) {
            try {
                return Integer.valueOf(String.valueOf(yaml.get(path)));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private YamlConfiguration loadYaml(String fileName) {
        try {
            File file = new File(plugin.getDataFolder(), fileName);
            if (!file.exists()) {
                plugin.saveResource(fileName, false);
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            try (InputStream input = plugin.getResource(fileName)) {
                if (input != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input));
                    copyMissing(defaults, yaml, "");
                    yaml.save(file);
                }
            } catch (Exception ignored) {
            }
            return yaml;
        } catch (Exception exception) {
            plugin.getLogger().warning("Falha ao carregar " + fileName + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private void copyMissing(ConfigurationSection source, ConfigurationSection target, String prefix) {
        for (String key : source.getKeys(false)) {
            String fullPath = prefix.isEmpty() ? key : prefix + "." + key;
            if (source.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                }
                copyMissing(source.getConfigurationSection(key), target.getConfigurationSection(key), fullPath);
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, source.get(key));
            }
        }
    }
}
