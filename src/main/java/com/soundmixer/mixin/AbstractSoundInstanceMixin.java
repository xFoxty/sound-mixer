package com.soundmixer.mixin;

import com.soundmixer.config.SoundMixerConfig;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales the volume of every sound right where the sound engine asks for it.
 * This covers one-shot sounds, looping sounds, music and UI clicks alike.
 */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
	@Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
	private void soundmixer$scaleVolume(CallbackInfoReturnable<Float> cir) {
		AbstractSoundInstance instance = (AbstractSoundInstance) (Object) this;
		Identifier identifier = instance.getIdentifier();
		float multiplier = SoundMixerConfig.getMultiplier(identifier);
		if (multiplier != 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * multiplier);
		}
	}
}
