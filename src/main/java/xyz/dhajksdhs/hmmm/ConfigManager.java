package xyz.dhajksdhs.hmmm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages configuration for OreMiner and does saving and loading settings OreMiner.json
 */
public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ore-miner-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create(); // Google library that works with JSON, already in Minecraft.
    private static final String CONFIG_FILE_NAME = "OreMiner.json"; // Name changable later
    private static Path configPath; // Stores the config path

    /**
     * Looks for config file path if it exists and where to place it.
     */
    public static void initialize() {
        try {
            // Get the config directory (works for both client and server)
            Path configDir = Paths.get("config"); // Folder name
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                LOGGER.info("Created config directory");
            }
            configPath = configDir.resolve(CONFIG_FILE_NAME); // Name the fil
            LOGGER.info("Config file path: {}", configPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to initialize config directory", e);
        }
    }

    /**
     * Save current settings to config file - where all settings are written.
     */
    public static void saveConfig() {
        if (configPath == null) {
            initialize();
        }

        try {
            JsonObject config = new JsonObject(); // Create empty json

            // Save MaxAutomaticDepth
            config.addProperty("MaxAutomaticDepth", OreMiner.MaxDepth);
            // Save global enable state
            config.addProperty("globalEnabled", OreMiner.globalEnable);
            // Save the list of ores and add it to Json array
            JsonArray oresArray = new JsonArray();
            for (Block block : OreMiner.configuredOres) {
                Identifier id = Registries.BLOCK.getId(block);
                oresArray.add(id.toString());
            }
            config.add("veinMineableBlocks", oresArray);
            // Save player preferences - saves their enabled state
            JsonObject playerPrefs = new JsonObject();
            for (Map.Entry<UUID, Boolean> entry : OreMiner.enabled.entrySet()) {
                playerPrefs.addProperty(entry.getKey().toString(), entry.getValue());
            }
            config.add("playerPreferences", playerPrefs);

            // Write to file
            try (Writer writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(config, writer);
            }

            LOGGER.info("Config saved successfully to {}", configPath.getFileName());
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    /**
     * Load settings from config file - reads from disk and updates Java variables
     */
    public static void loadConfig() {
        if (configPath == null) {
            initialize();
        }

        // If config doesn't exist, creates it
        if (!Files.exists(configPath)) {
            LOGGER.info("Config file not found, creating!");
            saveConfig();
            return;
        }

        try {
            // Read and parse into Json object - Check if key exists -> Gets the values -> Update the java variable
            String content = Files.readString(configPath);
            JsonObject config = JsonParser.parseString(content).getAsJsonObject();

            // Load MaxAutomaticDepth
            if (config.has("MaxAutomaticDepth")) {
                OreMiner.MaxDepth = config.get("MaxAutomaticDepth").getAsInt();
                LOGGER.info("Loaded MaxAutomaticDepth: {}", OreMiner.MaxDepth);
            }
            // Load global enable state
            if (config.has("globalEnabled")) {
                OreMiner.globalEnable = config.get("globalEnabled").getAsBoolean();
                LOGGER.info("Loaded globalEnabled: {}", OreMiner.globalEnable);
            }
            // Load vein-mineable blocks
            if (config.has("veinMineableBlocks")) {
                OreMiner.configuredOres.clear();
                JsonArray oresArray = config.getAsJsonArray("veinMineableBlocks");

                for (int i = 0; i < oresArray.size(); i++) {
                    String blockId = oresArray.get(i).getAsString();
                    Identifier id = Identifier.tryParse(blockId);

                    if (id != null && Registries.BLOCK.containsId(id)) {
                        Block block = Registries.BLOCK.get(id);
                        OreMiner.configuredOres.add(block);
                    } else {
                        LOGGER.warn("Unknown block in config: {}", blockId);
                    }
                }
                LOGGER.info("Loaded {} vein-mineable blocks", OreMiner.configuredOres.size());
            }
            // Load player preferences
            if (config.has("playerPreferences")) {
                OreMiner.enabled.clear();
                JsonObject playerPrefs = config.getAsJsonObject("playerPreferences");

                for (String uuidStr : playerPrefs.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        boolean enabled = playerPrefs.get(uuidStr).getAsBoolean();
                        OreMiner.enabled.put(uuid, enabled);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid UUID in config: {}", uuidStr);
                    }
                }
                LOGGER.info("Loaded {} player preferences", OreMiner.enabled.size());
            }

            LOGGER.info("Config loaded successfully from {}", configPath.getFileName());
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        } catch (Exception e) {
            LOGGER.error("Failed to parse config, it may be corrupted", e);
        }
    }

    /**
     * Reload config from disk - calls loadconfig() again.
     */
    public static void reloadConfig() {
        LOGGER.info("Reloading config...");
        loadConfig();
    }
}