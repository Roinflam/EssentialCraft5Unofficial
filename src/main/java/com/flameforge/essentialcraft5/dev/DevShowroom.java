package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.FancyMaterial;
import com.flameforge.essentialcraft5.block.FancyPattern;
import com.flameforge.essentialcraft5.block.MagicalDisplayBlock;
import com.flameforge.essentialcraft5.block.MimicBlock;
import com.flameforge.essentialcraft5.block.RedstoneTransmitterBlock;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.WeaponMakerBlockEntity;
import com.flameforge.essentialcraft5.client.GunsClient;
import com.flameforge.essentialcraft5.client.render.BeamEffects;
import com.flameforge.essentialcraft5.client.render.MatrixHitEffects;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.registry.ECCreativeTabs;
import com.flameforge.essentialcraft5.registry.ECDecorations;
import com.flameforge.essentialcraft5.registry.ECDigger;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalDisplayBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.client.guide.GuideClient;
import com.flameforge.essentialcraft5.client.guide.GuideClientConfig;
import com.flameforge.essentialcraft5.client.guide.GuideIndexCheck;
import com.flameforge.essentialcraft5.client.guide.GuideScreen;
import com.flameforge.essentialcraft5.client.guide.GuideScreenOpener;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECGuide;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.registry.ECMirror;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECReactor;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import com.flameforge.essentialcraft5.registry.ECStorage;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 开发用的「展示厅」：只在 {@code -Dessentialcraft5.showroom=true} 时生效（./gradlew runClient -PecShowroom）。
 * <p>
 * 自动新建超平坦创造世界（白天、无刷怪），把 ECBlocks 里登记的<b>所有</b>方块按网格摆出来（每排 {@value #PER_ROW} 个），
 * 每排拍一张（最后一排是一个 15000 MRU 的聚集体，看光线渲染）；再在方块排北边摆几个特殊场景（拟态伪装、红石传递器六个朝向、魔力显示屏三种方式、
 * 魔力镜送东西、成形的特斯拉线圈打僵尸、成形的反应堆；第五批：盔甲架拿枪 + 5 种光束 + 第一人称手持 / 狙击镜、花纹墙、花纹拟态伪装对照、地脉投影仪），
 * 隐藏界面、飞在空中逐个拍（要 HUD 的几张在 SceneShot.client 里打开）；再把每台有界面的机器（MRUBlockEntity 且 createMenu 不为 null）
 * 和几个额外界面（魔力箱、手持过滤器、手持合成框架、场景里的两台投影仪、创造栏「装饰」页）依次打开拍一张；然后按手册的三种虚拟宽度各拍一轮研究手册
 * （游戏界面缩放「自动」= 3；手册跟游戏 427×240、手册自动挑 2 倍 640×360、开发入口 1 倍 1280×720 只看版式；
 * 首页、各种块、结构图逐层、打勾、搜索、查阅后回物品栏、条件开关对照，640 那一轮另拍开场 / 查阅短开场 / 收场关键帧和换倍数的阅读位置锚定，
 * 见 {@link #guideSteps}）；最后退出游戏。
 * 只想看手册时加 {@code -PecShowroomGuide}（跳过方块排、场景和机器界面，约 1 分钟）；只看第六批 6a 加 {@code -PecShowroomHoanna}，
 * 只看第六批 6b（建筑魔杖、传送器与熔炉、终极产能器、虚空箱、高级过滤器、无尽道具，见 {@link DevShowroomVoid}）加 {@code -PecShowroom6b}；
 * 只看第七批（恶魔与交易、余烬锻炉与余烬元素剑、水晶柱与灵魂五芒星 2 / 3 档、虚空套装、灵魂刻刀，见 {@link DevShowroomDemon}）加 {@code -PecShowroom7}；
 * 只看第八批（全息投影台与 Boss、遥控器与二分枪、宅男套装与反重力板，见 {@link DevShowroomHologram}）加 {@code -PecShowroom8}；
 * 只看第九批（地脉凝聚器，见 {@link DevShowroomCondenser}）加 {@code -PecShowroom9}；
 * 只看第十批 10a（饰品封禁场与宅男终端、附录「与原作的不同」，见 {@link DevShowroomSeal}）加 {@code -PecShowroom10a}；
 * 只看第十批 10b（风之试炼、坍缩、透镜与嵌刻、二分枪对首领、轨道锁定、全息诱饵、四个新效果，见 {@link DevShowroomTrials}）加 {@code -PecShowroom10b}；
 * 只看第十批 10c（审判庭、终端与模块、力场穹顶，见 {@link DevShowroomModules}）加 {@code -PecShowroom10c}。
 * 只看 JEI 里本模组的几个分类加 {@code -PecShowroomJei}（挂上完整 JEI，进世界后交给 compat/jei 的 DevJeiShowroom 逐个打开拍照，拍完回到这里退出）。
 * 机器摆好后 MRU 充到一半，界面上能看到数值。快捷栏放前 9 个物品。截图在 run/screenshots/ec5_*.png。
 * <p>
 * 全程不发按键、不动鼠标，不影响正在用电脑的人（窗口被挡住也照常跑，已关掉失焦暂停）。
 * 新移植的方块和机器自动出现在方块排和界面里；要看成形结构、链接、实体互动这类「摆出来才看得到」的效果，在 {@link #buildScenes} 加场景。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class DevShowroom {

    private static final boolean ENABLED = Boolean.getBoolean("essentialcraft5.showroom");
    /** 只拍研究手册（-PecShowroomGuide）：跳过方块排、场景、机器界面 */
    private static final boolean GUIDE_ONLY = Boolean.getBoolean("essentialcraft5.showroom.guideOnly");
    /** 只拍第六批 6a 海纳尔部分（-PecShowroomHoanna）：跳过方块排、前五批场景和机器界面，手册只拍海纳尔相关几页 */
    static final boolean HOANNA_ONLY = Boolean.getBoolean("essentialcraft5.showroom.hoannaOnly");
    /** 只拍第六批 6b 虚空机器（-PecShowroom6b）：跳过方块排和前几批场景，6b 方块另摆一排，手册只拍 6b 相关几页 */
    static final boolean VOID_ONLY = Boolean.getBoolean("essentialcraft5.showroom.voidOnly");
    /** 只拍第七批虚空与恶魔（-PecShowroom7）：跳过方块排和前几批场景，本批方块另摆一排，手册只拍本批相关几页 */
    static final boolean DEMON_ONLY = Boolean.getBoolean("essentialcraft5.showroom.demonOnly");
    /** 只拍第八批全息与计算机（-PecShowroom8）：跳过方块排和前几批场景，手册只拍本批相关几页（含首页「主线已完成」） */
    static final boolean HOLOGRAM_ONLY = Boolean.getBoolean("essentialcraft5.showroom.hologramOnly");
    /** 只拍第九批地脉凝聚器（-PecShowroom9）：跳过方块排和前几批场景，手册只拍本批相关几页 */
    static final boolean CONDENSER_ONLY = Boolean.getBoolean("essentialcraft5.showroom.condenserOnly");
    /** 只拍第十批 10a（-PecShowroom10a）：饰品封禁场的画面和终端、封禁场与附录的手册页；全量展示厅不跑封禁场部分（见 {@link DevShowroomSeal}） */
    static final boolean SEAL_ONLY = Boolean.getBoolean("essentialcraft5.showroom.sealOnly");
    /** 只拍第十批 10b（-PecShowroom10b）：本批的场景、提示框与界面、手册页；全量展示厅只拍本批手册页（见 {@link DevShowroomTrials}） */
    static final boolean TRIALS_ONLY = Boolean.getBoolean("essentialcraft5.showroom.trialsOnly");
    /** 只拍第十批 10c（-PecShowroom10c）：本批的场景、提示框与界面、手册页；全量展示厅只拍本批手册页（见 {@link DevShowroomModules}） */
    static final boolean MODULES_ONLY = Boolean.getBoolean("essentialcraft5.showroom.modulesOnly");
    /** 只拍第十一批（-PecShowroom11）：MRU 转换器、修过贴图坐标的 OBJ 机器、本批的手册页（见 {@link DevShowroomBatch11}） */
    static final boolean BATCH11_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch11Only");
    /**
     * 只拍第十二批（-PecShowroom12）：三台机器、九样物品、本批的界面和手册页（见 {@link DevShowroomBatch12}）。
     * 本批的截图名都以 {@code b12_} 开头、不加 01_ ~ 04_ 的分类前缀，存成 {@code ec5_b12_*.png}
     */
    static final boolean BATCH12_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch12Only");
    /**
     * 只拍第十三批 13a（-PecShowroom13a）：淬炼台与试炼、凝结、同调和终端、本批的界面和手册页（见 {@link DevShowroomBatch13a}）。
     * 截图名都以 {@code b13a_} 开头、不加分类前缀，存成 {@code ec5_b13a_*.png}
     */
    static final boolean BATCH13A_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch13aOnly");
    /**
     * 只拍第十三批 13b（-PecShowroom13b）：四套盔甲与电量条、空中跳、定罪和断罪、本批的提示框和手册页（见 {@link DevShowroomBatch13b}）。
     * 截图名都以 {@code b13b_} 开头、不加分类前缀，存成 {@code ec5_b13b_*.png}
     */
    static final boolean BATCH13B_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch13bOnly");
    /**
     * 只拍第十三批 13c（-PecShowroom13c）：新怪物和两只首领的关键姿势、精英、矩阵之影三相与镜影、魔像护盾电弧、本批的提示框和异兽录（见 {@link DevShowroomBatch13c}）。
     * 截图名都以 {@code b13c_} 开头、不加分类前缀，存成 {@code ec5_b13c_*.png}
     */
    static final boolean BATCH13C_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch13cOnly");
    /**
     * 只拍第十三批 13d（-PecShowroom13d）：海纳尔特产方块、巢穴核心、里程碑、八种本地怪的关键姿势和精英、七个群系与三种中景观、地脉风暴和落晶、
     * 大潮光幕、热点细尘、勘测图光柱，本批的提示框和手册页（见 {@link DevShowroomBatch13d}）。截图名都以 {@code b13d_} 开头、不加分类前缀，存成 {@code ec5_b13d_*.png}
     */
    static final boolean BATCH13D_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch13dOnly");
    /**
     * 只拍第十三批 13e（-PecShowroom13e）：三座海纳尔结构（直接摆在主世界）、宝库谜题、废城守望者和炮台、渊潮领主、天灾之心护盾期与四相、
     * 4 档灵魂五芒星，本批的手册页（见 {@link DevShowroomBatch13e}）。截图名都以 {@code b13e_} 开头、不加分类前缀，存成 {@code ec5_b13e_*.png}
     */
    static final boolean BATCH13E_ONLY = Boolean.getBoolean("essentialcraft5.showroom.batch13eOnly");
    /** 只拍各章篇首的题记（-PecShowroomQuote，同时是只拍手册）：最长、最短、带缺字、公文体、口语体几种题记在三种手册宽度下的排版 */
    static final boolean QUOTE_ONLY = Boolean.getBoolean("essentialcraft5.showroom.quoteOnly");
    /** 只拍立体结构图（-PecShowroomStruct，同时是只拍手册）：{@link #structure3dGuidePages} 那一组 */
    static final boolean STRUCT_ONLY = Boolean.getBoolean("essentialcraft5.showroom.structOnly");
    /**
     * 只拍 JEI（-PecShowroomJei）：进世界后停在 {@link Stage#JEI}，由 compat/jei 的 DevJeiShowroom 拍完调 {@link #jeiFinished}。
     * 这里不引用 JEI 的类（没装 JEI 时这个类照样加载），超时没等到就照常退出
     */
    public static final boolean JEI_ONLY = Boolean.getBoolean("essentialcraft5.showroom.jeiOnly");
    /** 只拍 JEI 时最多等这么多刻（2 分钟）：JEI 没加载、插件出错时不至于一直挂着 */
    private static final int JEI_TIMEOUT = 2400;
    private static volatile boolean jeiFinished;
    private static final String SAVE = "ec5_showroom";
    private static final int PER_ROW = 6;
    private static final int SPACING_X = 3;
    private static final int SPACING_Z = 10;
    /** 特殊场景区在方块排北边这么多格（方块排的镜头朝南，拍不到这里；场景的镜头朝北，背后空无一物） */
    private static final int SCENE_Z = -40;
    /** 相邻两个场景的 X 间距（镜头离场景 6~10 格，隔壁场景出不了画面） */
    private static final int SCENE_SPACING = 24;
    /** 刚传送过去的第一张场景图要等区块发过来、建好网格 */
    static final int FIRST_SCENE_WAIT = 120;
    /**
     * 带 {@code ready} 的场景等够 delay 以后最多再等这么多刻（30 秒），超时照拍、打一行警告。
     * 固定刻数不够用：服务端「Can't keep up」落后几秒时，传送和发区块都跟着晚（6a 集成时 04_scene_modern_shafts 两张就这样拍成了空天）
     */
    static final int READY_TIMEOUT = 600;

    private enum Stage { WAIT_TITLE, WAIT_WORLD, ROW, ROW_SHOT, SCENE, SCENE_SHOT, SCREEN, SCREEN_OPEN, SCREEN_SHOT, GUIDE, GUIDE_SHOT, JEI, DONE }

    /**
     * 研究手册的一步：先做 {@code action}（客户端主线程），等 {@code delay} 刻，{@code shot} 不为 null 时拍一张。
     * 截图名是 {@code 03_guide_<轮>_<shot>}（轮 = {@link GuideRound#name}，按手册的虚拟宽度：v427 / v640 / v1280）；
     * {@code shot} 以 {@code 03_} 开头时原样当截图名（开场 / 查阅短开场 / 收场关键帧：{@code 03_guide_intro_<毫秒>} 等）。
     */
    private record GuideStep(@Nullable String shot, Consumer<Minecraft> action, int delay) { }

    /**
     * 研究手册的一轮：游戏界面缩放统一设成「自动」（窗口 1280×720 时实际 3 倍），手册的倍数走展示厅入口
     * {@link GuideScreen#setShowroomGuideScale}（不写配置）：-1 跟游戏、0 自动、1 开发入口（只看版式，不看字）
     *
     * @param name       截图名里的轮名（手册的虚拟宽度）
     * @param guideScale 展示厅入口的值
     */
    private record GuideRound(String name, int guideScale) { }

    /** 三轮：427×240（跟游戏 3 倍）、640×360（自动应挑出 2 倍）、1280×720（1 倍只看版式） */
    private static final List<GuideRound> GUIDE_ROUNDS = List.of(new GuideRound("v427", -1), new GuideRound("v640", 0),
            new GuideRound("v1280", 1));
    /** 开场 / 收场关键帧、换倍数锚定在这一轮拍（手册自动挑出的默认档） */
    private static final String GUIDE_KEY_ROUND = "v640";
    /** 开场关键帧（开场时间轴毫秒，见 GuideIntro 的分镜：汇聚、法阵描线、五芒星、成形、展开、劈四角、卡角、光幕、显影、就绪、结束） */
    private static final int[] GUIDE_INTRO_FRAMES = {200, 450, 620, 760, 900, 1000, 1100, 1250, 1400, 1520, 1800};
    /** 查阅短开场关键帧（开场时间轴毫秒，短开场从 800 起） */
    private static final int[] GUIDE_QUICK_FRAMES = {820, 900, 1000, 1100};
    /** 收场关键帧（收场毫秒：封存、合卷、收线火花、光线收拢、法阵缩进光点、光粒散开） */
    private static final int[] GUIDE_CLOSE_FRAMES = {120, 260, 420, 520, 620, 700, 800, 900, 960};
    /** 当前这一轮的轮名（截图名用；每轮第一步设） */
    private static String guideRound = GUIDE_ROUNDS.get(0).name();

    /**
     * 特殊场景的一张截图：{@code client}（客户端，可为 null）→ {@code prepare}（服务端，可为 null）→ 玩家飞起来、眼睛摆到 {@code eye} 看向 {@code target}
     * → 等 {@code delay} 刻再拍。同一场景拍几张时，后面几张 prepare 传 null。
     * 每张开始前先恢复默认（藏 HUD、关掉强制开镜），{@code client} 在那之后跑，要拍 HUD / 第一人称手持 / 开镜的在这里改回来。
     * {@code eye} 为 null 时不替它站位（prepare 自己算好位置再调 {@link #aim}，海纳尔那几张要等区块加载完才知道地面多高）；
     * 名字以 {@code 05_} 开头的原样当截图名（海纳尔天空与画面，{@link DevShowroomHoanna}），以 {@code 02_} 开头的也原样（场景里当场打开的界面，
     * 如第七批的恶魔交易界面，{@link DevShowroomDemon}；下一张场景的 client 负责关掉），以 {@code b12_} 开头的也原样（第十二批），其余前面加 {@code 04_scene_}。
     * {@code ready}（客户端每刻问一次，可为 null）：等够 {@code delay} 刻之后还要等它返回 true 才拍，最多再等 {@link #READY_TIMEOUT} 刻
     * （例：{@link ShowroomChunkWait} 等镜头周围的区块收齐、编译完；6a 审查 M6-1）。
     */
    record SceneShot(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                     @Nullable Consumer<Minecraft> client, @Nullable Predicate<Minecraft> ready) {
        SceneShot(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay) {
            this(name, prepare, eye, target, delay, null, null);
        }

        SceneShot(String name, @Nullable Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target, int delay,
                  @Nullable Consumer<Minecraft> client) {
            this(name, prepare, eye, target, delay, client, null);
        }
    }

    /** 一张界面截图：{@code prepare}（服务端：站位、往手上放东西）→ 5 刻后 {@code open}（服务端开界面）→ 30 刻后拍 */
    record ScreenShot(String name, Consumer<ServerPlayer> prepare, Consumer<ServerPlayer> open) { }

    /** 「打勾」那几张用的流程进度：每轮开头撤掉、拍打勾之前再发，每轮都从没打勾开始（打勾电弧每轮都拍得到） */
    private static final List<String> GUIDE_PROGRESS = List.of("main/root", "guide/elemental_gem", "guide/absorber_parts", "main/soul_stone", "guide/first_kill");

    private static Stage stage = Stage.WAIT_TITLE;
    private static int timer;
    /**
     * 服务端的 {@link #setup} 跑完了（6a 审查 M1-7）。setup 在服务端线程排队执行，只拍海纳尔时要找取景点、预生成海纳尔区块，要好几秒；
     * 以前客户端不等它，前几张场景图的传送排在 setup 后面还没执行就拍了（04_scene_hoanna_flora 等 4 张是空草地），{@link #scenes} 也可能还在往里加。
     */
    private static volatile boolean setupDone;
    private static BlockPos origin;
    private static int rows;
    private static int rowIndex;
    static final List<SceneShot> scenes = new ArrayList<>();
    private static int sceneIndex;
    static final List<ScreenShot> screens = new ArrayList<>();
    private static int machineScreens;
    private static int screenIndex;
    private static final List<GuideStep> guideSteps = new ArrayList<>();
    private static int guideIndex;
    private static int guideShots;

    private DevShowroom() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        timer++;
        switch (stage) {
            case WAIT_TITLE -> {
                // 全新的 run/ 第一次启动先出无障碍设置的欢迎页（options.txt 还没有 onboardAccessibility:false），关掉它进标题界面
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                }
                // 等 Forge 的加载遮罩消失、停在标题界面 1 秒后再建世界
                if (mc.getOverlay() == null && mc.screen instanceof TitleScreen && timer > 20) {
                    prepareClient(mc);
                    createWorld(mc);
                    next(Stage.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                if (mc.level != null && mc.player != null && server != null && mc.screen == null && timer > 40) {
                    server.execute(() -> {
                        try {
                            setup(server);
                        } finally {
                            setupDone = true;
                        }
                    });
                    next(GUIDE_ONLY ? Stage.GUIDE : JEI_ONLY ? Stage.JEI : Stage.ROW);
                }
            }
            case JEI -> {
                if (jeiFinished || timer > JEI_TIMEOUT) {
                    if (!jeiFinished) {
                        EssentialCraft5.LOGGER.warn("[showroom] 等了 {} 刻 JEI 那边还没拍完（没挂 JEI，或插件出错），退出", JEI_TIMEOUT);
                    }
                    next(Stage.DONE);
                }
            }
            case ROW -> {
                if (timer > 20 && setupDone) {
                    // 界面检查只看开着界面的那些图，方块排不拍
                    if (rowIndex >= rows || GuiAudit.ENABLED) {
                        next(Stage.SCENE);
                    } else {
                        int row = rowIndex;
                        server.execute(() -> lookAtRow(server, row));
                        next(Stage.ROW_SHOT);
                    }
                }
            }
            case ROW_SHOT -> {
                if (timer > 40) {
                    shot(mc, String.format("01_row_%02d", rowIndex));
                    rowIndex++;
                    next(Stage.ROW);
                }
            }
            case SCENE -> {
                if (sceneIndex >= scenes.size()) {
                    mc.options.hideGui = false;
                    GunsClient.showroomForceScope = false;
                    DevShowroomHologram.clientFinish(mc);
                    DevShowroomCondenser.clientFinish();
                    DevShowroomTrials.clientFinish(mc);
                    DevShowroomModules.clientFinish(mc);
                    DevShowroomBatch13b.clientFinish(mc);
                    DevShowroomBatch13c.clientFinish(mc);
                    DevShowroomBatch13d.clientFinish(mc);
                    DevShowroomBatch13e.clientFinish(mc);
                    server.execute(() -> finishScenes(server));
                    next(Stage.SCREEN);
                } else if (timer > 5) {
                    mc.options.hideGui = true;
                    GunsClient.showroomForceScope = false;
                    SceneShot scene = scenes.get(sceneIndex);
                    if (scene.client() != null) {
                        scene.client().accept(mc);
                    }
                    server.execute(() -> {
                        ServerPlayer player = player(server);
                        if (scene.prepare() != null) {
                            scene.prepare().accept(player);
                        }
                        if (scene.eye() != null && scene.target() != null) {
                            aim(player, scene.eye(), scene.target());
                        }
                    });
                    next(Stage.SCENE_SHOT);
                }
            }
            case SCENE_SHOT -> {
                SceneShot scene = scenes.get(sceneIndex);
                if (!mc.options.hideGui) {
                    // 开着 HUD 拍的场景：等的这些刻里每刻清掉右上角的提示（新配方、进度、教程），prepare 里换装备、开试炼时弹出来的也清得掉。
                    // 截图取的是上一帧，所以要在拍之前的刻里就清
                    mc.getToasts().clear();
                }
                if (timer > scene.delay() && sceneReady(mc, scene)) {
                    shot(mc, scene.name().startsWith("05_") || scene.name().startsWith("02_") || scene.name().startsWith("b12_")
                            || scene.name().startsWith("b13a_") || scene.name().startsWith("b13b_") || scene.name().startsWith("b13c_")
                            ? scene.name() : "04_scene_" + scene.name());
                    sceneIndex++;
                    next(Stage.SCENE);
                }
            }
            case SCREEN -> {
                if (screenIndex >= screens.size()) {
                    restoreCreativeTab();
                    next(Stage.GUIDE);
                } else if (timer > 10) {
                    ScreenShot screen = screens.get(screenIndex);
                    server.execute(() -> screen.prepare().accept(player(server)));
                    next(Stage.SCREEN_OPEN);
                }
            }
            case SCREEN_OPEN -> {
                if (timer > 5) {
                    ScreenShot screen = screens.get(screenIndex);
                    server.execute(() -> screen.open().accept(player(server)));
                    next(Stage.SCREEN_SHOT);
                }
            }
            case SCREEN_SHOT -> {
                if (timer > 30) {
                    String screenName = screens.get(screenIndex).name();
                    shot(mc, screenName.startsWith("b12_") || screenName.startsWith("b13a_") || screenName.startsWith("b13b_")
                            || screenName.startsWith("b13c_") ? screenName
                            : "02_gui_" + screenName);
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    screenIndex++;
                    next(Stage.SCREEN);
                }
            }
            case GUIDE -> {
                if (guideSteps.isEmpty()) {
                    buildGuideSteps(server);
                }
                if (guideIndex >= guideSteps.size()) {
                    next(Stage.DONE);
                } else if (timer > 2) {
                    guideSteps.get(guideIndex).action().accept(mc);
                    next(Stage.GUIDE_SHOT);
                }
            }
            case GUIDE_SHOT -> {
                GuideStep step = guideSteps.get(guideIndex);
                if (timer > step.delay()) {
                    if (step.shot() != null) {
                        // 第十二批、第十三批 13a / 13b / 13c 的手册页：b12_<名字> → b12_guide_<轮>_<名字>，b13a_、b13b_、b13c_ 同理
                        shot(mc, step.shot().startsWith("03_") ? step.shot()
                                : step.shot().startsWith("b12_") ? "b12_guide_" + guideRound + "_" + step.shot().substring(4)
                                : step.shot().startsWith("b13a_") ? "b13a_guide_" + guideRound + "_" + step.shot().substring(5)
                                : step.shot().startsWith("b13b_") ? "b13b_guide_" + guideRound + "_" + step.shot().substring(5)
                                : step.shot().startsWith("b13c_") ? "b13c_guide_" + guideRound + "_" + step.shot().substring(5)
                                : "03_guide_" + guideRound + "_" + step.shot());
                        guideShots++;
                    }
                    guideIndex++;
                    next(Stage.GUIDE);
                }
            }
            case DONE -> {
                if (timer > 10) {
                    EssentialCraft5.LOGGER.info("[showroom] 完成：{} 排方块，{} 张场景图，{} 个界面（其中机器 {} 个），{} 张手册，退出",
                            rows, sceneIndex, screens.size(), machineScreens, guideShots);
                    mc.stop();
                }
            }
        }
    }

    /** 只拍 JEI 时：已经进了世界、摆好了场地，JEI 那边可以开始拍 */
    public static boolean jeiStageReady() {
        return ENABLED && JEI_ONLY && stage == Stage.JEI && timer > 20 && setupDone;
    }

    /** JEI 那边拍完了，下一刻退出 */
    public static void jeiFinished() {
        jeiFinished = true;
    }

    /** 截图存到 run/screenshots/ec5_{name}.png（JEI 那边也用这个，命名一致） */
    public static void shotNamed(Minecraft mc, String name) {
        shot(mc, name);
    }

    private static void next(Stage nextStage) {
        stage = nextStage;
        timer = 0;
    }

    /** 场景的 {@code ready}（没有就算好了）；超过 delay + {@link #READY_TIMEOUT} 刻照拍。它抛异常也照拍，不让展示厅在客户端刻里崩掉 */
    private static boolean sceneReady(Minecraft mc, SceneShot scene) {
        if (scene.ready() == null) {
            return true;
        }
        boolean ready;
        try {
            ready = scene.ready().test(mc);
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("[showroom] {} 的 ready 出错，照拍", scene.name(), e);
            return true;
        }
        if (ready) {
            EssentialCraft5.LOGGER.info("[showroom] {} 等了 {} 刻（最少 {}）", scene.name(), timer, scene.delay());
            return true;
        }
        if (timer > scene.delay() + READY_TIMEOUT) {
            EssentialCraft5.LOGGER.warn("[showroom] {} 等了 {} 刻还没准备好（{}），照拍", scene.name(), timer, scene.ready());
            return true;
        }
        return false;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void prepareClient(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(3);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
        mc.resizeDisplay();
    }

    private static void createWorld(Minecraft mc) {
        File saveDir = new File(new File(mc.gameDirectory, "saves"), SAVE);
        try {
            FileUtils.deleteDirectory(saveDir);
        } catch (IOException e) {
            EssentialCraft5.LOGGER.warn("[showroom] 删不掉旧存档 {}", saveDir, e);
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(SAVE, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(SAVE, settings, new WorldOptions(0L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions());
    }

    /** 在服务端线程摆出所有方块、发物品 */
    private static void setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        level.setDayTime(6000);
        origin = player.blockPosition().offset(-(PER_ROW - 1) * SPACING_X / 2, 0, 6);
        if (JEI_ONLY) {
            // JEI 的配方界面盖满屏幕，不用摆场地
            return;
        }
        if (HOANNA_ONLY) {
            DevShowroomHoanna.build(level, player, DevShowroom::sceneBase, 11);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍海纳尔：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (VOID_ONLY) {
            DevShowroomVoid.build(level, player, DevShowroom::sceneBase, 0, true);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍 6b：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (DEMON_ONLY) {
            DevShowroomDemon.build(level, player, DevShowroom::sceneBase, 0, true);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第七批：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (HOLOGRAM_ONLY) {
            DevShowroomHologram.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第八批：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (CONDENSER_ONLY) {
            DevShowroomCondenser.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第九批：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (SEAL_ONLY) {
            DevShowroomSeal.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十批 10a：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (TRIALS_ONLY) {
            DevShowroomTrials.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十批 10b：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (MODULES_ONLY) {
            DevShowroomModules.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十批 10c：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH11_ONLY) {
            DevShowroomBatch11.build(level, player, DevShowroom::sceneBase, 0, true);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十一批：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH12_ONLY) {
            DevShowroomBatch12.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十二批：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH13A_ONLY) {
            DevShowroomBatch13a.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十三批 13a：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH13B_ONLY) {
            DevShowroomBatch13b.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十三批 13b：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH13C_ONLY) {
            DevShowroomBatch13c.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十三批 13c：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH13D_ONLY) {
            DevShowroomBatch13d.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十三批 13d：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }
        if (BATCH13E_ONLY) {
            DevShowroomBatch13e.build(level, player, DevShowroom::sceneBase, 0);
            fillHotbar(player);
            EssentialCraft5.LOGGER.info("[showroom] 只拍第十三批 13e：{} 张场景图，{} 个界面", scenes.size(), screens.size());
            return;
        }

        // 装饰页的方块（花纹方块 ×128、花纹拟态 ×16，第五批；海纳尔植物等第六批 6a）不进方块排，否则多拍约 25 张 01_row；花纹用场景拍。
        // 海纳尔之门（没有物品）也不进：单独一格门摆在排里没有框，拍照时玩家要是站进去 4 秒后会被传走；门在 05_hoanna_portal_* 拍。
        // 无尽 MRU / ESPE 源（6b 无尽道具）也不进：它们每秒给 8 格内的机器补满，排里离得近的几台界面截图会变成满的；两个源在 04_scene_creative_sources 拍
        List<String> skipped = List.of("hoanna_portal", "infinite_mru_source", "infinite_espe_source");
        List<ECBlocks.Entry> entries = ECBlocks.entries().stream()
                .filter(entry -> !entry.spec().isDecoration() && !skipped.contains(entry.block().getId().getPath())).toList();
        rows = (entries.size() + PER_ROW - 1) / PER_ROW;
        for (int i = 0; i < entries.size(); i++) {
            Block block = entries.get(i).block().get();
            BlockPos pos = origin.offset((i % PER_ROW) * SPACING_X, 0, (i / PER_ROW) * SPACING_Z);
            BlockState state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            // 走一遍放置逻辑（多格方块会在这里补上另一半）
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
            // 太阳棱镜要求同层四个方向隔一格有方块（紧挨的 8 格留空），不补支架它第一次检查就自毁
            if (entries.get(i).block().getId().getPath().equals("solar_prism")) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    level.setBlockAndUpdate(pos.relative(direction, 2), Blocks.STONE.defaultBlockState());
                }
            }
            // 自然熔炉放几朵花，排的截图里能看到它烧起来
            if (level.getBlockEntity(pos) instanceof NaturalFurnaceBlockEntity furnace) {
                furnace.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY, 16));
            }
            // 武器工作台：放满一套能组装的材料，界面右上角有预览（02_gui_*_weapon_maker）
            if (level.getBlockEntity(pos) instanceof WeaponMakerBlockEntity maker) {
                maker.fillShowroomDemo();
            }
            // 地脉投影仪：放透镜、开「保留岩石」，到拍界面时输出格里有东西（主人 = 展示玩家，上面 setPlacedBy 记的）
            if (level.getBlockEntity(pos) instanceof LeyProjectorBlockEntity ley) {
                ley.getInventory().setStackInSlot(LeyProjectorBlockEntity.SLOT_LENS, new ItemStack(ECDigger.PALE_GEM.get()));
                ley.toggleKeepRock();
            }
            // 地脉凝聚器：选上钻石，拍界面时是「正在凝聚」（02_gui_ley_condenser）
            if (level.getBlockEntity(pos) instanceof LeyCondenserBlockEntity condenser) {
                condenser.setTarget(LeyCondenserBlockEntity.Target.DIAMOND);
            }
            if (level.getBlockEntity(pos) instanceof MRUBlockEntity be
                    && be.createMenu(0, player.getInventory(), player) != null) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU() / 2);
                BlockPos machine = pos.immutable();
                screens.add(new ScreenShot(entries.get(i).block().getId().getPath(),
                        p -> {
                            p.teleportTo(machine.getX() + 0.5, machine.getY(), machine.getZ() - 1.5);
                            // 拍界面前再充到一半：前面几分钟里用掉 MRU 的机器（投影仪这类）界面上显示的还是「在工作」
                            if (p.serverLevel().getBlockEntity(machine) instanceof MRUBlockEntity machineBe) {
                                machineBe.getMRUStorage().setMRU(machineBe.getMRUStorage().getMaxMRU() / 2);
                            }
                        },
                        p -> {
                            if (p.serverLevel().getBlockEntity(machine) instanceof MRUBlockEntity machineBe) {
                                NetworkHooks.openScreen(p, machineBe, machine);
                            }
                        }));
            }
        }
        machineScreens = screens.size();
        // 多摆一排：风之符文祭坛、0 档灵魂五芒星的完整结构（照代码里登记的结构逐格摆代表方块），看成形后的效果
        BlockPos structureRow = origin.offset(0, 0, rows * SPACING_Z);
        buildStructure(level, player, ECWind.RUNE_STRUCTURE, structureRow.offset(2, 1, 0));
        buildStructure(level, player, ECPentacle.TIER_PATTERNS.get(0), structureRow.offset(12, 0, 0));
        rows++;
        // 再多摆一排：15000 MRU 的聚集体（不会自己消失），看光线渲染
        BlockPos presencePos = origin.offset((PER_ROW - 1) * SPACING_X / 2, 1, rows * SPACING_Z);
        level.addFreshEntity(MRUPresenceEntity.create(level, presencePos.getX() + 0.5D, presencePos.getY() + 0.5D,
                presencePos.getZ() + 0.5D, 15000, 1F, true, true));
        rows++;
        // 特殊场景（方块排北边）和额外界面
        buildScenes(level, player);
        // 第一次进世界应已收到研究手册（ResearchBookGift），下面发物品会覆盖快捷栏，先查一下
        EssentialCraft5.LOGGER.info("[showroom] 进世界时身上有研究手册：{}",
                player.getInventory().contains(new ItemStack(ECGuide.RESEARCH_BOOK.get())));
        fillHotbar(player);
        EssentialCraft5.LOGGER.info("[showroom] 摆了 {} 个方块（{} 排），其中 {} 台有界面；{} 张场景图，{} 个界面",
                entries.size(), rows, machineScreens, scenes.size(), screens.size());
    }

    /** 快捷栏放前 9 个物品（进世界时；枪的场景换过快捷栏，拍完场景再放回来） */
    private static void fillHotbar(ServerPlayer player) {
        List<ECItems.Entry> items = ECItems.entries();
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, i < items.size() ? new ItemStack(items.get(i).item().get()) : ItemStack.EMPTY);
        }
        player.getInventory().selected = 0;
    }

    /** 按结构的规则逐格摆代表方块（空气格跳过；多格方块走 setPlacedBy 补上其它段） */
    private static void buildStructure(ServerLevel level, ServerPlayer player, StructurePattern pattern, BlockPos origin) {
        pattern.rules().forEach((offset, rule) -> {
            if (rule.isAir()) {
                return;
            }
            BlockPos pos = StructurePattern.toWorld(origin, offset, Direction.NORTH);
            BlockState state = rule.displayState();
            level.setBlockAndUpdate(pos, state);
            state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
        });
    }

    private static void lookAtRow(MinecraftServer server, int row) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        double x = origin.getX() + (PER_ROW - 1) * SPACING_X / 2.0 + 0.5;
        double z = origin.getZ() + row * SPACING_Z - 7.5;
        player.connection.teleport(x, origin.getY() + 2, z, 0F, 15F);
    }

    // ================================================================ 特殊场景

    /** 第 index 个场景的基准格（地面上一格，和方块排同高）；场景沿 X 排开，镜头都从南边朝北拍 */
    static BlockPos sceneBase(int index) {
        return origin.offset((index - 3) * SCENE_SPACING, 0, SCENE_Z);
    }

    /** 基准格加偏移后的一点（方块坐标 + 小数，0.5 = 格子中间） */
    static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return new Vec3(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
    }

    /** 服务端：静态的方块在这里摆好（结构有时间成形、区块早点建好网格）；链接、生成实体这类会过期的放进场景的 prepare */
    private static void buildScenes(ServerLevel level, ServerPlayer player) {
        buildMimicScene(level, sceneBase(0));
        buildTransmitterScene(level, sceneBase(1));
        buildDisplayScene(level, sceneBase(2));
        buildMirrorScene(level, player, sceneBase(3));
        buildTeslaScene(level, player, sceneBase(4));
        buildReactorScene(level, player, sceneBase(5));
        buildExtraScreens(level, sceneBase(6));
        // 第五批
        buildGunsScene(level, sceneBase(7));
        buildFancyWallScene(level, sceneBase(8));
        buildFancyMimicScene(level, sceneBase(9));
        buildLeyProjectorScene(level, player, sceneBase(10));
        screens.add(new ScreenShot("creative_decorations", p -> { }, p -> Minecraft.getInstance().execute(() ->
                openCreativeTab(Minecraft.getInstance(), ECCreativeTabs.DECORATIONS.get()))));
        // 第六批 6b：建筑魔杖、传送器与熔炉、终极产能器、无尽道具和传送器往返（往返回到主世界才结束，所以排在海纳尔部分前面）
        DevShowroomVoid.build(level, player, DevShowroom::sceneBase, 11, false);
        // 第七批：恶魔五芒星与召唤、交易界面、余烬锻炉与剑、水晶柱、灵魂五芒星 2 / 3 档、虚空套装、恶魔护板（都在主世界，排在海纳尔部分前面）
        DevShowroomDemon.build(level, player, DevShowroom::sceneBase, 11 + DevShowroomVoid.SCENE_SLOTS, false);
        // 第八批：全息投影台与 Boss、遥控器与二分枪、宅男套装与反重力板（都在主世界，排在海纳尔部分前面）
        DevShowroomHologram.build(level, player, DevShowroom::sceneBase, 11 + DevShowroomVoid.SCENE_SLOTS + DevShowroomDemon.SCENE_SLOTS);
        // 第九批：地脉凝聚器（凝下界之星、凝粗铁接漏斗和魔力箱、和投影仪并排对照）
        DevShowroomCondenser.build(level, player, DevShowroom::sceneBase,
                11 + DevShowroomVoid.SCENE_SLOTS + DevShowroomDemon.SCENE_SLOTS + DevShowroomHologram.SCENE_SLOTS);
        // 第十一批：MRU 转换器的四种外观与真在工作的一排、修过贴图坐标的 OBJ 机器近景（都在主世界，排在海纳尔部分前面）
        DevShowroomBatch11.build(level, player, DevShowroom::sceneBase,
                11 + DevShowroomVoid.SCENE_SLOTS + DevShowroomDemon.SCENE_SLOTS + DevShowroomHologram.SCENE_SLOTS
                        + DevShowroomCondenser.SCENE_SLOTS, false);
        // 第六批 6a：主世界里的植物 / 虚空方块 / 结构剖面（04_scene_*），再到真的海纳尔世界拍天空与画面（05_hoanna_*）
        DevShowroomHoanna.build(level, player, DevShowroom::sceneBase,
                11 + DevShowroomVoid.SCENE_SLOTS + DevShowroomDemon.SCENE_SLOTS + DevShowroomHologram.SCENE_SLOTS
                        + DevShowroomCondenser.SCENE_SLOTS + DevShowroomBatch11.SCENE_SLOTS);
    }

    /**
     * 拟态方块：伪装成草方块、玻璃、朝东的橡木楼梯各一块，一块不伪装；每块伪装的后面（北边两格）摆一块真的作对照。
     * 地面本身就是草方块，看草的着色对不对。
     */
    private static void buildMimicScene(ServerLevel level, BlockPos base) {
        BlockState[] camos = {
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                null};
        for (int i = 0; i < camos.length; i++) {
            BlockPos pos = base.offset(i * 2, 0, 0);
            level.setBlockAndUpdate(pos, ECRedstoneTools.MIMIC.get().defaultBlockState());
            BlockState camo = camos[i];
            if (camo == null) {
                continue;
            }
            if (!MimicBlock.acceptsCamo(camo)) {
                EssentialCraft5.LOGGER.warn("[showroom] 拟态方块不接受伪装 {}", camo);
            }
            if (level.getBlockEntity(pos) instanceof MimicBlockEntity mimic) {
                mimic.setCamo(camo);
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {} 没有拟态方块实体", pos);
            }
            level.setBlockAndUpdate(pos.north(2), camo);
        }
        scenes.add(new SceneShot("mimic", null, at(base, 3.5, 3, 6.5), at(base, 3.5, 0.5, -0.5), FIRST_SCENE_WAIT));
        scenes.add(new SceneShot("mimic_side", null, at(base, 9.5, 2.5, 3.5), at(base, 3.5, 0.5, -0.5), 30));
    }

    /**
     * 红石传递器六个朝向，从西到东依次是 下、上、北、南、西、东（Direction.values()），离地一格；
     * 每个贴着的那一格（FACING 指向的方块）放一块玻璃，看底板是不是贴在玻璃那一面。
     */
    private static void buildTransmitterScene(ServerLevel level, BlockPos base) {
        Direction[] facings = Direction.values();
        for (int i = 0; i < facings.length; i++) {
            BlockPos pos = base.offset(i * 2, 1, 0);
            level.setBlockAndUpdate(pos.relative(facings[i]), Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(pos, ECRedstoneTools.REDSTONE_TRANSMITTER.get().defaultBlockState()
                    .setValue(RedstoneTransmitterBlock.FACING, facings[i]));
        }
        scenes.add(new SceneShot("transmitter_south", null, at(base, 5.5, 3.5, 6.5), at(base, 5.5, 1.5, 0.5), 30));
        scenes.add(new SceneShot("transmitter_northeast", null, at(base, 12.5, 5, -5.5), at(base, 5.5, 1.5, 0.5), 30));
    }

    /**
     * 魔力显示屏：一面石砖墙（北边），墙上三块朝南的屏各放一把附魔钻石剑，分别是方式 0、1、2；
     * 墙东头一根石柱上一块朝东的屏（方式 1），地上一块朝上的屏（方式 0，放钻石）。
     */
    private static void buildDisplayScene(ServerLevel level, BlockPos base) {
        for (int x = -1; x <= 8; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlockAndUpdate(base.offset(x, y, -1), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int mode = 0; mode < MagicalDisplayBlockEntity.MODES; mode++) {
            placeDisplay(level, base.offset(mode * 3, 1, 0), Direction.SOUTH, enchantedSword(), mode);
        }
        level.setBlockAndUpdate(base.offset(9, 1, 1), Blocks.STONE_BRICKS.defaultBlockState());
        placeDisplay(level, base.offset(10, 1, 1), Direction.EAST, enchantedSword(), 1);
        placeDisplay(level, base.offset(3, 0, 2), Direction.UP, new ItemStack(Items.DIAMOND), 0);
        scenes.add(new SceneShot("display", null, at(base, 4.5, 2.2, 6.5), at(base, 4.5, 1.2, 0.5), 30));
        scenes.add(new SceneShot("display_mode1", null, at(base, 3.5, 1.6, 2.4), at(base, 3.5, 1.5, 0.5), 20));
        scenes.add(new SceneShot("display_mode2", null, at(base, 6.5, 1.6, 2.4), at(base, 6.5, 1.5, 0.5), 20));
        scenes.add(new SceneShot("display_side", null, at(base, 12.5, 2.2, 3.5), at(base, 10.5, 1.5, 1.5), 20));
    }

    private static ItemStack enchantedSword() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 5);
        sword.enchant(Enchantments.UNBREAKING, 3);
        return sword;
    }

    /** 放一块屏（朝向 = 屏的正面朝哪，背面贴着相反方向那一格），放上物品、切到第 mode 种显示方式 */
    private static void placeDisplay(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack, int mode) {
        level.setBlockAndUpdate(pos, ECStorage.MAGICAL_DISPLAY.get().defaultBlockState().setValue(MagicalDisplayBlock.FACING, facing));
        if (level.getBlockEntity(pos) instanceof MagicalDisplayBlockEntity display) {
            display.setDisplayed(stack);
            for (int i = 0; i < mode; i++) {
                display.cycleMode();
            }
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有显示屏方块实体", pos);
        }
    }

    /**
     * 魔力镜：下面一个装了几样东西的箱子，镜子链接到东边 3 格的另一个空箱子。链接放在场景的 prepare 里（到场才开始送），
     * 送一件要 60 刻，第一张在送到一半时拍，第二张在快送到时拍。
     */
    private static void buildMirrorScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos source = base;
        BlockPos mirror = base.above();
        BlockPos target = base.east(3);
        level.setBlockAndUpdate(source, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(source) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            chest.setItem(1, new ItemStack(Items.IRON_INGOT, 8));
            chest.setItem(2, new ItemStack(Items.APPLE, 4));
            chest.setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
        }
        BlockState state = ECMirror.MAGICAL_MIRROR.get().defaultBlockState();
        level.setBlockAndUpdate(mirror, state);
        state.getBlock().setPlacedBy(level, mirror, state, player, new ItemStack(state.getBlock()));
        Consumer<ServerPlayer> link = p -> {
            if (p.serverLevel().getBlockEntity(mirror) instanceof MagicalMirrorBlockEntity be) {
                be.link(target);
                EssentialCraft5.LOGGER.info("[showroom] 魔力镜链接到 {}，开始送：{}", target, be.tryStartTransfer());
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔力镜方块实体", mirror);
            }
        };
        scenes.add(new SceneShot("mirror_a", link, at(base, 1.5, 2.8, 5.5), at(base, 1.5, 1.2, 0.5), 30));
        scenes.add(new SceneShot("mirror_b", null, at(base, 1.5, 2.8, 5.5), at(base, 1.5, 1.2, 0.5), 15));
        scenes.add(new SceneShot("mirror_top", null, at(base, 1.5, 5.5, 3.5), at(base, 1.5, 1.2, 0.5), 5));
        // 镜框转向目标以后，从南边看是侧面；站到目标箱子那一侧看镜面
        scenes.add(new SceneShot("mirror_face", null, at(base, 4.5, 2.4, 1.8), at(base, 0.5, 1.4, 0.5), 10));
    }

    /**
     * 特斯拉线圈：照 {@link ECTesla#COIL_STRUCTURE} 摆底座（25 块魔力护板）+ 两层基座 + 线圈（射程 7），记下主人、MRU 充满。
     * 到场时（prepare）把难度调成简单（和平难度会马上删掉怪物），在底座东边 5 格放一只不持久的僵尸：
     * 不动（NoAI）、戴钻石头盔不被太阳烧、血量 1000（线圈每 20 刻打 18，拍完之前打不死）。拍完场景把难度调回和平，僵尸随之消失。
     */
    private static void buildTeslaScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos coil = base.above(3);
        buildStructure(level, player, ECTesla.COIL_STRUCTURE, coil);
        if (level.getBlockEntity(coil) instanceof MRUBlockEntity be) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有线圈方块实体", coil);
        }
        Consumer<ServerPlayer> spawnZombie = p -> {
            MinecraftServer server = p.getServer();
            if (server != null) {
                server.setDifficulty(Difficulty.EASY, true);
            }
            ServerLevel serverLevel = p.serverLevel();
            Zombie zombie = EntityType.ZOMBIE.create(serverLevel);
            if (zombie == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 生成不了僵尸");
                return;
            }
            zombie.moveTo(base.getX() + 5.5, base.getY(), base.getZ() + 0.5, 90F, 0F);
            zombie.setNoAi(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(1000D);
            }
            zombie.setHealth(1000F);
            serverLevel.addFreshEntity(zombie);
            EssentialCraft5.LOGGER.info("[showroom] 线圈场景放了僵尸，持久：{}", zombie.isPersistenceRequired());
        };
        scenes.add(new SceneShot("tesla_a", spawnZombie, at(base, 3, 4.5, 9.5), at(base, 2.5, 2, 0.5), 45));
        scenes.add(new SceneShot("tesla_b", null, at(base, 3, 4.5, 9.5), at(base, 2.5, 2, 0.5), 8));
        scenes.add(new SceneShot("tesla_close_a", null, at(base, 0.5, 4.2, 3.5), at(base, 0.5, 2.6, 0.5), 20));
        scenes.add(new SceneShot("tesla_close_b", null, at(base, 0.5, 4.2, 3.5), at(base, 0.5, 2.6, 0.5), 25));
        // 基座火花是客户端随机刻抽中才冒（离玩家越近越容易抽中），贴近基座多拍几张
        for (int i = 0; i < 4; i++) {
            scenes.add(new SceneShot("tesla_hardener_" + (char) ('a' + i), null, at(base, 0.5, 2.7, 2.3), at(base, 0.5, 1.8, 0.5), 10));
        }
    }

    /**
     * 反应堆：照 {@link ECReactor#REACTOR_STRUCTURE}（沿 X 轴，核心朝北）摆 5×3 魔力护板地基、核心、两侧各两格支架。
     * 支架照玩家放置那样按周围算状态（{@link Block#updateFromNeighbourShapes}，从下往上），直接放默认状态上层会显示成「底」。
     */
    private static void buildReactorScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos core = base.above();
        List<BlockPos> supports = new ArrayList<>();
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECReactor.REACTOR_STRUCTURE.rules().entrySet()) {
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isAir()) {
                continue;
            }
            BlockPos pos = StructurePattern.toWorld(core, entry.getKey(), ECReactor.X_AXIS);
            BlockState state = rule.displayState();
            if (state.is(ECReactor.REACTOR_SUPPORT.get())) {
                supports.add(pos);
            } else if (!state.is(ECReactor.REACTOR.get())) {
                level.setBlockAndUpdate(pos, state);
            }
        }
        supports.sort(Comparator.comparingInt(BlockPos::getY));
        for (BlockPos pos : supports) {
            level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(ECReactor.REACTOR_SUPPORT.get().defaultBlockState(), level, pos));
        }
        BlockState coreState = ECReactor.REACTOR.get().defaultBlockState();
        level.setBlockAndUpdate(core, coreState);
        coreState.getBlock().setPlacedBy(level, core, coreState, player, new ItemStack(coreState.getBlock()));
        scenes.add(new SceneShot("reactor", null, at(base, 0.5, 3.5, 6.5), at(base, 0.5, 1.5, 0.5), 30));
        scenes.add(new SceneShot("reactor_close", null, at(base, 0.5, 2.6, 3.2), at(base, 0.5, 1.8, 0.5), 20));
    }

    /**
     * 额外界面：魔力箱（不继承 MRUBlockEntity，机器界面阶段拍不到）、手持白名单 / 黑名单过滤器、手持合成框架（都是 HeldItemMenu，从手上的物品打开）。
     * 手持的那件放进当前选中的快捷栏格（两端都是 0 号），5 刻后照玩家右键的路径（{@link ItemStack#use}）打开。
     */
    private static void buildExtraScreens(ServerLevel level, BlockPos base) {
        BlockPos chestPos = base;
        level.setBlockAndUpdate(chestPos, ECStorage.MAGICAL_CHEST.get().defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof MagicalChestBlockEntity chest) {
            chest.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
            chest.getInventory().setStackInSlot(1, enchantedSword());
            chest.getInventory().setStackInSlot(9, new ItemStack(Items.IRON_INGOT, 32));
            chest.getInventory().setStackInSlot(53, new ItemStack(Items.GOLDEN_APPLE, 3));
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔力箱方块实体", chestPos);
        }
        screens.add(new ScreenShot("magical_chest",
                p -> p.teleportTo(chestPos.getX() + 0.5, chestPos.getY(), chestPos.getZ() - 1.5),
                p -> {
                    if (p.serverLevel().getBlockEntity(chestPos) instanceof MagicalChestBlockEntity chest) {
                        NetworkHooks.openScreen(p, chest, chestPos);
                    }
                }));
        ItemStack whitelist = new ItemStack(ECLogistics.WHITELIST_FILTER.get());
        ItemFilters.setSamples(whitelist, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.WHEAT_SEEDS)));
        screens.add(heldScreen("whitelist_filter", whitelist));
        screens.add(heldScreen("blacklist_filter", new ItemStack(ECLogistics.BLACKLIST_FILTER.get())));
        screens.add(heldScreen("crafting_frame", new ItemStack(ECCrafter.CRAFTING_FRAME.get())));
    }

    private static ScreenShot heldScreen(String name, ItemStack stack) {
        return new ScreenShot(name,
                p -> p.getInventory().setItem(p.getInventory().selected, stack.copy()),
                p -> p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND));
    }

    // ---------------------------------------------------------------- 第五批场景

    /** 四把部件各不相同的枪（照 ECGuns 模块给的四套部件；枪没注册时是空栈） */
    private static ItemStack[] showroomGuns() {
        return new ItemStack[] {
                GunItem.create(GunType.PISTOL, GunParts.uniform("mru").withScope("mru").withLens("chaos"), 5000),
                GunItem.create(GunType.RIFLE, new GunParts("pale", "mru", "mithriline", "frozen", "mru"), 5000),
                GunItem.create(GunType.SNIPER, GunParts.uniform("mithriline").withScope("mithriline").withLens("pure"), 5000),
                GunItem.create(GunType.GATLING, GunParts.uniform("mru").withLens("shade"), 5000)};
    }

    /**
     * 枪（ECGuns）：四个盔甲架面朝东、右臂平举，各拿一把部件不同的枪，从南边看是侧面（右手在南侧，朝着镜头）。
     * 第二张在客户端直接生成光束（{@link BeamEffects#spawn}）：盔甲架后面 5 道横着的，透镜 0~4 从下到上（青、红、蓝、品红、灰），
     * 另一道从手枪枪口往东北射向远处。光束只活 60 刻，所以在那一张的 client 里现生成、10 刻后就拍。
     * 第三张开着 HUD、第一人称拿手枪，快捷栏放几把调过「已射 / 磨损」的枪（热量条、耐久条）；第四张强制开狙击镜（米斯莱 7 倍）从 20 格外看盔甲架。
     * 另有两张真开一枪、第 5 刻拍：第一人称手枪打地面（gun_fire_fp，紧跟第三张）、开狙击镜打向盔甲架之间（sniper_fire_scoped），
     * 看自己的光束从枪口出来、不染屏（审查 GUNS-3）；开镜那张的光束应从镜子下沿正中升上来收进准星、是一条细线（2026-09-29 改）。
     * 最后两张是矩阵受损特效（GUNS-4）：盔甲架身上的电弧与粒子（matrix_hit）、自己屏幕边缘的品红晕影（matrix_hit_self）。
     */
    private static void buildGunsScene(ServerLevel level, BlockPos base) {
        ItemStack[] guns = showroomGuns();
        for (int i = 0; i < guns.length; i++) {
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 生成不了盔甲架");
                return;
            }
            stand.moveTo(base.getX() + 0.5 + i * 3, base.getY(), base.getZ() + 0.5, -90F, 0F);
            stand.setYHeadRot(-90F);
            stand.setYBodyRot(-90F);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            stand.setRightArmPose(new Rotations(-90F, 0F, 0F));
            stand.setItemSlot(EquipmentSlot.MAINHAND, guns[i]);
            level.addFreshEntity(stand);
            if (guns[i].isEmpty()) {
                EssentialCraft5.LOGGER.warn("[showroom] 第 {} 把枪造不出来（枪没注册？）", i);
            }
        }
        Consumer<Minecraft> beams = mc -> {
            for (int balance = 0; balance < 5; balance++) {
                double y = base.getY() + 0.6 + balance * 0.45;
                BeamEffects.spawn(new Vec3(base.getX() - 3, y, base.getZ() - 1.5), new Vec3(base.getX() + 13, y, base.getZ() - 1.5), balance);
            }
            BeamEffects.spawn(at(base, 1.4, 1.45, 0.9), at(base, 12, 3, -14), 1);
        };
        Consumer<ServerPlayer> hotbar = p -> {
            ItemStack[] held = showroomGuns();
            // 热量条：手枪打了 1 发、步枪打了一半、加特林快打完；耐久条：步枪磨损 50、狙击磨损满了（满了以后会卡壳）
            GunParts.setFired(held[0], 1);
            GunParts.setFired(held[1], 10);
            GunParts.setWear(held[1], 50);
            GunParts.setFired(held[2], 2);
            GunStats.of(held[2]).ifPresent(stats -> GunParts.setWear(held[2], stats.durabilityInt()));
            GunParts.setFired(held[3], 60);
            for (int i = 0; i < held.length; i++) {
                p.getInventory().setItem(i, held[i]);
            }
            p.getInventory().setItem(4, ECGuns.PISTOL.isPresent() ? new ItemStack(ECGuns.PISTOL.get()) : ItemStack.EMPTY);
            for (int i = 5; i < 9; i++) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
            p.getInventory().selected = 0;
        };
        Consumer<ServerPlayer> sniperInHand = p -> p.getInventory().setItem(0, showroomGuns()[2]);
        Vec3 eye = at(base, 5, 1.8, 5);
        Vec3 target = at(base, 5, 1.3, 0.5);
        scenes.add(new SceneShot("guns", null, eye, target, FIRST_SCENE_WAIT));
        scenes.add(new SceneShot("guns_beams", null, eye, target, 10, beams));
        scenes.add(new SceneShot("guns_beams_far", null, at(base, 5, 4, 10), at(base, 5, 1.3, -1.5), 5));
        scenes.add(new SceneShot("gun_hand", hotbar, at(base, 5, 2, 4), at(base, 5, 1.4, 0.5), 25, mc -> mc.options.hideGui = false));
        // 真开一枪、第 5 刻拍（审查 GUNS-3）：第一人称光束应从右下角枪口收向准星，画面不被透镜色染满；开狙击镜开枪时镜里只有远处那一截。
        // 每张开拍前清掉之前的光束；枪口朝地面 / 盔甲架之间，打不到人。
        // gun_fire_fp 紧跟 gun_hand：快捷栏第一格一直是手枪，换成新的一把不触发换手动画（MRUStaffItem），拍到的是端平的枪、看得出光束是不是从枪口出来
        Vec3 fireEye = at(base, 2, 2, 8);
        Vec3 fireTarget = at(base, 2, 0, 1.5);
        Vec3 scopeEye = at(base, 5, 2, 22);
        scenes.add(new SceneShot("gun_fire_fp", p -> fireShowroomGun(p, 0, fireEye, fireTarget), fireEye, fireTarget, 5, mc -> {
            mc.options.hideGui = false;
            BeamEffects.clear();
        }));
        scenes.add(new SceneShot("sniper_scope", sniperInHand, at(base, 5, 2, 22), target, 40, mc -> {
            mc.options.hideGui = false;
            GunsClient.showroomForceScope = true;
            BeamEffects.clear();    // 上一张的手枪光束还没散
        }));
        scenes.add(new SceneShot("sniper_fire_scoped", p -> fireShowroomGun(p, 2, scopeEye, target), scopeEye, target, 5, mc -> {
            mc.options.hideGui = false;
            GunsClient.showroomForceScope = true;
            BeamEffects.clear();
        }));
        // 矩阵受损特效（GUNS-4）：展示厅只有一个玩家、打不到别的玩家，直接在客户端放。
        // matrix_hit：拿狙击枪的那个盔甲架身上窜品红 / 紫罗兰电弧、冒粒子（第 3 刻拍，电弧 8~10 刻寿命）；
        // matrix_hit_self：被打的人自己的屏幕边缘泛品红晕影（开 HUD，第 2 刻拍 = 刚升满）
        scenes.add(new SceneShot("matrix_hit", null, at(base, 6.5, 1.6, 3.2), at(base, 6.5, 1.1, 0.5), 3, mc -> {
            BeamEffects.clear();
            showroomMatrixHit(mc, at(base, 6.5, 0, 0.5));
        }));
        scenes.add(new SceneShot("matrix_hit_self", null, eye, target, 2, mc -> {
            mc.options.hideGui = false;
            BeamEffects.clear();
            MatrixHitEffects.flashSelf(3);
        }));
    }

    /** 展示厅：在 feet 处那个盔甲架身上放一次「矩阵受损」（强度 3） */
    private static void showroomMatrixHit(Minecraft mc, Vec3 feet) {
        if (mc.level == null) {
            return;
        }
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(feet, feet).inflate(0.75D));
        if (stands.isEmpty()) {
            EssentialCraft5.LOGGER.warn("[showroom] 找不到放矩阵受损特效的盔甲架（{}）", feet);
            return;
        }
        MatrixHitEffects.play(stands.get(0), 3);
    }

    /** 展示厅：第 index 把展示枪放到快捷栏第一格，站到 eye 看向 target 真开一枪（光束包照常发给展示厅玩家） */
    private static void fireShowroomGun(ServerPlayer player, int index, Vec3 eye, Vec3 target) {
        ItemStack gun = showroomGuns()[index];
        player.getInventory().setItem(0, gun);
        player.getInventory().selected = 0;
        aim(player, eye, target);
        GunType.of(gun).ifPresent(type -> GunStats.of(gun).ifPresent(stats ->
                GunItem.shoot(player, gun, type, stats, player.getRandom(), true, RayShot.PACKET)));
    }

    /**
     * 花纹墙（ECDecorations）：16 列 = 16 种花纹（原作 meta 顺序），从上到下 9 行底材（按 {@link FancyMaterial} 顺序：MRU、混凝土、强化石、霜冻石、
     * 魔力 / 淡青护板、虚空石、米斯莱护板、恶魔护板），最下面一行是没伪装的花纹拟态。
     * 镜头高度按底材行数算（墙高 = 行数 + 1），以后加底材不用改。再拍两张近景：左上角（MRU 底材的动画、混凝土），左下角（花纹拟态）。
     */
    private static void buildFancyWallScene(ServerLevel level, BlockPos base) {
        FancyMaterial[] materials = FancyMaterial.values();
        FancyPattern[] patterns = FancyPattern.values();
        for (int col = 0; col < patterns.length; col++) {
            for (int row = 0; row < materials.length; row++) {
                level.setBlockAndUpdate(base.offset(col, materials.length - row, 0),
                        ECDecorations.fancy(materials[row], patterns[col]).get().defaultBlockState());
            }
            level.setBlockAndUpdate(base.offset(col, 0, 0), ECDecorations.fancyMimic(patterns[col]).get().defaultBlockState());
        }
        double middle = (materials.length + 1) / 2.0D;
        scenes.add(new SceneShot("fancy_wall", null, at(base, 8, middle, 14.5), at(base, 8, middle, 0.5), 30));
        scenes.add(new SceneShot("fancy_wall_close_top", null, at(base, 2.5, materials.length - 0.5, 3.5),
                at(base, 2.5, materials.length - 0.5, 0.5), 20));
        scenes.add(new SceneShot("fancy_wall_close_mimic", null, at(base, 2.5, 1.2, 3.2), at(base, 2.5, 0.6, 0.5), 20));
    }

    /**
     * 花纹拟态（ECDecorations + 框架 F5-1 伪装叠花纹）：前排 5 块花纹拟态，依次是没伪装、橡木木板、草方块、玻璃、石台阶下半；
     * 后排（北边两格）同样伪装的普通拟态方块作对比。看花纹是否叠在伪装上、不闪、草色不染花纹、玻璃透明处看得到花纹、没伪装时是拟态贴图 + 花纹。
     */
    private static void buildFancyMimicScene(ServerLevel level, BlockPos base) {
        FancyPattern[] patterns = {FancyPattern.RUNIC, FancyPattern.BRICK, FancyPattern.GEM, FancyPattern.MACHINE, FancyPattern.TEMPLE};
        BlockState[] camos = {
                null,
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)};
        for (int i = 0; i < patterns.length; i++) {
            BlockPos fancy = base.offset(i * 2, 0, 0);
            BlockPos plain = fancy.north(2);
            level.setBlockAndUpdate(fancy, ECDecorations.fancyMimic(patterns[i]).get().defaultBlockState());
            level.setBlockAndUpdate(plain, ECRedstoneTools.MIMIC.get().defaultBlockState());
            if (camos[i] == null) {
                continue;
            }
            for (BlockPos pos : new BlockPos[] {fancy, plain}) {
                if (level.getBlockEntity(pos) instanceof MimicBlockEntity mimic) {
                    mimic.setCamo(camos[i]);
                } else {
                    EssentialCraft5.LOGGER.warn("[showroom] {} 没有拟态方块实体", pos);
                }
            }
        }
        scenes.add(new SceneShot("fancy_mimic", null, at(base, 4.5, 3, 6.5), at(base, 4.5, 0.5, -0.5), 30));
        scenes.add(new SceneShot("fancy_mimic_close", null, at(base, 5.5, 1.6, 2.6), at(base, 5.5, 0.5, 0.5), 20));
        scenes.add(new SceneShot("fancy_mimic_side", null, at(base, 10.5, 2.5, 3.5), at(base, 4.5, 0.5, -0.5), 20));
    }

    /**
     * 地脉投影仪（ECLeyProjector）：离地 5 格悬空放一台（光束往下 16 格，钻进地面的那段看不见），主人 = 展示玩家、放透镜、MRU 加满、开「保留岩石」
     * （真表 1 秒一格、约 1% 出矿，开着保留岩石输出格才有东西）；同一区块里再放一台同主人、晚放下的（显示「已有你的投影仪」和挡路坐标）。
     * 两台各拍一张界面（02_gui_ley_projector_scene_*）；拍工作那台之前用 {@link LeyProjectorBlockEntity#fillShowroomDemo} 把输出格摆成
     * 深板岩圆石、圆石各一满组 + 五种矿 + 两格空（岩石最多占 2 格，LEY-1）。
     */
    private static void buildLeyProjectorScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos first = base.above(5);
        // 第二台放在同一区块里（隔一格）
        BlockPos second = first.east((first.getX() & 15) < 14 ? 2 : -2);
        BlockState state = ECLeyProjector.LEY_PROJECTOR.get().defaultBlockState();
        for (BlockPos pos : new BlockPos[] {first, second}) {
            level.setBlockAndUpdate(pos, state);
            state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
        }
        if (level.getBlockEntity(first) instanceof LeyProjectorBlockEntity ley) {
            ley.getInventory().setStackInSlot(LeyProjectorBlockEntity.SLOT_LENS, new ItemStack(ECDigger.PALE_GEM.get()));
            ley.getMRUStorage().setMRU(ley.getMRUStorage().getMaxMRU());
            ley.toggleKeepRock();
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有投影仪方块实体", first);
        }
        if (level.getBlockEntity(second) instanceof LeyProjectorBlockEntity ley) {
            ley.markPlaced(level.getGameTime() + 1);
            ley.getMRUStorage().setMRU(ley.getMRUStorage().getMaxMRU());
        }
        Consumer<ServerPlayer> refill = p -> {
            if (p.serverLevel().getBlockEntity(first) instanceof LeyProjectorBlockEntity ley) {
                ley.getMRUStorage().setMRU(ley.getMRUStorage().getMaxMRU());
            }
        };
        scenes.add(new SceneShot("ley_projector", refill, at(base, 1.5, 6, 7.5), at(base, 0.5, 2.5, 0.5), 40));
        scenes.add(new SceneShot("ley_projector_close", null, at(base, 0.5, 5.8, 2.6), at(base, 0.5, 4.2, 0.5), 20));
        for (BlockPos pos : new BlockPos[] {first, second}) {
            screens.add(new ScreenShot(pos == first ? "ley_projector_scene_working" : "ley_projector_scene_duplicate",
                    p -> {
                        p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.5);
                        refill.accept(p);
                        // 工作的那台：输出格摆成「岩石两格（各一满组，之后出的岩石都丢掉）、矿五格、空两格」，截图看得出各占几格（审查 LEY-1）
                        if (pos == first && p.serverLevel().getBlockEntity(pos) instanceof LeyProjectorBlockEntity ley) {
                            ley.fillShowroomDemo();
                        }
                    },
                    p -> {
                        if (p.serverLevel().getBlockEntity(pos) instanceof LeyProjectorBlockEntity ley) {
                            NetworkHooks.openScreen(p, ley, pos);
                        }
                    }));
        }
    }

    /**
     * 客户端：打开创造模式物品栏并停在 {@code tab} 那一页（第三页「装饰」）。原版只在 init 时按静态字段 selectedTab 选页，
     * 开发环境里字段名就是 selectedTab（official 映射），反射改它；改不了就停在默认页并记一行。
     */
    private static void openCreativeTab(Minecraft mc, CreativeModeTab tab) {
        if (mc.player == null) {
            return;
        }
        Object before = setCreativeTab(tab);
        if (creativeTabBefore == null) {
            creativeTabBefore = before;
        }
        mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get()));
    }

    /** 创造栏原来停在哪页（拍完界面放回去，后面手册那几张打开的物品栏还是原来的样子） */
    @Nullable
    private static Object creativeTabBefore;

    /** 改静态字段 selectedTab，返回原来的值（改不了返回 null） */
    @Nullable
    private static Object setCreativeTab(Object tab) {
        try {
            Field field = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            field.setAccessible(true);
            Object before = field.get(null);
            field.set(null, tab);
            return before;
        } catch (ReflectiveOperationException | RuntimeException e) {
            EssentialCraft5.LOGGER.warn("[showroom] 选不了创造栏页 {}", tab, e);
            return null;
        }
    }

    private static void restoreCreativeTab() {
        if (creativeTabBefore != null) {
            setCreativeTab(creativeTabBefore);
            creativeTabBefore = null;
        }
    }

    /** 服务端：飞起来（不往下掉），眼睛放到 eye、看向 target */
    static void aim(ServerPlayer player, Vec3 eye, Vec3 target) {
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.connection.teleport(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    /**
     * 场景拍完：难度调回和平（线圈场景的僵尸、海纳尔的毒雾随之消失）；快捷栏放回原来的物品（枪的场景换过）；
     * 从海纳尔回到主世界方块排前面（后面的机器界面按主世界坐标站位）、时间和天气复原、客户端的海纳尔展示厅覆盖全撤掉
     */
    private static void finishScenes(MinecraftServer server) {
        server.setDifficulty(Difficulty.PEACEFUL, true);
        ServerPlayer player = player(server);
        DevShowroomHologram.finish(player);
        DevShowroomTrials.finish(player);
        DevShowroomModules.finish(player);
        DevShowroomBatch13a.finish(player);
        DevShowroomBatch13b.finish(player);
        DevShowroomBatch13c.finish(player);
        DevShowroomBatch13d.finish(player);
        DevShowroomBatch13e.finish(player);
        DevShowroomHoanna.finish(server, player, origin);
        fillHotbar(player);
    }

    // ================================================================ 研究手册

    /**
     * 研究手册要拍的一轮（手册的每种虚拟宽度各一轮，见 {@link #GUIDE_ROUNDS}）。阅读页按「条目 + 那一篇里第几个某种类型的块（可指定层号）」定位，
     * 内容改写后块的位置变了也不用跟着改；条目不存在时跳过并打一行日志。
     */
    private static void buildGuideSteps(MinecraftServer server) {
        for (GuideRound round : GUIDE_ROUNDS) {
            // 界面检查时只拍一轮（-Dessentialcraft5.guiAudit.guideRound，默认 v640），三轮都拍要半个多小时
            if (GuiAudit.ENABLED && !round.name().equals(GuiAudit.GUIDE_ROUND)) {
                continue;
            }
            guideSteps.add(new GuideStep(null, mc -> {
                mc.setScreen(null);
                GuideScreen.setShowroomFreeze(-1F);
                GuideScreen.setShowroomCloseFreeze(-1F);
                GuideClientConfig.setOverride(null);
                guideRound = round.name();
                GuideScreen.setShowroomGuideScale(round.guideScale());
                // 游戏界面缩放用「自动」（1280×720 时实际 3 倍）：手册的自动档只在游戏选「自动」时替玩家挑，手动倍数跟游戏
                if (mc.options.guiScale().get() != 0) {
                    mc.options.guiScale().set(0);
                    mc.resizeDisplay();
                }
            }, 5));
            guideSteps.add(new GuideStep(null, mc -> server.execute(() -> {
                for (String id : GUIDE_PROGRESS) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "advancement revoke @a only " + EssentialCraft5.MODID + ":" + id);
                }
            }), 10));
            if (HOANNA_ONLY) {
                hoannaGuidePages();
                continue;
            }
            if (VOID_ONLY) {
                voidGuidePages(server);
                continue;
            }
            if (DEMON_ONLY) {
                demonGuidePages();
                continue;
            }
            if (HOLOGRAM_ONLY) {
                hologramGuidePages(server);
                continue;
            }
            if (CONDENSER_ONLY) {
                condenserGuidePages();
                continue;
            }
            if (SEAL_ONLY) {
                sealGuidePages();
                continue;
            }
            if (TRIALS_ONLY) {
                trialsGuidePages();
                continue;
            }
            if (MODULES_ONLY) {
                modulesGuidePages(server);
                continue;
            }
            if (BATCH11_ONLY) {
                batch11GuidePages();
                continue;
            }
            if (BATCH12_ONLY) {
                batch12GuidePages();
                continue;
            }
            if (BATCH13A_ONLY) {
                batch13aGuidePages();
                continue;
            }
            if (BATCH13B_ONLY) {
                batch13bGuidePages();
                continue;
            }
            if (BATCH13C_ONLY) {
                batch13cGuidePages();
                continue;
            }
            if (BATCH13D_ONLY) {
                batch13dGuidePages();
                continue;
            }
            if (BATCH13E_ONLY) {
                batch13eGuidePages();
                continue;
            }
            if (QUOTE_ONLY) {
                quoteGuidePages();
                continue;
            }
            if (STRUCT_ONLY) {
                structure3dGuidePages();
                continue;
            }
            // 首页：开场终态；日志记一行这一轮手册实际用的倍数和虚拟尺寸（v640 那一轮核对自动挑出的是 2）
            page("home", null, null, 0, -1);
            guideSteps.add(new GuideStep(null, mc -> EssentialCraft5.LOGGER.info("[showroom] 研究手册 {}：手册倍数 = {}",
                    round.name(), GuideScreen.showroomScaleInfo()), 1));
            if (round.name().equals(GUIDE_KEY_ROUND)) {
                guideKeyFrames();
            }
            arcSteps();
            // 各种块
            page("rituals", "elements/rituals", "ritual", 0, -1);
            page("mithriline_furnace", "mithriline/furnace", "structure", 0, -1);
            page("enrichment_controller", "enrichment/controller", "structure", 0, -1);
            page("mithriline_conversions", "mithriline/conversions", "recipe", 0, -1);
            page("magmatic_smeltery", "magmatic/smeltery", "recipe", 1, -1);
            page("matrix_absorber", "mru/matrix_absorber", null, 0, -1);
            page("matrix_absorber_recipe", "mru/matrix_absorber", "recipe", 0, -1);
            page("start_glossary", "start/glossary", null, 0, -1);
            page("start_numbers", "start/numbers", null, 0, -1);
            page("solar_prism_layers", "solar/solar_prism", "structure", 1, -1);
            page("mru_image", "start/mru", "image", 0, -1);
            page("tesla_coil_structure", "tesla/mru_coil", "structure", 0, -1);
            // 第五批三个支线分类（草稿）：投影仪的数值与产物表、武器工作台步骤、枪械部件表、开火规则、装饰的底材表 / 合成树 / 花纹拟态
            page("ley_projector", "ley/ley_projector", "kv", 0, -1);
            page("ley_lens_table", "ley/ley_lens", "table", 0, -1);
            page("guns_weapon_maker", "guns/weapon_maker", "steps", 0, -1);
            page("guns_parts_table", "guns/parts", "table", 0, -1);
            page("guns_pistol", "guns/pistol", "kv", 0, -1);
            page("guns_firing", "guns/firing", null, 0, -1);
            page("decorations_chisel_table", "decorations/magical_chisel", "table", 0, -1);
            page("decorations_patterns", "decorations/patterns", "table", 0, -1);
            page("decorations_fancy_mimic", "decorations/fancy_mimic", null, 0, -1);
            hoannaGuidePages();
            voidGuidePages(server);
            demonGuidePages();
            hologramGuidePages(server);
            condenserGuidePages();
            sealGuidePages();
            trialsGuidePages();
            modulesGuidePages(server);
            batch11GuidePages();
            // 提示框：「源质小知识」（lore）后面紧跟一个 good，两种颜色放一屏里对照
            page("tip_lore", "automation/crafter", "tip", 0, -1);
            // 图标：元素水晶的默认栈是全透明贴图（手册里画替身，见 GuideStacks）——条目头与左栏、物品块、月亮井配方的原料格；
            // 支线「装备与防护」的分类图标是魔力防护头盔（BEWLR 画的 3D 图标）
            page("crystal_entry", "crystal/elemental_crystal", null, 0, -1);
            page("crystal_former_items", "crystal/crystal_former", "items", 0, -1);
            page("moon_well_recipe", "crystal/moon_well", "recipe", 0, -1);
            // 月亮井配方里元素水晶那一格的物品提示：格子里画的是替身（长成的水晶），提示要是默认栈（不写大小）。
            // 先让手册找到那一格，再把假鼠标放上去（测试窗口在后台，不动真鼠标）
            guideSteps.add(new GuideStep(null, mc -> GuideScreen.showroomProbe(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem()), 3));
            guideSteps.add(new GuideStep(null, mc -> {
                int[] at = GuideScreen.showroomProbeResult();
                if (at == null) {
                    EssentialCraft5.LOGGER.warn("[showroom] 月亮井配方里没找到元素水晶的格子");
                } else {
                    GuideScreen.showroomMouse(at[0], at[1]);
                }
            }, 6));
            guideSteps.add(new GuideStep("moon_well_tooltip", mc -> mc.getToasts().clear(), 1));
            guideSteps.add(new GuideStep(null, mc -> GuideScreen.showroomProbe(null), 1));
            page("gear_sidebar", "gear/mhs_armor", null, 0, -1);
            // 首页滚到最底：支线「装备与防护」的格子图标是魔力防护头盔（BEWLR 画的 3D 图标）
            guideSteps.add(new GuideStep(null, mc -> {
                mc.setScreen(null);
                mc.getToasts().clear();
                GuideScreenOpener.open(null);
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomHome();
                }
            }, 25));
            guideSteps.add(new GuideStep(null, mc -> {
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomHomeEnd();
                }
            }, 12));
            guideSteps.add(new GuideStep("home_side", mc -> mc.getToasts().clear(), 1));
            // 物品栏里的同一批图标：魔力防护四件、默认栈 / 长成 / 半大带火元素的元素水晶，对照手册里画得对不对。
            // 放快捷栏：创造模式打开物品栏是创造物品栏，只有快捷栏那一排是玩家自己的（前两格留给原来的物品）
            guideSteps.add(new GuideStep(null, mc -> server.execute(() -> {
                ServerPlayer p = player(server);
                p.getInventory().setItem(2, new ItemStack(ECGear.MHS_HELMET.get()));
                p.getInventory().setItem(3, new ItemStack(ECGear.MHS_CHESTPLATE.get()));
                p.getInventory().setItem(4, new ItemStack(ECGear.MHS_LEGGINGS.get()));
                p.getInventory().setItem(5, new ItemStack(ECGear.MHS_BOOTS.get()));
                p.getInventory().setItem(6, new ItemStack(ECCrystal.ELEMENTAL_CRYSTAL.get()));
                p.getInventory().setItem(7, ElementalCrystalItem.create(100D, 0D, 0D, 0D, 0D));
                p.getInventory().setItem(8, ElementalCrystalItem.create(50D, 100D, 0D, 0D, 0D));
            }), 10));
            guideSteps.add(new GuideStep(null, mc -> {
                mc.setScreen(null);
                mc.getToasts().clear();
                if (mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }, 10));
            guideSteps.add(new GuideStep("inventory_icons", mc -> mc.getToasts().clear(), 2));
            guideSteps.add(new GuideStep(null, mc -> mc.setScreen(null), 2));
            // 层切换：米斯莱熔炉（4 层）切到最上一层；灵魂五芒星两档结构
            page("mithriline_furnace_top", "mithriline/furnace", "structure", 0, 3);
            page("pentacle", "pentacle/pentacle", "structure", 0, -1);
            page("pentacle_tier_one", "pentacle/tier_one", "structure", 0, -1);
            // 条件对照：线圈「打玩家」开和关（覆盖入口，不动真配置）。tesla/mru_coil 第一段的机械转换里还没有 if，
            // 拍「数值速查」里线圈那张 kv 表（第 3 张）：「打玩家每下」那一行带行级 if，开时有、关时没有
            for (boolean on : new boolean[] {true, false}) {
                guideSteps.add(new GuideStep(null, mc -> GuideClientConfig.setOverride(
                        Map.of("machines.mru_coil.hurt_players", String.valueOf(on))), 1));
                page("numbers_coil_hurt_players_" + on, "start/numbers", "kv", 2, -1);
            }
            guideSteps.add(new GuideStep(null, mc -> GuideClientConfig.setOverride(null), 1));
            if (round == GUIDE_ROUNDS.get(0)) {
                // 条目 items 条件组的端到端自检（门关着时花纹方块不进查阅索引和搜索），只看日志，不拍图；跑完自己撤掉覆盖值
                guideSteps.add(new GuideStep(null, mc -> GuideIndexCheck.runGateClosed(), 1));
            }
            // 打勾：发几个流程进度，再拍首页角标、侧栏、底部「下一步」条。
            // 第一次翻开时刚做完的几步在首页格子上闪一下（打勾电弧，冻结在 80ms）；第二次翻开已经看过，不再闪。
            // 发进度之前先合上书：开着的书会在自己那一页上先看到这几步变化（左栏里看不见那几行就只记下、不闪）
            guideSteps.add(new GuideStep(null, mc -> mc.setScreen(null), 2));
            guideSteps.add(new GuideStep(null, mc -> server.execute(() -> {
                for (String id : GUIDE_PROGRESS) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "advancement grant @a only " + EssentialCraft5.MODID + ":" + id);
                }
            }), 20));
            guideSteps.add(new GuideStep(null, mc -> GuideScreen.setShowroomArcAge(80F), 1));
            page("progress_home_arc", null, null, 0, -1);
            guideSteps.add(new GuideStep(null, mc -> GuideScreen.setShowroomArcAge(-1F), 1));
            page("progress_home", null, null, 0, -1);
            page("progress_sidebar", "elements/rituals", null, 0, -1);
            // 开着书时做完一步：书停在「地面仪式」，撤掉再发第一章的两步（元素晶石、机器零件），左栏那两行的 ✓ 上各闪一下（冻结在 80ms）
            guideSteps.add(new GuideStep(null, mc -> server.execute(() -> {
                for (String id : List.of("guide/elemental_gem", "guide/absorber_parts")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "advancement revoke @a only " + EssentialCraft5.MODID + ":" + id);
                }
            }), 15));
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.setShowroomArcAge(80F);
                server.execute(() -> {
                    for (String id : List.of("guide/elemental_gem", "guide/absorber_parts")) {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                                "advancement grant @a only " + EssentialCraft5.MODID + ":" + id);
                    }
                });
            }, 15));
            guideSteps.add(new GuideStep("progress_sidebar_arc", mc -> mc.getToasts().clear(), 1));
            guideSteps.add(new GuideStep(null, mc -> GuideScreen.setShowroomArcAge(-1F), 1));
            // 搜索
            guideSteps.add(new GuideStep(null, mc -> {
                mc.setScreen(null);
                GuideScreenOpener.open(null);
            }, 25));
            guideSteps.add(new GuideStep("search_soul_stone", mc -> {
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomSearch("灵魂石");
                }
            }, 15));
            // 查阅：开着物品栏按 Ctrl 查阅灵魂石（短开场 + 目标块亮一下），关掉后回到物品栏
            guideSteps.add(new GuideStep(null, mc -> {
                mc.setScreen(null);
                mc.getToasts().clear();
                if (mc.player != null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                }
            }, 10));
            guideSteps.add(new GuideStep("lookup_open", mc -> {
                ItemStack stone = new ItemStack(com.flameforge.essentialcraft5.registry.ECBootstrap.SOUL_STONE.get());
                if (!GuideClient.lookup(stone, mc.screen)) {
                    EssentialCraft5.LOGGER.warn("[showroom] 研究手册里查不到灵魂石");
                }
            }, 10));
            guideSteps.add(new GuideStep(null, mc -> {
                if (mc.screen instanceof GuideScreen screen) {
                    screen.onClose();
                }
            }, 30));
            guideSteps.add(new GuideStep("lookup_back", mc -> EssentialCraft5.LOGGER.info("[showroom] 查阅关掉后的界面：{}",
                    mc.screen == null ? "无" : mc.screen.getClass().getSimpleName()), 2));
            guideSteps.add(new GuideStep(null, mc -> mc.setScreen(null), 2));
        }
    }

    /** 第六批 6a：海纳尔章（草稿）与特殊饰品的两条新条目 */
    private static void hoannaGuidePages() {
        page("hoanna_legend", "hoanna/legend", null, 0, -1);
        page("hoanna_open_gate", "hoanna/open_gate", "steps", 0, -1);
        page("hoanna_sky", "hoanna/sky", null, 0, -1);
        page("hoanna_lands_table", "hoanna/lands", "table", 0, -1);
        page("hoanna_flora_items", "hoanna/flora", "items", 0, -1);
        page("hoanna_radiation_kv", "hoanna/radiation", "kv", 0, -1);
        page("hoanna_events", "hoanna/events", null, 0, -1);
        page("hoanna_ruins", "hoanna/ruins", null, 0, -1);
        page("hoanna_relics_kv", "hoanna/relics", "kv", 0, -1);
        page("hoanna_void_stone_recipe", "hoanna/void_stone", "recipe", 0, -1);
        page("hoanna_void_plating_recipe", "hoanna/void_plating", "recipe", 0, -1);
        page("baubles_radiation", "baubles/radiation", "list", 0, -1);
        page("baubles_mystical_accessory", "baubles/mystical_accessory", "kv", 0, -1);
    }

    /** 第六批 6b：新支线章「虚空机器」五条、物流章两条新条目、改过的岩浆熔炉速度表、无尽道具章 */
    private static void voidGuidePages(MinecraftServer server) {
        page("void_machines_ultra_heat_generator", "void_machines/ultra_heat_generator", "table", 0, -1);
        page("void_machines_ultra_natural_furnace", "void_machines/ultra_natural_furnace", "table", 0, -1);
        page("void_machines_magical_furnace", "void_machines/magical_furnace", "structure", 0, -1);
        page("void_machines_magical_teleporter", "void_machines/magical_teleporter", "structure", 0, -1);
        page("void_machines_magical_teleporter_formula", "void_machines/magical_teleporter", "formula", 0, -1);
        page("void_machines_magical_builder", "void_machines/magical_builder", "table", 0, -1);
        page("logistics_advanced_filters", "logistics/advanced_filters", "table", 0, -1);
        page("logistics_void_chest", "logistics/void_chest", null, 0, -1);
        page("magmatic_furnace_upgrades_table", "magmatic/furnace_upgrades", "table", 0, -1);
        page("creative_creative_items", "creative/creative_items", null, 0, -1);
        // 无尽道具章不带游戏模式条件：首页创造 / 生存各拍一张，两张都该有这一格；生存里直接开这一章照样能读；
        // 停在首页时切回创造、不关书，书按 GuideView.isStale（比的是 instabuild）重建一次，画面应当不变
        page("void_home_creative", null, null, 0, -1);
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> gameMode(server, "survival")), 10));
        page("void_home_survival", null, null, 0, -1);
        page("void_creative_entry_in_survival", "creative/creative_items", null, 0, -1);
        page("void_home_survival_again", null, null, 0, -1);
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> gameMode(server, "creative")), 20));
        guideSteps.add(new GuideStep("void_home_creative_without_reopen", mc -> mc.getToasts().clear(), 1));
    }

    /**
     * 第七批：新主线章「恶魔」八条、灵魂五芒星 2 / 3 档、虚空套装、余烬锻炉与剑、改过的旧条目（五种水晶柱表、枪械材料表、凿子底材表、数值速查、矩阵类），
     * 以及开着物品栏按 Ctrl 查阅带 NBT 的被俘灵魂、锻好的剑（查得到才会翻到对应条目；被俘灵魂的物品提示在 02_gui_tooltip_* 拍）
     */
    private static void demonGuidePages() {
        page("demons_void_crystal_pillar", "demons/void_crystal_pillar", "recipe", 0, -1);
        page("demons_demonic_pentacle", "demons/demonic_pentacle", null, 0, -1);
        page("demons_demonic_pentacle_structure", "demons/demonic_pentacle", "structure", 0, -1);
        page("demons_demonic_pentacle_structure_layer0", "demons/demonic_pentacle", "structure", 0, 0);
        page("demons_summoning", "demons/summoning", "steps", 0, -1);
        page("demons_demon_entity", "demons/demon", "entity", 0, -1);
        page("demons_trade", "demons/trade", null, 0, -1);
        page("demons_trade_steps", "demons/trade", "steps", 0, -1);
        page("demons_soul_scriber", "demons/soul_scriber", null, 0, -1);
        page("demons_ackronite", "demons/ackronite", null, 0, -1);
        page("demons_demonic_plating", "demons/demonic_plating", null, 0, -1);
        page("pentacle_tier_two", "pentacle/tier_two", "structure", 0, -1);
        page("pentacle_tier_three", "pentacle/tier_three", "structure", 0, -1);
        page("gear_void_armor", "gear/void_armor", "kv", 0, -1);
        page("gear_void_armor_top", "gear/void_armor", null, 0, -1);
        page("void_machines_ember_forge", "void_machines/ember_forge", "structure", 0, -1);
        page("void_machines_ember_sword", "void_machines/ember_sword", "table", 0, -1);
        page("void_machines_ember_sword_combos", "void_machines/ember_sword", "table", 1, -1);
        page("mithriline_crystal_pillar_table", "mithriline/crystal_pillar", "table", 0, -1);
        page("guns_parts_table_ackronite", "guns/parts", "table", 0, -1);
        page("decorations_chisel_table_demonic", "decorations/magical_chisel", "table", 0, -1);
        page("start_numbers_demons", "start/numbers", "kv", 4, -1);
        page("corruption_matrix", "corruption/matrix", null, 0, -1);
        // 按 Ctrl 查阅：带实体 NBT 的被俘灵魂（显示名改过）、锻好的剑（带主副元素 NBT），都该翻到各自的条目
        lookupStep("lookup_captured_soul", () -> CapturedSoulItem.of(EntityType.VILLAGER, 3));
        lookupStep("lookup_forged_sword", () -> DevShowroomDemon.sword(ForgedSwordData.Element.FIRE, ForgedSwordData.Element.WATER));
    }

    /**
     * 第八批：新主线章「全息」七条、「宅男套装」九条（含终章）、改过的旧条目（入门概览、数值速查、物流章「主线之后」、阿克罗尼特的用处），
     * 查阅数学核心；最后给玩家发全部进度拍首页底部条「主线已完成」，拍完撤掉（后面的页照常是没打勾的样子）
     */
    private static void hologramGuidePages(MinecraftServer server) {
        page("hologram_neuronic_plating", "hologram/neuronic_plating", null, 0, -1);
        page("hologram_holopad_recipe", "hologram/holopad", "recipe", 0, -1);
        page("hologram_summoning", "hologram/summoning", null, 0, -1);
        page("hologram_hologram", "hologram/hologram", null, 0, -1);
        page("hologram_hologram_table", "hologram/hologram", "table", 0, -1);
        page("hologram_math_cores", "hologram/math_cores", null, 0, -1);
        page("hologram_orbital_remote", "hologram/orbital_remote", "kv", 0, -1);
        page("hologram_divider_gun", "hologram/divider_gun", null, 0, -1);
        page("computer_force_parts", "computer/force_parts", null, 0, -1);
        page("computer_computer_engine", "computer/computer_engine", "recipe", 0, -1);
        page("computer_suit", "computer/suit", null, 0, -1);
        page("computer_suit_table", "computer/suit", "table", 0, -1);
        page("computer_antigravity_board", "computer/antigravity_board", null, 0, -1);
        page("computer_costs", "computer/costs", "table", 0, -1);
        page("computer_epilogue", "computer/epilogue", null, 0, -1);
        page("start_overview", "start/overview", null, 0, -1);
        page("start_numbers_hologram", "start/numbers", "kv", 5, -1);
        page("logistics_after_main_line", "logistics/after_main_line", null, 0, -1);
        page("demons_ackronite_uses", "demons/ackronite", "list", 0, -1);
        lookupStep("lookup_addition_core", () -> new ItemStack(com.flameforge.essentialcraft5.registry.ECComputer.ADDITION_CORE.get()));
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement grant @a everything")), 40));
        page("home_main_done", null, null, 0, -1);
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement revoke @a everything")), 20));
    }

    /**
     * 第九批：支线「地脉投影」新加的两条（凝聚器的篇首、下界之星的柱子摆法、价格表），以及改过的旧条目（投影仪篇末「后期」、
     * 数学核心多出来的那段、宅男套装「全套要多少」、物流章「主线之后」的地脉那一行）；按住 Ctrl 查阅凝聚器
     */
    /**
     * 第十批 10a：饰品封禁场一篇（开头、耗电 kv、「破法」列表、进阶细节）、附录「与原作的不同」七篇里表格最长的几处
     * （changed 的「风、饰品与腐化」表、new 的封禁场那一行所在的表、how 的按键表、removed 第一列较长的表）、入门里新加的「玩过原作？」。
     */
    private static void sealGuidePages() {
        page("computer_seal_field", "computer/seal_field", null, 0, -1);
        page("computer_seal_field_kv", "computer/seal_field", "kv", 0, -1);
        page("computer_seal_field_counter", "computer/seal_field", "list", 2, -1);
        page("computer_seal_field_fine_print", "computer/seal_field", "list", 3, -1);
        page("from_ec4_overview", "from_ec4/overview", null, 0, -1);
        page("from_ec4_where", "from_ec4/where", "table", 0, -1);
        page("from_ec4_new", "from_ec4/new", "table", 0, -1);
        page("from_ec4_changed", "from_ec4/changed", "table", 0, -1);
        page("from_ec4_changed_baubles", "from_ec4/changed", "table", 2, -1);
        page("from_ec4_removed", "from_ec4/removed", "table", 0, -1);
        page("from_ec4_fixed", "from_ec4/fixed", "table", 0, -1);
        page("from_ec4_how_keys", "from_ec4/how", "table", 3, -1);
        page("start_overview_from_ec4", "start/overview", "tip", 1, -1);
    }

    /**
     * 第十批 10b：首页（主线必做步骤的 x/y 跟着变长）、新条目（风之心愿的八种心愿表、号角、大法师、产线、坍缩、净化、透镜、嵌刻、
     * 风之契约两篇、全息诱饵）、改过的旧条目（定向培育、风的真名、二分枪的数值表、遥控器的锁定、余烬两对的组合表）、
     * 入门「主线大致路线」与速查的风之心愿一节、附录「与原作的不同」里海纳尔那两张表
     */
    private static void trialsGuidePages() {
        page("trials_home", null, null, 0, -1);
        page("wind_wishes", "wind/wishes", null, 0, -1);
        page("wind_wishes_table", "wind/wishes", "table", 0, -1);
        page("wind_wind_horn", "wind/wind_horn", null, 0, -1);
        page("wind_wind_horn_list", "wind/wind_horn", "list", 0, -1);
        page("wind_archmage", "wind/archmage", null, 0, -1);
        page("wind_windbound_top", "wind/windbound", null, 0, -1);
        page("hoanna_wind_name", "hoanna/wind_name", null, 0, -1);
        page("hoanna_collapse", "hoanna/collapse", null, 0, -1);
        page("hoanna_collapse_steps", "hoanna/collapse", "steps", 0, -1);
        page("automation_assembly_line", "automation/assembly_line", null, 0, -1);
        page("demons_purge", "demons/purge", null, 0, -1);
        page("essence_crystal_controller", "essence/crystal_controller", null, 0, -1);
        page("guns_lens_effects", "guns/lens_effects", null, 0, -1);
        page("void_machines_ember_inlay", "void_machines/ember_inlay", null, 0, -1);
        page("void_machines_ember_sword_table", "void_machines/ember_sword", "table", 1, -1);
        page("wind_mage_steadfast", "wind_mage/steadfast", null, 0, -1);
        page("wind_mage_deflect", "wind_mage/deflect", null, 0, -1);
        page("hologram_decoy", "hologram/decoy", null, 0, -1);
        page("hologram_divider_gun_kv", "hologram/divider_gun", "kv", 0, -1);
        page("hologram_orbital_remote_lock", "hologram/orbital_remote", "list", 0, -1);
        page("start_overview_route", "start/overview", "table", 0, -1);
        page("start_numbers_wind", "start/numbers", "kv", 4, -1);
        page("from_ec4_new_hoanna", "from_ec4/new", "table", 1, -1);
        page("from_ec4_changed_hoanna", "from_ec4/changed", "table", 3, -1);
    }

    /**
     * 第十批 10c：首页（主线 21 章）、第十九章审判庭九篇、第二十章宅男套装改过的几处（「穿齐四件」末尾的模块槽、力场穹顶一篇、改成过渡的尾声）、
     * 第二十一章终端与模块全部（含主线终点的尾声）、入门「主线大致路线」与速查的审判庭 / 模块两节、矩阵损伤「从哪来」表、附录海纳尔表；
     * 按住 Ctrl 查阅空白模块；最后把进度全给上拍首页「主线已完成」（门开着时指向终端与模块的尾声）
     */
    private static void modulesGuidePages(MinecraftServer server) {
        page("c10_home", null, null, 0, -1);
        page("c10_inquisitorium_legend", "inquisitorium/legend", null, 0, -1);
        page("c10_inquisitorium_glitchy_core", "inquisitorium/glitchy_core", null, 0, -1);
        page("c10_inquisitorium_decoding", "inquisitorium/decoding", null, 0, -1);
        page("c10_inquisitorium_journey", "inquisitorium/journey", null, 0, -1);
        page("c10_inquisitorium_beacon", "inquisitorium/beacon", null, 0, -1);
        page("c10_inquisitorium_beacon_recipe", "inquisitorium/beacon", "recipe", 0, -1);
        page("c10_inquisitorium_sentinel", "inquisitorium/sentinel", null, 0, -1);
        page("c10_inquisitorium_purify", "inquisitorium/purify", null, 0, -1);
        page("c10_inquisitorium_chip", "inquisitorium/chip", null, 0, -1);
        page("c10_inquisitorium_armor", "inquisitorium/armor", null, 0, -1);
        page("c10_computer_suit_full_set", "computer/suit", "list", 0, -1);
        page("c10_computer_force_dome", "computer/force_dome", null, 0, -1);
        page("c10_computer_force_dome_gui", "computer/force_dome", "list", 1, -1);
        page("c10_computer_epilogue", "computer/epilogue", null, 0, -1);
        page("c10_terminal_legend", "terminal/legend", null, 0, -1);
        page("c10_terminal_legend_table", "terminal/legend", "table", 0, -1);
        page("c10_terminal_blank_module", "terminal/blank_module", null, 0, -1);
        page("c10_terminal_install", "terminal/install", null, 0, -1);
        page("c10_terminal_modules", "terminal/modules", null, 0, -1);
        page("c10_terminal_rewind", "terminal/rewind", null, 0, -1);
        page("c10_terminal_matrix_filter", "terminal/matrix_filter", null, 0, -1);
        page("c10_terminal_matrix_shunt", "terminal/matrix_shunt", null, 0, -1);
        page("c10_terminal_accumulator", "terminal/accumulator", null, 0, -1);
        page("c10_terminal_shred", "terminal/shred", null, 0, -1);
        page("c10_terminal_phase_anchor", "terminal/phase_anchor", null, 0, -1);
        page("c10_terminal_epilogue", "terminal/epilogue", null, 0, -1);
        page("c10_start_overview_route", "start/overview", "table", 0, -1);
        page("c10_start_numbers_inquisitorium", "start/numbers", "h", 9, -1);
        page("c10_start_numbers_modules", "start/numbers", "h", 11, -1);
        page("c10_corruption_matrix_damage_sources", "corruption/matrix_damage", "table", 0, -1);
        page("c10_hologram_math_cores_modules", "hologram/math_cores", "list", 0, -1);
        page("c10_from_ec4_new_hoanna", "from_ec4/new", "table", 1, -1);
        lookupStep("c10_lookup_blank_module", () -> new ItemStack(com.flameforge.essentialcraft5.registry.ECModules.BLANK_MODULE.get()));
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement grant @a everything")), 40));
        page("c10_home_main_done", null, null, 0, -1);
        guideSteps.add(new GuideStep(null, mc -> server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "advancement revoke @a everything")), 20));
    }

    /** 第十一批：MRU 转换器一章、无尽档、附录新加的行；扣血改按最大生命以后的几处表格；封禁场「进阶细节」 */
    /** 题记块：篇首就是题记，按条目打开即可（最长的海纳尔带缺字、最短的装饰、公文体的全息与审判庭、风的口语、墓志、序） */
    private static void quoteGuidePages() {
        page("quote_start", "start/overview", null, 0, -1);
        page("quote_hoanna", "hoanna/legend", null, 0, -1);
        page("quote_solar", "solar/sun_imbued_glass", null, 0, -1);
        page("quote_decorations", "decorations/magical_chisel", null, 0, -1);
        page("quote_hologram", "hologram/neuronic_plating", null, 0, -1);
        page("quote_inquisitorium", "inquisitorium/legend", null, 0, -1);
        page("quote_wind", "wind/wind_rune", null, 0, -1);
        page("quote_corruption", "corruption/matrix_damage", null, 0, -1);
    }

    /**
     * 第十二批：新条目所在的每一章翻一页（农牧自动化、精华与焦点、装备与防护、腐化效果、终端与模块），
     * 古卷一章翻说明页和一篇卷文（{@link DevShowroomBatch12#SHOWN_VOLUME}，进世界时已记成读过）
     */
    private static void batch12GuidePages() {
        page("b12_farming_magical_cultivator", "farming/magical_cultivator", null, 0, -1);
        page("b12_essence_elemental_decomposer", "essence/elemental_decomposer", null, 0, -1);
        page("b12_gear_essential_tools", "gear/essential_tools", null, 0, -1);
        page("b12_corruption_matrix_tuner", "corruption/matrix_tuner", null, 0, -1);
        page("b12_terminal_attraction", "terminal/attraction", null, 0, -1);
        page("b12_scrolls_scrolls", "scrolls/scrolls", null, 0, -1);
        page("b12_scrolls_book_" + DevShowroomBatch12.SHOWN_VOLUME, "scrolls/book_" + DevShowroomBatch12.SHOWN_VOLUME, null, 0, -1);
    }

    /**
     * 第十三批 13a：首页（「矩阵 · n阶」那一行）、矩阵速查和同调两章的几篇、凝结和契约之誉两篇；
     * 展示玩家这时是七阶（前面拍界面时最后设的，见 DevShowroomBatch13a 神圣重锤那张提示框）
     */
    private static void batch13aGuidePages() {
        page("b13a_home", null, null, 0, -1);
        page("b13a_matrix_overview", "matrix/overview", null, 0, -1);
        page("b13a_matrix_overview_table", "matrix/overview", "table", 0, -1);
        page("b13a_matrix_ranks", "matrix/ranks", null, 0, -1);
        page("b13a_matrix_crucible", "matrix/crucible", null, 0, -1);
        page("b13a_attunement_overview", "attunement/overview", null, 0, -1);
        page("b13a_attunement_computer_armor", "attunement/computer_armor", null, 0, -1);
        page("b13a_attunement_computer_armor_recipe", "attunement/computer_armor", "recipe", 0, -1);
        page("b13a_pentacle_condensed", "pentacle/condensed", null, 0, -1);
        page("b13a_demons_standing", "demons/standing", null, 0, -1);
    }

    /**
     * 第十三批 13b：同调一章新加的六篇（各套盔甲、余烬元素剑、裁决之刃和它的升格配方）、四套盔甲本身那几篇、充能仓（给穿着的盔甲充电、电量条）
     */
    private static void batch13bGuidePages() {
        page("b13b_attunement_mhs_armor", "attunement/mhs_armor", null, 0, -1);
        page("b13b_attunement_mhs_armor_table", "attunement/mhs_armor", "table", 0, -1);
        page("b13b_attunement_wind_armor", "attunement/wind_armor", null, 0, -1);
        page("b13b_attunement_wind_armor_table", "attunement/wind_armor", "table", 0, -1);
        page("b13b_attunement_void_armor", "attunement/void_armor", null, 0, -1);
        page("b13b_attunement_inquisitorium_armor", "attunement/inquisitorium_armor", null, 0, -1);
        page("b13b_attunement_ember_sword", "attunement/ember_sword", null, 0, -1);
        page("b13b_attunement_verdict_blade", "attunement/verdict_blade", null, 0, -1);
        page("b13b_attunement_verdict_blade_recipe", "attunement/verdict_blade", "recipe", 0, -1);
        page("b13b_attunement_verdict_blade_judgment", "attunement/verdict_blade", "kv", 0, -1);
        page("b13b_gear_mhs_armor", "gear/mhs_armor", null, 0, -1);
        page("b13b_gear_void_armor", "gear/void_armor", null, 0, -1);
        page("b13b_wind_gear_wind_armor", "wind_gear/wind_armor", null, 0, -1);
        page("b13b_inquisitorium_armor", "inquisitorium/armor", null, 0, -1);
        page("b13b_storage_charging_chamber", "storage/charging_chamber", null, 0, -1);
        page("b13b_storage_charging_chamber_worn", "storage/charging_chamber", "h", 1, -1);
    }

    /**
     * 第十三批 13c：异兽录总述和七篇（篇首带实体预览：游灵、游魂、风暴术士（风法师 {Type:3b}）、小鬼、潜行者、魔像、矩阵之影），
     * 两只首领的招式表 / 三相表，风法师一篇补的风暴术士那段、腐化一章的矩阵游魂来源行
     */
    private static void batch13cGuidePages() {
        page("b13c_bestiary_overview", "bestiary/overview", null, 0, -1);
        page("b13c_bestiary_overview_elite", "bestiary/overview", "kv", 0, -1);
        for (String id : List.of("elemental_wisp", "matrix_wraith", "storm_sorcerer", "nether_imp", "void_stalker", "mru_golem", "matrix_shade")) {
            page("b13c_bestiary_" + id, "bestiary/" + id, null, 0, -1);
        }
        page("b13c_bestiary_mru_golem_moves", "bestiary/mru_golem", "table", 0, -1);
        page("b13c_bestiary_matrix_shade_phases", "bestiary/matrix_shade", "table", 0, -1);
        page("b13c_bestiary_matrix_shade_recipe", "bestiary/matrix_shade", "recipe", 0, -1);
        page("b13c_wind_mage_storm_sorcerer", "wind_mage/wind_mages", "table", 0, -1);
        page("b13c_corruption_matrix_damage_sources", "corruption/matrix_damage", "table", 0, -1);
    }

    /**
     * 第十三批 13d：海纳尔探索一章十一篇（潮汐公式、特产表、风暴、天灾窗口、勘测、渊盐、中景观、巢穴各补一张表 / 列表页），
     * 异兽录的八种海纳尔本地怪（篇首带实体预览，畸变母巢多一张幼体预览），海纳尔一章补了 13d 内容的地貌表和天灾那页
     */
    private static void batch13dGuidePages() {
        for (String id : List.of("overview", "tide", "specialties", "ley_storm", "calamity_windows", "hotspots", "survey", "survey_trip",
                "abyssal_salt", "midscapes", "nests")) {
            page("b13d_hoanna_explore_" + id, "hoanna_explore/" + id, null, 0, -1);
        }
        page("b13d_hoanna_explore_tide_formula", "hoanna_explore/tide", "formula", 0, -1);
        page("b13d_hoanna_explore_specialties_table", "hoanna_explore/specialties", "table", 0, -1);
        page("b13d_hoanna_explore_ley_storm_kv", "hoanna_explore/ley_storm", "kv", 0, -1);
        page("b13d_hoanna_explore_calamity_table", "hoanna_explore/calamity_windows", "table", 0, -1);
        page("b13d_hoanna_explore_survey_recipe", "hoanna_explore/survey", "recipe", 0, -1);
        page("b13d_hoanna_explore_midscapes_table", "hoanna_explore/midscapes", "table", 0, -1);
        page("b13d_hoanna_explore_nests_table", "hoanna_explore/nests", "table", 0, -1);
        for (String id : List.of("frostbitten", "mana_prism", "chaos_aberrant", "ember_spirit", "hoanna_scavenger", "abyssal_eel",
                "inquisitorium_patroller", "catacomb_revenant")) {
            page("b13d_bestiary_" + id, "bestiary/" + id, null, 0, -1);
        }
        page("b13d_bestiary_chaos_larva", "bestiary/chaos_aberrant", "entity", 1, -1);
        page("b13d_hoanna_lands_table", "hoanna/lands", "table", 0, -1);
        page("b13d_hoanna_events_table", "hoanna/events", "table", 0, -1);
    }

    private static void batch13eGuidePages() {
        for (String id : List.of("intro", "attune", "umbral", "shade_core", "shade_pillar", "tier4", "oath", "rank_8")) {
            page("b13e_shade_" + id, "shade/" + id, null, 0, -1);
        }
        page("b13e_shade_tier4_structure", "shade/tier4", "structure", 0, -1);
        page("b13e_shade_shade_pillar_recipe", "shade/shade_pillar", "recipe", 0, -1);
        for (String id : List.of("intro", "beacon", "summon", "fight", "rewards", "rank_9", "ascension", "epilogue")) {
            page("b13e_apex_" + id, "apex/" + id, null, 0, -1);
        }
        page("b13e_apex_beacon_recipe", "apex/beacon", "recipe", 0, -1);
        for (String id : List.of("sealed_vault", "sunken_district", "inquisitorium_outpost")) {
            page("b13e_hoanna_explore_" + id, "hoanna_explore/" + id, null, 0, -1);
        }
        for (String id : List.of("town_sentry", "abyss_tyrant", "calamity_heart")) {
            page("b13e_bestiary_" + id, "bestiary/" + id, null, 0, -1);
            page("b13e_bestiary_" + id + "_entity", "bestiary/" + id, "entity", 0, -1);
        }
    }

    private static void batch11GuidePages() {
        structure3dGuidePages();
        page("converter_mru_converter", "converter/mru_converter", null, 0, -1);
        page("converter_mru_converter_recipe", "converter/mru_converter", "recipe", 0, -1);
        page("converter_energy", "converter/energy", null, 0, -1);
        page("converter_energy_formula", "converter/energy", "formula", 0, -1);
        page("converter_mana", "converter/mana", null, 0, -1);
        page("converter_upgrades", "converter/upgrades", null, 0, -1);
        page("converter_upgrades_table", "converter/upgrades", "table", 0, -1);
        page("converter_upgrades_table2", "converter/upgrades", "table", 1, -1);
        page("creative_items_converter", "creative/creative_items", "h", 1, -1);
        page("from_ec4_new_b11", "from_ec4/new", "table", 0, -1);
        page("from_ec4_changed_b11", "from_ec4/changed", "table", 2, -1);
        page("from_ec4_changed_b11_hoanna", "from_ec4/changed", "table", 3, -1);
        page("corruption_body_b11", "corruption/body", "table", 0, -1);
        page("corruption_matrix_b11", "corruption/matrix", "table", 0, -1);
        page("hoanna_radiation_b11", "hoanna/radiation", "kv", 0, -1);
        page("start_numbers_b11", "start/numbers", "h", 6, -1);
        page("start_mrucu_b11", "start/mrucu", null, 0, -1);
        page("computer_seal_field_fine_print_b11", "computer/seal_field", "list", 3, -1);
        lookupStep("lookup_mru_converter", () -> new ItemStack(com.flameforge.essentialcraft5.registry.ECConverters.MRU_CONVERTER.get()));
    }

    /** 立体结构图：代码登记的、JSON 多层 / 单层的、带流体和空气格的、三格高水晶柱的、靠渲染器画静止一帧的（元素水晶、魔力镜）；切层、转角度、切回平面图各一张 */
    private static void structure3dGuidePages() {
        page("s3d_wind_rune", "wind/wind_rune", "structure", 0, -1);
        page("s3d_mithriline_furnace", "mithriline/furnace", "structure", 0, -1);
        page("s3d_mithriline_furnace_cut0", "mithriline/furnace", "structure", 0, 0);
        page("s3d_ley_condenser", "ley/ley_condenser", "structure", 0, -1);
        page("s3d_solar_prism_code", "solar/solar_prism", "structure", 0, -1);
        page("s3d_solar_prism_layers", "solar/solar_prism", "structure", 1, -1);
        page("s3d_heat_generator", "generators/heat_generator", "structure", 0, -1);
        page("s3d_enrichment_controller", "enrichment/controller", "structure", 0, -1);
        page("s3d_pentacle_tier_three", "pentacle/tier_three", "structure", 0, -1);
        page("s3d_demonic_pentacle", "demons/demonic_pentacle", "structure", 0, -1);
        page("s3d_tesla_coil", "tesla/mru_coil", "structure", 0, -1);
        page("s3d_reactor", "tesla/reactor", "structure", 0, -1);
        page("s3d_ember_forge", "void_machines/ember_forge", "structure", 0, -1);
        // 水晶柱一圈（增幅 / 升华）、书架范围、冷源摆法、贴着元素水晶的两台、镜子在容器上方
        page("s3d_enchant_amplifier", "devices/enchant_amplifier", "structure", 0, -1);
        page("s3d_enchant_ascender", "devices/enchant_ascender", "structure", 0, -1);
        page("s3d_magical_enchanter", "devices/magical_enchanter", "structure", 0, -1);
        page("s3d_cold_distillator", "generators/cold_distillator", "structure", 0, -1);
        page("s3d_crystal_extractor", "essence/crystal_extractor", "structure", 0, -1);
        page("s3d_crystal_controller", "essence/crystal_controller", "structure", 0, -1);
        page("s3d_magical_mirror", "logistics/magical_mirror", "structure", 0, -1);
        structureAngles("s3d_mithriline_furnace_top", "mithriline/furnace", 20F, 80F);
        structureAngles("s3d_wind_rune_side", "wind/wind_rune", 10F, 8F);
        structureHover("s3d_wind_rune_hover", "wind/wind_rune");
        structureHover("s3d_solar_prism_hover", "solar/solar_prism");
        guideSteps.add(new GuideStep(null, mc -> GuideScreen.showroomStructureFlat(true), 1));
        page("s3d_flat_mithriline_furnace", "mithriline/furnace", "structure", 0, -1);
        guideSteps.add(new GuideStep(null, mc -> GuideScreen.showroomStructureFlat(false), 1));
    }

    /** 翻到那一篇的第一张结构图，假鼠标放到模型区正中拍悬停（描框和提示），拍完挪开 */
    private static void structureHover(String name, String entry) {
        page(null, entry, "structure", 0, -1);
        guideSteps.add(new GuideStep(null, mc -> {
            int[] at = GuideScreen.showroomStructureCenter();
            if (at != null) {
                GuideScreen.showroomMouse(at[0], at[1]);
            }
        }, 4));
        guideSteps.add(new GuideStep(name, mc -> { }, 2));
        guideSteps.add(new GuideStep(null, mc -> GuideScreen.showroomMouse(1, 1), 1));
    }

    /** 翻到那一篇的第一张结构图，转到给定角度再拍 */
    private static void structureAngles(String name, String entry, float yaw, float pitch) {
        page(null, entry, "structure", 0, -1);
        guideSteps.add(new GuideStep(null, mc -> {
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomStructureAngles(yaw, pitch);
            }
        }, 3));
        guideSteps.add(new GuideStep(name, mc -> { }, 2));
    }

    private static void condenserGuidePages() {
        page("ley_condenser", "ley/ley_condenser", null, 0, -1);
        page("ley_condenser_recipe", "ley/ley_condenser", "recipe", 0, -1);
        page("ley_condenser_structure", "ley/ley_condenser", "structure", 0, -1);
        page("ley_condenser_list", "ley/ley_condenser", "list", 0, -1);
        page("ley_condensing", "ley/condensing", null, 0, -1);
        page("ley_condensing_table", "ley/condensing", "table", 0, -1);
        page("ley_condensing_list", "ley/condensing", "list", 0, -1);
        page("ley_projector_later", "ley/ley_projector", "tip", 2, -1);
        page("hologram_math_cores_condenser", "hologram/math_cores", "p", 2, -1);
        page("computer_costs_condenser", "computer/costs", "p", 2, -1);
        page("logistics_after_main_line_ley", "logistics/after_main_line", "list", 0, -1);
        lookupStep("lookup_ley_condenser", () -> new ItemStack(com.flameforge.essentialcraft5.registry.ECLeyCondenser.LEY_CONDENSER.get()));
    }

    /** 开着物品栏查阅 stack（同灵魂石那张），查不到记一行；拍完关掉 */
    private static void lookupStep(String name, java.util.function.Supplier<ItemStack> stack) {
        guideSteps.add(new GuideStep(null, mc -> {
            mc.setScreen(null);
            mc.getToasts().clear();
            if (mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        }, 10));
        guideSteps.add(new GuideStep(name, mc -> {
            boolean found = GuideClient.lookup(stack.get(), mc.screen);
            EssentialCraft5.LOGGER.info("[showroom] 查阅 {}：{}", name, found ? "查到了" : "查不到");
        }, 30));
        guideSteps.add(new GuideStep(null, mc -> mc.setScreen(null), 2));
    }

    private static void gameMode(MinecraftServer server, String mode) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "gamemode " + mode + " @a");
        EssentialCraft5.LOGGER.info("[showroom] 游戏模式改成 {}", mode);
    }

    /**
     * 研究手册的开场 / 收场关键帧（手册默认档 {@link #GUIDE_KEY_ROUND} 那一轮拍，截图名不带轮名；冻结时电弧不走限速）：
     * <ul>
     *   <li>开场冻结在 {@link #GUIDE_INTRO_FRAMES} → {@code 03_guide_intro_<毫秒>}；</li>
     *   <li>查阅短开场（查阅灵魂石）冻结在 {@link #GUIDE_QUICK_FRAMES}（开场时间轴毫秒）→ {@code 03_guide_quick_<毫秒>}；</li>
     *   <li>收场：先跳过开场停在首页，冻结在 {@link #GUIDE_CLOSE_FRAMES} 再按关闭 → {@code 03_guide_close_<毫秒>}（620 是收线回到起笔点的火花）；</li>
     *   <li>换倍数看阅读位置锚定：「数值速查」滚到第 3 张键值表 → {@code anchor_x2}，换成 1 倍 → {@code anchor_x1}（那张表应当还在视口顶上）。</li>
     * </ul>
     */
    private static void guideKeyFrames() {
        for (int ms : GUIDE_INTRO_FRAMES) {
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.setShowroomFreeze(ms);
                mc.setScreen(null);
                mc.getToasts().clear();
                GuideScreenOpener.open(null);
            }, 8));
            guideSteps.add(new GuideStep("03_guide_intro_" + ms, mc -> mc.getToasts().clear(), 2));
        }
        for (int ms : GUIDE_QUICK_FRAMES) {
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.setShowroomFreeze(ms);
                mc.setScreen(null);
                mc.getToasts().clear();
                ItemStack stone = new ItemStack(com.flameforge.essentialcraft5.registry.ECBootstrap.SOUL_STONE.get());
                if (!GuideClient.lookup(stone, null)) {
                    EssentialCraft5.LOGGER.warn("[showroom] 研究手册里查不到灵魂石（短开场关键帧）");
                }
            }, 8));
            guideSteps.add(new GuideStep("03_guide_quick_" + ms, mc -> mc.getToasts().clear(), 2));
        }
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomFreeze(-1F);
            mc.setScreen(null);
        }, 2));
        for (int ms : GUIDE_CLOSE_FRAMES) {
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.setShowroomCloseFreeze(-1F);
                mc.setScreen(null);
                mc.getToasts().clear();
                GuideScreenOpener.open(null);
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomHome();
                }
            }, 6));
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.setShowroomCloseFreeze(ms);
                if (mc.screen instanceof GuideScreen screen) {
                    screen.onClose();
                }
            }, 3));
            guideSteps.add(new GuideStep("03_guide_close_" + ms, mc -> mc.getToasts().clear(), 1));
        }
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomCloseFreeze(-1F);
            mc.setScreen(null);
        }, 2));
        guideSteps.add(new GuideStep(null, mc -> {
            mc.setScreen(null);
            mc.getToasts().clear();
            GuideScreenOpener.open(null);
            if (mc.screen instanceof GuideScreen screen) {
                int block = screen.showroomFindBlock("start/numbers", "kv", 2);
                if (!screen.showroomOpen("start/numbers", block)) {
                    EssentialCraft5.LOGGER.warn("[showroom] 研究手册没有条目 start/numbers，锚定截图拍的是首页");
                }
            }
        }, 25));
        guideSteps.add(new GuideStep("anchor_x2", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomGuideScale(1);
            EssentialCraft5.LOGGER.info("[showroom] 研究手册换成 1 倍：{}", GuideScreen.showroomScaleInfo());
        }, 10));
        guideSteps.add(new GuideStep("anchor_x1", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomGuideScale(0);
            mc.setScreen(null);
        }, 2));
    }

    /**
     * 研究手册的电弧（见 GuideArcs）：翻页擦除冻结在 45%（首页 → 阅读页、阅读页 → 首页、以及「鼠标刚移上格子马上点」—— 悬停火花刚亮过，
     * 翻页优先，电弧照样有），悬停火花冻结在 60ms（首页格子、「下一步」条、后退按钮，用假鼠标，不动真鼠标），
     * 环境电弧四处各强制来一道（顶栏空白、底栏空白、外框上沿内侧、面板外留白；那一处看不见时换成外框上沿）。
     * 限速：普通事件两次亮起至少隔 350ms、任意一秒至多 3 次，相邻两次触发之间至少等 9 刻，连着几次之后等 25 刻让窗口清空
     */
    private static void arcSteps() {
        guideSteps.add(new GuideStep(null, mc -> {
            mc.setScreen(null);
            mc.getToasts().clear();
            GuideScreen.showroomMouse(1, 1);
            GuideScreenOpener.open(null);
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomHome();
            }
        }, 25));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomWipe(0.45F);
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomOpen("elements/rituals", -1);
            }
        }, 3));
        guideSteps.add(new GuideStep("wipe_arc_to_read", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> GuideScreen.setShowroomWipe(-1F), 10));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomWipe(0.45F);
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomHome();
            }
        }, 3));
        guideSteps.add(new GuideStep("wipe_arc_to_home", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> GuideScreen.setShowroomWipe(-1F), 25));
        // 鼠标刚移上格子（悬停火花亮起）、约 200ms 后就点下去：翻页电弧不被 350ms 间隔吃掉，悬停火花被收掉
        guideSteps.add(new GuideStep(null, mc -> {
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomClearArcs();
                screen.showroomHoverTile(2);
            }
        }, 0));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomWipe(0.45F);
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomOpen("elements/rituals", -1);
            }
        }, 3));
        guideSteps.add(new GuideStep("wipe_arc_after_hover", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomWipe(-1F);
            GuideScreen.showroomMouse(1, 1);
        }, 4));
        guideSteps.add(new GuideStep(null, mc -> {
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomHome();
            }
        }, 30));
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomArcAge(60F);
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomClearArcs();
                screen.showroomHoverTile(2);
            }
        }, 4));
        guideSteps.add(new GuideStep("hover_arc_tile", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> {
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomClearArcs();
                screen.showroomHoverContinue();
            }
        }, 9));
        guideSteps.add(new GuideStep("hover_arc_next", mc -> mc.getToasts().clear(), 1));
        guideSteps.add(new GuideStep(null, mc -> {
            if (mc.screen instanceof GuideScreen screen) {
                screen.showroomClearArcs();
                screen.showroomHoverNav(0);
            }
        }, 9));
        guideSteps.add(new GuideStep("hover_arc_nav", mc -> mc.getToasts().clear(), 1));
        String[] ambient = {"ambient_arc_header", "ambient_arc_footer", "ambient_arc_edge", "ambient_arc_outside"};
        for (int site = 0; site < ambient.length; site++) {
            int force = site;
            guideSteps.add(new GuideStep(null, mc -> {
                GuideScreen.showroomMouse(1, 1);
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomClearArcs();
                }
                GuideScreen.showroomAmbientArc(force);
            }, 9));
            guideSteps.add(new GuideStep(ambient[site], mc -> mc.getToasts().clear(), 1));
        }
        guideSteps.add(new GuideStep(null, mc -> {
            GuideScreen.setShowroomArcAge(-1F);
            GuideScreen.showroomMouse(-1, -1);
            mc.setScreen(null);
        }, 2));
    }

    /**
     * 拍一页：打开条目（null = 首页），滚到那一篇里第 nth 个 type 类型的块（null = 篇首），多层结构切到第 layer 层（-1 = 默认）
     */
    private static void page(String name, @Nullable String entry, @Nullable String type, int nth, int layer) {
        guideSteps.add(new GuideStep(null, mc -> {
            mc.setScreen(null);
            mc.getToasts().clear();
            GuideScreenOpener.open(null);
            if (entry == null && mc.screen instanceof GuideScreen screen) {
                screen.showroomHome();
            }
            if (entry != null && mc.screen instanceof GuideScreen screen) {
                int block = type == null ? -1 : screen.showroomFindBlock(entry, type, nth);
                if (type != null && block < 0) {
                    EssentialCraft5.LOGGER.warn("[showroom] 研究手册 {} 里没有第 {} 个 {} 块，拍篇首", entry, nth + 1, type);
                }
                if (!screen.showroomOpen(entry, block)) {
                    EssentialCraft5.LOGGER.warn("[showroom] 研究手册没有条目 {}，跳过", entry);
                }
            }
        }, 25));
        if (layer >= 0) {
            guideSteps.add(new GuideStep(null, mc -> {
                if (mc.screen instanceof GuideScreen screen) {
                    screen.showroomSelectLayer(layer);
                }
            }, 3));
        }
        guideSteps.add(new GuideStep(null, mc -> { }, 11));
        // 进世界头几秒原版的「新配方已解锁」提示会一直冒出来：拍之前清一次，免得盖住顶栏
        guideSteps.add(new GuideStep(name, mc -> mc.getToasts().clear(), 1));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "ec5_" + name + ".png", mc.getMainRenderTarget(),
                message -> EssentialCraft5.LOGGER.info("[showroom] 截图 {}", name));
        GuiAudit.record(mc, name);
    }
}
