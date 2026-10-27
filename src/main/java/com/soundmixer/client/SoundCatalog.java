package com.soundmixer.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a searchable, localized catalogue of every sound event currently
 * registered (vanilla and modded).
 */
public final class SoundCatalog {
	private static final Logger LOGGER = LoggerFactory.getLogger("sound_mixer/names");
	private static volatile Map<String, LocalizedName> names;

	private SoundCatalog() {
	}

	public static List<SoundEntry> build() {
		Map<String, LocalizedName> localizedNames = names();
		boolean chinese = isChinese();
		List<SoundEntry> entries = new ArrayList<>(BuiltInRegistries.SOUND_EVENT.size());
		for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
			LocalizedName localized = localizedNames.get(id.toString());
			String name;
			String searchNames;
			if (localized != null) {
				name = chinese ? localized.zh() : localized.en();
				searchNames = localized.en() + " " + localized.zh();
			} else {
				name = resolveName(id);
				searchNames = name;
			}
			Category category = Category.of(id);
			String search = (name + " " + searchNames + " " + id + " "
					+ id.getPath().replace('.', ' ').replace('_', ' ') + " "
					+ category.searchTerms() + " " + aliases(id)).toLowerCase(Locale.ROOT);
			entries.add(new SoundEntry(id, name, id.toString(), category, search));
		}
		entries.sort(Comparator.comparing(SoundEntry::name, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(entry -> entry.id().toString()));
		return List.copyOf(entries);
	}

	private static Map<String, LocalizedName> names() {
		Map<String, LocalizedName> current = names;
		if (current != null) {
			return current;
		}
		synchronized (SoundCatalog.class) {
			if (names == null) {
				names = loadNames();
			}
			return names;
		}
	}

	private static Map<String, LocalizedName> loadNames() {
		try (InputStream stream = SoundCatalog.class.getResourceAsStream("/assets/sound_mixer/sound_names.json")) {
			if (stream == null) {
				LOGGER.warn("Sound name database is missing");
				return Map.of();
			}
			JsonObject root = JsonParser.parseReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, LocalizedName> loaded = new HashMap<>(root.size());
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject object = entry.getValue().getAsJsonObject();
				JsonElement english = object.get("en");
				JsonElement chinese = object.get("zh");
				if (english != null && chinese != null) {
					loaded.put(entry.getKey(), new LocalizedName(english.getAsString(), chinese.getAsString()));
				}
			}
			LOGGER.info("Loaded {} localized sound names", loaded.size());
			return Map.copyOf(loaded);
		} catch (Exception exception) {
			LOGGER.warn("Could not load the sound name database", exception);
			return Map.of();
		}
	}

	private static boolean isChinese() {
		try {
			String language = Minecraft.getInstance().getLanguageManager().getSelected();
			return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	/**
	 * Fallback for modded sound events (not present in the bundled vanilla name
	 * database). Resolution order: a Sound Mixer translation, then the vanilla
	 * subtitle key, then a readable registry id.
	 */
	private static String resolveName(Identifier id) {
		String customKey = "sound_mixer.sound." + id.getNamespace() + "." + id.getPath();
		String custom = lookup(customKey);
		if (custom != null) {
			return custom;
		}

		String namespaced = id.getNamespace().equals("minecraft")
				? null
				: lookup("subtitles." + id.getNamespace() + "." + id.getPath());
		if (namespaced != null) {
			return namespaced;
		}

		String vanilla = lookup("subtitles." + id.getPath());
		if (vanilla != null) {
			return vanilla;
		}
		return prettify(id);
	}

	private static String lookup(String translationKey) {
		String translated = Component.translatable(translationKey).getString();
		return translated.equals(translationKey) || translated.isBlank() ? null : translated;
	}

	/**
	 * A few sounds are shared by several gameplay objects, so the subtitle alone
	 * does not contain the words players usually search for. These aliases make
	 * e.g. "tnt" find the generic explosion sound used by TNT.
	 */
	private static String aliases(Identifier id) {
		return switch (id.toString()) {
			case "minecraft:entity.generic.explode" -> "tnt explosion 爆炸 tnt爆炸 炸药";
			case "minecraft:entity.tnt.primed" -> "tnt fuse 点燃 炸药";
			case "minecraft:entity.blaze.shoot" -> "blaze fireball 烈焰人 火球 攻击";
			case "minecraft:entity.player.hurt" -> "player hurt 玩家 受伤";
			case "minecraft:entity.generic.hurt" -> "hurt 受伤";
			case "minecraft:entity.player.death" -> "player death 玩家 死亡";
			case "minecraft:entity.generic.death" -> "death 死亡";
			case "minecraft:entity.creeper.primed" -> "creeper hiss fuse 苦力怕 嘶嘶 引信";
			case "minecraft:entity.arrow.shoot" -> "arrow shoot bow 箭 射击 弓";
			case "minecraft:entity.item.pickup" -> "item pickup 物品 拾取";
			case "minecraft:entity.experience_orb.pickup" -> "experience orb pickup 经验球 拾取";
			case "minecraft:ui.button.click" -> "button click ui 按钮 点击 界面";
			default -> "";
		};
	}

	private static String prettify(Identifier id) {
		String[] words = id.getPath().replace('.', ' ').replace('_', ' ').split(" ");
		StringBuilder builder = new StringBuilder(id.getPath().length());
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) {
				builder.append(word, 1, word.length());
			}
		}
		return builder.isEmpty() ? id.toString() : builder.toString();
	}

	public record LocalizedName(String en, String zh) {
	}

	public record SoundEntry(Identifier id, String name, String idText, Category category, String search) {
		public boolean matches(String query) {
			if (query.isEmpty()) {
				return true;
			}
			for (String token : query.split("\\s+")) {
				if (!search.contains(token)) {
					return false;
				}
			}
			return true;
		}
	}

	public enum Category {
		ALL("sound_mixer.category.all", "all everything 全部 所有"),
		BLOCKS("sound_mixer.category.blocks", "block blocks 方块"),
		ENTITIES("sound_mixer.category.entities", "entity entities mob mobs 实体 生物"),
		AMBIENT("sound_mixer.category.ambient", "ambient environment weather 环境 天气"),
		MUSIC("sound_mixer.category.music", "music record disc 音乐 唱片"),
		UI("sound_mixer.category.ui", "ui interface gui 界面"),
		OTHER("sound_mixer.category.other", "other misc 其他");

		private final String translationKey;
		private final String searchTerms;

		Category(String translationKey, String searchTerms) {
			this.translationKey = translationKey;
			this.searchTerms = searchTerms;
		}

		public Component label() {
			return Component.translatable(this.translationKey);
		}

		public String searchTerms() {
			return this.searchTerms;
		}

		public static Category of(Identifier id) {
			String path = id.getPath();
			if (path.startsWith("entity.")) {
				return ENTITIES;
			}
			if (path.startsWith("block.")) {
				return BLOCKS;
			}
			if (path.startsWith("ambient.") || path.startsWith("weather.")) {
				return AMBIENT;
			}
			if (path.startsWith("music.") || path.startsWith("music_disc.")
					|| path.startsWith("record.") || path.startsWith("records.")) {
				return MUSIC;
			}
			if (path.startsWith("ui.")) {
				return UI;
			}
			return OTHER;
		}
	}
}
