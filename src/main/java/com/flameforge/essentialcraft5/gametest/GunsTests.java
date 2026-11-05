package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.gun.GunConfig;
import com.flameforge.essentialcraft5.gun.GunMatrixFx;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.network.S2CGunMatrixHitPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGuns;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * ECGuns「枪与射线」的 GameTest（batch5.md §3.1）：开火与扣费、弹匣与装填、连发节奏、卡壳、按住时换枪、射线（伤害源、穿透、被方块挡、草丛不挡、墙后、
 * 身后贴身、斜射玻璃板背后、擦边容差、同乘、盟友、上限、散布、不生成实体）、PvP 与矩阵损伤、队伍、盔甲架、领地拦截、假玩家、伤害类型、铁砧修枪；
 * 「打中就加矩阵损伤」（GUNS-4 修订：无敌帧、LivingHurtEvent / LivingDamageEvent 被取消、倍率 0 照加，排在最后的领地监听器、模拟的混合端插件拦下不加，
 * 判定表 {@link RayHitGate#verdict}）、矩阵损伤特效的服务端限频（{@link GunMatrixFx.Throttle}）。
 * <p>
 * <b>测试玩家的限制</b>（{@code ProtectionFrameworkTests.realPlayer}）：不在世界、不在玩家列表里——射线的实体查询找不到当靶子的玩家，打玩家的测试直接调
 * {@link RayShot#hit}；光束包走玩家列表发不到它，光束用 {@link RayShot.BeamSink} 记下来断言；刚 new 的 ServerPlayer 有 60 刻出生无敌，
 * 当靶子前先空跑 61 次 tick（{@link #victim}）；驯服生物的 {@code isAlliedTo} 在世界里找不到这个主人，射线另按主人 UUID 认。
 * <p>
 * 站位（模板 {@code empty_large}，11×8×11）：射手脚在 (5.5, 1, 1.5)、朝 +z（yaw 0）、眼睛 y = 2.62；靶子排在 x = 5.5 这条线上；
 * z = 10 立一堵石墙（{@link #backstop}），射线不会飞出本测试的结构去打别的测试的生物。
 * 靶子用 {@code EntityType.create} + {@code addFreshEntity}、关 AI、戴皮革帽（白天不烧），同 TeslaTests。
 * 射线测试用散布为 0 的属性（{@link #steady}），结果不靠随机。改配置用 {@code GunConfig.setTestOverrides}，同一段同步代码里设、测、置回；
 * 改 PvP 开关同理。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GunsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final Vec3 SHOOTER_FEET = new Vec3(5.5D, 1D, 1.5D);
    private static final int LANE_X = 5;
    private static final float EPS = 0.01F;

    private GunsTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 真玩家射手，脚在 relativeFeet、朝向 yaw / pitch（名字 ≤ 16 字符） */
    private static ServerPlayer shooter(GameTestHelper helper, String name, Vec3 relativeFeet, float yaw, float pitch) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, yaw, pitch);
        return player;
    }

    private static ServerPlayer shooter(GameTestHelper helper) {
        return shooter(helper, "ec5-gunner", SHOOTER_FEET, 0F, 0F);
    }

    /** 要挨打的真玩家：过掉 60 刻出生无敌，脚在 relativeFeet */
    private static ServerPlayer victim(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 关 AI、戴帽子的僵尸，脚在 relativeFeet */
    private static Zombie zombie(GameTestHelper helper, Vec3 relativeFeet) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("建不出僵尸");
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        zombie.moveTo(at.x, at.y, at.z, 180F, 0F);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        level.addFreshEntity(zombie);
        return zombie;
    }

    /** 本条 lane 上 z 处的僵尸 */
    private static Zombie laneZombie(GameTestHelper helper, double z) {
        return zombie(helper, new Vec3(LANE_X + 0.5D, 1D, z));
    }

    /** z = 10 的挡墙（x 4~6、y 1~4），射线停在本测试里 */
    private static void backstop(GameTestHelper helper) {
        for (int x = 4; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            }
        }
    }

    /** 射手眼前（z = 2）一格石头：走物品 use / onUseTick 的测试用，射线立刻停住 */
    private static void faceWall(GameTestHelper helper) {
        helper.setBlock(new BlockPos(LANE_X, 2, 2), Blocks.STONE);
    }

    private static ItemStack gun(GunType type, GunParts parts, int mru) {
        ItemStack stack = GunItem.create(type, parts, mru);
        if (stack.isEmpty()) {
            throw new IllegalStateException("枪没注册：" + type.itemId());
        }
        return stack;
    }

    private static ItemStack mruGun(GunType type, int mru) {
        return gun(type, GunParts.uniform("mru"), mru);
    }

    private static int mruOf(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(-1);
    }

    /** 魔力锭手枪的属性、散布改成 0（射线测试不靠随机） */
    private static GunStats steady() {
        GunStats base = GunStats.of(GunType.PISTOL, GunParts.uniform("mru"));
        return new GunStats(base.damage(), base.durability(), base.reload(), base.recoil(), base.speed(), 0F, base.shots(), base.zoom(),
                base.balance());
    }

    /** 记下每一道光束 */
    private static final class Beams implements RayShot.BeamSink {
        final List<Vec3[]> beams = new ArrayList<>();

        @Override
        public void beam(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 to, int balance) {
            beams.add(new Vec3[]{from, to});
        }
    }

    private static RayShot.Result fire(ServerPlayer shooter, GunStats stats) {
        return RayShot.fire(shooter, stats, RandomSource.create(1L), false, new Beams());
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    private static int matrix(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? -1 : data.getMatrixDamage();
    }

    private static void discard(Collection<? extends Entity> entities) {
        entities.forEach(Entity::discard);
    }

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
    }

    /** 让 player 看向 target（世界坐标） */
    private static void aimAt(ServerPlayer player, Vec3 target) {
        Vec3 d = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        player.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F);
        player.setXRot((float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG));
    }

    // ------------------------------------------------------------------ 开火、弹匣、连发

    /** 满 MRU 魔力锭手枪打一发：枪少 133 MRU、fired = 1、冷却 10 刻 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsPistolShotCostsMru(GameTestHelper helper) {
        faceWall(helper);
        ServerPlayer player = shooter(helper);
        ItemStack stack = mruGun(GunType.PISTOL, 5000);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.CONSUME, "应当开火，结果 " + result.getResult());
        helper.assertTrue(mruOf(stack) == 5000 - 133, "应扣 133 MRU，剩 " + mruOf(stack));
        helper.assertTrue(GunParts.fired(stack) == 1 && GunParts.wear(stack) == 1, "fired / wear 应为 1");
        Item pistol = ECGuns.PISTOL.get();
        for (int i = 0; i < 9; i++) {
            player.getCooldowns().tick();
        }
        helper.assertTrue(player.getCooldowns().isOnCooldown(pistol), "9 刻后还在冷却");
        player.getCooldowns().tick();
        helper.assertFalse(player.getCooldowns().isOnCooldown(pistol), "10 刻后冷却结束");
        helper.succeed();
    }

    /** 生存、身上和枪里都没有 MRU：不打、fired 不变、僵尸不掉血（原作照样把子弹「打掉」） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsNoMruNoShot(GameTestHelper helper) {
        backstop(helper);
        Zombie target = laneZombie(helper, 6.5D);
        ServerPlayer player = shooter(helper);
        ItemStack stack = mruGun(GunType.PISTOL, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.FAIL, "没 MRU 应失败");
        helper.assertTrue(GunParts.fired(stack) == 0 && GunParts.wear(stack) == 0, "没打不该计数");
        helper.assertFalse(hurt(target), "僵尸不该掉血");
        helper.assertFalse(player.getCooldowns().isOnCooldown(ECGuns.PISTOL.get()), "没打不该冷却");
        target.discard();
        helper.succeed();
    }

    /** 创造模式免费（MRUDrain 的现有规则）：枪里 0 MRU 照打 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsCreativeFiresFree(GameTestHelper helper) {
        faceWall(helper);
        ServerPlayer player = shooter(helper);
        player.getAbilities().instabuild = true;
        ItemStack stack = mruGun(GunType.PISTOL, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(GunParts.fired(stack) == 1 && mruOf(stack) == 0, "创造模式应免费开火");
        helper.succeed();
    }

    /** 手枪 3 发后第 4 次右键开始装填，装完 fired = 0 且有 60 刻冷却；步枪装完没有冷却 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsMagazineThenReload(GameTestHelper helper) {
        faceWall(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper);
        ItemStack pistol = mruGun(GunType.PISTOL, 5000);
        GunStats stats = GunStats.of(pistol).orElseThrow();
        helper.assertTrue(stats.magazine() == 3, "魔力锭手枪弹量 3");
        player.setItemInHand(InteractionHand.MAIN_HAND, pistol);
        for (int i = 0; i < 3; i++) {
            player.getCooldowns().removeCooldown(pistol.getItem());
            pistol.use(level, player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(GunParts.fired(pistol) == 3 && !player.isUsingItem(), "打了 3 发，还没开始装填");
        helper.assertTrue(pistol.getUseAnimation() == UseAnim.BLOCK, "过热时是装填动作");
        player.getCooldowns().removeCooldown(pistol.getItem());
        int before = mruOf(pistol);
        pistol.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem() && player.getUseItemRemainingTicks() == stats.reloadTicks(),
                "第 4 次右键应开始装填 " + stats.reloadTicks() + " 刻，实际 " + player.getUseItemRemainingTicks());
        helper.assertTrue(mruOf(pistol) == before && GunParts.fired(pistol) == 3, "装填不扣 MRU、不开火");
        pistol.finishUsingItem(level, player);
        player.stopUsingItem();
        helper.assertTrue(GunParts.fired(pistol) == 0, "装完 fired = 0");
        helper.assertTrue(player.getCooldowns().isOnCooldown(pistol.getItem()), "手枪装完有 3 秒冷却");

        ItemStack rifle = mruGun(GunType.RIFLE, 5000);
        GunStats rifleStats = GunStats.of(rifle).orElseThrow();
        GunParts.setFired(rifle, rifleStats.magazine());
        player.setItemInHand(InteractionHand.MAIN_HAND, rifle);
        rifle.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem() && player.getUseItemRemainingTicks() == rifleStats.reloadTicks(), "步枪过热也是装填");
        rifle.finishUsingItem(level, player);
        player.stopUsingItem();
        helper.assertTrue(GunParts.fired(rifle) == 0, "步枪装完 fired = 0");
        helper.assertFalse(player.getCooldowns().isOnCooldown(rifle.getItem()), "步枪装完没有冷却");
        helper.succeed();
    }

    /** 步枪按住：第 0、3、6 刻各一发（9 刻 3 发），每发 40 MRU */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRifleFiresEvery3Ticks(GameTestHelper helper) {
        faceWall(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper);
        ItemStack rifle = mruGun(GunType.RIFLE, 5000);
        player.setItemInHand(InteractionHand.MAIN_HAND, rifle);
        rifle.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "步枪右键应开始按住");
        for (int t = 0; t < 9; t++) {
            rifle.getItem().onUseTick(level, player, rifle, GunItem.USE_DURATION - t);
        }
        helper.assertTrue(GunParts.fired(rifle) == 3, "9 刻应打 3 发，实际 " + GunParts.fired(rifle));
        helper.assertTrue(mruOf(rifle) == 5000 - 3 * 40, "每发 40 MRU，剩 " + mruOf(rifle));
        player.stopUsingItem();
        helper.succeed();
    }

    /** 加特林：前 60 刻只空转不打，之后每刻一发、每发 26 MRU；主手、副手移动速度都 −0.6 MULTIPLY_TOTAL（两个 UUID） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsGatlingSpinup(GameTestHelper helper) {
        faceWall(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = shooter(helper);
        ItemStack gatling = gun(GunType.GATLING, GunParts.uniform("mru").withLens("chaos"), 5000);
        player.setItemInHand(InteractionHand.MAIN_HAND, gatling);
        for (int t = 0; t < 60; t++) {
            gatling.getItem().onUseTick(level, player, gatling, GunItem.USE_DURATION - t);
        }
        helper.assertTrue(GunParts.fired(gatling) == 0 && mruOf(gatling) == 5000, "预转 60 刻内不开火");
        for (int t = 60; t < 70; t++) {
            gatling.getItem().onUseTick(level, player, gatling, GunItem.USE_DURATION - t);
        }
        helper.assertTrue(GunParts.fired(gatling) == 10, "之后每刻一发，10 刻应 10 发，实际 " + GunParts.fired(gatling));
        helper.assertTrue(mruOf(gatling) == 5000 - 10 * 26, "每发 26 MRU，剩 " + mruOf(gatling));
        Collection<AttributeModifier> speed = gatling.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed.stream().anyMatch(m -> Math.abs(m.getAmount() - GunItem.GATLING_SLOW) < 1.0E-6D
                && m.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL), "加特林主手应有 −0.6 MULTIPLY_TOTAL 的移速修饰");
        // 审查 GUNS-5（取 A）：副手也减速，UUID 和主手的不同（共用的话两只手都拿着时放下一把会把另一把的减速一起摘掉）
        Collection<AttributeModifier> offhand = gatling.getAttributeModifiers(EquipmentSlot.OFFHAND).get(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(offhand.stream().anyMatch(m -> Math.abs(m.getAmount() - GunItem.GATLING_SLOW) < 1.0E-6D
                && m.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL && m.getId().equals(GunItem.GATLING_SLOW_OFFHAND_ID)),
                "加特林副手也应有 −0.6 MULTIPLY_TOTAL 的移速修饰（副手的 UUID）");
        helper.assertFalse(offhand.stream().anyMatch(m -> m.getId().equals(GunItem.GATLING_SLOW_ID)), "副手的修饰不能和主手共用 UUID");
        helper.assertTrue(gatling.getAttributeModifiers(EquipmentSlot.HEAD).get(Attributes.MOVEMENT_SPEED).isEmpty(), "不在手上不减速");
        helper.assertTrue(mruGun(GunType.PISTOL, 0).getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.MOVEMENT_SPEED).isEmpty(),
                "手枪不减速");
        helper.succeed();
    }

    /** 磨损满了：固定种子下卡壳（fired 直接 = 弹量），另一个种子不卡壳；磨损不再增加 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsJamWhenWorn(GameTestHelper helper) {
        faceWall(helper);
        ServerPlayer player = shooter(helper);
        // 候选种子要打散：LegacyRandomSource（java.util.Random 同一个 LCG）对 0、1、2…… 这样挨着的种子，第一次 nextFloat 几乎一样（都约 0.73），
        // 挨个试 0~999 找不到 < 0.25 的（集成时实测挂过）
        Long jamSeed = null;
        Long okSeed = null;
        for (long i = 1L; i <= 1000L && (jamSeed == null || okSeed == null); i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            float roll = RandomSource.create(seed).nextFloat();
            if (roll < GunItem.JAM_CHANCE && jamSeed == null) {
                jamSeed = seed;
            } else if (roll >= GunItem.JAM_CHANCE && okSeed == null) {
                okSeed = seed;
            }
        }
        helper.assertTrue(jamSeed != null && okSeed != null, "找不到卡壳 / 不卡壳的种子");
        GunStats stats = GunStats.of(GunType.PISTOL, GunParts.uniform("mru"));
        ItemStack jam = mruGun(GunType.PISTOL, 5000);
        GunParts.setWear(jam, stats.durabilityInt());
        player.setItemInHand(InteractionHand.MAIN_HAND, jam);
        helper.assertTrue(GunItem.shoot(player, jam, GunType.PISTOL, stats, RandomSource.create(jamSeed), false, new Beams()), "应开火");
        helper.assertTrue(GunParts.fired(jam) == stats.magazine(), "卡壳后 fired = 弹量，实际 " + GunParts.fired(jam));
        helper.assertTrue(GunParts.wear(jam) == stats.durabilityInt(), "磨损不超过耐久");

        ItemStack ok = mruGun(GunType.PISTOL, 5000);
        GunParts.setWear(ok, stats.durabilityInt());
        GunItem.shoot(player, ok, GunType.PISTOL, stats, RandomSource.create(okSeed), false, new Beams());
        helper.assertTrue(GunParts.fired(ok) == 1, "没卡壳时照常 +1");
        helper.succeed();
    }

    /**
     * 按住时手上换了一把（审查 GUNS-2）：同一个对象、客户端每发同步来的新对象（只是 fired / MRU 变了）接着用；
     * 装填中换成有弹的同种枪、换成部件不同的同种枪、换成别种枪都中止——不然能借别的枪的装填时间，加特林能跳过 3 秒预转。
     */
    @GameTest(template = EMPTY)
    public static void gunsSwapWhileHeldStops(GameTestHelper helper) {
        GunItem rifle = ECGuns.RIFLE.get();
        ItemStack held = mruGun(GunType.RIFLE, 5000);
        helper.assertTrue(rifle.canContinueUsing(held, held), "同一个对象应接着用");
        ItemStack synced = held.copy();
        GunParts.setFired(synced, 1);
        synced.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(4960));
        helper.assertTrue(rifle.canContinueUsing(held, synced), "只是 fired / MRU 变了（客户端每发同步来的新对象）应接着用");

        GunStats mruRifle = GunStats.of(GunType.RIFLE, GunParts.uniform("mru"));
        ItemStack emptyRifle = mruGun(GunType.RIFLE, 5000);
        GunParts.setFired(emptyRifle, mruRifle.magazine());
        helper.assertFalse(rifle.canContinueUsing(emptyRifle, mruGun(GunType.RIFLE, 5000)), "装填中换成有弹的同种枪应中止");
        ItemStack emptyOther = gun(GunType.RIFLE, GunParts.uniform("pale"), 5000);
        GunParts.setFired(emptyOther, GunStats.of(GunType.RIFLE, GunParts.uniform("pale")).magazine());
        helper.assertFalse(rifle.canContinueUsing(emptyRifle, emptyOther), "装填中换成部件不同的空枪应中止（装填时长按开始那把定）");
        helper.assertFalse(rifle.canContinueUsing(held, mruGun(GunType.PISTOL, 5000)), "换成别种枪应中止");

        GunParts gatlingParts = GunParts.uniform("mru").withLens("chaos");
        GunItem gatling = ECGuns.GATLING.get();
        ItemStack emptyGatling = gun(GunType.GATLING, gatlingParts, 5000);
        GunParts.setFired(emptyGatling, GunStats.of(GunType.GATLING, gatlingParts).magazine());
        helper.assertFalse(gatling.canContinueUsing(emptyGatling, gun(GunType.GATLING, gatlingParts, 5000)),
                "加特林装填中换成有弹的应中止（不能跳过预转）");
        helper.assertFalse(gatling.canContinueUsing(gun(GunType.GATLING, gatlingParts, 5000), gun(GunType.GATLING, GunParts.uniform("mru"), 5000)),
                "透镜不同也算部件不同");
        helper.succeed();
    }

    /** 没组装的枪（创造栏）右键：只从已开放的材料里随机组装 */
    @GameTest(template = EMPTY)
    public static void gunsUnassembledAssemblesRandomly(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (GunType type : GunType.values()) {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(type.itemId()));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            GunParts parts = GunParts.read(stack).orElse(null);
            helper.assertTrue(parts != null, type.id() + " 右键后应组装好");
            for (String body : List.of(parts.base(), parts.handle(), parts.device())) {
                helper.assertTrue(GunMaterials.body(body).map(GunMaterials.Body::available).orElse(false), type.id() + " 用了没开放的材料 " + body);
            }
            helper.assertTrue(GunParts.fired(stack) == 0, "组装时不开火");
            helper.assertTrue(type.scopeKind() != GunType.ScopeKind.NONE || parts.scope() == null, "加特林没有瞄具");
        }
        helper.succeed();
    }

    /** 假玩家拿着不开火、不扣 MRU */
    @GameTest(template = EMPTY)
    public static void gunsFakePlayerDoesNotFire(GameTestHelper helper) {
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        ItemStack stack = mruGun(GunType.PISTOL, 5000);
        ItemStack held = fake.getItemInHand(InteractionHand.MAIN_HAND);
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            InteractionResultHolder<ItemStack> result = stack.use(helper.getLevel(), fake, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult() == InteractionResult.FAIL, "假玩家应失败");
            helper.assertTrue(mruOf(stack) == 5000 && GunParts.fired(stack) == 0, "假玩家不扣 MRU、不计数");
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 射线

    /** 伤害类型 mru_ray、攻击者是射手、直接实体为空；僵尸记下射手是凶手；发了一道光束、终点在挡墙 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayHitsWithShooterSource(GameTestHelper helper) {
        backstop(helper);
        Zombie target = laneZombie(helper, 6.5D);
        ServerPlayer player = shooter(helper);
        Beams beams = new Beams();
        RayShot.Result result = RayShot.fire(player, steady(), RandomSource.create(1L), false, beams);
        helper.assertTrue(result.damaged().contains(target) && hurt(target), "僵尸应掉血");
        DamageSource source = target.getLastDamageSource();
        helper.assertTrue(source != null && source.is(ECGuns.MRU_RAY), "伤害类型应是 mru_ray");
        helper.assertTrue(source.getEntity() == player && source.getDirectEntity() == null, "causing = 射手、直接实体为空");
        helper.assertTrue(target.getLastHurtByMob() == player && target.getKillCredit() == player, "僵尸应记下射手（玩家击杀）");
        helper.assertTrue(beams.beams.size() == 1, "应发一道光束");
        Vec3 wall = helper.absoluteVec(new Vec3(5.5D, 2.62D, 10D));
        helper.assertTrue(Math.abs(result.to().z - wall.z) < 0.01D, "光束终点应在挡墙表面，实际 " + result.to());
        target.discard();
        helper.succeed();
    }

    /** 一条线上的 3 只全打中（穿透） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayPiercesLine(GameTestHelper helper) {
        backstop(helper);
        List<Zombie> line = List.of(laneZombie(helper, 3.5D), laneZombie(helper, 5.5D), laneZombie(helper, 7.5D));
        RayShot.Result result = fire(shooter(helper), steady());
        helper.assertTrue(line.stream().allMatch(GunsTests::hurt) && result.damaged().size() == 3, "3 只都该掉血");
        helper.assertTrue(result.targets().get(0) == line.get(0) && result.targets().get(2) == line.get(2), "目标按距离从近到远");
        discard(line);
        helper.succeed();
    }

    private static void assertBlocked(GameTestHelper helper, BlockPos obstacle, BlockState expected) {
        backstop(helper);
        Zombie behind = laneZombie(helper, 6.5D);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(obstacle)) {
            RayShot.Result result = fire(shooter(helper), steady());
            helper.assertFalse(hurt(behind), expected.getBlock() + " 后面的僵尸不该掉血");
            helper.assertTrue(new AABB(helper.absolutePos(obstacle)).inflate(1.0E-3D).contains(result.to()),
                    "射线应停在 " + expected.getBlock() + " 这一格里，终点 " + result.to());
            helper.assertTrue(claims.breaks(obstacle) == 0 && claims.places(obstacle) == 0, "射线不发破坏 / 放置事件");
        }
        helper.assertBlockState(obstacle, state -> state == expected, () -> "挡住射线的方块不该变");
        behind.discard();
    }

    /** 玻璃挡住射线，玻璃不碎、不发任何方块事件 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayBlockedByGlass(GameTestHelper helper) {
        BlockPos pos = new BlockPos(LANE_X, 2, 4);
        helper.setBlock(pos, Blocks.GLASS);
        assertBlocked(helper, pos, Blocks.GLASS.defaultBlockState());
        helper.succeed();
    }

    /** 栅栏（只有中间的柱子）挡住射线 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayBlockedByFence(GameTestHelper helper) {
        BlockPos pos = new BlockPos(LANE_X, 2, 4);
        helper.setBlock(pos, Blocks.OAK_FENCE);
        assertBlocked(helper, pos, helper.getBlockState(pos));
        helper.succeed();
    }

    /** 关着的门（两格都放）挡住射线 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayBlockedByClosedDoor(GameTestHelper helper) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.setBlock(new BlockPos(LANE_X, 1, 4), lower);
        helper.setBlock(new BlockPos(LANE_X, 2, 4), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos upper = new BlockPos(LANE_X, 2, 4);
        assertBlocked(helper, upper, helper.getBlockState(upper));
        helper.succeed();
    }

    /** 草丛没有碰撞箱，不挡（1.20.1 里叫 minecraft:grass） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayPassesShortGrass(GameTestHelper helper) {
        backstop(helper);
        helper.setBlock(new BlockPos(LANE_X, 1, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(LANE_X, 2, 4), Blocks.GRASS);
        Zombie behind = laneZombie(helper, 6.5D);
        fire(shooter(helper), steady());
        helper.assertTrue(hurt(behind), "草丛后面的僵尸应掉血");
        helper.assertBlockPresent(Blocks.GRASS, new BlockPos(LANE_X, 2, 4));
        behind.discard();
        helper.succeed();
    }

    /** 紧贴 1 格石墙背后的僵尸不掉血（原作每 0.5 格取样，能隔着薄墙打中墙后的东西） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayNotThroughWallEdge(GameTestHelper helper) {
        backstop(helper);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(LANE_X, y, 4), Blocks.STONE);
        }
        Zombie pressed = laneZombie(helper, 5.31D);                    // 碰撞箱 z 从 5.01 开始，紧贴墙背面
        ServerPlayer player = shooter(helper);
        aimAt(player, pressed.getBoundingBox().getCenter());
        RayShot.Result result = fire(player, steady());
        helper.assertFalse(hurt(pressed) || result.targets().contains(pressed), "墙后的僵尸不该被打中");
        pressed.discard();
        helper.succeed();
    }

    /**
     * 身后贴身的僵尸（中心在射手身后 0.6 格）不挨打（审查 GUNS-1：眼睛在它外扩 0.5 的容差框里，原先一律判中、距离 0；原作第 0 步以眼睛为中心取 1³ 的框，同样打得到）。
     * 前面同一条线上的僵尸照打，说明这一枪确实打出去了。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayNotBehindShooter(GameTestHelper helper) {
        backstop(helper);
        Zombie behind = zombie(helper, new Vec3(SHOOTER_FEET.x, SHOOTER_FEET.y, SHOOTER_FEET.z - 0.6D));
        Zombie ahead = laneZombie(helper, 6.5D);
        ServerPlayer player = shooter(helper);
        helper.assertTrue(behind.getBoundingBox().inflate(RayShot.HIT_TOLERANCE).contains(player.getEyePosition()),
                "站位不对：射手眼睛应在身后僵尸的容差框里（这条测的就是这种情况）");
        RayShot.Result result = fire(player, steady());
        helper.assertTrue(hurt(ahead), "前面的僵尸应掉血");
        helper.assertFalse(hurt(behind) || result.targets().contains(behind), "身后贴身的僵尸不该被打中");
        discard(List.of(behind, ahead));
        helper.succeed();
    }

    /**
     * 斜着打一排玻璃板，紧贴玻璃板背后的僵尸不挨打（审查 GUNS-1：射线还没碰到玻璃板，就先穿过了背后僵尸外扩 0.5 的容差框）。
     * 两枪：偏离法线 15° 和 45°，落点都在各自那只僵尸旁边 0.6 格（原先这两种都判中）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayNotThroughPaneOblique(GameTestHelper helper) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 4), pane);
            }
        }
        double paneFront = 4D + 7D / 16D;
        double eyeY = SHOOTER_FEET.y + 1.62D;
        double zombieZ = 4D + 9D / 16D + 0.3D + 0.01D;                 // 碰撞箱前沿离玻璃板背面 0.01
        List<Zombie> pressed = new ArrayList<>();
        List<Vec3> landings = new ArrayList<>();
        for (double angle : new double[]{15D, 45D}) {
            double landingX = SHOOTER_FEET.x + (paneFront - SHOOTER_FEET.z) * Math.tan(Math.toRadians(angle));
            landings.add(new Vec3(landingX, eyeY, paneFront));
            pressed.add(zombie(helper, new Vec3(landingX - 0.6D, 1D, zombieZ)));
        }
        ServerPlayer player = shooter(helper);
        for (int i = 0; i < landings.size(); i++) {
            Vec3 landing = helper.absoluteVec(landings.get(i));
            aimAt(player, landing);
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(landing.subtract(eye).scale(1.001D));
            helper.assertTrue(pressed.get(i).getBoundingBox().inflate(RayShot.HIT_TOLERANCE).clip(eye, end).isPresent(),
                    "站位不对：第 " + (i + 1) + " 枪应先擦过僵尸的容差框（这条测的就是这种情况）");
            RayShot.Result result = fire(player, steady());
            helper.assertTrue(Math.abs(result.to().z - landing.z) < 0.01D, "第 " + (i + 1) + " 枪应停在玻璃板上，终点 " + result.to());
            for (Zombie zombie : pressed) {
                helper.assertFalse(result.targets().contains(zombie), "第 " + (i + 1) + " 枪不该打中玻璃板背后的僵尸");
            }
        }
        helper.assertFalse(pressed.stream().anyMatch(GunsTests::hurt), "玻璃板背后的僵尸不该掉血");
        discard(pressed);
        helper.succeed();
    }

    /** 擦边容差照旧：射线从僵尸身边 0.35 格擦过照打，0.6 格外的不打 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayGrazeTolerance(GameTestHelper helper) {
        backstop(helper);
        Zombie grazed = zombie(helper, new Vec3(LANE_X + 0.5D + 0.3D + 0.35D, 1D, 5.5D));
        Zombie clear = zombie(helper, new Vec3(LANE_X + 0.5D - 0.3D - 0.6D, 1D, 7.5D));
        RayShot.Result result = fire(shooter(helper), steady());
        helper.assertTrue(hurt(grazed) && result.damaged().contains(grazed), "擦边 0.35 格的僵尸应掉血");
        helper.assertFalse(hurt(clear) || result.targets().contains(clear), "离射线 0.6 格的僵尸不该被打中");
        discard(List.of(grazed, clear));
        helper.succeed();
    }

    /** 骑着的猪（射手的坐骑）不挨打 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRaySkipsShooterMount(GameTestHelper helper) {
        for (int x = 3; x <= 7; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        Pig pig = EntityType.PIG.create(level);
        if (pig == null) {
            throw new IllegalStateException("建不出猪");
        }
        Vec3 at = helper.absoluteVec(new Vec3(5.5D, 2D, 4.5D));
        pig.moveTo(at.x, at.y, at.z, 0F, 0F);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        ServerPlayer player = shooter(helper, "ec5-rider", new Vec3(5.5D, 3D, 4.5D), 0F, 0F);
        try {
            helper.assertTrue(player.startRiding(pig, true), "应骑上猪");
            pig.positionRider(player);
            aimAt(player, pig.getBoundingBox().getCenter());
            RayShot.Result result = fire(player, steady());
            helper.assertFalse(hurt(pig) || result.targets().contains(pig), "自己骑的猪不该挨打");
        } finally {
            player.stopRiding();
            pig.discard();
        }
        helper.succeed();
    }

    /** 自己驯服的狼不挨打（射手不在世界里，按主人 UUID 认）；别人的狼照打 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRaySkipsAllies(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer player = shooter(helper);
        ServerLevel level = helper.getLevel();
        Wolf mine = EntityType.WOLF.create(level);
        Wolf theirs = EntityType.WOLF.create(level);
        if (mine == null || theirs == null) {
            throw new IllegalStateException("建不出狼");
        }
        List<Wolf> wolves = List.of(mine, theirs);
        double z = 4.5D;
        for (Wolf wolf : wolves) {
            Vec3 at = helper.absoluteVec(new Vec3(LANE_X + 0.5D, 2.2D, z));
            wolf.moveTo(at.x, at.y, at.z, 0F, 0F);
            wolf.setNoAi(true);
            wolf.setTame(true);
            level.addFreshEntity(wolf);
            z += 3D;
        }
        mine.setOwnerUUID(player.getUUID());
        theirs.setOwnerUUID(UUID.randomUUID());
        fire(player, steady());
        helper.assertFalse(hurt(mine), "自己的狼不该挨打");
        helper.assertTrue(hurt(theirs), "别人的狼照打（领地、PvP 由伤害事件管）");
        discard(wolves);
        helper.succeed();
    }

    /** 一线 40 只，上限设 32（测试覆盖值）→ 近的 32 只掉血，远的 8 只不掉 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsRayMaxTargets(GameTestHelper helper) {
        backstop(helper);
        List<Zombie> line = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            line.add(laneZombie(helper, 2.5D + i * 0.17D));
        }
        ServerPlayer player = shooter(helper);
        RayShot.Result result;
        GunConfig.setTestOverrides(null, null, 32);
        try {
            result = fire(player, steady());
        } finally {
            GunConfig.setTestOverrides(null, null, null);
        }
        helper.assertTrue(result.targets().size() == 32 && result.damaged().size() == 32, "应正好打中 32 只，实际 " + result.damaged().size());
        for (int i = 0; i < 40; i++) {
            boolean expected = i < 32;
            helper.assertTrue(hurt(line.get(i)) == expected, "第 " + i + " 只：期望" + (expected ? "" : "不") + "掉血");
        }
        discard(line);
        helper.succeed();
    }

    /** 散布两个轴都生效（原作水平散布不生效）：spread 20、固定种子 200 个方向，偏航、俯仰都有离散，且不超过 ±10° */
    @GameTest(template = EMPTY)
    public static void gunsSpreadBothAxes(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20260929L);
        double[] yaw = new double[200];
        double[] pitch = new double[200];
        for (int i = 0; i < 200; i++) {
            Vec3 d = RayShot.direction(0F, 0F, 20F, random);
            yaw[i] = Math.toDegrees(Math.atan2(-d.x, d.z));
            pitch[i] = Math.toDegrees(Math.asin(-d.y));
            helper.assertTrue(Math.abs(yaw[i]) <= 10.001D && Math.abs(pitch[i]) <= 10.001D, "散布超出 ±10°：" + yaw[i] + " / " + pitch[i]);
        }
        helper.assertTrue(stddev(yaw) > 1D, "偏航应有散布，标准差 " + stddev(yaw));
        helper.assertTrue(stddev(pitch) > 1D, "俯仰应有散布，标准差 " + stddev(pitch));
        Vec3 straight = RayShot.direction(10F, 30F, 0F, random);
        Vec3 look = Vec3.directionFromRotation(10F, 30F);
        helper.assertTrue(straight.distanceToSqr(look) < 1.0E-9D, "散布 0 时就是视线方向");
        helper.succeed();
    }

    private static double stddev(double[] values) {
        double mean = 0D;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;
        double sum = 0D;
        for (double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return Math.sqrt(sum / values.length);
    }

    /** 不生成射线实体：开火前后实体数不变，光束出口收到一道 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsNoProjectileEntity(GameTestHelper helper) {
        backstop(helper);
        ServerPlayer player = shooter(helper);
        ServerLevel level = helper.getLevel();
        AABB around = new AABB(player.blockPosition()).inflate(80D);
        int before = level.getEntities((Entity) null, around, e -> true).size();
        Beams beams = new Beams();
        RayShot.fire(player, steady(), RandomSource.create(3L), true, beams);
        int after = level.getEntities((Entity) null, around, e -> true).size();
        helper.assertTrue(before == after, "开火不该生成实体：" + before + " → " + after);
        helper.assertTrue(beams.beams.size() == 1, "应发一道光束");
        helper.succeed();
    }

    /** 盔甲架照原版规则不吃这种伤害（只认 player 伤害和箭） */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsArmorStandImmune(GameTestHelper helper) {
        backstop(helper);
        ServerLevel level = helper.getLevel();
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            throw new IllegalStateException("建不出盔甲架");
        }
        Vec3 at = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D));
        stand.moveTo(at.x, at.y, at.z, 0F, 0F);
        level.addFreshEntity(stand);
        RayShot.Result result = fire(shooter(helper), steady());
        helper.assertTrue(stand.isAlive() && !result.damaged().contains(stand), "盔甲架不该被打坏");
        stand.discard();
        helper.succeed();
    }

    /** 领地（Forge 端领地模组按 LivingAttackEvent 拦）：被拦的目标不掉血、不加矩阵损伤；射线照常停在墙上 */
    @GameTest(template = EMPTY_LARGE)
    public static void gunsClaimDeniesDamage(GameTestHelper helper) {
        backstop(helper);
        Zombie protectedZombie = laneZombie(helper, 6.5D);
        ServerPlayer shooter = shooter(helper);
        ServerPlayer owner = victim(helper, "ec5-claimown", new Vec3(8.5D, 1D, 5.5D));
        int matrixBefore = matrix(owner);
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        Consumer<LivingAttackEvent> claim = event -> {
            if ((event.getEntity() == protectedZombie || event.getEntity() == owner) && event.getSource().getEntity() == shooter) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, claim);
        RayShot.Result result;
        boolean ownerHit;
        try {
            server.setPvpAllowed(true);
            result = fire(shooter, steady());
            ownerHit = RayShot.hit(shooter, owner, steady());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
            server.setPvpAllowed(pvp);
        }
        helper.assertTrue(result.targets().contains(protectedZombie) && result.damaged().isEmpty() && !hurt(protectedZombie),
                "被领地拦下的僵尸不该掉血");
        helper.assertFalse(ownerHit || hurt(owner), "被领地拦下的玩家不该掉血");
        helper.assertTrue(matrix(owner) == matrixBefore, "被拦下不该加矩阵损伤");
        protectedZombie.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ PvP 与矩阵损伤

    /** PvP 关着：打玩家不掉血、矩阵不变 */
    @GameTest(template = EMPTY)
    public static void gunsPvpOffNoDamage(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-pvp-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-pvp-b", new Vec3(1.5D, 1D, 3.5D));
        int before = matrix(target);
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        boolean hit;
        try {
            server.setPvpAllowed(false);
            hit = RayShot.hit(shooter, target, steady());
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.assertFalse(hit || hurt(target), "PvP 关了不该掉血");
        helper.assertTrue(matrix(target) == before, "PvP 关了不该加矩阵损伤");
        helper.succeed();
    }

    /** PvP 开着：掉血，矩阵损伤 + floor(13.33 × 100) = 1333（D2 A 的默认值）；倍率 0.5 时伤害减半、矩阵照原作口径按枪的伤害算（不乘倍率） */
    @GameTest(template = EMPTY)
    public static void gunsPvpOnDamageAndMatrix(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-pvp-c", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-pvp-d", new Vec3(1.5D, 1D, 3.5D));
        ServerPlayer half = victim(helper, "ec5-pvp-e", new Vec3(3.5D, 1D, 3.5D));
        int before = matrix(target);
        int halfBefore = matrix(half);
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        boolean hit;
        boolean halfHit;
        try {
            server.setPvpAllowed(true);
            GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, null);
            hit = RayShot.hit(shooter, target, steady());
            GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 0.5D, null);
            halfHit = RayShot.hit(shooter, half, steady());
        } finally {
            GunConfig.setTestOverrides(null, null, null);
            server.setPvpAllowed(pvp);
        }
        float damage = steady().damage();
        helper.assertTrue(hit && Math.abs(target.getMaxHealth() - target.getHealth() - damage) < EPS,
                "应掉 " + damage + " 血，实际 " + (target.getMaxHealth() - target.getHealth()));
        helper.assertTrue(matrix(target) - before == 1333, "矩阵损伤应 +1333，实际 +" + (matrix(target) - before));
        helper.assertTrue(halfHit && Math.abs(half.getMaxHealth() - half.getHealth() - damage / 2F) < EPS, "倍率 0.5 时伤害减半");
        helper.assertTrue(matrix(half) - halfBefore == 1333, "矩阵损伤按枪的伤害算、不乘倍率 +1333，实际 +" + (matrix(half) - halfBefore));
        helper.succeed();
    }

    /** 创造模式的玩家：不掉血、不加矩阵损伤（原作照加） */
    @GameTest(template = EMPTY)
    public static void gunsCreativeNoMatrix(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-cre-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-cre-b", new Vec3(1.5D, 1D, 3.5D));
        target.setGameMode(GameType.CREATIVE);
        int before = matrix(target);
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        boolean hit;
        try {
            server.setPvpAllowed(true);
            hit = RayShot.hit(shooter, target, steady());
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.assertFalse(hit, "创造模式不该挨打");
        helper.assertTrue(matrix(target) == before, "创造模式不该加矩阵损伤");
        helper.succeed();
    }

    /** 同队、关了友伤：不掉血、不加矩阵；只有射手有队伍：照常掉血、不崩（原作队伍判断空指针） */
    @GameTest(template = EMPTY)
    public static void gunsTeamNoFriendlyFire(GameTestHelper helper) {
        String suffix = Integer.toHexString(helper.getLevel().getRandom().nextInt(0x7FFFFF));
        ServerPlayer shooter = shooter(helper, "ec5ta" + suffix, new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer mate = victim(helper, "ec5tb" + suffix, new Vec3(1.5D, 1D, 3.5D));
        ServerPlayer loner = victim(helper, "ec5tc" + suffix, new Vec3(3.5D, 1D, 3.5D));
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("ec5gun" + suffix);
        team.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam(shooter.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(mate.getScoreboardName(), team);
        int mateBefore = matrix(mate);
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        boolean mateHit;
        boolean lonerHit;
        try {
            server.setPvpAllowed(true);
            helper.assertTrue(RayShot.isAlly(shooter, mate), "同队算盟友（射线会跳过）");
            mateHit = RayShot.hit(shooter, mate, steady());
            lonerHit = RayShot.hit(shooter, loner, steady());
        } finally {
            server.setPvpAllowed(pvp);
            scoreboard.removePlayerTeam(team);
        }
        helper.assertFalse(mateHit || hurt(mate), "关了友伤的队友不该掉血");
        helper.assertTrue(matrix(mate) == mateBefore, "队友不该加矩阵损伤");
        helper.assertTrue(lonerHit && hurt(loner), "只有射手有队伍时照常掉血");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 打中就加矩阵损伤（GUNS-4 修订）

    /** 记下每次矩阵损伤特效（被打的人、强度） */
    private static final class MatrixFx implements RayShot.MatrixFxSink {
        final List<ServerPlayer> targets = new ArrayList<>();
        final List<Integer> intensities = new ArrayList<>();

        @Override
        public void matrixHit(ServerPlayer target, int intensity) {
            targets.add(target);
            intensities.add(intensity);
        }
    }

    /** 同一段同步代码里开 PvP、用默认的矩阵 / 倍率（或给定的倍率）跑 body，跑完置回 */
    private static void withPvp(GameTestHelper helper, Double multiplier, Runnable body) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, multiplier, null);
            body.run();
        } finally {
            GunConfig.setTestOverrides(null, null, null);
            server.setPvpAllowed(pvp);
        }
    }

    /**
     * 无敌帧里再打：第一下掉血、+1333；马上第二下 hurt 返回 false（无敌帧、同样的伤害不比上一下重），血不再掉，但矩阵照加（共 +2666），
     * 特效出口两次都收到（强度 2 = 中）
     */
    @GameTest(template = EMPTY)
    public static void gunsMatrixDuringInvulnerability(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-inv-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-inv-b", new Vec3(1.5D, 1D, 3.5D));
        int before = matrix(target);
        MatrixFx fx = new MatrixFx();
        RayShot.Hit[] hits = new RayShot.Hit[2];
        withPvp(helper, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, () -> {
            hits[0] = RayShot.strike(shooter, target, steady(), fx);
            hits[1] = RayShot.strike(shooter, target, steady(), fx);
        });
        float damage = steady().damage();
        helper.assertTrue(hits[0].damaged() && hits[0].matrix(), "第一下应掉血并加矩阵损伤");
        helper.assertTrue(target.invulnerableTime > 10, "第一下之后应在无敌帧里（invulnerableTime " + target.invulnerableTime + "）");
        helper.assertTrue(!hits[1].damaged() && hits[1].matrix(), "无敌帧里 hurt 返回 false，但矩阵照加");
        helper.assertTrue(Math.abs(target.getMaxHealth() - target.getHealth() - damage) < EPS, "只掉第一下的血");
        helper.assertTrue(matrix(target) - before == 2666, "两下都加：+2666，实际 +" + (matrix(target) - before));
        helper.assertTrue(fx.targets.size() == 2 && fx.targets.get(1) == target && fx.intensities.get(0) == 2,
                "两下都交给特效出口（强度 2），实际 " + fx.intensities);
        helper.succeed();
    }

    /** 伤害在 LivingHurtEvent 被别的模组取消：血不掉，矩阵照加 +1333 */
    @GameTest(template = EMPTY)
    public static void gunsMatrixWhenHurtCancelled(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-hc-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-hc-b", new Vec3(1.5D, 1D, 3.5D));
        int before = matrix(target);
        Consumer<LivingHurtEvent> cancel = event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, cancel);
        RayShot.Hit[] hit = new RayShot.Hit[1];
        try {
            withPvp(helper, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, () -> hit[0] = RayShot.strike(shooter, target, steady(), new MatrixFx()));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertFalse(hurt(target), "LivingHurtEvent 取消了，不该掉血");
        helper.assertTrue(hit[0].matrix() && matrix(target) - before == 1333, "矩阵照加 +1333，实际 +" + (matrix(target) - before));
        helper.succeed();
    }

    /** 伤害在 LivingDamageEvent 被取消（护甲之后那一步）：血不掉，矩阵照加 */
    @GameTest(template = EMPTY)
    public static void gunsMatrixWhenDamageCancelled(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-dc-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-dc-b", new Vec3(1.5D, 1D, 3.5D));
        int before = matrix(target);
        Consumer<LivingDamageEvent> cancel = event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDamageEvent.class, cancel);
        try {
            withPvp(helper, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, () -> RayShot.hit(shooter, target, steady()));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertFalse(hurt(target), "LivingDamageEvent 取消了，不该掉血");
        helper.assertTrue(matrix(target) - before == 1333, "矩阵照加 +1333，实际 +" + (matrix(target) - before));
        helper.succeed();
    }

    /** 打玩家倍率 0（只损伤矩阵）：不掉血、hurt 返回 false，矩阵照加 +1333；PvP 关着时倍率 0 也不加 */
    @GameTest(template = EMPTY)
    public static void gunsMatrixZeroMultiplier(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-zm-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-zm-b", new Vec3(1.5D, 1D, 3.5D));
        ServerPlayer peaceful = victim(helper, "ec5-zm-c", new Vec3(3.5D, 1D, 3.5D));
        int before = matrix(target);
        int peacefulBefore = matrix(peaceful);
        MatrixFx fx = new MatrixFx();
        RayShot.Hit[] hit = new RayShot.Hit[2];
        withPvp(helper, 0D, () -> hit[0] = RayShot.strike(shooter, target, steady(), fx));
        MinecraftServer server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 0D, null);
            hit[1] = RayShot.strike(shooter, peaceful, steady(), fx);
        } finally {
            GunConfig.setTestOverrides(null, null, null);
            server.setPvpAllowed(pvp);
        }
        helper.assertTrue(!hit[0].damaged() && !hurt(target), "倍率 0 不该掉血");
        helper.assertTrue(hit[0].matrix() && matrix(target) - before == 1333, "倍率 0 矩阵照加 +1333，实际 +" + (matrix(target) - before));
        helper.assertTrue(!hit[1].matrix() && matrix(peaceful) == peacefulBefore, "PvP 关着，倍率 0 也不加");
        helper.assertTrue(fx.targets.size() == 1, "只有真加了的那一下有特效，实际 " + fx.targets.size());
        helper.succeed();
    }

    /**
     * 领地模组的监听器排在本模组之后（同为 LOWEST、注册得晚）取消攻击：本模组的监听器先看到了事件，但 hurt 返回后看的是事件最终的取消状态——不加。
     * PvP 关着（{@code canHarmPlayer} 在攻击事件之前就返回）同样不加、不交给特效出口。
     */
    @GameTest(template = EMPTY)
    public static void gunsMatrixClaimLowestDenies(GameTestHelper helper) {
        ServerPlayer shooter = shooter(helper, "ec5-cl-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer target = victim(helper, "ec5-cl-b", new Vec3(1.5D, 1D, 3.5D));
        int before = matrix(target);
        Consumer<LivingAttackEvent> claim = event -> {
            if (event.getEntity() == target && event.getSource().getEntity() == shooter) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingAttackEvent.class, claim);
        MatrixFx fx = new MatrixFx();
        RayShot.Hit[] hit = new RayShot.Hit[1];
        try {
            withPvp(helper, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, () -> hit[0] = RayShot.strike(shooter, target, steady(), fx));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
        }
        helper.assertTrue(!hit[0].damaged() && !hit[0].matrix() && !hurt(target), "被排在最后的领地监听器拦下：不掉血、不算打中");
        helper.assertTrue(matrix(target) == before && fx.targets.isEmpty(), "被拦下不加矩阵损伤、没有特效");
        helper.succeed();
    }

    /**
     * Mohist 系混合端（{@code RayHitGate.setHybridForTest(true)} 模拟）：领地插件的 Bukkit 伤害事件夹在 LivingHurtEvent 和 LivingDamageEvent 之间，
     * 拦下时后者不会发。这里用「LivingHurtEvent 被取消 → 纯 Forge 不再发 LivingDamageEvent」造出同样的现象：混合端上当成插件拦了、不加。
     * 另测混合端上的无敌帧：射手 A 完整打中一次（放行）→ 马上再打（无敌帧里插件没被问到）按「A 刚被放行过」照加；
     * 射手 B 在同一段无敌帧里打，没有被放行的记录，不加。
     */
    @GameTest(template = EMPTY)
    public static void gunsMatrixHybridPluginDenied(GameTestHelper helper) {
        ServerPlayer shooterA = shooter(helper, "ec5-hy-a", new Vec3(1.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer shooterB = shooter(helper, "ec5-hy-b", new Vec3(3.5D, 1D, 1.5D), 0F, 0F);
        ServerPlayer denied = victim(helper, "ec5-hy-c", new Vec3(1.5D, 1D, 3.5D));
        ServerPlayer target = victim(helper, "ec5-hy-d", new Vec3(3.5D, 1D, 3.5D));
        int deniedBefore = matrix(denied);
        int before = matrix(target);
        Consumer<LivingHurtEvent> plugin = event -> {
            if (event.getEntity() == denied) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingHurtEvent.class, plugin);
        RayShot.Hit[] hits = new RayShot.Hit[4];
        try {
            RayHitGate.setHybridForTest(true);
            withPvp(helper, GunConfig.DEFAULT_PLAYER_DAMAGE_MULTIPLIER, () -> {
                hits[0] = RayShot.strike(shooterA, denied, steady(), new MatrixFx());
                hits[1] = RayShot.strike(shooterA, target, steady(), new MatrixFx());
                hits[2] = RayShot.strike(shooterA, target, steady(), new MatrixFx());
                hits[3] = RayShot.strike(shooterB, target, steady(), new MatrixFx());
            });
        } finally {
            RayHitGate.setHybridForTest(null);
            MinecraftForge.EVENT_BUS.unregister(plugin);
        }
        helper.assertTrue(!hits[0].matrix() && matrix(denied) == deniedBefore, "混合端上到了 LivingHurtEvent、没到 LivingDamageEvent = 插件拦了，不加");
        helper.assertTrue(hits[1].damaged() && hits[1].matrix(), "A 第一下完整放行：掉血、加");
        helper.assertTrue(!hits[2].damaged() && hits[2].matrix(), "A 马上再打（无敌帧）：刚被放行过，照加");
        helper.assertFalse(hits[3].matrix(), "B 在同一段无敌帧里打：没有被放行的记录，不加");
        helper.assertTrue(matrix(target) - before == 2666, "目标共 +2666，实际 +" + (matrix(target) - before));
        helper.succeed();
    }

    /** RayHitGate 的判定表（纯 Forge / 混合端各几种），和「最近被放行过」的有效期 */
    @GameTest(template = EMPTY)
    public static void gunsHitGateVerdict(GameTestHelper helper) {
        // 参数：攻击放行、到了 LivingHurtEvent、它被取消或清零、到了 LivingDamageEvent、混合端、最近被放行过
        helper.assertFalse(RayHitGate.verdict(false, true, false, true, false, true), "攻击被拦：不算");
        helper.assertTrue(RayHitGate.verdict(true, true, false, true, false, false), "纯 Forge 正常掉血：算");
        helper.assertTrue(RayHitGate.verdict(true, false, false, false, false, false), "纯 Forge 无敌帧 / 0 伤害：算");
        helper.assertTrue(RayHitGate.verdict(true, true, true, false, false, false), "纯 Forge LivingHurtEvent 被取消：算");
        helper.assertFalse(RayHitGate.verdict(true, true, false, false, false, false), "纯 Forge 放行了却没到 LivingDamageEvent（中间被拦）：不算");
        helper.assertTrue(RayHitGate.verdict(true, true, true, true, true, false), "混合端到了 LivingDamageEvent（插件放行，LivingHurtEvent 被取消也一样）：算");
        helper.assertFalse(RayHitGate.verdict(true, true, false, false, true, true), "混合端没到 LivingDamageEvent（插件拦了）：不算");
        helper.assertFalse(RayHitGate.verdict(true, true, true, false, true, true), "混合端 LivingHurtEvent 被取消且没到 LivingDamageEvent：插件拦了，不算");
        helper.assertTrue(RayHitGate.verdict(true, false, false, false, true, true), "混合端无敌帧、最近被放行过：算");
        helper.assertFalse(RayHitGate.verdict(true, false, false, false, true, false), "混合端无敌帧、没有被放行的记录：不算");
        helper.assertFalse(RayHitGate.isBukkitHybrid(), "GameTest 跑在纯 Forge 上，不该认成混合端");
        helper.succeed();
    }

    /** 服务端特效限频：同一目标 10 刻一次；每刻全服至多 8 个；刻数倒退（服务器重启）当过期 */
    @GameTest(template = EMPTY)
    public static void gunsMatrixFxThrottle(GameTestHelper helper) {
        GunMatrixFx.Throttle throttle = new GunMatrixFx.Throttle();
        UUID a = UUID.randomUUID();
        helper.assertTrue(throttle.tryAcquire(a, 100), "第一次放行");
        helper.assertFalse(throttle.tryAcquire(a, 101), "下一刻（加特林的下一发）不放行");
        helper.assertFalse(throttle.tryAcquire(a, 109), "9 刻后仍不放行");
        helper.assertTrue(throttle.tryAcquire(a, 110), "10 刻后放行");
        int granted = 0;
        for (int i = 0; i < 12; i++) {
            if (throttle.tryAcquire(UUID.randomUUID(), 200)) {
                granted++;
            }
        }
        helper.assertTrue(granted == GunMatrixFx.MAX_PER_TICK, "同一刻 12 个目标只放行 " + GunMatrixFx.MAX_PER_TICK + " 个，实际 " + granted);
        helper.assertTrue(throttle.tryAcquire(UUID.randomUUID(), 201), "下一刻预算重置");
        helper.assertTrue(throttle.tryAcquire(a, 5), "刻数倒退（服务器重启）不该一直卡住");
        helper.assertTrue(GunMatrixFx.intensity(5F) == 1 && GunMatrixFx.intensity(13.3F) == 2 && GunMatrixFx.intensity(86.7F) == 3, "强度按枪的伤害分三档");
        helper.succeed();
    }

    /** 矩阵受损特效包：实体 id 原样带过去、强度夹到 1~3；音效 sound.matrix_damage 已注册 */
    @GameTest(template = EMPTY)
    public static void gunsMatrixHitPacket(GameTestHelper helper) {
        helper.assertTrue(matrixRoundTrip(new S2CGunMatrixHitPacket(987_654, 2)).equals(new S2CGunMatrixHitPacket(987_654, 2)), "存读后应不变");
        helper.assertTrue(matrixRoundTrip(new S2CGunMatrixHitPacket(1, 9)).intensity() == 3
                && matrixRoundTrip(new S2CGunMatrixHitPacket(1, -4)).intensity() == 1, "强度应夹到 1~3");
        ResourceLocation sound = EssentialCraft5.id("sound.matrix_damage");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(sound) && ECGuns.MATRIX_DAMAGE.get().getLocation().equals(sound),
                "sound.matrix_damage 应已注册");
        helper.succeed();
    }

    private static S2CGunMatrixHitPacket matrixRoundTrip(S2CGunMatrixHitPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CGunMatrixHitPacket.encode(packet, buf);
            return S2CGunMatrixHitPacket.decode(buf);
        } finally {
            buf.release();
        }
    }

    // ------------------------------------------------------------------ 数据、铁砧

    /** mru_ray 不带 is_projectile（D1 A），不无视护甲、不在「击杀不掉落」里 */
    @GameTest(template = EMPTY)
    public static void gunsMruRayDamageType(GameTestHelper helper) {
        Holder<DamageType> type = ECDamageTypes.holder(helper.getLevel(), ECGuns.MRU_RAY);
        helper.assertFalse(type.is(DamageTypeTags.IS_PROJECTILE), "mru_ray 不该带 is_projectile");
        helper.assertFalse(type.is(DamageTypeTags.BYPASSES_ARMOR), "mru_ray 不该无视护甲");
        helper.assertFalse(type.is(ECDamageTypes.NO_DEATH_LOOT), "mru_ray 打死的照常掉落");
        helper.assertTrue("essentialcraft5.mru_ray".equals(type.value().msgId()), "死亡消息键 death.attack.essentialcraft5.mru_ray");
        helper.succeed();
    }

    /** 铁砧：魔力锭枪托的枪 + 魔力锭 → 磨损清零、4 级、1 个；+ 淡青锭 → 没有输出；没磨损 → 没有输出；混搭部件按枪托认 */
    @GameTest(template = EMPTY)
    public static void gunsAnvilRepairBaseMaterial(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack magical = new ItemStack(item("magical_ingot"));
        ItemStack pale = new ItemStack(item("pale_ingot"));
        ItemStack worn = mruGun(GunType.PISTOL, 1000);
        GunParts.setWear(worn, 50);
        GunParts.setFired(worn, 2);

        AnvilUpdateEvent ok = anvil(worn, magical, player);
        helper.assertTrue(!ok.getOutput().isEmpty() && GunParts.wear(ok.getOutput()) == 0, "同种材料应修好");
        helper.assertTrue(ok.getCost() == 4 && ok.getMaterialCost() == 1, "4 级、1 个材料");
        helper.assertTrue(GunParts.fired(ok.getOutput()) == 2 && mruOf(ok.getOutput()) == 1000, "只清磨损，别的不动");
        helper.assertTrue(GunParts.wear(worn) == 50, "左格原物不变");
        helper.assertTrue(anvil(worn, pale, player).getOutput().isEmpty(), "别的材料不能修");
        helper.assertTrue(anvil(mruGun(GunType.PISTOL, 0), magical, player).getOutput().isEmpty(), "没磨损不给修");

        ItemStack mixed = gun(GunType.RIFLE, new GunParts("pale", "mru", "mru", null, null), 0);
        GunParts.setWear(mixed, 10);
        helper.assertTrue(anvil(mixed, magical, player).getOutput().isEmpty(), "握把 / 机栓的材料不算");
        helper.assertTrue(GunParts.wear(anvil(mixed, pale, player).getOutput()) == 0, "按枪托的材料修");
        helper.succeed();
    }

    private static AnvilUpdateEvent anvil(ItemStack left, ItemStack right, ServerPlayer player) {
        AnvilUpdateEvent event = new AnvilUpdateEvent(left, right, "", 0, player);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }
}
