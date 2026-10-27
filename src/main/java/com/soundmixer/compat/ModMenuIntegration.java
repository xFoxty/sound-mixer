package com.soundmixer.compat;

import com.soundmixer.client.SoundMixerScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Reflective entry point used by Mod Menu. This class is only loaded when Mod
 * Menu is installed, so Mod Menu stays an optional dependency.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SoundMixerScreen::new;
	}
}
