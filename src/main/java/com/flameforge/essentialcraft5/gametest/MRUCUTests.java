package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.MRUCUContainerItem;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * MRU 聚集体模块的 GameTest：腐化泄漏的累积与结算、聚集体合并与消失、腐化效果叠加、移动魔杖的扫描距离，
 * 以及第三批安全回补的移动魔杖权限（当前格、目的格都要使用者能动，假玩家不行，被拒不扣耐久）、上古遗迹的稀有度。
 * <p>
 * 泄漏会汇到 32 格内最近的聚集体。别的测试（魔力工作台、辐射仓）留下的聚集体、以及测试结束后仍在工作的机器的泄漏会干扰计数，
 * 所以精确计数都放在同一 tick 内同步完成：泄漏测试独占一个批次（批次之间顺序执行），开头先结算掉全部泄漏、再清场；
 * 需要等 tick 的断言只检查「有没有发生」，不卡死具体数值。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MRUCUTests {

    private static final String EMPTY = "empty";
    private static final String BATCH_LEAK = "essentialcraft5_mrucu_leak";

    private MRUCUTests() {
    }

    private static List<MRUPresenceEntity> presences(GameTestHelper helper, double radius) {
        AABB area = new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(radius);
        return helper.getLevel().getEntitiesOfClass(MRUPresenceEntity.class, area, Entity::isAlive);
    }

    /** 泄漏先累积、结算时新建聚集体；32 格内已有聚集体时加给它；0 不做事；正常的 20 tick 结算也会生效 */
    @GameTest(template = EMPTY, batch = BATCH_LEAK)
    public static void corruptionAccumulatesAndSettles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 清场：先把全维度攒着的泄漏结算掉（可能在附近新建聚集体），再删掉附近所有聚集体。本测试独占批次，不会删到别的测试的东西
        ECCorruption.settle(level);
        presences(helper, 64).forEach(Entity::discard);
        BlockPos machine = helper.absolutePos(new BlockPos(2, 1, 2));

        ECCorruption.increaseAt(level, machine, 0);
        ECCorruption.settle(level);
        helper.assertTrue(presences(helper, 40).isEmpty(), "泄漏 0 不应生成聚集体");

        for (int i = 0; i < 3; ++i) {
            ECCorruption.increaseAt(level, machine, 30);
        }
        helper.assertTrue(presences(helper, 40).isEmpty(), "泄漏应先累积，不立即生成聚集体");
        ECCorruption.settle(level);
        List<MRUPresenceEntity> found = presences(helper, 40);
        helper.assertTrue(found.size() == 1, "结算后应新建 1 个聚集体，实际 " + found.size());
        MRUPresenceEntity presence = found.get(0);
        helper.assertTrue(presence.getMRU() == 90, "新聚集体应有 90 MRU，实际 " + presence.getMRU());
        helper.assertTrue(presence.getFlag() && !presence.isAlwaysStay(), "泄漏生成的聚集体应 flag=true、alwaysStay=false");
        helper.assertTrue(presence.position().distanceTo(Vec3.atCenterOf(machine)) < 0.01D, "聚集体应在泄漏方块的中心");
        float balance = presence.getBalance();
        helper.assertTrue(balance >= 0F && balance <= 2F, "平衡值应在 0~2，实际 " + balance);

        ECCorruption.increaseAt(level, machine.offset(5, 0, 0), 10);
        ECCorruption.settle(level);
        helper.assertTrue(presences(helper, 40).size() == 1, "32 格内已有聚集体时不应再新建");
        helper.assertTrue(presence.getMRU() == 100, "泄漏应加给已有的聚集体，实际 " + presence.getMRU());

        // 不手动结算，等世界 tick 按 20 tick 周期结算（其它还在工作的机器也可能往里加，所以只查「至少」）
        ECCorruption.increaseAt(level, machine, 7);
        helper.succeedWhen(() -> {
            helper.assertTrue(presence.getMRU() >= 107, "20 tick 结算应把泄漏加给聚集体，实际 " + presence.getMRU());
            presence.discard();
        });
    }

    /** 合并的数值（同步调用，不受别的测试干扰）：平衡值按 MRU 加权、超上限的部分丢失、两个 0 MRU 合并不出 NaN、继承 alwaysStay */
    @GameTest(template = EMPTY)
    public static void presenceAbsorbMath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 不放进世界，直接调 absorb
        MRUPresenceEntity small = MRUPresenceEntity.create(level, 0D, 0D, 0D, 1000, 0.5F, false, true);
        MRUPresenceEntity big = MRUPresenceEntity.create(level, 0D, 0D, 0D, 3000, 1.5F, false, false);
        big.absorb(small);
        helper.assertTrue(small.isRemoved(), "被吞掉的一方应被移除");
        helper.assertTrue(big.getMRU() == 4000, "合并后应有 4000 MRU，实际 " + big.getMRU());
        helper.assertTrue(Math.abs(big.getBalance() - 1.25F) < 1.0E-4F, "平衡值应按 MRU 加权为 1.25，实际 " + big.getBalance());
        helper.assertTrue(big.isAlwaysStay(), "吞掉 alwaysStay 的聚集体后应继承 alwaysStay");

        MRUPresenceEntity full = MRUPresenceEntity.create(level, 0D, 0D, 0D, 15000, 1F, false, false);
        full.absorb(MRUPresenceEntity.create(level, 0D, 0D, 0D, 8000, 1F, false, false));
        helper.assertTrue(full.getMRU() == MRUPresenceEntity.MAX_MRU, "超过上限的部分应丢失，实际 " + full.getMRU());

        MRUPresenceEntity emptyA = MRUPresenceEntity.create(level, 0D, 0D, 0D, 0, 0.3F, false, false);
        emptyA.absorb(MRUPresenceEntity.create(level, 0D, 0D, 0D, 0, 1.7F, false, false));
        helper.assertTrue(Math.abs(emptyA.getBalance() - 0.3F) < 1.0E-4F, "两个 0 MRU 合并时平衡值不变（原作算出 NaN），实际 " + emptyA.getBalance());
        helper.succeed();
    }

    /** 世界里同格的两个聚集体会自动合并；0 MRU 的聚集体不是 alwaysStay 就消失 */
    @GameTest(template = EMPTY)
    public static void presenceMergesAndExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.5D, 1.5D));
        MRUPresenceEntity small = MRUPresenceEntity.create(level, at.x, at.y, at.z, 1000, 0.5F, false, true);
        MRUPresenceEntity big = MRUPresenceEntity.create(level, at.x + 0.2D, at.y, at.z, 3000, 1.5F, false, true);
        // 第一轮结算就会检查 MRU 是否归零；新聚集体在加进世界后的第一个 tick 就结算，早于任何泄漏结算
        Vec3 far = helper.absoluteVec(new Vec3(1.5D, 1.5D, 3.5D));
        MRUPresenceEntity leaving = MRUPresenceEntity.create(level, far.x, far.y, far.z, 0, 1F, false, false);
        MRUPresenceEntity staying = MRUPresenceEntity.create(level, far.x + 2D, far.y, far.z, 0, 1F, false, true);
        level.addFreshEntity(small);
        level.addFreshEntity(big);
        level.addFreshEntity(leaving);
        level.addFreshEntity(staying);

        helper.succeedWhen(() -> {
            helper.assertTrue(small.isRemoved() && big.isAlive(), "MRU 少的一方应被同格 MRU 多的一方吞掉");
            helper.assertTrue(leaving.isRemoved(), "0 MRU 且不是 alwaysStay 的聚集体应消失");
            helper.assertTrue(staying.isAlive(), "alwaysStay 的聚集体 0 MRU 也应保留");
            big.discard();
            staying.discard();
        });
    }

    /** MRU 腐化叠加（矩阵后遗症，不是药水效果）：首次 200 tick、0 级；之后每层 +2 tick，等级 = 叠加前时长 / 2000 */
    @GameTest(template = EMPTY)
    public static void corruptionEffectStacks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        IPlayerData data = PlayerDataEvents.get(player);
        helper.assertTrue(data != null, "模拟玩家应有玩家数据");
        String id = Affliction.MRU_CORRUPTION.id();

        MRUCorruption.stack(player, 1);
        helper.assertTrue(data.getAfflictionTicks(id) == 200 && data.getAfflictionLevel(id) == 0,
                "首次叠加应为 200 tick、0 级，实际 " + data.getAfflictionTicks(id) + " / " + data.getAfflictionLevel(id));

        MRUCorruption.stack(player, 5);
        helper.assertTrue(data.getAfflictionTicks(id) == 210 && data.getAfflictionLevel(id) == 0,
                "再叠 5 层应为 210 tick、0 级，实际 " + data.getAfflictionTicks(id) + " / " + data.getAfflictionLevel(id));

        data.setAffliction(id, 3999, 0);
        MRUCorruption.stack(player, 2);
        // 第一层叠加前 3999 → 1 级、时长变 4001；第二层叠加前 4001 → 2 级、时长变 4003
        helper.assertTrue(data.getAfflictionTicks(id) == 4003 && data.getAfflictionLevel(id) == 2,
                "3999 tick 上叠 2 层应为 4003 tick、2 级，实际 " + data.getAfflictionTicks(id) + " / " + data.getAfflictionLevel(id));
        helper.assertFalse(player.hasEffect(ECMRUCU.MRU_CORRUPTION.get()), "不挂药水效果");
        helper.succeed();
    }

    /** 移动魔杖能找到 4 格外的聚集体（原作的 break 只查前方第一段），并把它往玩家这边拉 0.1 格（真玩家，没有领地） */
    @GameTest(template = EMPTY)
    public static void moverReachesAndPulls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 target = helper.absoluteVec(new Vec3(2.5D, 1.5D, 4.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, target.x, target.y, target.z, 500, 1F, false, true);
        level.addFreshEntity(presence);

        Player player = ProtectionFrameworkTests.realPlayer(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(2.5D, 1.5D, 0.5D));
        // yRot 0 朝 +Z（南），xRot 0 平视
        player.moveTo(feet.x, feet.y, feet.z, 0F, 0F);

        MRUPresenceEntity found = MRUMoverItem.findTarget(level, player);
        helper.assertTrue(found == presence, "应找到正前方 4 格处的聚集体");

        ItemStack wand = new ItemStack(ECMRUCU.MRU_MOVER.get());
        double before = presence.getZ();
        wand.getItem().onUseTick(level, player, wand, 71999);
        double moved = presence.getZ() - before;
        presence.discard();
        helper.assertTrue(Math.abs(moved + 0.1D) < 1.0E-6D, "不潜行时应往玩家这边拉 0.1 格，实际移动 " + moved);
        helper.succeed();
    }

    /**
     * 聚集体收容瓶：身上没有 MRU、聚集体那格领地不许时都不收；收成时付存量 10%（向上取整），瓶里记下 MRU / 平衡值 / 是否保留、世界里那团消失；
     * 装着时不能再收；右键放到别处，放出来的那团数据不变、第一轮结算跳过，瓶子变空
     */
    @GameTest(template = EMPTY)
    public static void containerCapturesAndReleases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.5D, 4.5D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, 1001, 0.3F, false, false);
        level.addFreshEntity(presence);
        ItemStack flask = new ItemStack(ECGear.MRUCU_CONTAINER.get());
        try {
            ServerPlayer broke = facingSouth(helper);
            broke.setGameMode(GameType.SURVIVAL);
            helper.assertFalse(MRUCUContainerItem.capture(flask, level, broke), "身上没有 MRU 时不该收");
            helper.assertTrue(presence.isAlive() && !MRUCUContainerItem.isFilled(flask), "没收成时聚集体还在、瓶子还是空的");

            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(2, 1, 4))) {
                ServerPlayer denied = facingSouth(helper);
                denied.setGameMode(GameType.SURVIVAL);
                denied.getInventory().setItem(9, shard(500));
                helper.assertFalse(MRUCUContainerItem.capture(flask, level, denied), "聚集体那格领地不许时不该收");
                helper.assertTrue(presence.isAlive(), "领地不许时聚集体还在");
            }

            ServerPlayer player = facingSouth(helper);
            player.setGameMode(GameType.SURVIVAL);
            ItemStack shard = shard(500);
            player.getInventory().setItem(9, shard);
            helper.assertTrue(MRUCUContainerItem.capture(flask, level, player), "应收成");
            MRUCUContainerItem.Contents contents = MRUCUContainerItem.contents(flask);
            helper.assertTrue(!presence.isAlive() && contents != null && contents.mru() == 1001 && Math.abs(contents.balance() - 0.3F) < 1.0E-6F
                    && !contents.alwaysStay(), "瓶里应记下 1001 MRU、平衡值 0.3、不保留，实际 " + contents);
            int left = shard.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandler::getMRU).orElse(-1);
            helper.assertTrue(left == 500 - 101, "应付 1001 × 10% 向上取整 = 101 MRU，灵魂碎片剩 " + left);

            Vec3 again = helper.absoluteVec(new Vec3(2.5D, 1.5D, 3.5D));
            MRUPresenceEntity second = MRUPresenceEntity.create(level, again.x, again.y, again.z, 200, 1F, false, false);
            level.addFreshEntity(second);
            helper.assertFalse(MRUCUContainerItem.capture(flask, level, player), "装着时不能再收");
            helper.assertTrue(second.isAlive(), "装着时第二团不该被收走");
            second.discard();

            Vec3 dest = helper.absoluteVec(new Vec3(5.5D, 1.5D, 2.5D));
            helper.assertTrue(MRUCUContainerItem.release(flask, level, player, dest), "应放成");
            helper.assertFalse(MRUCUContainerItem.isFilled(flask), "放完瓶子应变空");
            MRUPresenceEntity out = MRUPresenceEntity.findClosest(level, BlockPos.containing(dest), 2);
            helper.assertTrue(out != null && out.getMRU() == 1001 && Math.abs(out.getBalance() - 0.3F) < 1.0E-6F && out.getFlag()
                    && out.position().distanceTo(dest) < 1.0E-6D, "放出来的应是 1001 MRU、平衡值 0.3、跳过第一轮，实际 " + out);
            out.discard();
        } finally {
            presence.discard();
        }
        helper.succeed();
    }

    /**
     * 收容瓶倒掉：装着的瓶子按住用完，瓶里那团散掉、瓶子变空；已付的收取费不退；空瓶倒不了；
     * 装着的瓶子时长是倒掉的 60 刻，空瓶是收的 40 刻
     */
    @GameTest(template = EMPTY)
    public static void containerDispersesFilledFlask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = facingSouth(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack flask = new ItemStack(ECGear.MRUCU_CONTAINER.get());
        helper.assertFalse(MRUCUContainerItem.disperse(flask, level, player), "空瓶没有东西可倒");
        helper.assertTrue(flask.getItem().getUseDuration(flask) == MRUCUContainerItem.USE_TICKS, "空瓶按住的时长是收的 40 刻");
        MRUCUContainerItem.setContents(flask, new MRUCUContainerItem.Contents(12345, 1.2F, false));
        helper.assertTrue(flask.getItem().getUseDuration(flask) == MRUCUContainerItem.DISPERSE_TICKS, "装着的瓶子按住的时长是倒掉的 60 刻");
        ItemStack finished = flask.getItem().finishUsingItem(flask, level, player);
        helper.assertTrue(finished == flask && !MRUCUContainerItem.isFilled(flask), "按住用完应倒空瓶子");
        // 同一批里别的测试也在这个世界里摆聚集体，只看玩家身边这一小圈
        helper.assertTrue(level.getEntitiesOfClass(MRUPresenceEntity.class, player.getBoundingBox().inflate(1.5D)).isEmpty(),
                "倒掉不会在玩家身边放出聚集体");
        helper.assertFalse(MRUCUContainerItem.disperse(flask, level, player), "倒空以后不能再倒");
        helper.succeed();
    }

    private static ItemStack shard(int mru) {
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        shard.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return shard;
    }

    /** 站在 (2.5, 1.5, 0.5) 朝南（+Z）平视的真玩家 */
    private static ServerPlayer facingSouth(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Vec3 feet = helper.absoluteVec(new Vec3(2.5D, 1.5D, 0.5D));
        player.moveTo(feet.x, feet.y, feet.z, 0F, 0F);
        return player;
    }

    /**
     * 第三批安全回补（P1）：移动魔杖挪之前，聚集体当前格和目的格都要使用者自己能动。
     * 目的格被模拟领地拒绝：不动、二阶魔杖不扣耐久；当前格被拒：不动；假玩家一律不动；都放行时照常拉、每 20 刻扣 1 点耐久
     */
    @GameTest(template = EMPTY)
    public static void moverRespectsClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 聚集体在 (2, 1, 4) 格靠北的边上：往北拉 0.1 格就进了 (2, 1, 3)
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1.5D, 4.05D));
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, 500, 1F, false, true);
        level.addFreshEntity(presence);
        BlockPos current = new BlockPos(2, 1, 4);
        BlockPos destination = new BlockPos(2, 1, 3);
        // 一阶没有耐久；二阶（ECGear）有，按注册名取，没接线就用一阶、跳过耐久断言
        ResourceLocation t2 = EssentialCraft5.id("mru_mover_t2");
        ItemStack wand = ForgeRegistries.ITEMS.containsKey(t2)
                ? new ItemStack(ForgeRegistries.ITEMS.getValue(t2)) : new ItemStack(ECMRUCU.MRU_MOVER.get());
        boolean damageable = wand.isDamageableItem();
        // 剩余使用时间是 20 的倍数的那一刻才扣耐久
        int useTick = 71980;
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(destination)) {
            ServerPlayer player = facingSouth(helper);
            helper.assertTrue(MRUMoverItem.findTarget(level, player) == presence, "应找到正前方的聚集体");
            Vec3 before = presence.position();
            wand.getItem().onUseTick(level, player, wand, useTick);
            helper.assertTrue(presence.position().equals(before), "目的格被拒时不应动，实际移到 " + presence.position());
            helper.assertTrue(wand.getDamageValue() == 0, "被拒时不应扣耐久");
            helper.assertTrue(claims.probes(destination) == 1, "应以使用者身份探测一次目的格");

            FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            Vec3 feet = helper.absoluteVec(new Vec3(2.5D, 1.5D, 0.5D));
            fake.moveTo(feet.x, feet.y, feet.z, 0F, 0F);
            claims.allow(destination);
            wand.getItem().onUseTick(level, fake, wand, useTick);
            helper.assertTrue(presence.position().equals(before), "假玩家不应能挪");

            // 当前格被拒：换一个没有缓存的新玩家
            claims.deny(current);
            ServerPlayer other = facingSouth(helper);
            wand.getItem().onUseTick(level, other, wand, useTick);
            helper.assertTrue(presence.position().equals(before), "当前格被拒时不应动");
            helper.assertTrue(wand.getDamageValue() == 0, "被拒时不应扣耐久");

            // 两格都放行：照常往玩家这边拉 0.1 格，二阶扣 1 点耐久
            claims.allow(current);
            ServerPlayer free = facingSouth(helper);
            wand.getItem().onUseTick(level, free, wand, useTick);
            double moved = presence.getZ() - before.z;
            helper.assertTrue(Math.abs(moved + 0.1D) < 1.0E-6D, "两格都能动时应拉 0.1 格，实际移动 " + moved);
            if (damageable) {
                helper.assertTrue(wand.getDamageValue() == 1, "挪动的那一刻应扣 1 点耐久，实际 " + wand.getDamageValue());
            }
        } finally {
            presence.discard();
        }
        helper.succeed();
    }

    /**
     * 第三批安全回补（P3，policy §6.1，P-1）：上古遗迹的 placed feature 最前面是 rarity_filter chance 16
     * （每区块先掷 1/16，再照原作尝试一次：实测每 190~260 区块一座，刷怪笼密度低于原版的 3/4）
     */
    @GameTest(template = EMPTY)
    public static void elderRuinsAreRare(GameTestHelper helper) {
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .get(EssentialCraft5.id("elder_mrucu"));
        helper.assertTrue(feature != null, "缺少 placed_feature essentialcraft5:elder_mrucu");
        List<PlacementModifier> placement = feature.placement();
        helper.assertTrue(!placement.isEmpty() && placement.get(0) instanceof RarityFilter, "第一个放置规则应是 rarity_filter，实际 " + placement);
        JsonElement json = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, placement.get(0)).result().orElse(null);
        helper.assertTrue(json != null && json.isJsonObject() && json.getAsJsonObject().get("chance").getAsInt() == 16,
                "稀有度应为 1/16，实际 " + json);
        helper.succeed();
    }
}
