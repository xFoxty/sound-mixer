package com.soundmixer.client;

import com.soundmixer.config.SoundMixerConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A 0% - 200% slider bound to one sound event at a time.
 */
public final class VolumeSlider extends AbstractSliderButton {
	private SoundCatalog.SoundEntry entry;
	private boolean binding;
	private Runnable releaseCallback = () -> {
	};

	public VolumeSlider(int x, int y, int width, int height) {
		super(x, y, width, height, Component.literal("100%"), 0.5D);
		this.setTooltip(Tooltip.create(Component.translatable("sound_mixer.slider.tooltip")));
	}

	public void bind(SoundCatalog.SoundEntry entry) {
		this.entry = entry;
		this.binding = true;
		this.value = SoundMixerConfig.getMultiplier(entry.id()) / 2.0D;
		this.binding = false;
		this.updateMessage();
	}

	public void resetToDefault() {
		this.binding = true;
		this.value = 0.5D;
		this.binding = false;
		this.updateMessage();
	}

	public SoundCatalog.SoundEntry entry() {
		return this.entry;
	}

	public void setOnRelease(Runnable releaseCallback) {
		this.releaseCallback = releaseCallback;
	}

	@Override
	protected void updateMessage() {
		int percent = (int) Math.round(this.value * 200.0D);
		this.setMessage(Component.literal(percent + "%"));
	}

	@Override
	protected void applyValue() {
		if (this.binding || this.entry == null) {
			return;
		}
		SoundMixerConfig.set(this.entry.id(), (float) (this.value * 2.0D));
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		super.onRelease(event);
		SoundMixerConfig.saveIfDirty();
		this.releaseCallback.run();
	}
}
