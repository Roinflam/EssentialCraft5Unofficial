package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.vault.VaultElement;
import com.flameforge.essentialcraft5.block.vault.VaultKeyholeBlock;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.block.vault.VaultSealBlock;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity.ClaimResult;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity.KeyResult;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity.PedestalResult;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECSealedVault;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.SealedVaultStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultLayout;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultPuzzle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 第十三批 13e-S1 封印宝库（batch13.md §6.4、§12.2 13e 行；HS §2.4.1）。batch {@value #BATCH}。
 * <p>
 * 要整座宝库的测试用 {@link SealedVaultStructure#placeDirect} 摆在超平坦主世界远处的半空（每条测试一个位置，互不干扰），
 * 玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在玩家列表里，「答对时谁在前厅」用 {@code VaultCoreBlockEntity.setTestOverrides} 给）。
 * 用到静态覆盖（时间、难度、在场玩家、配置）的测试整个在测试函数里同步做完、finally 里还原，同一 batch 的别的测试看不到。
 * <ul>
 *   <li>{@code vaultLayoutSeeds}：300 个种子的布局（水平 ≤ 24 × 24、深度、四座祭座在前厅里、锁孔 / 核心 / 门 / 碑互相找得到、台阶连成一圈落到前厅地上、
 *       碑文能还原出顺序、四个拼件都在总包围盒里）；</li>
 *   <li>{@code vaultPiecesRoundTripNbt}：谜题种子、地面高度存读档；</li>
 *   <li>{@code vaultGeneratesOnlyInVaultBiomes}：海纳尔生成器上，群系对的区块生成、不对的不生成；结构集不在主世界；</li>
 *   <li>{@code placeVaultEndToEnd}：摆出来的方块（碑、祭座刻的元素和核心的谜题一致、锁孔、九块封印石、核心、台阶）、入口找得到核心；</li>
 *   <li>{@code vaultSolveListsOnlyAntechamber}：答对开门、只记前厅里的生存真玩家（创造的、宝库室里挖墙进来的、地面上的都不记）、核心每人一份、两人各一份；</li>
 *   <li>{@code vaultWrongRefundsHalfBacklashGuards}：答错退一半、反噬 20%、召 2 只守灵（来源召唤），60 秒内再错不召、满 60 秒再召；和平难度不召；</li>
 *   <li>{@code vaultNeedsKeyAndMatchingCrystal}：没放结晶不让放晶石、拿错元素不扣不算错、锁孔一次一颗；</li>
 *   <li>{@code vaultLockoutAfterThreeFails}：连错 3 次封锁一天（拨主世界时间后解封）、封锁中锁孔不扣结晶、答对清零；</li>
 *   <li>{@code vaultFakePlayerRejected}：假玩家右键锁孔、祭座、核心都没反应；</li>
 *   <li>{@code vaultSealOpensForListOrInside}：关着的封印石只为名单上的人和宝库室里的人再开；</li>
 *   <li>{@code vaultDoorClosesByScheduledTick}：门按计划刻关上、门洞里有生物时晚一点再关；</li>
 *   <li>{@code vaultBlocksUnbreakable}：五种方块挖不动、抗爆、推不动（真活塞推一次）、凋灵 / 末影龙标签；</li>
 *   <li>{@code vaultRewardTable}：奖励表能加载，开出来必有先辈残核和地脉结晶。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class SealedVaultTests {

    public static final String BATCH = "sealed_vault";
    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    /** 各测试摆宝库的位置：x = BASE_X + 序号 × 64，z 固定；半空里的地面高度 */
    private static final int BASE_X = 61_000;
    private static final int BASE_Z = 61_000;
    private static final int GROUND = 70;

    private SealedVaultTests() {
    }

    // ================================================================== 布局（纯计算）

    @GameTest(template = EMPTY, batch = BATCH)
    public static void vaultLayoutSeeds(GameTestHelper helper) {
        Set<Integer> firstElements = new HashSet<>();
        Set<String> northElements = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            long seed = (i + 1) * SEED_STEP;
            long mixed = HoannaStructurePiece.mix(seed);
            int ground = 40 + (int) Math.floorMod(mixed, 120L);
            int ox = (int) Math.floorMod(mixed >>> 20, 200_000L) - 100_000;
            int oz = (int) Math.floorMod(mixed >>> 40, 200_000L) - 100_000;
            VaultLayout.Plan plan = VaultLayout.plan(ox, ground, oz, -64);
            String at = " (种子 " + seed + "，地面 " + ground + ")";
            BoundingBox bounds = plan.bounds();
            helper.assertTrue(bounds.getXSpan() <= VaultLayout.MAX_SPAN && bounds.getZSpan() <= VaultLayout.MAX_SPAN,
                    "宝库水平应 ≤ 24 × 24：" + bounds + at);
            int depth = plan.depth();
            helper.assertTrue(depth == VaultLayout.depthFor(ground) && depth >= VaultLayout.MIN_DEPTH && depth <= VaultLayout.MAX_DEPTH,
                    "深度 = clamp(地面 − 56, 8, 20)：" + depth + at);
            helper.assertTrue(depth == Math.min(20, Math.max(8, ground - 56)), "深度公式" + at);
            BoundingBox ante = plan.antechamberInterior();
            helper.assertTrue(ante.isInside(plan.keyhole()), "锁孔在前厅里" + at);
            Set<BlockPos> pedestals = new HashSet<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos pedestal = plan.pedestal(side);
                helper.assertTrue(ante.isInside(pedestal), "祭座 " + side + " 应在前厅里" + at);
                pedestals.add(pedestal);
            }
            helper.assertTrue(pedestals.size() == 4, "四座祭座位置各不相同" + at);
            helper.assertTrue(plan.chamberInterior().isInside(plan.core()), "核心在宝库室里" + at);
            helper.assertTrue(plan.core().equals(plan.keyhole().offset(VaultLayout.CORE_FROM_KEYHOLE)), "锁孔 → 核心的偏移" + at);
            for (BlockPos cell : plan.doorCells()) {
                helper.assertTrue(plan.chamberBox().isInside(cell) && !plan.antechamberBox().isInside(cell), "封印石在宝库室南墙上" + at);
                helper.assertTrue(cell.getZ() == plan.antechamberBox().minZ() - 1, "封印门紧贴前厅北墙" + at);
                helper.assertTrue(VaultLayout.coreCandidatesFromSeal(cell).contains(plan.core()), "封印石 → 核心的候选里有核心" + at);
            }
            // 碑 → 核心：从碑底往核心那一列往下找得到
            BlockPos column = plan.stele(0).offset(VaultLayout.STELE_TO_CORE_DX, 0, VaultLayout.STELE_TO_CORE_DZ);
            int dy = column.getY() - plan.core().getY();
            helper.assertTrue(column.getX() == plan.core().getX() && column.getZ() == plan.core().getZ()
                    && dy >= 1 && dy <= VaultLayout.STELE_PROBE_DEPTH, "碑 → 核心那一列、往下 1~" + VaultLayout.STELE_PROBE_DEPTH + " 格" + at);
            // 台阶：最后一级落在前厅地上，相邻两级只差一个水平方向、低一格，不压在祭座 / 锁孔上
            int steps = plan.stairSteps();
            int[] last = VaultLayout.ringCell(steps - 1);
            BlockPos lastStep = new BlockPos(plan.ox() + last[0], plan.ground() - steps, plan.oz() + last[1]);
            helper.assertTrue(lastStep.getY() == plan.floorY() + 1 && ante.isInside(lastStep), "最后一级台阶在前厅地上：" + lastStep + at);
            for (int s = 1; s < steps; s++) {
                int[] a = VaultLayout.ringCell(s - 1);
                int[] b = VaultLayout.ringCell(s);
                helper.assertTrue(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == 1, "相邻台阶只差一格" + at);
                Direction facing = VaultLayout.stairFacing(s);
                helper.assertTrue(facing.getStepX() == a[0] - b[0] && facing.getStepZ() == a[1] - b[1], "台阶朝上一级" + at);
                BlockPos stepPos = new BlockPos(plan.ox() + b[0], plan.ground() - 1 - s, plan.oz() + b[1]);
                helper.assertFalse(pedestals.contains(stepPos) || stepPos.equals(plan.keyhole()), "台阶压在谜题方块上" + at);
            }
            // 拼件
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            SealedVaultStructure.addPieces(builder, plan, seed);
            List<StructurePiece> pieces = builder.build().pieces();
            helper.assertTrue(pieces.size() == 4, "四个拼件" + at);
            for (StructurePiece piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                helper.assertTrue(bounds.isInside(box.minX(), box.minY(), box.minZ()) && bounds.isInside(box.maxX(), box.maxY(), box.maxZ()),
                        "拼件在总包围盒里：" + box + at);
            }
            VaultPieces.Antechamber antechamber = (VaultPieces.Antechamber) pieces.get(0);
            VaultPieces.Chamber chamber = (VaultPieces.Chamber) pieces.get(2);
            helper.assertTrue(antechamber.puzzle() == chamber.puzzle(), "前厅和宝库室用同一个谜题种子" + at);
            helper.assertTrue(antechamber.keyhole().equals(plan.keyhole()) && chamber.core().equals(plan.core()), "拼件里算的锁孔、核心和布局一致" + at);
            // 谜题：碑文四行能还原出顺序，祭座四种元素各一
            VaultPuzzle puzzle = VaultPuzzle.of(chamber.puzzle());
            Set<VaultElement> carved = new HashSet<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                carved.add(puzzle.elementAt(side));
            }
            helper.assertTrue(carved.size() == 4, "四座祭座各刻一种元素" + at);
            VaultElement[] lineElements = new VaultElement[4];
            int[] lineOrdinals = new int[4];
            for (int line = 0; line < 4; line++) {
                String key = puzzle.riddleKey(line);
                String[] parts = key.substring("message.essentialcraft5.vault.riddle.".length()).split("\\.");
                lineElements[line] = VaultElement.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT));
                lineOrdinals[line] = List.of(VaultPuzzle.ORDINALS).indexOf(parts[1]);
            }
            int[] solved = VaultPuzzle.solveFromRiddle(lineElements, lineOrdinals);
            helper.assertTrue(solved != null && java.util.Arrays.equals(solved, puzzle.order()), "碑文应能还原出顺序" + at);
            firstElements.add(puzzle.order()[0]);
            northElements.add(puzzle.elementAt(Direction.NORTH).getSerializedName());
        }
        helper.assertTrue(firstElements.size() == 4 && northElements.size() == 4, "300 座里第一步、北边祭座的元素四种都应出现过");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void vaultPiecesRoundTripNbt(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        VaultLayout.Plan plan = VaultLayout.plan(100, 90, 200, -64);
        List<StructurePiece> pieces = List.of(VaultPieces.Antechamber.of(plan, 11L, -123456789L), VaultPieces.Chamber.of(plan, 12L, -123456789L),
                VaultPieces.Shaft.of(plan, 13L), VaultPieces.Stele.of(plan, 14L));
        for (StructurePiece piece : pieces) {
            CompoundTag tag = piece.createTag(context);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            StructurePieceType type = BuiltInRegistries.STRUCTURE_PIECE.get(id);
            helper.assertTrue(type != null, "拼件类型没注册：" + id);
            StructurePiece loaded = type.load(context, tag);
            helper.assertTrue(loaded.getClass() == piece.getClass() && loaded.getBoundingBox().equals(piece.getBoundingBox()), "读回来同类同包围盒：" + id);
            helper.assertTrue(loaded.createTag(context).equals(tag), "存读档相同：" + id);
            if (loaded instanceof VaultPieces.Antechamber a) {
                helper.assertTrue(a.puzzle() == -123456789L, "前厅的谜题种子读回来");
            } else if (loaded instanceof VaultPieces.Chamber c) {
                helper.assertTrue(c.puzzle() == -123456789L && c.core().equals(plan.core()), "宝库室的谜题种子、核心位置读回来");
            } else if (loaded instanceof VaultPieces.Stele s) {
                helper.assertTrue(s.groundY() == 90 && s.steleBase().equals(plan.stele(0)), "平台的地面、碑的位置读回来");
            } else if (loaded instanceof HoannaStructurePiece h) {
                helper.assertTrue(h.groundY() == 90, "井的地面读回来");
            }
        }
        helper.succeed();
    }

    // ================================================================== 群系与结构集

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultGeneratesOnlyInVaultBiomes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HoannaTestWorld.Resolved hoanna = HoannaTestWorld.resolve(server);
        Structure vault = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EssentialCraft5.id("sealed_vault"));
        helper.assertTrue(vault != null, "注册表里要有 sealed_vault");
        DimensionType dimension = hoanna.stem().type().value();
        LevelHeightAccessor height = LevelHeightAccessor.create(dimension.minY(), dimension.height());
        int seaLevel = hoanna.settings().seaLevel();
        int inside = 0;
        int outside = 0;
        for (int i = 0; i < 900 && (inside < 3 || outside < 3); i++) {
            ChunkPos chunk = new ChunkPos((i % 30) * 17 - 250, (i / 30) * 17 - 250);
            Holder<Biome> biome = hoanna.biomeSource().getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(seaLevel),
                    QuartPos.fromBlock(chunk.getMiddleBlockZ()), hoanna.randomState().sampler());
            boolean allowed = vault.biomes().contains(biome);
            if (allowed ? inside >= 3 : outside >= 3) {
                continue;
            }
            StructureStart start = vault.generate(server.registryAccess(), hoanna.generator(), hoanna.biomeSource(), hoanna.randomState(),
                    server.getStructureManager(), hoanna.seed(), chunk, 0, height, vault.biomes()::contains);
            if (allowed) {
                helper.assertTrue(start.isValid(), "沙漠 / 恐惧之地的区块应生成宝库：" + chunk + " " + biome.unwrapKey());
                BoundingBox box = start.getBoundingBox();
                helper.assertTrue(start.getPieces().size() == 4 && box.getXSpan() <= 24 && box.getZSpan() <= 24, "四个拼件、水平 ≤ 24 × 24：" + box);
                VaultPieces.Shaft shaft = (VaultPieces.Shaft) start.getPieces().get(1);
                int ground = shaft.groundY();
                int floor = start.getPieces().get(0).getBoundingBox().minY();
                helper.assertTrue(ground - floor == VaultLayout.depthFor(ground), "前厅地板 = 地面 − 深度：地面 " + ground + "，地板 " + floor);
                inside++;
            } else {
                helper.assertFalse(start.isValid(), "别的群系不该生成宝库：" + chunk + " " + biome.unwrapKey());
                outside++;
            }
        }
        helper.assertTrue(inside >= 1 && outside >= 1, "抽样里两种区块都应碰到：群系对 " + inside + "，不对 " + outside);
        // 结构集只在海纳尔：主世界的结构放置状态里没有 sealed_vault
        ResourceLocation set = EssentialCraft5.id("sealed_vault");
        boolean inOverworld = helper.getLevel().getServer().overworld().getChunkSource().getGeneratorState().possibleStructureSets().stream()
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(set)).orElse(false));
        helper.assertFalse(inOverworld, "主世界不该有 sealed_vault 结构集");
        boolean inHoanna = hoanna.structureState().possibleStructureSets().stream()
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(set)).orElse(false));
        helper.assertTrue(inHoanna, "海纳尔的结构放置状态里应有 sealed_vault 结构集");
        helper.succeed();
    }

    // ================================================================== 端到端摆放

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void placeVaultEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 0, 0x5EA1EDL);
        VaultLayout.Plan plan = v.plan;
        VaultCoreBlockEntity core = v.core;
        for (int i = 0; i < VaultLayout.STELE_HEIGHT; i++) {
            helper.assertTrue(level.getBlockState(plan.stele(i)).is(ECSealedVault.ELDER_STELE.get()), "先辈碑第 " + i + " 块");
        }
        helper.assertFalse(level.getBlockState(plan.stele(VaultLayout.STELE_HEIGHT)).is(ECSealedVault.ELDER_STELE.get()), "碑只有 5 块高");
        helper.assertTrue(level.getBlockState(plan.keyhole()).is(ECSealedVault.VAULT_KEYHOLE.get()), "前厅中央是锁孔");
        Set<VaultElement> carved = new HashSet<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState pedestal = level.getBlockState(plan.pedestal(side));
            helper.assertTrue(pedestal.is(ECSealedVault.VAULT_PEDESTAL.get()), "祭座 " + side);
            helper.assertTrue(pedestal.getValue(VaultPedestalBlock.ELEMENT) == core.puzzle().elementAt(side),
                    "祭座刻的元素应和核心里的谜题一致：" + side);
            helper.assertTrue(VaultCoreBlockEntity.pedestalSide(level, plan.pedestal(side)) == side, "祭座找得到锁孔、认得出自己在哪一侧");
            carved.add(pedestal.getValue(VaultPedestalBlock.ELEMENT));
        }
        helper.assertTrue(carved.size() == 4, "四座祭座四种元素");
        for (BlockPos cell : plan.doorCells()) {
            BlockState seal = level.getBlockState(cell);
            helper.assertTrue(seal.is(ECSealedVault.VAULT_SEAL.get()) && !seal.getValue(VaultSealBlock.OPEN), "封印石关着：" + cell);
            helper.assertTrue(VaultCoreBlockEntity.fromSeal(level, cell) == core, "封印石找得到核心");
        }
        helper.assertTrue(VaultCoreBlockEntity.fromStele(level, plan.stele(3)) == core, "碑找得到核心");
        helper.assertTrue(VaultCoreBlockEntity.fromKeyhole(level, plan.keyhole()) == core, "锁孔找得到核心");
        helper.assertTrue(level.getBlockState(plan.core().above()).isAir() && level.getBlockState(plan.keyhole().above()).isAir(),
                "宝库室、前厅里面是空的");
        int stairs = 0;
        for (int s = 0; s < plan.stairSteps(); s++) {
            int[] cell = VaultLayout.ringCell(s);
            BlockState state = level.getBlockState(new BlockPos(plan.ox() + cell[0], plan.ground() - 1 - s, plan.oz() + cell[1]));
            if (state.is(Blocks.DEEPSLATE_TILE_STAIRS) && state.getValue(StairBlock.FACING) == VaultLayout.stairFacing(s)) {
                stairs++;
            }
        }
        helper.assertTrue(stairs == plan.stairSteps(), "螺旋楼梯每一级都在、朝向对：" + stairs + " / " + plan.stairSteps());
        helper.assertTrue(level.getBlockState(new BlockPos(plan.ox(), plan.floorY() + 1, plan.oz())).is(com.flameforge.essentialcraft5.registry.ECMaterials.FORTIFIED_STONE.get()),
                "井中间的柱子落到前厅地上");
        // 谜题在 NBT 里存读档
        CompoundTag saved = core.saveWithoutMetadata();
        VaultCoreBlockEntity copy = new VaultCoreBlockEntity(plan.core(), ECSealedVault.VAULT_CORE.get().defaultBlockState());
        copy.load(saved);
        helper.assertTrue(copy.puzzle().equals(core.puzzle()) && copy.puzzleSeed() == core.puzzleSeed(), "核心的谜题存读档相同");
        helper.succeed();
    }

    // ================================================================== 解谜

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultSolveListsOnlyAntechamber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 1, 0xA11CEL);
        ServerPlayer solver = player(helper, "vault_solver", v.plan.keyhole().south());
        ServerPlayer friend = player(helper, "vault_friend", v.plan.keyhole().east());
        ServerPlayer creative = player(helper, "vault_creative", v.plan.keyhole().west());
        creative.setGameMode(GameType.CREATIVE);
        ServerPlayer digger = player(helper, "vault_digger", v.plan.core().east(2));
        ServerPlayer outside = player(helper, "vault_outside", v.plan.stele(0).east(2));
        try {
            VaultCoreBlockEntity.setTestOverrides(1_000L, 1_000L, true, List.of(solver, friend, creative, digger, outside));
            helper.assertTrue(use(level, solver, v.plan.keyhole(), ley(1)) == InteractionResult.CONSUME, "锁孔右键应被处理");
            helper.assertTrue(solver.getMainHandItem().isEmpty() && v.core.keyed(), "地脉结晶放进锁孔、扣掉");
            helper.assertTrue(level.getBlockState(v.plan.keyhole()).getValue(VaultKeyholeBlock.KEYED), "锁孔亮了");
            for (int k = 0; k < 4; k++) {
                VaultElement element = v.core.puzzle().expected(k);
                Direction side = sideOf(v.core.puzzle(), element);
                ItemStack crystals = new ItemStack(element.crystal(), 3);
                solver.setItemInHand(InteractionHand.MAIN_HAND, crystals);
                use(level, solver, v.plan.pedestal(side), crystals);
                helper.assertTrue(crystals.getCount() == 2, "每座只收 1 个（默认）：" + element);
                if (k < 3) {
                    helper.assertTrue(v.core.step() == k + 1, "第 " + (k + 1) + " 座放满");
                    helper.assertTrue(level.getBlockState(v.plan.pedestal(side)).getValue(VaultPedestalBlock.FILLED), "放满的祭座亮起来");
                }
            }
            helper.assertTrue(v.core.step() == 0 && !v.core.keyed(), "四座放满后答对，这一次清空");
            for (BlockPos cell : v.plan.doorCells()) {
                helper.assertTrue(level.getBlockState(cell).getValue(VaultSealBlock.OPEN), "答对后封印门开：" + cell);
                helper.assertTrue(level.getBlockTicks().hasScheduledTick(cell, ECSealedVault.VAULT_SEAL.get()), "开门时排了关门的计划刻");
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.assertFalse(level.getBlockState(v.plan.pedestal(side)).getValue(VaultPedestalBlock.FILLED), "答对后祭座熄掉，等下一次");
            }
            helper.assertTrue(v.core.isUnlocked(solver.getUUID()) && v.core.isUnlocked(friend.getUUID()), "前厅里的两名生存玩家进名单");
            helper.assertFalse(v.core.isUnlocked(creative.getUUID()), "创造模式的不进名单");
            helper.assertFalse(v.core.isUnlocked(digger.getUUID()), "挖墙进宝库室的不进名单");
            helper.assertFalse(v.core.isUnlocked(outside.getUUID()), "地面上的不进名单");
            helper.assertTrue(v.core.unlockedCount() == 2, "名单上只有两人");
            // 领奖：每人一份、两人各一份、不在名单上的不给
            helper.assertTrue(v.core.claim(digger) == ClaimResult.NOT_LISTED && count(digger, ECSealedVault.ELDER_REMNANT.get()) == 0,
                    "挖墙进来的右键核心不给");
            use(level, solver, v.plan.core(), ItemStack.EMPTY);
            helper.assertTrue(v.core.hasClaimed(solver.getUUID()) && count(solver, ECSealedVault.ELDER_REMNANT.get()) == 1, "解谜的人领到一份，含先辈残核");
            helper.assertTrue(v.core.claim(solver) == ClaimResult.ALREADY_CLAIMED && count(solver, ECSealedVault.ELDER_REMNANT.get()) == 1, "同一人领第二次不给");
            helper.assertTrue(v.core.claim(friend) == ClaimResult.REWARDED && count(friend, ECSealedVault.ELDER_REMNANT.get()) == 1, "同伴也领到自己那一份");
            // 存读档后名单照留
            VaultCoreBlockEntity copy = new VaultCoreBlockEntity(v.plan.core(), ECSealedVault.VAULT_CORE.get().defaultBlockState());
            copy.load(v.core.saveWithoutMetadata());
            helper.assertTrue(copy.isUnlocked(friend.getUUID()) && copy.hasClaimed(friend.getUUID()) && !copy.isUnlocked(digger.getUUID()), "名单存读档");
        } finally {
            VaultCoreBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultWrongRefundsHalfBacklashGuards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 2, 0xBAD5EEDL);
        ServerPlayer player = player(helper, "vault_wrong", v.plan.keyhole().south());
        List<CatacombRevenantEntity> spawned = new ArrayList<>();
        try {
            ECSealedVault.Config.setTestOverride(ECSealedVault.Config.CRYSTALS_PER_PEDESTAL, 2);
            ECSealedVault.Config.setTestOverride(ECSealedVault.Config.LOCKOUT_FAILURES, 0);
            VaultCoreBlockEntity.setTestOverrides(10_000L, 5_000L, false, List.of(player));
            VaultPuzzle puzzle = v.core.puzzle();
            // 前两座放对（各 2 个），第三步放到最后那座上：放进去 2 + 2 + 2 = 6 个，退 3 个
            use(level, player, v.plan.keyhole(), ley(1));
            int[] before = new int[4];
            for (int k = 0; k < 2; k++) {
                VaultElement element = puzzle.expected(k);
                ItemStack stack = new ItemStack(element.crystal(), 2);
                use(level, player, v.plan.pedestal(sideOf(puzzle, element)), stack);
                helper.assertTrue(stack.isEmpty(), "放对的祭座收 2 个");
            }
            VaultElement wrong = puzzle.expected(3);
            ItemStack wrongStack = new ItemStack(wrong.crystal(), 5);
            player.setHealth(player.getMaxHealth());
            float maxHealth = player.getMaxHealth();
            use(level, player, v.plan.pedestal(sideOf(puzzle, wrong)), wrongStack);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(wrongStack.getCount() == 3, "放错的那座也拿走 2 个（算这一次的花费）");
            int refunded = 0;
            for (VaultElement element : VaultElement.values()) {
                refunded += count(player, element.crystal()) - before[element.ordinal()];
            }
            helper.assertTrue(refunded == 3, "放进去 6 个退一半（3 个），实际退 " + refunded);
            helper.assertTrue(count(player, puzzle.expected(0).crystal()) == 2 && count(player, puzzle.expected(1).crystal()) == 1,
                    "先退顺序靠前的：第一种 2 个、第二种 1 个");
            helper.assertTrue(Math.abs(player.getHealth() - maxHealth * 0.8F) < 0.01F, "反噬 = 当前最大生命 20%：剩 " + player.getHealth());
            helper.assertTrue(v.core.step() == 0 && !v.core.keyed() && v.core.spentTotal() == 0, "答错后这一次清空");
            helper.assertFalse(level.getBlockState(v.plan.keyhole()).getValue(VaultKeyholeBlock.KEYED), "锁孔里的结晶不退、锁孔熄掉");
            List<CatacombRevenantEntity> guards = v.core.lastGuards();
            spawned.addAll(guards);
            helper.assertTrue(guards.size() == 2, "第一次答错召 2 只守卫，实际 " + guards.size());
            for (CatacombRevenantEntity guard : guards) {
                helper.assertTrue(guard.isAddedToWorld() && !guard.isRemoved() && guard.level() == level, "守卫加进了世界");
                helper.assertTrue(SpawnOrigin.of(guard) == SpawnOrigin.Origin.SUMMONED, "守卫记成召唤来的（不掉关键物品）");
                helper.assertTrue(guard.getTarget() == player, "守卫盯着放错的人");
                helper.assertTrue(v.plan.antechamberBox().inflatedBy(1).isInside(guard.blockPosition()), "守卫在前厅里：" + guard.blockPosition());
            }
            // 60 秒内再错：只反噬不召
            VaultCoreBlockEntity.setTestOverrides(10_000L + 1_199L, 5_000L, false, List.of(player));
            failOnce(level, v, player);
            helper.assertTrue(v.core.lastGuards().isEmpty(), "60 秒内再错不召守卫");
            // 满 60 秒：再召
            VaultCoreBlockEntity.setTestOverrides(10_000L + 1_200L, 5_000L, false, List.of(player));
            failOnce(level, v, player);
            spawned.addAll(v.core.lastGuards());
            helper.assertTrue(v.core.lastGuards().size() == 2, "满 60 秒再错又召 2 只");
            // 和平难度：冷却到了也不召
            VaultCoreBlockEntity.setTestOverrides(10_000L + 2_400L, 5_000L, true, List.of(player));
            failOnce(level, v, player);
            helper.assertTrue(v.core.lastGuards().isEmpty(), "和平难度不召守卫");
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "lockout_failures = 0 时不封锁");
        } finally {
            ECSealedVault.Config.clearTestOverrides();
            VaultCoreBlockEntity.clearTestOverrides();
            spawned.forEach(Entity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultNeedsKeyAndMatchingCrystal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 3, 0xC0FFEEL);
        ServerPlayer player = player(helper, "vault_key", v.plan.keyhole().north());
        try {
            VaultCoreBlockEntity.setTestOverrides(1_000L, 1_000L, true, List.of(player));
            VaultPuzzle puzzle = v.core.puzzle();
            VaultElement first = puzzle.expected(0);
            Direction firstSide = sideOf(puzzle, first);
            ItemStack right = new ItemStack(first.crystal(), 4);
            helper.assertTrue(v.core.usePedestal(player, right, firstSide) == PedestalResult.NO_KEY && right.getCount() == 4,
                    "锁孔空着时祭座不收晶石、不扣");
            helper.assertTrue(v.core.usePedestal(player, ItemStack.EMPTY, firstSide) == PedestalResult.INSPECTED, "空手只看纹样");
            helper.assertTrue(v.core.useKeyhole(player, new ItemStack(first.crystal(), 1)) == KeyResult.NEED_CRYSTAL, "锁孔只收地脉结晶");
            ItemStack key = ley(3);
            helper.assertTrue(v.core.useKeyhole(player, key) == KeyResult.ACCEPTED && key.getCount() == 2, "锁孔收 1 颗");
            helper.assertTrue(v.core.useKeyhole(player, key) == KeyResult.ALREADY && key.getCount() == 2, "已经有一颗了不再收");
            VaultElement other = puzzle.expected(1);
            ItemStack mismatch = new ItemStack(other.crystal(), 4);
            helper.assertTrue(v.core.usePedestal(player, mismatch, firstSide) == PedestalResult.WRONG_ITEM && mismatch.getCount() == 4,
                    "拿错元素的魔晶：不收、不扣");
            helper.assertTrue(v.core.failCount(player.getUUID()) == 0 && v.core.keyed(), "拿错元素不算答错、锁孔还在");
            helper.assertTrue(v.core.usePedestal(player, right, firstSide) == PedestalResult.FILLED && right.getCount() == 3, "放对：收 1 个");
            helper.assertTrue(v.core.usePedestal(player, right, firstSide) == PedestalResult.ALREADY_FILLED && right.getCount() == 3, "放满的不再收");
        } finally {
            VaultCoreBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultLockoutAfterThreeFails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 4, 0xD00DL);
        ServerPlayer player = player(helper, "vault_locked", v.plan.keyhole().south());
        try {
            ECSealedVault.Config.setTestOverride(ECSealedVault.Config.GUARD_COUNT, 0);
            VaultCoreBlockEntity.setTestOverrides(1_000L, 5_000L, false, List.of(player));
            failOnce(level, v, player);
            failOnce(level, v, player);
            helper.assertTrue(v.core.failCount(player.getUUID()) == 2 && v.core.lockRemaining(level, player.getUUID()) == 0L, "连错 2 次还没封锁");
            // 答对清零
            solve(level, v, player);
            helper.assertTrue(v.core.failCount(player.getUUID()) == 0, "答对后连错次数清零");
            failOnce(level, v, player);
            failOnce(level, v, player);
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "清零后再错 2 次不封锁");
            failOnce(level, v, player);
            long remaining = v.core.lockRemaining(level, player.getUUID());
            helper.assertTrue(remaining == ECSealedVault.Config.DEFAULT_LOCKOUT_TICKS, "连错 3 次封锁一个游戏日：剩 " + remaining);
            ItemStack key = ley(1);
            helper.assertTrue(v.core.useKeyhole(player, key) == KeyResult.LOCKED && key.getCount() == 1, "封锁中锁孔不收结晶");
            VaultElement first = v.core.puzzle().expected(0);
            ItemStack crystal = new ItemStack(first.crystal(), 1);
            helper.assertTrue(v.core.usePedestal(player, crystal, sideOf(v.core.puzzle(), first)) == PedestalResult.LOCKED && crystal.getCount() == 1,
                    "封锁中祭座不收晶石");
            // 拨主世界时间：过半天还封着，过一天解封
            VaultCoreBlockEntity.setTestOverrides(1_000L, 5_000L + 12_000L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 12_000L, "过半天还剩半天");
            VaultCoreBlockEntity.setTestOverrides(1_000L, 5_000L + 24_000L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "过一天解封");
            helper.assertTrue(v.core.useKeyhole(player, key) == KeyResult.ACCEPTED, "解封后又能试");
            // 时间被往回拨：剩余最多按满额
            failOnce(level, v, player);
            failOnce(level, v, player);
            failOnce(level, v, player);
            VaultCoreBlockEntity.setTestOverrides(1_000L, 0L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == ECSealedVault.Config.DEFAULT_LOCKOUT_TICKS, "往回拨时间封锁最多按一天算");
            // 往回拨以后照 gameTime 倒数（日内时间要追很久才追得上）：过 6 000 刻剩 18 000，过满一天解封
            VaultCoreBlockEntity.setTestOverrides(1_000L + 6_000L, 0L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 18_000L, "往回拨以后按 gameTime 倒数：剩 " + v.core.lockRemaining(level, player.getUUID()));
            VaultCoreBlockEntity.setTestOverrides(1_000L + 24_000L, 0L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "往回拨以后 gameTime 走满一天解封");
            helper.assertTrue(v.core.useKeyhole(player, ley(1)) == KeyResult.ACCEPTED, "解封后又能试");
            // 关了昼夜循环（日内时间一直是 6 000）：gameTime 走满一天解封
            long frozenDay = 6_000L;
            long lockedAt = 50_000L;
            VaultCoreBlockEntity.setTestOverrides(lockedAt, frozenDay, false, List.of(player));
            failOnce(level, v, player);
            failOnce(level, v, player);
            failOnce(level, v, player);
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == ECSealedVault.Config.DEFAULT_LOCKOUT_TICKS, "日夜锁住时封锁一天");
            VaultCoreBlockEntity.setTestOverrides(lockedAt + 23_999L, frozenDay, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 1L, "日夜锁住时照 gameTime 倒数：差 1 刻");
            // 存读档：开始的 gameTime 存进去了；没有这个键的老记录只按日内时间
            CompoundTag saved = v.core.saveWithoutMetadata();
            v.core.load(saved.copy());
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 1L, "读档后照旧差 1 刻");
            CompoundTag legacy = saved.copy();
            net.minecraft.nbt.ListTag list = legacy.getList(VaultCoreBlockEntity.TAG_FAILS, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                list.getCompound(i).remove("start");
            }
            v.core.load(legacy);
            VaultCoreBlockEntity.setTestOverrides(lockedAt + 24_000L, frozenDay, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == ECSealedVault.Config.DEFAULT_LOCKOUT_TICKS, "老记录（没有开始的 gameTime）只按日内时间");
            v.core.load(saved.copy());
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "日夜锁住时 gameTime 走满一天解封");
            helper.assertTrue(v.core.useKeyhole(player, ley(1)) == KeyResult.ACCEPTED, "解封后又能试");
        } finally {
            ECSealedVault.Config.clearTestOverrides();
            VaultCoreBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    /**
     * 封锁也按主世界 gameTime 到期：锁了日夜循环（日内时间不动）过半天 gameTime 剩半天、满一天解封；
     * 时间被往回拨（日内时间变小）时剩余按满额算，并且照 gameTime 倒数，满一天照样解封；读档后照旧
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultLockoutGameTimeClock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 7, 0xD1CEL);
        ServerPlayer player = player(helper, "vault_clock", v.plan.keyhole().south());
        long lockout = ECSealedVault.Config.DEFAULT_LOCKOUT_TICKS;
        try {
            ECSealedVault.Config.setTestOverride(ECSealedVault.Config.GUARD_COUNT, 0);
            VaultCoreBlockEntity.setTestOverrides(100_000L, 5_000L, false, List.of(player));
            failOnce(level, v, player);
            failOnce(level, v, player);
            failOnce(level, v, player);
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == lockout, "前提：封锁一天");
            VaultCoreBlockEntity.setTestOverrides(100_000L + lockout / 2, 5_000L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == lockout / 2, "日内时间不动、gameTime 过半天：剩半天");
            CompoundTag saved = v.core.saveWithoutMetadata();
            VaultCoreBlockEntity.setTestOverrides(100_000L + lockout, 5_000L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "锁了日夜循环：满一天 gameTime 解封");

            v.core.load(saved);
            VaultCoreBlockEntity.setTestOverrides(100_000L + lockout / 2, 5_000L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == lockout / 2, "读档后照旧按 gameTime 算");

            VaultCoreBlockEntity.setTestOverrides(200_000L, 5_000L, false, List.of(player));
            failOnce(level, v, player);
            failOnce(level, v, player);
            failOnce(level, v, player);
            VaultCoreBlockEntity.setTestOverrides(200_000L + 1_000L, 0L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == lockout - 1_000L, "往回拨：剩余照 gameTime 倒数");
            VaultCoreBlockEntity.setTestOverrides(200_000L + lockout, 0L, false, List.of(player));
            helper.assertTrue(v.core.lockRemaining(level, player.getUUID()) == 0L, "往回拨以后满一天 gameTime 照样解封");
        } finally {
            ECSealedVault.Config.clearTestOverrides();
            VaultCoreBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultFakePlayerRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 5, 0xFA4EL);
        Player fake = ProtectionFrameworkTests.fakePlayer(helper);
        fake.moveTo(Vec3.atBottomCenterOf(v.plan.keyhole().south()));
        ItemStack key = ley(2);
        fake.setItemInHand(InteractionHand.MAIN_HAND, key);
        helper.assertTrue(use(level, fake, v.plan.keyhole(), key) == InteractionResult.PASS && key.getCount() == 2 && !v.core.keyed(),
                "假玩家右键锁孔没反应、不扣结晶");
        VaultElement first = v.core.puzzle().expected(0);
        ItemStack crystal = new ItemStack(first.crystal(), 2);
        fake.setItemInHand(InteractionHand.MAIN_HAND, crystal);
        helper.assertTrue(use(level, fake, v.plan.pedestal(sideOf(v.core.puzzle(), first)), crystal) == InteractionResult.PASS && crystal.getCount() == 2,
                "假玩家右键祭座没反应");
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(use(level, fake, v.plan.core(), ItemStack.EMPTY) == InteractionResult.PASS && !v.core.hasClaimed(fake.getUUID()),
                "假玩家右键核心没反应");
        helper.assertTrue(use(level, fake, v.plan.doorCells().get(0), ItemStack.EMPTY) == InteractionResult.PASS
                && !level.getBlockState(v.plan.doorCells().get(0)).getValue(VaultSealBlock.OPEN), "假玩家右键封印石没反应");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public static void vaultSealOpensForListOrInside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Placed v = place(helper, 6, 0x5EA15L);
        ServerPlayer member = player(helper, "vault_member", v.plan.keyhole().south());
        ServerPlayer stranger = player(helper, "vault_stranger", v.plan.keyhole().north(2));
        ServerPlayer trapped = player(helper, "vault_trapped", v.plan.core().west(2));
        BlockPos seal = v.plan.doorCells().get(4);
        try {
            VaultCoreBlockEntity.setTestOverrides(1_000L, 1_000L, true, List.of(member));
            solve(level, v, member);
            helper.assertTrue(v.core.isUnlocked(member.getUUID()), "解谜的人进名单");
            closeDoor(level, v);
            use(level, stranger, seal, ItemStack.EMPTY);
            helper.assertFalse(level.getBlockState(seal).getValue(VaultSealBlock.OPEN), "不在名单上的人在前厅右键封印石不开");
            use(level, member, seal, ItemStack.EMPTY);
            helper.assertTrue(level.getBlockState(seal).getValue(VaultSealBlock.OPEN), "名单上的人右键封印石再开");
            closeDoor(level, v);
            use(level, trapped, seal, ItemStack.EMPTY);
            helper.assertTrue(level.getBlockState(seal).getValue(VaultSealBlock.OPEN), "人在宝库室里右键能开门出去");
            helper.assertFalse(v.core.isUnlocked(trapped.getUUID()) || v.core.claim(trapped) == ClaimResult.REWARDED, "开门出去不等于进名单");
        } finally {
            VaultCoreBlockEntity.clearTestOverrides();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void vaultDoorClosesByScheduledTick(GameTestHelper helper) {
        // 在测试区域里摆一个最小的门：核心在 (2, 1, 0)，门在核心南边 4 格（x 1~3、y 1~3、z 4）
        BlockPos coreRel = new BlockPos(2, 1, 0);
        helper.setBlock(coreRel, ECSealedVault.VAULT_CORE.get());
        BlockPos coreAbs = helper.absolutePos(coreRel);
        for (BlockPos cell : VaultLayout.doorCells(coreAbs)) {
            helper.getLevel().setBlock(cell, ECSealedVault.VAULT_SEAL.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        ECSealedVault.Config.setTestOverride(ECSealedVault.Config.DOOR_OPEN_TICKS, 20);
        VaultCoreBlockEntity core = (VaultCoreBlockEntity) helper.getLevel().getBlockEntity(coreAbs);
        helper.assertTrue(core != null, "核心有方块实体");
        core.openDoor(helper.getLevel());
        BlockPos blocked = VaultLayout.doorCells(coreAbs).get(1);
        BlockPos free = VaultLayout.doorCells(coreAbs).get(0);
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        stand.moveTo(Vec3.atBottomCenterOf(blocked));
        helper.getLevel().addFreshEntity(stand);
        helper.assertTrue(helper.getLevel().getBlockState(free).getValue(VaultSealBlock.OPEN), "开门");
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertFalse(helper.getLevel().getBlockState(free).getValue(VaultSealBlock.OPEN), "到点关门");
                    helper.assertTrue(helper.getLevel().getBlockState(blocked).getValue(VaultSealBlock.OPEN), "门洞里站着东西的那块先不关");
                    stand.discard();
                })
                .thenExecuteAfter(VaultSealBlock.BLOCKED_RETRY + 5, () -> {
                    helper.assertFalse(helper.getLevel().getBlockState(blocked).getValue(VaultSealBlock.OPEN), "让开以后也关上");
                    ECSealedVault.Config.setTestOverride(ECSealedVault.Config.DOOR_OPEN_TICKS, null);
                })
                .thenSucceed();
    }

    // ================================================================== 方块

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void vaultBlocksUnbreakable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Block> blocks = List.of(ECSealedVault.ELDER_STELE.get(), ECSealedVault.VAULT_PEDESTAL.get(), ECSealedVault.VAULT_KEYHOLE.get(),
                ECSealedVault.VAULT_SEAL.get(), ECSealedVault.VAULT_CORE.get());
        BlockPos probe = helper.absolutePos(new BlockPos(4, 1, 4));
        for (Block block : blocks) {
            BlockState state = block.defaultBlockState();
            helper.assertTrue(state.getDestroySpeed(level, probe) < 0F, "挖不动：" + block);
            helper.assertTrue(block.getExplosionResistance() >= ECSealedVault.PUZZLE_RESISTANCE, "炸不掉：" + block);
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "推不动：" + block);
            helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE) && state.is(BlockTags.FEATURES_CANNOT_REPLACE),
                    "凋灵、末影龙、地物都动不了：" + block);
        }
        // 真活塞推一次祭座
        BlockPos piston = new BlockPos(1, 1, 1);
        BlockPos pedestal = new BlockPos(2, 1, 1);
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(pedestal, ECSealedVault.VAULT_PEDESTAL.get());
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(6, () -> {
            helper.assertBlockPresent(ECSealedVault.VAULT_PEDESTAL.get(), pedestal);
            helper.assertFalse(helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "活塞推不动祭座、伸不出去");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void vaultRewardTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.level.storage.loot.LootTable table = level.getServer().getLootData().getLootTable(VaultCoreBlockEntity.REWARD_TABLE);
        helper.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "chests/sealed_vault_reward 应能加载");
        ServerPlayer player = player(helper, "vault_loot", new BlockPos(0, 1, 0));
        int surveys = 0;
        for (int i = 0; i < 40; i++) {
            net.minecraft.world.level.storage.loot.LootParams params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, player)
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            int remnants = 0;
            int ley = 0;
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(ECSealedVault.ELDER_REMNANT.get())) {
                    remnants += stack.getCount();
                } else if (stack.is(ECHoannaMaterials.LEY_CRYSTAL.get())) {
                    ley += stack.getCount();
                } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("torn_survey_map")) {
                    surveys++;
                }
            }
            helper.assertTrue(remnants == 1, "每份必有 1 个先辈残核，实际 " + remnants);
            helper.assertTrue(ley >= 4 && ley <= 8, "地脉结晶 4~8，实际 " + ley);
        }
        helper.assertTrue(surveys > 0 && surveys < 40, "残破勘测图 30%：40 份里 " + surveys + " 张");
        helper.succeed();
    }

    // ================================================================== 工具

    private record Placed(VaultLayout.Plan plan, VaultCoreBlockEntity core) {
    }

    /** 在主世界远处的半空摆一座（第 index 个位置），返回布局和核心 */
    private static Placed place(GameTestHelper helper, int index, long seed) {
        ServerLevel level = helper.getLevel();
        int ox = BASE_X + index * 64;
        BlockPos core = SealedVaultStructure.placeDirect(level, ox, GROUND, BASE_Z, seed);
        VaultLayout.Plan plan = VaultLayout.plan(ox, GROUND, BASE_Z, level.getMinBuildHeight());
        helper.assertTrue(core.equals(plan.core()), "核心位置和布局一致");
        if (!(level.getBlockEntity(core) instanceof VaultCoreBlockEntity be)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("摆出来的宝库没有核心：" + core);
        }
        return new Placed(plan, be);
    }

    /** 生存真玩家（过了出生无敌、满血），站在 feet（绝对坐标）那一格 */
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos feet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static ItemStack ley(int count) {
        return new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get(), count);
    }

    /** 拿着 held 右键 pos（走方块的 use） */
    private static InteractionResult use(ServerLevel level, Player player, BlockPos pos, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, hit);
    }

    private static Direction sideOf(VaultPuzzle puzzle, VaultElement element) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (puzzle.elementAt(side) == element) {
                return side;
            }
        }
        throw new IllegalStateException("谜题里没有 " + element);
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** 放 1 颗结晶，直接往第二步的祭座上放：答错一次 */
    private static void failOnce(ServerLevel level, Placed v, ServerPlayer player) {
        v.core.useKeyhole(player, ley(1));
        VaultElement wrong = v.core.puzzle().expected(1);
        PedestalResult result = v.core.usePedestal(player, new ItemStack(wrong.crystal(), 1), sideOf(v.core.puzzle(), wrong));
        if (result != PedestalResult.FAILED) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("应答错一次，实际 " + result);
        }
        player.setHealth(player.getMaxHealth());
    }

    /** 按正确顺序解一次 */
    private static void solve(ServerLevel level, Placed v, ServerPlayer player) {
        v.core.useKeyhole(player, ley(1));
        PedestalResult result = null;
        for (int k = 0; k < 4; k++) {
            VaultElement element = v.core.puzzle().expected(k);
            result = v.core.usePedestal(player, new ItemStack(element.crystal(), 1), sideOf(v.core.puzzle(), element));
        }
        if (result != PedestalResult.SOLVED) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("应答对，实际 " + result);
        }
    }

    private static void closeDoor(ServerLevel level, Placed v) {
        for (BlockPos cell : v.plan.doorCells()) {
            BlockState state = level.getBlockState(cell);
            level.setBlock(cell, state.setValue(VaultSealBlock.OPEN, false), Block.UPDATE_ALL);
        }
    }
}
