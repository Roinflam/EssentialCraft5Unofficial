package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.WindMageSummons;
import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindRelations;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 风之试炼（第十批 10b-M1，batch10.md §5.5.6）的 GameTest，24 条：
 * <ul>
 *   <li>心愿 12 条：没受眷顾问不到；同时只有一个；只给做得到的（不背鞘翅不给乘风、不打雷不给听雷）；八种的完成判定两种一条（登高 + 追风、远行 + 他乡、
 *       落羽 + 月下、乘风 + 听雷，直接调 {@link WindWishes#secondTick} / {@code featherTick} 推进；落羽的摔落伤害走真的 {@code player.hurt}、
 *       月下走真的 {@code jumpFromGround}，监听器换错事件也测得出；天气、昼夜、群系用测试覆盖，不动世界）；
 *       过期不罚、冷却后能再问；风之药水换一个且药水用掉（走真的符文右键和 RightClickBlock 放行）；完成时好感 +100、心愿数 +1、第一个发进度；
 *       石板回应要好感和心愿都够；心愿数攒够、真名没显全时风不再给（真名以后照常给、好感照算）。</li>
 *   <li>号角 9 条：没受眷顾注风被拒且不扣 ESPE；领地全拒时不召、不消耗；召来的法师只盯召唤者、打旁人的箭被取消；别人打死不掉落、召唤者打死掉受困之风；
 *       召唤者的狼打死不掉、召唤者打过以后 5 秒内被烧死照样掉；召唤者离开 64 格后法师消失；冷却中不响应；
 *       赶来途中下线 / 死了 / 走远 / 生成点被堵都退号角；「夜里」和原版一样算、重生 / 换维度后补回物品冷却。</li>
 *   <li>手册步骤 2 条：号角、大法师两步按拿到东西判（受困之风、石板、号角）；和平难度下戴捕风挂坠捕到受困之风也走得下去。</li>
 * </ul>
 * 另有迁移 1 条（{@link #windWishLegacyMigration}：老存档心愿数记满、调高门槛后开过门的老玩家由迁移表的动作记满）；登录时补的 true_name 不当证明进度在
 * {@code GuideMigrationTests.guideMigrationLoginTrueNameNotProof}。
 * 玩家都是 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer，不进世界、不进玩家列表，所以服务端的每刻事件不会替它推进，计时全由测试手动调）。
 * 号角的维度 / 露天 / 夜里 / 和平用 {@code WindHornItem.Config} 的测试覆盖跳过；召唤者要「在线」时用 {@code ECProtection.setOnlineOverride}。
 * 每条结束都撤掉测试覆盖、清掉批记录。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindTrialTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos RUNE = new BlockPos(5, 2, 5);
    private static final double EPS = 1.0E-6D;
    /** 手册第十三章「大法师」「风之号角」两步的进度 */
    private static final ResourceLocation ARCHMAGE_STEP = EssentialCraft5.id("guide/wind/archmage");
    private static final ResourceLocation HORN_STEP = EssentialCraft5.id("guide/wind/wind_horn");

    private WindTrialTests() {
    }

    // ================================================================ 工具

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    /** 受到风之眷顾的真玩家 */
    private static ServerPlayer windbound(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        data(player).setWindbound(true);
        return player;
    }

    /** 能挨打的真玩家：先跑掉 60 tick 的出生无敌（同 CorruptionTests.victim） */
    private static ServerPlayer hittable(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        return player;
    }

    /** 直接给一个心愿（期限从现在起 deadline 刻） */
    private static void giveWish(ServerPlayer player, WindWishes.Type type, String param, long deadline) {
        long now = player.level().getGameTime();
        data(player).setWindWish(new WindWishes.Wish(type, param, now + deadline, 0D).toTag(), true);
        WindWishes.refreshTracker(player);
    }

    private static WindWishes.Wish wish(Player player) {
        return WindWishes.current(player);
    }

    private static double progress(Player player) {
        WindWishes.Wish wish = wish(player);
        return wish == null ? -1D : wish.progress();
    }

    /** 把玩家放到测试区正上方的高空（Y 210，头顶露天、离地面远） */
    private static void high(GameTestHelper helper, ServerPlayer player) {
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 1D, 0.5D));
        player.moveTo(at.x, 210D, at.z, 0F, 0F);
    }

    private static boolean done(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static Holder<Biome> biome(GameTestHelper helper, net.minecraft.resources.ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
    }

    private static void cleanup(ServerPlayer... players) {
        WindWishes.Config.clearTestOverrides();
        WindHornItem.Config.clearTestOverrides();
        for (ServerPlayer player : players) {
            ECProtection.clearOnlineOverride(player.getUUID());
            WindMageSummons.clear(player.getUUID());
        }
    }

    /** 摆出整座风之符文祭坛（同 WindTests），返回符文的方块实体 */
    private static WindRuneBlockEntity buildAltar(GameTestHelper helper) {
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECWind.RUNE_STRUCTURE.rules().entrySet()) {
            helper.setBlock(RUNE.offset(entry.getKey()), entry.getValue().displayState());
        }
        if (!(helper.getBlockEntity(RUNE) instanceof WindRuneBlockEntity rune)) {
            throw new IllegalStateException("符文那一格应有方块实体");
        }
        return rune;
    }

    private static List<BlockPos> pillars() {
        return ECWind.RUNE_STRUCTURE.offsets(ECWind.RUNE_PILLAR).stream().map(RUNE::offset).toList();
    }

    private static void fillPillars(GameTestHelper helper, double each) {
        for (BlockPos pos : pillars()) {
            if (helper.getBlockEntity(pos) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                pillar.getESPEStorage().setESPE(each);
            }
        }
    }

    private static double totalESPE(GameTestHelper helper) {
        double total = 0D;
        for (BlockPos pos : pillars()) {
            if (helper.getBlockEntity(pos) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                total += pillar.getESPEStorage().getESPE();
            }
        }
        return total;
    }

    /**
     * 召唤场地：empty_large 里 y = 1 一排石头（x 2~8、z = 8），生成点在 y = 2 上（相对坐标），召唤者站在 (1, 2, 8)。
     * 返回生成点的绝对坐标。
     */
    private static List<BlockPos> summonField(GameTestHelper helper, ServerPlayer summoner) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = 1; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 8), Blocks.STONE);
        }
        for (int x = 3; x <= 7; x += 2) {
            spots.add(helper.absolutePos(new BlockPos(x, 2, 8)));
        }
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 8.5D));
        summoner.moveTo(at.x, at.y, at.z, 0F, 0F);
        return spots;
    }

    /** 召来一批（在线覆盖 + plan + spawn），返回法师；第一只是大法师 */
    private static List<WindMageEntity> summon(GameTestHelper helper, ServerPlayer summoner) {
        ECProtection.setOnlineOverride(summoner);
        List<BlockPos> spots = summonField(helper, summoner);
        for (BlockPos spot : spots) {
            helper.assertTrue(WindMageSummons.standable(helper.getLevel(), spot), "生成点应能站：" + spot.toShortString());
        }
        WindMageSummons.Plan plan = WindMageSummons.plan(summoner, spots);
        helper.assertTrue(plan != null && plan.points().size() == spots.size(), "没有领地时三个点都应探测放行，实际 " + plan);
        return WindMageSummons.spawn(summoner, plan);
    }

    private static void discard(List<WindMageEntity> mages) {
        for (WindMageEntity mage : mages) {
            if (!mage.isRemoved()) {
                mage.discard();
            }
        }
    }

    /**
     * 让背包变化像在游戏里一样触发 inventory_changed：测试玩家不进玩家列表，原版不会替它挂背包监听器（PlayerList.placeNewPlayer 里的
     * initInventoryMenu），这里补上；之后每次放东西调 {@link #pickUp}（原版每刻 broadcastChanges 时才比对格子、发触发器）。
     */
    private static void listenInventory(ServerPlayer player) {
        player.initInventoryMenu();
    }

    /** 把东西放进背包，再像每刻一样比对一次格子（触发 inventory_changed） */
    private static void pickUp(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
    }

    /** 附近（aabb 里）有几个是这种物品的掉落物 */
    private static int dropsOf(ServerLevel level, AABB around, net.minecraft.world.item.Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(item)).size();
    }

    private static void clearDrops(ServerLevel level, AABB around) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, around)) {
            drop.discard();
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, around)) {
            orb.discard();
        }
    }

    /** 戴上捕风挂坠（直接放进项链槽，同 BaublesTests.wear），并让饰品缓存作废 */
    private static void wearCatcher(GameTestHelper helper, Player player) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player).resolve();
        ICurioStacksHandler handler = inventory.map(h -> h.getCurios().get(CuriosCompat.NECKLACE)).orElse(null);
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 项链槽");
            return;
        }
        handler.getStacks().setStackInSlot(0, new ItemStack(ECBaubles.WIND_CATCHER.get()));
        CuriosCompat.invalidate(player);
    }

    /** 第一次 nextFloat 落在捕风挂坠几率以内的随机源（挨着的小种子第一次 nextFloat 几乎一样，候选种子乘个大奇数打散） */
    private static RandomSource luckyRandom() {
        for (long i = 1; i < 10_000; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            if (RandomSource.create(seed).nextFloat() <= WindRelations.CATCHER_CHANCE) {
                return RandomSource.create(seed);
            }
        }
        throw new IllegalStateException("找不到第一次 nextFloat ≤ " + WindRelations.CATCHER_CHANCE + " 的种子");
    }

    // ================================================================ 心愿

    /** 没受眷顾问不到：NOT_WINDBOUND，身上没有心愿；功能关掉时 DISABLED */
    @GameTest(template = EMPTY)
    public static void windWishNeedsWindbound(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.NOT_WINDBOUND, "没受眷顾应问不到");
            helper.assertTrue(wish(player) == null && !WindWishes.isTracked(player), "不应有心愿");
            data(player).setWindbound(true);
            WindWishes.Config.setTestOverrides(false, null, null);
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.DISABLED && wish(player) == null, "功能关掉时不给");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 受眷顾后给出一个、期限是 deadline_ticks；再问一遍只提醒、心愿不变 */
    @GameTest(template = EMPTY)
    public static void windWishOneAtATime(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            long now = player.level().getGameTime();
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.GIVEN, "应给出一个心愿");
            WindWishes.Wish first = wish(player);
            helper.assertTrue(first != null && first.deadline() == now + WindWishes.Config.deadlineTicks() && first.progress() == 0D,
                    "期限应是现在 + " + WindWishes.Config.deadlineTicks() + "，实际 " + first);
            helper.assertTrue(WindWishes.isTracked(player), "给出后应开始追踪");
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.ACTIVE, "已有心愿时只提醒");
            helper.assertTrue(first.equals(wish(player)), "心愿不应被换掉");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 只给做得到的：不背鞘翅不给乘风、不打雷不给听雷；穿上鞘翅、打起雷来才有；随便挑 200 次也挑不出做不到的 */
    @GameTest(template = EMPTY)
    public static void windWishOnlyFeasible(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            WindWishes.Feasibility calm = WindWishes.Feasibility.of(player).withThundering(false);
            List<WindWishes.Type> options = WindWishes.options(calm, null);
            helper.assertTrue(!options.contains(WindWishes.Type.GLIDE) && !options.contains(WindWishes.Type.THUNDER),
                    "没鞘翅、没打雷时不应给乘风 / 听雷，实际 " + options);
            helper.assertTrue(options.contains(WindWishes.Type.CHASE) && options.contains(WindWishes.Type.FEATHER) && options.contains(WindWishes.Type.ABROAD),
                    "追风、落羽、他乡总能给，实际 " + options);
            RandomSource random = RandomSource.create(0x9E3779B97F4A7C15L);
            for (int i = 0; i < 200; i++) {
                WindWishes.Wish picked = WindWishes.pick(calm, null, random, 0L);
                helper.assertTrue(picked != null && picked.type() != WindWishes.Type.GLIDE && picked.type() != WindWishes.Type.THUNDER,
                        "挑出了做不到的心愿：" + picked);
                if (picked.type() == WindWishes.Type.TRAVEL) {
                    WindWishes.TravelTarget target = WindWishes.TravelTarget.byId(picked.param());
                    helper.assertTrue(target != null && !target.matches(calm.biome()), "远行不该点玩家脚下的群系：" + picked.param());
                }
            }
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            WindWishes.Feasibility winged = WindWishes.Feasibility.of(player);
            helper.assertTrue(winged.elytra() && WindWishes.options(winged.withThundering(false), null).contains(WindWishes.Type.GLIDE), "背上鞘翅应能给乘风");
            helper.assertTrue(WindWishes.options(winged.withThundering(true), null).contains(WindWishes.Type.THUNDER), "打雷时应能给听雷");
            helper.assertTrue(!WindWishes.options(winged, WindWishes.Type.CHASE).contains(WindWishes.Type.CHASE), "换的时候排除当前这种");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 登高：Y 200 以上露天 30 秒（29 秒还差一点）；追风：疾跑 400 格，一秒最多记 20 格（传送一大步只算 20） */
    @GameTest(template = EMPTY)
    public static void windWishClimbAndChase(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            high(helper, player);
            giveWish(player, WindWishes.Type.CLIMB, "", 72_000L);
            for (int i = 0; i < WindWishes.CLIMB_SECONDS - 1; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(progress(player) == WindWishes.CLIMB_SECONDS - 1, "29 秒时还没完成，实际进度 " + progress(player));
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 1, "30 秒登高完成，心愿数 1");

            giveWish(player, WindWishes.Type.CHASE, "", 72_000L);
            player.setSprinting(true);
            WindWishes.secondTick(player);   // 第一秒只记位置
            player.moveTo(player.getX() + 100D, player.getY(), player.getZ());
            WindWishes.secondTick(player);
            helper.assertTrue(Math.abs(progress(player) - WindWishes.CHASE_MAX_STEP) < EPS, "一秒位移 100 只记 20，实际 " + progress(player));
            player.setSprinting(false);
            player.moveTo(player.getX() + 10D, player.getY(), player.getZ());
            WindWishes.secondTick(player);
            helper.assertTrue(Math.abs(progress(player) - WindWishes.CHASE_MAX_STEP) < EPS, "没在疾跑不算，实际 " + progress(player));
            player.setSprinting(true);
            int steps = (WindWishes.CHASE_BLOCKS - WindWishes.CHASE_MAX_STEP) / 10;
            for (int i = 0; i < steps; i++) {
                helper.assertTrue(wish(player) != null, "第 " + i + " 步之前不该完成");
                player.moveTo(player.getX() + 10D, player.getY(), player.getZ());
                WindWishes.secondTick(player);
            }
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 2, "跑满 400 格完成，心愿数 2，实际 " + WindWishes.count(player));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 远行：点名恶地，在平原不算、走进恶地就成；他乡：在给心愿时的维度不计时，到别的维度待满 120 秒 */
    @GameTest(template = EMPTY)
    public static void windWishTravelAndAbroad(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            giveWish(player, WindWishes.Type.TRAVEL, WindWishes.TravelTarget.BADLANDS.id(), 72_000L);
            WindWishes.Config.setTestEnvironment(null, null, biome(helper, Biomes.PLAINS));
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) != null, "还在平原，不算");
            WindWishes.Config.setTestEnvironment(null, null, biome(helper, Biomes.ERODED_BADLANDS));
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 1, "走进恶地（风蚀恶地也算）就完成");
            WindWishes.Config.clearTestOverrides();

            String here = player.level().dimension().location().toString();
            giveWish(player, WindWishes.Type.ABROAD, here, 72_000L);
            for (int i = 0; i < 10; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(progress(player) == 0D, "还在给心愿的维度，不计时，实际 " + progress(player));
            giveWish(player, WindWishes.Type.ABROAD, "minecraft:the_nether", 72_000L);
            for (int i = 0; i < WindWishes.ABROAD_SECONDS - 1; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(progress(player) == WindWishes.ABROAD_SECONDS - 1, "119 秒还差一点，实际 " + progress(player));
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 2, "到别的维度待满 2 分钟完成");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 落羽：落下 30 格、中途挨了摔落伤害不算（伤害走真的 player.hurt → LivingDamageEvent 监听），再落一次不受伤就成；
     * 月下：白天跳不算，夜里露天连跳 32 次（起跳走真的 jumpFromGround → LivingJumpEvent 监听）
     */
    @GameTest(template = EMPTY)
    public static void windWishFeatherAndMoon(GameTestHelper helper) {
        ServerPlayer player = hittable(helper, "ec5-feather");
        data(player).setWindbound(true);
        try {
            high(helper, player);
            double top = player.getY();
            giveWish(player, WindWishes.Type.FEATHER, "", 72_000L);
            player.setOnGround(false);
            WindWishes.featherTick(player);
            player.moveTo(player.getX(), top - 30D, player.getZ());
            float health = player.getHealth();
            helper.assertTrue(player.hurt(player.damageSources().fall(), 5F) && player.getHealth() < health, "摔落伤害应真的落下");
            player.setOnGround(true);
            WindWishes.featherTick(player);
            helper.assertTrue(wish(player) != null, "挨了摔落伤害的那一次不算");
            player.moveTo(player.getX(), top, player.getZ());
            player.setOnGround(false);
            WindWishes.featherTick(player);
            player.moveTo(player.getX(), top - 10D, player.getZ());
            player.setOnGround(true);
            WindWishes.featherTick(player);
            helper.assertTrue(wish(player) != null, "只落 10 格不算");
            player.moveTo(player.getX(), top, player.getZ());
            player.setOnGround(false);
            WindWishes.featherTick(player);
            player.moveTo(player.getX(), top - WindWishes.FEATHER_HEIGHT, player.getZ());
            player.setOnGround(true);
            WindWishes.featherTick(player);
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 1, "落 25 格没受伤就完成");

            high(helper, player);
            giveWish(player, WindWishes.Type.MOON, "", 72_000L);
            WindWishes.Config.setTestEnvironment(false, null, null);
            for (int i = 0; i < 5; i++) {
                player.jumpFromGround();
            }
            helper.assertTrue(progress(player) == 0D, "白天跳不算，实际 " + progress(player));
            WindWishes.Config.setTestEnvironment(true, null, null);
            for (int i = 0; i < WindWishes.MOON_JUMPS - 1; i++) {
                player.jumpFromGround();
            }
            helper.assertTrue(progress(player) == WindWishes.MOON_JUMPS - 1, "31 次还差一次，实际 " + progress(player));
            player.jumpFromGround();
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 2, "夜里连跳 32 次完成");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 乘风：滑翔 100 格后落地重数，再连续滑 200 格就成；听雷：不打雷不计时，雷雨里露天站满 60 秒 */
    @GameTest(template = EMPTY)
    public static void windWishGlideAndThunder(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            high(helper, player);
            giveWish(player, WindWishes.Type.GLIDE, "", 72_000L);
            player.startFallFlying();
            WindWishes.secondTick(player);
            for (int i = 0; i < 10; i++) {
                player.moveTo(player.getX() + 10D, player.getY(), player.getZ());
                WindWishes.secondTick(player);
            }
            helper.assertTrue(Math.abs(progress(player) - 100D) < EPS, "滑了 100 格，实际 " + progress(player));
            player.stopFallFlying();
            WindWishes.secondTick(player);
            helper.assertTrue(progress(player) == 0D, "落地重数，实际 " + progress(player));
            player.startFallFlying();
            WindWishes.secondTick(player);
            for (int i = 0; i < WindWishes.GLIDE_BLOCKS / 10; i++) {
                player.moveTo(player.getX() + 10D, player.getY(), player.getZ());
                WindWishes.secondTick(player);
            }
            player.stopFallFlying();
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 1, "连续滑 200 格完成");

            giveWish(player, WindWishes.Type.THUNDER, "", 72_000L);
            WindWishes.Config.setTestEnvironment(null, false, null);
            for (int i = 0; i < 5; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(progress(player) == 0D, "不打雷不计时，实际 " + progress(player));
            WindWishes.Config.setTestEnvironment(null, true, null);
            for (int i = 0; i < WindWishes.THUNDER_SECONDS - 1; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(progress(player) == WindWishes.THUNDER_SECONDS - 1, "59 秒还差一点，实际 " + progress(player));
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == 2, "雷雨里站满 60 秒完成");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 过期不罚：心愿没了、心愿数和好感都不变、开始冷却，冷却中问得到 COOLDOWN；冷却过了再问能给 */
    @GameTest(template = EMPTY)
    public static void windWishExpiresWithoutPenalty(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            IPlayerData data = data(player);
            data.setWindPoints(500);
            giveWish(player, WindWishes.Type.CHASE, "", -1L);
            long now = player.level().getGameTime();
            WindWishes.secondTick(player);
            helper.assertTrue(wish(player) == null && !WindWishes.isTracked(player), "过期后心愿没了");
            helper.assertTrue(WindWishes.count(player) == 0 && data.getWindPoints() == 500, "过期不罚：心愿数 0、好感 500 不变");
            helper.assertTrue(WindWishes.cooldownUntil(player) == now + WindWishes.Config.cooldownTicks(),
                    "冷却到现在 + " + WindWishes.Config.cooldownTicks() + "，实际 " + WindWishes.cooldownUntil(player));
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.COOLDOWN && wish(player) == null, "冷却中问不到");
            CompoundTag elapsed = new CompoundTag();
            elapsed.putLong("cooldown", now);
            data.setWindWish(elapsed, true);
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.GIVEN && wish(player) != null, "冷却过了能再问");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 存下的冷却 / 期限比「现在 + 配置」还晚（游戏刻对不上，比如数据从别的服搬来）：按现在重新起算，不会等几天 */
    @GameTest(template = EMPTY)
    public static void windWishClockMismatchReanchors(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            IPlayerData data = data(player);
            long now = player.level().getGameTime();
            CompoundTag stale = new CompoundTag();
            stale.putLong("cooldown", now + 26_000_000L);
            data.setWindWish(stale, true);
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.COOLDOWN, "冷却中问不到");
            helper.assertTrue(WindWishes.cooldownUntil(player) == now + WindWishes.Config.cooldownTicks(),
                    "冷却改按现在起算，实际 " + (WindWishes.cooldownUntil(player) - now));

            giveWish(player, WindWishes.Type.CHASE, "", 26_000_000L);
            WindWishes.secondTick(player);
            WindWishes.Wish wish = wish(player);
            helper.assertTrue(wish != null && wish.deadline() - now <= WindWishes.Config.deadlineTicks(),
                    "期限也按现在起算，实际 " + (wish == null ? "没了" : wish.deadline() - now));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 风之药水换一个：受眷顾、潜行、主手风之药水点符文时 RightClickBlock 放行方块交互（没受眷顾的不放行，照常去喝）；
     * 符文右键换成别的种类、药水少一瓶；手上没有心愿时药水不动；祭坛不完整时什么都不换
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windWishSwapWithAirPotion(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        ServerPlayer player = windbound(helper);
        ServerPlayer stranger = ProtectionFrameworkTests.realPlayer(helper);
        try {
            BlockPos runePos = helper.absolutePos(RUNE);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(runePos), net.minecraft.core.Direction.UP, runePos, false);
            for (ServerPlayer p : List.of(player, stranger)) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECWind.AIR_POTION.get(), 3));
                p.setShiftKeyDown(true);
            }
            PlayerInteractEvent.RightClickBlock allowed = ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, runePos, hit);
            PlayerInteractEvent.RightClickBlock normal = ForgeHooks.onRightClickBlock(stranger, InteractionHand.MAIN_HAND, runePos, hit);
            helper.assertTrue(allowed.getUseBlock() == Event.Result.ALLOW, "受眷顾的人应放行方块交互，实际 " + allowed.getUseBlock());
            helper.assertTrue(normal.getUseBlock() == Event.Result.DEFAULT, "没受眷顾的不放行（照常喝药水），实际 " + normal.getUseBlock());
            helper.assertTrue(rune.wouldHandle(player) && !rune.wouldHandle(stranger), "客户端预判：受眷顾的归符文，没受眷顾的让给喝药水");
            helper.assertTrue(rune.imbue(stranger) == WindRuneBlockEntity.Outcome.PASS && stranger.getMainHandItem().getCount() == 3,
                    "没受眷顾的人这样点符文什么都不做");

            helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.WISH && player.getMainHandItem().getCount() == 3,
                    "手上没有心愿时药水不动");
            giveWish(player, WindWishes.Type.CHASE, "", 72_000L);
            helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.WISH, "应换心愿");
            WindWishes.Wish swapped = wish(player);
            helper.assertTrue(swapped != null && swapped.type() != WindWishes.Type.CHASE, "应换成别的种类，实际 " + swapped);
            helper.assertTrue(player.getMainHandItem().is(ECWind.AIR_POTION.get()) && player.getMainHandItem().getCount() == 2, "药水用掉一瓶");

            helper.setBlock(RUNE.offset(1, -1, 1), Blocks.STONE);
            WindWishes.Wish before = wish(player);
            helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.NOT_FORMED && before.equals(wish(player))
                    && player.getMainHandItem().getCount() == 2, "祭坛不完整时不换、不扣药水");
        } finally {
            cleanup(player, stranger);
        }
        helper.succeed();
    }

    /** 完成时：好感 +100 实得（系数 0.1 × 1000）、心愿数 +1、第一个心愿发 guide/wind/wish */
    @GameTest(template = EMPTY)
    public static void windWishCompletionRewards(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try {
            high(helper, player);
            int expected = WindAffinity.scaled(WindWishes.Config.points(), WindAffinity.coefficient(player));
            helper.assertTrue(expected == 100, "默认配置下一个心愿实得 100，实际 " + expected);
            helper.assertTrue(!done(player, WindWishes.WISH_ADVANCEMENT), "还没完成过心愿");
            giveWish(player, WindWishes.Type.ABROAD, "minecraft:the_end", 72_000L);
            for (int i = 0; i < WindWishes.ABROAD_SECONDS; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(data(player).getWindPoints() == expected, "好感应 +" + expected + "，实际 " + data(player).getWindPoints());
            helper.assertTrue(WindWishes.count(player) == 1 && done(player, WindWishes.WISH_ADVANCEMENT), "心愿数 1、发了手册进度");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 石板回应要好感和心愿都够；required 0 或功能关掉时只看好感；成立时 checkTrueName 发 true_name */
    @GameTest(template = EMPTY)
    public static void windTabletNeedsAffinityAndWishes(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            IPlayerData data = data(player);
            int threshold = ECWind.Config.tabletPoints();
            int required = WindWishes.required();
            helper.assertTrue(required == WindWishes.Config.DEFAULT_REQUIRED, "默认要 7 个心愿，实际 " + required);
            data.setWindPoints(threshold);
            helper.assertTrue(WindTabletItem.nameRevealed(player) && !WindTabletItem.attuned(player), "好感够、心愿 0：真名显全但不回应");
            WindWishes.checkTrueName(player);
            helper.assertTrue(!done(player, WindWishes.TRUE_NAME_ADVANCEMENT), "心愿不够时不发 true_name");
            data.setWindPoints(0);
            data.setWindWishes(required);
            helper.assertTrue(!WindTabletItem.attuned(player), "心愿够、好感 0：不回应");
            WindWishes.Config.setTestOverrides(null, 0, null);
            data.setWindWishes(0);
            data.setWindPoints(threshold);
            helper.assertTrue(WindTabletItem.attuned(player), "required = 0 时只看好感");
            WindWishes.Config.setTestOverrides(false, null, null);
            helper.assertTrue(WindWishes.required() == 0 && WindTabletItem.attuned(player), "心愿功能关掉时不要心愿");
            WindWishes.Config.clearTestOverrides();
            data.setWindWishes(required);
            helper.assertTrue(WindTabletItem.attuned(player), "两样都够：回应");
            WindWishes.checkTrueName(player);
            helper.assertTrue(done(player, WindWishes.TRUE_NAME_ADVANCEMENT), "成立时发 guide/hoanna/true_name");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 真名之前只给 required 个：心愿数够了、好感没到门槛时 ask 返回 RESTING，好感和心愿数都不变；手上还有一个（服主中途调低 required）时做完照样记数、不给好感；
     * 好感到了门槛（真名显全）以后照常给，完成照样实得 100；required = 0 时真名之前一个都不给
     */
    @GameTest(template = EMPTY)
    public static void windWishRestsBeforeTrueName(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        ServerPlayer zero = windbound(helper);
        try {
            IPlayerData data = data(player);
            int required = WindWishes.required();
            int threshold = ECWind.Config.tabletPoints();
            helper.assertTrue(required > 0 && threshold > 1000, "默认配置：要心愿、门槛高于 1000");
            data.setWindWishes(required);
            data.setWindPoints(1000);
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.RESTING, "心愿够了、真名没显全：风不再给");
            helper.assertTrue(wish(player) == null && data.getWindPoints() == 1000 && WindWishes.count(player) == required, "好感、心愿数都不变");

            giveWish(player, WindWishes.Type.ABROAD, "minecraft:the_end", 72_000L);
            for (int i = 0; i < WindWishes.ABROAD_SECONDS; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(wish(player) == null && WindWishes.count(player) == required + 1, "手上那一个做完照样记数");
            helper.assertTrue(data.getWindPoints() == 1000, "真名之前第 required + 1 个不给好感，实际 " + data.getWindPoints());

            data.setWindPoints(threshold);
            helper.assertTrue(WindTabletItem.nameRevealed(player), "好感到门槛：真名显全");
            helper.assertTrue(WindWishes.ask(player) == WindWishes.AskResult.GIVEN && wish(player) != null, "真名以后照常给");
            int expected = WindAffinity.scaled(WindWishes.Config.points(), WindAffinity.coefficient(player));
            giveWish(player, WindWishes.Type.ABROAD, "minecraft:the_end", 72_000L);
            for (int i = 0; i < WindWishes.ABROAD_SECONDS; i++) {
                WindWishes.secondTick(player);
            }
            helper.assertTrue(data.getWindPoints() == threshold + expected, "真名以后完成照样实得 " + expected + "，实际 " + (data.getWindPoints() - threshold));

            WindWishes.Config.setTestOverrides(null, 0, null);
            helper.assertTrue(WindWishes.ask(zero) == WindWishes.AskResult.RESTING && wish(zero) == null, "required = 0：真名之前一个都不给");
        } finally {
            cleanup(player, zero);
        }
        helper.succeed();
    }

    // ================================================================ 号角

    /** 风之号角的注风配方要已受眷顾：没受眷顾被拒、不扣 ESPE、山羊角还在；受眷顾后换出号角、扣 general.wind_horn.espe */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornImbueNeedsWindbound(GameTestHelper helper) {
        WindRuneBlockEntity rune = buildAltar(helper);
        fillPillars(helper, 5000D);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            ItemStack goatHorn = new ItemStack(Items.GOAT_HORN);
            WindImbueRecipe recipe = WindImbueRecipe.find(helper.getLevel(), goatHorn);
            helper.assertTrue(recipe != null && recipe.requiresWindbound() && recipe.getResultItem(helper.getLevel().registryAccess()).is(ECWind.WIND_HORN.get()),
                    "山羊角应能注风成风之号角，且要已受眷顾");
            double cost = recipe.getEspe();
            helper.assertTrue(Math.abs(cost - WindHornItem.Config.espe()) < EPS, "花费读 general.wind_horn.espe，实际 " + cost);
            double before = totalESPE(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, goatHorn);
            helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.NEEDS_WINDBOUND, "没受眷顾应被拒");
            helper.assertTrue(Math.abs(totalESPE(helper) - before) < EPS && player.getMainHandItem().is(Items.GOAT_HORN), "不扣 ESPE、山羊角还在");
            data(player).setWindbound(true);
            helper.assertTrue(rune.imbue(player) == WindRuneBlockEntity.Outcome.IMBUED, "受眷顾后应注风成功");
            helper.assertTrue(player.getMainHandItem().is(ECWind.WIND_HORN.get()), "主手换成风之号角");
            helper.assertTrue(Math.abs(before - totalESPE(helper) - cost) < EPS, "扣 " + cost + " ESPE，实际 " + (before - totalESPE(helper)));
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 生成点全在别人领地：以召唤者身份探测被拒 → NO_SPACE，号角不消耗、没有待召唤；领地撤掉后召唤成功、号角用掉、开始冷却 */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornDeniedByClaims(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            WindHornItem.Config.setTestOverrides(true, null);
            List<BlockPos> spots = summonField(helper, player);
            BlockPos a = spots.get(0);
            BlockPos b = spots.get(spots.size() - 1);
            claims.denyBox(new BoundingBox(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
            ItemStack horn = new ItemStack(ECWind.WIND_HORN.get());
            helper.assertTrue(WindHornItem.blow(player, horn, spots) == WindHornItem.Refusal.NO_SPACE, "领地全拒时应不召");
            helper.assertTrue(horn.getCount() == 1 && !WindMageSummons.hasBatch(player.getUUID()), "号角不消耗、没有待召唤的一批");
            int probes = 0;
            for (BlockPos spot : spots) {
                probes += claims.breaksAt(spot);
            }
            helper.assertTrue(probes == spots.size(), "每个生成点各探测一次，实际 " + probes);
            helper.assertTrue(data(player).getWindHornCooldown() == 0L, "没召成不进冷却");

            claims.clearBoxes();
            ECProtection.forget(player.getUUID());
            helper.assertTrue(WindHornItem.blow(player, horn, spots) == WindHornItem.Refusal.NONE, "领地撤掉后应召唤成功");
            helper.assertTrue(horn.isEmpty() && WindMageSummons.hasBatch(player.getUUID()), "号角用掉、排进待召唤");
            helper.assertTrue(data(player).getWindHornCooldown() == player.level().getGameTime() + WindHornItem.Config.cooldownTicks(), "开始冷却");
        } finally {
            cleanup(player);
        }
        helper.succeed();
    }

    /** 召来的一批：大法师 + 2 随从，都盯着召唤者、记着召唤者；换目标被拦；它们的箭打旁人、打牛都被取消，打召唤者照中 */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornMagesOnlyTargetSummoner(GameTestHelper helper) {
        ServerPlayer summoner = hittable(helper, "ec5-summoner");
        data(summoner).setWindbound(true);
        ServerPlayer bystander = hittable(helper, "ec5-bystander");
        List<WindMageEntity> mages = List.of();
        try {
            mages = summon(helper, summoner);
            helper.assertTrue(mages.size() == 1 + WindHornItem.Config.escorts(), "应召来 1 + " + WindHornItem.Config.escorts() + " 只，实际 " + mages.size());
            helper.assertTrue(mages.get(0).getMageType() == WindMageEntity.ARCHMAGE, "第一只是大法师");
            for (int i = 1; i < mages.size(); i++) {
                helper.assertTrue(mages.get(i).getMageType() != WindMageEntity.ARCHMAGE, "随从是学徒或法师");
            }
            for (WindMageEntity mage : mages) {
                helper.assertTrue(summoner.getUUID().equals(mage.getSummonedBy()) && mage.getTarget() == summoner, "都盯着召唤者");
                mage.setTarget(bystander);
                helper.assertTrue(mage.getTarget() == summoner, "换成旁人应被拦下");
            }
            helper.assertTrue(WindMageSummons.batchSize(summoner.getUUID()) == mages.size(), "这一批记着全部法师");
            WindMageEntity shooter = mages.get(0);
            ServerLevel level = helper.getLevel();
            float before = bystander.getHealth();
            boolean hitBystander = bystander.hurt(level.damageSources().arrow(new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter), shooter), 4F);
            helper.assertTrue(!hitBystander && bystander.getHealth() == before, "打旁人的箭应被取消");
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "建一头牛");
            cow.moveTo(helper.absoluteVec(new Vec3(8.5D, 2D, 2.5D)));
            float cowBefore = cow.getHealth();
            helper.assertTrue(!cow.hurt(level.damageSources().arrow(new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter), shooter), 4F)
                    && cow.getHealth() == cowBefore, "打别的生物也取消");
            before = summoner.getHealth();
            boolean hitSummoner = summoner.hurt(level.damageSources().arrow(new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter), shooter), 4F);
            helper.assertTrue(hitSummoner && summoner.getHealth() < before, "打召唤者照中");
        } finally {
            discard(mages);
            cleanup(summoner, bystander);
        }
        helper.succeed();
    }

    /**
     * 别人打死召来的大法师：不掉受困之风、不掉经验，打死它的人手册的号角、大法师两步都不打勾（原版击杀归属记在他头上，进度不能按击杀判）；
     * 召唤者亲手打死：掉受困之风，捡起来两步才打勾
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornLootOnlyForSummoner(GameTestHelper helper) {
        ServerPlayer summoner = windbound(helper);
        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-thief");
        List<WindMageEntity> first = List.of();
        List<WindMageEntity> second = List.of();
        try {
            listenInventory(summoner);
            listenInventory(other);
            ServerLevel level = helper.getLevel();
            first = summon(helper, summoner);
            WindMageEntity stolen = first.get(0);
            AABB around = stolen.getBoundingBox().inflate(4D);
            stolen.hurt(level.damageSources().playerAttack(other), 1000F);
            helper.assertTrue(stolen.isDeadOrDying(), "别人一下打死");
            int keepers = dropsOf(level, around, ECWind.WIND_KEEPER.get());
            int orbs = level.getEntitiesOfClass(ExperienceOrb.class, around).size();
            helper.assertTrue(keepers == 0 && orbs == 0, "别人打死不掉落：受困之风 " + keepers + "、经验球 " + orbs);
            helper.assertTrue(!done(other, ARCHMAGE_STEP) && !done(other, HORN_STEP), "打死别人召来的大法师，手册两步都不打勾");
            helper.assertTrue(!done(summoner, ARCHMAGE_STEP) && !done(summoner, HORN_STEP), "召唤者没拿到受困之风，两步都不打勾");
            discard(first);
            WindMageSummons.clear(summoner.getUUID());

            second = summon(helper, summoner);
            WindMageEntity earned = second.get(0);
            AABB here = earned.getBoundingBox().inflate(4D);
            earned.hurt(level.damageSources().playerAttack(summoner), 1000F);
            helper.assertTrue(earned.isDeadOrDying(), "召唤者一下打死");
            int got = dropsOf(level, here, ECWind.WIND_KEEPER.get());
            helper.assertTrue(got >= 1, "召唤者亲手打死掉受困之风，实际 " + got);
            helper.assertTrue(!done(summoner, ARCHMAGE_STEP), "受困之风还在地上：这一步还没做完");
            pickUp(summoner, new ItemStack(ECWind.WIND_KEEPER.get()));
            helper.assertTrue(done(summoner, ARCHMAGE_STEP) && done(summoner, HORN_STEP), "捡起受困之风：号角、大法师两步都打勾");
            clearDrops(level, here.inflate(4D));
        } finally {
            discard(first);
            discard(second);
            cleanup(summoner, other);
            summoner.getAdvancements().stopListening();
            other.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    /**
     * 召唤者的狼补了最后一下：不掉、手册两步不打勾；召唤者打过一下、它在 5 秒内被烧死（致命一击没有攻击者实体）：算召唤者打倒的，照样掉受困之风；
     * 打过以后过了 100 刻才被烧死：不算
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornLootPetsAndFire(GameTestHelper helper) {
        ServerPlayer summoner = windbound(helper);
        List<WindMageEntity> mages = new ArrayList<>();
        try {
            listenInventory(summoner);
            ServerLevel level = helper.getLevel();
            List<WindMageEntity> first = summon(helper, summoner);
            mages.addAll(first);
            WindMageEntity bitten = first.get(0);
            Wolf wolf = EntityType.WOLF.create(level);
            helper.assertTrue(wolf != null, "建一只狼");
            wolf.moveTo(bitten.position());
            wolf.tame(summoner);
            AABB around = bitten.getBoundingBox().inflate(4D);
            bitten.hurt(level.damageSources().mobAttack(wolf), 1000F);
            helper.assertTrue(bitten.isDeadOrDying(), "狼一下咬死");
            helper.assertTrue(dropsOf(level, around, ECWind.WIND_KEEPER.get()) == 0 && level.getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(),
                    "召唤者的狼打死不掉落、不给经验");
            helper.assertTrue(!done(summoner, ARCHMAGE_STEP) && !done(summoner, HORN_STEP), "狼打死的，手册两步都不打勾");
            discard(first);
            WindMageSummons.clear(summoner.getUUID());

            List<WindMageEntity> second = summon(helper, summoner);
            mages.addAll(second);
            WindMageEntity burnt = second.get(0);
            AABB here = burnt.getBoundingBox().inflate(4D);
            burnt.hurt(level.damageSources().playerAttack(summoner), 1F);
            burnt.hurt(level.damageSources().onFire(), 1000F);
            helper.assertTrue(burnt.isDeadOrDying(), "打一下后烧死");
            helper.assertTrue(dropsOf(level, here, ECWind.WIND_KEEPER.get()) >= 1, "召唤者打过、5 秒内烧死：算召唤者打倒的，掉受困之风");
            clearDrops(level, here.inflate(4D));
            discard(second);
            WindMageSummons.clear(summoner.getUUID());

            List<WindMageEntity> third = summon(helper, summoner);
            mages.addAll(third);
            WindMageEntity late = third.get(0);
            AABB there = late.getBoundingBox().inflate(4D);
            late.hurt(level.damageSources().playerAttack(summoner), 1F);
            late.tickCount += WindMageSummons.SELF_KILL_WINDOW + 1;
            late.hurt(level.damageSources().onFire(), 1000F);
            helper.assertTrue(late.isDeadOrDying(), "过了 100 刻后烧死");
            helper.assertTrue(dropsOf(level, there, ECWind.WIND_KEEPER.get()) == 0, "召唤者打过已经超过 100 刻：不算，不掉");
            clearDrops(level, there.inflate(4D));
        } finally {
            discard(mages);
            cleanup(summoner);
            summoner.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    /** 召唤者在 64 格内不散；走到 70 格外、或者下线，召来的法师随风散去（直接移除，不死） */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornMagesLeaveWithSummoner(GameTestHelper helper) {
        ServerPlayer summoner = windbound(helper);
        List<WindMageEntity> mages = List.of();
        try {
            mages = summon(helper, summoner);
            WindMageEntity near = mages.get(0);
            helper.assertTrue(!WindMageSummons.checkMage(near) && !near.isRemoved(), "召唤者在身边时不散");
            Vec3 home = summoner.position();
            summoner.moveTo(home.x + 70D, home.y, home.z);
            helper.assertTrue(WindMageSummons.checkMage(near) && near.isRemoved() && near.getHealth() > 0F, "召唤者走出 64 格：散去（不是死）");
            summoner.moveTo(home.x, home.y, home.z);
            WindMageEntity second = mages.get(1);
            helper.assertTrue(!WindMageSummons.checkMage(second), "回来以后剩下的不散");
            ECProtection.clearOnlineOverride(summoner.getUUID());
            helper.assertTrue(WindMageSummons.checkMage(second) && second.isRemoved(), "召唤者下线：散去");
        } finally {
            discard(mages);
            cleanup(summoner);
        }
        helper.succeed();
    }

    /** 冷却中不响应（号角不消耗）；上一批还在时也不响应；冷却和批都清掉后又能吹；没受眷顾的人吹不响 */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornCooldown(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        ServerPlayer stranger = ProtectionFrameworkTests.realPlayer(helper);
        try {
            WindHornItem.Config.setTestOverrides(true, null);
            List<BlockPos> spots = summonField(helper, player);
            helper.assertTrue(WindHornItem.blow(player, new ItemStack(ECWind.WIND_HORN.get()), spots) == WindHornItem.Refusal.NONE, "第一次召唤成功");
            ItemStack horn = new ItemStack(ECWind.WIND_HORN.get());
            helper.assertTrue(WindHornItem.blow(player, horn, spots) == WindHornItem.Refusal.COOLDOWN && horn.getCount() == 1, "冷却中不响应、号角不消耗");
            data(player).setWindHornCooldown(0L);
            helper.assertTrue(WindHornItem.refusal(player) == WindHornItem.Refusal.BUSY, "上一批还在（正在赶来）时不响应");
            WindMageSummons.clear(player.getUUID());
            helper.assertTrue(WindHornItem.refusal(player) == WindHornItem.Refusal.NONE, "冷却和批都清掉后又能吹");
            helper.assertTrue(WindHornItem.refusal(stranger) == WindHornItem.Refusal.NOT_WINDBOUND, "没受眷顾的人吹不响");
            WindHornItem.Config.clearTestOverrides();
        } finally {
            cleanup(player, stranger);
        }
        helper.succeed();
    }

    /**
     * 号角退还：法师赶来途中召唤者下线（登出事件）退号角、清冷却、批取消；法师到场以后再下线不退；
     * 赶来那一刻召唤者死了（号角掉在脚下）、走出 64 格、生成点全被堵上，tickPending 都退号角、清冷却
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windHornRefunds(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        List<WindMageEntity> arrived = List.of();
        try {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            IPlayerData data = data(player);
            ECProtection.setOnlineOverride(player);
            WindHornItem.Config.setTestOverrides(true, null);
            List<BlockPos> spots = summonField(helper, player);
            Vec3 home = player.position();

            ItemStack horn = new ItemStack(ECWind.WIND_HORN.get());
            helper.assertTrue(WindHornItem.blow(player, horn, spots) == WindHornItem.Refusal.NONE && horn.isEmpty(), "吹响、号角用掉");
            helper.assertTrue(WindMageSummons.hasBatch(player.getUUID()) && data.getWindHornCooldown() > level.getGameTime(), "排进待召唤、开始冷却");
            WindMageSummons.WindMageSummonEvents.windSummonLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(!WindMageSummons.hasBatch(player.getUUID()), "赶来途中下线：这一批取消");
            helper.assertTrue(player.getInventory().countItem(ECWind.WIND_HORN.get()) == 1, "下线时号角还回背包");
            helper.assertTrue(data.getWindHornCooldown() == 0L && !player.getCooldowns().isOnCooldown(ECWind.WIND_HORN.get()), "冷却清掉");

            player.getInventory().clearContent();
            helper.assertTrue(WindHornItem.blow(player, new ItemStack(ECWind.WIND_HORN.get()), spots) == WindHornItem.Refusal.NONE, "再吹一次");
            arrived = WindMageSummons.spawnPending(player);
            helper.assertTrue(!arrived.isEmpty(), "法师到场");
            WindMageSummons.WindMageSummonEvents.windSummonLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(player.getInventory().countItem(ECWind.WIND_HORN.get()) == 0 && data.getWindHornCooldown() > 0L, "到场以后下线不退号角");
            discard(arrived);
            WindMageSummons.clear(player.getUUID());

            // 赶来那一刻（due = 现在）的三种退法：tickPending 直接跑一次
            WindMageSummons.Plan plan = WindMageSummons.plan(player, spots);
            helper.assertTrue(plan != null, "生成点照样能用");

            WindMageSummons.schedule(player, plan, level.getGameTime());
            WindHornItem.startCooldown(player);
            player.setHealth(0F);
            WindMageSummons.tickPending(server);
            player.setHealth(player.getMaxHealth());
            AABB feet = player.getBoundingBox().inflate(2D);
            helper.assertTrue(!WindMageSummons.hasBatch(player.getUUID()) && dropsOf(level, feet, ECWind.WIND_HORN.get()) == 1,
                    "召唤者死了：退号角（人死了，掉在脚下）");
            helper.assertTrue(data.getWindHornCooldown() == 0L, "死了退号角也清冷却");
            clearDrops(level, feet);

            WindMageSummons.schedule(player, plan, level.getGameTime());
            WindHornItem.startCooldown(player);
            player.moveTo(home.x + 70D, home.y, home.z);
            WindMageSummons.tickPending(server);
            player.moveTo(home.x, home.y, home.z);
            helper.assertTrue(!WindMageSummons.hasBatch(player.getUUID()) && player.getInventory().countItem(ECWind.WIND_HORN.get()) == 1
                    && data.getWindHornCooldown() == 0L, "召唤者走出 64 格：退号角、清冷却");
            player.getInventory().clearContent();

            WindMageSummons.schedule(player, plan, level.getGameTime());
            WindHornItem.startCooldown(player);
            for (BlockPos spot : spots) {
                level.setBlockAndUpdate(spot, Blocks.STONE.defaultBlockState());
            }
            WindMageSummons.tickPending(server);
            helper.assertTrue(!WindMageSummons.hasBatch(player.getUUID()) && player.getInventory().countItem(ECWind.WIND_HORN.get()) == 1
                    && data.getWindHornCooldown() == 0L, "生成点全被堵上：退号角、清冷却");
            helper.assertTrue(level.getEntitiesOfClass(WindMageEntity.class, new AABB(spots.get(0)).inflate(8D), m -> player.getUUID().equals(m.getSummonedBy())).isEmpty(),
                    "堵上以后一只都没生成");
        } finally {
            discard(arrived);
            cleanup(player);
        }
        helper.succeed();
    }

    /**
     * 「夜里」和原版一样算：一天里取几个时刻，号角的 night 和服务端每刻算的 isNight 一致（时间设完、算完立刻改回）；
     * 重生、换维度后按玩家数据补回原版物品冷却；客户端预判也看同步来的冷却
     */
    @GameTest(template = EMPTY)
    public static void windHornNightAndCooldownSync(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        long dayTime = level.getDayTime();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        try {
            for (long t : new long[]{0L, 1_000L, 6_000L, 11_000L, 12_500L, 13_000L, 13_500L, 18_000L, 22_500L, 23_000L, 23_500L}) {
                level.setDayTime(t);
                level.updateSkyBrightness();
                helper.assertTrue(WindHornItem.night(level) == level.isNight(), "时刻 " + t + "：号角的夜里应和原版 isNight 一致");
            }

            IPlayerData data = data(player);
            data.setWindHornCooldown(level.getGameTime() + 600L);
            player.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
            WindHornItem.WindHornEvents.windHornRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertTrue(player.getCooldowns().isOnCooldown(ECWind.WIND_HORN.get()), "重生后补回物品冷却");
            player.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
            WindHornItem.WindHornEvents.windHornChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.NETHER, Level.OVERWORLD));
            helper.assertTrue(player.getCooldowns().isOnCooldown(ECWind.WIND_HORN.get()), "换维度后补回物品冷却");

            if (difficulty == Difficulty.PEACEFUL) {
                server.setDifficulty(Difficulty.EASY, true);
            }
            high(helper, player);
            level.setDayTime(18_000L);
            level.updateSkyBrightness();
            helper.assertTrue(WindHornItem.clientRefusal(player) == WindHornItem.Refusal.COOLDOWN, "客户端预判看同步来的冷却");
            data.setWindHornCooldown(0L);
            helper.assertTrue(WindHornItem.clientRefusal(player) == WindHornItem.Refusal.NONE, "半夜、露天、不在冷却：客户端预判放行");
            player.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
            WindHornItem.WindHornEvents.windHornRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertTrue(!player.getCooldowns().isOnCooldown(ECWind.WIND_HORN.get()), "没在冷却时什么都不补");
            level.setDayTime(6_000L);
            level.updateSkyBrightness();
            if (!level.isNight()) {
                helper.assertTrue(WindHornItem.clientRefusal(player) == WindHornItem.Refusal.NOT_NIGHT, "正午（不打雷）：客户端预判不放行");
            }
        } finally {
            level.setDayTime(dayTime);
            level.updateSkyBrightness();
            if (server.getWorldData().getDifficulty() != difficulty) {
                server.setDifficulty(difficulty, true);
            }
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 手册步骤

    /** 号角、大法师两步按拿到东西判：拿到受困之风两步都打勾；拿到风之石板两步都打勾；只拿到号角只打号角那一步 */
    @GameTest(template = EMPTY)
    public static void windTrialStepsByItems(GameTestHelper helper) {
        ServerPlayer keeper = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer tablet = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer horn = ProtectionFrameworkTests.realPlayer(helper);
        try {
            for (ServerPlayer p : List.of(keeper, tablet, horn)) {
                listenInventory(p);
                helper.assertTrue(!done(p, ARCHMAGE_STEP) && !done(p, HORN_STEP), "开始时两步都没做");
            }
            pickUp(keeper, new ItemStack(ECWind.WIND_KEEPER.get()));
            helper.assertTrue(done(keeper, ARCHMAGE_STEP) && done(keeper, HORN_STEP), "拿到受困之风（不管从哪来）：号角、大法师两步都打勾");
            pickUp(tablet, new ItemStack(ECWind.WIND_TABLET.get()));
            helper.assertTrue(done(tablet, ARCHMAGE_STEP) && done(tablet, HORN_STEP), "拿到风之石板：两步都打勾");
            pickUp(horn, new ItemStack(ECWind.WIND_HORN.get()));
            helper.assertTrue(done(horn, HORN_STEP) && !done(horn, ARCHMAGE_STEP), "只有号角：只打号角那一步");
        } finally {
            for (ServerPlayer p : List.of(keeper, tablet, horn)) {
                p.getAdvancements().stopListening();
            }
            cleanup(keeper, tablet, horn);
        }
        helper.succeed();
    }

    /**
     * 和平难度：号角吹不响、也不会有法师；戴着捕风挂坠加好感时捕到受困之风，号角、大法师两步照样打勾，主线走得下去
     * （难度设完、测完立刻改回，中间没有刻推进，别的测试碰不到）
     */
    @GameTest(template = EMPTY)
    public static void windTrialPeacefulCatcher(GameTestHelper helper) {
        ServerPlayer player = windbound(helper);
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty difficulty = server.getWorldData().getDifficulty();
        try {
            listenInventory(player);
            wearCatcher(helper, player);
            server.setDifficulty(Difficulty.PEACEFUL, true);
            helper.assertTrue(WindHornItem.refusal(player) == WindHornItem.Refusal.PEACEFUL, "和平难度下号角吹不响");
            WindRelations.grant(player, WindWishes.Config.points(), luckyRandom());
            player.inventoryMenu.broadcastChanges();
            helper.assertTrue(player.getInventory().countItem(ECWind.WIND_KEEPER.get()) == 1, "捕风挂坠捕到一个受困之风");
            helper.assertTrue(done(player, ARCHMAGE_STEP) && done(player, HORN_STEP), "和平难度下靠捕风挂坠拿到受困之风：两步都打勾");
        } finally {
            if (server.getWorldData().getDifficulty() != difficulty) {
                server.setDifficulty(difficulty, true);
            }
            player.getAdvancements().stopListening();
            cleanup(player);
        }
        helper.succeed();
    }

    // ================================================================ 迁移

    /**
     * 老存档（玩家数据里没有 wishes 键）：好感已到门槛的心愿数记成 required、补 true_name；没到门槛的不动；
     * 有 wishes 键的新数据即使好感到了门槛也不动（不能靠喝药水跳过心愿）；
     * 第一次登录时心愿功能刚好关着，照样按配置的 required 记满（标记只取一次，不能白白取走）；
     * 好感低于（调高后的）门槛、但已经开过门的老玩家，手册迁移表补 true_name 时由动作记满心愿数，好感仍然不够、石板照样不回应
     */
    @GameTest(template = EMPTY)
    public static void windWishLegacyMigration(GameTestHelper helper) {
        ServerPlayer veteran = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer newcomer = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer potionDrinker = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer featureOff = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer gatekeeper = ProtectionFrameworkTests.realPlayer(helper);
        try {
            int threshold = ECWind.Config.tabletPoints();
            int required = WindWishes.required();
            for (ServerPlayer p : List.of(veteran, newcomer, featureOff, gatekeeper)) {
                if (!(PlayerDataEvents.get(p) instanceof PlayerData data)) {
                    throw new IllegalStateException("应是 PlayerData");
                }
                CompoundTag old = data.serializeNBT();
                old.remove("wishes");
                old.remove("wish");
                old.remove("wind_horn_cooldown");
                old.putInt("wind_points", p == veteran || p == featureOff ? threshold : threshold - 1);
                data.deserializeNBT(old);
            }
            helper.assertTrue(WindWishes.migrateLegacy(veteran) && WindWishes.count(veteran) == required, "好感到门槛的老玩家心愿数记成 " + required);
            WindWishes.checkTrueName(veteran);
            helper.assertTrue(done(veteran, WindWishes.TRUE_NAME_ADVANCEMENT) && WindTabletItem.attuned(veteran), "补 true_name，石板回应");
            helper.assertTrue(!WindWishes.migrateLegacy(veteran), "只迁移一次");
            helper.assertTrue(!WindWishes.migrateLegacy(newcomer) && WindWishes.count(newcomer) == 0, "没到门槛的老玩家照常要做心愿");
            data(potionDrinker).setWindPoints(threshold);
            helper.assertTrue(!WindWishes.migrateLegacy(potionDrinker) && WindWishes.count(potionDrinker) == 0, "新数据不迁移");

            WindWishes.Config.setTestOverrides(false, null, null);
            helper.assertTrue(WindWishes.required() == 0, "心愿功能关着时 required() 是 0");
            helper.assertTrue(WindWishes.migrateLegacy(featureOff) && WindWishes.count(featureOff) == WindWishes.Config.required(),
                    "第一次登录时心愿功能关着：照样按配置的 required 记满，实际 " + WindWishes.count(featureOff));
            WindWishes.Config.clearTestOverrides();

            helper.assertTrue(GuideMigration.hasAction(WindWishes.LEGACY_ACTION), "迁移动作 " + WindWishes.LEGACY_ACTION + " 应已登记");
            WindWishes.award(gatekeeper, EssentialCraft5.id("guide/hoanna/portal"));
            helper.assertTrue(!WindWishes.migrateLegacy(gatekeeper) && WindWishes.count(gatekeeper) == 0, "好感低于门槛：登录时不记满");
            GuideMigration.migrate(gatekeeper);
            helper.assertTrue(done(gatekeeper, WindWishes.TRUE_NAME_ADVANCEMENT), "开过门的老玩家：迁移表补 true_name");
            helper.assertTrue(WindWishes.count(gatekeeper) == required, "补 true_name 时心愿数记满，实际 " + WindWishes.count(gatekeeper));
            helper.assertTrue(!WindTabletItem.attuned(gatekeeper), "好感仍低于门槛：石板照样不回应");
        } finally {
            cleanup(veteran, newcomer, potionDrinker, featureOff, gatekeeper);
            gatekeeper.getAdvancements().stopListening();
        }
        helper.succeed();
    }
}
