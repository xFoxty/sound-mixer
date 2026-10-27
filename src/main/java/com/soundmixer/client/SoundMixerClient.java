package com.soundmixer.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soundmixer.config.SoundMixerConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SoundMixerClient implements ClientModInitializer {
	public static final String MOD_ID = "sound_mixer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KeyMapping openKey;
	private static int saveTicker;

	@Override
	public void onInitializeClient() {
		SoundMixerConfig.load();

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.sound_mixer.open",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (saveTicker++ >= 20) {
				saveTicker = 0;
				SoundMixerConfig.saveIfDirty();
			}

			boolean pressed = openKey.consumeClick();
			if (pressed && client.gui.screen() == null) {
				client.gui.setScreen(new SoundMixerScreen(null));
			}
		});

		LOGGER.info("Sound Mixer ready");
	}
}
