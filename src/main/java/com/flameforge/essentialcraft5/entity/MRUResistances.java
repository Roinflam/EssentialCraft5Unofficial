package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方块的 MRU 抗性表：聚集体给玩家造成矩阵损伤前，按连线上最大的抗性 r 以 1/⌊r⌋ 的概率放过（见 MRUPresenceEntity）。
 * <p>
 * 原作 ECUtils.MRU_RESISTANCES 用「翻译键 + meta」拼成的字符串当键（DummyData），每查一个方块都要拼一次字符串；
 * 这里改成 Block → float 的映射，内容来自服务端配置 machines.mru_presence.block_resistances（「方块 id=抗性」的列表），
 * 配置加载或热重载时重建一次，平时查表是一次 IdentityHashMap 查找。
 * <p>
 * 默认值照原作 StructureRegistry 的登记。原作区分 meta 的几项（强化玻璃、魔力护板、淡青护板、米斯莱护板只登记了 meta 0，即白色）
 * 在 1.20 里颜色是方块状态，这里按方块登记，所有颜色都算。还没移植的方块（护板、虚空石、MRUCUEC 各部件等）先写进默认值，
 * 移植后自动生效；注册表里找不到的条目直接忽略。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MRUResistances {

    /** 没列出的方块（包括空气）的抗性，同原作 */
    public static final float DEFAULT = 1F;

    /** 原作 StructureRegistry.registerBlockResistance 的登记，换成新注册名 */
    static final List<String> DEFAULTS = List.of(
            "essentialcraft5:fortified_stone=2",
            "essentialcraft5:fortified_glass=3",
            "essentialcraft5:magic_plating=5",
            "essentialcraft5:mithriline_plating=7",
            "essentialcraft5:pale_plating=10",
            "essentialcraft5:void_glass=15",
            "essentialcraft5:void_stone=18",
            "essentialcraft5:demonic_plating=36",
            "essentialcraft5:ec_controller=100",
            "essentialcraft5:ec_acceptor=100",
            "essentialcraft5:ec_balancer=100",
            "essentialcraft5:ec_ejector=100",
            "essentialcraft5:ec_holding_chamber=100",
            "essentialcraft5:ec_redstone_controller=100",
            "essentialcraft5:ec_state_checker=100");

    /** 服务端配置是否已加载（没加载时用默认值，开发环境里直接读配置会抛异常） */
    private static volatile boolean configLoaded;
    /** 解析好的表；null 表示要重建。配置热重载可能发生在别的线程，所以整表替换 */
    private static volatile Map<Block, Float> table;

    private MRUResistances() {
    }

    public static float get(BlockState state) {
        Map<Block, Float> current = table;
        if (current == null) {
            current = rebuild();
        }
        Float value = current.get(state.getBlock());
        return value == null ? DEFAULT : value;
    }

    /** 配置列表的元素校验：「命名空间:路径=非负数」 */
    public static boolean isValidEntry(Object entry) {
        return entry instanceof String text && parse(text) != null;
    }

    private static Map<Block, Float> rebuild() {
        var value = MRUPresenceEntity.Config.blockResistances;
        // value 为 null：模块的 init() 没接线，配置小节不存在
        List<? extends String> entries = value == null ? DEFAULTS : configLoaded ? value.get() : value.getDefault();
        Map<Block, Float> map = new IdentityHashMap<>();
        for (String entry : entries) {
            Entry parsed = parse(entry);
            if (parsed == null) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(parsed.id());
            // getValue 找不到时返回默认值空气，不能让空气也有抗性
            if (block != null && ForgeRegistries.BLOCKS.containsKey(parsed.id())) {
                map.put(block, parsed.resistance());
            }
        }
        table = map;
        return map;
    }

    private record Entry(ResourceLocation id, float resistance) {
    }

    private static Entry parse(String text) {
        int split = text.lastIndexOf('=');
        if (split <= 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(text.substring(0, split).trim());
        if (id == null) {
            return null;
        }
        try {
            float resistance = Float.parseFloat(text.substring(split + 1).trim());
            return Float.isFinite(resistance) && resistance >= 0F ? new Entry(id, resistance) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isOurServerConfig(ModConfig config) {
        return config.getModId().equals(EssentialCraft5.MODID) && config.getType() == ModConfig.Type.SERVER;
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (isOurServerConfig(event.getConfig())) {
            configLoaded = true;
            table = null;
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (isOurServerConfig(event.getConfig())) {
            configLoaded = true;
            table = null;
        }
    }

    @SubscribeEvent
    public static void onConfigUnloading(ModConfigEvent.Unloading event) {
        if (isOurServerConfig(event.getConfig())) {
            configLoaded = false;
            table = null;
        }
    }
}
