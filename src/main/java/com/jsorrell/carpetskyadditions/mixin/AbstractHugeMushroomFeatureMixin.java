package com.jsorrell.carpetskyadditions.mixin;

import com.jsorrell.carpetskyadditions.settings.SkyAdditionsSettings;
import java.util.Set;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHugeMushroomFeature.class)
public class AbstractHugeMushroomFeatureMixin {
    @Unique
    private void generateMycelium(LevelAccessor level, RandomSource random, BlockPos pos) {
        AlterGroundDecorator decorator = new AlterGroundDecorator(BlockStateProvider.simple(Blocks.MYCELIUM));
        decorator.place(
            new TreeDecorator.Context(
                level,
                (blockPos, blockState) -> level.setBlock(blockPos, blockState, Block.UPDATE_ALL),
                random,
                Set.of(pos),
                Set.of(),
                Set.of()
            )
        );
    }

    @Inject(method = "place", at = @At("TAIL"))
    private void generateMycelium(
            CallbackInfoReturnable<Boolean> cir,
            @Local WorldGenLevel level,
            @Local RandomSource random,
            @Local BlockPos pos
    ) {
        if (SkyAdditionsSettings.hugeMushroomsSpreadMycelium) {
            generateMycelium(level, random, pos);
        }
    }
}
