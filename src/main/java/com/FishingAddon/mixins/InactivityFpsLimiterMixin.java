package com.FishingAddon.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.class_9919")
public class InactivityFpsLimiterMixin {
  @Shadow
  private int field_52732;

  @Overwrite(remap = false)
  public int update() {
    return this.field_52732;
  }
}
