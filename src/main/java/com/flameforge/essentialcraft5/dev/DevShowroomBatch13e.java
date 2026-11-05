package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.vault.VaultKeyholeBlock;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.client.boss.CalamityHeartOverlay;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSkills;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import com.flameforge.essentialcraft5.entity.boss.seal.CalamitySeal;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.InquisitoriumOutpostStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenDistrictStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.SealedVaultStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 展示厅的第十三批 13e 部分（海纳尔结构与终章，只在 -PecShowroom13e 时跑；batch13.md §12.3 的 13e 行）。截图名都以 {@code b13e_} 开头，存成 {@code ec5_b13e_*.png}。
 * <p>
 * 三座结构不等自然生成，直接在主世界（超平坦）场景格北边摆一座：宝库用 {@link SealedVaultStructure#placeDirect}（先在它的包围盒里填满石头，
 * 前厅、井、宝库室由拼件自己挖出来）；沉城先砌一池海水再放拼件（同 {@code SunkenDistrictTests.placeSunkenEndToEnd}）；前哨放在平地上。
 * 摆法都是结构生成时用的同一套拼件，长相和自然生成的一样，只是不登记结构起点（{@code /locate} 找不到）。
 * <p>
 * <b>取景</b>：镜头位置从结构的包围盒 / 布局算，每张拍之前查一遍镜头那格是不是空气、视线方向 8 格内有没有方块（{@link #frameCheck}），
 * 不满足就打警告（13d 有四张拍成了全黑 / 埋在土里 / 对着空地）。地下的几张给夜视。
 * <p>
 * 首领都不带 AI、脸朝南：废城守望者架两座炮台、镇压红圈；渊潮领主在沉城穹顶外的水里；天灾之心护盾期四柱、四相各起一招。
 * 4 档灵魂五芒星照 {@link ECPentacle#TIER_PATTERNS} 摆实物。手册页见 {@link DevShowroom} 的 {@code batch13eGuidePages}。
 * 只写了摆法和机位，拍出来哪张不对改这里的数，不动结构和实体。
 */
final class DevShowroomBatch13e {

    private DevShowroomBatch13e() {
    }

    /** 拍完删掉的实体 */
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static boolean built;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        built = true;
        if (level.getServer().getWorldData().getDifficulty() != Difficulty.EASY) {
            // 和平难度下首领会被删掉；场景拍完 DevShowroom.finishScenes 调回和平
            level.getServer().setDifficulty(Difficulty.EASY, true);
        }
        int i = firstIndex;
        buildPentacle(level, sceneBase.apply(i++));
        buildSentry(level, sceneBase.apply(i++));
        buildHeart(level, sceneBase.apply(i++));
        // 三座结构比场景格（24 格）宽，放到场景排北边更远的地方，彼此隔 160 格
        BlockPos far = sceneBase.apply(firstIndex).offset(0, 0, -120);
        buildVault(level, far);
        buildOutpost(level, far.offset(160, 0, 0));
        buildSunken(level, far.offset(320, 0, 0));
    }

    /** 服务端：删掉生成的实体、去掉夜视（DevShowroom.finishScenes 调；没跑过 build 时什么都不做） */
    static void finish(ServerPlayer player) {
        if (!built) {
            return;
        }
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        SPAWNED.clear();
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** 客户端：场景拍完的收尾（现在没有客户端覆盖要撤，留着和别的批次对齐） */
    static void clientFinish(Minecraft mc) {
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay) {
        scene(name, prepare, eye, target, delay, null);
    }

    /** client：客户端在这张开始时跑（要开着 HUD 拍的在这里改回来） */
    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay, @Nullable Consumer<Minecraft> client) {
        Consumer<ServerPlayer> checked = player -> {
            frameCheck((ServerLevel) player.level(), name, eye, target);
            if (prepare != null) {
                prepare.accept(player);
            }
        };
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, checked, eye, target, delay, client, null));
    }

    /** 拍之前的取景检查：镜头那格要是空气（或水，水下机位），沿视线 8 格内要碰到方块或实体所在的格子；不满足只打警告 */
    static void frameCheck(ServerLevel level, String name, Vec3 eye, Vec3 target) {
        BlockPos eyePos = BlockPos.containing(eye);
        BlockState at = level.getBlockState(eyePos);
        if (!at.isAir() && !at.is(Blocks.WATER)) {
            EssentialCraft5.LOGGER.warn("[showroom] {}：镜头那格 {} 是 {}，镜头埋在方块里", name, eyePos, at);
        }
        Vec3 dir = target.subtract(eye).normalize();
        for (int step = 1; step <= 8; step++) {
            BlockState seen = level.getBlockState(BlockPos.containing(eye.add(dir.scale(step))));
            if (!seen.isAir() && !seen.is(Blocks.WATER)) {
                return;
            }
        }
        if (target.distanceTo(eye) > 8D) {
            EssentialCraft5.LOGGER.warn("[showroom] {}：视线方向 8 格内没有方块，目标在 {} 格外（远景可忽略）", name, Math.round(target.distanceTo(eye)));
        }
    }

    /** 不带 AI、不会消失、脸朝南的一只 */
    @Nullable
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos, @Nullable Consumer<T> setup) {
        T mob = type.create(level);
        if (mob == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 生成不了 {}", EntityType.getKey(type));
            return null;
        }
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        mob.setYHeadRot(0F);
        mob.setYBodyRot(0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        if (setup != null) {
            setup.accept(mob);
        }
        if (!level.addFreshEntity(mob)) {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没进世界", EntityType.getKey(type));
            return null;
        }
        SPAWNED.add(mob);
        return mob;
    }

    private static Consumer<ServerPlayer> nightVision() {
        return p -> p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false));
    }

    private static Consumer<ServerPlayer> noNightVision() {
        return p -> p.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** 同步加载包围盒碰到的区块，逐区块放拼件（同结构生成：每件只在自己的包围盒和这一区块里写） */
    private static void placePieces(ServerLevel level, List<? extends HoannaStructurePiece> pieces) {
        BoundingBox union = pieces.get(0).getBoundingBox();
        for (HoannaStructurePiece piece : pieces) {
            union = BoundingBox.encapsulatingBoxes(List.of(union, piece.getBoundingBox())).orElseThrow();
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int cx = SectionPos.blockToSectionCoord(union.minX()); cx <= SectionPos.blockToSectionCoord(union.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(union.minZ()); cz <= SectionPos.blockToSectionCoord(union.maxZ()); cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                level.getChunk(cx, cz);
                BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                        level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                for (HoannaStructurePiece piece : pieces) {
                    if (piece.getBoundingBox().intersects(chunkBox)) {
                        piece.postProcess(level, level.structureManager(), generator, RandomSource.create(chunk.toLong()), chunkBox, chunk,
                                new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()));
                    }
                }
            }
        }
    }

    private static void fill(ServerLevel level, BoundingBox box, BlockState state) {
        int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    level.setBlock(pos.set(x, y, z), state, quiet);
                }
            }
        }
    }

    // ================================================================ 4 档灵魂五芒星

    private static void buildPentacle(ServerLevel level, BlockPos base) {
        BlockPos center = base.offset(0, 1, -2);
        StructurePattern full = ECPentacle.TIER_PATTERNS.get(ECPentacle.MAX_TIER);
        put(level, center, full.offsets(ECPentacle.PLATING_RULE), ECMithriline.MITHRILINE_PLATING.get());
        put(level, center, full.offsets(ECPentacle.PALE_RULE), ECPentacle.PALE_BLOCK.get());
        put(level, center, full.offsets(ECPentacle.VOID_STONE_RULE), ECVoid.VOID_STONE.get());
        put(level, center, full.offsets(ECPentacle.DEMONIC_RULE), ECDemonic.DEMONIC_PLATING.get());
        List<Block> pillars = List.of(ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get(), ECPentacle.PALE_CRYSTAL_PILLAR.get(),
                ECCrystalPillars.VOID_CRYSTAL_PILLAR.get(), ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get(), ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get());
        for (int t = 0; t <= ECPentacle.MAX_TIER; t++) {
            put(level, center, ECPentacle.PILLAR_OFFSETS.get(t), pillars.get(t));
        }
        level.setBlock(center, ECPentacle.PLAYER_PENTACLE.get().defaultBlockState(), Block.UPDATE_ALL);
        // 斜上 45° 看四个角的暗渎柱
        scene("b13e_pentacle_tier4", null, at(center, 7.5, 6.0, 7.5), at(center, 0.5, 1.0, 0.5), 20);
        scene("b13e_pentacle_tier4_side", null, at(center, 0.5, 2.5, 9.5), at(center, 0.5, 1.5, 0.5), 10);
    }

    private static void put(ServerLevel level, BlockPos center, List<BlockPos> offsets, Block block) {
        for (BlockPos offset : offsets) {
            level.setBlock(center.offset(offset), block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ================================================================ 废城守望者 + 炮台

    private static void buildSentry(ServerLevel level, BlockPos base) {
        Vec3 feet = at(base, 0.5, 1.0, -3.5);
        TownSentryEntity sentry = spawn(level, ECTownSentry.TOWN_SENTRY.get(), feet, null);
        if (sentry == null) {
            return;
        }
        Consumer<ServerPlayer> turrets = p -> {
            while (sentry.turrets().size() < 2 && sentry.deployTurret() != null) {
                // 架满两座（炮台不存盘，场景拍完随守望者一起删）
            }
        };
        scene("b13e_town_sentry_front", turrets, at(base, 5.5, 3.5, 5.5), at(base, 0.5, 2.5, -3.5), 20);
        scene("b13e_town_sentry_suppress", turrets.andThen(p -> {
            sentry.beginSuppress();
            sentry.suppressWarning();
        }), at(base, 0.5, 9.0, 4.5), at(base, 0.5, 1.0, -3.5), 5);
    }

    // ================================================================ 天灾之心

    private static void buildHeart(ServerLevel level, BlockPos base) {
        BlockPos floor = base.offset(0, 0, -16);
        Vec3 pos = at(floor, 0.5, 7.0, 0.5);
        CalamityHeartEntity heart = spawn(level, ECCalamityHeart.CALAMITY_HEART.get(), pos, h -> h.setupArena(floor, CalamityHeartEntity.Aspect.SUN_ARRAY, null));
        if (heart == null) {
            return;
        }
        heart.syncPillars(level);
        Vec3 overview = at(floor, 20.5, 9.0, 20.5);
        Vec3 aim = at(floor, 0.5, 4.0, 0.5);
        scene("b13e_calamity_heart_shield", null, overview, aim, 20);
        skillScene(heart, "b13e_calamity_heart_prism", CalamityHeartSkills.Skill.PRISM, overview, aim, null);
        skillScene(heart, "b13e_calamity_heart_breath", CalamityHeartSkills.Skill.BREATH, at(floor, 6.5, 2.5, 8.5), aim, null);
        skillScene(heart, "b13e_calamity_heart_shockwave", CalamityHeartSkills.Skill.SHOCKWAVE, overview, aim, null);
        buildHeartFury(heart, overview, aim);
        // 熄光：拿火把 / 空手各一张（空手只看得见眼睛）
        skillScene(heart, "b13e_calamity_heart_blackout_torch", CalamityHeartSkills.Skill.BLACKOUT, at(floor, 6.5, 2.5, 8.5), aim, new ItemStack(Items.TORCH));
        scene("b13e_calamity_heart_blackout_empty", p -> p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY), at(floor, 6.5, 2.5, 8.5), aim, 10);
    }

    /**
     * 加强那一轮（tools/plan/calamity_heart_v2.md §5）：封印锁图标和熄掉光泽的装备、开场名字、两种合相技、四灾归心。名字、横幅、锁图标都画在 HUD 上，开着 HUD 拍。
     * 首领不带 AI 时不走开场、不续封印租约，这里直接发画面包、手动续一次租约（40 刻，够拍）；每张先撤掉上一张冻住的招和还在排队的横幅
     * （不撤的话这张的横幅要排在上一张后面，等到它淡入时招式的预警粒子已经散了）。
     */
    private static void buildHeartFury(CalamityHeartEntity heart, Vec3 overview, Vec3 aim) {
        ServerLevel level = (ServerLevel) heart.level();
        Consumer<Minecraft> hud = mc -> {
            mc.options.hideGui = false;
            CalamityHeartOverlay.clear();
        };
        // 封印只封生存 / 冒险模式的人：切生存、照样悬空；地震那一处（护腿、靴子）当作已解，四个锁三暗一亮
        scene("b13e_calamity_heart_seal", p -> {
            heart.heartSkills().cancelAll(level);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.setItemSlot(EquipmentSlot.CHEST, enchanted(Items.DIAMOND_CHESTPLATE, Enchantments.ALL_DAMAGE_PROTECTION));
            p.setItemSlot(EquipmentSlot.LEGS, enchanted(Items.DIAMOND_LEGGINGS, Enchantments.ALL_DAMAGE_PROTECTION));
            int selected = p.getInventory().selected;
            p.getInventory().setItem(selected, enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS));
            p.getInventory().setItem((selected + 1) % 9, enchanted(Items.DIAMOND_PICKAXE, Enchantments.BLOCK_EFFICIENCY));
            // 附魔书不封，光泽照常
            p.getInventory().setItem((selected + 2) % 9, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.MENDING, 1)));
            heart.bossRoster().add(p);
            CalamitySeal.hold(p, heart.getUUID(), CalamitySeal.ALL & ~CalamitySeal.LEGS_FEET, CalamitySeal.now(level.getServer()));
        }, overview, aim, 20, hud);
        scene("b13e_calamity_heart_title", p -> {
            CalamitySeal.release(heart.getUUID(), p.getUUID());
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().clearContent();
            S2CBossFxPacket.send(p, S2CBossFxPacket.of(S2CBossFxPacket.INTRO, heart, CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal(), 60));
        }, overview, aim, 30, hud);
        comboScene(heart, "b13e_calamity_heart_combo_eclipse", 0, overview, aim, hud);
        comboScene(heart, "b13e_calamity_heart_combo_molten_rift", 3, overview, aim, hud);
        scene("b13e_calamity_heart_converge", p -> {
            heart.heartSkills().cancelAll(level);
            heart.heartSkills().converge(level);
            S2CBossFxPacket.send(p, S2CBossFxPacket.of(S2CBossFxPacket.BANNER, heart, CalamityHeartEntity.BANNER_CONVERGE,
                    CalamityHeartEntity.Aspect.SUN_ARRAY.color()));
            for (int t = 0; t < 10; t++) {
                heart.heartSkills().tickEffects(level);
            }
        }, overview, aim, 10, hud);
        // 灾柱反噬：0 号那根脚下的红圈（反噬没有横幅）。客户端丢掉离镜头 32 格以外的粒子，镜头又要下一帧才跟到新机位，
        // 所以先拍一张灾柱近景把镜头摆过去，下一张同一机位再放
        BlockPos feet = heart.pillarFeet(0);
        if (feet != null) {
            Vec3 eye = at(feet, 6.5, 4.0, 8.5);
            Vec3 look = at(feet, 0.5, 0.5, 0.5);
            scene("b13e_calamity_heart_pillar", p -> heart.heartSkills().cancelAll(level), eye, look, 10, hud);
            scene("b13e_calamity_heart_backlash", p -> {
                heart.heartSkills().backlash(level, Vec3.atBottomCenterOf(feet));
                for (int t = 0; t < 10; t++) {
                    heart.heartSkills().tickEffects(level);
                }
            }, eye, look, 10, hud);
        }
    }

    /** 一种合相技：放招、发它的横幅、推 10 刻让预警画出来 */
    private static void comboScene(CalamityHeartEntity heart, String name, int pair, Vec3 eye, Vec3 target, Consumer<Minecraft> client) {
        scene(name, p -> {
            ServerLevel level = p.serverLevel();
            heart.heartSkills().cancelAll(level);
            heart.bossRoster().add(p);
            heart.heartSkills().combo(level, pair);
            S2CBossFxPacket.send(p, S2CBossFxPacket.of(S2CBossFxPacket.BANNER, heart, CalamityHeartEntity.BANNER_COMBO + pair,
                    CalamityHeartEntity.comboColor(pair)));
            for (int t = 0; t < 10; t++) {
                heart.heartSkills().tickEffects(level);
            }
        }, eye, target, 10, client);
    }

    private static ItemStack enchanted(Item item, Enchantment enchantment) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment, enchantment.getMaxLevel());
        return stack;
    }

    private static void skillScene(CalamityHeartEntity heart, String name, CalamityHeartSkills.Skill skill, Vec3 eye, Vec3 target, @Nullable ItemStack held) {
        scene(name, p -> {
            if (held != null) {
                p.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            }
            heart.bossRoster().add(p);
            heart.heartSkills().start(skill, p);
            // 不带 AI 时首领自己不推预警，推几刻让预警画出来；熄光要推过预警、真的熄了才拍（之后首领不走刻，一直熄着）
            for (int t = 0; t < 10 || (skill == CalamityHeartSkills.Skill.BLACKOUT && !heart.blackout() && t < 400); t++) {
                heart.heartSkills().tickEffects((ServerLevel) p.level());
            }
        }, eye, target, 8);
    }

    // ================================================================ 封印宝库

    private static void buildVault(ServerLevel level, BlockPos base) {
        // 抬高地面，前厅、宝库室埋在填满的石头里（超平坦太浅，不填的话前厅会冒出地面）
        int ground = base.getY() + 30;
        VaultLayout.Plan plan = VaultLayout.plan(base.getX(), ground, base.getZ(), level.getMinBuildHeight());
        BoundingBox bounds = plan.bounds();
        BoundingBox solid = new BoundingBox(bounds.minX() - 2, base.getY(), bounds.minZ() - 2, bounds.maxX() + 2, ground - 2, bounds.maxZ() + 2);
        for (int cx = SectionPos.blockToSectionCoord(solid.minX()); cx <= SectionPos.blockToSectionCoord(solid.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(solid.minZ()); cz <= SectionPos.blockToSectionCoord(solid.maxZ()); cz++) {
                level.getChunk(cx, cz);
            }
        }
        fill(level, solid, Blocks.STONE.defaultBlockState());
        fill(level, new BoundingBox(solid.minX(), ground - 1, solid.minZ(), solid.maxX(), ground - 1, solid.maxZ()), Blocks.SAND.defaultBlockState());
        BlockPos corePos = SealedVaultStructure.placeDirect(level, base.getX(), ground, base.getZ(), 0x13E5EA1L);
        VaultCoreBlockEntity core = VaultCoreBlockEntity.at(level, corePos);
        BlockPos stele = plan.stele(0);
        BlockPos keyhole = plan.keyhole();
        // 1 地面：平台南边 8 格、高 4 格，朝北看碑和井口
        scene("b13e_vault_surface", noNightVision(), at(stele, 0.5, 4.0, 8.5), at(stele, 0.5, 2.0, -2.5), 20);
        // 2 井：井口正上方 6 格往下看
        scene("b13e_vault_shaft", nightVision(), at(new BlockPos(plan.ox(), ground, plan.oz()), 0.5, 6.0, 1.5),
                at(new BlockPos(plan.ox(), plan.floorY(), plan.oz()), 0.5, 1.0, 0.5), 10);
        // 3 前厅：西南角朝东北看四座祭座 + 锁孔，空的和放满亮着的各一张
        Vec3 anteEye = at(keyhole, -3.5, 3.0, 3.5);
        Vec3 anteAim = at(keyhole, 0.5, 0.5, -0.5);
        scene("b13e_vault_antechamber_empty", nightVision(), anteEye, anteAim, 10);
        scene("b13e_vault_antechamber_lit", nightVision().andThen(p -> {
            level.setBlock(keyhole, level.getBlockState(keyhole).setValue(VaultKeyholeBlock.KEYED, true), Block.UPDATE_ALL);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos pedestal = plan.pedestal(side);
                BlockState state = level.getBlockState(pedestal);
                if (state.hasProperty(VaultPedestalBlock.FILLED)) {
                    level.setBlock(pedestal, state.setValue(VaultPedestalBlock.FILLED, true), Block.UPDATE_ALL);
                }
            }
        }), anteEye, anteAim, 10);
        // 4 封印门关 / 开：前厅北半朝北
        Vec3 doorEye = at(keyhole, 0.5, 2.0, -1.5);
        Vec3 doorAim = at(keyhole, 0.5, 1.5, -6.5);
        scene("b13e_vault_seal_closed", nightVision(), doorEye, doorAim, 10);
        scene("b13e_vault_seal_open", nightVision().andThen(p -> {
            if (core != null) {
                core.openDoor(level);
            }
        }), doorEye, doorAim, 10);
        // 5 宝库室：门内朝北看核心和两个侧龛
        scene("b13e_vault_chamber", nightVision(), at(corePos, 0.5, 2.0, 3.5), at(corePos, 0.5, 0.5, -0.5), 10);
    }

    // ================================================================ 审判庭前哨

    private static void buildOutpost(ServerLevel level, BlockPos base) {
        ChunkPos chunk = new ChunkPos(base);
        int ground = base.getY() + 1;
        InquisitoriumOutpostStructure.Plan plan = InquisitoriumOutpostStructure.plan(RandomSource.create(0x13E0057L), chunk, ground);
        placePieces(level, InquisitoriumOutpostStructure.createPieces(plan));
        BlockPos center = new BlockPos(plan.centerX(), plan.ground(), plan.centerZ());
        // 1 高空 45° 俯拍整座；2 平视信标座（黄昏）；3 营地近景
        scene("b13e_outpost_overview", noNightVision(), at(center, 34.5, 32.0, 34.5), at(center, 0.5, 0.0, 0.5), 30);
        scene("b13e_outpost_dais", p -> level.setDayTime(12_600L), at(center, 0.5, 2.0, 10.5), at(center, 0.5, 1.0, 0.5), 10);
        if (!plan.camps().isEmpty()) {
            InquisitoriumOutpostStructure.CampSpec camp = plan.camps().get(0);
            BlockPos campPos = new BlockPos(camp.x(), plan.ground(), camp.z());
            scene("b13e_outpost_camp", p -> level.setDayTime(6000L), at(campPos, 6.5, 4.0, 6.5), at(campPos, 0.5, 0.5, 0.5), 10);
        }
    }

    // ================================================================ 沉没城区 + 渊潮领主

    private static void buildSunken(ServerLevel level, BlockPos base) {
        int seabed = base.getY();
        int ground = seabed + 10;
        int waterTop = ground + 11;
        SunkenDistrictStructure.Layout layout = SunkenDistrictStructure.layout(RandomSource.create(0x13E5D1L), base.getX(), base.getZ());
        List<HoannaStructurePiece> pieces = SunkenDistrictStructure.createPieces(layout, ground, (x, z) -> ground);
        BoundingBox union = pieces.get(0).getBoundingBox();
        for (HoannaStructurePiece piece : pieces) {
            union = BoundingBox.encapsulatingBoxes(List.of(union, piece.getBoundingBox())).orElseThrow();
        }
        BoundingBox region = new BoundingBox(union.minX() - 3, seabed, union.minZ() - 3, union.maxX() + 3, Math.max(union.maxY(), waterTop), union.maxZ() + 3);
        for (int cx = SectionPos.blockToSectionCoord(region.minX()); cx <= SectionPos.blockToSectionCoord(region.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(region.minZ()); cz <= SectionPos.blockToSectionCoord(region.maxZ()); cz++) {
                level.getChunk(cx, cz);
            }
        }
        // 一池海水：底下石头、四周玻璃墙、上面空气
        fill(level, new BoundingBox(region.minX(), seabed, region.minZ(), region.maxX(), ground - 1, region.maxZ()), Blocks.STONE.defaultBlockState());
        fill(level, new BoundingBox(region.minX(), ground, region.minZ(), region.maxX(), waterTop, region.maxZ()), Blocks.GLASS.defaultBlockState());
        fill(level, new BoundingBox(region.minX() + 1, ground, region.minZ() + 1, region.maxX() - 1, waterTop, region.maxZ() - 1), Blocks.WATER.defaultBlockState());
        placePieces(level, pieces);
        SunkenPieces.Dome dome = (SunkenPieces.Dome) pieces.get(0);
        BlockPos altar = dome.altarPos();
        Consumer<ServerPlayer> underwater = nightVision().andThen(p -> p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 1200, 0, false, false)));
        // 1 全景：穹顶上方水面下、斜 45° 往下；2 穹顶里离祭坛 4 格；3 渊潮领主在穹顶外的水里、漩涡
        scene("b13e_sunken_overview", underwater, at(altar, 14.5, 9.0, 14.5), at(altar, 0.5, 0.0, 0.5), 30);
        scene("b13e_sunken_dome_inside", nightVision(), at(altar, 0.5, 1.6, 4.5), at(altar, 0.5, 0.8, 0.5), 10);
        Vec3 tyrantAt = at(altar, 0.5, 0, -14.5);
        tyrantAt = new Vec3(tyrantAt.x, Math.min(waterTop - 4, dome.floorY() + 4), tyrantAt.z);
        AbyssTyrantEntity tyrant = spawn(level, ECSunkenDistrict.ABYSS_TYRANT.get(), tyrantAt, null);
        if (tyrant != null) {
            Vec3 aim = tyrantAt;
            // 机位放在领主背向穹顶的一侧，往回拍领主和穹顶，前景不被穹顶外壳挡住
            scene("b13e_abyss_tyrant", underwater, aim.add(-5, 1.5, -7), aim, 10);
            scene("b13e_abyss_tyrant_vortex", underwater.andThen(p -> {
                tyrant.bossRoster().add(p);
                tyrant.startVortex();
            }), aim.add(-5, 1.5, -7), aim, 10);
        }
    }
}
