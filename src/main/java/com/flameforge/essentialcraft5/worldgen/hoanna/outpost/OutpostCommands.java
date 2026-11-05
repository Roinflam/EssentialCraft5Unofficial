package com.flameforge.essentialcraft5.worldgen.hoanna.outpost;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gametest.HoannaTestWorld;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /ec5 dev hoanna_census [种子] [边长] [步长]}（权限 4，只给开发 / 测试；第十三批 13e-S3，batch13.md §7.4 第 1 条）：
 * 暗影区普查，见 {@link OutpostCensus}。不带种子时量这台服务器真的海纳尔（群系源、结构集都是它自己的）；带种子时按那个种子现解一套海纳尔的生成器
 * （{@code gametest/HoannaTestWorld}，和 GameTest 同一套），不碰任何世界。默认以 (0, 0) 为中心 2 048 × 2 048 区块、每 4 区块一点，约 2 秒，
 * 在主线程上同步跑——别在有人玩的服上用。点数（(边长 / 步长)²）最多 {@value #MAX_POINTS}（默认参数那一档），超了直接报错不跑：
 * 每点约 7.5 µs，边长 8 192、步长 1 是 6 700 万点、约 8 分钟，会被看门狗当成卡死杀掉服务端。结果发给执行的人并写进日志（「[审判庭前哨]」开头，含分档明细）。
 */
public final class OutpostCommands {

    public static final String KEY = "commands.essentialcraft5.hoanna_census.";
    public static final int DEFAULT_SIZE = 2048;
    public static final int DEFAULT_STEP = 4;
    /** 一次最多采几个点（= 默认 2 048 / 4 的 512²，约 2 秒） */
    public static final long MAX_POINTS = 512L * 512L;
    private static final String ARG_SEED = "seed";
    private static final String ARG_SIZE = "size";
    private static final String ARG_STEP = "step";

    private OutpostCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> build(CommandBuildContext context) {
        return Commands.literal("dev").requires(source -> source.hasPermission(4))
                .then(Commands.literal("hoanna_census")
                        .executes(ctx -> census(ctx.getSource(), null, DEFAULT_SIZE, DEFAULT_STEP))
                        .then(Commands.argument(ARG_SEED, LongArgumentType.longArg())
                                .executes(ctx -> census(ctx.getSource(), LongArgumentType.getLong(ctx, ARG_SEED), DEFAULT_SIZE, DEFAULT_STEP))
                                .then(Commands.argument(ARG_SIZE, IntegerArgumentType.integer(64, 8192))
                                        .then(Commands.argument(ARG_STEP, IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> census(ctx.getSource(), LongArgumentType.getLong(ctx, ARG_SEED),
                                                        IntegerArgumentType.getInteger(ctx, ARG_SIZE), IntegerArgumentType.getInteger(ctx, ARG_STEP)))))));
    }

    /** 量哪一个世界：没给种子量真的海纳尔，给了按种子现解（解不出 / 没有结构集时为空） */
    public static Optional<Subject> subject(MinecraftServer server, @Nullable Long seed) {
        StructureForecast.World world;
        if (seed == null) {
            ServerLevel hoanna = ECHoanna.level(server);
            if (hoanna == null) {
                return Optional.empty();
            }
            world = StructureForecast.World.of(hoanna);
        } else {
            HoannaTestWorld.Resolved resolved = HoannaTestWorld.resolve(server, seed);
            world = resolved.forecast();
        }
        Optional<Holder.Reference<StructureSet>> set = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).getHolder(OutpostStructures.SET);
        if (set.isEmpty()) {
            return Optional.empty();
        }
        List<StructureForecast.Target> targets = StructureForecast.targets(world, set.get());
        return targets.isEmpty() ? Optional.empty() : Optional.of(new Subject(world, targets.get(0)));
    }

    /** 量的世界和前哨的结构集 */
    public record Subject(StructureForecast.World world, StructureForecast.Target target) {
    }

    /** 这组参数要采几个点（同 {@code OutpostCensus.sample} 的取整） */
    public static long points(int size, int step) {
        long side = Math.max(1, size / Math.max(1, step));
        return side * side;
    }

    private static int census(CommandSourceStack source, @Nullable Long seed, int size, int step) {
        long points = points(size, step);
        if (points > MAX_POINTS) {
            source.sendFailure(Component.translatable(KEY + "too_many_points", points, MAX_POINTS));
            return 0;
        }
        Optional<Subject> subject = subject(source.getServer(), seed);
        if (subject.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "no_set"));
            return 0;
        }
        StructureForecast.World world = subject.get().world();
        OutpostCensus.Grid grid = OutpostCensus.sample(world, subject.get().target().biomes(), ChunkPos.ZERO, size, step);
        OutpostCensus.Tally tally = OutpostCensus.tally(world, grid, subject.get().target());
        int[] q = OutpostCensus.sizeQuantiles(grid);
        long ms = grid.nanos() / 1_000_000L;
        int side = grid.side() * grid.step();
        source.sendSuccess(() -> Component.translatable(KEY + "header", String.valueOf(world.seed()), side, side, grid.step(), grid.samples(), ms), false);
        source.sendSuccess(() -> Component.translatable(KEY + "patches", grid.patches(), OutpostCensus.LARGE_PATCH, tally.largePatches(),
                tally.largeSiteChunks(), q[0], q[2], q[3]), false);
        source.sendSuccess(() -> Component.translatable(KEY + "outposts", tally.spacing(), tally.separation(), tally.outposts(), tally.candidates(),
                tally.inBiome(), String.format(Locale.ROOT, "%.0f", tally.excludedShare() * 100D), tally.unassigned(),
                String.format(Locale.ROOT, "%.2f", tally.perLargePatch()), String.format(Locale.ROOT, "%.0f", tally.largeEmptyShare() * 100D)), false);
        EssentialCraft5.LOGGER.info("[审判庭前哨] 普查 种子 {}、{} × {} 区块、步长 {}（{} 个点 {} ms）：{}；每片大小 中位 {} / 75% {} / 90% {} / 最大 {} 区块",
                world.seed(), side, side, grid.step(), grid.samples(), ms, OutpostCensus.describe(grid, tally), q[0], q[1], q[2], q[3]);
        return tally.outposts();
    }
}
