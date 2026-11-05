package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.InquisitoriumBeaconBlock;
import com.flameforge.essentialcraft5.block.InquisitoriumDaisBlock;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostCommands;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13e-S3「审判庭前哨」的注册类（batch13.md §6.4、§9.5；HS §2.4.3）。
 * <ul>
 *   <li>方块 {@link #INQUISITORIUM_DAIS} 信标座：前哨中央高台的装饰方块，硬度照审判庭信标、亮度同信标，掉本体（生存里只在前哨挖得到）。</li>
 *   <li>方块标签 {@link #OUTPOST_CLEARABLE}（{@code tools/tags/ecinquisitoriumoutpost.json}）：生成前哨时场地里能削平、能铺地板的「天然方块」，数据包可改。</li>
 *   <li>开发指令 {@code /ec5 dev hoanna_census}（权限 4，{@code worldgen/hoanna/outpost/OutpostCommands}）：暗影区普查。</li>
 *   <li>服务端配置 {@code general.inquisitorium_outpost}（{@link Config}）：解码器先不先找前哨。</li>
 * </ul>
 * 结构类型 {@code inquisitorium_outpost} 与 4 种拼件在 {@code worldgen/hoanna/outpost/OutpostStructures}（{@code ECHoannaStructures.init()} 调它），
 * 结构 / 结构集 JSON、群系标签 {@code has_structure/inquisitorium_outpost}（= {@code #inquisitorium_sites}）在数据包里。战利品表
 * {@code chests/inquisitorium_outpost} 由 {@code tools/gen/hoanna_loot_outpost.py} 出。第十九章解码器改指前哨在 {@code blockentity/GlitchyCoreDecoder.SiteSearch}。
 * 文字在 {@code tools/lang/keys/ecinquisitoriumoutpost.json}，音效 {@code tools/sounds/ecinquisitoriumoutpost.json}（没有）。
 */
public final class ECInquisitoriumOutpost {

    /** 信标座：硬度照审判庭信标（1 / 1），亮度同信标 */
    public static final RegistryObject<InquisitoriumDaisBlock> INQUISITORIUM_DAIS = ECBlocks.register("inquisitorium_dais",
            () -> new InquisitoriumDaisBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE).lightLevel(state -> InquisitoriumBeaconBlock.LIGHT).strength(1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of(BlockSpec.cubeAll("block/inquisitorium_dais")));

    /** 生成前哨时能削平、铺地板、补地基的天然方块（另外「可替换、不含液体」的一律算，有方块实体的一律不算，见 {@code OutpostPieces.clearable}） */
    public static final TagKey<Block> OUTPOST_CLEARABLE = TagKey.create(Registries.BLOCK, EssentialCraft5.id("outpost_clearable"));

    private ECInquisitoriumOutpost() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置、开发指令在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECCommands.addSubcommand(OutpostCommands::build);
    }

    /** 服务端配置 {@code general.inquisitorium_outpost} */
    public static final class Config {

        /** 默认：解码器先找前哨 */
        public static final boolean DEFAULT_DECODER_TARGETS_OUTPOST = true;

        private static ForgeConfigSpec.BooleanValue decoderTargetsOutpost;
        @Nullable
        private static volatile Boolean testDecoderTargetsOutpost;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Inquisitorium Outposts in Hoanna's Shadow Corrupted Land.", "海纳尔暗影腐化之地里的审判庭前哨").push("inquisitorium_outpost");
            decoderTargetsOutpost = builder.comment("When a Radiating Chamber decodes a Glitchy Core, the coordinates point at the nearest Inquisitorium Outpost "
                                    + "within the search radius first; only if there is none do they fall back to the nearest Shadow Corrupted Land. "
                                    + "Turn off to always point at the nearest Shadow Corrupted Land.",
                            "辐射仓解码故障核心时，坐标先指搜索半径内最近的一座审判庭前哨，没有才退回最近的暗影腐化之地；关掉就一律指最近的暗影腐化之地")
                    .define("decoder_targets_outpost", DEFAULT_DECODER_TARGETS_OUTPOST);
            builder.pop();
        }

        /** 解码器先不先找前哨（测试覆盖值 → 配置；没加载时默认值） */
        public static boolean decoderTargetsOutpost() {
            Boolean override = testDecoderTargetsOutpost;
            if (override != null) {
                return override;
            }
            return decoderTargetsOutpost == null ? DEFAULT_DECODER_TARGETS_OUTPOST : ECConfig.get(decoderTargetsOutpost);
        }

        /** GameTest 用：null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestDecoderTargetsOutpost(@Nullable Boolean value) {
            testDecoderTargetsOutpost = value;
        }
    }
}
