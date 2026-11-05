package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.block.WindRuneBlock;
import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.WindCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.effect.ParanormalLightnessEffect;
import com.flameforge.essentialcraft5.item.AirPotionItem;
import com.flameforge.essentialcraft5.item.BottledWindItem;
import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;

/**
 * 风模块（第三批，batch3.md §3.3）：风之符文祭坛与注风（新配方类型 {@code essentialcraft5:wind_imbue}）、风之水晶、风之药水与「轻盈」、
 * 瓶中之风三件、风之石板（换海纳尔之门的逻辑在 {@link com.flameforge.essentialcraft5.item.WindTabletItem} / {@link ECHoannaPortal}）、「风之触」效果、风之眷顾后的风之好感事件（{@link com.flameforge.essentialcraft5.player.WindRelations}）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>windRune（BlockWindRune / TileWindRune）→ wind_rune</li>
 *   <li>genItem:55 → wind_gem；air_potion → air_potion；bottledWind / imprisonedWind / windKeeper → bottled_wind / imprisoned_wind / wind_keeper；
 *       windTablet → wind_tablet</li>
 *   <li>效果 potion.paranormalLightness / potion.windtouch → paranormal_lightness / wind_touch（不在命名表范围，名字按 batch3.md §9）</li>
 * </ul>
 * 工作台配方和三条注风在 data/essentialcraft5/recipes/wind/，进度 main/wind_gem 与 guide/wind/*，
 * 文字在 tools/lang/keys/ecwind.json，手册章节 assets/essentialcraft5/guide/wind.json；客户端部分（符文 BER、手册配方画法）见 client/WindClient。
 * 风之好感的系数和加法是框架的 {@link com.flameforge.essentialcraft5.api.WindAffinity}，这里只写「什么时候加、加多少」。
 * <p>
 * 第十批 10b-M1「风之试炼」接在后面：风之心愿（{@link com.flameforge.essentialcraft5.player.WindWishes}，配置 {@code general.wind_wish}）、
 * 风之号角 {@code wind_horn}（EC5 新增，{@link WindHornItem}，注风配方 data/essentialcraft5/recipes/wind/imbue/wind_horn.json，配置 {@code general.wind_horn}）、
 * 号角召来的风法师的规矩（{@link com.flameforge.essentialcraft5.entity.WindMageSummons}）；进度 guide/wind/{wish,wind_horn,archmage}、guide/hoanna/true_name，
 * 文字在 tools/lang/keys/windtrial.json，测试 gametest/WindTrialTests。
 */
public final class ECWind {

    // ---------------- 方块 ----------------

    /**
     * 原作 registerBlockSimple(new BlockWindRune(), "windRune", 3, 10, 0)：先抗爆后硬度 → 硬度 3、抗爆 6；Material.ROCK（要镐）、不透光度 0。
     * 1/16 格高的薄片、没有碰撞箱（原作 NULL_AABB），但和原作一样挡液体（见下面 forceSolidOn）；世界里的方块模型是空的（只给破坏粒子），地上那张 3×3 的符文由 BER 画、只在结构完整时出现，
     * 物品用原作的平面图标 block/windrune。
     */
    public static final RegistryObject<WindRuneBlock> WIND_RUNE = ECBlocks.register("wind_rune",
            () -> new WindRuneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE)
                    .strength(3F, ECBlocks.legacyResistance(3F, 10F, true))
                    .requiresCorrectToolForDrops()
                    // forceSolidOn：原作 Material.ROCK 挡液体；1.20 没碰撞箱的方块默认会被水冲掉、被岩浆烧掉（岩浆烧掉时什么都不掉），
                    // 往这一格倒桶（发射器，或点旁边方块的侧面）也会直接把它打掉（canBeReplaced(液体) 对不是 isSolid 的方块放行）。
                    // 同原版蜘蛛网、告示牌的写法：只改 isSolid / blocksMotion，不加碰撞箱，照样能穿过去
                    .forceSolidOn()
                    .noCollission()
                    .noOcclusion()),
            BlockSpec.of((provider, block) -> {
                ModelFile empty = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/windrune"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(empty).build());
                provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("block/windrune"));
            }));

    public static final RegistryObject<BlockEntityType<WindRuneBlockEntity>> WIND_RUNE_BE =
            ECBlockEntities.type("wind_rune", WindRuneBlockEntity::new, WIND_RUNE);

    // ---------------- 物品 ----------------

    /** genItem:55「风之水晶」：钻石在风之符文上注风 10 000 ESPE（配置 general.wind.gem_espe）；风之工具、风之护甲的材料（ECWindGear） */
    public static final RegistryObject<Item> WIND_GEM = ECItems.register("wind_gem",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/windgem"));

    /** 原作 ItemLiquidAir（可堆叠 64，贴图 items/fluidair）：水瓶注风 250 ESPE */
    public static final RegistryObject<AirPotionItem> AIR_POTION = ECItems.register("air_potion",
            () -> new AirPotionItem(new Item.Properties()), ItemSpec.generated("item/fluidair"));

    /** 原作 ItemWindKeeper(10)：风法师学徒掉落 */
    public static final RegistryObject<BottledWindItem> BOTTLED_WIND = ECItems.register("bottled_wind",
            () -> new BottledWindItem(new Item.Properties(), 10), ItemSpec.generated("item/bottledwind"));
    /** 原作 ItemWindKeeper(25)：风法师掉落 */
    public static final RegistryObject<BottledWindItem> IMPRISONED_WIND = ECItems.register("imprisoned_wind",
            () -> new BottledWindItem(new Item.Properties(), 25), ItemSpec.generated("item/imprisonedwind"));
    /** 原作 ItemWindKeeper(100)「受困之风」：风法师大法师掉落、捕风挂坠 15% 给；风之石板的材料 */
    public static final RegistryObject<BottledWindItem> WIND_KEEPER = ECItems.register("wind_keeper",
            () -> new BottledWindItem(new Item.Properties(), 100), ItemSpec.generated("item/windkeeper"));

    /** 原作 ItemWindTablet（ItemMRUGeneric 系，自带 5000 MRU，堆叠 1）。右键点燃的下界门换海纳尔之门、在海纳尔补空框的逻辑在 WindTabletItem.useOn（第六批 6a M2） */
    public static final RegistryObject<WindTabletItem> WIND_TABLET = ECItems.register("wind_tablet",
            () -> new WindTabletItem(new Item.Properties().stacksTo(1)), ItemSpec.generated("item/windtablet"));

    /**
     * 风之号角（EC5 新增，第十批 batch10.md §5.5.2）：山羊角在风之符文上注风（要已受风之眷顾），夜里吹响召来风之大法师和随从。
     * 堆叠 1（同原版山羊角）；贴图 item/windhorn：镶金口沿的灰绿山羊角，号口和环纹里透出风光。
     */
    public static final RegistryObject<WindHornItem> WIND_HORN = ECItems.register("wind_horn",
            () -> new WindHornItem(new Item.Properties().stacksTo(1)), ItemSpec.generated("item/windhorn"));

    // ---------------- 效果 ----------------

    /** 原作 PotionUnnormalLightness(true, 0xFFFFCC)：移速 +40% × 等级、跳得更高（见效果类） */
    public static final RegistryObject<ParanormalLightnessEffect> PARANORMAL_LIGHTNESS =
            ECEffects.register("paranormal_lightness", ParanormalLightnessEffect::new);

    /**
     * 原作 PotionWindTouch(true, 0xCCFFCC)：什么都不做的标记效果，框架的 WindAffinity.add 每次加好感时按注册名给 1000 tick。
     * 原作两个效果都标成负面（isBad=true）；1.20 的负面分类会被别的模组的「清除负面效果」清掉、药水提示显示成红字，这里改成正面。
     */
    public static final RegistryObject<MobEffect> WIND_TOUCH = ECEffects.simple("wind_touch", MobEffectCategory.BENEFICIAL, 0xCCFFCC);

    // ---------------- 配方类型 ----------------

    /** 注风（原作 api/WindImbueRecipe，RecipesCore L205~209）；JSON 格式见 WindImbueRecipe */
    public static final RegistryObject<RecipeType<WindImbueRecipe>> WIND_IMBUE = ECRecipes.type("wind_imbue");
    public static final RegistryObject<RecipeSerializer<WindImbueRecipe>> WIND_IMBUE_SERIALIZER =
            ECRecipes.serializer("wind_imbue", WindImbueRecipe.Serializer::new);

    // ---------------- 结构（必须写在它引用的方块字段之后） ----------------

    /** 8 根 ESPE 水晶柱（任意档，放底座）：注风从这 8 根里抽（原作 TileWindRune.coords） */
    public static final StructurePattern.Rule RUNE_PILLAR = MithrilineCrystalPillarBlock.pillarRule("structure.essentialcraft5.wind_rune.pillar");

    /**
     * 风之符文祭坛（原作 TileWindRune.tier0Checker，以符文下方一格为原点逐格列出）：米斯莱护板 13、淡青护板 12、魔力护板 8、ESPE 水晶柱 8，护板颜色随意。
     * 按北向写（x 向东、z 向南），四面对称。从下到上：符文下一层 25 格里 21 格护板；符文这一层四角是水晶柱、四边正中是魔力护板、斜对角是米斯莱护板；
     * 再往上一层斜对角 4 块米斯莱护板；再往上一层这 4 块上面各放一根水晶柱。其余格子随意（符文前后左右 4 格空着最好：地上的符文图案画在符文周围 3×3 上，四个斜角被米斯莱护板占着）。
     * 研究手册的结构页按 id 画这一份（"structure": "essentialcraft5:wind_rune"）。
     */
    public static final StructurePattern RUNE_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("wind_rune"),
            StructurePattern.builder()
                    .where('M', StructurePattern.Rule.block(ECMithriline.MITHRILINE_PLATING))
                    .where('L', StructurePattern.Rule.block(ECAlloys.PALE_PLATING))
                    .where('G', StructurePattern.Rule.block(ECAlloys.MAGIC_PLATING))
                    .where('C', RUNE_PILLAR)
                    .where('R', StructurePattern.Rule.block(WIND_RUNE))
                    .anchor('R')
                    .layer(" LGL ", "LLMLL", "GMMMG", "LLMLL", " LGL ")   // y=-1：符文正下方和十字是米斯莱，外圈淡青与魔力
                    .layer("C G C", " M M ", "G R G", " M M ", "C G C")   // y=0：四角水晶柱，四边魔力，斜对角米斯莱
                    .layer("     ", " M M ", "     ", " M M ", "     ")   // y=1：斜对角米斯莱
                    .layer("     ", " C C ", "     ", " C C ", "     ")   // y=2：斜对角水晶柱
                    .build());

    private ECWind() {
    }

    /**
     * 由 ECRegistries.register() 调用：类加载即登记上面的条目，这里再登记配置小节 general.wind 和 /ec5 player … wind；
     * 第十批 10b 接在后面登记 general.wind_wish、general.wind_horn，以及手册迁移补上风之真名以后记满心愿数的动作。
     */
    public static void init() {
        ECConfig.general(Config::define);
        ECCommands.addPlayerSubcommand(WindCommands::build);
        ECConfig.general(WindWishes.Config::define);
        ECConfig.general(WindHornItem.Config::define);
        GuideMigration.registerAction(WindWishes.LEGACY_ACTION, WindWishes::fillLegacyWishes);
    }

    /**
     * SERVER 配置 general.wind（E5：风线的门槛照原作做成配置，开服看玩家进度再调；policy §4：新定的上限做成配置）。
     * 风之好感的基础系数不在这里，在框架的 general.wind_affinity.base_coefficient。
     */
    public static final class Config {
        /** 风之水晶的注风花费（原作 10 000）；只作用于 JSON 里写了 "espe_config": "gem_espe" 的注风配方 */
        public static ForgeConfigSpec.DoubleValue gemESPE;
        /** 受风之眷顾的注风花费（原作 40 000，主线一步要 8 根柱子主世界攒 2.8 小时，减半）；只作用于 JSON 里写了 "espe_config": "windbound_espe" 的注风配方 */
        public static ForgeConfigSpec.DoubleValue windboundESPE;
        /** 风之石板显出风的真名要多少风之好感（原作 3 500；海纳尔批的换门门槛也用它） */
        public static ForgeConfigSpec.IntValue tabletPoints;
        /** 风之石板把点燃的下界门换成海纳尔之门（海纳尔里补空门框也一样）花多少 MRU（原作 500；第六批 6a M2） */
        public static ForgeConfigSpec.IntValue tabletPortalMRU;
        /** 轻盈叠喝的等级上限（原作无上限，policy §4.2 封顶 III） */
        public static ForgeConfigSpec.IntValue lightnessMaxLevel;
        /** 轻盈叠喝的时长上限，分钟（原作无上限，policy §4.2 封顶 60 分钟） */
        public static ForgeConfigSpec.IntValue lightnessMaxMinutes;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("The wind: Wind Rune imbuing, Air Potion, Wind Tablet.", "风：风之符文注风、风之药水、风之石板").push("wind");
            gemESPE = builder.comment("ESPE cost of imbuing a Wind Crystal (recipes marked \"espe_config\": \"gem_espe\").",
                            "注风做一颗风之水晶要多少 ESPE（只对 JSON 写了 \"espe_config\": \"gem_espe\" 的配方生效）")
                    .defineInRange("gem_espe", 10000D, 0D, 1.0E7D);
            windboundESPE = builder.comment("ESPE cost of imbuing a Soul Stone to become windbound (recipes marked \"espe_config\": \"windbound_espe\"; original: 40000).",
                            "注风灵魂石、受风之眷顾要多少 ESPE（只对 JSON 写了 \"espe_config\": \"windbound_espe\" 的配方生效；原作 40000）")
                    .defineInRange("windbound_espe", 20000D, 0D, 1.0E7D);
            tabletPoints = builder.comment("Wind relation needed for the Wind Tablet to reveal the wind's name.",
                            "风之石板显出风之真名所需的风之好感度")
                    .defineInRange("tablet_points", 3500, 0, Integer.MAX_VALUE);
            tabletPortalMRU = builder.comment("MRU the Wind Tablet spends to turn a lit Nether portal into a Gate to Hoanna, or to refill an empty frame in Hoanna "
                                    + "(original: 500; only spent when it works).",
                            "风之石板把点燃的下界传送门换成海纳尔之门（在海纳尔补空门框也一样）花多少 MRU（原作 500；换成了才扣）")
                    .defineInRange("tablet_portal_mru", 500, 0, 5000);
            lightnessMaxLevel = builder.comment("Highest Lightness level reachable by drinking Air Potions (original: unlimited).",
                            "喝风之药水叠出的轻盈最高几级（原作无上限）")
                    .defineInRange("lightness_max_level", 3, 1, 10);
            lightnessMaxMinutes = builder.comment("Longest Lightness duration reachable by drinking Air Potions, in minutes (original: unlimited).",
                            "喝风之药水叠出的轻盈最长几分钟（原作无上限）")
                    .defineInRange("lightness_max_minutes", 60, 2, 1440);
            builder.pop();
        }

        public static double gemESPE() {
            return gemESPE == null ? 10000D : ECConfig.get(gemESPE);
        }

        public static double windboundESPE() {
            return windboundESPE == null ? 20000D : ECConfig.get(windboundESPE);
        }

        public static int tabletPoints() {
            return tabletPoints == null ? 3500 : ECConfig.get(tabletPoints);
        }

        public static int tabletPortalMRU() {
            return tabletPortalMRU == null ? 500 : ECConfig.get(tabletPortalMRU);
        }

        public static int lightnessMaxLevel() {
            return lightnessMaxLevel == null ? 3 : ECConfig.get(lightnessMaxLevel);
        }

        public static int lightnessMaxMinutes() {
            return lightnessMaxMinutes == null ? 60 : ECConfig.get(lightnessMaxMinutes);
        }
    }
}
