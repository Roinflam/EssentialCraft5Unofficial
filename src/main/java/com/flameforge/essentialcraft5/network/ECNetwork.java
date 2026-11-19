package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * 本模组唯一的网络通道。
 * <p>
 * 规则（从原作的漏洞吸取的）：
 * <ul>
 *   <li>包 ID 只能追加在末尾；改任何包的格式、增删包都要升 {@link #PROTOCOL}。注意模块包（{@link #registerC2S} / {@link #registerS2C}）
 *       排在框架自带的包之后、末尾的配置重发包之前，加模块包会挪动重发包的 ID，所以也要升。</li>
 *   <li>C2S 包一律用 {@code ctx.getSender()} 认人，坐标/UUID/创造模式这类东西不信客户端给的，
 *       并校验玩家当前打开的菜单确实是对应机器。原作的五芒星按钮信客户端报的 isCreative、
 *       MIM 取物包信客户端给的 requester UUID、魔力之翼包带「坐标 + 任意玩家 UUID」，都是能被利用的洞。</li>
 *   <li><b>界面按钮不走自定义包</b>：一律 {@code AbstractContainerMenu#clickMenuButton}（客户端 {@code gameMode.handleInventoryButtonClick}），
 *       原版已经校验了「打开的就是这个菜单、stillValid、不是旁观者」，见 tools/plan/porting_guide.md §9。</li>
 *   <li>方块实体的渲染状态走原版方块实体更新包，界面数值走菜单的 ContainerData，不走这里。</li>
 * </ul>
 * 两端都必须装本模组（内容模组），所以协议版本严格相等。
 * <p>
 * <b>模块自己加包</b>（第三批框架 G5）：在注册类的 {@code init()} 里调 {@link #registerC2S} / {@link #registerS2C}（模组构造阶段），
 * ID 排在框架自带的包后面、按调用顺序分配（init 的顺序在 ECRegistries 里写死，两端一致）。C2S 的处理器已经在服务端主线程、
 * 拿到的是真的发送者；处理器里仍要自己校验一切（距离、手里 / 身上的物品、冷却），不信包里的任何东西。decoder 里读字符串用
 * {@code readUtf(最大长度)}、读集合先限长度，防恶意客户端发超大包。
 * <pre>
 * ECNetwork.registerC2S(C2SFooPacket.class, C2SFooPacket::encode, C2SFooPacket::decode, (packet, player) -&gt; …);
 * ECNetwork.registerS2C(S2CBarPacket.class, S2CBarPacket::encode, S2CBarPacket::decode, packet -&gt; BarClient.handle(packet));
 * </pre>
 * S2C 的 clientHandler 只在客户端调用；里面只调 client 包里类的静态方法（lambda 体里引用的类到执行时才加载，专用服不会碰到）。
 */
public final class ECNetwork {

    /**
     * 2：第三批加了魔力之翼开关包（C2S）与叫停包（S2C）；3：末尾加了配置热重载后的重发包（研究手册改版，2026-09-28）；
     * 4：第五批 ECGuns 登记了第一个模块包（枪械光束 S2C）——模块包排在框架自带的 3 个包之后、配置重发包之前，
     * 光束包拿 ID 3、重发包从 3 挪到 4（2026-09-29）；同批又加了矩阵受损特效包（S2C，ID 4），重发包挪到 5——第五批还没发布，协议号不再升；
     * 5：第六批 6a（海纳尔，ECHoanna）加了天灾状态包（S2C，ID 5）和强震包（S2C，ID 6），重发包挪到 7（2026-09-29）；
     * 6：第八批 ECComputer 加宅男套装的夜视 / 冲刺包（C2S，1 字节，模块包末尾），重发包随之后挪（2026-10-06）；
     * 同批又在它后面加了反重力板飞行状态包（S2C，实体 id + 1 字节），重发包再往后挪一位——第八批还没发布，协议号不再升；
     * 7：第十批的饰品封禁场（压制状态包 S2C、宅男终端两个包，都接在 ECComputer 的包后面），重发包随之后挪（2026-10-07）；
     * 同批又在压制状态包后面加了压制期间的单格同步包（S2C），终端两个包和重发包各往后挪一位——第十批还没发布，协议号不再升；
     * 8：第十批 10c 的宅男终端数据包（S2C）在带名单的包末尾加了「模块」页一段（包的个数、顺序没变）；
     * 9：第十二批加了地脉探测仪的扫描包（S2C，模块包末尾，ECLeyDetector 登记），配置重发包随之后挪（2026-10-19）；
     * 10：第十三批 13a 在框架自带的包里加了矩阵图谱包、矩阵状态包（S2C，接在魔力之翼两个包后面，ID 3、4），
     * 模块包和配置重发包整体后挪两位；普通玩家数据包不再带图谱（batch13.md §3.1.5，2026-10-26）；
     * 11：第十三批 13c 在框架自带的包里加了首领画面包 {@link S2CBossFxPacket}（S2C，接在矩阵状态包后面，ID 5），
     * 模块包和配置重发包整体后挪一位；玩家数据加了只在服务端的击杀台账，普通同步包不带它（batch13.md §9.3，2026-10-27）；
     * 12：第十三批 13d 的天灾状态包认得第五种天灾地脉风暴（事件 id 5），13d 各路还要登记模块包（地脉风暴、勘测图等），
     * 一次升到 12；玩家数据加了渊盐冷却、首领每日领奖记录，普通同步包不带（batch13.md §9.4，2026-11-01）；
     * 13：天灾之心加强（calamity_heart_v2.md）加了天灾封印状态包（S2C，模块包，ECCalamityHeart 登记），首领画面包多了三种类型
     * （开场名字、横幅、镜头震动）；第十三批还没发布，和 13a~13e 一起上（2026-11-02）；
     * 14：特效大改第 1 批在框架自带的包里加了首领预警包 {@link S2CBossWarningPacket}（S2C，接在首领画面包后面，ID 6），
     * 模块包和配置重发包整体后挪一位；首领预警不再撒原版粒子（2026-11-09）；同一版本里武器防具成长加强又加了装备特效事件包
     * {@link S2CGearFxPacket}（模块包，{@code ECAttunement.init} 登记，排在它之后的模块包和配置重发包再后挪一位）。14 还没上服，不再升号
     */
    public static final String PROTOCOL = "14";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            EssentialCraft5.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** 模块登记的包，{@link #register} 时按顺序接在框架自带的包后面 */
    private static final List<IntConsumer> MODULE_PACKETS = new ArrayList<>();
    private static boolean registered;

    private ECNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(S2CPlayerDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CPlayerDataPacket::encode)
                .decoder(S2CPlayerDataPacket::decode)
                .consumerMainThread(S2CPlayerDataPacket::handle)
                .add();
        // 第三批 G5：魔力之翼
        CHANNEL.messageBuilder(C2SWingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWingsPacket::encode)
                .decoder(C2SWingsPacket::decode)
                .consumerMainThread(C2SWingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CWingsStopPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CWingsStopPacket::encode)
                .decoder(S2CWingsStopPacket::decode)
                .consumerMainThread(S2CWingsStopPacket::handle)
                .add();
        // 第十三批 13a：矩阵图谱（登录 / 重生 / 换维度整份，平时只发新记的那条）、淬炼台界面要的矩阵状态
        CHANNEL.messageBuilder(S2CMatrixCodexPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMatrixCodexPacket::encode)
                .decoder(S2CMatrixCodexPacket::decode)
                .consumerMainThread(S2CMatrixCodexPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CMatrixStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMatrixStatusPacket::encode)
                .decoder(S2CMatrixStatusPacket::decode)
                .consumerMainThread(S2CMatrixStatusPacket::handle)
                .add();
        // 第十三批 13c：首领的整屏画面（矩阵之影的悖论画面；13e 的熄光、天色压暗、崩解倒计时），只发参战者
        CHANNEL.messageBuilder(S2CBossFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBossFxPacket::encode)
                .decoder(S2CBossFxPacket::decode)
                .consumerMainThread(S2CBossFxPacket::handle)
                .add();
        // 特效大改第 1 批（协议 14）：首领招式的预警，起手时发一次，客户端自己画完
        CHANNEL.messageBuilder(S2CBossWarningPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBossWarningPacket::encode)
                .decoder(S2CBossWarningPacket::decode)
                .consumerMainThread(S2CBossWarningPacket::handle)
                .add();
        for (IntConsumer packet : MODULE_PACKETS) {
            packet.accept(id++);
        }
        // 追加在最末（协议 3）：远程服热重载 SERVER 配置后重发 toml，客户端照握手的做法载入
        CHANNEL.messageBuilder(S2CConfigResyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CConfigResyncPacket::encode)
                .decoder(S2CConfigResyncPacket::decode)
                .consumerMainThread(S2CConfigResyncPacket::handle)
                .add();
    }

    /** 登记一个客户端 → 服务端的包（见类注释）。handler 在服务端主线程调用，player 是真正的发送者 */
    public static <T> void registerC2S(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                                       BiConsumer<T, ServerPlayer> handler) {
        checkOpen(type);
        MODULE_PACKETS.add(id -> CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((packet, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        handler.accept(packet, sender);
                    }
                })
                .add());
    }

    /** 登记一个服务端 → 客户端的包（见类注释）。clientHandler 在客户端主线程调用 */
    public static <T> void registerS2C(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                                       Consumer<T> clientHandler) {
        checkOpen(type);
        MODULE_PACKETS.add(id -> CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder)
                .decoder(decoder)
                .consumerMainThread((packet, ctx) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientHandler.accept(packet)))
                .add());
    }

    private static void checkOpen(Class<?> type) {
        if (registered) {
            throw new IllegalStateException("网络包要在模块 init() 里登记（模组构造阶段），已经晚了：" + type.getName());
        }
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 只能在客户端调 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
