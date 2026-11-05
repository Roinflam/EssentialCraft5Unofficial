package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagicalMirrorBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockStateProvider;
import com.flameforge.essentialcraft5.item.ControlRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.ObjModelBuilder;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力镜模块（第四批 4b，batch4 §3.8）：魔力镜、控制棒。镜子放在容器正上方，用控制棒链接到 8 格内的另一个容器，每 3 秒送 1 件，不耗 MRU。
 * <p>
 * 新旧注册名：magicalMirror → magical_mirror；controlRod → control_rod。
 * 链接类（policy §4.3 规划补 ②）：链接那一下对镜子格、镜子正下方的源容器格、目标格 {@code canEdit}（{@link ControlRodItem#tryLink}），
 * 之后不再查、不需要主人在线；运行时只读已加载区块，先模拟后取放，绝不吞物品（{@link MagicalMirrorBlockEntity}）。
 * <p>
 * 模型：方块只有破坏粒子（原作 blockstate normal = minecraft:block + magicplatingblock 粒子），镜框和镜面由 BER 画——
 * 同一个原作 OBJ {@code mirror.obj} 烘焙成两份额外模型（{@code magical_mirror_frame} 只留镜框 pCube2、{@code magical_mirror_glass} 只留镜面 pPlane1，
 * 用 OBJ 加载器的 visibility），镜面传送时要单独着色；物品用原作的 {@code mirrorinv.obj}（{@code magical_mirror_inventory}，带自己的界面显示变换）。
 * 两个 OBJ 用 {@code tools/import_assets.py obj mirror / mirrorinv} 导入，贴图 block/model/armtextures、block/model/mirror；
 * {@code mirrorinv.obj} 末尾有 EC5 手工补的镜面背面（两个反向面），重新导入会冲掉，要照文件里的注释补回去。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/mirror/，文字在 tools/lang/keys/ecmirror.json，
 * 手册是「物流」章（ECLogistics）的 {@code magical_mirror} 条目（assets/essentialcraft5/guide/logistics.json）。客户端部分见 client/MirrorClient。
 */
public final class ECMirror {

    /** 原作 registerBlockSimple(…, "magicalMirror", 1, 1, 0)：先抗爆后硬度 → 抗爆 1.0；透光（原作 setLightOpacity(0)）；Material.ROCK、MapColor.BLACK */
    public static final RegistryObject<MagicalMirrorBlock> MAGICAL_MIRROR = ECBlocks.register("magical_mirror",
            () -> new MagicalMirrorBlock(ECBlocks.machine(MapColor.COLOR_BLACK, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion(),
                    () -> ECMirror.MAGICAL_MIRROR_BE.get()),
            BlockSpec.of((provider, block) -> {
                ModelFile particle = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/magicplatingblock"));
                provider.simpleBlock(block, particle);
                // 原作物品模型 mirrorinv.obj；镜框贴图有透明像素，按 cutout 画（物品按 cutout 画会剔背面，镜面 pPlane1 原本是单面的，
                // EC5 在 OBJ 末尾补了一组反向面，左手、掉在地上转过去时也看得到镜面）。
                // 界面图标另给 gui 变换：沿用 block/block 的 [30, 225, 0] × 0.625 时看到的是镜面背面，模型又在 y 0.36~1.26 偏上，
                // 图标只剩一圈又小又偏的细框。改成正面朝外、微微侧转，1 倍大、下移 4.8 像素居中（约 9.5 × 14 像素）；其余视角照旧用 block/block
                provider.simpleBlockItem(block, provider.objModel("magical_mirror_inventory", "mirrorinv", "block/magicplatingblock")
                        .renderType("cutout")
                        .transforms()
                        .transform(ItemDisplayContext.GUI).rotation(15F, -25F, 0F).translation(0F, -4.8F, 0F).scale(1F).end()
                        .end());
                mirrorPart(provider, "magical_mirror_frame", "pPlane1", "block/model/armtextures");
                mirrorPart(provider, "magical_mirror_glass", "pCube2", "block/model/mirror");
            }));

    public static final RegistryObject<BlockEntityType<MagicalMirrorBlockEntity>> MAGICAL_MIRROR_BE =
            ECBlockEntities.type("magical_mirror",
                    (pos, state) -> new MagicalMirrorBlockEntity(ECMirror.MAGICAL_MIRROR_BE.get(), pos, state), MAGICAL_MIRROR);

    /** 原作叠 1，物品模型 essentialcraft:tool（= handheld） */
    public static final RegistryObject<ControlRodItem> CONTROL_ROD = ECItems.register("control_rod",
            () -> new ControlRodItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/tools/controlrod"));

    private ECMirror() {
    }

    /** BER 用的额外模型：mirror.obj 藏掉另一部分（OBJ 分组 pCube2 = 镜框、pPlane1 = 镜面） */
    private static void mirrorPart(ECBlockStateProvider provider, String name, String hiddenPart, String particle) {
        provider.models().getBuilder(name)
                .parent(provider.models().getExistingFile(provider.mcLoc("block/block")))
                .customLoader(ObjModelBuilder::begin)
                .modelLocation(provider.modLoc("models/block/mirror.obj"))
                .flipV(true)
                .visibility(hiddenPart, false)
                .end()
                .texture("particle", provider.modLoc(particle));
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.magical_mirror */
    public static void init() {
        ECConfig.machine(MagicalMirrorBlockEntity.Config::define);
    }
}
