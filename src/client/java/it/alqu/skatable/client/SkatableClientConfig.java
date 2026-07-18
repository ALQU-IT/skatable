package it.alqu.skatable.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.alqu.skatable.Skatable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side config, stored as JSON in config/skatable-client.json.
 */
public class SkatableClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static SkatableClientConfig instance;

	/** Gently turn the camera to follow the board while riding. */
	public boolean smoothCamera = true;
	/** How strongly the camera follows the board each tick (0..1). */
	public float cameraFollowStrength = 0.12f;
	/** Show trick names / XP popups on the HUD. */
	public boolean showHud = true;
	/** Apply the deck material stat system (acceleration differences). */
	public boolean deckStats = true;
	/** Play surface-dependent rolling sounds. */
	public boolean rollSounds = true;

	public static SkatableClientConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("skatable-client.json");
	}

	private static SkatableClientConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				return GSON.fromJson(Files.readString(path), SkatableClientConfig.class);
			} catch (Exception e) {
				Skatable.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		SkatableClientConfig config = new SkatableClientConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(this));
		} catch (IOException e) {
			Skatable.LOGGER.warn("Could not save skatable client config", e);
		}
	}
}
