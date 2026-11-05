package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 公共实体注册入口（第三批框架 G1）。实体类型登记进 {@link ECRegistries#ENTITY_TYPES}，属性、刷怪位置、刷怪蛋也在这里登记，
 * 模块只在自己的注册类里写静态字段，不用自己监听 {@link EntityAttributeCreationEvent} / {@link SpawnPlacementRegisterEvent}。
 * <p>
 * 用法（ECWindMage 为例，写在 {@code registry/ECWindMage}）：
 * <pre>
 * // 原作 EntitiesCore.registerEntity(EntityWindMage.class, 64, 1, true)：追踪 64 格 → clientTrackingRange(4)（单位是区块）、每 tick 同步 → updateInterval(1)
 * public static final RegistryObject&lt;EntityType&lt;WindMageEntity&gt;&gt; WIND_MAGE = ECEntities.register("wind_mage",
 *         EntityType.Builder.of(WindMageEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(4).updateInterval(1));
 * public static final RegistryObject&lt;ECEntities.SpawnEgg&gt; WIND_MAGE_SPAWN_EGG =
 *         ECEntities.spawnEgg("wind_mage_spawn_egg", WIND_MAGE, 0x…, 0x…);
 *
 * public static void init() {
 *     ECEntities.attributes(WIND_MAGE, WindMageEntity::createAttributes);   // Mob.createMobAttributes().add(…)
 *     ECEntities.spawnPlacement(WIND_MAGE, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
 *             ECEntities.notInBiomes(Monster::checkMonsterSpawnRules, Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK));
 * }
 * </pre>
 * <ul>
 *   <li>{@link #attributes} / {@link #spawnPlacement} 必须在模组构造阶段调（注册类的静态字段或 init() 里），两个事件在注册表事件之后才发。
 *       活体实体<b>必须</b>登记属性，否则一生成就崩。</li>
 *   <li>自然生成本身（生物群系、权重、每群数量）是数据：{@code data/essentialcraft5/forge/biome_modifier/*.json}（{@code forge:add_spawns}），
 *       不在这里。{@link #spawnPlacement} 只管「这一格能不能刷」，刷怪蛋、/summon 不查它；刷怪笼（{@code MobSpawnType.SPAWNER}）
 *       没写自定义规则时照原版也要过它（刷怪笼放在怪刷不出来的地方就一只不出）。</li>
 *   <li>实体渲染器由模块的客户端类在 {@code EntityRenderersEvent.RegisterRenderers} 里注册；实体名字键 {@code entity.essentialcraft5.<注册名>}
 *       写进模块自己的 tools/lang/keys（RegistryFrameworkTests 会查注册了的实体都有中英文名字）。</li>
 *   <li>刷怪蛋：{@link #spawnEgg} 用原版刷怪蛋模板模型，两层颜色由 Forge 的 {@link ForgeSpawnEggItem} 自动注册，
 *       同时放进本模组「物品」页和原版「刷怪蛋」页；默认<b>点刷怪笼不改类型</b>（见 {@link SpawnEgg}）。</li>
 * </ul>
 * ECMRUCU 的 mru_presence 仍在 ECMRUCU 里用 RegisterEvent 注册（注册名不变，不迁移）；聚集体不是 Mob，也不用这里的刷怪蛋。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECEntities {

    private static final List<Consumer<EntityAttributeCreationEvent>> ATTRIBUTES = new ArrayList<>();
    private static final List<Consumer<SpawnPlacementRegisterEvent>> PLACEMENTS = new ArrayList<>();
    private static final List<RegistryObject<SpawnEgg>> SPAWN_EGGS = new ArrayList<>();

    private ECEntities() {
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    static void init() {
    }

    /**
     * 登记一种实体类型。builder 在注册表事件里才 build（名字按 {@code essentialcraft5:<name>} 给数据修复器用）。
     * 原作 {@code registerEntity(cls, trackingRange, tickDelay, trackRotation)} 的换算：
     * {@code clientTrackingRange(trackingRange / 16)}（格 → 区块）、{@code updateInterval(tickDelay)}。
     */
    public static <T extends Entity> RegistryObject<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        Objects.requireNonNull(builder, "builder");
        String id = EssentialCraft5.id(name).toString();
        return ECRegistries.ENTITY_TYPES.register(name, () -> builder.build(id));
    }

    /**
     * 登记活体实体的属性（{@link EntityAttributeCreationEvent}）。attributes 通常是实体类的静态方法
     * {@code Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20)}，在事件里才调用。
     */
    public static void attributes(Supplier<? extends EntityType<? extends LivingEntity>> type,
                                  Supplier<AttributeSupplier.Builder> attributes) {
        ATTRIBUTES.add(event -> event.put(type.get(), attributes.get().build()));
    }

    /**
     * 登记自然生成时「这一格能不能刷」的判定（{@link SpawnPlacementRegisterEvent}，本模组自己的实体用 REPLACE）。
     * 刷怪表本身写在 forge:add_spawns 数据里。
     */
    public static <T extends Mob> void spawnPlacement(Supplier<EntityType<T>> type, SpawnPlacements.Type placement,
                                                      Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
        PLACEMENTS.add(event -> event.register(type.get(), placement, heightmap, predicate,
                SpawnPlacementRegisterEvent.Operation.REPLACE));
    }

    /**
     * 在 base 的基础上排除几个生物群系（按刷的那一格的群系判断）。风法师用它排除蘑菇岛和深暗之域（policy §4.2）：
     * forge:add_spawns 写 {@code #minecraft:is_overworld} 会把这两个群系也算进去；原作 biomesToSpawn 只挑「有怪物刷怪表」的群系，
     * 而这两个群系在原版里都不刷怪物，所以照原作的意思保持不刷。
     */
    @SafeVarargs
    public static <T extends Mob> SpawnPlacements.SpawnPredicate<T> notInBiomes(SpawnPlacements.SpawnPredicate<T> base,
                                                                              ResourceKey<Biome>... excluded) {
        List<ResourceKey<Biome>> keys = List.of(excluded);
        return (type, level, spawnType, pos, random) -> {
            Holder<Biome> biome = level.getBiome(pos);
            for (ResourceKey<Biome> key : keys) {
                if (biome.is(key)) {
                    return false;
                }
            }
            return base.test(type, level, spawnType, pos, random);
        };
    }

    /**
     * 登记刷怪蛋（{@link SpawnEgg}，点刷怪笼不改类型）。颜色：background = 蛋壳底色，highlight = 斑点色。
     * 注册名要和命名表 tools/id_map_items.json 对得上（风法师的是 wind_mage_spawn_egg）。
     */
    public static RegistryObject<SpawnEgg> spawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type,
                                                    int background, int highlight) {
        return spawnEgg(name, type, background, highlight, false);
    }

    /** 同上；{@code changesSpawner = true} 时和原版一样，右键刷怪笼把它改成这种实体 */
    public static RegistryObject<SpawnEgg> spawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type,
                                                    int background, int highlight, boolean changesSpawner) {
        RegistryObject<SpawnEgg> egg = ECItems.register(name,
                () -> new SpawnEgg(type, background, highlight, new Item.Properties(), changesSpawner),
                ItemSpec.of((provider, item) -> provider.withExistingParent(provider.name(item), provider.mcLoc("item/template_spawn_egg"))));
        SPAWN_EGGS.add(egg);
        return egg;
    }

    @SubscribeEvent
    public static void createAttributes(EntityAttributeCreationEvent event) {
        ATTRIBUTES.forEach(action -> action.accept(event));
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        PLACEMENTS.forEach(action -> action.accept(event));
    }

    /** 刷怪蛋另外放进原版的「刷怪蛋」页（本模组「物品」页由 ECCreativeTabs 按 ECItems 的登记自动放） */
    @SubscribeEvent
    public static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            SPAWN_EGGS.forEach(egg -> event.accept(egg.get()));
        }
    }

    /**
     * 本模组的刷怪蛋。和原版的区别只有一条：默认右键刷怪笼<b>不</b>改刷怪笼的类型，而是像点普通方块一样在旁边放出一只
     * （原版任何人都能拿蛋改刷怪笼；本模组的蛋只有创造模式拿得到，但为防刷怪塔默认不开这个口子）。
     * 发射器、在水里右键、给幼年生物用等行为全部照原版 {@link ForgeSpawnEggItem}。
     */
    public static class SpawnEgg extends ForgeSpawnEggItem {

        private final boolean changesSpawner;

        public SpawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor,
                        Item.Properties properties, boolean changesSpawner) {
            super(type, backgroundColor, highlightColor, properties);
            this.changesSpawner = changesSpawner;
        }

        public boolean changesSpawner() {
            return changesSpawner;
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            if (changesSpawner || !(context.getLevel() instanceof ServerLevel level)
                    || !(level.getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity)) {
                return super.useOn(context);
            }
            // 同原版 SpawnEggItem.useOn 的非刷怪笼分支：刷怪笼有碰撞箱，放在点到的那一面外侧
            ItemStack stack = context.getItemInHand();
            BlockPos clicked = context.getClickedPos();
            BlockState state = level.getBlockState(clicked);
            BlockPos pos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
            EntityType<?> type = getType(stack.getTag());
            if (type.spawn(level, stack, context.getPlayer(), pos, MobSpawnType.SPAWN_EGG, true,
                    !Objects.equals(clicked, pos) && context.getClickedFace() == Direction.UP) != null) {
                stack.shrink(1);
                level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clicked);
            }
            return InteractionResult.CONSUME;
        }
    }
}
