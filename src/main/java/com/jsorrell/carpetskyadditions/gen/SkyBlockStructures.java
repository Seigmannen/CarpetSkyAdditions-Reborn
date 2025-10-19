package com.jsorrell.carpetskyadditions.gen;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfigs;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;


public class SkyBlockStructures {
    protected record StructureOrientation(Rotation rotation, Mirror mirror) {
        private int applyXTransform(int x, int z, BoundingBox boundingBox) {
            if ((rotation == Rotation.NONE && mirror != Mirror.FRONT_BACK)
                || (rotation == Rotation.CLOCKWISE_180 && mirror == Mirror.FRONT_BACK)) {
                return boundingBox.minX() + x;
            } else if (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180) {
                return boundingBox.maxX() - x;
            } else if ((rotation == Rotation.COUNTERCLOCKWISE_90 && mirror != Mirror.LEFT_RIGHT)
                || (rotation == Rotation.CLOCKWISE_90 && mirror == Mirror.LEFT_RIGHT)) {
                return boundingBox.minX() + z;
            } else {
                return boundingBox.maxX() - z;
            }
        }

        private int applyZTransform(int x, int z, BoundingBox boundingBox) {
            if ((rotation == Rotation.NONE && mirror != Mirror.LEFT_RIGHT)
                || (rotation == Rotation.CLOCKWISE_180 && mirror == Mirror.LEFT_RIGHT)) {
                return boundingBox.minZ() + z;
            } else if (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180) {
                return boundingBox.maxZ() - z;
            } else if ((rotation == Rotation.CLOCKWISE_90 && mirror != Mirror.FRONT_BACK)
                || (rotation == Rotation.COUNTERCLOCKWISE_90 && mirror == Mirror.LEFT_RIGHT)) {
                return boundingBox.minZ() + x;
            } else {
                return boundingBox.maxZ() - x;
            }
        }
    }

    protected abstract static class SkyBlockStructure {
        protected BoundingBox boundingBox;
        protected StructureOrientation orientation;
        protected Rotation rotation;
        protected Mirror mirror;

        public SkyBlockStructure(StructurePiece piece) {
            boundingBox = piece.getBoundingBox();
            rotation = Objects.requireNonNullElse(piece.getRotation(), Rotation.NONE);
            mirror = Objects.requireNonNullElse(piece.getMirror(), Mirror.NONE);
            orientation = new StructureOrientation(rotation, mirror);
        }

        protected int applyXTransform(int x, int z) {
            return orientation.applyXTransform(x, z, boundingBox);
        }

        protected int applyYTransform(int y) {
            return y + boundingBox.minY();
        }

        protected int applyZTransform(int x, int z) {
            return orientation.applyZTransform(x, z, boundingBox);
        }

        protected BlockPos.MutableBlockPos offsetPos(int x, int y, int z) {
            return new BlockPos.MutableBlockPos(applyXTransform(x, z), applyYTransform(y), applyZTransform(x, z));
        }

        protected BlockPos.MutableBlockPos addBlock(ServerLevelAccessor level, BlockState block, int x, int y, int z, BoundingBox bounds) {
            BlockPos.MutableBlockPos blockPos = offsetPos(x, y, z);
            if (!bounds.isInside(blockPos)) {
                return blockPos;
            }
            if (mirror != Mirror.NONE) {
                block = block.mirror(mirror);
            }
            if (rotation != Rotation.NONE) {
                block = block.rotate(rotation);
            }
            level.getChunk(blockPos).setInhabitedTime(6000);
            level.setBlock(blockPos, block, Block.UPDATE_CLIENTS);
            return blockPos;
        }

        protected void fillBlocks(
            ServerLevelAccessor level,
            BlockState block,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            BoundingBox bounds) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        addBlock(level, block, x, y, z, bounds);
                    }
                }
            }
        }

        public abstract void generate(ServerLevelAccessor level, BoundingBox bounds, RandomSource random);
    }

    public static class EndPortalStructure extends SkyBlockStructure {
        public EndPortalStructure(StructurePiece piece) {
            super(piece);
        }

        @Override
        public void generate(ServerLevelAccessor level, BoundingBox bounds, RandomSource random) {
            BlockState northFrame = Blocks.END_PORTAL_FRAME.defaultBlockState();
            BlockState southFrame = northFrame.setValue(EndPortalFrameBlock.FACING, Direction.SOUTH);
            BlockState eastFrame = northFrame.setValue(EndPortalFrameBlock.FACING, Direction.EAST);
            BlockState westFrame = northFrame.setValue(EndPortalFrameBlock.FACING, Direction.WEST);

            boolean complete = true;
            boolean[] hasEye = new boolean[12];
            for (int l = 0; l < hasEye.length; ++l) {
                hasEye[l] = random.nextFloat() > 0.9f;
                complete &= hasEye[l];
            }

            addBlock(level, southFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[0]), 4, 3, 3, bounds);
            addBlock(level, southFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[1]), 5, 3, 3, bounds);
            addBlock(level, southFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[2]), 6, 3, 3, bounds);
            addBlock(level, northFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[3]), 4, 3, 7, bounds);
            addBlock(level, northFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[4]), 5, 3, 7, bounds);
            addBlock(level, northFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[5]), 6, 3, 7, bounds);
            addBlock(level, eastFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[6]), 3, 3, 4, bounds);
            addBlock(level, eastFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[7]), 3, 3, 5, bounds);
            addBlock(level, eastFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[8]), 3, 3, 6, bounds);
            addBlock(level, westFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[9]), 7, 3, 4, bounds);
            addBlock(level, westFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[10]), 7, 3, 5, bounds);
            addBlock(level, westFrame.setValue(EndPortalFrameBlock.HAS_EYE, hasEye[11]), 7, 3, 6, bounds);

            if (complete) {
                fillBlocks(level, Blocks.END_PORTAL.defaultBlockState(), 4, 3, 4, 6, 3, 6, bounds);
            }
        }
    }

    public static class AncientCityPortalStructure extends SkyBlockStructure {
        public AncientCityPortalStructure(StructurePiece piece) {
            super(piece);
        }

        @Override
        public void generate(ServerLevelAccessor level, BoundingBox bounds, RandomSource random) {
            // Horizontal Sides
            fillBlocks(level, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 13, 17, 10, 13, 17, 31, bounds);
            fillBlocks(level, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 13, 24, 10, 13, 24, 31, bounds);

            // Vertical Sides
            fillBlocks(level, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 13, 18, 10, 13, 23, 10, bounds);
            fillBlocks(level, Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), 13, 18, 31, 13, 23, 31, bounds);

            // Sculk Shrieker
            addBlock(
                level,
                Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true),
                9,
                8,
                20,
                bounds);
        }
    }

    public static class TrialChamberEntrance extends SkyBlockStructure {

        public TrialChamberEntrance(StructurePiece piece){
            super(piece);
        }

        @Override
        public void generate(ServerLevelAccessor level, BoundingBox bounds, RandomSource random) {
            // Place regular vault
            BlockPos.MutableBlockPos vaultPos = addBlock(
                level,
                Blocks.VAULT.defaultBlockState(),
                0, 0, 0,
                bounds);

            // Place ominous vault
            BlockPos.MutableBlockPos ominousVaultPos = addBlock(
                level,
                Blocks.VAULT.defaultBlockState().setValue(VaultBlock.OMINOUS, true),
                1, 0, 0,
                bounds);

            // Place trial spawner
            BlockPos.MutableBlockPos trialSpawnerPos = addBlock(
                level,
                Blocks.TRIAL_SPAWNER.defaultBlockState(),
                10, 0, 0,
                bounds);

            level.getServer().submit(() -> {
                BlockEntity trialSpawnerEntity = level.getBlockEntity(trialSpawnerPos);
                if (trialSpawnerEntity instanceof TrialSpawnerBlockEntity spawner) {
                    CompoundTag configNbt = new CompoundTag();
                    configNbt.putString("normal_config", "minecraft:trial_chamber/breeze/normal");
                    configNbt.putString("ominous_config", "minecraft:trial_chamber/breeze/ominous");
                    ValueInput valueInput = TagValueInput.create(null, level.registryAccess(), configNbt);
                    spawner.getTrialSpawner().load(valueInput);
                    spawner.setChanged();
                }

                BlockEntity vaultEntity = level.getBlockEntity(vaultPos);

                if (vaultEntity instanceof VaultBlockEntity vault) {
                    VaultConfig vaultConfig = vault.getConfig();
                    ItemStack trialKey = new ItemStack(Items.TRIAL_KEY);
                    VaultConfig config = new VaultConfig(
                        BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
                        vaultConfig.activationRange(),
                        vaultConfig.deactivationRange(),
                        trialKey,
                        Optional.empty(),
                        PlayerDetector.INCLUDING_CREATIVE_PLAYERS,
                        PlayerDetector.EntitySelector.SELECT_FROM_LEVEL
                    );
                    vault.setConfig(config);
                    vault.setChanged();
                }

                BlockEntity ominousVaultEntity = level.getBlockEntity(ominousVaultPos);
                if (ominousVaultEntity instanceof VaultBlockEntity ominousVault) {
                    VaultConfig vaultConfig = ominousVault.getConfig();
                    ItemStack ominousTrialKey = new ItemStack(Items.OMINOUS_TRIAL_KEY);
                    VaultConfig ominousConfig = new VaultConfig(
                        BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS,
                        vaultConfig.activationRange(),
                        vaultConfig.deactivationRange(),
                        ominousTrialKey,
                        Optional.empty(),
                        PlayerDetector.INCLUDING_CREATIVE_PLAYERS,
                        PlayerDetector.EntitySelector.SELECT_FROM_LEVEL
                    );
                    ominousVault.setConfig(ominousConfig);
                    ominousVault.setChanged();
                }
            });
        }
    }

    public static class SpawnerStructure extends SkyBlockStructure {
        private final BlockPos spawnerPos;
        private final EntityType<?> spawnerType;

        public SpawnerStructure(StructurePiece piece, BlockPos spawnerPos, EntityType<?> spawnerType) {
            super(piece);
            this.spawnerPos = spawnerPos;
            this.spawnerType = spawnerType;
        }

        @Override
        public void generate(ServerLevelAccessor level, BoundingBox bounds, RandomSource random) {
            BlockPos.MutableBlockPos spawnerAbsolutePos =
                offsetPos(spawnerPos.getX(), spawnerPos.getY(), spawnerPos.getZ());
            if (bounds.isInside(spawnerAbsolutePos)) {
                level.getChunk(spawnerAbsolutePos).setInhabitedTime(6000);
                level.setBlock(spawnerAbsolutePos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockEntity blockEntity = level.getBlockEntity(spawnerAbsolutePos);
                if (blockEntity instanceof SpawnerBlockEntity spawnerEntity) {
                    spawnerEntity.setEntityId(spawnerType, random);
                }
            }
        }
    }

    public static class SilverfishSpawnerStructure extends SpawnerStructure {
        public SilverfishSpawnerStructure(StructurePiece piece) {
            super(piece, new BlockPos(5, 3, 9), EntityType.SILVERFISH);
        }
    }

    public static class MagmaCubeSpawner extends SpawnerStructure {
        public MagmaCubeSpawner(StructurePiece piece) {
            super(piece, new BlockPos(11, 7, 19), EntityType.MAGMA_CUBE);
        }
    }
}
