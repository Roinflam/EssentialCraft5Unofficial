package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.FancyBlock;
import com.flameforge.essentialcraft5.block.FancyMaterial;
import com.flameforge.essentialcraft5.block.FancyMimicBlock;
import com.flameforge.essentialcraft5.block.FancyPattern;
import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.FancyMimicBlockEntity;
import com.flameforge.essentialcraft5.item.MagicalChiselItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 魔力凿与花纹方块（第五批 ECDecorations，batch5 §3.3；D3 取 A：本批只做有底材的 6 种 × 16 + 花纹拟态 16）。
 * <p>
 * 新旧注册名：magicalChisel → magical_chisel；fancyBlock.&lt;底材&gt;（meta 0~15）→ {@code fancy_<底材>_<花纹>}
 * （底材按 {@link FancyMaterial} 顺序：mru、concrete、fortified_stone、cold_stone、magic_plating、pale_plating、void_stone、mithriline_plating、demonic_plating；
 * 花纹按原作 meta 顺序，见 {@link FancyPattern}）；
 * fancyBlock.mimic（meta 0~15）→ {@code fancy_mimic_<花纹>}，方块实体 {@code fancy_mimic}。混凝土、虚空石的花纹随第六批 6a（ECVoid）加入，恶魔护板的随第七批（ECDemonic）。
 * <p>
 * <b>字段名约定的例外</b>：144 个花纹方块（9 种底材 × 16）、16 个花纹拟态不各写一个字段（「字段名 = 注册名大写」），按底材 / 花纹放在 {@link #FANCY}、{@link #FANCY_MIMIC} 两张表里，
 * 用 {@link #fancy(FancyMaterial, FancyPattern)}、{@link #fancyMimic(FancyPattern)} 取；别的模块照约定按注册名从 {@code ForgeRegistries} 取。
 * 注册顺序 = 创造栏顺序：底材为主、花纹按原作 meta 顺序，然后花纹拟态。全部进创造栏第三页「装饰」（{@code .decoration()}，F5-3），不进方块 / 物品页。
 * <p>
 * 标签：花纹方块的物品进 {@code essentialcraft5:fancy_blocks}、花纹拟态进 {@code essentialcraft5:fancy_mimics}（手册的打勾进度用）；
 * 花纹拟态进 {@code essentialcraft5:mimic_blacklist}（不能被模仿）、{@code minecraft:prevent_mob_spawning_inside}。
 * <b>不进任何原料标签或结构标签</b>（防刷：1 个底材换 4 个花纹；GameTest {@code fancyNotUsableAsMaterial} 断言）。标签都写在规格上，没有 tools/tags 文件。
 * <p>
 * 配方 data/essentialcraft5/recipes/decorations/（{@code tools/gen/fancy_recipes.py} 生成，161 条：10 种底材（含拟态方块）× 16 + 魔力凿），文字 tools/lang/keys/ecdecorations.json，
 * 模型模板 models/block/fancy_overlayed.json（底图 + 花纹）、fancy_overlay.json（只有花纹，伪装时叠的那层；原作 overlay.json 导入），
 * 手册 guide/{zh_cn,en_us}/decorations.json、进度 advancements/guide/decorations/。没有菜单、配置、网络包、客户端类（伪装渲染由框架 client/camo 做）。
 */
public final class ECDecorations {

    /** 花纹方块的物品标签（手册 decorations/patterns 的打勾进度用） */
    public static final TagKey<Item> FANCY_BLOCKS = ECTags.modItem("fancy_blocks");
    /** 花纹拟态的物品标签（手册 decorations/fancy_mimic 的打勾进度用） */
    public static final TagKey<Item> FANCY_MIMICS = ECTags.modItem("fancy_mimics");

    // ---------------- 魔力凿 ----------------

    /** 原作 ItemMagicalChisel：setMaxDamage(256)；手持画法 item/handheld，贴图 item/tools/magicalchisel */
    public static final RegistryObject<MagicalChiselItem> MAGICAL_CHISEL = ECItems.register("magical_chisel",
            () -> new MagicalChiselItem(new Item.Properties().durability(MagicalChiselItem.USES)),
            ItemSpec.handheld("item/tools/magicalchisel").decoration());

    // ---------------- 花纹方块（底材 × 花纹） ----------------

    /** 底材 → 花纹 → 方块（96 个，见类注释的字段约定例外） */
    public static final Map<FancyMaterial, Map<FancyPattern, RegistryObject<FancyBlock>>> FANCY = registerFancyBlocks();

    // ---------------- 花纹拟态 ----------------

    /** 花纹 → 花纹拟态（16 个） */
    public static final Map<FancyPattern, RegistryObject<FancyMimicBlock>> FANCY_MIMIC = registerFancyMimics();

    /** 16 种花纹拟态共用的方块实体类型（必须写在 {@link #FANCY_MIMIC} 之后：类加载时按顺序初始化） */
    public static final RegistryObject<BlockEntityType<FancyMimicBlockEntity>> FANCY_MIMIC_BE =
            ECBlockEntities.type("fancy_mimic", FancyMimicBlockEntity::new, fancyMimicSuppliers());

    private ECDecorations() {
    }

    /** 某种底材某种花纹的花纹方块 */
    public static RegistryObject<FancyBlock> fancy(FancyMaterial material, FancyPattern pattern) {
        return FANCY.get(material).get(pattern);
    }

    /** 某种花纹的花纹拟态 */
    public static RegistryObject<FancyMimicBlock> fancyMimic(FancyPattern pattern) {
        return FANCY_MIMIC.get(pattern);
    }

    /** 全部 128 个花纹方块（8 种底材 × 16），按注册顺序 */
    public static List<RegistryObject<FancyBlock>> allFancy() {
        List<RegistryObject<FancyBlock>> all = new ArrayList<>();
        for (Map<FancyPattern, RegistryObject<FancyBlock>> row : FANCY.values()) {
            all.addAll(row.values());
        }
        return Collections.unmodifiableList(all);
    }

    /** 伪装时叠在上面的花纹模型 id：{@code essentialcraft5:block/fancy_overlay/<花纹>}（datagen 生成，父模型 block/fancy_overlay） */
    public static ResourceLocation overlayModel(FancyPattern pattern) {
        return EssentialCraft5.id("block/fancy_overlay/" + pattern.id());
    }

    private static Map<FancyMaterial, Map<FancyPattern, RegistryObject<FancyBlock>>> registerFancyBlocks() {
        Map<FancyMaterial, Map<FancyPattern, RegistryObject<FancyBlock>>> table = new EnumMap<>(FancyMaterial.class);
        for (FancyMaterial material : FancyMaterial.values()) {
            Map<FancyPattern, RegistryObject<FancyBlock>> row = new EnumMap<>(FancyPattern.class);
            for (FancyPattern pattern : FancyPattern.values()) {
                BlockSpec spec = BlockSpec.of(overlayedModel(material.texture(), pattern))
                        .itemTags(FANCY_BLOCKS)
                        .decoration();
                if (material.glassy()) {
                    spec.blockTags();   // 霜冻石（原作 Material.GLASS）空手可挖、镐不加速：不进 mineable
                }
                row.put(pattern, ECBlocks.register("fancy_" + material.id() + "_" + pattern.id(),
                        () -> new FancyBlock(material, pattern), spec));
            }
            table.put(material, Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableMap(table);
    }

    /**
     * 属性照拟态方块（原作 L207 {@code setResistance(15).setHardness(3).setLightOpacity(15)} → 硬度 3、抗爆 9）：液体流不进来、上面不刷怪、
     * 不闷人、不挡视线、不当红石导体（原作 BlockMimic 的 isFullCube 为假，不算普通方块，这几样都照它）。和拟态方块唯一的玩法区别是有碰撞箱（{@link FancyMimicBlock}）。
     */
    private static Map<FancyPattern, RegistryObject<FancyMimicBlock>> registerFancyMimics() {
        Map<FancyPattern, RegistryObject<FancyMimicBlock>> table = new EnumMap<>(FancyPattern.class);
        for (FancyPattern pattern : FancyPattern.values()) {
            table.put(pattern, ECBlocks.register("fancy_mimic_" + pattern.id(),
                    () -> new FancyMimicBlock(ECBlocks.machine(MapColor.STONE, 3F, ECBlocks.legacyResistance(3F, 15F, true))
                            .noOcclusion()
                            .forceSolidOn()
                            .isValidSpawn((state, level, pos, type) -> false)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
                            .isRedstoneConductor((state, level, pos) -> false), pattern),
                    BlockSpec.of(fancyMimicModel(pattern))
                            .addBlockTags(Camo.MIMIC_BLACKLIST, BlockTags.PREVENT_MOB_SPAWNING_INSIDE)
                            .itemTags(FANCY_MIMICS)
                            .decoration()));
        }
        return Collections.unmodifiableMap(table);
    }

    @SuppressWarnings("unchecked")
    private static Supplier<? extends Block>[] fancyMimicSuppliers() {
        return FANCY_MIMIC.values().toArray(new Supplier[0]);
    }

    // ---------------- 模型（datagen 时才执行） ----------------

    /**
     * 花纹方块：模板 {@code block/fancy_overlayed}（底图在前、正常明暗；花纹在后、{@code shade: false}，同原作 cube_all + overlay 子模型），镂空层。
     * 不复用矿石的 {@code ore_overlayed}：它的里层 {@code shade: false}，底图会发亮。
     */
    private static BlockSpec.ModelGen overlayedModel(String baseTexture, FancyPattern pattern) {
        return (provider, block) -> provider.simpleBlockWithItem(block, provider.models()
                .withExistingParent(provider.name(block), provider.modLoc("block/fancy_overlayed"))
                .texture("base", provider.tex(baseTexture))
                .texture("overlay", provider.tex(pattern.overlayTexture()))
                .texture("particle", provider.tex(baseTexture))
                .renderType("cutout_mipped"));
    }

    /**
     * 花纹拟态：没伪装时的模型 = 拟态方块贴图 + 花纹（原作 fancyblock/mimicinv）；另外生成伪装时叠的那层
     * {@code block/fancy_overlay/<花纹>}（只有花纹，原作 fancyblock/mimic 的 overlay 模型），由 {@link #init()} 登记给框架。
     */
    private static BlockSpec.ModelGen fancyMimicModel(FancyPattern pattern) {
        BlockSpec.ModelGen own = overlayedModel("block/mimic", pattern);
        return (provider, block) -> {
            own.generate(provider, block);
            provider.models()
                    .withExistingParent(overlayModel(pattern).getPath(), provider.modLoc("block/fancy_overlay"))
                    .texture("out", provider.tex(pattern.overlayTexture()));
        };
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister；这里把 16 种花纹拟态登记给伪装框架（带花纹模型，F5-1） */
    public static void init() {
        for (Map.Entry<FancyPattern, RegistryObject<FancyMimicBlock>> entry : FANCY_MIMIC.entrySet()) {
            Camo.registerCamoBlock(entry.getValue(), overlayModel(entry.getKey()));
        }
    }
}
