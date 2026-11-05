package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EmberForgeBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 展示厅的第七批部分（虚空与恶魔，{@link DevShowroom} 调，只在 -PecShowroom / -PecShowroom7 时跑）。
 * <p>
 * <b>场景</b>（04_scene_*，沿 X 排在方块排北边，镜头从南边朝北拍）：
 * 成形的恶魔五芒星（16 根柱子灌好 ESPE）→ 真的召唤一次（{@link DemonicPentacleBlockEntity#summon}）→ 恶魔正面 / 侧面（旁边站一个盔甲架作比例）
 * → 一只不持久的僵尸、当场 {@link DemonEntity#clearOnce}（清怪 + 过滤爆炸）→ 交易界面两种需求（物品 / 灵魂，02_gui_demon_trade*）；
 * 没成形的五芒星（暗灰符文）；余烬锻炉摆满 8 格（白天托着、覆盖成夜里在锻造）；25 种主 / 副元素的余烬元素剑挂成 5 × 5、两个盔甲架手持、第一人称手持；
 * 五种水晶柱并排（核对三份新 MTL 的浮动水晶）；灵魂五芒星 2 档、3 档（柱子灌满）和 2 档的界面（身上一个能治、一个不能治的矩阵类效果）；
 * 两个盔甲架穿满电的虚空套装 / 魔力防护作对照（虚空那个拿灵魂刻刀）；16 色恶魔护板。
 * <p>
 * <b>界面</b>（02_gui_*）：快捷栏里的本批物品图标（虚空套装的 3D 图标、刻刀、被俘灵魂、余烬元素剑）；几件物品的提示框
 * （锻好的剑、满电虚空胸甲、虚空护腿、灵魂刻刀、被俘灵魂（村民）、暗渎柱），用一个只画提示框的界面拍（不动真鼠标）。
 * 只拍第七批（-PecShowroom7）时另摆一排本批方块。
 * <p>
 * 场景里的界面（交易、五芒星）在场景阶段打开，名字以 {@code 02_} 开头原样当截图名；下一张场景开拍前把界面关掉。
 */
public final class DevShowroomDemon {

    private DevShowroomDemon() {
    }

    /** 全量展示厅里本类占几个场景位（后面的部分从 firstIndex + 这个数开始） */
    static final int SCENE_SLOTS = 9;

    private static final DyeColor[] COLORS = DyeColor.values();
    private static final double FULL_ESPE = 10_000D;
    private static final ResourceLocation VILLAGER = ResourceLocation.withDefaultNamespace("villager");

    /** 召唤出来的那只恶魔（只在服务端线程读写） */
    @Nullable
    private static DemonEntity summoned;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex, boolean ownRow) {
        buildDemonScene(level, player, sceneBase.apply(firstIndex));
        buildUnformedScene(level, sceneBase.apply(firstIndex + 1));
        buildEmberForgeScene(level, sceneBase.apply(firstIndex + 2));
        buildSwordScene(level, sceneBase.apply(firstIndex + 3));
        buildPillarScene(level, player, sceneBase.apply(firstIndex + 4));
        buildPentacleScene(level, player, sceneBase.apply(firstIndex + 5), 2);
        buildPentacleScene(level, player, sceneBase.apply(firstIndex + 6), 3);
        buildArmorScene(level, sceneBase.apply(firstIndex + 7));
        buildPlatingScene(level, sceneBase.apply(firstIndex + 8));
        buildScreens();
        if (ownRow) {
            buildOwnRow(level, player, sceneBase.apply(firstIndex + 9));
        }
    }

    // ================================================================ 小工具

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client, @Nullable Predicate<Minecraft> ready) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, ready));
    }

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    /** 关掉上一张场景里打开的界面（交易界面关掉时把放进去的东西退回） */
    private static void closeScreen(Minecraft mc) {
        if (mc.screen != null && mc.player != null) {
            mc.player.closeContainer();
        }
    }

    /** 照结构表摆出全部必需格子（护板随便染几种颜色），每格走一遍放置逻辑（水晶柱补上上面两段），最后放机器那一格 */
    private static void buildStructure(ServerLevel level, ServerPlayer player, StructurePattern pattern, BlockPos anchor) {
        int index = 0;
        BlockState center = null;
        for (var entry : pattern.rules().entrySet()) {
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isOptional() || rule.isAir()) {
                continue;
            }
            BlockState state = rule.displayState();
            if (entry.getKey().equals(BlockPos.ZERO)) {
                center = state;
                continue;
            }
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, COLORS[(index++ * 5) % COLORS.length]);
            }
            BlockPos pos = StructurePattern.toWorld(anchor, entry.getKey(), Direction.NORTH);
            level.setBlockAndUpdate(pos, state);
            state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
        }
        if (center != null) {
            level.setBlockAndUpdate(anchor, center);
            center.getBlock().setPlacedBy(level, anchor, center, player, new ItemStack(center.getBlock()));
        }
    }

    /** 结构里每根水晶柱灌满 ESPE */
    private static void fillPillars(ServerLevel level, BlockPos anchor, List<BlockPos> offsets) {
        for (BlockPos offset : offsets) {
            if (level.getBlockEntity(anchor.offset(offset)) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                pillar.getESPEStorage().setESPE(FULL_ESPE);
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {} 不是水晶柱底座", anchor.offset(offset));
            }
        }
    }

    private static ArmorStand armorStand(ServerLevel level, double x, double y, double z, float yaw) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            throw new IllegalStateException("生成不了盔甲架");
        }
        stand.moveTo(x, y, z, yaw, 0F);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setShowArms(true);
        stand.setNoBasePlate(true);
        return stand;
    }

    private static Item item(@Nullable ResourceLocation id) {
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Items.AIR : item;
    }

    // ================================================================ 恶魔

    /**
     * 成形的恶魔五芒星（照 {@link ECDemon#STRUCTURE} 逐格摆，柱子按三段柱位放米斯莱 / 淡青 / 虚空柱）。到场先拍成形的样子，
     * 再给 16 根柱子灌满 ESPE、用展示玩家真召唤一次（召唤冒的烟只有几刻，召唤后 6 刻拍），随后正面、侧面；
     * 恶魔的清怪先关掉（默认 12 格会打到别的场景），清怪那张放一只不持久、关了 AI 的僵尸（戴头盔白天不烧），当场调一次 {@link DemonEntity#clearOnce}。
     */
    private static void buildDemonScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos pentacle = base.offset(3, 0, -3);
        buildStructure(level, player, ECDemon.STRUCTURE, pentacle);
        ArmorStand scale = armorStand(level, pentacle.getX() + 3.5D, pentacle.getY(), pentacle.getZ() + 4.5D, 200F);
        scale.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        scale.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        level.addFreshEntity(scale);
        Vec3 wideEye = at(base, 3.5, 6, 6.5);
        Vec3 wideTarget = at(base, 3.5, 1, -3);
        scene("demonic_pentacle_formed", null, wideEye, wideTarget, DevShowroom.FIRST_SCENE_WAIT, null, null);
        scene("demonic_pentacle_formed_close", null, at(base, 3.5, 2.6, 1.5), at(base, 3.5, 0.6, -3), 30, null, null);
        Consumer<ServerPlayer> summon = p -> {
            fillPillars(p.serverLevel(), pentacle, ECDemon.PILLAR_OFFSETS);
            if (p.serverLevel().getBlockEntity(pentacle) instanceof DemonicPentacleBlockEntity be) {
                DemonicPentacleBlockEntity.Outcome outcome = be.summon(p);
                EssentialCraft5.LOGGER.info("[showroom] 召唤恶魔：{}（{}）", outcome.result(), outcome.message() == null ? "" : outcome.message().getString());
                summoned = outcome.demon();
                if (summoned != null) {
                    summoned.setTestClearOverrides(0, null);
                }
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {} 没有恶魔五芒星方块实体", pentacle);
            }
        };
        scene("demon_summon", summon, wideEye, wideTarget, 6, null, null);
        // 正南方 (0, 1, 3) 那根柱子正好挡在恶魔前面，镜头抬高从柱顶上面看过去
        scene("demon_front", null, at(base, 3.5, 5.6, 1.6), at(base, 3.5, 2.0, -2.5), 30, null, null);
        scene("demon_side", null, at(base, 8.5, 2.6, -1), at(base, 3.5, 1.6, -3), 15, null, null);
        Consumer<ServerPlayer> clear = p -> {
            ServerLevel server = p.serverLevel();
            MinecraftServer mcServer = p.getServer();
            if (mcServer != null) {
                mcServer.setDifficulty(Difficulty.EASY, true);
            }
            Zombie zombie = EntityType.ZOMBIE.create(server);
            if (zombie == null || summoned == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 清怪场景少了僵尸或恶魔（恶魔 {}）", summoned);
                return;
            }
            zombie.moveTo(pentacle.getX() - 2.5D, pentacle.getY(), pentacle.getZ() + 4.5D, 90F, 0F);
            zombie.setNoAi(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            server.addFreshEntity(zombie);
            var target = summoned.clearOnce(8);
            EssentialCraft5.LOGGER.info("[showroom] 恶魔清怪打中：{}，僵尸还活着 {}", target, zombie.isAlive());
        };
        scene("demon_clear", clear, at(base, 1.5, 4.5, 9.5), at(base, 2, 1, 0), 3, null, null);
        // 交易界面：创造模式的展示玩家能交易；主手清空（拿着刷怪蛋之类会先走原版的交互）
        scene("02_gui_demon_trade", p -> openTrade(p, DemonWant.item(Items.IRON_BLOCK, 8, false), at(base, 3.5, 1.5, 0)),
                null, null, 25, null, null);
        scene("02_gui_demon_trade_soul", p -> openTrade(p, DemonWant.soul(VILLAGER, 3), at(base, 3.5, 1.5, 0)),
                null, null, 25, DevShowroomDemon::closeScreen, null);
    }

    private static void openTrade(ServerPlayer player, DemonWant want, Vec3 stand) {
        if (summoned == null || !summoned.isAlive()) {
            EssentialCraft5.LOGGER.warn("[showroom] 拍交易界面时恶魔不在了（{}）", summoned);
            return;
        }
        DevShowroom.aim(player, stand, summoned.position().add(0D, 1.5D, 0D));
        player.getInventory().setItem(player.getInventory().selected, ItemStack.EMPTY);
        summoned.setWant(want);
        EssentialCraft5.LOGGER.info("[showroom] 打开交易界面：{}", summoned.interact(player, InteractionHand.MAIN_HAND));
    }

    /** 没成形的五芒星：孤零零一个，符文是不转的暗灰 */
    private static void buildUnformedScene(ServerLevel level, BlockPos base) {
        BlockPos pentacle = base.offset(2, 0, -2);
        level.setBlockAndUpdate(pentacle, ECDemon.DEMONIC_PENTACLE.get().defaultBlockState());
        scene("demonic_pentacle_unformed", null, at(base, 2.5, 2.6, 1.5), at(base, 2.5, 0.2, -2), 30, DevShowroomDemon::closeScreen, null);
    }

    // ================================================================ 余烬锻炉与余烬元素剑

    /**
     * 余烬锻炉：照结构摆好，到场时 8 格各放一件（余烬格酸蚀 / 混沌 / 腐化 / 燃烧，焦点格四种焦点，摆在格子底面正中、速度为零）。
     * 第一张覆盖成白天（托着、连线粒子）；第二张覆盖成夜里、锻一把要 72000 刻（不会真的出剑），客户端那一份进度直接设到 500，岩浆火星拉满。
     */
    private static void buildEmberForgeScene(ServerLevel level, BlockPos base) {
        BlockPos anchor = base.offset(3, 0, -3);
        int index = 0;
        for (var entry : ECEmberForge.STRUCTURE.rules().entrySet()) {
            BlockState state = entry.getValue().displayState();
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, COLORS[(index++ * 3) % COLORS.length]);
            }
            level.setBlockAndUpdate(StructurePattern.toWorld(anchor, entry.getKey(), Direction.NORTH), state);
        }
        EmberKind[] embers = {EmberKind.ACIDIC, EmberKind.CHAOS, EmberKind.CORRUPTED, EmberKind.FLAME};
        Consumer<ServerPlayer> fill = p -> {
            if (!(p.serverLevel().getBlockEntity(anchor) instanceof EmberForgeBlockEntity forge)) {
                EssentialCraft5.LOGGER.warn("[showroom] {} 没有余烬锻炉方块实体", anchor);
                return;
            }
            forge.setTestOverrides(false, 72_000);
            forge.structure().checkNow(p.serverLevel(), anchor, Direction.NORTH);
            for (int i = 0; i < EmberForgeBlockEntity.SLOT_COUNT; i++) {
                ItemStack stack = EmberForgeBlockEntity.isEmberSlot(i) ? new ItemStack(embers[i % 4].item())
                        : new ItemStack(item(ForgedSwordData.Element.SINGLE.get(i % 4).focus()));
                BlockPos cell = forge.cell(i);
                ItemEntity entity = new ItemEntity(p.serverLevel(), cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, stack, 0D, 0D, 0D);
                p.serverLevel().addFreshEntity(entity);
            }
        };
        Vec3 eye = at(base, 3.5, 4.5, 4.5);
        Vec3 target = at(base, 3.5, 1.2, -3);
        scene("ember_forge_idle", fill, eye, target, 50, null, null);
        scene("ember_forge_idle_close", null, at(base, 6.2, 2.6, -0.2), at(base, 3.5, 1.4, -3), 15, null, null);
        Consumer<ServerPlayer> forging = p -> {
            if (p.serverLevel().getBlockEntity(anchor) instanceof EmberForgeBlockEntity forge) {
                forge.setTestOverrides(true, 72_000);
                EssentialCraft5.LOGGER.info("[showroom] 余烬锻炉：{}，托着 {}", forge.status(), Integer.toBinaryString(forge.heldMask()));
            }
        };
        Consumer<Minecraft> clientProgress = mc -> {
            if (mc.level != null && mc.level.getBlockEntity(anchor) instanceof EmberForgeBlockEntity forge) {
                forge.setShowroomProgress(500);
            }
        };
        scene("ember_forge_forging", forging, eye, target, 40, clientProgress, null);
        scene("ember_forge_forging_close", null, at(base, 6.2, 2.6, -0.2), at(base, 3.5, 1.4, -3), 10, clientProgress, null);
    }

    /** 一把锻好的剑：主元素 primary、副元素 secondary，余烬 4 个（酸蚀 / 混沌 / 阴燃 / 燃烧），焦点按主元素（复合 = 四种各一） */
    static ItemStack sword(ForgedSwordData.Element primary, ForgedSwordData.Element secondary) {
        List<ResourceLocation> embers = List.of(EmberKind.ACIDIC.id(), EmberKind.CHAOS.id(), EmberKind.COMMON.id(), EmberKind.FLAME.id());
        List<ResourceLocation> foci = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            foci.add(primary == ForgedSwordData.Element.COMBINED ? ForgedSwordData.Element.SINGLE.get(i).focus() : primary.focus());
        }
        ItemStack stack = ECEmberForge.FORGED_ELEMENTAL_SWORD.get().forged(new ForgedSwordData(embers, foci, primary, secondary));
        stack.getCapability(com.flameforge.essentialcraft5.api.ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
        return stack;
    }

    /**
     * 25 种主 / 副元素的剑挂成 5 × 5（隐形展示框，石墙上；行 = 主元素，列 = 副元素，顺序同 {@link ForgedSwordData.Element}），
     * 两个盔甲架右手平举复合 / 火、火 / 水两把（看第三人称手持时两层错开、侧边不闪），第一人称拿复合 / 火那把（开 HUD）。
     */
    private static void buildSwordScene(ServerLevel level, BlockPos base) {
        ForgedSwordData.Element[] elements = ForgedSwordData.Element.values();
        for (int x = -1; x <= elements.length; x++) {
            for (int y = 0; y <= elements.length + 1; y++) {
                level.setBlockAndUpdate(base.offset(x, y, -1), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int row = 0; row < elements.length; row++) {
            for (int col = 0; col < elements.length; col++) {
                BlockPos pos = base.offset(col, elements.length - row, 0);
                ItemFrame frame = new ItemFrame(level, pos, Direction.SOUTH);
                frame.setItem(sword(elements[row], elements[col]), false);
                frame.setInvisible(true);
                level.addFreshEntity(frame);
            }
        }
        float yaw = -90F;
        ArmorStand combined = armorStand(level, base.getX() + 8.5D, base.getY(), base.getZ() + 0.5D, yaw);
        combined.setRightArmPose(new Rotations(-90F, 0F, 0F));
        combined.setItemSlot(EquipmentSlot.MAINHAND, sword(ForgedSwordData.Element.COMBINED, ForgedSwordData.Element.FIRE));
        level.addFreshEntity(combined);
        ArmorStand fire = armorStand(level, base.getX() + 11.5D, base.getY(), base.getZ() + 0.5D, yaw);
        fire.setRightArmPose(new Rotations(-90F, 0F, 0F));
        fire.setItemSlot(EquipmentSlot.MAINHAND, sword(ForgedSwordData.Element.FIRE, ForgedSwordData.Element.WATER));
        level.addFreshEntity(fire);
        scene("ember_swords", null, at(base, 2.5, 3.5, 6.5), at(base, 2.5, 3.5, 0), 40, null, null);
        scene("ember_swords_close", null, at(base, 1.5, 4.5, 2.4), at(base, 1.5, 4.5, 0), 15, null, null);
        scene("ember_sword_third_person", null, at(base, 10, 1.7, 3.2), at(base, 10, 1.3, 0.5), 15, null, null);
        Consumer<ServerPlayer> hold = p -> {
            p.getInventory().setItem(0, sword(ForgedSwordData.Element.COMBINED, ForgedSwordData.Element.FIRE));
            p.getInventory().setItem(1, sword(ForgedSwordData.Element.FIRE, ForgedSwordData.Element.WATER));
            p.getInventory().setItem(2, sword(ForgedSwordData.Element.EARTH, ForgedSwordData.Element.AIR));
            p.getInventory().selected = 0;
        };
        scene("ember_sword_first_person", hold, at(base, 2.5, 2.5, 5), at(base, 2.5, 2, 0), 25, mc -> mc.options.hideGui = false, null);
    }

    // ================================================================ 水晶柱与灵魂五芒星

    /** 五种水晶柱并排（米斯莱、淡青、虚空、恶魔、暗渎），走放置逻辑补成三格高 */
    private static void buildPillarScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        List<RegistryObject<? extends Block>> pillars = List.of(ECMithriline.MITHRILINE_CRYSTAL_PILLAR, ECPentacle.PALE_CRYSTAL_PILLAR,
                ECCrystalPillars.VOID_CRYSTAL_PILLAR, ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR, ECCrystalPillars.SHADE_CRYSTAL_PILLAR);
        for (int i = 0; i < pillars.size(); i++) {
            BlockPos pos = base.offset(i * 2, 0, -1);
            Block block = pillars.get(i).get();
            BlockState state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
        }
        scene("crystal_pillars", null, at(base, 4.5, 2.5, 5.5), at(base, 4.5, 1.5, -1), 30, null, null);
        scene("crystal_pillars_close", null, at(base, 6.5, 1.8, 1.6), at(base, 6.5, 1.6, -1), 15, null, null);
    }

    /**
     * 灵魂五芒星 tier 档：照 {@code ECPentacle.TIER_PATTERNS.get(tier)} 逐格摆，各档柱子灌满。符文颜色看档位（2 档紫、3 档红）。
     * 2 档那座另拍一张界面：身上先加 matrix_11（2 档治得了）和 matrix_12（成本 72000，2 档治不了），下一座（3 档）开拍时去掉。
     */
    private static void buildPentacleScene(ServerLevel level, ServerPlayer player, BlockPos base, int tier) {
        BlockPos pentacle = base.offset(3, 0, -3);
        buildStructure(level, player, ECPentacle.TIER_PATTERNS.get(tier), pentacle);
        List<BlockPos> pillars = new ArrayList<>();
        for (int t = 0; t <= tier; t++) {
            pillars.addAll(ECPentacle.pillarOffsets(t));
        }
        fillPillars(level, pentacle, pillars);
        String name = "pentacle_tier" + tier;
        // 先去掉上一座拍界面时临时加的矩阵类效果（没有就什么都不做），关掉那个界面
        scene(name, DevShowroomDemon::removeMatrixEffects, at(base, 3.5, 7, 6.5), at(base, 3.5, 1, -3), 40, DevShowroomDemon::closeScreen, null);
        scene(name + "_close", null, at(base, 3.5, 2.4, 0.2), at(base, 3.5, 0.3, -3), 15, null, null);
        if (tier == 2) {
            scene("02_gui_player_pentacle_tier2", p -> {
                IPlayerData data = PlayerDataEvents.get(p);
                if (data != null) {
                    data.addCorruptionEffect("matrix_11");
                    data.addCorruptionEffect("matrix_12");
                }
                DevShowroom.aim(p, at(base, 3.5, 1.6, 0.5), at(base, 3.5, 0.5, -3));
                if (p.serverLevel().getBlockEntity(pentacle) instanceof PlayerPentacleBlockEntity be) {
                    NetworkHooks.openScreen(p, be, pentacle);
                } else {
                    EssentialCraft5.LOGGER.warn("[showroom] {} 没有灵魂五芒星方块实体", pentacle);
                }
            }, null, null, 30, null, null);
        }
    }

    /** 照档位表摆一座 tier 档的灵魂五芒星、各档柱子灌满（第十批 10c 的展示厅拍 3 档，见 DevShowroomModules） */
    static void buildPentacleForShowroom(ServerLevel level, ServerPlayer player, BlockPos pentacle, int tier) {
        buildStructure(level, player, ECPentacle.TIER_PATTERNS.get(tier), pentacle);
        List<BlockPos> pillars = new ArrayList<>();
        for (int t = 0; t <= tier; t++) {
            pillars.addAll(ECPentacle.pillarOffsets(t));
        }
        fillPillars(level, pentacle, pillars);
    }

    /** 拍完 2 档界面以后去掉临时加的两个矩阵类效果（3 档那座第一张的 prepare 里调；虚空套装那张再调一次兜底） */
    private static void removeMatrixEffects(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            data.removeCorruptionEffect("matrix_11");
            data.removeCorruptionEffect("matrix_12");
        }
    }

    // ================================================================ 虚空套装、刻刀、恶魔护板

    /**
     * 两个盔甲架面朝镜头：左边穿满电的虚空套装、右手拿灵魂刻刀，右边穿魔力防护作对照（看面罩半透明像素在 cutout 下是不是画成实心，两套一样）。
     */
    private static void buildArmorScene(ServerLevel level, BlockPos base) {
        ArmorStand voidStand = armorStand(level, base.getX() + 1.5D, base.getY(), base.getZ() + 0.5D, 0F);
        voidStand.setItemSlot(EquipmentSlot.HEAD, MRUStaffItem.full(ECVoidArmor.VOID_HELMET.get()));
        voidStand.setItemSlot(EquipmentSlot.CHEST, MRUStaffItem.full(ECVoidArmor.VOID_CHESTPLATE.get()));
        voidStand.setItemSlot(EquipmentSlot.LEGS, MRUStaffItem.full(ECVoidArmor.VOID_LEGGINGS.get()));
        voidStand.setItemSlot(EquipmentSlot.FEET, MRUStaffItem.full(ECVoidArmor.VOID_BOOTS.get()));
        voidStand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ECDemonic.SOUL_SCRIBER.get()));
        voidStand.setRightArmPose(new Rotations(-60F, 0F, 0F));
        level.addFreshEntity(voidStand);
        ArmorStand mhsStand = armorStand(level, base.getX() + 4.5D, base.getY(), base.getZ() + 0.5D, 0F);
        mhsStand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECGear.MHS_HELMET.get()));
        mhsStand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECGear.MHS_CHESTPLATE.get()));
        mhsStand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECGear.MHS_LEGGINGS.get()));
        mhsStand.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECGear.MHS_BOOTS.get()));
        level.addFreshEntity(mhsStand);
        scene("void_armor", DevShowroomDemon::removeMatrixEffects, at(base, 3, 1.6, 5), at(base, 3, 1.1, 0.5), 30, DevShowroomDemon::closeScreen, null);
        scene("void_armor_helmets", null, at(base, 3, 2.1, 2.6), at(base, 3, 1.75, 0.5), 15, null, null);
        scene("void_armor_back", null, at(base, 3, 1.8, -4), at(base, 3, 1.1, 0.5), 15, null, null);
    }

    /** 16 色恶魔护板排两层（下层 0~7、上层 8~15） */
    private static void buildPlatingScene(ServerLevel level, BlockPos base) {
        for (int i = 0; i < COLORS.length; i++) {
            BlockPos pos = base.offset(i % 8, i / 8, -1);
            level.setBlockAndUpdate(pos, ECDemonic.DEMONIC_PLATING.get().defaultBlockState().setValue(DyeableBlock.COLOR, COLORS[i]));
        }
        scene("demonic_plating", null, at(base, 4, 1.5, 5), at(base, 4, 1, -1), 25, null, null);
    }

    // ================================================================ 界面

    private static void buildScreens() {
        Item[] hotbar = {ECVoidArmor.VOID_HELMET.get(), ECVoidArmor.VOID_CHESTPLATE.get(), ECVoidArmor.VOID_LEGGINGS.get(),
                ECVoidArmor.VOID_BOOTS.get(), ECDemonic.SOUL_SCRIBER.get(), ECDemonic.ACKRONITE_INGOT.get(), ECDemonic.DEMONIC_PLATE.get(),
                ECDemonic.DEMONIC_CORE.get()};
        DevShowroom.screens.add(new DevShowroom.ScreenShot("batch7_items_hotbar", p -> {
            for (int i = 0; i < 9; i++) {
                ItemStack stack;
                if (i < 4) {
                    stack = MRUStaffItem.full(hotbar[i]);
                } else if (i < hotbar.length) {
                    stack = new ItemStack(hotbar[i]);
                } else {
                    stack = sword(ForgedSwordData.Element.FIRE, ForgedSwordData.Element.AIR);
                }
                p.getInventory().setItem(i, stack);
            }
            p.getInventory().setItem(9, CapturedSoulItem.of(EntityType.VILLAGER, 7));
            p.getInventory().setItem(10, CapturedSoulItem.of(EntityType.ENDERMAN, 2));
            p.getInventory().selected = 8;
        }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            if (mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        })));
        tooltip("tooltip_forged_sword", () -> sword(ForgedSwordData.Element.FIRE, ForgedSwordData.Element.WATER));
        tooltip("tooltip_void_chestplate", () -> MRUStaffItem.full(ECVoidArmor.VOID_CHESTPLATE.get()));
        tooltip("tooltip_void_leggings", () -> MRUStaffItem.full(ECVoidArmor.VOID_LEGGINGS.get()));
        tooltip("tooltip_soul_scriber", () -> new ItemStack(ECDemonic.SOUL_SCRIBER.get()));
        tooltip("tooltip_captured_soul_villager", () -> CapturedSoulItem.of(EntityType.VILLAGER, 1));
        tooltip("tooltip_shade_crystal_pillar", () -> new ItemStack(ECCrystalPillars.SHADE_CRYSTAL_PILLAR.get()));
    }

    /** 一张物品提示框（{@link TooltipShot}，只在客户端开；关界面照常走 closeContainer） */
    private static void tooltip(String name, java.util.function.Supplier<ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            mc.setScreen(new TooltipShot(stack.get()));
        })));
    }

    /** 只画一个物品图标和它的提示框（用游戏自己的提示内容和样式），截图用，不动鼠标 */
    private static final class TooltipShot extends Screen {
        private final ItemStack stack;

        TooltipShot(ItemStack stack) {
            super(Component.empty());
            this.stack = stack;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.renderItem(stack, 16, 16);
            graphics.renderTooltip(font, stack, 30, 30);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    // ================================================================ 只拍第七批时的一排方块

    private static void buildOwnRow(ServerLevel level, ServerPlayer player, BlockPos base) {
        List<RegistryObject<? extends Block>> blocks = List.of(ECDemon.DEMONIC_PENTACLE, ECEmberForge.EMBER_FORGE,
                ECCrystalPillars.VOID_CRYSTAL_PILLAR, ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR, ECCrystalPillars.SHADE_CRYSTAL_PILLAR,
                ECDemonic.DEMONIC_PLATING);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = base.offset(i * 2, 0, -2);
            Block block = blocks.get(i).get();
            BlockState state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
        }
        scene("batch7_row", null, at(base, 5.5, 3.5, 5.5), at(base, 5.5, 0.8, -2), 30, null, null);
    }
}
