package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.item.MagicalWaterItem;
import com.flameforge.essentialcraft5.corruption.MRUCorruption;
import com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.bauble.BaubleHarvestEvents;
import com.flameforge.essentialcraft5.item.bauble.PortableMatrixDestructorItem;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.loot.DungeonLootInjection;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.UBMRUEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.ECShieldBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 特殊饰品（ECBaubles，batch3 §3.8）的 GameTest：同种只生效一件、缓存换装后失效、ω 击杀 UBMRU ×2、配置禁用不生效、
 * ν 只连锁一层且 ≤16、ψ 新目标不是村民、两个戴 σ 的人互打不来回弹、地牢注入表只出已注册且没被禁用的饰品（平均 1.5 件；32 件 × 权重 3 + 空 32）、
 * 海纳尔批 5 件（δ ε λ μ ξ）的注册、槽位与效果标签、
 * ρ 不吃权限探测和被领地拒绝的破坏、ρ 在 LOWEST 之前换掉经验、机器和法杖代挖不给 ρ / ο、θ、ι、η、医师腰带、便携矩阵换能器、φ、
 * ο（放下再挖回来的不加、陶罐的陶片不加）、τ、χ（含 Curios 修饰品时取消拾取）、κ、风之好感与标签。
 * 第十批饰品平衡：新数值的默认值、经验指环 20 点换 1 点经验、π ×2 和 π / ρ 只认矿石、χ 另给 50%、θ 冷却、ζ / ι 只算打实的攻击
 * （混合端的插件在 LivingHurtEvent 之后拦下时不扣费、不补伤害）、ζ 的 tick 末补清、τ / ψ 不管首领、ε 只认敌对生物。
 * 审查后补的：π 的 5% 不给「挖下来就是方块自己」的（精准采集、远古残骸）和工具不对的、θ 的冷却退出重进不清、
 * ζ / ι 不算混合端上被盾牌整下挡掉的一下（{@code util/ECShieldBlocks} 的记录规则一起测）、提示里跟着配置走的几段。
 * 10b 审查后补的：ζ / ι 不算伤害最后被改成 0（没取消）的一下，含对方的 σ 免掉的那一下。
 * <p>
 * 模拟玩家（{@code makeMockPlayer}）不在世界里 tick，Curios 不会自己发 CurioChangeEvent；它生在世界原点，要用的测试先挪到测试区域里。
 * 要真 ServerPlayer 的（权限探测）用 {@link ProtectionFrameworkTests#realPlayer}。配置禁用用 {@code CuriosCompat.Config.setDisabledForTest}，不改真配置。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BaublesTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private BaublesTests() {
    }

    // ------------------------------------------------------------------ 工具

    private static Player mockPlayer(GameTestHelper helper, double x, double y, double z) {
        Player player = helper.makeMockPlayer();
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ICuriosItemHandler curios(GameTestHelper helper, LivingEntity entity) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity).resolve();
        if (inventory.isEmpty()) {
            helper.fail("玩家没有 Curios 饰品栏");
        }
        return inventory.get();
    }

    /** 直接放进饰品槽（不走 canEquip，模拟指令塞进去），并让缓存作废 */
    private static ItemStack wear(GameTestHelper helper, Player player, String slot, int index, Item item) {
        ItemStack stack = new ItemStack(item);
        ICurioStacksHandler handler = curios(helper, player).getCurios().get(slot);
        if (handler == null || handler.getSlots() <= index) {
            helper.fail("玩家没有 Curios 槽位 " + slot + "#" + index);
        }
        handler.getStacks().setStackInSlot(index, stack);
        CuriosCompat.invalidate(player);
        return stack;
    }

    /** 戴进这件饰品自己那种槽的第 0 格 */
    private static ItemStack wear(GameTestHelper helper, Player player, RegistryObject<? extends SpecialBaubleItem> item) {
        SpecialBaubleItem bauble = item.get();
        return wear(helper, player, bauble.slot(), 0, bauble);
    }

    private static IPlayerData data(GameTestHelper helper, Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            helper.fail("玩家没有 EC5 玩家数据");
        }
        return data;
    }

    // ------------------------------------------------------------------ 同种只生效一件、缓存

    @GameTest(template = EMPTY)
    public static void onlyOneOfKindWorks(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        SpecialBaubleItem omega = ECBaubles.OMEGA_RING.get();
        ItemStack first = wear(helper, player, CuriosCompat.RING, 0, omega);

        SlotContext otherRing = new SlotContext(CuriosCompat.RING, player, 1, false, true);
        SlotContext sameSlot = new SlotContext(CuriosCompat.RING, player, 0, false, true);
        SlotContext cosmetic = new SlotContext(CuriosCompat.RING, player, 1, true, true);
        helper.assertFalse(omega.canEquip(otherRing, new ItemStack(omega)), "已经戴着一枚 ω 指环时第二枚应戴不上");
        helper.assertTrue(omega.canEquip(sameSlot, new ItemStack(omega)), "换同一格里的那枚应放行");
        helper.assertTrue(omega.canEquip(cosmetic, new ItemStack(omega)), "装饰槽应放行");
        helper.assertTrue(ECBaubles.CHI_RING.get().canEquip(otherRing, new ItemStack(ECBaubles.CHI_RING.get())), "不同的饰品应放行");

        // 用指令塞进第二枚（绕过 canEquip）：只有缓存里的那一枚生效
        ItemStack second = wear(helper, player, CuriosCompat.NECKLACE, 0, omega);
        RandomSource random = RandomSource.create(1L);
        float a = omega.getModifiedValue(400F, first, random, player);
        float b = omega.getModifiedValue(400F, second, random, player);
        helper.assertTrue((a == 800F) != (b == 800F), "两枚 ω 应只有一枚生效，实际 " + a + " / " + b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cacheInvalidatesAfterUnequip(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        SpecialBaubleItem omega = ECBaubles.OMEGA_RING.get();
        ItemStack stack = wear(helper, player, CuriosCompat.RING, 0, omega);
        helper.assertTrue(CuriosCompat.has(player, omega), "戴上后应查得到");
        helper.assertTrue(omega.getModifiedValue(100F, stack, RandomSource.create(1L), player) == 200F, "戴着时应 ×2");

        curios(helper, player).getCurios().get(CuriosCompat.RING).getStacks().setStackInSlot(0, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new CurioChangeEvent(player, CuriosCompat.RING, 0, stack, ItemStack.EMPTY));
        helper.assertFalse(CuriosCompat.has(player, omega), "摘下并发 CurioChangeEvent 后缓存应作废");
        helper.assertTrue(omega.getModifiedValue(100F, stack, RandomSource.create(1L), player) == 100F, "摘下的那枚不应再生效");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 击杀 UBMRU

    /** ω：击杀 UBMRU ×2；被 general.baubles.disabled 关掉后不生效 */
    @GameTest(template = EMPTY)
    public static void omegaDoublesAndDisabledDoesNothing(GameTestHelper helper) {
        Player killer = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, killer);
        Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 3));
        wear(helper, killer, ECBaubles.OMEGA_RING);

        long seed = 12345L;
        int base = UBMRUEvents.killReward(victim, RandomSource.create(seed));
        data.setUBMRU(0);
        killer.getRandom().setSeed(seed);
        UBMRUEvents.handleDeath(victim, killer.damageSources().playerAttack(killer));
        helper.assertTrue(data.getUBMRU() == base * 2, "戴 ω 击杀应得 ×2：基础 " + base + "，实际 " + data.getUBMRU());

        try {
            CuriosCompat.Config.setDisabledForTest(List.of(ECBaubles.OMEGA_RING.getId().toString()));
            helper.assertFalse(CuriosCompat.has(killer, ECBaubles.OMEGA_RING.get()), "被禁用的饰品查询时应当不存在");
            data.setUBMRU(0);
            killer.getRandom().setSeed(seed);
            UBMRUEvents.handleDeath(victim, killer.damageSources().playerAttack(killer));
            helper.assertTrue(data.getUBMRU() == base, "被禁用后应只得基础值 " + base + "，实际 " + data.getUBMRU());
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.succeed();
    }

    /** ν：一刀只连锁一层、最多 16 只（20 只 1 血的骷髅，杀一只应再死 16 只、剩 3 只） */
    @GameTest(template = EMPTY_LARGE)
    public static void nuChainsOneLayerAndCaps(GameTestHelper helper) {
        Player player = mockPlayer(helper, 5.5D, 1D, 5.5D);
        wear(helper, player, ECBaubles.NU_AMULET);
        List<Skeleton> skeletons = new ArrayList<>();
        for (int x = 1; x <= 9; x += 2) {
            for (int z = 2; z <= 8; z += 2) {
                Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, new BlockPos(x, 1, z));
                skeleton.setHealth(1F);
                skeletons.add(skeleton);
            }
        }
        helper.assertTrue(skeletons.size() == 20, "应摆 20 只骷髅");
        int max = ECBaubles.Limits.nuMaxTargets();
        skeletons.get(0).hurt(player.damageSources().playerAttack(player), 100F);
        long dead = skeletons.stream().filter(s -> !s.isAlive()).count();
        long expected = 1 + Math.min(max, skeletons.size() - 1);
        helper.assertTrue(dead == expected, "应死 1 + " + Math.min(max, 19) + " 只（只连锁一层、上限 " + max + "），实际 " + dead);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 战斗

    /** ψ：新目标只能是敌对生物，不会是村民；冷却内沿用上次的目标 */
    @GameTest(template = EMPTY_LARGE)
    public static void psiNeverTargetsVillagers(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        wear(helper, player, ECBaubles.PSI_RING);
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 1, 3));
        Zombie first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        first.setTarget(player);
        helper.assertTrue(first.getTarget() == player, "附近只有村民时不应换目标，实际 " + first.getTarget());

        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, new BlockPos(5, 1, 5));
        Zombie second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 1, 5));
        second.setTarget(player);
        LivingEntity target = second.getTarget();
        helper.assertTrue(target != null && target != player && target != villager && target instanceof Enemy,
                "盯上戴 ψ 的玩家的怪应改打别的敌对生物，实际 " + target);
        second.setTarget(player);
        helper.assertTrue(second.getTarget() == target, "冷却内应沿用上次换的目标，实际 " + second.getTarget());
        helper.assertTrue(skeleton.isAlive() && villager.isAlive(), "测试生物应还活着");
        helper.succeed();
    }

    /**
     * σ：两个戴 σ 的玩家互打，反回去的伤害不会再被反回来（重复到出现一次反弹为止，10% 几率，200 次都不出现的概率约 7e-10）。
     * 反弹在本 tick 末结算（第十批 A5），这里每下之后直接调 {@code flushSigma}
     */
    @GameTest(template = EMPTY)
    public static void sigmaDoesNotBounceForever(GameTestHelper helper) {
        Player attacker = mockPlayer(helper, 1.5D, 1D, 1.5D);
        Player victim = mockPlayer(helper, 2.5D, 1D, 1.5D);
        wear(helper, attacker, ECBaubles.SIGMA_RING);
        wear(helper, victim, ECBaubles.SIGMA_RING);
        for (int i = 0; i < 200; i++) {
            attacker.setHealth(20F);
            victim.setHealth(20F);
            attacker.invulnerableTime = 0;
            victim.invulnerableTime = 0;
            victim.hurt(victim.damageSources().playerAttack(attacker), 4F);
            BaubleCombatEvents.flushSigma();
            if (victim.getHealth() == 20F) {
                helper.assertTrue(attacker.getHealth() == 16F, "反弹时攻击者应受原样的 4 点，实际剩 " + attacker.getHealth());
                helper.succeed();
                return;
            }
            helper.assertTrue(victim.getHealth() == 16F && attacker.getHealth() == 20F, "没反弹时应照常受伤");
        }
        helper.fail("200 次都没有出现反弹");
    }

    /** θ：致命一击扣「伤害 × 5000」UBMRU 抵掉；不致命的不扣 */
    @GameTest(template = EMPTY)
    public static void thetaSavesFromFatalHit(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.THETA_BELT);
        data.setUBMRU(200000);
        player.setHealth(20F);
        player.hurt(player.damageSources().generic(), 30F);
        helper.assertTrue(player.isAlive() && player.getHealth() == 20F, "致命一击应被抵掉，实际生命 " + player.getHealth());
        helper.assertTrue(data.getUBMRU() == 200000 - 30 * BaubleEffects.THETA_COST_PER_DAMAGE, "应扣 30 × 5000，实际剩 " + data.getUBMRU());
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().generic(), 4F);
        helper.assertTrue(player.getHealth() == 16F, "不致命的伤害照常受，实际 " + player.getHealth());
        helper.assertTrue(data.getUBMRU() == 50000, "不致命的伤害不应扣 UBMRU，实际 " + data.getUBMRU());

        // 冷却（第十批）：抵掉一次以后，冷却中的致命伤不再抵、不扣 UBMRU；冷却结束又能抵
        Item theta = ECBaubles.THETA_BELT.get();
        int cooldown = ECBaubles.Limits.thetaCooldown();
        helper.assertTrue(player.getCooldowns().isOnCooldown(theta) == (cooldown > 0), "抵掉以后应进入冷却（冷却 " + cooldown + " tick）");
        data.setUBMRU(1_000_000);
        LivingDamageEvent during = new LivingDamageEvent(player, player.damageSources().generic(), 30F);
        BaubleCombatEvents.onBaubleLivingDamage(during);
        if (cooldown > 0) {
            helper.assertTrue(during.getAmount() == 30F && data.getUBMRU() == 1_000_000,
                    "冷却中的致命伤不应再抵、不应扣 UBMRU，实际伤害 " + during.getAmount() + "、剩 " + data.getUBMRU());
        }
        // 冷却到期：物品冷却清掉，持久数据里的到期刻也拨到现在
        player.getCooldowns().removeCooldown(theta);
        player.getPersistentData().putLong(BaubleCombatEvents.THETA_READY_KEY, player.level().getGameTime());
        LivingDamageEvent after = new LivingDamageEvent(player, player.damageSources().generic(), 30F);
        BaubleCombatEvents.onBaubleLivingDamage(after);
        helper.assertTrue(after.getAmount() == 0F && data.getUBMRU() == 1_000_000 - 30 * BaubleEffects.THETA_COST_PER_DAMAGE,
                "冷却结束后应照常抵掉，实际伤害 " + after.getAmount() + "、剩 " + data.getUBMRU());
        helper.succeed();
    }

    /**
     * θ 的冷却退出重进不清：原版物品冷却不存盘，抵掉一次以后清掉物品冷却（等于重进）仍然不抵、不扣；
     * 登录时把剩下的刻数补回物品冷却（饰品栏照样画遮罩）；到期以后又能抵，登录时顺手删掉过期的记录
     */
    @GameTest(template = EMPTY)
    public static void thetaCooldownSurvivesRelog(GameTestHelper helper) {
        int cooldown = ECBaubles.Limits.thetaCooldown();
        if (cooldown <= 0) {
            helper.succeed();   // 配成没有冷却时没什么可测
            return;
        }
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.THETA_BELT);
        Item theta = ECBaubles.THETA_BELT.get();
        data.setUBMRU(1_000_000);
        player.setHealth(20F);
        LivingDamageEvent first = new LivingDamageEvent(player, player.damageSources().generic(), 30F);
        BaubleCombatEvents.onBaubleLivingDamage(first);
        int afterFirst = data.getUBMRU();
        helper.assertTrue(first.getAmount() == 0F, "第一下致命伤应被抵掉");
        long remaining = BaubleCombatEvents.thetaRemainingTicks(player);
        helper.assertTrue(remaining == cooldown, "持久数据里应记下剩 " + cooldown + " 刻，实际 " + remaining);

        player.getCooldowns().removeCooldown(theta);   // 物品冷却不存盘：重进以后就没了
        helper.assertTrue(BaubleCombatEvents.thetaCoolingDown(player), "物品冷却没了、持久数据还没到期，仍算冷却中");
        LivingDamageEvent relog = new LivingDamageEvent(player, player.damageSources().generic(), 30F);
        BaubleCombatEvents.onBaubleLivingDamage(relog);
        helper.assertTrue(relog.getAmount() == 30F && data.getUBMRU() == afterFirst,
                "重进以后冷却没到期，致命伤不应再抵、不应扣 UBMRU，实际伤害 " + relog.getAmount() + "、剩 " + data.getUBMRU());

        BaubleCombatEvents.onBaubleThetaLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.assertTrue(player.getCooldowns().isOnCooldown(theta), "登录时应把剩下的冷却补回物品冷却（饰品栏画遮罩）");

        player.getCooldowns().removeCooldown(theta);
        player.getPersistentData().putLong(BaubleCombatEvents.THETA_READY_KEY, player.level().getGameTime() - 1L);
        BaubleCombatEvents.onBaubleThetaLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.assertFalse(player.getCooldowns().isOnCooldown(theta) || player.getPersistentData().contains(BaubleCombatEvents.THETA_READY_KEY),
                "到期以后登录不补冷却、删掉过期的记录");
        LivingDamageEvent expired = new LivingDamageEvent(player, player.damageSources().generic(), 30F);
        BaubleCombatEvents.onBaubleLivingDamage(expired);
        helper.assertTrue(expired.getAmount() == 0F, "到期以后又能抵");
        helper.succeed();
    }

    /**
     * η：打出的伤害翻倍；ι：拆出 10%，tick 末补成魔法伤害，打完把无敌窗口原样放回（无敌帧还是 20、lastHurt 还是 5：
     * 窗口里 5 点打不进，没蓄满的连点照原版挡掉，不再像清 0 那样下一刻就整下打实）
     */
    @GameTest(template = EMPTY)
    public static void etaDoublesAndIotaSplits(GameTestHelper helper) {
        Player eta = mockPlayer(helper, 1.5D, 1D, 1.5D);
        wear(helper, eta, ECBaubles.ETA_AMULET);
        Pig first = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 1));
        first.hurt(eta.damageSources().playerAttack(eta), 2F);
        helper.assertTrue(first.getHealth() == first.getMaxHealth() - 4F, "η 打出的伤害应 ×2，实际剩 " + first.getHealth());

        Player iota = mockPlayer(helper, 1.5D, 1D, 3.5D);
        wear(helper, iota, ECBaubles.IOTA_RING);
        Pig second = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 3));
        float full = second.getMaxHealth();
        second.hurt(iota.damageSources().playerAttack(iota), 5F);
        helper.assertTrue(Math.abs(second.getHealth() - (full - 4.5F)) < 1.0E-4F, "ι 应先只打 90%，实际剩 " + second.getHealth());
        BaubleCombatEvents.flushIota();
        helper.assertTrue(Math.abs(second.getHealth() - (full - 5F)) < 1.0E-4F, "tick 末应补上 10% 的魔法伤害，实际剩 " + second.getHealth());
        helper.assertTrue(second.invulnerableTime == 20, "补完无敌帧应放回原来那一下的 20，实际 " + second.invulnerableTime);
        helper.assertFalse(second.hurt(helper.getLevel().damageSources().generic(), 5F), "lastHurt 放回 5：窗口里 5 点打不进");
        float mid = second.getHealth();
        helper.assertTrue(second.hurt(helper.getLevel().damageSources().generic(), 5.5F) && Math.abs(mid - second.getHealth() - 0.5F) < 1.0E-4F,
                "窗口里 5.5 只打进差额 0.5，实际 " + (mid - second.getHealth()));
        helper.succeed();
    }

    // ------------------------------------------------------------------ 地牢注入

    /** 注入表只出本模块已注册、没被禁用的饰品，平均每箱约 1.5 件 */
    @GameTest(template = EMPTY)
    public static void dungeonInjectOnlyEnabledBaubles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().getLootData().getLootTable(DungeonLootInjection.SIMPLE_DUNGEON);
        helper.assertTrue(table != LootTable.EMPTY, "注入表应存在");
        Set<Item> baubles = new HashSet<>();
        for (RegistryObject<? extends SpecialBaubleItem> object : ECBaubles.all()) {
            baubles.add(object.get());
        }
        helper.assertTrue(baubles.size() == 32, "应有 32 件饰品，实际 " + baubles.size());
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .create(LootContextParamSets.CHEST);
        int chests = 400;
        int total = 0;
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < chests; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                helper.assertTrue(baubles.contains(stack.getItem()), "注入表开出了不是特殊饰品的 " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
                total += stack.getCount();
                seen.add(stack.getItem());
            }
        }
        double average = total / (double) chests;
        helper.assertTrue(average > 1.3D && average < 1.7D, "平均每箱应约 1.5 件，实际 " + average);
        helper.assertTrue(seen.size() >= 25, "400 箱里应见到大部分饰品，实际 " + seen.size() + " 种");

        Item zeta = ECBaubles.ZETA_AMULET.get();
        try {
            CuriosCompat.Config.setDisabledForTest(List.of(ECBaubles.ZETA_AMULET.getId().toString()));
            for (int i = 0; i < chests; i++) {
                for (ItemStack stack : table.getRandomItems(params)) {
                    helper.assertFalse(stack.is(zeta), "被禁用的 ζ 护身符不应再从地牢箱子里开出来");
                }
            }
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 注入表恢复原作「3 : 1」（batch6a §5.8 裁定 16）：32 件各权重 3 + 空 32（每抽 3/4 出饰品、每件 3/128），每件带 not_disabled 条件，
     * 件数和 ECBaubles 一一对应；海纳尔批的 5 件真的开得出来。
     */
    @GameTest(template = EMPTY)
    public static void dungeonPool32(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation file = EssentialCraft5.id("loot_tables/" + DungeonLootInjection.SIMPLE_DUNGEON.getPath() + ".json");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            helper.fail("找不到注入表文件 " + file);
            return;
        }
        JsonObject root;
        try (Reader reader = resource.get().openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("注入表读不出来：" + e);
            return;
        }
        JsonArray pools = root.getAsJsonArray("pools");
        helper.assertTrue(pools.size() == 1, "注入表应只有 1 个池，实际 " + pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        JsonObject rolls = pool.getAsJsonObject("rolls");
        helper.assertTrue(rolls.get("min").getAsInt() == 1 && rolls.get("max").getAsInt() == 3, "每箱照原作抽 1~3 次，实际 " + rolls);

        Set<String> expected = new HashSet<>();
        for (RegistryObject<? extends SpecialBaubleItem> object : ECBaubles.all()) {
            expected.add(object.getId().toString());
        }
        helper.assertTrue(expected.size() == 32, "ECBaubles 应有 32 件，实际 " + expected.size());
        Set<String> listed = new HashSet<>();
        int emptyWeight = 0;
        int itemWeight = 0;
        for (JsonElement element : pool.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            String type = entry.get("type").getAsString();
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            if (type.equals("minecraft:empty")) {
                emptyWeight += weight;
                continue;
            }
            helper.assertTrue(type.equals("minecraft:item"), "注入表里只该有物品和空条目，实际 " + type);
            String name = entry.get("name").getAsString();
            helper.assertTrue(listed.add(name), "注入表里 " + name + " 出现了两次");
            helper.assertTrue(weight == 3, name + " 的权重应是 3，实际 " + weight);
            itemWeight += weight;
            JsonArray conditions = entry.getAsJsonArray("conditions");
            helper.assertTrue(conditions != null && conditions.size() == 1, name + " 应带一个 not_disabled 条件");
            JsonObject condition = conditions.get(0).getAsJsonObject();
            helper.assertTrue(condition.get("condition").getAsString().equals("essentialcraft5:not_disabled")
                    && condition.get("item").getAsString().equals(name), name + " 的 not_disabled 条件应指向它自己，实际 " + condition);
        }
        helper.assertTrue(listed.equals(expected), "注入表的饰品应和 ECBaubles 一一对应：多了 " + difference(listed, expected) + "，少了 " + difference(expected, listed));
        helper.assertTrue(itemWeight == 96 && emptyWeight == 32, "权重应是 32 × 3 + 空 32，实际饰品 " + itemWeight + "、空 " + emptyWeight);

        LootTable table = level.getServer().getLootData().getLootTable(DungeonLootInjection.SIMPLE_DUNGEON);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .create(LootContextParamSets.CHEST);
        Set<Item> hoanna = Set.of(ECBaubles.DELTA_RING.get(), ECBaubles.EPSILON_BELT.get(), ECBaubles.LAMBDA_RING.get(), ECBaubles.MU_RING.get(),
                ECBaubles.XI_BELT.get());
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < 600; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                seen.add(stack.getItem());
            }
        }
        for (Item item : hoanna) {
            helper.assertTrue(seen.contains(item), "600 箱里应开出过 " + ForgeRegistries.ITEMS.getKey(item) + "（期望约 28 次）");
        }
        helper.succeed();
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.removeAll(b);
        return result;
    }

    /** 海纳尔批的 5 件（δ ε λ μ ξ）：注册了、槽位对、在 special_baubles 和各自的效果标签里（一个标签只有一件）、戴上后按标签查得到、被禁用就查不到 */
    @GameTest(template = EMPTY)
    public static void specialBaublesRegistered(GameTestHelper helper) {
        record Expect(RegistryObject<SpecialBaubleItem> item, String slot, TagKey<Item> slotTag, TagKey<Item> effect) {
        }
        List<Expect> expects = List.of(
                new Expect(ECBaubles.DELTA_RING, CuriosCompat.RING, ECTags.CURIOS_RING, ECBaubles.HALVES_RADIATION),
                new Expect(ECBaubles.EPSILON_BELT, CuriosCompat.BELT, ECTags.CURIOS_BELT, ECBaubles.DROPS_EMBERS),
                new Expect(ECBaubles.LAMBDA_RING, CuriosCompat.RING, ECTags.CURIOS_RING, ECBaubles.PURGES_RADIATION),
                new Expect(ECBaubles.MU_RING, CuriosCompat.RING, ECTags.CURIOS_RING, ECBaubles.HOANNA_ATTUNED),
                new Expect(ECBaubles.XI_BELT, CuriosCompat.BELT, ECTags.CURIOS_BELT, ECBaubles.IRRADIATES_ON_HIT));
        helper.assertTrue(ECBaubles.all().size() == 32, "ECBaubles 应有 32 件，实际 " + ECBaubles.all().size());
        for (Expect expect : expects) {
            String id = String.valueOf(expect.item().getId());
            helper.assertTrue(expect.item().isPresent(), id + " 应已注册");
            helper.assertTrue(ECBaubles.all().contains(expect.item()), id + " 应在 ECBaubles.all() 里（地牢池、手册、测试都按它遍历）");
            SpecialBaubleItem item = expect.item().get();
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(item.slot().equals(expect.slot()) && stack.is(expect.slotTag()), id + " 应戴在 " + expect.slot() + " 槽");
            helper.assertTrue(stack.is(ECBaubles.SPECIAL_BAUBLES), id + " 应在 special_baubles 标签里");
            helper.assertTrue(stack.getMaxStackSize() == 1, id + " 应不能堆叠（原作 setMaxStackSize(1)）");
            for (Expect other : expects) {
                helper.assertTrue(new ItemStack(other.item().get()).is(expect.effect()) == (other == expect),
                        "效果标签 " + expect.effect().location() + " 应只有 " + id + "，" + other.item().getId() + (other == expect ? " 却不在里面" : " 却也在里面"));
            }

            Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
            wear(helper, player, expect.item());
            helper.assertTrue(CuriosCompat.has(player, expect.effect()), "戴上 " + id + " 后应按标签 " + expect.effect().location() + " 查得到");
            try {
                CuriosCompat.Config.setDisabledForTest(List.of(id));
                helper.assertFalse(CuriosCompat.has(player, expect.effect()), "被禁用的 " + id + " 不应再按标签查得到");
            } finally {
                CuriosCompat.Config.setDisabledForTest(null);
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 挖掘、掉落

    /** ρ：权限探测不给 UBMRU；被领地拒绝的破坏不给；真破坏按经验 × 1000 给、经验清零 */
    @GameTest(template = EMPTY)
    public static void rhoIgnoresProbesAndDeniedBreaks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.DIAMOND_ORE);
        BlockState state = level.getBlockState(pos);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        wear(helper, player, ECBaubles.RHO_RING);
        IPlayerData data = data(helper, player);
        data.setUBMRU(0);

        helper.assertTrue(ECProtection.canEdit(player, level, pos), "没有领地时应能动");
        helper.assertTrue(data.getUBMRU() == 0, "权限探测不应给 UBMRU，实际 " + data.getUBMRU());

        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(rel)) {
            BlockEvent.BreakEvent denied = new BlockEvent.BreakEvent(level, pos, state, player);
            helper.assertTrue(denied.getExpToDrop() > 0, "拿钻石镐挖钻石矿应有经验");
            MinecraftForge.EVENT_BUS.post(denied);
            helper.assertTrue(denied.isCanceled(), "模拟领地应拒绝");
            helper.assertTrue(data.getUBMRU() == 0, "被拒的破坏不应给 UBMRU，实际 " + data.getUBMRU());

            claims.allow(rel);
            BlockEvent.BreakEvent allowed = new BlockEvent.BreakEvent(level, pos, state, player);
            // Curios 在 HIGHEST 优先级按「玩家时运 + 饰品时运」重新随机算一次经验（CuriosEventHandler.onBreakBlock），
            // 构造时的经验值不作数；在 NORMAL 优先级（ρ 是 LOW）记下 ρ 实际看到的经验值。
            // 另在 LOWEST 记一次：有的模组在 LOWEST 取经验值自己掉，ρ 必须已经在它之前把经验换掉（BAU-2）
            int[] seen = {-1};
            int[] lowest = {-1};
            java.util.function.Consumer<BlockEvent.BreakEvent> probe = event -> {
                if (event == allowed) {
                    seen[0] = event.getExpToDrop();
                }
            };
            java.util.function.Consumer<BlockEvent.BreakEvent> lastProbe = event -> {
                if (event == allowed) {
                    lowest[0] = event.getExpToDrop();
                }
            };
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, probe);
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, lastProbe);
            try {
                MinecraftForge.EVENT_BUS.post(allowed);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(probe);
                MinecraftForge.EVENT_BUS.unregister(lastProbe);
            }
            int xp = seen[0];
            helper.assertTrue(xp > 0, "ρ 之前的经验值应大于 0，实际 " + xp);
            helper.assertFalse(allowed.isCanceled(), "放行后不应被取消");
            helper.assertTrue(data.getUBMRU() == xp * ECBaubles.Limits.rhoUbmruPerXp(),
                    "应得经验 " + xp + " × " + ECBaubles.Limits.rhoUbmruPerXp() + "，实际 " + data.getUBMRU());
            helper.assertTrue(allowed.getExpToDrop() == 0, "经验应换掉、不再掉落");
            helper.assertTrue(lowest[0] == 0, "LOWEST 的监听器（有的模组在这里取经验）看到的应是 ρ 换完的 0，实际 " + lowest[0]);
        }
        helper.succeed();
    }

    /**
     * ο：只有非方块掉落时才扣 500、每组 +1；放下再挖回来的方块自身物品（红石线、绊线、没长成的作物）不加，成熟作物照样加；
     * τ：生物掉落每组 +0~2、扣 1000，没掉落不扣
     */
    @GameTest(template = EMPTY)
    public static void omicronAndTauChargeOnlyWhenTheyAdd(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.OMICRON_AMULET);
        wear(helper, player, ECBaubles.TAU_RING);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 1));
        data.setUBMRU(5000);

        List<ItemStack> blocks = new ObjectArrayList<>(List.of(new ItemStack(Items.COBBLESTONE)));
        helper.assertFalse(BaubleEffects.omicronBonus(player, pos, Blocks.STONE.defaultBlockState(), blocks), "掉的全是方块时 ο 不应生效");
        helper.assertTrue(data.getUBMRU() == 5000 && blocks.get(0).getCount() == 1, "掉的全是方块时不应扣 UBMRU");
        List<ItemStack> ore = new ObjectArrayList<>(List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.DIAMOND), new ItemStack(Items.WHEAT_SEEDS)));
        helper.assertTrue(BaubleEffects.omicronBonus(player, pos, Blocks.GRASS.defaultBlockState(), ore), "有非方块掉落时 ο 应生效");
        helper.assertTrue(ore.get(0).getCount() == 1 && ore.get(1).getCount() == 2 && ore.get(2).getCount() == 2,
                "非方块掉落（钻石、草丛掉的种子）每组 +1，方块不加");
        helper.assertTrue(data.getUBMRU() == 5000 - BaubleEffects.OMICRON_COST, "应扣 500，实际剩 " + data.getUBMRU());
        BlockPos far = helper.absolutePos(new BlockPos(2, 1, 1)).offset(40, 0, 0);
        helper.assertFalse(BaubleEffects.omicronBonus(player, far, Blocks.DIAMOND_ORE.defaultBlockState(), new ObjectArrayList<>(List.of(new ItemStack(Items.DIAMOND)))),
                "够不着的方块（别的模组远程替玩家挖）不应生效");

        // 放下再挖回来：红石线 → 红石粉、绊线 → 线、0 龄小麦 → 种子、0 龄胡萝卜 → 胡萝卜，都不加、不扣
        data.setUBMRU(5000);
        BlockState[] replanted = {Blocks.REDSTONE_WIRE.defaultBlockState(), Blocks.TRIPWIRE.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState(), Blocks.CARROTS.defaultBlockState()};
        Item[] ownForm = {Items.REDSTONE, Items.STRING, Items.WHEAT_SEEDS, Items.CARROT};
        for (int i = 0; i < replanted.length; i++) {
            List<ItemStack> back = new ObjectArrayList<>(List.of(new ItemStack(ownForm[i])));
            helper.assertFalse(BaubleEffects.omicronBonus(player, pos, replanted[i], back),
                    "挖掉 " + replanted[i] + " 还回来的 " + ForgeRegistries.ITEMS.getKey(ownForm[i]) + " 不应 +1");
            helper.assertTrue(back.get(0).getCount() == 1 && data.getUBMRU() == 5000, "放下再挖回来的不应加、不应扣 UBMRU");
        }
        // 成熟作物照样加：长成的小麦掉的小麦和种子、长成的胡萝卜
        BlockState grownWheat = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        List<ItemStack> harvest = new ObjectArrayList<>(List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS, 2)));
        helper.assertTrue(BaubleEffects.omicronBonus(player, pos, grownWheat, harvest), "长成的小麦应生效");
        helper.assertTrue(harvest.get(0).getCount() == 2 && harvest.get(1).getCount() == 3, "长成的小麦掉的小麦、种子每组 +1");
        BlockState grownCarrots = ((CropBlock) Blocks.CARROTS).getStateForAge(((CropBlock) Blocks.CARROTS).getMaxAge());
        List<ItemStack> carrots = new ObjectArrayList<>(List.of(new ItemStack(Items.CARROT, 3)));
        helper.assertTrue(BaubleEffects.omicronBonus(player, pos, grownCarrots, carrots) && carrots.get(0).getCount() == 4,
                "长成的胡萝卜应 +1");
        helper.assertTrue(data.getUBMRU() == 5000 - 2 * BaubleEffects.OMICRON_COST, "两次各扣 500，实际剩 " + data.getUBMRU());

        data.setUBMRU(1000);
        helper.assertFalse(BaubleEffects.tauBonus(player, new ObjectArrayList<>(), RandomSource.create(1L)), "没有掉落时 τ 不应扣费");
        helper.assertTrue(data.getUBMRU() == 1000, "没有掉落时不应扣 UBMRU");
        List<ItemStack> drops = new ObjectArrayList<>(List.of(new ItemStack(Items.BONE)));
        helper.assertTrue(BaubleEffects.tauBonus(player, drops, RandomSource.create(1L)), "有掉落时 τ 应生效");
        int count = drops.get(0).getCount();
        helper.assertTrue(count >= 1 && count <= 3 && data.getUBMRU() == 0, "每组 +0~2、扣 1000，实际 " + count + " 个、剩 " + data.getUBMRU());
        helper.assertFalse(BaubleEffects.tauBonus(player, new ObjectArrayList<>(List.of(new ItemStack(Items.BONE))), RandomSource.create(1L)),
                "UBMRU 不够时 τ 不应生效");
        helper.succeed();
    }

    /**
     * ο：方块实体里的动态掉落（陶罐敲碎掉的陶片 / 砖）原样还回来，不 +1、不扣 UBMRU；
     * 对照：同一个玩家、同一把镐走战利品表挖钻石矿照常 +1、扣 500（证明全局战利品修改器在跑）
     */
    @GameTest(template = EMPTY)
    public static void omicronSkipsDecoratedPotSherds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.OMICRON_AMULET);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);

        BlockPos oreRel = new BlockPos(2, 1, 1);
        helper.setBlock(oreRel, Blocks.DIAMOND_ORE);
        BlockPos orePos = helper.absolutePos(oreRel);
        data.setUBMRU(5000);
        List<ItemStack> oreDrops = Block.getDrops(level.getBlockState(orePos), level, orePos, null, player, pickaxe);
        int diamonds = oreDrops.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(diamonds == 2 && data.getUBMRU() == 5000 - BaubleEffects.OMICRON_COST,
                "对照：戴 ο 挖钻石矿应 1 + 1、扣 500，实际 " + diamonds + " 个、剩 " + data.getUBMRU());

        BlockPos potRel = new BlockPos(1, 1, 2);
        helper.setBlock(potRel, Blocks.DECORATED_POT);
        BlockPos potPos = helper.absolutePos(potRel);
        if (!(level.getBlockEntity(potPos) instanceof DecoratedPotBlockEntity pot)) {
            helper.fail("陶罐没有方块实体");
            return;
        }
        pot.load(new DecoratedPotBlockEntity.Decorations(Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD,
                Items.ARMS_UP_POTTERY_SHERD, Items.BRICK).save(new CompoundTag()));
        data.setUBMRU(5000);
        List<ItemStack> potDrops = Block.getDrops(level.getBlockState(potPos), level, potPos, pot, player, pickaxe);
        int pieces = potDrops.stream().filter(stack -> !stack.is(Items.DECORATED_POT)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(pieces == 4, "拿镐敲碎陶罐应原样还 4 块陶片 / 砖，实际 " + pieces + "：" + potDrops);
        helper.assertTrue(data.getUBMRU() == 5000, "陶片不 +1 就不应扣 UBMRU，实际剩 " + data.getUBMRU());
        helper.succeed();
    }

    /**
     * 法杖代挖（ECProtection.breakGuarded，岩浆法杖用的就是它）：ρ 不给 UBMRU、ο 不加掉落也不扣；
     * 对照：同样的经验值、玩家自己挖（直接发 BreakEvent）时 ρ 照常给
     */
    @GameTest(template = EMPTY)
    public static void guardedBreakGivesNoBaubleRewards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        wear(helper, player, ECBaubles.RHO_RING);
        wear(helper, player, ECBaubles.OMICRON_AMULET);
        IPlayerData data = data(helper, player);
        BlockPos guardedRel = new BlockPos(1, 1, 1);
        BlockPos handRel = new BlockPos(2, 1, 1);
        helper.setBlock(guardedRel, Blocks.DIAMOND_ORE);
        helper.setBlock(handRel, Blocks.DIAMOND_ORE);
        BlockPos guarded = helper.absolutePos(guardedRel);
        BlockPos hand = helper.absolutePos(handRel);
        // Curios 在 HIGHEST 不看工具重算一份经验（代挖时主手是空的也照算）；这里在 NORMAL 固定成 5，ρ（LOW）看到的就是 5
        Consumer<BlockEvent.BreakEvent> fixXp = event -> {
            if (event.getPos().equals(guarded) || event.getPos().equals(hand)) {
                event.setExpToDrop(5);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, fixXp);
        try {
            data.setUBMRU(10000);
            helper.assertTrue(ECProtection.breakGuarded(player, level, guarded, new ItemStack(Items.DIAMOND_PICKAXE), true), "代挖应挖得动");
            helper.assertTrue(level.getBlockState(guarded).isAir(), "代挖后方块应被删掉");
            helper.assertTrue(data.getUBMRU() == 10000, "代挖时 ρ 不应给 UBMRU、ο 不应扣费，实际 " + data.getUBMRU());
            int diamonds = level.getEntitiesOfClass(ItemEntity.class, new AABB(guarded).inflate(1.5D), item -> item.getItem().is(Items.DIAMOND))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(diamonds == 1, "代挖的掉落 ο 不应 +1，实际 " + diamonds + " 个钻石");

            MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, hand, level.getBlockState(hand), player));
            helper.assertTrue(data.getUBMRU() == 10000 + 5 * ECBaubles.Limits.rhoUbmruPerXp(),
                    "对照：玩家自己挖应得 5 × " + ECBaubles.Limits.rhoUbmruPerXp() + "，实际 " + data.getUBMRU());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(fixXp);
        }
        helper.succeed();
    }

    /** φ：剪得出东西才扣 UBMRU 和剪刀耐久；两次之间有冷却 */
    @GameTest(template = EMPTY)
    public static void phiShearsOnlyProductiveMobs(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.PHI_RING);
        data.setUBMRU(5000);
        ItemStack shears = new ItemStack(Items.SHEARS);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));

        helper.assertFalse(BaubleHarvestEvents.shear(player, InteractionHand.MAIN_HAND, shears, pig), "猪剪不出东西");
        helper.assertTrue(shears.getDamageValue() == 0 && data.getUBMRU() == 5000, "剪不出东西时不应扣耐久和 UBMRU");
        helper.assertTrue(BaubleHarvestEvents.shear(player, InteractionHand.MAIN_HAND, shears, cow), "牛应剪出皮革");
        helper.assertTrue(shears.getDamageValue() == BaubleEffects.PHI_SHEARS_DAMAGE && data.getUBMRU() == 5000 - BaubleEffects.PHI_COST,
                "应扣 32 耐久、1000 UBMRU，实际耐久损耗 " + shears.getDamageValue() + "、剩 " + data.getUBMRU());
        if (ECBaubles.Limits.phiCooldown() > 0) {
            helper.assertFalse(BaubleHarvestEvents.shear(player, InteractionHand.MAIN_HAND, shears, cow), "冷却内不应再剪");
        }
        helper.succeed();
    }

    /**
     * χ：捡经验球另得球上数值的 {@code chi_xp_bonus}（默认 50%，第十批从「另得一份」改来）。Curios 修经验修补饰品时（NORMAL 取消拾取、自己吃掉球）
     * 也照样给，按修之前的数值；被取消、球还在的拾取不给（原版这时不设拾取间隔，照给就是每 tick 一份）
     */
    @GameTest(template = EMPTY)
    public static void chiGivesBonusOrbExperience(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        wear(helper, player, ECBaubles.CHI_RING);
        double ratio = ECBaubles.Limits.chiXpBonus();
        int low = (int) Math.floor(8 * ratio);
        int high = (int) Math.ceil(8 * ratio);
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 1D, 1.5D));
        ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 8);
        int before = player.totalExperience;
        MinecraftForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, orb));
        int gained = player.totalExperience - before;
        helper.assertTrue(gained >= low && gained <= high, "8 点的球应另给 8 × " + ratio + " 点经验，实际 " + gained);
        helper.assertTrue(ECBaubles.Limits.DEFAULT_CHI_XP_BONUS == 0.5D && BaubleHarvestEvents.chiBonus(8, RandomSource.create(1L)) <= 8,
                "默认另给 50%（原作 100%）");
        helper.assertTrue(orb.value == 8, "经验球本身的数值不应改（1.20 的经验球会合并，改了会越捡越多）");

        // 照 Curios 的 handleMending：取消拾取、4 点拿去修饰品、剩下 4 点给玩家、收走球
        ExperienceOrb mended = new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 8);
        ExperienceOrb blocked = new ExperienceOrb(helper.getLevel(), at.x, at.y, at.z, 8);
        Consumer<PlayerXpEvent.PickupXp> curiosLike = event -> {
            if (event.getOrb() == mended) {
                event.setCanceled(true);
                mended.value -= 4;
                event.getEntity().giveExperiencePoints(mended.value);
                mended.discard();
            } else if (event.getOrb() == blocked) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerXpEvent.PickupXp.class, curiosLike);
        try {
            before = player.totalExperience;
            MinecraftForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, mended));
            int mendedGain = player.totalExperience - before;
            helper.assertTrue(mendedGain >= 4 + low && mendedGain <= 4 + high,
                    "Curios 修饰品时应得剩下的 4 点 + χ 按原数值 8 另给的那份，实际 " + mendedGain);
            before = player.totalExperience;
            MinecraftForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, blocked));
            helper.assertTrue(player.totalExperience == before, "被取消、球还在的拾取不应给 χ 那一份，实际多了 " + (player.totalExperience - before));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(curiosLike);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 戴着时的效果

    /** 医师腰带：八种负面效果缩短，别的效果不动；便携矩阵换能器 10 UBMRU → 1 MRU、算储存物品；κ 加 UBMRU */
    @GameTest(template = EMPTY)
    public static void wornTickEffects(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);

        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        helper.assertTrue(BaubleEffects.shortenHarmfulEffects(player, 20) == 1, "应只缩短中毒一个效果");
        helper.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == 180, "中毒应缩短 20 tick，实际 " + player.getEffect(MobEffects.POISON).getDuration());
        helper.assertTrue(player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 200, "速度不是负面效果，不应缩短");

        ItemStack destructor = wear(helper, player, ECBaubles.PORTABLE_MATRIX_DESTRUCTOR);
        data.setUBMRU(1000);
        int times = PortableMatrixDestructorItem.convert(player, destructor, PortableMatrixDestructorItem.INTERVAL);
        IMRUHandlerItem storage = destructor.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        helper.assertTrue(storage != null && storage.isStorage(), "便携矩阵换能器应是储存物品");
        helper.assertTrue(times == 20 && data.getUBMRU() == 800 && storage.getMRU() == 20,
                "一次结算应换 20 次（200 UBMRU → 20 MRU），实际 " + times + " 次、剩 " + data.getUBMRU() + " UBMRU、" + storage.getMRU() + " MRU");
        helper.assertTrue(MRUDrain.tryDrainFromStorages(player, 15) && storage.getMRU() == 5, "耗 MRU 时应能从它身上扣");
        data.setUBMRU(5);
        helper.assertTrue(PortableMatrixDestructorItem.convert(player, destructor, 20) == 0 && data.getUBMRU() == 5, "UBMRU 不够一次时不换");

        data.setUBMRU(0);
        BaubleEffects.kappaTick(player, ItemStack.EMPTY);
        helper.assertTrue(player.tickCount % BaubleEffects.KAPPA_INTERVAL != 0 || data.getUBMRU() == 1, "κ 每 10 tick 应 +1 UBMRU");
        helper.succeed();
    }

    /** 风之好感系数（风法师护符 +0.3、捕风挂坠 −0.1）与交给别的模块读的标签（需要先跑 runData 生成标签） */
    @GameTest(template = EMPTY)
    public static void windAffinityAndTags(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        float base = WindAffinity.baseCoefficient();
        wear(helper, player, CuriosCompat.NECKLACE, 0, ECBaubles.WIND_MAGES_TALISMAN.get());
        helper.assertTrue(Math.abs(WindAffinity.coefficient(player) - (base + 0.3F)) < 1.0E-4F, "风法师护符应 +0.3，实际 " + WindAffinity.coefficient(player));
        wear(helper, player, CuriosCompat.NECKLACE, 0, ECBaubles.WIND_CATCHER.get());
        helper.assertTrue(Math.abs(WindAffinity.coefficient(player) - (base - 0.1F)) < 1.0E-4F, "捕风挂坠应 −0.1，实际 " + WindAffinity.coefficient(player));
        helper.assertTrue(WindAffinity.isWearing(player, WindAffinity.WIND_CATCHER), "捕风挂坠应在 wind_catcher 标签里");

        wear(helper, player, CuriosCompat.RING, 0, ECBaubles.RING_OF_RESISTANCE.get());
        helper.assertTrue(WindAffinity.isWearing(player, WindAffinity.WIND_RESISTANT), "抗性指环应在 wind_resistant 标签里");
        wear(helper, player, CuriosCompat.RING, 0, ECBaubles.BETA_RING.get());
        helper.assertTrue(WindAffinity.isWearing(player, WindAffinity.WIND_BOON), "β 指环应在 wind_boon 标签里");
        wear(helper, player, CuriosCompat.RING, 0, ECBaubles.ALPHA_RING.get());
        helper.assertTrue(CuriosCompat.has(player, MagicalWaterItem.UBMRU_FROM_MAGIC_WATER), "α 指环应在 ubmru_from_magic_water 标签里");
        wear(helper, player, CuriosCompat.RING, 0, ECBaubles.GAMMA_RING.get());
        helper.assertTrue(MRUCorruption.healsInstead(player), "γ 指环应让 MRU 腐化改成回血");
        for (RegistryObject<? extends SpecialBaubleItem> object : ECBaubles.all()) {
            helper.assertTrue(new ItemStack(object.get()).is(ECBaubles.SPECIAL_BAUBLES), object.getId() + " 应在 special_baubles 标签里");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 第十批饰品平衡

    /** 新数值的默认值（改了要同步手册和 batch10 饰品平衡一节），以及小数按几率进位 */
    @GameTest(template = EMPTY)
    public static void baubleBalanceDefaults(GameTestHelper helper) {
        helper.assertTrue(ECBaubles.Limits.DEFAULT_EXPERIENCE_RING_UBMRU_PER_XP == 20, "经验指环默认 20 点换 1 点（原作 4）");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_PI_XP_MULTIPLIER == 2D, "π 默认 ×2（原作 ×3）");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_CHI_XP_BONUS == 0.5D, "χ 默认另给 50%（原作 100%）");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_MINING_XP_ORES_ONLY, "π、ρ 默认只认矿石");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_RHO_UBMRU_PER_XP == 1000, "ρ 默认每点 1000（原作）");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_THETA_COOLDOWN == 600, "θ 默认冷却 600 tick");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_XI_DOSE_PER_HIT == 5000 && ECBaubles.Limits.DEFAULT_XI_DOSE_CEILING == 15000,
                "ξ 默认每下 5000、上限 15000");
        helper.assertTrue(ECBaubles.Limits.DEFAULT_EPSILON_EMBER_CHANCE == 0.05D, "ε 默认 5%");
        helper.assertFalse(ECBaubles.Limits.DEFAULT_TAU_AFFECTS_BOSSES || ECBaubles.Limits.DEFAULT_PSI_AFFECTS_BOSSES, "τ、ψ 默认不管首领");

        RandomSource random = RandomSource.create(20261007L * 0x9E3779B97F4A7C15L);
        helper.assertTrue(ECBaubles.Limits.roundRandomly(6D, random) == 6 && ECBaubles.Limits.roundRandomly(0D, random) == 0
                && ECBaubles.Limits.roundRandomly(-3D, random) == 0 && ECBaubles.Limits.roundRandomly(Double.NaN, random) == 0, "整数原样、负数和 NaN 当 0");
        int sum = 0;
        for (int i = 0; i < 4000; i++) {
            int v = ECBaubles.Limits.roundRandomly(2.25D, random);
            helper.assertTrue(v == 2 || v == 3, "2.25 只能进位成 2 或 3，实际 " + v);
            sum += v;
        }
        helper.assertTrue(sum > 4000 * 2.2D && sum < 4000 * 2.3D, "2.25 按几率进位的平均应约 2.25，实际 " + sum / 4000D);
        helper.succeed();
    }

    /** 经验指环：拿走击杀 UBMRU 的 10%，每 experience_ring_ubmru_per_xp 点变 1 点经验（以前每 4 点）；所得照样少 10% */
    @GameTest(template = EMPTY)
    public static void experienceRingConvertsAtNewRate(GameTestHelper helper) {
        Player player = mockPlayer(helper, 2.5D, 1D, 2.5D);
        ItemStack ring = wear(helper, player, ECBaubles.RING_OF_EXPERIENCE);
        int per = ECBaubles.Limits.experienceRingUbmruPerXp();
        float original = 10F * per * 3F;
        helper.killAllEntitiesOfClass(ExperienceOrb.class);
        float result = ECBaubles.RING_OF_EXPERIENCE.get().getModifiedValue(original, ring, RandomSource.create(7L), player);
        helper.assertTrue(Math.abs(result - original * 0.9F) < 1.0E-3F, "所得应少 10%：" + original + " → " + original * 0.9F + "，实际 " + result);
        AABB around = player.getBoundingBox().inflate(4D);
        int xp = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, around).stream().mapToInt(o -> o.value).sum();
        helper.assertTrue(xp == 3, "拿走的 " + original / 10F + " 点应换 3 点经验（每 " + per + " 点 1 点），实际 " + xp);
        if (per == ECBaubles.Limits.DEFAULT_EXPERIENCE_RING_UBMRU_PER_XP) {
            // 两点经验会拆成两个 1 点的球、偶尔合并成一个（球的 count 看不到），这里直接算数
            int zombie = ECBaubles.Limits.roundRandomly(400F / 10F / per, RandomSource.create(7L));
            helper.assertTrue(zombie == 2, "一只僵尸（400 UBMRU）应给 2 点经验（以前 10 点），实际 " + zombie);
        }
        helper.killAllEntitiesOfClass(ExperienceOrb.class);
        helper.succeed();
    }

    /**
     * π：矿石的经验 ×2；ρ：矿石的经验换 UBMRU；刷怪笼、石头这类不在 #forge:ores 里的方块两件都不管（默认只认矿石时）。
     * 手里拿铁镐（π 的 5% 要工具对、挖下来掉的不是矿石自己，见 {@link #piBonusSkipsSelfDrops}）
     */
    @GameTest(template = EMPTY)
    public static void piAndRhoOnlyCountOres(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        IPlayerData data = data(helper, player);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
        BlockState iron = Blocks.IRON_ORE.defaultBlockState();
        BlockState spawner = Blocks.SPAWNER.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(BaubleHarvestEvents.countsForMiningXp(ore) && BaubleHarvestEvents.countsForMiningXp(iron), "钻石矿、铁矿在 #forge:ores 里");
        boolean oresOnly = ECBaubles.Limits.miningXpOresOnly();
        helper.assertTrue(BaubleHarvestEvents.countsForMiningXp(spawner) != oresOnly && BaubleHarvestEvents.countsForMiningXp(stone) != oresOnly,
                "只认矿石时刷怪笼、石头不算");

        ItemStack pi = wear(helper, player, ECBaubles.PI_BELT);
        double multiplier = ECBaubles.Limits.piXpMultiplier();
        BlockEvent.BreakEvent oreBreak = new BlockEvent.BreakEvent(level, pos, ore, player);
        oreBreak.setExpToDrop(4);
        BaubleHarvestEvents.applyBreakExperience(player, oreBreak);
        int expected = (int) Math.round(4 * multiplier);
        helper.assertTrue(Math.abs(oreBreak.getExpToDrop() - 4 * multiplier) < 1D,
                "π 挖矿石 4 点应变成 4 × " + multiplier + "，实际 " + oreBreak.getExpToDrop());
        if (multiplier == ECBaubles.Limits.DEFAULT_PI_XP_MULTIPLIER) {
            helper.assertTrue(oreBreak.getExpToDrop() == 8 && expected == 8, "默认 ×2：4 点 → 8 点（原作 12 点），实际 " + oreBreak.getExpToDrop());
        }
        if (oresOnly) {
            BlockEvent.BreakEvent cage = new BlockEvent.BreakEvent(level, pos, spawner, player);
            cage.setExpToDrop(20);
            BaubleHarvestEvents.applyBreakExperience(player, cage);
            helper.assertTrue(cage.getExpToDrop() == 20, "π 不应管刷怪笼，实际 " + cage.getExpToDrop());
            for (int i = 0; i < 200; i++) {
                BlockEvent.BreakEvent rock = new BlockEvent.BreakEvent(level, pos, stone, player);
                rock.setExpToDrop(0);
                BaubleHarvestEvents.applyBreakExperience(player, rock);
                helper.assertTrue(rock.getExpToDrop() == 0, "π 的「没经验也给 1 点」不应对石头掷");
            }
        }
        int bonus = 0;
        for (int i = 0; i < 200; i++) {
            BlockEvent.BreakEvent ironBreak = new BlockEvent.BreakEvent(level, pos, iron, player);
            ironBreak.setExpToDrop(0);
            BaubleHarvestEvents.applyBreakExperience(player, ironBreak);
            bonus += ironBreak.getExpToDrop();
        }
        helper.assertTrue(bonus > 0 && bonus < 40, "铁矿（不掉经验的矿石）200 次应有约 10 次给 1 点，实际 " + bonus);

        // ρ：换掉 π
        curios(helper, player).getCurios().get(CuriosCompat.BELT).getStacks().setStackInSlot(0, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new CurioChangeEvent(player, CuriosCompat.BELT, 0, pi, ItemStack.EMPTY));
        wear(helper, player, ECBaubles.RHO_RING);
        data.setUBMRU(0);
        BlockEvent.BreakEvent rhoOre = new BlockEvent.BreakEvent(level, pos, ore, player);
        rhoOre.setExpToDrop(4);
        BaubleHarvestEvents.applyBreakExperience(player, rhoOre);
        helper.assertTrue(rhoOre.getExpToDrop() == 0 && data.getUBMRU() == 4 * ECBaubles.Limits.rhoUbmruPerXp(),
                "ρ 挖矿石应把 4 点换成 UBMRU，实际经验 " + rhoOre.getExpToDrop() + "、UBMRU " + data.getUBMRU());
        if (oresOnly) {
            data.setUBMRU(0);
            BlockEvent.BreakEvent rhoCage = new BlockEvent.BreakEvent(level, pos, spawner, player);
            rhoCage.setExpToDrop(20);
            BaubleHarvestEvents.applyBreakExperience(player, rhoCage);
            helper.assertTrue(rhoCage.getExpToDrop() == 20 && data.getUBMRU() == 0,
                    "ρ 不应换刷怪笼的经验（幽匿块同理），实际经验 " + rhoCage.getExpToDrop() + "、UBMRU " + data.getUBMRU());
        }
        helper.succeed();
    }

    /**
     * ζ、ι 只算打实的攻击：在 LivingHurtEvent 之后被拦下（混合端的 Bukkit 伤害事件就在这里，用最低优先级的取消模拟）时，
     * ζ 不扣 UBMRU、不清无敌帧，ι 排的魔法伤害不打；打实的照常。混合端在伤害事件之后才设无敌帧，用 tick 末的补清兜住。
     */
    @GameTest(template = EMPTY)
    public static void zetaAndIotaOnlyCountLandedHits(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.ZETA_AMULET);
        wear(helper, player, ECBaubles.IOTA_RING);
        Pig landed = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 1));
        Pig blocked = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 3));
        BaubleCombatEvents.flushIota();
        BaubleCombatEvents.flushZeta();
        Consumer<LivingHurtEvent> lateBlock = event -> {
            if (event.getEntity() == blocked) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateBlock);
        try {
            data.setUBMRU(1000);
            float full = blocked.getHealth();
            blocked.hurt(player.damageSources().playerAttack(player), 4F);
            helper.assertTrue(blocked.getHealth() == full, "被拦下的攻击不应掉血");
            helper.assertTrue(data.getUBMRU() == 1000, "被拦下的攻击 ζ 不应扣 UBMRU，实际剩 " + data.getUBMRU());
            helper.assertTrue(blocked.invulnerableTime > 0, "被拦下的攻击 ζ 不应清无敌帧，实际 " + blocked.invulnerableTime);

            landed.hurt(player.damageSources().playerAttack(player), 4F);
            helper.assertTrue(data.getUBMRU() == 1000 - BaubleEffects.ZETA_COST, "打实的攻击 ζ 应扣 300，实际剩 " + data.getUBMRU());
            helper.assertTrue(landed.invulnerableTime == 0, "打实的攻击 ζ 应清掉无敌帧，实际 " + landed.invulnerableTime);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateBlock);
        }
        float landedBefore = landed.getHealth();
        float blockedBefore = blocked.getHealth();
        helper.assertTrue(BaubleCombatEvents.flushIota() == 1, "ι 只应补打实的那一下");
        helper.assertTrue(landed.getHealth() < landedBefore && blocked.getHealth() == blockedBefore,
                "ι 的魔法伤害只打打实的那只，实际 " + landed.getHealth() + " / " + blocked.getHealth());

        // 混合端：伤害事件之后无敌帧又被设成 20；本 tick 末补清
        landed.invulnerableTime = 20;
        helper.assertTrue(BaubleCombatEvents.flushZeta() >= 1 && landed.invulnerableTime == 0, "tick 末应再清一次被 ζ 打实过的目标的无敌帧");
        helper.assertTrue(blocked.invulnerableTime > 0, "被拦下的那只不在补清名单里");
        helper.succeed();
    }

    /** τ、ψ 默认不管首领（#forge:bosses）：凋灵的下界之星不加、首领不会被引去打别的怪；普通怪照旧 */
    @GameTest(template = EMPTY_LARGE)
    public static void tauAndPsiSkipBosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.TAU_RING);
        wear(helper, player, CuriosCompat.RING, 1, ECBaubles.PSI_RING.get());
        WitherBoss wither = EntityType.WITHER.create(level);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (wither == null || zombie == null) {
            helper.fail("建不出凋灵 / 僵尸");
            return;
        }
        helper.assertTrue(BaubleEffects.isBoss(wither) && !BaubleEffects.isBoss(zombie), "凋灵在 #forge:bosses 里、僵尸不在");
        boolean tauBosses = ECBaubles.Limits.tauAffectsBosses();
        data.setUBMRU(5000);
        List<ItemStack> star = new ObjectArrayList<>(List.of(new ItemStack(Items.NETHER_STAR)));
        boolean added = BaubleEffects.tauBonus(player, wither, star, RandomSource.create(1L));
        if (!tauBosses) {
            helper.assertTrue(!added && star.get(0).getCount() == 1 && data.getUBMRU() == 5000, "τ 不应加首领的掉落、不应扣 UBMRU");
        }
        List<ItemStack> flesh = new ObjectArrayList<>(List.of(new ItemStack(Items.ROTTEN_FLESH)));
        helper.assertTrue(BaubleEffects.tauBonus(player, zombie, flesh, RandomSource.create(1L)), "τ 对普通怪照旧");

        // ψ：首领、普通怪各放在一只骷髅旁边（不进世界，只要有位置），看盯上玩家时换不换目标
        Skeleton skeleton = helper.spawnWithNoFreeWill(EntityType.SKELETON, new BlockPos(6, 1, 6));
        Vec3 near = helper.absoluteVec(new Vec3(4.5D, 1D, 6.5D));
        wither.moveTo(near.x, near.y, near.z, 0F, 0F);
        zombie.moveTo(near.x, near.y, near.z, 0F, 0F);
        LivingChangeTargetEvent bossEvent = new LivingChangeTargetEvent(wither, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        BaubleCombatEvents.onBaubleChangeTarget(bossEvent);
        if (!ECBaubles.Limits.psiAffectsBosses()) {
            helper.assertTrue(bossEvent.getNewTarget() == player, "ψ 不应让首领改打别的怪，实际 " + bossEvent.getNewTarget());
        }
        LivingChangeTargetEvent mobEvent = new LivingChangeTargetEvent(zombie, player, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        BaubleCombatEvents.onBaubleChangeTarget(mobEvent);
        LivingEntity redirected = mobEvent.getNewTarget();
        helper.assertTrue(redirected != player && redirected instanceof Enemy && skeleton.isAlive(),
                "ψ 对普通怪照旧（改打旁边的敌对生物），实际 " + redirected);
        helper.succeed();
    }

    /** ε 只认敌对生物：动物、玩家不算（原作都算，刷鸡、互杀能刷余烬） */
    @GameTest(template = EMPTY)
    public static void epsilonOnlyHostileMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        wear(helper, player, ECBaubles.EPSILON_BELT);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        Player other = mockPlayer(helper, 3.5D, 1D, 1.5D);
        helper.assertTrue(ECVoid.VoidEmberEvents.isEmberVictim(zombie), "僵尸算");
        helper.assertFalse(ECVoid.VoidEmberEvents.isEmberVictim(pig) || ECVoid.VoidEmberEvents.isEmberVictim(other), "猪、玩家不算");
        long hit = 0L;
        for (long i = 1; i < 100_000; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            if (RandomSource.create(seed).nextFloat() < 0.01F) {
                hit = seed;
                break;
            }
        }
        DamageSource byPlayer = level.damageSources().playerAttack(player);
        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(pig, byPlayer, RandomSource.create(hit)).isEmpty(), "掷中了也不应从猪身上掉余烬");
        helper.assertTrue(ECVoid.VoidEmberEvents.rollEmber(other, byPlayer, RandomSource.create(hit)).isEmpty(), "掷中了也不应从玩家身上掉余烬");
        if (ECBaubles.Limits.epsilonEmberChance() > 0.01D) {
            helper.assertFalse(ECVoid.VoidEmberEvents.rollEmber(zombie, byPlayer, RandomSource.create(hit)).isEmpty(), "对照：僵尸掷中时照掉");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 审查后补的

    /** 新建一次挖掘：主手拿 tool，经验按「本来不掉经验」设成 0（π 的 5% 只在这时掷） */
    private static BlockEvent.BreakEvent zeroXpBreak(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        event.setExpToDrop(0);
        return event;
    }

    /**
     * π 的「没经验也 5% 给 1 点」不给挖下来就是方块自己的：精准采集挖铁矿、下界合金镐挖远古残骸（都在 #forge:ores 里，放回去能再挖），
     * 工具不对（空手挖铁矿，什么都不掉）也不给；铁镐挖铁矿（掉粗铁）照给。掷骰用测试覆盖值固定成必中，各挖 200 次；
     * 再戴上 ρ：精准采集挖 200 次一点 UBMRU 都不加（以前平均每次 50）。对照：掷骰固定成不中时铁镐也不给
     */
    @GameTest(template = EMPTY)
    public static void piBonusSkipsSelfDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 1));
        BlockState iron = Blocks.IRON_ORE.defaultBlockState();
        BlockState debris = Blocks.ANCIENT_DEBRIS.defaultBlockState();
        helper.assertTrue(BaubleHarvestEvents.countsForMiningXp(iron) && BaubleHarvestEvents.countsForMiningXp(debris),
                "测试前提：铁矿、远古残骸都算矿石（#forge:ores）");
        ItemStack ironPick = new ItemStack(Items.IRON_PICKAXE);
        ItemStack silkPick = new ItemStack(Items.IRON_PICKAXE);
        silkPick.enchant(Enchantments.SILK_TOUCH, 1);
        ItemStack netheritePick = new ItemStack(Items.NETHERITE_PICKAXE);
        wear(helper, player, ECBaubles.PI_BELT);
        BaubleHarvestEvents.setPiRollForTest(0F);
        try {
            BlockEvent.BreakEvent normal = zeroXpBreak(level, pos, iron, player, ironPick);
            BaubleHarvestEvents.applyBreakExperience(player, normal);
            helper.assertTrue(normal.getExpToDrop() == 1, "铁镐挖铁矿（掉粗铁）掷中应给 1 点，实际 " + normal.getExpToDrop());
            int silk = 0;
            int ancient = 0;
            int bare = 0;
            for (int i = 0; i < 200; i++) {
                BlockEvent.BreakEvent silkBreak = zeroXpBreak(level, pos, iron, player, silkPick);
                BaubleHarvestEvents.applyBreakExperience(player, silkBreak);
                silk += silkBreak.getExpToDrop();
                BlockEvent.BreakEvent debrisBreak = zeroXpBreak(level, pos, debris, player, netheritePick);
                BaubleHarvestEvents.applyBreakExperience(player, debrisBreak);
                ancient += debrisBreak.getExpToDrop();
                BlockEvent.BreakEvent bareBreak = zeroXpBreak(level, pos, iron, player, ItemStack.EMPTY);
                BaubleHarvestEvents.applyBreakExperience(player, bareBreak);
                bare += bareBreak.getExpToDrop();
            }
            helper.assertTrue(silk == 0, "精准采集挖铁矿 200 次不应给经验，实际 " + silk);
            helper.assertTrue(ancient == 0, "下界合金镐挖远古残骸 200 次不应给经验，实际 " + ancient);
            helper.assertTrue(bare == 0, "空手挖铁矿（工具不对）200 次不应给经验，实际 " + bare);

            wear(helper, player, ECBaubles.RHO_RING);
            data.setUBMRU(0);
            for (int i = 0; i < 200; i++) {
                BaubleHarvestEvents.applyBreakExperience(player, zeroXpBreak(level, pos, iron, player, silkPick));
            }
            helper.assertTrue(data.getUBMRU() == 0, "π + ρ 精准采集挖铁矿 200 次不应加 UBMRU，实际 " + data.getUBMRU());
            BaubleHarvestEvents.applyBreakExperience(player, zeroXpBreak(level, pos, iron, player, ironPick));
            helper.assertTrue(data.getUBMRU() == ECBaubles.Limits.rhoUbmruPerXp(), "对照：铁镐挖铁矿掷中，π 给的 1 点被 ρ 换成 UBMRU，实际 " + data.getUBMRU());

            BaubleHarvestEvents.setPiRollForTest(0.99F);
            BlockEvent.BreakEvent miss = zeroXpBreak(level, pos, iron, player, ironPick);
            BaubleHarvestEvents.applyBreakExperience(player, miss);
            helper.assertTrue(miss.getExpToDrop() == 0, "对照：没掷中时不给");
        } finally {
            BaubleHarvestEvents.setPiRollForTest(null);
        }
        helper.succeed();
    }

    /**
     * ζ、ι 不算混合端上被盾牌整下挡掉的一下：按混合端的顺序手动发 {@code LivingHurtEvent} → {@code ShieldBlockEvent} → {@code LivingDamageEvent(0)}
     * （混合端在 LivingHurtEvent 之后才算举盾，挡满照样发 LivingDamageEvent(0)）：ζ 不扣 UBMRU、不清无敌帧，ι 排的那一下不补。
     * 对照：没有格挡的 {@code LivingDamageEvent(0)}（插件放行、伤害被盔甲吃完）照常算打实。
     * 另测 {@code ECShieldBlocks} 的记录规则：只挡一部分的、被取消的格挡不记；伤害 &gt; 0、别的实体、别的伤害源对象都不算
     */
    @GameTest(template = EMPTY)
    public static void zetaAndIotaIgnoreShieldBlockedHits(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.ZETA_AMULET);
        wear(helper, player, ECBaubles.IOTA_RING);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 1));
        Pig other = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 3));
        BaubleCombatEvents.flushIota();
        BaubleCombatEvents.flushZeta();
        data.setUBMRU(1000);

        DamageSource shielded = player.damageSources().playerAttack(player);
        target.invulnerableTime = 20;
        LivingHurtEvent hurt = new LivingHurtEvent(target, shielded, 5F);
        MinecraftForge.EVENT_BUS.post(hurt);
        helper.assertTrue(Math.abs(hurt.getAmount() - 4.5F) < 1.0E-4F, "测试前提：ι 拆出了 10%，实际 " + hurt.getAmount());
        MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(target, shielded, hurt.getAmount()));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, shielded, 0F));
        helper.assertTrue(data.getUBMRU() == 1000, "被盾牌整下挡掉的一下 ζ 不应扣 UBMRU，实际剩 " + data.getUBMRU());
        helper.assertTrue(target.invulnerableTime == 20, "被盾牌整下挡掉的一下 ζ 不应清无敌帧，实际 " + target.invulnerableTime);
        float before = target.getHealth();
        BaubleCombatEvents.flushIota();
        helper.assertTrue(target.getHealth() == before, "被盾牌整下挡掉的一下 ι 不应补魔法伤害，实际 " + before + " → " + target.getHealth());

        // 对照：没有格挡记录的 LivingDamageEvent(0) 照常算打实
        DamageSource landed = player.damageSources().playerAttack(player);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 5F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
        helper.assertTrue(data.getUBMRU() == 1000 - BaubleEffects.ZETA_COST && target.invulnerableTime == 0,
                "对照：没被盾挡的 LivingDamageEvent(0) ζ 照扣照清，实际剩 " + data.getUBMRU() + "、无敌帧 " + target.invulnerableTime);
        float beforeLanded = target.getHealth();
        BaubleCombatEvents.flushIota();
        helper.assertTrue(Math.abs(target.getHealth() - (beforeLanded - 0.5F)) < 1.0E-4F,
                "对照：ι 照补 0.5 点，实际 " + beforeLanded + " → " + target.getHealth());

        // 记录规则
        DamageSource partialSource = player.damageSources().playerAttack(player);
        ShieldBlockEvent partial = new ShieldBlockEvent(target, partialSource, 4F);
        partial.setBlockedDamage(2F);
        MinecraftForge.EVENT_BUS.post(partial);
        helper.assertFalse(ECShieldBlocks.fullyShielded(target, partialSource, 0F), "只挡一部分的格挡不算整下挡掉");
        DamageSource canceledSource = player.damageSources().playerAttack(player);
        ShieldBlockEvent canceled = new ShieldBlockEvent(target, canceledSource, 4F);
        canceled.setCanceled(true);
        MinecraftForge.EVENT_BUS.post(canceled);
        helper.assertFalse(ECShieldBlocks.fullyShielded(target, canceledSource, 0F), "被取消的格挡（盾没挡）不记");
        DamageSource fullSource = player.damageSources().playerAttack(player);
        MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(target, fullSource, 4F));
        helper.assertTrue(ECShieldBlocks.fullyShielded(target, fullSource, 0F), "整下挡掉、伤害 0：算");
        helper.assertFalse(ECShieldBlocks.fullyShielded(target, fullSource, 1F), "伤害 > 0 不算（只挡了一部分、照样掉血）");
        helper.assertFalse(ECShieldBlocks.fullyShielded(other, fullSource, 0F), "别的实体不算");
        helper.assertFalse(ECShieldBlocks.fullyShielded(target, player.damageSources().playerAttack(player), 0F), "别的伤害源对象不算");
        helper.succeed();
    }

    /**
     * ζ、ι 不算原伤害的 LivingHurtEvent 最后被取消的一下：混合端上别的模组排在 ζ / ι 后面取消 LivingHurtEvent 时，伤害按 0 照样往下走，
     * Bukkit 事件放行后照发 {@code LivingDamageEvent(0)}；纯 Forge 取消以后不再发，所以按混合端的顺序手动发
     * {@code LivingHurtEvent}（运行时加的最低优先级监听器排在 ζ / ι 之后取消）→ {@code LivingDamageEvent(0)}：ζ 不扣 UBMRU、不清无敌帧，ι 排的那一下不补。
     * 在 ζ / ι 之前就被取消的（最高优先级取消，ζ / ι 的 LivingHurtEvent 监听器收不到）同样不算。对照：同样的顺序不取消时照常算打实。
     */
    @GameTest(template = EMPTY)
    public static void zetaAndIotaIgnoreCanceledHurt(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.ZETA_AMULET);
        wear(helper, player, ECBaubles.IOTA_RING);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 1));
        BaubleCombatEvents.flushIota();
        BaubleCombatEvents.flushZeta();
        data.setUBMRU(1000);

        DamageSource late = player.damageSources().playerAttack(player);
        DamageSource early = player.damageSources().playerAttack(player);
        Consumer<LivingHurtEvent> lateCancel = event -> {
            if (event.getSource() == late) {
                event.setCanceled(true);
            }
        };
        Consumer<LivingHurtEvent> earlyCancel = event -> {
            if (event.getSource() == early) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateCancel);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingHurtEvent.class, earlyCancel);
        try {
            target.invulnerableTime = 20;
            LivingHurtEvent hurt = new LivingHurtEvent(target, late, 5F);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(hurt.isCanceled() && Math.abs(hurt.getAmount() - 4.5F) < 1.0E-4F,
                    "测试前提：ι 先拆了 10%、之后才被取消，实际取消 " + hurt.isCanceled() + "、伤害 " + hurt.getAmount());
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, late, 0F));
            helper.assertTrue(data.getUBMRU() == 1000, "LivingHurtEvent 在后面被取消的一下 ζ 不应扣 UBMRU，实际剩 " + data.getUBMRU());
            helper.assertTrue(target.invulnerableTime == 20, "LivingHurtEvent 在后面被取消的一下 ζ 不应清无敌帧，实际 " + target.invulnerableTime);

            LivingHurtEvent blockedFirst = new LivingHurtEvent(target, early, 5F);
            MinecraftForge.EVENT_BUS.post(blockedFirst);
            helper.assertTrue(blockedFirst.isCanceled() && blockedFirst.getAmount() == 5F, "测试前提：最高优先级先取消，ι 没拆");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, early, 0F));
            helper.assertTrue(data.getUBMRU() == 1000 && target.invulnerableTime == 20,
                    "在 ζ 之前就被取消的一下也不算，实际剩 " + data.getUBMRU() + "、无敌帧 " + target.invulnerableTime);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateCancel);
            MinecraftForge.EVENT_BUS.unregister(earlyCancel);
        }
        float before = target.getHealth();
        helper.assertTrue(BaubleCombatEvents.flushIota() == 0 && target.getHealth() == before,
                "LivingHurtEvent 被取消的一下 ι 不应补魔法伤害，实际 " + before + " → " + target.getHealth());

        // 对照：同样的顺序、不取消，照常算打实
        DamageSource landed = player.damageSources().playerAttack(player);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 5F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
        helper.assertTrue(data.getUBMRU() == 1000 - BaubleEffects.ZETA_COST && target.invulnerableTime == 0,
                "对照：没被取消的一下 ζ 照扣照清，实际剩 " + data.getUBMRU() + "、无敌帧 " + target.invulnerableTime);
        float beforeLanded = target.getHealth();
        helper.assertTrue(BaubleCombatEvents.flushIota() == 1, "对照：ι 照补这一下");
        helper.assertTrue(Math.abs(target.getHealth() - (beforeLanded - 0.5F)) < 1.0E-4F,
                "对照：ι 照补 0.5 点，实际 " + beforeLanded + " → " + target.getHealth());
        helper.succeed();
    }

    /**
     * ζ、ι 不算伤害最后被改成 0（没取消）的一下：混合端在 {@code onLivingHurt} 之后只对 &lt; 0 提前返回，0 照样发 Bukkit 事件、放行后照发
     * {@code LivingDamageEvent(0)}；纯 Forge 在 {@code actuallyHurt} 的 {@code <= 0} 处返回、不再发。所以按混合端的顺序手动发
     * {@code LivingHurtEvent}（运行时加的最低优先级监听器排在 ζ / ι 之后把伤害改成 0）→ {@code LivingDamageEvent(0)}：ζ 不扣 UBMRU、不清无敌帧，ι 排的那一下不补。
     * 对照：同样的顺序、伤害没被改成 0 时照常算打实
     */
    @GameTest(template = EMPTY)
    public static void zetaAndIotaIgnoreZeroedHurt(GameTestHelper helper) {
        Player player = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, player);
        wear(helper, player, ECBaubles.ZETA_AMULET);
        wear(helper, player, ECBaubles.IOTA_RING);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 1, 1));
        BaubleCombatEvents.flushIota();
        BaubleCombatEvents.flushZeta();
        data.setUBMRU(1000);

        DamageSource zeroed = player.damageSources().playerAttack(player);
        Consumer<LivingHurtEvent> lateZero = event -> {
            if (event.getSource() == zeroed) {
                event.setAmount(0F);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateZero);
        try {
            target.invulnerableTime = 20;
            LivingHurtEvent hurt = new LivingHurtEvent(target, zeroed, 5F);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(!hurt.isCanceled() && hurt.getAmount() == 0F, "测试前提：没取消、伤害被改成 0，实际取消 " + hurt.isCanceled() + "、伤害 " + hurt.getAmount());
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, zeroed, 0F));
            helper.assertTrue(data.getUBMRU() == 1000, "伤害被改成 0 的一下 ζ 不应扣 UBMRU，实际剩 " + data.getUBMRU());
            helper.assertTrue(target.invulnerableTime == 20, "伤害被改成 0 的一下 ζ 不应清无敌帧，实际 " + target.invulnerableTime);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateZero);
        }
        float before = target.getHealth();
        helper.assertTrue(BaubleCombatEvents.flushIota() == 0 && target.getHealth() == before,
                "伤害被改成 0 的一下 ι 不应补魔法伤害，实际 " + before + " → " + target.getHealth());

        // 对照：同样的顺序、伤害没被改成 0
        DamageSource landed = player.damageSources().playerAttack(player);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 5F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
        helper.assertTrue(data.getUBMRU() == 1000 - BaubleEffects.ZETA_COST && target.invulnerableTime == 0,
                "对照：伤害没被改成 0 的一下 ζ 照扣照清，实际剩 " + data.getUBMRU() + "、无敌帧 " + target.invulnerableTime);
        helper.assertTrue(BaubleCombatEvents.flushIota() == 1, "对照：ι 照补这一下");
        helper.succeed();
    }

    /**
     * 本模组自己的情况：攻击者戴 ζ + ι，受害者戴 σ、σ 必中（{@code setSigmaRollForTest(0)}）把这一下免成 0。按混合端的顺序手动发
     * {@code LivingHurtEvent} → {@code LivingDamageEvent(0)}：ζ 不扣 UBMRU、不清无敌帧，ι 不补魔法伤害（σ 的免伤不会被打穿），和纯 Forge 一样
     */
    @GameTest(template = EMPTY)
    public static void zetaAndIotaRespectSigma(GameTestHelper helper) {
        Player attacker = mockPlayer(helper, 1.5D, 1D, 1.5D);
        IPlayerData data = data(helper, attacker);
        wear(helper, attacker, ECBaubles.ZETA_AMULET);
        wear(helper, attacker, ECBaubles.IOTA_RING);
        Player victim = mockPlayer(helper, 3.5D, 1D, 1.5D);
        wear(helper, victim, ECBaubles.SIGMA_RING);
        BaubleCombatEvents.flushIota();
        BaubleCombatEvents.flushZeta();
        BaubleCombatEvents.flushSigma();
        data.setUBMRU(1000);
        DamageSource source = attacker.damageSources().playerAttack(attacker);
        float before = victim.getHealth();
        int iota;
        try {
            BaubleCombatEvents.setSigmaRollForTest(0F);
            victim.invulnerableTime = 20;
            LivingHurtEvent hurt = new LivingHurtEvent(victim, source, 5F);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(!hurt.isCanceled() && hurt.getAmount() == 0F, "测试前提：σ 把这一下免成 0，实际取消 " + hurt.isCanceled() + "、伤害 " + hurt.getAmount());
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(victim, source, 0F));
            helper.assertTrue(data.getUBMRU() == 1000, "σ 免掉的一下 ζ 不应扣 UBMRU，实际剩 " + data.getUBMRU());
            helper.assertTrue(victim.invulnerableTime == 20, "σ 免掉的一下 ζ 不应清无敌帧，实际 " + victim.invulnerableTime);
            iota = BaubleCombatEvents.flushIota();
        } finally {
            BaubleCombatEvents.setSigmaRollForTest(null);
            BaubleCombatEvents.flushSigma();
        }
        helper.assertTrue(iota == 0 && victim.getHealth() == before, "σ 免掉的一下 ι 不应补魔法伤害，实际补了 " + iota + " 下、生命 " + before + " → " + victim.getHealth());
        helper.succeed();
    }

    /**
     * 提示里跟着配置走的几段：ξ 的「打玩家」「自己受伤」两段只在会给玩家加辐射时出现（辐射关掉时换成空的），
     * τ / ψ 的「首领不算」半句在对首领也生效时是空的
     */
    @GameTest(template = EMPTY)
    public static void baubleTooltipArgsFollowConfig(GameTestHelper helper) {
        try {
            HoannaHazardsConfig.setTestRadiationEnabled(false);
            Object[] off = ECBaubles.xiArgs();
            helper.assertTrue(off.length == 2 && isEmptyComponent(off[0]) && isEmptyComponent(off[1]), "辐射关着时 ξ 提示不应写打玩家 / 自己受伤加辐射");
            HoannaHazardsConfig.setTestRadiationEnabled(true);
            Object[] on = ECBaubles.xiArgs();
            if (ECBaubles.Limits.xiDoseCeiling() > 0) {
                helper.assertTrue(isTranslation(on[0], "tooltip.essentialcraft5.bauble.xi_belt.players") && isTranslation(on[1], "tooltip.essentialcraft5.bauble.xi_belt.self"),
                        "剂量上限 > 0、辐射开着时 ξ 提示应写两段");
            } else {
                helper.assertTrue(isEmptyComponent(on[0]) && isEmptyComponent(on[1]), "剂量上限是 0 时 ξ 不给玩家加辐射，两段应是空的");
            }
        } finally {
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
        helper.assertTrue(isTranslation(ECBaubles.bossNote("tau_ring", false), "tooltip.essentialcraft5.bauble.tau_ring.no_bosses"), "τ 不管首领时写「首领不加」");
        helper.assertTrue(isEmptyComponent(ECBaubles.bossNote("psi_ring", true)), "ψ 对首领也生效时不写「首领不受影响」");
        helper.succeed();
    }

    private static boolean isEmptyComponent(Object value) {
        return value instanceof Component component && component.getContents() == ComponentContents.EMPTY && component.getSiblings().isEmpty();
    }

    private static boolean isTranslation(Object value, String key) {
        return value instanceof Component component && component.getContents() instanceof TranslatableContents translatable && key.equals(translatable.getKey());
    }
}
