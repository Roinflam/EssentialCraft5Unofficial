package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.LevitatorBlock;
import com.flameforge.essentialcraft5.block.SpreaderBlock;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 第六批 6a M7「虚空材料与海纳尔遗物」（计划 tools/plan/batch6a.md §5.7）：海纳尔遗迹（城镇废墟塔顶、古代墓穴出口房）箱子里的东西，
 * 以及城镇废墟用到的几种方块。原作里它们大多没有配方、只从遗迹里来（虚空石 / 玻璃 / 栅栏能合成，虚空板能在魔术师台上压；混凝土、悬浮器、散射器只能从城镇里挖）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>titanite / twinkling_titanite → titanite / twinkling_titanite；genItem:35 / 36 / 37 → void_plating / void_core / void_mru_reactor
 *       （魔术师台升级 ×6 / ×7.5 / ×10 早在 {@code api/MagicianTableUpgrades} 里按注册名登记好了，注册即生效；
 *       虚空板同时是枪身与 12 倍狙击镜材料，{@code gun/GunMaterials} 按「物品已注册」自动开放）。</li>
 *   <li>ember:0~7 → acidic / chaos / common / corrupted / crystal / divine / magical / flame_ember（原作一组 16 个，照搬；
 *       进物品标签 {@link #EMBERS}，第七批余烬锻炉和 ε 腰带用）。原作 meta 8「unknown」是占位（不在创造栏、没有模型），不移植。</li>
 *   <li>bauble（ItemBaublesResistance）→ mystical_accessory（{@link MysticalAccessoryItem}：开箱时定属性，见该类）。</li>
 *   <li>voidStone / voidGlass（BlockDyeable）/ voidFence（BlockFenceEC）→ void_stone / void_glass / void_fence；concrete → concrete；
 *       levitator / spreader（BlockMRULevitator / BlockMRUSpreader）→ levitator / spreader。</li>
 *   <li>花纹方块 fancyBlock.concrete / fancyBlock.voidStone（meta 0~15）→ {@code fancy_concrete_<花纹>} / {@code fancy_void_stone_<花纹>}：
 *       底材表 {@code block/FancyMaterial} 加两行，由 ECDecorations 注册（配方 tools/gen/fancy_recipes.py）。</li>
 * </ul>
 * 其它文件：配方 data/essentialcraft5/recipes/void/（虚空石 / 玻璃 / 栅栏、魔术师台做虚空板；原作两条配方的矿辞 genTitanite 是笔误、原作做不出来，
 * 这里直接引用物品）；两张箱子表 loot_tables/chests/{town_tower,catacombs}.json（tools/gen/hoanna_loot.py 生成，id 在 {@code loot/HoannaLoot}）；
 * 战利品函数 {@code essentialcraft5:randomize_accessory}（{@code loot/RandomizeAccessoryFunction}）；地脉投影仪的海纳尔表（tools/gen/ley_projector_tables.py）；
 * 文字 tools/lang/keys/ecvoid.json；标签 tools/tags/ecvoid.json（虚空石进特斯拉线圈底座 {@code mru_coil_base}，虚空石 / 玻璃进富集仓外壳 {@code mrucuec_casing}）；
 * 进度 advancements/guide/hoanna/{relics,void_stone,void_plating}.json；客户端 {@code client/VoidClient}（神秘饰品模型属性、悬浮器 / 散射器粒子）；
 * MRU 抗性（虚空石 18、虚空玻璃 15）早在 {@code entity/MRUResistances} 的默认表里，注册即生效。
 * <p>
 * ε 腰带（{@link ECBaubles#DROPS_EMBERS}）的效果在 {@link VoidEmberEvents}。本模块没有配置、指令、网络包。
 */
public final class ECVoid {

    /** 八种余烬（第七批余烬锻炉的材料、ε 腰带的掉落池）；不进任何 forge 共享标签 */
    public static final TagKey<Item> EMBERS = ECTags.modItem("embers");

    // ---------------------------------------------------------------- 材料（原作 ItemsCore）

    /** 原作 titanite：遗迹箱子里的主要材料（2~10 个），做虚空石 / 虚空玻璃 */
    public static final RegistryObject<Item> TITANITE = item("titanite", "item/misc/titanite");
    /** 原作 twinkling_titanite：虚空石 / 虚空玻璃配方正中那一格 */
    public static final RegistryObject<Item> TWINKLING_TITANITE = item("twinkling_titanite", "item/misc/twinkling_titanite");
    /** genItem:35（旧译「空白板」是误译）：魔术师台升级 ×6、虚空枪身、12 倍狙击镜；魔术师台中心放虚空石、1 000 MRU 做 */
    public static final RegistryObject<Item> VOID_PLATING = item("void_plating", "item/misc/voidplating");
    /** genItem:36（旧译「无效的核心」是误译）：魔术师台升级 ×7.5；只从遗迹箱子里来 */
    public static final RegistryObject<Item> VOID_CORE = item("void_core", "item/misc/voidcore");
    /** genItem:37（显示名 MRU Reactor，中文「MRU 反应核」免得和反应堆方块同名）：魔术师台升级 ×10；只从遗迹箱子里来 */
    public static final RegistryObject<Item> VOID_MRU_REACTOR = item("void_mru_reactor", "item/misc/voidmrureactor");

    // ---------------------------------------------------------------- 余烬（原作 ItemEmber，meta 0~7，一组 16 个）

    public static final RegistryObject<Item> ACIDIC_EMBER = ember("acidic");
    public static final RegistryObject<Item> CHAOS_EMBER = ember("chaos");
    public static final RegistryObject<Item> COMMON_EMBER = ember("common");
    public static final RegistryObject<Item> CORRUPTED_EMBER = ember("corrupted");
    public static final RegistryObject<Item> CRYSTAL_EMBER = ember("crystal");
    public static final RegistryObject<Item> DIVINE_EMBER = ember("divine");
    public static final RegistryObject<Item> MAGICAL_EMBER = ember("magical");
    public static final RegistryObject<Item> FLAME_EMBER = ember("flame");

    /** 原作一组 16 个 */
    public static final int EMBER_STACK = 16;

    // ---------------------------------------------------------------- 神秘饰品（原作 ItemBaublesResistance，改名）

    /** 原作 setMaxStackSize(1)；进三种 Curios 槽位标签，真正能戴进哪个槽由 NBT 里的形态决定（见 {@link MysticalAccessoryItem#canEquip}） */
    public static final RegistryObject<MysticalAccessoryItem> MYSTICAL_ACCESSORY = ECItems.register("mystical_accessory",
            () -> new MysticalAccessoryItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of(ECVoid::mysticalAccessoryModels).tags(ECTags.CURIOS_NECKLACE, ECTags.CURIOS_BELT, ECTags.CURIOS_RING));

    // ---------------------------------------------------------------- 方块

    /**
     * 原作 {@code new BlockDyeable(Material.ROCK, MapColor.BLACK).setHardness(8).setResistance(150)}（先硬度后抗爆）→ 硬度 8、抗爆 90；要镐。
     * 染料右键改色、潜行染 5 × 5 × 5（复用 {@link DyeableBlock}：连带的格子逐格发放置事件、只给真玩家），挖下来总是白色。
     * MRU 抗性 18（entity/MRUResistances 默认表）；特斯拉线圈底座、富集仓外壳（tools/tags/ecvoid.json）。
     * <p>
     * 模型两层（{@link ECMaterials#veinedDyeableModel}）：原贴图 voidstone.png 不染色，染料色只染上面那层纹路 voidstone_veins.png
     * （tools/gen/void_veins.py）。原作整张乘色，底图平均亮度只有 24 / 255，16 色乘出来 L* 都在 10 以下、十几对分不清。
     */
    public static final RegistryObject<DyeableBlock> VOID_STONE = ECBlocks.register("void_stone",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(8F, ECBlocks.legacyResistance(8F, 150F, false))
                    .requiresCorrectToolForDrops(), false),
            BlockSpec.of(ECMaterials.veinedDyeableModel("block/voidstone", "block/voidstone_veins", false)));

    /**
     * 原作 {@code new BlockDyeable(Material.GLASS, MapColor.BLACK).setHardness(8).setResistance(150)} → 硬度 8、抗爆 90。
     * Material.GLASS 不要求工具（空手也掉自己），属性照魔力强化玻璃（ECMaterials.FORTIFIED_GLASS）。原作镐也不加速：硬度 8 拿什么挖都要约 12 秒；
     * 这里进 mineable/pickaxe（BlockSpec 默认）让镐加速（钻石镐约 1.5 秒），但不加 requiresCorrectToolForDrops，空手照样掉自己。
     * 贴图 voidglass.png 的 alpha 在 196~255 之间（半透明的深紫），模型走 <b>translucent</b> 层：照强化玻璃的 cutout 画会变成不透明的黑块。
     * 同虚空石分两层：玻璃本身不染色，染的是 voidglass_veins.png 的纹路（主脉两侧的光晕是半透明的），纹路各面外推 0.05 像素保证画在玻璃之后
     * （见 {@link ECMaterials#veinedDyeableModel}）；纹路出口和虚空石对齐，石头和玻璃拼在一起纹路也连得上。
     * MRU 抗性 15；富集仓外壳。结构里不用它（半透明层大面积铺开要排序）。
     */
    public static final RegistryObject<DyeableBlock> VOID_GLASS = ECBlocks.register("void_glass",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.HAT)
                    .strength(8F, ECBlocks.legacyResistance(8F, 150F, false))
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false), true),
            BlockSpec.of(ECMaterials.veinedDyeableModel("block/voidglass", "block/voidglass_veins", true)));

    /**
     * 原作 {@code new BlockFenceEC(Material.ROCK, MapColor.BLACK).setHardness(8).setResistance(150)} → 原版 FenceBlock（同 ECAlloys 的两种栅栏：
     * 石质、要镐，进 minecraft:fences，能和别的非木质栅栏相连；不进 forge:fences）。贴图同虚空石（不染色）。
     */
    public static final RegistryObject<FenceBlock> VOID_FENCE = ECBlocks.register("void_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(8F, ECBlocks.legacyResistance(8F, 150F, false))
                    .requiresCorrectToolForDrops()),
            BlockSpec.of((provider, block) -> {
                        ResourceLocation tex = provider.tex("block/voidstone");
                        provider.fenceBlock((FenceBlock) block, tex);
                        provider.itemModels().fenceInventory(provider.name(block), tex);
                    })
                    .addBlockTags(BlockTags.FENCES)
                    .itemTags(ItemTags.FENCES));

    /**
     * 原作 {@code registerBlockSimple(new BlockEC(Material.ROCK), "concrete", 1, 1, 0)}（先抗爆后硬度）→ 硬度 1、抗爆 1；要镐。
     * 中文名保留「混凝土」（原版 1.20.1 只有 &lt;颜色&gt;_concrete，没有裸 concrete，不冲突）。城镇废墟、古代墓穴的建材，没有配方，生存里只能从那里挖。
     * 原作第 5 个参数（不透光度 0）是顺手填的：光穿过实心方块、下面的洞穴漏天光，这里按普通不透光方块（同 M1 对恐惧泥土等的处理）。
     */
    public static final RegistryObject<Block> CONCRETE = ECBlocks.register("concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .requiresCorrectToolForDrops()),
            BlockSpec.of(BlockSpec.cubeAll("block/firstworld/burnedconcrete")));

    /** 原作 {@code registerBlockSimple(new BlockMRULevitator(), "levitator", 1, 100, 0)} → 硬度 1、抗爆 60；Material.ROCK 要镐。见 {@link LevitatorBlock} */
    public static final RegistryObject<LevitatorBlock> LEVITATOR = ECBlocks.register("levitator",
            () -> new LevitatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1F, ECBlocks.legacyResistance(1F, 100F, true))
                    .noOcclusion()
                    .requiresCorrectToolForDrops()),
            BlockSpec.of(BlockSpec.existing("levitator")));

    /** 原作 {@code registerBlockSimple(new BlockMRUSpreader(), "spreader", 1, 100, 0)} + {@code setLightLevel(1F)}、MapColor.PURPLE → 硬度 1、抗爆 60、亮度 15。见 {@link SpreaderBlock} */
    public static final RegistryObject<SpreaderBlock> SPREADER = ECBlocks.register("spreader",
            () -> new SpreaderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1F, ECBlocks.legacyResistance(1F, 100F, true))
                    .lightLevel(state -> SpreaderBlock.LIGHT)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()),
            BlockSpec.of(BlockSpec.existing("spreader")));

    private ECVoid() {
    }

    /** 静态字段在类加载时已登记进 DeferredRegister；本模块没有配置、指令、网络包要登记 */
    public static void init() {
    }

    /** 八种余烬，按原作 meta 顺序 */
    public static List<RegistryObject<Item>> embers() {
        return List.of(ACIDIC_EMBER, CHAOS_EMBER, COMMON_EMBER, CORRUPTED_EMBER, CRYSTAL_EMBER, DIVINE_EMBER, MAGICAL_EMBER, FLAME_EMBER);
    }

    // ---------------------------------------------------------------- 注册工具

    private static RegistryObject<Item> item(String name, String texture) {
        return ECItems.register(name, () -> new Item(new Item.Properties()), ItemSpec.generated(texture));
    }

    /** 注册名 {@code <种类>_ember}，贴图 {@code item/misc/ember_<种类>}（原作物品方块状态 blockstates/item/ember.json 的 type=&lt;种类&gt;） */
    private static RegistryObject<Item> ember(String kind) {
        return ECItems.register(kind + "_ember", () -> new Item(new Item.Properties().stacksTo(EMBER_STACK)),
                ItemSpec.generated("item/misc/ember_" + kind).tags(EMBERS));
    }

    /**
     * 神秘饰品的物品模型（datagen 时才执行）：3 种形态 × 6 种底 × 6 种面 = 108 个两层模型 {@code item/mystical_accessory/<形态>_<底>_<面>}
     * （layer0 = {@code item/baubles/bauble_<形态>_<底>}，layer1 = {@code bauble_<形态>_<面>_t}，照原作物品方块状态 blockstates/item/baubles{amulet,belt,ring}.json），
     * 主模型按物品属性 {@link MysticalAccessoryItem#MODEL_PREDICATE}（= 形态 × 36 + 底 × 6 + 面，整数，不夹到 0~1）逐个 override。
     * override 取「最后一个所有条件都 ≤ 属性值」的那条，所以按阈值从小到大写；没定形态的显示项链 0 / 0（和原作一样）。
     */
    private static void mysticalAccessoryModels(ECItemModelProvider provider, Item item) {
        ItemModelBuilder main = layered(provider, provider.name(item), 0, 0, 0);
        for (int type = 0; type < MysticalAccessoryItem.TYPES; type++) {
            for (int bottom = 0; bottom < MysticalAccessoryItem.STYLES; bottom++) {
                for (int top = 0; top < MysticalAccessoryItem.STYLES; top++) {
                    String name = "item/mystical_accessory/" + MysticalAccessoryItem.KINDS[type] + "_" + bottom + "_" + top;
                    ItemModelBuilder variant = layered(provider, name, type, bottom, top);
                    main.override()
                            .predicate(MysticalAccessoryItem.MODEL_PREDICATE, MysticalAccessoryItem.modelIndex(type, bottom, top))
                            .model(variant)
                            .end();
                }
            }
        }
    }

    private static ItemModelBuilder layered(ECItemModelProvider provider, String name, int type, int bottom, int top) {
        String kind = MysticalAccessoryItem.KINDS[type];
        return provider.withExistingParent(name, provider.mcLoc("item/generated"))
                .texture("layer0", provider.tex("item/baubles/bauble_" + kind + "_" + bottom))
                .texture("layer1", provider.tex("item/baubles/bauble_" + kind + "_" + top + "_t"));
    }

    // ---------------------------------------------------------------- ε 腰带

    /**
     * ε 腰带（{@link ECBaubles#DROPS_EMBERS}，原作 ECEventHandler.onKillEntity L1093~1110）：真玩家亲手击杀（伤害源的来源是真玩家，弓箭也算）一只敌对生物时
     * 按 {@code bauble_limits.epsilon_ember_chance}（默认 {@value #EMBER_CHANCE_PERCENT}%）多掉一个随机余烬（从 {@link #EMBERS} 标签里等概率挑）。
     * <p>
     * 移植改动：原作在死亡事件里直接生成掉落物，不管 doMobLoot、被别的模组救活 / 取消掉落了也照掉；这里加进 {@link LivingDropsEvent} 的掉落列表
     * （NORMAL 优先级，不收已取消的），遵守 doMobLoot；假玩家（机器、自动化）击杀不掉（原作也排除 FakePlayer）。
     * 第十批饰品平衡起只认敌对生物（{@link VoidEmberEvents#isEmberVictim}）：原作对动物和玩家也算。饰品被 {@code general.baubles.disabled} 禁用时查不到（CuriosCompat 缓存）。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class VoidEmberEvents {

        /** 5%（原作 {@code rand.nextFloat() < 0.05F}；现在读 {@code bauble_limits.epsilon_ember_chance}，这里是默认值） */
        public static final float EMBER_CHANCE = 0.05F;
        static final int EMBER_CHANCE_PERCENT = 5;

        private VoidEmberEvents() {
        }

        @SubscribeEvent
        public static void onLivingDrops(LivingDropsEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) {
                return;
            }
            ItemStack ember = rollEmber(victim, event.getSource(), victim.getRandom());
            if (!ember.isEmpty()) {
                ItemEntity entity = new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), ember);
                entity.setDefaultPickUpDelay();
                event.getDrops().add(entity);
            }
        }

        /**
         * 算不算 ε 的击杀：敌对生物（{@link Enemy}），玩家不算。第十批饰品平衡：原作对所有生物（鸡、繁殖出来的牛羊）和玩家都算，
         * 下蛋孵鸡、繁殖器、找朋友互杀都能刷余烬，余烬又是余烬锻炉的材料；只认敌对生物以后，亲手打怪的正常玩法不受影响。
         */
        public static boolean isEmberVictim(LivingEntity victim) {
            return victim instanceof Enemy && !(victim instanceof Player);
        }

        /**
         * 这次死亡要不要多掉一个余烬（GameTest 直接调）：来源是真玩家、死者是敌对生物（{@link #isEmberVictim}）、戴着生效的 ε 腰带、doMobLoot 开着，
         * 再按 {@code bauble_limits.epsilon_ember_chance}（默认 {@link #EMBER_CHANCE}）掷一次。
         *
         * @return 掉的余烬；不掉返回空栈
         */
        public static ItemStack rollEmber(LivingEntity victim, @Nullable DamageSource source, RandomSource random) {
            if (source == null || !ECProtection.isRealPlayer(source.getEntity())) {
                return ItemStack.EMPTY;
            }
            Player killer = (Player) source.getEntity();
            Level level = victim.level();
            if (killer == victim || !isEmberVictim(victim) || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                    || !CuriosCompat.has(killer, ECBaubles.DROPS_EMBERS)) {
                return ItemStack.EMPTY;
            }
            if (random.nextFloat() >= ECBaubles.Limits.epsilonEmberChance()) {
                return ItemStack.EMPTY;
            }
            Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(EMBERS);
            if (tag.isEmpty() || tag.get().size() == 0) {
                return ItemStack.EMPTY;
            }
            Optional<Holder<Item>> picked = tag.get().getRandomElement(random);
            return picked.map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
        }
    }
}
