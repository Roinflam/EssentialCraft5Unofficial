package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CHoannaEventPacket;
import com.flameforge.essentialcraft5.network.S2CHoannaJoltPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 海纳尔维度的骨架（第六批 6a 框架 F6a，计划 tools/plan/batch6a.md §6.1）：各种键、「这是不是海纳尔」、GameTest 的测试覆盖、
 * 两个同步包的登记、强震声 {@code sound.deep_noise}。维度本身全是数据：
 * <ul>
 *   <li>维度类型 {@code data/essentialcraft5/dimension_type/hoanna.json}（静态，床能用、重生锚不能用、natural、不固定时间 → 昼夜天气跟主世界）；</li>
 *   <li>维度 {@code data/essentialcraft5/dimension/hoanna.json} 和 7 个群系、群系标签：{@code tools/gen/hoanna_biomes.py} 生成（改配色、群系参数改脚本再重跑，别手改生成物）；</li>
 *   <li>噪声设置 {@code essentialcraft5:hoanna} 与它用到的噪声、密度函数的副本：datagen {@code datagen/ECWorldgenProvider}（runData 生成，D1 A′：
 *       原版主世界的噪声 / 密度函数复制到 {@code essentialcraft5:hoanna/…}，地貌和主世界同坐标不重合，见 {@code worldgen/hoanna/HoannaTerrain}）。</li>
 * </ul>
 * 数据驱动的维度会自动加进旧存档（Forge 读档时把数据包维度并进 level.dat）。
 * <p>
 * <b>GameTest 服里有真的海纳尔世界</b>（合入实测 2026-09-29：Forge 的 runGameTestServer 走专用服 {@code Main} 的建世界流程，数据包维度照样并进来；
 * 原版 {@code GameTestServer.create} 那条空维度表的路 Forge 不走。{@code HoannaFrameworkTests.hoannaLevelInGameTest} 守着），种子每次随机、地形不固定。
 * 测试结构都摆在超平坦主世界：要「在测试结构里摆方块、又算在海纳尔」的测试用 {@link #setTestHoannaOverride} 把超平坦主世界当成海纳尔，
 * 用完一定 {@link #clearTestOverride}（写在 finally 里或测试最后一步；关服时也会清）；要固定种子的生成器、群系源、结构集用 {@code gametest/HoannaTestWorld}
 * （从资源解出维度 JSON）；真传送（{@code changeDimension}）之类可以直接用 {@code server.getLevel(LEVEL)}，但别依赖那里的地形、用完把玩家 / 强制加载清掉。
 */
public final class ECHoanna {

    public static final ResourceLocation ID = EssentialCraft5.id("hoanna");
    /** 世界（ServerLevel / ClientLevel 的 dimension()） */
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);
    /** 维度（level.dat 里的 LevelStem） */
    public static final ResourceKey<LevelStem> LEVEL_STEM = ResourceKey.create(Registries.LEVEL_STEM, ID);
    public static final ResourceKey<DimensionType> DIMENSION_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, ID);
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, ID);
    /** 维度效果 id（维度类型 JSON 的 effects；客户端 ECHoannaSky 的 HoannaEffects 按它注册，没注册时客户端退回主世界效果） */
    public static final ResourceLocation EFFECTS = ID;

    // ------------------------------------------------------------------
    // 7 个群系（D2 A：6 个 EC 群系 + 海纳尔之海；中英名见 tools/lang/glossary.md，群系 JSON 由 tools/gen/hoanna_biomes.py 生成）
    // ------------------------------------------------------------------

    public static final ResourceKey<Biome> FROZEN_CORRUPTION = biome("frozen_corruption");
    public static final ResourceKey<Biome> MAGIC_CORRUPTION = biome("magic_corruption");
    public static final ResourceKey<Biome> CHAOS_CORRUPTION = biome("chaos_corruption");
    public static final ResourceKey<Biome> DREADLANDS = biome("dreadlands");
    public static final ResourceKey<Biome> HOANNA_DESERT = biome("hoanna_desert");
    public static final ResourceKey<Biome> SHADOW_CORRUPTION = biome("shadow_corruption");
    public static final ResourceKey<Biome> HOANNA_SEA = biome("hoanna_sea");
    /** 全部 7 个，顺序 = 维度 JSON 里 multi_noise 参数表的顺序（冷到热，暗影、海在后） */
    public static final List<ResourceKey<Biome>> BIOMES = List.of(FROZEN_CORRUPTION, MAGIC_CORRUPTION, CHAOS_CORRUPTION, DREADLANDS,
            HOANNA_DESERT, SHADOW_CORRUPTION, HOANNA_SEA);

    /** 7 个海纳尔群系（{@code data/essentialcraft5/tags/worldgen/biome/is_hoanna.json}）；不进任何 {@code #minecraft:is_*} / {@code #forge:is_*} */
    public static final TagKey<Biome> IS_HOANNA = TagKey.create(Registries.BIOME, EssentialCraft5.id("is_hoanna"));
    /** 6 个陆地群系（不含海纳尔之海）；城镇废墟只在这里生成 */
    public static final TagKey<Biome> HOANNA_LAND = TagKey.create(Registries.BIOME, EssentialCraft5.id("hoanna_land"));

    /**
     * 地震强震声（D7；M3 播，AMBIENT 类别、只给本地玩家，建议音量 0.5~0.7、音高 1）。
     * <b>没有拷原作的 deep_noise.ogg</b>：来源核实不了——46 秒的立体声环境录音、没有 Vorbis 注释，和两个已确认来自 freesfx 的音效
     * 同一次提交加入（Modbder，4.3 版 ceabf8b，2014-12-08），编码器也和原作自己别的音效不同；原作从没播放过它；而且 46 秒、立体声（不随位置衰减）
     * 也不适合每 6.7 秒一次的强震。定义（{@code tools/sounds/echoanna.json}）指向原版事件 {@code minecraft:entity.generic.explode}，
     * 在定义里乘了音量 0.25、音高 0.5（原版对 event 引用的音量 / 音高是相乘的，{@code SoundManager} L264），代码按 0.6 播 ≈ 原计划的 0.15 / 0.5。
     * 以后有了能用的录音，把定义换成 {@code essentialcraft5:sound/deep_noise} 文件，代码不用改。
     */
    public static final RegistryObject<SoundEvent> DEEP_NOISE = ECSounds.register("sound.deep_noise");

    /** GameTest 里被当成海纳尔的世界（见类注释）；只在服务端主线程读写 */
    @Nullable
    private static volatile ServerLevel testOverride;

    private ECHoanna() {
    }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, EssentialCraft5.id(name));
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：登记两个 S2C 包、关服时清测试覆盖 */
    public static void init() {
        // 处理器只在客户端调（registerS2C 已套 DistExecutor），lambda 体里的 HoannaClientEvents 到执行时才加载，专用服碰不到
        ECNetwork.registerS2C(S2CHoannaEventPacket.class, S2CHoannaEventPacket::encode, S2CHoannaEventPacket::decode,
                packet -> HoannaClientEvents.onEventPacket(packet));
        ECNetwork.registerS2C(S2CHoannaJoltPacket.class, S2CHoannaJoltPacket::encode, S2CHoannaJoltPacket::decode,
                packet -> HoannaClientEvents.onJoltPacket(packet));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, event -> testOverride = null);
    }

    // ------------------------------------------------------------------
    // 是不是海纳尔
    // ------------------------------------------------------------------

    /**
     * 这个世界是不是海纳尔：两端都能用（客户端比较 {@code ClientLevel.dimension()}）；GameTest 里 {@link #setTestHoannaOverride} 设过的世界也算。
     * 天空画面另外按 {@code level.effects()} 判断（M3），这里只看维度。
     */
    public static boolean isHoanna(@Nullable Level level) {
        if (level == null) {
            return false;
        }
        ServerLevel override = testOverride;
        return level == override || LEVEL.equals(level.dimension());
    }

    /** 只按维度键判断（不认测试覆盖）：给拿不到 Level 对象的地方（包、存档数据）用 */
    public static boolean isHoanna(@Nullable ResourceKey<Level> dimension) {
        return LEVEL.equals(dimension);
    }

    /**
     * 服务端的海纳尔世界：有测试覆盖（而且属于这个服）时返回它，否则 {@code server.getLevel(LEVEL)}。
     * 服主用数据包删掉了这个维度时是 null——调用方一律要判空。
     */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel override = testOverride;
        if (override != null && override.getServer() == server) {
            return override;
        }
        return server.getLevel(LEVEL);
    }

    // ------------------------------------------------------------------
    // GameTest 覆盖
    // ------------------------------------------------------------------

    /**
     * 只给 GameTest 用：把这个世界（一般是 {@code helper.getLevel()}，超平坦主世界）当成海纳尔——{@link #isHoanna(Level)}、{@link #level} 都认它。
     * 同一时间只有一个；用完必须 {@link #clearTestOverride()}（多刻的测试在最后一步、单刻的写 finally）。GameTest 是在同一个服里跑一批的，
     * 忘了清会让后面所有测试的主世界都算海纳尔（辐射、事件都会跑起来）；关服时也会清。
     */
    public static void setTestHoannaOverride(ServerLevel level) {
        if (testOverride != null && testOverride != level) {
            EssentialCraft5.LOGGER.warn("[海纳尔] 测试覆盖被另一个世界顶掉了：上一个测试没清？ {} -> {}", testOverride.dimension().location(), level.dimension().location());
        }
        testOverride = level;
    }

    /** 清掉 {@link #setTestHoannaOverride} 设的世界（没设过也可以调） */
    public static void clearTestOverride() {
        testOverride = null;
    }

    /** 现在被当成海纳尔的测试世界（没有时 null） */
    @Nullable
    public static ServerLevel testOverride() {
        return testOverride;
    }
}
