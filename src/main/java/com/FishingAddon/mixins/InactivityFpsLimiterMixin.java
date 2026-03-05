package com.FishingAddon.mixins;

import com.FishingAddon.module.QOL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_9919")
public class InactivityFpsLimiterMixin {

  @Shadow(remap = false)
  private int field_52732;

  @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
  private void fishingAddon$disableInactivityLimit(CallbackInfoReturnable<Integer> cir) {
    if (QOL.INSTANCE.getDisableInactivityFpsLimit()) {
      cir.setReturnValue(this.field_52732);
    }
  }
}
