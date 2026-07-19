package it.alqu.skatable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.alqu.skatable.power.DeckPowers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Common (server-authoritative) config, stored as config/skatable.json.
 * The server's disabled-powers list is synced to clients on join.
 */
public class SkatableCommonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SkatableCommonConfig instance;

	/** Deck power ids (e.g. "copper", "tnt") that are switched off. */
	public List<String> disabledPowers = new ArrayList<>();

	public static SkatableCommonConfig get() {
		if (instance == null) {
			instance = load();
			DeckPowers.setDisabled(instance.disabledPowers);
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("skatable.json");
	}

	private static SkatableCommonConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				return GSON.fromJson(Files.readString(path), SkatableCommonConfig.class);
			} catch (Exception e) {
				Skatable.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		SkatableCommonConfig config = new SkatableCommonConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(this));
		} catch (IOException e) {
			Skatable.LOGGER.warn("Could not save skatable common config", e);
		}
	}
}
