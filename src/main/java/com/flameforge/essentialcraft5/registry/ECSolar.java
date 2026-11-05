package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.EnderGeneratorBlock;
import com.flameforge.essentialcraft5.block.SolarPrismBlock;
import com.flameforge.essentialcraft5.block.SunRayAbsorberBlock;
import com.flameforge.essentialcraft5.blockentity.EnderGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SunRayAbsorberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BalanceSetterItem;
import com.flameforge.essentialcraft5.menu.EnderGeneratorMenu;
import com.flameforge.essentialcraft5.menu.SunRayAbsorberMenu;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 日光与末影模块（第二批）：日光吸收器、太阳棱镜、末影产能器、平衡值设定器。
 * <p>
 * 新旧注册名：sunRayAbsorber → sun_ray_absorber；solarPrism → solar_prism；enderGenerator → ender_generator；
 * balance_setter 不变。三个方块原作都是 setHardness(1).setResistance(1).setLightOpacity(0)
 * （先硬度后抗爆 → 抗爆 0.6，透天空光），Material.ROCK（要镐）；吸收器、末影产能器 MapColor.PURPLE，棱镜 MapColor.GOLD。
 * <p>
 * 原作的太阳光束是实体（EntitySolarBeam），这里不做实体：棱镜服务端算落点、直接通知吸收器，
 * 客户端的光柱由棱镜的 BER 画（见 {@link SolarPrismBlockEntity}）。
 * 棱镜的摆法写成框架结构 {@link #PRISM_STRUCTURE}（id essentialcraft5:solar_prism），研究手册的结构页直接引用它。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/solar/，界面与手册文字在 tools/lang/keys/ecsolar.json，
 * 手册章节 assets/essentialcraft5/guide/solar.json。客户端部分（两个界面、棱镜光柱 BER）见 client/SolarClient。
 */
public final class ECSolar {

    // ---------------- 日光吸收器 ----------------

    /** 原作 OBJ 模型 sunraycollector（blockstate 里 flip-v: true，BlockSpec.obj 默认就翻） */
    public static final RegistryObject<SunRayAbsorberBlock> SUN_RAY_ABSORBER = ECBlocks.register("sun_ray_absorber",
            () -> new SunRayAbsorberBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("sunraycollector", "block/model/sunraycollector")));

    public static final RegistryObject<BlockEntityType<SunRayAbsorberBlockEntity>> SUN_RAY_ABSORBER_BE =
            ECBlockEntities.type("sun_ray_absorber", SunRayAbsorberBlockEntity::new, SUN_RAY_ABSORBER);

    public static final RegistryObject<MenuType<SunRayAbsorberMenu>> SUN_RAY_ABSORBER_MENU =
            ECMenus.register("sun_ray_absorber", SunRayAbsorberMenu::new);

    // ---------------- 太阳棱镜 ----------------

    /**
     * 原作 JSON 模型 solarprism（导入为 models/block/solar_prism.json，translucent）：一块 3×3 格大的薄板，
     * 物品模型按它的大小缩小到约 1/2.75（原作直接拿方块模型当物品模型，物品栏里会溢出格子）。
     * 碰撞箱、选取框照原作也是 3×3（见 SolarPrismBlock）。没有界面、没有 MRU。
     */
    public static final RegistryObject<SolarPrismBlock> SOLAR_PRISM = ECBlocks.register("solar_prism",
            () -> new SolarPrismBlock(ECBlocks.machine(MapColor.GOLD, 1F, ECBlocks.legacyResistance(1F, 1F, false))
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)),
            BlockSpec.of((provider, block) -> {
                ModelFile model = provider.models().getExistingFile(provider.modLoc("block/solar_prism"));
                provider.simpleBlock(block, model);
                provider.itemModels().getBuilder("solar_prism").parent(model).transforms()
                        .transform(ItemDisplayContext.GUI).rotation(30F, 225F, 0F).scale(0.23F).end()
                        .transform(ItemDisplayContext.GROUND).translation(0F, 3F, 0F).scale(0.1F).end()
                        .transform(ItemDisplayContext.FIXED).scale(0.2F).end()
                        .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75F, 45F, 0F).translation(0F, 2.5F, 0F).scale(0.14F).end()
                        .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(75F, 45F, 0F).translation(0F, 2.5F, 0F).scale(0.14F).end()
                        .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0F, 45F, 0F).scale(0.15F).end()
                        .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0F, 225F, 0F).scale(0.15F).end()
                        .end();
            }).item(block -> new BlockItem(block, new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.essentialcraft5.solar_prism").withStyle(ChatFormatting.GRAY));
                }
            }));

    public static final RegistryObject<BlockEntityType<SolarPrismBlockEntity>> SOLAR_PRISM_BE =
            ECBlockEntities.type("solar_prism", SolarPrismBlockEntity::new, SOLAR_PRISM);

    // ---------------- 末影产能器 ----------------

    /** 原作 OBJ 模型 endergenerator（装了 CodeChickenLib 时原作改用 TESR 画同一个 OBJ，效果一样，这里直接用方块模型） */
    public static final RegistryObject<EnderGeneratorBlock> ENDER_GENERATOR = ECBlocks.register("ender_generator",
            () -> new EnderGeneratorBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("endergenerator", "block/model/endergenerator")));

    public static final RegistryObject<BlockEntityType<EnderGeneratorBlockEntity>> ENDER_GENERATOR_BE =
            ECBlockEntities.type("ender_generator", EnderGeneratorBlockEntity::new, ENDER_GENERATOR);

    public static final RegistryObject<MenuType<EnderGeneratorMenu>> ENDER_GENERATOR_MENU =
            ECMenus.register("ender_generator", EnderGeneratorMenu::new);

    // ---------------- 平衡值设定器 ----------------

    /** 原作 ItemBalanceSetter：模型借用原版烈焰棒（原作同样），没有配方，创造模式工具 */
    public static final RegistryObject<BalanceSetterItem> BALANCE_SETTER = ECItems.register("balance_setter",
            () -> new BalanceSetterItem(new Item.Properties().stacksTo(1)),
            ItemSpec.handheld("minecraft:item/blaze_rod"));

    // ---------------- 棱镜摆法 ----------------

    /**
     * 太阳棱镜的摆法（原作 TileSolarPrism.update 开头那三段判断）：同一层东南西北<b>隔一格</b>各有一个方块（任意），
     * 紧挨着的 8 格必须是空气。四面对称，判定时朝向传 NORTH。必须写在 {@link #SOLAR_PRISM} 之后（类加载顺序）。
     * 研究手册的结构页写 {@code "structure": "essentialcraft5:solar_prism"} 直接画这份定义。
     */
    public static final StructurePattern PRISM_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("solar_prism"),
            StructurePattern.builder()
                    .where('B', StructurePattern.Rule.anyBlock())
                    .where('A', StructurePattern.Rule.air())
                    .where('S', StructurePattern.Rule.block(SOLAR_PRISM))
                    .anchor('S')
                    .layer("  B  ", " AAA ", "BASAB", " AAA ", "  B  ")
                    .build());

    private ECSolar() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(SunRayAbsorberBlockEntity.Config::define);
        ECConfig.machine(SolarPrismBlockEntity.Config::define);
        ECConfig.machine(EnderGeneratorBlockEntity.Config::define);
    }
}
