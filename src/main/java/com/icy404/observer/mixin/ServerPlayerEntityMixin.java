package com.icy404.observer.mixin;

import com.icy404.observer.ending.EndingManager;
import com.icy404.observer.profile.HomeProfiler;
import com.mojang.datafixers.util.Either;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public final class ServerPlayerEntityMixin {
    @Inject(method = "trySleep", at = @At("RETURN"))
    private void observer$onTrySleep(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        if (cir.getReturnValue().right().isPresent()) {
            HomeProfiler.recordSleep((ServerPlayerEntity) (Object) this, pos);
            EndingManager.onSleep((ServerPlayerEntity) (Object) this, pos);
        }
    }
}
