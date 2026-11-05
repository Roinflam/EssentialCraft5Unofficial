package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * 花纹方块的底材（原作 {@code BlocksCore.postInitLoad} L196~204 的 {@code createFancyBlock} 调用，每行一种，顺序照原作）。第五批只做有底材的 6 种（batch5 D3 A）；
 * 混凝土、虚空石第六批 6a 随底材（ECVoid）加进来，恶魔护板第七批随底材（ECDemonic）加进来。加底材时在这里加一行、在 {@code tools/gen/fancy_recipes.py} 的表里加同一行再重跑。
 * {@link #ordinal()} 只在 {@code values()} 遍历里用（注册顺序、展示厅花纹墙的行），没存盘、没走网络，中间插行不影响旧存档（方块按注册名存）。
 * <p>
 * 数值：原作 {@code createFancyBlock} 先 {@code setResistance} 后 {@code setHardness}，抗爆用 {@link ECBlocks#legacyResistance}(硬度, 抗爆, true) 换算
 * （MRU 1 / 100 → 60，混凝土 1 / 5 → 3，强化石 1.5 / 8 → 4.8，霜冻石 0.7 / 1 → 0.7，魔力 / 淡青护板 2 / 8 → 4.8，虚空石 3 / 28 → 16.8，
 * 米斯莱护板、恶魔护板 3 / 100 → 60）。花纹比底材弱是原作设计。
 * <ul>
 *   <li>石质（原作 Material.ROCK）：低音鼓、石头声、要镐（0 级）才掉。</li>
 *   <li>霜冻石（原作 Material.GLASS）：玻璃声、空手可挖（1.12 的 GLASS 不要求工具）、不导红石（1.12 的 GLASS 不算不透明材料，方块不算普通方块）、
 *       上面不刷怪（同底材霜冻石和原版玻璃，batch5 §3.3 定的）；
 *       地图色取冰（原作 GLASS 的地图色是 AIR，地图上是透明的）；冷凝器冷值 0.5（原作 {@code BlockFancy.getColdModifier}）。
 *       和底材霜冻石不同，花纹霜冻石是实心不透光的（原作没覆盖 isOpaqueCube，照原作）。</li>
 * </ul>
 * 贴图 {@code textures/block/fancyblocks/<texture>.png}（MRU 那张是 16×512 的 32 帧动画）。
 */
public enum FancyMaterial {

    MRU("mru", "mru", "magical_slag", 1F, 100F, MapColor.STONE, false),
    /** 第六批 6a（ECVoid 注册混凝土）：原作 L197 {@code createFancyBlock(ROCK, "concrete", …, 1, 5)} → 抗爆 3 */
    CONCRETE("concrete", "concrete", "concrete", 1F, 5F, MapColor.STONE, false),
    FORTIFIED_STONE("fortified_stone", "fortifiedstone", "fortified_stone", 1.5F, 8F, MapColor.STONE, false),
    COLD_STONE("cold_stone", "coldstone", "cold_stone", 0.7F, 1F, MapColor.ICE, true),
    MAGIC_PLATING("magic_plating", "magicplating", "magic_plating", 2F, 8F, MapColor.STONE, false),
    PALE_PLATING("pale_plating", "paleplating", "pale_plating", 2F, 8F, MapColor.STONE, false),
    /** 第六批 6a（ECVoid 注册虚空石）：原作 L202 {@code createFancyBlock(ROCK, "voidStone", …, 3, 28)} → 抗爆 16.8（比虚空石本身的 90 弱得多，照原作） */
    VOID_STONE("void_stone", "voidstone", "void_stone", 3F, 28F, MapColor.STONE, false),
    MITHRILINE_PLATING("mithriline_plating", "mithrilineplating", "mithriline_plating", 3F, 100F, MapColor.STONE, false),
    /** 第七批（ECDemonic 注册恶魔护板）：原作 L204 {@code createFancyBlock(ROCK, "demonicPlating", …, 3, 100)} → 抗爆 60 */
    DEMONIC_PLATING("demonic_plating", "demonicplating", "demonic_plating", 3F, 100F, MapColor.STONE, false);

    /** 花纹霜冻石给冷凝器的冷值（原作 BlockFancy.getColdModifier 对霜冻石那一行返回 0.5） */
    public static final float COLD_STONE_COLD = 0.5F;

    private final String id;
    private final String texture;
    private final ResourceLocation source;
    private final float hardness;
    private final float legacyResistance;
    private final MapColor mapColor;
    private final boolean glassy;

    FancyMaterial(String id, String texture, String source, float hardness, float legacyResistance, MapColor mapColor, boolean glassy) {
        this.id = id;
        this.texture = texture;
        this.source = EssentialCraft5.id(source);
        this.hardness = hardness;
        this.legacyResistance = legacyResistance;
        this.mapColor = mapColor;
        this.glassy = glassy;
    }

    /** 注册名里的底材段：{@code fancy_<id>_<花纹>} */
    public String id() {
        return id;
    }

    /** 底图（相对 textures/）：{@code block/fancyblocks/<原作贴图名>} */
    public String texture() {
        return "block/fancyblocks/" + texture;
    }

    /** 合成用的底材物品（和魔力凿一起 → 4 个大方砖；配方由 tools/gen/fancy_recipes.py 生成，两边的表要一致） */
    public ResourceLocation source() {
        return source;
    }

    public float hardness() {
        return hardness;
    }

    /** 1.20.1 的抗爆值（原作数值按先抗爆后硬度换算） */
    public float resistance() {
        return ECBlocks.legacyResistance(hardness, legacyResistance, true);
    }

    /** 原作 Material.GLASS（只有霜冻石）：不要求工具、玻璃声、不导红石、不刷怪 */
    public boolean glassy() {
        return glassy;
    }

    /** 给冷凝器的冷值：霜冻石 0.5，其余 0 */
    public float coldModifier() {
        return this == COLD_STONE ? COLD_STONE_COLD : 0F;
    }

    /** 方块属性（每次新建一份） */
    public BlockBehaviour.Properties properties() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(hardness, resistance());
        if (glassy) {
            return properties
                    .instrument(NoteBlockInstrument.HAT)
                    .sound(SoundType.GLASS)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false);
        }
        return properties
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }
}
