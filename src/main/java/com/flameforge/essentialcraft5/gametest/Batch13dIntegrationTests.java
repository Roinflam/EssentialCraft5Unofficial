package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ManaPrismEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaMidscape;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 第十三批 13d 几路接在一起以后才测得到的规则（batch13.md §6.4、§6.5）：
 * <ul>
 *   <li>浮游棱晶的附近上限：平时 64 格内 ≤ {@code mana_prism.nearby_cap}；地脉风暴里、群系本来不刷棱晶的地方（风暴追加的那一条）
 *       改按 {@code hoanna_spawns.storm_prism_max_nearby}，和刷怪表那一级的闸同一个上限。</li>
 *   <li>巢穴奖励从头到尾：中景观摆的三种核心类型都有 JSON；按 {@link HoannaNests#giveReward} 的掷法（最后死的那只当 THIS_ENTITY、领奖人当击杀者），
 *       N3 的奖励表（{@code from_nest} + {@code killed_by_player}）真的给东西、手册进度也打勾。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13dIntegrationTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";

    private Batch13dIntegrationTests() {
    }

    /** 风暴追加的棱晶按 storm_prism_max_nearby 封顶；没有风暴时同样的数量还放行（平时上限更高）；刷怪笼来问只比数、不预订 */
    @GameTest(template = LARGE, batch = "b13d_int_storm_prism")
    public static void hoannaStormPrismNearbyCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<?> type = ECHoannaNativesA.MANA_PRISM.get();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        int stormCap = ECHoannaNativesC.Config.spawns().stormPrismMaxNearby();
        int normalCap = ECHoannaNativesA.Config.prism().nearbyCap();
        helper.assertTrue(stormCap > 0 && normalCap > stormCap, "默认值：平时上限应高于风暴追加的上限（" + normalCap + " / " + stormCap + "）");
        List<Mob> prisms = new ArrayList<>();
        ECHoanna.setTestHoannaOverride(level);
        try {
            for (int i = 0; i < stormCap; i++) {
                Mob prism = (Mob) type.create(level);
                helper.assertTrue(prism != null, "棱晶应能创建");
                Vec3 at = helper.absoluteVec(new Vec3(2.5D + 2 * i, 3D, 8.5D));
                prism.moveTo(at.x, at.y, at.z, 0F, 0F);
                prism.setNoAi(true);
                SpawnOrigin.set(prism, Origin.SUMMONED);
                level.addFreshEntity(prism);
                prisms.add(prism);
            }
            MobSpawnRules.clearCache();
            helper.assertTrue(!ManaPrismEntity.stormExtraSpawn(level, pos, type), "没有风暴时不算风暴追加");
            helper.assertTrue(ManaPrismEntity.nearbyCapAllows(type, level, MobSpawnType.SPAWNER, pos),
                    "平时 64 格内 " + stormCap + " 只还没到 nearby_cap " + normalCap);

            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.LEY_STORM, 400), "应能开地脉风暴");
            MobSpawnRules.clearCache();
            helper.assertTrue(ManaPrismEntity.stormExtraSpawn(level, pos, type), "测试群系的怪物表里没有棱晶：风暴里算追加的");
            helper.assertTrue(!ManaPrismEntity.nearbyCapAllows(type, level, MobSpawnType.SPAWNER, pos),
                    "风暴追加的：64 格内已有 " + stormCap + " 只就不放");

            prisms.remove(prisms.size() - 1).discard();
            MobSpawnRules.clearCache();
            helper.assertTrue(ManaPrismEntity.nearbyCapAllows(type, level, MobSpawnType.SPAWNER, pos), "少一只以后又放行");
        } finally {
            HoannaEvents.stop(level);
            ECHoanna.clearTestOverride();
            for (Mob prism : prisms) {
                prism.discard();
            }
            MobSpawnRules.clearCache();
        }
        helper.succeed();
    }

    /** 中景观摆的三种核心都能找到类型；按核心的掷法给真玩家发一份冰封营地的奖励：异变之心 1、异变残质 2~4，进度打勾 */
    @GameTest(template = EMPTY)
    public static void hoannaNestRewardEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (HoannaMidscape.Kind kind : HoannaMidscape.Kind.values()) {
            helper.assertTrue(HoannaNestTypes.get(kind.nestType()) != null, "中景观 " + kind + " 摆的巢穴类型 " + kind.nestType() + " 没有 JSON");
        }
        HoannaNestType type = HoannaNestTypes.get(HoannaMidscape.Kind.FROZEN_CAMP.nestType());
        Mob last = (Mob) ECHoannaNativesA.FROSTBITTEN.get().create(level);
        helper.assertTrue(last != null, "霜蚀者应能创建");
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
        last.moveTo(at.x, at.y, at.z, 0F, 0F);
        SpawnOrigin.set(last, Origin.NEST);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "b13d-nest");
        player.setGameMode(GameType.SURVIVAL);
        Advancement advancement = level.getServer().getAdvancements().getAdvancement(NestCoreBlockEntity.ADVANCEMENT);
        helper.assertTrue(advancement != null, "手册进度 " + NestCoreBlockEntity.ADVANCEMENT + " 应已加载");

        HoannaNests.giveReward(level, type, last, player);

        int heart = count(player, ECMobDrops.ABERRANT_HEART.get());
        int residue = count(player, ECMobDrops.ABERRANT_RESIDUE.get());
        helper.assertTrue(heart == 1, "异变之心 1 个，实际 " + heart);
        helper.assertTrue(residue >= 2 && residue <= 4, "异变残质 2~4，实际 " + residue);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "领到奖励的人手册进度打勾");
        last.discard();
        helper.succeed();
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }
}
