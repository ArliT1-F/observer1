package com.icy404.observer.mixin;

import com.icy404.observer.profile.HomeProfiler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public final class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void observer$onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) {
            return;
        }
        if (context.getWorld().isClient()) {
            return;
        }

        if (context.getPlayer() instanceof ServerPlayerEntity player) {
            HomeProfiler.recordBlockPlace(player);
        }
    }
}
