package com.jsorrell.carpetskyadditions.commands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import carpet.utils.CommandHelper;
import com.jsorrell.carpetskyadditions.gen.feature.SkyAdditionsConfiguredFeatures;
import com.jsorrell.carpetskyadditions.settings.SkyAdditionsSettings;
import com.jsorrell.carpetskyadditions.util.SkyAdditionsText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.LevelData;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class SkyIslandCommand {
    private static final SimpleCommandExceptionType ISLAND_NOT_CREATED =
            new SimpleCommandExceptionType(SkyAdditionsText.translatable("commands.skyisland.not_created"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        int maxIslandNum = SkyIslandPositionContainer.getNumIslands();

        LiteralArgumentBuilder<CommandSourceStack> command = literal("skyisland")
                .requires(source -> CommandHelper.canUseCommand(source, SkyAdditionsSettings.commandSkyIsland))
                .then(literal("new").executes(c -> newIsland(c.getSource())))
                .then(literal("join")
                        .then(argument("num", IntegerArgumentType.integer(1, maxIslandNum))
                                .executes(c -> joinIsland(
                                        c.getSource(),
                                        c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "num")))
                                .then(argument("player", EntityArgument.player())
                                        .executes(c -> joinIsland(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "num"))))))
                .then(literal("locate")
                        .then(argument("num", IntegerArgumentType.integer(1, maxIslandNum))
                                .executes(c -> locateIsland(c.getSource(), IntegerArgumentType.getInteger(c, "num")))));

        dispatcher.register(command);
    }

    private static int locateIsland(CommandSourceStack source, int islandNum) throws CommandSyntaxException {
        ChunkPos chunkPos = SkyIslandPositionContainer.getChunk(islandNum);
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        ChunkAccess chunk = source.getLevel().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
        if (chunk.getPersistedStatus() != ChunkStatus.FULL) {
            throw ISLAND_NOT_CREATED.create();
        }

        MutableComponent text = ComponentUtils.wrapInSquareBrackets(
                        SkyAdditionsText.translatable("commands.skyisland.locate.coordinates", x, z))
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + x + " ~ " + z))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
        source.sendSuccess(
                () -> SkyAdditionsText.translatable("commands.skyisland.locate.success", islandNum, text), false);

        BlockPos sourcePos = BlockPos.containing(source.getPosition());
        int xOff = sourcePos.getX() - x;
        int zOff = sourcePos.getZ() - z;
        return Mth.floor(Mth.sqrt(xOff * xOff + zOff * zOff));
    }


    private static int newIsland(CommandSourceStack source) {
        int max = SkyIslandPositionContainer.getNumIslands();
        Optional<ImmutablePair<Integer, ChunkPos>> islandOpt = IntStream.range(1, max)
                .mapToObj(i -> ImmutablePair.of(i, SkyIslandPositionContainer.getChunk(i)))
                .filter(i -> {
                    ChunkAccess chunk = source.getLevel().getChunk(i.right.x, i.right.z, ChunkStatus.EMPTY);
                    return chunk.getPersistedStatus() == ChunkStatus.EMPTY;
                })
                .findFirst();
        if (islandOpt.isEmpty()) {
            source.sendSuccess(() -> SkyAdditionsText.translatable("commands.skyisland.new.no_valid_positions"), true);
            return 0;
        }
        ImmutablePair<Integer, ChunkPos> island = islandOpt.get();
        ChunkPos chunkPos = island.right;
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();

        // Load the target area
        source.getLevel().getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, chunkPos, 2);
        Registry<ConfiguredFeature<?, ?>> configuredFeatureRegistry =
                source.getServer().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);

        //ConfiguredFeature<?, ?> skyIslandFeature = getIslandFeature(configuredFeatureRegistry);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureSeed(source.getLevel().getSeed(), chunkPos.x, chunkPos.z);

        Holder.Reference<ConfiguredFeature<?, ?>> skyIslandFeature = source.getServer().overworld().registryAccess()
            .lookupOrThrow(Registries.CONFIGURED_FEATURE)
            .get(SkyAdditionsConfiguredFeatures.SPAWN_PLATFORM).get();

        if (!skyIslandFeature.value().place(source.getServer().overworld(), source.getServer().overworld().getChunkSource().getGenerator(), random, new BlockPos(x, 0, z))) {
            SkyAdditionsSettings.LOG.error("Couldn't generate new island");
        }

        Supplier<Component> feedback =
                () -> SkyAdditionsText.translatable("commands.skyisland.new.success", island.getLeft(), x, z);
        source.sendSuccess(feedback, true);
        return island.getLeft();
    }

    private static int joinIsland(CommandSourceStack source, ServerPlayer player, int islandNum)
            throws CommandSyntaxException {
        ChunkPos chunkPos = SkyIslandPositionContainer.getChunk(islandNum);
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        joinIsland(source, player, x, z);
        return 1;
    }

    private static void joinIsland(CommandSourceStack source, ServerPlayer player, int x, int z)
            throws CommandSyntaxException {
        BlockPos pos = new BlockPos(x, 0, z);
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkAccess chunk = source.getLevel().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY);
        int y;
        Supplier<Integer> spawnHeight = () -> chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
        if (chunk.getPersistedStatus() != ChunkStatus.FULL || (y = spawnHeight.get()) <= chunk.getMinY()) {
            throw ISLAND_NOT_CREATED.create();
        }
        player.teleportTo(x + 0.5, y, z + 0.5);
        if (!player.isFallFlying()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            player.setOnGround(true);
        }
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(new LevelData.RespawnData(new GlobalPos(player.level().dimension(),new BlockPos(x, y, z)),0f, 0f), false), false);
    }

    public abstract static class SkyIslandPositionContainer {
        private static final ArrayList<ChunkPos> ISLAND_CHUNKS = new ArrayList<>();
        // Ordered to prioritize maximum distance from origin + previous islands
        // All of these numbers are 1 too high by mistake. Kept to preserve island numbers; 64 changed to 0
        private static final int[] ORDERING = {
            46, 59, 41, 54, 50, 63, 24, 13, 8, 16, 57, 48, 61, 39, 52, 19, 11, 31, 36, 20, 33, 44, 27, 22, 29, 1, 4, 3,
            6, 2, 5, 38, 30, 34, 26, 35, 25, 21, 28, 37, 23, 32, 51, 0, 43, 56, 40, 53, 49, 62, 45, 58, 47, 60, 42, 55,
            10, 7, 17, 12, 15, 14, 9, 18
        };

        static {
            ISLAND_CHUNKS.addAll(getIslandsInRing(384, 6, 0.25));
            ISLAND_CHUNKS.addAll(getIslandsInRing(768, 13, 0.5));
            ISLAND_CHUNKS.addAll(getIslandsInRing(1152, 19, 0.75));
            ISLAND_CHUNKS.addAll(getIslandsInRing(1536, 26, 1.));
        }

        public static int getNumIslands() {
            return ORDERING.length;
        }

        // 1 indexed
        public static ChunkPos getChunk(int i) {
            return ISLAND_CHUNKS.get(ORDERING[i - 1]);
        }

        private static ArrayList<ChunkPos> getIslandsInRing(int radius, int num, double offetAngle) {
            ArrayList<ChunkPos> islands = new ArrayList<>();

            for (int i = 0; i < num; i++) {
                double angle = offetAngle + i * (2 * Math.PI) / num;
                double x = Math.sin(angle) * radius;
                double z = Math.cos(angle) * radius;
                islands.add(new ChunkPos((int) x, (int) z));
            }
            return islands;
        }
    }
}
