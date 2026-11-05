package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.WindTier;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorMaterial;
import com.flameforge.essentialcraft5.loot.DungeonLootInjection;
import com.flameforge.essentialcraft5.loot.NotDisabledCondition;
import com.flameforge.essentialcraft5.network.C2SWingsPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.WingsFlight;
import com.flameforge.essentialcraft5.registry.ECEntities;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 第三批框架 G1~G7 的 GameTest：公共实体 / 效果 / 音效的登记与资源齐全、Curios 槽位与缓存、地牢注入与 not_disabled 条件、
 * 魔力之翼开关包、盔甲框架（登高修饰符、染色）、风之 Tier 排序。
 * <p>
 * 使用方（ECCharms、ECWindGear、ECWindMage、ECBaubles）是第三批模块，框架测试不 import 它们：要它们的物品时按注册名从 ForgeRegistries 取，
 * 取不到（模块还没接线）就跳过那部分断言，只测框架自己的逻辑（porting_guide §6）。
 * 模拟玩家（{@code makeMockPlayer}）不在世界里 tick，所以 Curios 不会自己发 CurioChangeEvent，测试里手动发。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class RegistryFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final double EPSILON = 1.0E-5D;

    private RegistryFrameworkTests() {
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < EPSILON;
    }

    /** 按注册名取别的模块的物品，没注册时返回空 */
    private static Optional<Item> optionalItem(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.ITEMS.containsKey(id) ? Optional.ofNullable(ForgeRegistries.ITEMS.getValue(id)) : Optional.empty();
    }

    private static ICuriosItemHandler curios(GameTestHelper helper, Player player) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            helper.fail("模拟玩家没有 Curios 饰品栏");
        }
        return inventory.get();
    }

    private static void put(GameTestHelper helper, ICuriosItemHandler inventory, String slot, ItemStack stack) {
        ICurioStacksHandler handler = inventory.getCurios().get(slot);
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 槽位 " + slot + "（data/essentialcraft5/curios/entities/essentialcraft5.json）");
            return;
        }
        handler.getStacks().setStackInSlot(0, stack);
    }

    // ---------------------------------------------------------------- G1 / G2 实体、效果、音效的资源齐全

    /** 读本模组 jar 里的一个 JSON（开发环境里是 build/resources/main，含 datagen 生成的 src/generated/resources） */
    private static Optional<JsonObject> modJson(String... path) {
        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource(path);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("读不了 " + file, e);
        }
    }

    private static boolean modFileExists(String... path) {
        return Files.exists(ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource(path));
    }

    /**
     * 本命名空间注册了的实体都有中英文名字、活体都登记了属性；药水效果都有中英文名字和图标；
     * 音效在合并后的 sounds.json 里都有定义、定义引用的 .ogg 都在。模块用 ECEntities / ECEffects / ECSounds 加东西时漏了资源，这条会失败。
     */
    @GameTest(template = EMPTY)
    public static void registeredContentHasAssets(GameTestHelper helper) {
        Optional<JsonObject> en = modJson("assets", EssentialCraft5.MODID, "lang", "en_us.json");
        Optional<JsonObject> zh = modJson("assets", EssentialCraft5.MODID, "lang", "zh_cn.json");
        Optional<JsonObject> sounds = modJson("assets", EssentialCraft5.MODID, "sounds.json");
        if (en.isEmpty() || zh.isEmpty() || sounds.isEmpty()) {
            helper.fail("找不到生成的语言文件或 sounds.json，先跑 runData");
            return;
        }
        List<String> problems = new ArrayList<>();
        ServerLevel level = helper.getLevel();

        for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(EssentialCraft5.MODID)) {
                continue;
            }
            EntityType<?> type = entry.getValue();
            String key = type.getDescriptionId();
            if (!en.get().has(key) || !zh.get().has(key)) {
                problems.add("实体 " + id + " 缺名字 " + key);
            }
            Entity entity = type.create(level);
            if (entity instanceof LivingEntity && !DefaultAttributes.hasSupplier(type)) {
                problems.add("活体实体 " + id + " 没登记属性（ECEntities.attributes）");
            }
            if (entity != null) {
                entity.discard();
            }
        }

        for (Map.Entry<ResourceKey<MobEffect>, MobEffect> entry : ForgeRegistries.MOB_EFFECTS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(EssentialCraft5.MODID)) {
                continue;
            }
            String key = entry.getValue().getDescriptionId();
            if (!en.get().has(key) || !zh.get().has(key)) {
                problems.add("效果 " + id + " 缺名字 " + key);
            }
            if (!modFileExists("assets", EssentialCraft5.MODID, "textures", "mob_effect", id.getPath() + ".png")) {
                problems.add("效果 " + id + " 缺图标 textures/mob_effect/" + id.getPath() + ".png");
            }
        }

        for (Map.Entry<ResourceKey<SoundEvent>, SoundEvent> entry : ForgeRegistries.SOUND_EVENTS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(EssentialCraft5.MODID)) {
                continue;
            }
            JsonElement definition = sounds.get().get(id.getPath());
            if (definition == null) {
                problems.add("音效 " + id + " 在 sounds.json 里没有定义（tools/sounds/<模块>.json）");
                continue;
            }
            for (JsonElement sound : definition.getAsJsonObject().getAsJsonArray("sounds")) {
                String name;
                boolean isEvent = false;
                if (sound.isJsonObject()) {
                    JsonObject object = sound.getAsJsonObject();
                    name = object.get("name").getAsString();
                    isEvent = object.has("type") && "event".equals(object.get("type").getAsString());
                } else {
                    name = sound.getAsString();
                }
                ResourceLocation file = ResourceLocation.tryParse(name);
                if (isEvent || file == null || !file.getNamespace().equals(EssentialCraft5.MODID)) {
                    continue;
                }
                if (!modFileExists("assets", EssentialCraft5.MODID, "sounds", file.getPath() + ".ogg")) {
                    problems.add("音效 " + id + " 引用的 sounds/" + file.getPath() + ".ogg 不存在");
                }
            }
        }

        helper.assertTrue(problems.isEmpty(), "资源不齐：" + problems);
        helper.succeed();
    }

    /** notInBiomes：所在群系在排除表里时不刷，否则交给原判定 */
    @GameTest(template = EMPTY)
    public static void spawnPredicateExcludesBiomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Optional<ResourceKey<Biome>> here = level.getBiome(pos).unwrapKey();
        helper.assertTrue(here.isPresent(), "测试位置应有群系");
        SpawnPlacements.SpawnPredicate<Zombie> always = (type, world, reason, at, random) -> true;
        SpawnPlacements.SpawnPredicate<Zombie> never = (type, world, reason, at, random) -> false;
        ResourceKey<Biome> other = here.get() == Biomes.MUSHROOM_FIELDS ? Biomes.DEEP_DARK : Biomes.MUSHROOM_FIELDS;

        helper.assertTrue(ECEntities.notInBiomes(always, other).test(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, pos, level.getRandom()),
                "不在排除的群系里时应交给原判定（放行）");
        helper.assertFalse(ECEntities.notInBiomes(always, other, here.get()).test(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, pos, level.getRandom()),
                "所在群系 " + here.get().location() + " 被排除时不应刷");
        helper.assertFalse(ECEntities.notInBiomes(never, other).test(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, pos, level.getRandom()),
                "原判定不通过时不应刷");
        helper.succeed();
    }

    /** ECWindMage 接线后：风法师刷怪蛋是框架的 SpawnEgg，右键刷怪笼不改刷怪笼的类型，而是在旁边放出一只 */
    @GameTest(template = EMPTY_LARGE)
    public static void spawnEggDoesNotChangeSpawner(GameTestHelper helper) {
        Optional<Item> egg = optionalItem("wind_mage_spawn_egg");
        if (egg.isEmpty()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(egg.get() instanceof ECEntities.SpawnEgg spawnEgg && !spawnEgg.changesSpawner(),
                "风法师刷怪蛋应是 ECEntities.SpawnEgg、默认不改刷怪笼，实际 " + egg.get().getClass().getName());
        ServerLevel level = helper.getLevel();
        BlockPos spawnerPos = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlockAndUpdate(spawnerPos, Blocks.SPAWNER.defaultBlockState());
        if (!(level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner)) {
            helper.fail("刷怪笼没有方块实体");
            return;
        }
        String before = spawner.getSpawner().save(new CompoundTag()).toString();
        Player player = helper.makeMockPlayer();
        ItemStack stack = new ItemStack(egg.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(spawnerPos).relative(Direction.UP, 0.5D), Direction.UP, spawnerPos, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        String after = spawner.getSpawner().save(new CompoundTag()).toString();
        helper.assertTrue(before.equals(after), "刷怪笼的内容不应被刷怪蛋改掉：" + before + " → " + after);
        AABB around = new AABB(spawnerPos).inflate(3D);
        List<Entity> spawned = level.getEntities((Entity) null, around, entity -> EssentialCraft5.MODID.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).getNamespace()));
        helper.assertFalse(spawned.isEmpty(), "应在刷怪笼旁边放出一只");
        spawned.forEach(Entity::discard);
        helper.succeed();
    }

    // ---------------------------------------------------------------- G3 Curios

    /** 玩家开了戒指、项链、腰带三种槽 */
    @GameTest(template = EMPTY)
    public static void curiosSlotsOpened(GameTestHelper helper) {
        Map<String, ?> slots = CuriosApi.getEntitySlots(EntityType.PLAYER, helper.getLevel());
        for (String slot : List.of(CuriosCompat.RING, CuriosCompat.NECKLACE, CuriosCompat.BELT)) {
            helper.assertTrue(slots.containsKey(slot), "玩家应有 Curios 槽位 " + slot + "，实际 " + slots.keySet());
        }
        helper.succeed();
    }

    /** 缓存：查过一次后槽位被悄悄改了，结果不变；发 CurioChangeEvent 后作废、重新读到新内容；按物品和按标签都能查 */
    @GameTest(template = EMPTY)
    public static void curiosCacheInvalidatesOnChange(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ICuriosItemHandler inventory = curios(helper, player);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        put(helper, inventory, CuriosCompat.NECKLACE, diamond);
        helper.assertTrue(CuriosCompat.has(player, Items.DIAMOND), "项链槽里的钻石应查得到");
        helper.assertTrue(CuriosCompat.find(player, Items.DIAMOND) == diamond, "find 应返回槽里的栈本身");
        helper.assertTrue(CuriosCompat.equipped(player).size() == 1, "应只有一件");

        // 悄悄换掉（不发事件）：缓存还是旧的
        put(helper, inventory, CuriosCompat.NECKLACE, ItemStack.EMPTY);
        put(helper, inventory, CuriosCompat.BELT, new ItemStack(Items.EMERALD));
        helper.assertTrue(CuriosCompat.has(player, Items.DIAMOND), "没发事件时应还是缓存里的结果（说明走了缓存）");

        MinecraftForge.EVENT_BUS.post(new CurioChangeEvent(player, CuriosCompat.NECKLACE, 0, diamond, ItemStack.EMPTY));
        helper.assertFalse(CuriosCompat.has(player, Items.DIAMOND), "CurioChangeEvent 后应读到钻石已经摘下");
        helper.assertTrue(CuriosCompat.has(player, Items.EMERALD), "应读到腰带槽里的绿宝石");
        helper.assertTrue(CuriosCompat.has(player, Tags.Items.GEMS_EMERALD), "按标签应查得到绿宝石");
        helper.assertFalse(CuriosCompat.has(player, Tags.Items.GEMS_DIAMOND), "按标签不应查到钻石");

        // 手动作废也行
        put(helper, inventory, CuriosCompat.BELT, ItemStack.EMPTY);
        CuriosCompat.invalidate(player);
        helper.assertTrue(CuriosCompat.equipped(player).isEmpty(), "invalidate 后应读到全空");
        helper.succeed();
    }

    /** 漏掉事件时（别的模组绕过 Curios 改槽位），最多 MAX_CACHE_AGE tick 后自己重建 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void curiosCacheExpires(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ICuriosItemHandler inventory = curios(helper, player);
        put(helper, inventory, CuriosCompat.RING, new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(CuriosCompat.has(player, Items.GOLD_INGOT), "戒指槽里的金锭应查得到");
        put(helper, inventory, CuriosCompat.RING, ItemStack.EMPTY);
        helper.runAfterDelay(CuriosCompat.MAX_CACHE_AGE + 1, () -> {
            helper.assertFalse(CuriosCompat.has(player, Items.GOLD_INGOT), "超过 " + CuriosCompat.MAX_CACHE_AGE + " tick 后缓存应自己重建");
            helper.succeed();
        });
    }

    /** onlyOneEquipped：别的槽里已经有同一种就拒；自己这一格、装饰槽、不同的物品放行 */
    @GameTest(template = EMPTY)
    public static void curiosOnlyOneOfKind(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ICuriosItemHandler inventory = curios(helper, player);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        put(helper, inventory, CuriosCompat.RING, diamond);

        SlotContext necklace = new SlotContext(CuriosCompat.NECKLACE, player, 0, false, true);
        SlotContext sameSlot = new SlotContext(CuriosCompat.RING, player, 0, false, true);
        SlotContext cosmetic = new SlotContext(CuriosCompat.NECKLACE, player, 0, true, true);
        helper.assertFalse(CuriosCompat.onlyOneEquipped(necklace, new ItemStack(Items.DIAMOND)), "戒指槽已有钻石时，项链槽应拒第二颗");
        helper.assertTrue(CuriosCompat.onlyOneEquipped(sameSlot, new ItemStack(Items.DIAMOND)), "换同一格里的那件应放行");
        helper.assertTrue(CuriosCompat.onlyOneEquipped(cosmetic, new ItemStack(Items.DIAMOND)), "装饰槽应放行");
        helper.assertTrue(CuriosCompat.onlyOneEquipped(necklace, new ItemStack(Items.EMERALD)), "不同的物品应放行");
        helper.assertFalse(CuriosCompat.onlyOneEquipped(necklace, stack -> stack.is(Tags.Items.GEMS)),
                "按标签判「同一种」时，戒指槽的钻石也算宝石，应拒");
        helper.succeed();
    }

    /** 配置 general.baubles.disabled：列进去的物品戴着查不到、not_disabled 条件不成立；还原后恢复 */
    @GameTest(template = EMPTY)
    public static void disabledBaublesConfig(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ICuriosItemHandler inventory = curios(helper, player);
        put(helper, inventory, CuriosCompat.NECKLACE, new ItemStack(Items.DIAMOND));
        LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY)).create(null);
        NotDisabledCondition condition = new NotDisabledCondition(ResourceLocation.withDefaultNamespace("diamond"));
        helper.assertTrue(CuriosCompat.has(player, Items.DIAMOND) && condition.test(context), "默认没有禁用任何物品");

        // 用内存覆盖值，不改真配置（见 CuriosCompat.Config.setDisabledForTest）
        CuriosCompat.Config.setDisabledForTest(List.of("minecraft:diamond"));
        try {
            helper.assertTrue(CuriosCompat.isDisabled(new ItemStack(Items.DIAMOND)), "钻石应被禁用");
            helper.assertFalse(CuriosCompat.has(player, Items.DIAMOND), "被禁用的饰品戴着应查不到（缓存随配置作废）");
            helper.assertFalse(condition.test(context), "not_disabled 条件应不成立");
            helper.assertTrue(new NotDisabledCondition(ResourceLocation.withDefaultNamespace("emerald")).test(context), "没禁用的应成立");
        } finally {
            CuriosCompat.Config.setDisabledForTest(null);
        }
        helper.assertTrue(CuriosCompat.has(player, Items.DIAMOND), "还原配置后应重新查得到");
        helper.succeed();
    }

    // ---------------------------------------------------------------- G4 地牢注入

    /** simple_dungeon 里有注入池、被引用的表存在；not_disabled 条件已注册，JSON 格式能解析 */
    @GameTest(template = EMPTY)
    public static void dungeonLootInjected(GameTestHelper helper) {
        var lootData = helper.getLevel().getServer().getLootData();
        LootTable dungeon = lootData.getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        helper.assertTrue(dungeon != LootTable.EMPTY, "原版地牢箱子的表应存在");
        helper.assertTrue(dungeon.getPool(DungeonLootInjection.POOL_NAME) != null, "地牢箱子的表里应有注入池 " + DungeonLootInjection.POOL_NAME);
        helper.assertTrue(lootData.getLootTable(DungeonLootInjection.SIMPLE_DUNGEON) != LootTable.EMPTY,
                "被引用的 " + DungeonLootInjection.SIMPLE_DUNGEON + " 应存在（data/essentialcraft5/loot_tables/inject/simple_dungeon.json）");

        helper.assertTrue(BuiltInRegistries.LOOT_CONDITION_TYPE.get(NotDisabledCondition.ID) == NotDisabledCondition.TYPE,
                "战利品条件 not_disabled 应已注册：get=" + BuiltInRegistries.LOOT_CONDITION_TYPE.get(NotDisabledCondition.ID)
                        + " key=" + BuiltInRegistries.LOOT_CONDITION_TYPE.getKey(NotDisabledCondition.TYPE)
                        + " has=" + BuiltInRegistries.LOOT_CONDITION_TYPE.containsKey(NotDisabledCondition.ID));
        Gson gson = Deserializers.createConditionSerializer().create();
        LootItemCondition parsed = gson.fromJson(JsonParser.parseString(
                "{\"condition\": \"essentialcraft5:not_disabled\", \"item\": \"essentialcraft5:zeta_amulet\"}"), LootItemCondition.class);
        helper.assertTrue(parsed instanceof NotDisabledCondition notDisabled && notDisabled.item().equals(EssentialCraft5.id("zeta_amulet")),
                "条件 JSON 应解析成 NotDisabledCondition，实际 " + parsed);
        helper.succeed();
    }

    // ---------------------------------------------------------------- G5 网络

    /** 开关包只带一个布尔、编解码对称；包要在构造阶段登记，之后登记会报错 */
    @GameTest(template = EMPTY)
    public static void wingsPacketCodec(GameTestHelper helper) {
        for (boolean value : new boolean[]{true, false}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            C2SWingsPacket.encode(new C2SWingsPacket(value), buf);
            helper.assertTrue(buf.readableBytes() == 1, "开关包应只有 1 字节，实际 " + buf.readableBytes());
            helper.assertTrue(C2SWingsPacket.decode(buf).active() == value, "解码应得到 " + value);
            buf.release();
        }
        boolean threw = false;
        try {
            ECNetwork.registerC2S(String.class, (s, b) -> { }, b -> "", (s, p) -> { });
        } catch (IllegalStateException e) {
            threw = true;
        }
        helper.assertTrue(threw, "通道注册完以后再登记包应报错");
        helper.succeed();
    }

    /** 没戴翅膀发「开」被忽略；ECCharms 接线后：戴在腰带槽里才记下，「关」和 stop 都清掉 */
    @GameTest(template = EMPTY)
    public static void wingsToggleRequiresWings(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        WingsFlight.handleToggle(player, true);
        helper.assertFalse(WingsFlight.isActive(player), "没戴翅膀时「开」应被忽略");

        Optional<Item> wings = optionalItem(WingsFlight.WINGS_PATH);
        if (wings.isEmpty()) {
            helper.succeed();
            return;
        }
        ICuriosItemHandler inventory = curios(helper, player);
        put(helper, inventory, CuriosCompat.BELT, new ItemStack(wings.get()));
        CuriosCompat.invalidate(player);
        helper.assertTrue(WingsFlight.isWearingWings(player), "腰带槽里的魔力之翼应算戴着");
        WingsFlight.handleToggle(player, true);
        helper.assertTrue(WingsFlight.isActive(player), "戴着翅膀时「开」应记下");
        WingsFlight.handleToggle(player, false);
        helper.assertFalse(WingsFlight.isActive(player), "「关」应清掉");
        WingsFlight.handleToggle(player, true);
        WingsFlight.stop(player);
        helper.assertFalse(WingsFlight.isActive(player), "stop 应清掉");
        helper.succeed();
    }

    // ---------------------------------------------------------------- G6 盔甲

    /** 登高修饰符：+0.6 → 玩家登高 1.2；同属性两条合并成一条；UUID 按部位固定；摘下恢复 */
    @GameTest(template = EMPTY)
    public static void armorBonusStepHeight(GameTestHelper helper) {
        ArmorBonus bonus = ArmorBonus.of()
                .add(ForgeMod.STEP_HEIGHT_ADDITION, 0.4D, AttributeModifier.Operation.ADDITION)
                .add(ForgeMod.STEP_HEIGHT_ADDITION, 0.2D, AttributeModifier.Operation.ADDITION);
        Multimap<Attribute, AttributeModifier> boots = bonus.modifiers(ArmorItem.Type.BOOTS);
        Attribute step = ForgeMod.STEP_HEIGHT_ADDITION.get();
        helper.assertTrue(boots.get(step).size() == 1, "同属性同运算应合并成一条，实际 " + boots);
        AttributeModifier modifier = boots.get(step).iterator().next();
        helper.assertTrue(near(modifier.getAmount(), 0.6D), "合并后应为 0.6，实际 " + modifier.getAmount());
        helper.assertTrue(modifier.getId().equals(bonus.modifiers(ArmorItem.Type.BOOTS).get(step).iterator().next().getId()),
                "同一部位的 UUID 应固定");
        helper.assertFalse(modifier.getId().equals(bonus.modifiers(ArmorItem.Type.HELMET).get(step).iterator().next().getId()),
                "不同部位的 UUID 应不同（四件的同种加成能叠加）");

        Player player = helper.makeMockPlayer();
        float base = player.getStepHeight();
        player.getAttributes().addTransientAttributeModifiers(boots);
        helper.assertTrue(near(player.getStepHeight(), base + 0.6D), "穿上后登高应为 " + (base + 0.6D) + "，实际 " + player.getStepHeight());
        player.getAttributes().removeAttributeModifiers(boots);
        helper.assertTrue(near(player.getStepHeight(), base), "摘下后应恢复");
        helper.succeed();
    }

    /** 材料按部位取值、贴图按部位分、染色默认白色；ECWindGear / ECWindMage 接线后查它们用的是框架的类 */
    @GameTest(template = EMPTY)
    public static void armorFrameworkBasics(GameTestHelper helper) {
        ECArmorMaterial material = ECArmorMaterial.builder("test").durability(1, 2, 3, 4).defense(5, 6, 7, 8).enchantability(9).build();
        helper.assertTrue(material.getDurabilityForType(ArmorItem.Type.HELMET) == 1 && material.getDurabilityForType(ArmorItem.Type.BOOTS) == 4
                        && material.getDefenseForType(ArmorItem.Type.CHESTPLATE) == 6 && material.getDefenseForType(ArmorItem.Type.LEGGINGS) == 7,
                "材料应按 头 / 胸 / 腿 / 靴 取值");
        helper.assertTrue(material.getName().equals("essentialcraft5:test"), "材料名应带命名空间");

        ArmorLook look = ArmorLook.vanilla("special/armor/wind_layer_1", "special/armor/wind_layer_2");
        helper.assertTrue(look.texture(EquipmentSlot.LEGS).getPath().equals("textures/special/armor/wind_layer_2.png")
                        && look.texture(EquipmentSlot.HEAD).getPath().equals("textures/special/armor/wind_layer_1.png") && look.model() == null,
                "原版形状：护腿用第二张贴图、其余第一张、没有自定义模型");
        helper.assertTrue(ArmorLook.modelArmorEC("special/armor/armorec3wind_0", "special/armor/armorec3wind_1").model() == ArmorLook.MODEL_ARMOR_EC,
                "ModelArmorEC 形状应带模型 id");

        ItemStack undyed = new ItemStack(Items.LEATHER_HELMET);
        helper.assertTrue(DyeableECArmorItem.colorOf(undyed) == DyeableECArmorItem.DEFAULT_COLOR, "没染过色应是白色（原作默认）");
        undyed.getOrCreateTagElement("display").putInt("color", 0x123456);
        helper.assertTrue(DyeableECArmorItem.colorOf(undyed) == 0x123456, "染过色应读 display.color");

        for (String id : List.of("wind_helmet", "wind_chestplate", "wind_leggings", "wind_boots")) {
            Optional<Item> item = optionalItem(id);
            if (item.isPresent()) {
                helper.assertTrue(item.get() instanceof DyeableECArmorItem, id + " 应是 DyeableECArmorItem（可染色）");
                helper.assertTrue(CauldronInteraction.WATER.get(item.get()) == CauldronInteraction.DYED_ITEM, id + " 应能在炼药锅里洗掉颜色");
            }
        }
        for (String id : List.of("wind_mage_hood", "wind_mage_robe", "wind_mage_leggings", "wind_mage_boots")) {
            Optional<Item> item = optionalItem(id);
            if (item.isPresent()) {
                helper.assertTrue(item.get() instanceof ECArmorItem armor && ArmorLook.MODEL_ARMOR_EC.equals(armor.look().model()),
                        id + " 应是 ECArmorItem、用 ModelArmorEC 模型（原作 ItemArmorEC）");
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- G7 风之 Tier

    /** 风排在元素之后（原作挖掘等级 11 > 6），能挖黑曜石；材料数值照原作 windElemental */
    @GameTest(template = EMPTY)
    public static void windTierSorted(GameTestHelper helper) {
        List<Tier> sorted = TierSortingRegistry.getSortedTiers();
        int netherite = sorted.indexOf(Tiers.NETHERITE);
        int elemental = sorted.indexOf(ElementalTier.ELEMENTAL);
        int wind = sorted.indexOf(WindTier.WIND);
        helper.assertTrue(netherite >= 0 && elemental > netherite && wind > elemental, "排序应为 下界合金 < 元素 < 风，实际 " + sorted);
        helper.assertTrue(TierSortingRegistry.byName(EssentialCraft5.id("wind")) == WindTier.WIND, "风之 Tier 应以 essentialcraft5:wind 登记");
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        helper.assertTrue(TierSortingRegistry.isCorrectTierForDrops(WindTier.WIND, obsidian), "风之 Tier 应能挖黑曜石");
        helper.assertTrue(WindTier.WIND.getTag() == ECTags.NEEDS_WIND_TOOL, "风之 Tier 的方块标签应是 needs_wind_tool");
        helper.assertTrue(WindTier.WIND.getUses() == 15684 && WindTier.WIND.getSpeed() == 15F
                        && WindTier.WIND.getAttackDamageBonus() == 6F && WindTier.WIND.getEnchantmentValue() == 42,
                "数值应照原作 windElemental（15684 / 15 / 6 / 42）");
        helper.succeed();
    }
}
