package com.soundmixer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores a per-sound-event volume multiplier in {@code config/sound_mixer.json}.
 * Values are kept in memory all the time and only written to disk when dirty,
 * so dragging a slider never blocks the render thread on I/O.
 */
public final class SoundMixerConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("sound_mixer/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final float MIN_MULTIPLIER = 0.0F;
	private static final float MAX_MULTIPLIER = 2.0F;
	private static final float EPSILON = 0.001F;

	private static final Map<String, Float> VOLUMES = new ConcurrentHashMap<>();
	private static final AtomicBoolean DIRTY = new AtomicBoolean(false);
	private static volatile boolean loaded;

	private SoundMixerConfig() {
	}

	public static void load() {
		if (loaded) {
			return;
		}
		synchronized (SoundMixerConfig.class) {
			if (loaded) {
				return;
			}
			VOLUMES.clear();
			Path path = path();
			if (Files.isRegularFile(path)) {
				try {
					String json = Files.readString(path, StandardCharsets.UTF_8);
					JsonElement rootElement = JsonParser.parseString(json);
					if (rootElement.isJsonObject()) {
						JsonElement volumesElement = rootElement.getAsJsonObject().get("volumes");
						if (volumesElement != null && volumesElement.isJsonObject()) {
							for (Map.Entry<String, JsonElement> entry : volumesElement.getAsJsonObject().entrySet()) {
								try {
									float value = entry.getValue().getAsFloat();
									if (value < MIN_MULTIPLIER || value > MAX_MULTIPLIER) {
										value = clamp(value);
									}
									if (Math.abs(value - 1.0F) > EPSILON) {
										VOLUMES.put(entry.getKey(), value);
									}
								} catch (RuntimeException ignored) {
									LOGGER.warn("Ignoring invalid volume for {}", entry.getKey());
								}
							}
						}
					}
				} catch (IOException | RuntimeException exception) {
					LOGGER.warn("Could not read {}", path, exception);
				}
			}
			loaded = true;
			DIRTY.set(false);
			LOGGER.info("Loaded {} custom sound volume(s)", VOLUMES.size());
		}
	}

	public static float getMultiplier(Identifier soundId) {
		Float value = VOLUMES.get(soundId.toString());
		return value == null ? 1.0F : value;
	}

	public static void set(Identifier soundId, float multiplier) {
		load();
		float value = clamp(multiplier);
		String key = soundId.toString();
		if (Math.abs(value - 1.0F) <= EPSILON) {
			if (VOLUMES.remove(key) != null) {
				DIRTY.set(true);
			}
		} else if (!Float.valueOf(value).equals(VOLUMES.put(key, value))) {
			DIRTY.set(true);
		}
	}

	public static void reset(Identifier soundId) {
		load();
		if (VOLUMES.remove(soundId.toString()) != null) {
			DIRTY.set(true);
		}
	}

	public static void resetAll() {
		load();
		if (!VOLUMES.isEmpty()) {
			VOLUMES.clear();
			DIRTY.set(true);
		}
	}

	public static boolean isModified(Identifier soundId) {
		return VOLUMES.containsKey(soundId.toString());
	}

	public static int modifiedCount() {
		return VOLUMES.size();
	}

	public static void saveIfDirty() {
		if (!loaded || !DIRTY.compareAndSet(true, false)) {
			return;
		}
		save();
	}

	public static void save() {
		load();
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			JsonObject volumes = new JsonObject();
			for (Map.Entry<String, Float> entry : new TreeMap<>(VOLUMES).entrySet()) {
				volumes.addProperty(entry.getKey(), entry.getValue());
			}
			root.add("volumes", volumes);

			Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicMoveFailed) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
			DIRTY.set(false);
		} catch (IOException exception) {
			DIRTY.set(true);
			LOGGER.warn("Could not save {}", path, exception);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("sound_mixer.json");
	}

	private static float clamp(float value) {
		if (Float.isNaN(value)) {
			return 1.0F;
		}
		return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
	}
}
