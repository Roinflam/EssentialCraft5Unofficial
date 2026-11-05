package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.EmberForgeBlock;
import com.flameforge.essentialcraft5.blockentity.EmberForgeBlockEntity;
import com.flameforge.essentialcraft5.client.model.ForgedSwordModelBuilder;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 余烬锻炉模块（第七批 M2，计划 tools/plan/batch7.md §4.2）：余烬锻炉 {@code ember_forge}（结构、托住物品、夜里锻造）与余烬元素剑 {@code forged_elemental_sword}。
 * <p>
 * 新旧注册名：emberForge → {@code ember_forge}（原作 BlockEmberForge + TileEmberForge；{@code BlocksCore} L103 {@code registerBlockSimple(…, 1, 1, 0)}：
 * 先抗爆后硬度 → 1.20.1 抗爆 1.0，不透光度 0 → 天空光穿过，Material.ROCK 要镐）；
 * elementalSword → {@code forged_elemental_sword}（原作 ItemElementalSword；原名转换后和元素工具剑 {@code elemental_sword} 重名，改名）。
 * <p>
 * 玩家视角、规则和与原作的不同见 {@link EmberForgeBlockEntity}、{@link ForgedElementalSwordItem} 的类注释，
 * 余烬组合表在 {@code item/ember/EmberEffect}，锻造数据在 {@code item/ember/ForgedSwordData}；服务端配置 {@code machines.ember_forge}（锻炉）、
 * {@code general.ember_sword}（剑的余烬加成上限）。
 * 结构 {@link #STRUCTURE}（手册结构页按 id 画，成形时触发进度条件 {@code essentialcraft5:structure_formed}）。
 * 工作台配方在 data/essentialcraft5/recipes/ember_forge/，剑进 {@code minecraft:swords}（tools/tags/ecemberforge.json），
 * 文字在 tools/lang/keys/ecemberforge.json，手册片段 tools/guide/fragments/void_machines/ember.*。
 * 客户端部分：剑的模型 loader {@code essentialcraft5:forged_sword}（client/model/ForgedSwordModel，登记在 client/EmberForgeClient），
 * 锻炉的粒子和火焰声在方块实体的 clientTick 里（法术粒子 client/render/SpellFx）。
 * <p>
 * 第十批 10b 接在后面：余烬嵌片 {@code ember_inlay}（{@link EmberInlayItem}，事件在 {@code item/ember/InlayEvents}），把一对余烬的命中效果嵌进任何武器；
 * 配置 {@code general.ember_inlay}，配方 {@code data/essentialcraft5/recipes/ember_inlay/}（14 条，{@code tools/gen/ember_inlay_recipes.py} 生成），
 * 标签 {@code essentialcraft5:inlayable} / {@code inlay_blacklist}（tools/tags/ecweaponmech.json），文字在 tools/lang/keys/ecweaponmech.json，
 * 客户端染色和武器提示在 client/EmberInlayClient。
 * <p>
 * 第十三批 13b 接在后面（batch13.md §4.4、§4.5）：余烬元素剑的同调阶效果（家族 {@code ember_sword}，最高 2 阶，倍数在 {@code general.ember_sword.stage*}）、
 * 裁决之刃 {@code verdict_blade}（{@link VerdictBladeItem}：余烬剑 2 阶在魔力工作台升格而来，家族 {@code verdict_blade} 最高 5 阶；配置 {@code general.verdict_blade}，
 * 伤害类型 {@code essentialcraft5:verdict}）。升阶 / 升格配方在 data/essentialcraft5/recipes/attunement/{ember_sword,verdict}_*.json，
 * 「任意余烬」是虚空模块的物品标签 {@code essentialcraft5:embers}（{@link ECVoid#EMBERS}）。裁决之刃是单层手持模型（贴图 item/tools/verdict_blade），
 * 元素和余烬只写在提示框里，不走 forged_sword 的分层模型。
 */
public final class ECEmberForge {

    /** 结构 id（手册结构页、进度条件 structure_formed 用）；登记处写的是同一个字面量（手册离线检查按字面量找登记） */
    public static final ResourceLocation STRUCTURE_ID = EssentialCraft5.id("ember_forge");

    // ---------------- 余烬锻炉 ----------------

    public static final RegistryObject<EmberForgeBlock> EMBER_FORGE = ECBlocks.register("ember_forge",
            () -> new EmberForgeBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().cubeTop(provider.name(block), provider.tex("block/fortifiedstone"), provider.tex("block/ember_anvil_top")))));

    public static final RegistryObject<BlockEntityType<EmberForgeBlockEntity>> EMBER_FORGE_BE =
            ECBlockEntities.type("ember_forge", EmberForgeBlockEntity::new, EMBER_FORGE);

    // ---------------- 余烬元素剑 ----------------

    /** 模型 lambda 只在 datagen 时执行（ForgedSwordModelBuilder 在 client 包，专用服不会加载） */
    public static final RegistryObject<ForgedElementalSwordItem> FORGED_ELEMENTAL_SWORD = ECItems.register("forged_elemental_sword",
            () -> new ForgedElementalSwordItem(new Item.Properties()),
            ItemSpec.of((provider, item) -> ForgedSwordModelBuilder.swordItem(provider, item)));

    // ---------------- 裁决之刃（第十三批 13b） ----------------

    /** 单层手持模型；进剑、矩阵特性、同调家族 verdict_blade 三个标签 */
    public static final RegistryObject<VerdictBladeItem> VERDICT_BLADE = ECItems.register("verdict_blade",
            () -> new VerdictBladeItem(new Item.Properties()),
            ItemSpec.handheld("item/tools/verdict_blade").tags(ItemTags.SWORDS));

    // ---------------- 余烬嵌片（第十批 10b） ----------------

    /** 底图 + 两层按余烬染色（{@link EmberInlayItem#tint}）：右上一颗是第一种余烬、左下一颗是第二种 */
    public static final RegistryObject<EmberInlayItem> EMBER_INLAY = ECItems.register("ember_inlay",
            () -> new EmberInlayItem(new Item.Properties()),
            ItemSpec.of((provider, item) -> provider.generated(item, "item/misc/ember_inlay_base")
                    .texture("layer1", provider.tex("item/misc/ember_inlay_a"))
                    .texture("layer2", provider.tex("item/misc/ember_inlay_b"))));

    /** 手册步骤「余烬嵌刻」：第一次从铁砧取走嵌好的武器时由代码发（{@code data/essentialcraft5/advancements/guide/void_machines/ember_inlay.json}） */
    public static final ResourceLocation ADVANCEMENT_EMBER_INLAY = EssentialCraft5.id("guide/void_machines/ember_inlay");

    // ---------------- 结构（字段写在它引用的 RegistryObject 之后，别的模块的方块用 lambda 取） ----------------

    /** 虚空石（任意颜色：染色是同一个方块的状态） */
    public static final StructurePattern.Rule VOID_STONE = StructurePattern.Rule.block(() -> ECVoid.VOID_STONE.get(),
            "structure.essentialcraft5.ember_forge.void_stone");
    /** 淡青护板（任意颜色；魔力、米斯莱护板不算，同原作 {@code BlocksCore.platingPale}） */
    public static final StructurePattern.Rule PALE_PLATING = StructurePattern.Rule.block(() -> ECAlloys.PALE_PLATING.get(),
            "structure.essentialcraft5.ember_forge.pale_plating");
    /** 魔力护板（任意颜色；淡青、米斯莱护板不算，同原作 {@code BlocksCore.magicPlating}） */
    public static final StructurePattern.Rule MAGIC_PLATING = StructurePattern.Rule.block(() -> ECAlloys.MAGIC_PLATING.get(),
            "structure.essentialcraft5.ember_forge.magic_plating");

    /**
     * 余烬锻炉（原作 {@code TileEmberForge.structureChecker} L322~345）：锻炉下一层 5×5 虚空石（25）；锻炉这一层四角虚空石（4）、
     * 正四向距离 2 的淡青护板（4，余烬放在它们上面）；上一层四角魔力护板（4，焦点放在它们上面）。合计虚空石 29、淡青护板 4、魔力护板 4。
     * 放东西的 8 格（淡青护板、魔力护板正上方）不在结构里：原作不查，东西本来就占着那一格。
     */
    public static final StructurePattern STRUCTURE = StructurePatterns.register(EssentialCraft5.id("ember_forge"),
            StructurePattern.builder()
                    .where('V', VOID_STONE)
                    .where('P', PALE_PLATING)
                    .where('M', MAGIC_PLATING)
                    .where('F', StructurePattern.Rule.block(EMBER_FORGE))
                    .anchor('F')
                    .layer("VVVVV", "VVVVV", "VVVVV", "VVVVV", "VVVVV")   // y = −1
                    .layer("V P V", "     ", "P F P", "     ", "V P V")   // y = 0：锻炉层
                    .layer("M   M", "     ", "     ", "     ", "M   M")   // y = +1：四角魔力护板
                    .build());

    private ECEmberForge() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，另登记锻炉和剑的配置 */
    public static void init() {
        ECConfig.machine(EmberForgeBlockEntity.Config::define);
        ECConfig.general(ForgedElementalSwordItem.Config::define);
        ECConfig.general(EmberInlayItem.Config::define);
        ECConfig.general(VerdictBladeItem.Config::define);
        MatrixTraits.registerWeaponMultiplier(VERDICT_BLADE, VerdictBladeItem.Config::traitMultiplier);
    }
}
