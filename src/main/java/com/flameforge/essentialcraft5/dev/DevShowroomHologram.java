package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.ComputerClient;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.PlayerCloneEntity;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第八批部分（全息与计算机，{@link DevShowroom} 调，只在 -PecShowroom / -PecShowroom8 时跑；batch8 §9）。
 * <p>
 * <b>场景</b>（04_scene_*，沿 X 排在方块排北边，镜头从南边朝北拍）：全息投影台；五只全息投影排成一排（休息 + 四种招式，头顶的核心跟着换）
 * 和只剩一只时的红色血条；用展示玩家皮肤和装备的玩家克隆体；停在半空的护甲破坏者；一下打死投影后掉了一地；手持遥控器看 20 格外的瞄准光柱、
 * 冻结在剩 1.5 秒的轨道打击（旁边 3 只僵尸）和落下那一刻；挂着不张开的二分场（球外、球里各一张）；第三人称拉二分枪；本批物品墙；
 * 宅男套装正面 / 背面、审判庭套装的盔甲架；第三人称看反重力板飞行；宅男套装的 HUD（夜视图标、冲刺冷却圈）。
 * <p>
 * <b>界面</b>（02_gui_*）：快捷栏和背包里的本批物品（盔甲是 3D 图标）；数学核心、满电宅男胸甲、审判庭头盔、遥控器、二分枪、反重力板、全息唱片的提示框。
 * <p>
 * 全息投影会放自己的 Boss 战音乐（唱片类），五只叠在一起太吵：拍全息那几张时把唱片音量临时调成 0，拍完放回。
 * 展示世界是和平难度，全息投影、克隆体、僵尸都会被清掉：本部分开拍时调成简单，场景拍完 {@link DevShowroom} 照例调回和平。
 */
public final class DevShowroomHologram {

    private DevShowroomHologram() {
    }

    /** 全量展示厅里本类占几个场景位（后面的部分从 firstIndex + 这个数开始） */
    static final int SCENE_SLOTS = 11;

    /** 场景里放出来的全息投影（服务端线程读写；最后一组场景前全部删掉） */
    private static final List<HologramEntity> HOLOGRAMS = new ArrayList<>();
    @Nullable
    private static OrbitalStrikeEntity strike;
    @Nullable
    private static DividerFieldEntity field;
    /** 拍全息前的唱片音量（客户端；null = 没改过） */
    @Nullable
    private static Double recordsBefore;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        buildHolopadScene(level, sceneBase.apply(firstIndex));
        buildHologramScene(sceneBase.apply(firstIndex + 1));
        buildCloneScene(sceneBase.apply(firstIndex + 2));
        buildArmorDestroyerScene(sceneBase.apply(firstIndex + 3));
        buildLootScene(sceneBase.apply(firstIndex + 4));
        buildOrbitalScene(sceneBase.apply(firstIndex + 5));
        buildDividerFieldScene(sceneBase.apply(firstIndex + 6));
        buildDividerGunScene(sceneBase.apply(firstIndex + 7));
        buildItemWall(level, sceneBase.apply(firstIndex + 8));
        buildArmorScene(level, sceneBase.apply(firstIndex + 9));
        buildBoardAndHudScene(sceneBase.apply(firstIndex + 10));
        buildScreens();
    }

    /** 客户端：场景全部拍完（或中途结束）时把改过的客户端状态放回（DevShowroom 的场景阶段结束时调） */
    static void clientFinish(Minecraft mc) {
        restoreRecords(mc);
        mc.options.keyUse.setDown(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        ComputerClient.setShowroomBoostReady(null);
    }

    /** 服务端：场景拍完后删掉还留着的实体、撤掉展示用的开关（DevShowroom.finishScenes 调） */
    static void finish(ServerPlayer player) {
        discardEntities();
        DividerGunItem.setShowroomHoldDraw(false);
        player.stopUsingItem();
        clearSuitAndBoard(player);
    }

    // ================================================================ 小工具

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, null));
    }

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void easy(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && server.getWorldData().getDifficulty() != Difficulty.EASY) {
            server.setDifficulty(Difficulty.EASY, true);
        }
    }

    /** NoAI 的全息投影（攻击循环、名单、放弃计时都不走），摆好招式（{@link HologramEntity#setTestState}） */
    @Nullable
    private static HologramEntity hologram(ServerLevel level, Vec3 at, float yaw, int attack, @Nullable Integer prev) {
        HologramEntity hologram = ECHologram.HOLOGRAM.get().create(level);
        if (hologram == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 造不出全息投影");
            return null;
        }
        hologram.moveTo(at.x, at.y, at.z, yaw, 0F);
        hologram.setYHeadRot(yaw);
        hologram.setYBodyRot(yaw);
        hologram.setNoAi(true);
        hologram.applyConfiguredHealth();
        level.addFreshEntity(hologram);
        hologram.setTestState(attack, 50, 100, prev);
        HOLOGRAMS.add(hologram);
        return hologram;
    }

    /**
     * SceneShot 的 ready：每刻清掉进度提示和聊天栏（拍 HUD 的那张前面刚拿到一堆本批物品，进度提示会盖住右上角），连着清够 ticks 刻才放行，
     * 截图拿的是上一帧，当刻清当刻拍会拍进去
     */
    private static java.util.function.Predicate<Minecraft> clearOverlaysFor(int ticks) {
        int[] left = {ticks};
        return mc -> {
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(false);
            return --left[0] <= 0;
        };
    }

    private static void muteRecords(Minecraft mc) {
        if (recordsBefore == null) {
            recordsBefore = mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).get();
            mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(0D);
        }
    }

    private static void restoreRecords(Minecraft mc) {
        if (recordsBefore != null) {
            mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(recordsBefore);
            recordsBefore = null;
        }
    }

    private static void discardEntities() {
        for (HologramEntity hologram : HOLOGRAMS) {
            if (!hologram.isRemoved()) {
                hologram.discard();
            }
        }
        HOLOGRAMS.clear();
        if (strike != null && !strike.isRemoved()) {
            strike.discard();
        }
        strike = null;
        if (field != null && !field.isRemoved()) {
            field.discard();
        }
        field = null;
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

    private static Zombie zombie(ServerLevel level, double x, double y, double z) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("生成不了僵尸");
        }
        // 面朝南边的镜头
        zombie.moveTo(x, y, z, 0F, 0F);
        zombie.setYHeadRot(0F);
        zombie.setYBodyRot(0F);
        zombie.setNoAi(true);
        // 白天不着火：只有轨道打击点的火
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        level.addFreshEntity(zombie);
        return zombie;
    }

    private static void hold(ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(player.getInventory().selected, stack);
    }

    /** 反重力板放进 / 拿出腰带槽第 0 格（不走 canEquip），并让饰品缓存作废 */
    private static void setBoard(ServerPlayer player, boolean wear) {
        ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        ICurioStacksHandler belt = inventory == null ? null : inventory.getCurios().get("belt");
        if (belt == null || belt.getSlots() < 1) {
            EssentialCraft5.LOGGER.warn("[showroom] 找不到腰带槽，反重力板没放上");
            return;
        }
        belt.getStacks().setStackInSlot(0, wear ? new ItemStack(ECComputer.COMPUTER_BOARD.get()) : ItemStack.EMPTY);
        CuriosCompat.invalidate(player);
    }

    private static void wearSuit(ServerPlayer player) {
        for (RegistryObject<? extends ArmorItem> piece : ECComputer.armor()) {
            ArmorItem item = piece.get();
            player.setItemSlot(item.getEquipmentSlot(), MRUStaffItem.full(item));
        }
    }

    private static void clearSuitAndBoard(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.removeEffect(MobEffects.NIGHT_VISION);
        setBoard(player, false);
    }

    // ================================================================ 全息投影台与 Boss

    /** 投影台摆在草地上：远一张、近一张 */
    private static void buildHolopadScene(ServerLevel level, BlockPos base) {
        level.setBlockAndUpdate(base.offset(2, 0, -2), ECHologram.HOLOPAD.get().defaultBlockState());
        scene("holopad", DevShowroomHologram::easy, at(base, 2.5, 2.2, 1.5), at(base, 2.5, 0.2, -2), DevShowroom.FIRST_SCENE_WAIT,
                DevShowroomHologram::muteRecords);
        scene("holopad_close", null, at(base, 3.4, 1.1, -0.6), at(base, 2.5, 0.25, -2), 15, null);
    }

    /**
     * 五只全息投影排一排（NoAI、站在地上）：休息（头顶故障核心）、加 / 减 / 乘 / 除四种招式（头顶换成对应的核心）。
     * 一张全景，每只一张近景；最后只留休息那一只，开着 HUD 拍红色血条。
     */
    private static void buildHologramScene(BlockPos base) {
        int[] states = {HologramEntity.REST, HologramEntity.ADDITION, HologramEntity.SUBTRACTION, HologramEntity.MULTIPLICATION,
                HologramEntity.DIVISION};
        String[] names = {"hologram_rest", "hologram_attack_addition", "hologram_attack_subtraction", "hologram_attack_multiplication",
                "hologram_attack_division"};
        Consumer<ServerPlayer> spawn = p -> {
            easy(p);
            for (int i = 0; i < states.length; i++) {
                hologram(p.serverLevel(), at(base, 0.5 + i * 4, 0, -4), 0F, states[i], null);
            }
        };
        scene("hologram_lineup", spawn, at(base, 8.5, 4.5, 7), at(base, 8.5, 1.4, -4), 40, DevShowroomHologram::muteRecords);
        for (int i = 0; i < states.length; i++) {
            scene(names[i], null, at(base, 0.5 + i * 4, 2.2, 0.2), at(base, 0.5 + i * 4, 1.9, -4), 15, null);
        }
        Consumer<ServerPlayer> keepOne = p -> {
            for (int i = 1; i < HOLOGRAMS.size(); i++) {
                HOLOGRAMS.get(i).discard();
            }
            if (HOLOGRAMS.size() > 1) {
                HOLOGRAMS.subList(1, HOLOGRAMS.size()).clear();
            }
        };
        scene("hologram_bossbar", keepOne, at(base, 0.5, 2.6, 4), at(base, 0.5, 1.8, -4), 20, mc -> mc.options.hideGui = false);
    }

    /**
     * 玩家克隆体：展示玩家先穿一身钻石套、拿钻石剑（克隆体照被克隆者当时的装备复制外观），放出来后脱掉。
     * 皮肤按玩家 UUID 从玩家列表取，开发环境离线时是默认皮肤
     */
    private static void buildCloneScene(BlockPos base) {
        Consumer<ServerPlayer> spawn = p -> {
            easy(p);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            hold(p, new ItemStack(Items.DIAMOND_SWORD));
            PlayerCloneEntity clone = PlayerCloneEntity.spawn(p.serverLevel(), null, p, at(base, 2.5, 0, -2.5));
            if (clone == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 玩家克隆体没生成出来");
            } else {
                clone.setNoAi(true);
                clone.setShowroomKeep(true);
                clone.setYRot(0F);
                clone.setYHeadRot(0F);
                clone.setYBodyRot(0F);
            }
            clearSuitAndBoard(p);
            hold(p, ItemStack.EMPTY);
        };
        scene("player_clone", spawn, at(base, 2.5, 1.9, 1.5), at(base, 2.5, 1.1, -2.5), 30, null);
        scene("player_clone_close", null, at(base, 2.5, 1.7, -0.6), at(base, 2.5, 1.4, -2.5), 15, null);
    }

    /** 护甲破坏者：一只 NoAI 的全息投影朝展示玩家射一发，然后让它停在半空（速度 0、无重力、不到寿命） */
    private static void buildArmorDestroyerScene(BlockPos base) {
        Consumer<ServerPlayer> shoot = p -> {
            easy(p);
            HologramEntity boss = hologram(p.serverLevel(), at(base, 2.5, 0, -5), 0F, HologramEntity.SUBTRACTION, null);
            if (boss == null) {
                return;
            }
            DevShowroom.aim(p, at(base, 2.5, 1.8, 2), at(base, 2.5, 1.5, -5));
            ArmorDestroyerEntity shot = ArmorDestroyerEntity.shoot(p.serverLevel(), boss, p, 1.5F, 0F);
            if (shot == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 护甲破坏者没射出来");
                return;
            }
            shot.moveTo(base.getX() + 2.5D, base.getY() + 1.6D, base.getZ() - 1.5D, shot.getYRot(), shot.getXRot());
            shot.setDeltaMovement(Vec3.ZERO);
            shot.setNoGravity(true);
            shot.setShowroomKeep(true);
        };
        scene("armor_destroyer", shoot, at(base, 4.2, 2.0, 1.2), at(base, 2.5, 1.5, -2.5), 25, null);
    }

    /** 一下打死：先摆成「上一招是乘法」，单次受伤封顶临时设 0，展示玩家打一下；掉乘法核心和 18~20 份遗物（另有几率掉遥控器、二分枪、唱片） */
    private static void buildLootScene(BlockPos base) {
        Consumer<ServerPlayer> kill = p -> {
            easy(p);
            HologramEntity boss = hologram(p.serverLevel(), at(base, 2.5, 0, -3), 0F, HologramEntity.REST, HologramEntity.MULTIPLICATION);
            if (boss == null) {
                return;
            }
            boss.setTestState(HologramEntity.REST, 0, 100, HologramEntity.MULTIPLICATION);
            ECHologram.Config.Values d = ECHologram.Config.DEFAULTS;
            ECHologram.Config.setTestOverrides(new ECHologram.Config.Values(d.maxHealth(), 0D, d.orbitalMaxDamage(), d.cloneMaxAttackDamage(),
                    d.maxClones(), d.maxArmorDestroyers(), d.maxDividers(), d.escapeRange(), d.abandonTicks(), d.otherDimensions()));
            try {
                boolean hurt = boss.hurt(p.damageSources().playerAttack(p), 10_000F);
                EssentialCraft5.LOGGER.info("[showroom] 打全息投影：{}，还活着 {}", hurt, boss.isAlive());
            } finally {
                ECHologram.Config.setTestOverrides(null);
            }
        };
        scene("hologram_loot", kill, at(base, 2.5, 3.2, 2.5), at(base, 2.5, 0.2, -3), 30, null);
    }

    // ================================================================ 遥控器与二分枪

    /**
     * 遥控器：手持（主手）看 20 格外的地面，瞄准光柱只在客户端画、隐藏界面时照画；然后在三只僵尸中间放一记（伤害 4，僵尸挨了着火不死），
     * 冻结在剩 30 刻（1.5 秒）拍光柱，再立刻结算拍落下那一刻（爆炸粒子、着火的僵尸）。拍倒计时前把遥控器拿走，后面的场景不再画瞄准光柱
     */
    private static void buildOrbitalScene(BlockPos base) {
        scene("orbital_preview", p -> hold(p, MRUStaffItem.full(ECHoloWeapons.ORBITAL_REMOTE.get())),
                at(base, 2.5, 2.6, 6), at(base, 2.5, 0, -14), 20, null);
        Consumer<ServerPlayer> countdown = p -> {
            hold(p, ItemStack.EMPTY);
            easy(p);
            ServerLevel level = p.serverLevel();
            zombie(level, base.getX() + 1D, base.getY(), base.getZ() - 5D);
            zombie(level, base.getX() + 4D, base.getY(), base.getZ() - 5.5D);
            zombie(level, base.getX() + 2.5D, base.getY(), base.getZ() - 7D);
            strike = OrbitalStrikeEntity.spawn(level, at(base, 2.5, 0, -5.5), 4F, 1F, 60, p, entity -> entity instanceof Zombie);
            if (strike == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 轨道打击没生成出来");
            } else {
                strike.setShowroomFreeze(30);
            }
        };
        scene("orbital_countdown", countdown, at(base, 2.5, 3.5, 5.5), at(base, 2.5, 3, -5.5), 30, null);
        Consumer<ServerPlayer> impact = p -> {
            if (strike != null && !strike.isRemoved()) {
                strike.detonate();
            }
        };
        scene("orbital_impact", impact, at(base, 2.5, 3.5, 5.5), at(base, 2.5, 1, -5.5), 3, null);
    }

    /** 二分场：挂着不张开（showroomHold），球外一张、镜头放进球里一张（看球体从里面看画不画） */
    private static void buildDividerFieldScene(BlockPos base) {
        Consumer<ServerPlayer> spawn = p -> {
            field = DividerFieldEntity.spawn(p.serverLevel(), at(base, 2.5, 1, -3.5), 40, p, entity -> false, true);
            if (field == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 二分场没生成出来");
            } else {
                field.setShowroomHold(true);
            }
        };
        scene("divider_field", spawn, at(base, 2.5, 2.4, 5), at(base, 2.5, 1, -3.5), 25, null);
        scene("divider_field_inside", null, at(base, 2.5, 1.2, -2.2), at(base, 2.5, 1, -8), 15, null);
    }

    /**
     * 第三人称（正面）拉二分枪：服务端开拉（展示厅钩子让它一直停在拉满、不自动发射），客户端把「使用」键状态按住
     * （只改键位对象的状态，不往系统发按键），不然本地客户端下一刻发现没按住就松开了
     */
    private static void buildDividerGunScene(BlockPos base) {
        Consumer<ServerPlayer> draw = p -> {
            if (field != null && !field.isRemoved()) {
                field.discard();
            }
            field = null;
            DividerGunItem.setShowroomHoldDraw(true);
            ItemStack gun = MRUStaffItem.full(ECHoloWeapons.DIVIDER_GUN.get());
            hold(p, gun);
            DevShowroom.aim(p, at(base, 2.5, 2.2, 0), at(base, 2.5, 2.2, -10));
            gun.use(p.serverLevel(), p, InteractionHand.MAIN_HAND);
        };
        Consumer<Minecraft> client = mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.options.keyUse.setDown(true);
        };
        scene("divider_gun_draw", draw, null, null, 30, client);
    }

    // ================================================================ 物品墙、盔甲架、反重力板与 HUD

    /** 本批物品挂在石墙上（隐形展示框，6 列）：16 种零件、4 种数学核心、反重力板、遥控器、二分枪、唱片、两个刷怪蛋、故障核心 */
    private static void buildItemWall(ServerLevel level, BlockPos base) {
        List<Supplier<ItemStack>> items = new ArrayList<>();
        items.add(() -> new ItemStack(ECComputer.NEURONIC_ENRICHED_PLATING.get()));
        for (RegistryObject<Item> part : ECComputer.parts()) {
            items.add(() -> new ItemStack(part.get()));
        }
        for (RegistryObject<Item> core : List.of(ECComputer.ADDITION_CORE, ECComputer.SUBTRACTION_CORE, ECComputer.MULTIPLICATION_CORE,
                ECComputer.DIVISION_CORE)) {
            items.add(() -> new ItemStack(core.get()));
        }
        items.add(() -> new ItemStack(ECComputer.COMPUTER_BOARD.get()));
        items.add(() -> MRUStaffItem.full(ECHoloWeapons.ORBITAL_REMOTE.get()));
        items.add(() -> MRUStaffItem.full(ECHoloWeapons.DIVIDER_GUN.get()));
        items.add(() -> new ItemStack(ECHologram.MUSIC_DISC_ROPOCALYPSE.get()));
        items.add(() -> new ItemStack(ECHologram.HOLOGRAM_SPAWN_EGG.get()));
        items.add(() -> new ItemStack(ECHologram.PLAYER_CLONE_SPAWN_EGG.get()));
        items.add(() -> new ItemStack(ECMaterials.GLITCHY_CORE.get()));
        int columns = 6;
        int rows = (items.size() + columns - 1) / columns;
        for (int x = -1; x <= columns; x++) {
            for (int y = 0; y <= rows + 1; y++) {
                level.setBlockAndUpdate(base.offset(x, y, -1), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int i = 0; i < items.size(); i++) {
            BlockPos pos = base.offset(i % columns, rows - i / columns, 0);
            ItemFrame frame = new ItemFrame(level, pos, Direction.SOUTH);
            frame.setItem(items.get(i).get(), false);
            frame.setInvisible(true);
            level.addFreshEntity(frame);
        }
        // 上一张是第三人称拉弓：切回第一人称、松开「使用」键、停止拉弓
        Consumer<ServerPlayer> stopDrawing = p -> {
            DividerGunItem.setShowroomHoldDraw(false);
            p.stopUsingItem();
            hold(p, ItemStack.EMPTY);
        };
        Consumer<Minecraft> firstPerson = mc -> {
            mc.options.keyUse.setDown(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        };
        scene("batch8_items", stopDrawing, at(base, 2.5, 3.2, 6.5), at(base, 2.5, 3.2, 0), 30, firstPerson);
        scene("batch8_items_parts", null, at(base, 2.5, 4.2, 3.0), at(base, 2.5, 4.2, 0), 15, null);
    }

    /** 宅男套装（满电）正面、背面，审判庭套装正面（核对 128 × 64 的宅男贴图和审判庭的两层贴图） */
    private static void buildArmorScene(ServerLevel level, BlockPos base) {
        ArmorStand computer = armorStand(level, base.getX() + 1.5D, base.getY(), base.getZ() + 0.5D, 0F);
        for (RegistryObject<? extends ArmorItem> piece : ECComputer.armor()) {
            computer.setItemSlot(piece.get().getEquipmentSlot(), MRUStaffItem.full(piece.get()));
        }
        computer.setRightArmPose(new Rotations(-20F, 0F, 10F));
        level.addFreshEntity(computer);
        ArmorStand inquisitorium = armorStand(level, base.getX() + 4.5D, base.getY(), base.getZ() + 0.5D, 0F);
        for (RegistryObject<? extends ArmorItem> piece : ECInquisitorium.all()) {
            inquisitorium.setItemSlot(piece.get().getEquipmentSlot(), new ItemStack(piece.get()));
        }
        level.addFreshEntity(inquisitorium);
        scene("computer_armor", null, at(base, 1.5, 1.6, 3.6), at(base, 1.5, 1.1, 0.5), 30, null);
        scene("computer_armor_back", null, at(base, 1.5, 1.8, -3.2), at(base, 1.5, 1.1, 0.5), 15, null);
        scene("inquisitorium_armor", null, at(base, 4.5, 1.6, 3.6), at(base, 4.5, 1.1, 0.5), 15, null);
    }

    /**
     * 反重力板：放进腰带槽、飞在离地 2 格处，第三人称从背后看脚下的板子（OBJ 的位置、朝向、缩放、贴图）；
     * HUD：穿满电宅男全套、服务端开夜视（隐藏图标的那份），客户端让冲刺圈停在缺一块（0.4），开着 HUD、第一人称拍屏幕顶上两个小圆。
     * 拍反重力板之前把还留着的全息投影、二分场都删掉（它们的血条、音乐不该出现在后面）
     */
    private static void buildBoardAndHudScene(BlockPos base) {
        Consumer<ServerPlayer> board = p -> {
            discardEntities();
            DividerGunItem.setShowroomHoldDraw(false);
            p.stopUsingItem();
            hold(p, ItemStack.EMPTY);
            setBoard(p, true);
        };
        Consumer<Minecraft> thirdPerson = mc -> {
            mc.options.keyUse.setDown(false);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        };
        scene("computer_board_flight", board, at(base, 2.5, 3.0, 1), at(base, 2.5, 2.2, -6), 30, thirdPerson);
        Consumer<ServerPlayer> hud = p -> {
            setBoard(p, false);
            wearSuit(p);
            boolean on = ComputerArmorEvents.toggleNightVision(p);
            EssentialCraft5.LOGGER.info("[showroom] 宅男头盔夜视：{}", on);
        };
        Consumer<Minecraft> hudClient = mc -> {
            restoreRecords(mc);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            ComputerClient.setShowroomBoostReady(0.4F);
        };
        DevShowroom.scenes.add(new DevShowroom.SceneShot("computer_hud", hud, at(base, 2.5, 2.0, 4), at(base, 2.5, 1.0, -6), 25, hudClient,
                clearOverlaysFor(10)));
    }

    // ================================================================ 界面

    private static void buildScreens() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("batch8_items_hotbar", p -> {
            // 创造模式开物品栏是创造栏，只看得到快捷栏这一排：审判庭四件（3D 图标）、宅男四件、反重力板放前面
            List<ItemStack> stacks = new ArrayList<>();
            for (RegistryObject<? extends ArmorItem> piece : ECInquisitorium.all()) {
                stacks.add(new ItemStack(piece.get()));
            }
            for (RegistryObject<? extends ArmorItem> piece : ECComputer.armor()) {
                stacks.add(MRUStaffItem.full(piece.get()));
            }
            stacks.add(new ItemStack(ECComputer.COMPUTER_BOARD.get()));
            stacks.add(MRUStaffItem.full(ECHoloWeapons.ORBITAL_REMOTE.get()));
            stacks.add(MRUStaffItem.full(ECHoloWeapons.DIVIDER_GUN.get()));
            stacks.add(new ItemStack(ECHologram.HOLOPAD.get()));
            stacks.add(new ItemStack(ECComputer.COMPUTER_ENGINE.get()));
            stacks.add(new ItemStack(ECComputer.ADDITION_CORE.get()));
            stacks.add(new ItemStack(ECComputer.SUBTRACTION_CORE.get()));
            stacks.add(new ItemStack(ECComputer.MULTIPLICATION_CORE.get()));
            stacks.add(new ItemStack(ECComputer.DIVISION_CORE.get()));
            stacks.add(new ItemStack(ECHologram.MUSIC_DISC_ROPOCALYPSE.get()));
            for (int i = 0; i < 18; i++) {
                p.getInventory().setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
            }
            p.getInventory().selected = 0;
        }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            if (mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        })));
        tooltip("tooltip_addition_core", () -> new ItemStack(ECComputer.ADDITION_CORE.get()));
        tooltip("tooltip_computer_chestplate", () -> MRUStaffItem.full(ECComputer.COMPUTER_CHESTPLATE.get()));
        tooltip("tooltip_inquisitorium_helmet", () -> new ItemStack(ECInquisitorium.INQUISITORIUM_HELMET.get()));
        tooltip("tooltip_orbital_remote", () -> MRUStaffItem.full(ECHoloWeapons.ORBITAL_REMOTE.get()));
        tooltip("tooltip_divider_gun", () -> MRUStaffItem.full(ECHoloWeapons.DIVIDER_GUN.get()));
        tooltip("tooltip_computer_board", () -> new ItemStack(ECComputer.COMPUTER_BOARD.get()));
        tooltip("tooltip_music_disc_ropocalypse", () -> new ItemStack(ECHologram.MUSIC_DISC_ROPOCALYPSE.get()));
    }

    /** 一张物品提示框（只在客户端开；关界面照常走 closeContainer） */
    private static void tooltip(String name, Supplier<ItemStack> stack) {
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
}
