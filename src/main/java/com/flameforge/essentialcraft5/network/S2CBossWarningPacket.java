package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.vfx.warn.BossWarningClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 附近的玩家：首领招式的预警（协议 14，特效大改第 1 批）和落下时的结算画面（第 2 批，{@link #RESOLVE}）。以前预警是服务端每 3~5 刻撒一圈原版粒子，
 * 现在起手时只发一个包，「多大、在哪、多久落下」都写在里面，客户端（{@code client/vfx/warn}）自己按时间画完整个预警；
 * 落下那一刻再发一个结算包，炸开的画面由客户端（{@code client/vfx/resolve}）画。
 * 只管画面：伤害、判定照旧全在服务端，客户端收不到包也不影响结算。
 * <p>
 * 格式：类型（1 字节）、所属实体 id、跟随实体 id（+1，0 = 不跟）、位置（3 × double）、两个尺寸、方向（3 × float）、
 * 预警时长、延迟（VarInt，刻）、主色、副色（RGB int）、标志（1 字节）。读的时候把尺寸、时长夹到合理范围，非有限数读成 0。
 * <p>
 * 类型只追加、不改已有的编号（改了要升 {@link ECNetwork#PROTOCOL}）：
 * <ul>
 *   <li>{@link #CANCEL}（0）：撤掉这个所属实体的全部预警（招式被打断、首领换相 / 散去），别的字段不用；</li>
 *   <li>{@link #CIRCLE}（1）：圆形预警（灾柱反噬、坠身、砸地、镇压、失序之柱、落石落点、正午）：位置 = 圆心（地面），size = 半径；
 *       带 {@link #FLAG_SECTOR} 时是扇形（吐息），dir = 朝向（水平单位向量），size2 = 左右各张开多少弧度；
 *       带 {@link #FLAG_PILLAR} 时圆心正上方 size2 格有能量往上聚（灾柱顶）；</li>
 *   <li>{@link #LINE}（2）：线预警：位置 = 中点，两端 = 中点 ± dir × size，size2 = 半宽（棱线）；带 {@link #FLAG_TETHER} 时是空中的
 *       一道瞄准线（暗袭），两端 = 位置、位置 + dir × size；</li>
 *   <li>{@link #COLUMN}（3）：光柱（烈日标记）：位置 = 柱脚，size = 柱高，size2 = 脚下标记圈的半径；通常跟着一名玩家；</li>
 *   <li>{@link #RING}（4）：移动的环（震波、收束）：位置 = 圆心，size = 起始半径，size2 = 结束半径，dir.x = 每刻扩多少格（负数往里收），
 *       dir.y = 打击带的半宽；delay = 原地蓄力几刻再开始动，duration = 从发包到环走完的总刻数；</li>
 *   <li>{@link #SAFE_ZONE}（5）：收缩安全圈（四灾归心）：位置 = 圆心，size = 安全圈半径，size2 = 外圈起始半径（从这里往里收到安全圈），
 *       color = 安全圈的颜色，color2 = 外面危险区的颜色。</li>
 *   <li>{@link #RESOLVE}（6，特效大改第 2 批加的）：招式落下那一刻的结算画面（炸开、砸地、光柱落下、电弧打到人……），
 *       也包括挂在实体上、持续一段时间的画面（漩涡、心智钉连线）。这一种的 flags 不是位标志，而是<b>画面编号</b>（{@code FX_*}），
 *       其余字段按编号各有含义（写在各常量上）；duration = 持续画面画多久（刻），一次性的画面填 0、客户端按自己的节奏画完。
 *       一次性的画面不受 {@link #CANCEL} 和所属实体消失影响（镜影碎了、落石落地，实体当场就没了，画面照样放完）；
 *       持续的画面（{@link #FX_VORTEX}、{@link #FX_LINK}）跟预警一样能撤、所属实体没了就淡掉。
 *       这种类型是在协议 14 里追加的：只认识前六种的客户端收到它直接忽略（{@code BossWarningClient.create} 返回 null），所以不升协议号。</li>
 * </ul>
 * 「跟随」：圈心每帧跟着这个实体的水平位置走（魔像、守望者站着蓄力时被推开，圈跟着它；烈日标记跟着被标记的人），实体不在客户端时停在包里的位置。
 * 「所属」：客户端收包时看得见所属实体、之后它没了（死了、被删），预警立即淡掉；收包时就看不见的，只按时长和 {@link #CANCEL} 收。
 *
 * @param kind     类型
 * @param owner    所属实体 id
 * @param follow   跟随的实体 id，-1 = 不跟
 * @param duration 从发包到落下（结算）的刻数
 * @param delay    延迟（只有 {@link #RING} 用）
 */
public record S2CBossWarningPacket(int kind, int owner, int follow, double x, double y, double z, float size, float size2,
                                   float dirX, float dirY, float dirZ, int duration, int delay, int color, int color2, int flags) {

    public static final int CANCEL = 0;
    public static final int CIRCLE = 1;
    public static final int LINE = 2;
    public static final int COLUMN = 3;
    public static final int RING = 4;
    public static final int SAFE_ZONE = 5;
    public static final int RESOLVE = 6;

    // ------------------------------------------------------------------ RESOLVE 的画面编号（只追加）
    // 没写到的字段填 0；「中心」都是地面上的点。color 是主色，客户端按它配一套等离子层次。

    /** 灾柱反噬炸开：位置 = 灾柱脚下，size = 半径，size2 = 灾柱高度 */
    public static final int FX_BACKLASH = 1;
    /** 四灾归心落下：位置 = 圆心，size = 安全圈半径，size2 = 场地半径；color = 安全圈色，color2 = 圈外危险色 */
    public static final int FX_CONVERGE = 2;
    /** 天光 / 蚀日棱线落下：位置 = 线的中点，dir = 中点指向一端的水平向量（两端 = 位置 ± dir），size2 = 打击半宽 */
    public static final int FX_PRISM = 3;
    /** 烈日落下：位置 = 被标记的人脚下，size = 光柱高度，size2 = 1 露天挨打、0 有遮挡（光打在遮挡上散掉） */
    public static final int FX_SUNFALL = 4;
    /** 残响 · 正午落下：位置 = 场地中心，size = 半径 */
    public static final int FX_NOON = 5;
    /** 暗袭落下：位置 = 落点，size = 半径，dir = 出手点 − 落点（影刃从那里扑下来） */
    public static final int FX_STRIKE = 6;
    /** 落石落地：位置 = 落点，size = 半径；color2 = 碎石的颜色 */
    public static final int FX_RUBBLE = 7;
    /** 崩解的一次脉冲：位置 = 本体脚下的地面，size = 半径 */
    public static final int FX_PULSE = 8;
    /** 灾柱破土（开场、重立）：位置 = 柱脚，size = 光柱高度，color = 这根柱子的相色 */
    public static final int FX_PILLAR_RISE = 9;
    /** 合相技起手：位置 = 本体中心，size = 本体半径，dirY = 本体中心离地面多高；color / color2 = 这一对的两种相色 */
    public static final int FX_COMBO = 10;
    /** 垂死反扑：位置 = 本体中心，size = 冲击半径；dirY = 本体中心离地面多高 */
    public static final int FX_THROE = 11;
    /** 矩阵之影坠身落地：位置 = 落点，size = 半径 */
    public static final int FX_SLAM = 12;
    /** 矩阵之影收束的冲击：位置 = 它脚下，size = 半径 */
    public static final int FX_SHOCK = 13;
    /** 失序之柱落下：位置 = 柱脚，size = 半径 */
    public static final int FX_DISORDER = 14;
    /** 一道电弧打到人：位置 = 起点，dir = 终点 − 起点；delay = 晚几刻再出（连锁电弧一跳一跳地出） */
    public static final int FX_ARC = 15;
    /** 镜影现身：位置 = 脚下，size = 身高 */
    public static final int FX_MIRROR = 16;
    /** 碎裂（镜影、心智钉）：位置 = 脚下，size = 身高，size2 = 碎片规模（镜影 1，心智钉 0.5） */
    public static final int FX_SHATTER = 17;
    /** 心智钉连线（持续）：所属 = 心智钉、跟随 = 矩阵之影，位置 = 钉子中心，duration = 最长画多久 */
    public static final int FX_LINK = 18;
    /** 聚能魔像砸地：位置 = 落点，size = 半径 */
    public static final int FX_GOLEM_SLAM = 19;
    /** 废城守望者镇压落地：位置 = 落点，size = 半径 */
    public static final int FX_SUPPRESS = 20;
    /** 炮台一枪：位置 = 枪口，dir = 命中点 − 枪口 */
    public static final int FX_TRACER = 21;
    /** 渊潮领主的漩涡（持续）：所属 = 跟随 = 领主，位置 = 领主身体中间，size = 半径，duration = 漩涡多少刻 */
    public static final int FX_VORTEX = 22;

    /** {@link #CIRCLE}：扇形 */
    public static final int FLAG_SECTOR = 1;
    /** {@link #CIRCLE}：圆心上方有一根柱子，能量往柱顶聚 */
    public static final int FLAG_PILLAR = 2;
    /** {@link #LINE}：空中的瞄准线，不是贴地的光带 */
    public static final int FLAG_TETHER = 4;

    /** 尺寸的上限（格）：比任何场地都大，挡住恶意 / 出错的数据把客户端卡死 */
    public static final float MAX_SIZE = 256F;
    /** 时长的上限（刻） */
    public static final int MAX_TICKS = 20 * 60;

    public S2CBossWarningPacket {
        kind = kind & 0xFF;
        flags = flags & 0xFF;
        x = Double.isFinite(x) ? x : 0D;
        y = Double.isFinite(y) ? y : 0D;
        z = Double.isFinite(z) ? z : 0D;
        size = clampSize(size);
        size2 = kind == CIRCLE && (flags & FLAG_SECTOR) != 0 ? Mth.clamp(finite(size2), 0F, Mth.PI) : clampSize(size2);
        dirX = Mth.clamp(finite(dirX), -MAX_SIZE, MAX_SIZE);
        dirY = Mth.clamp(finite(dirY), -MAX_SIZE, MAX_SIZE);
        dirZ = Mth.clamp(finite(dirZ), -MAX_SIZE, MAX_SIZE);
        duration = Mth.clamp(duration, 0, MAX_TICKS);
        delay = Mth.clamp(delay, 0, MAX_TICKS);
        color &= 0xFFFFFF;
        color2 &= 0xFFFFFF;
    }

    /** 撤掉某个实体的全部预警 */
    public static S2CBossWarningPacket cancel(int owner) {
        return new S2CBossWarningPacket(CANCEL, owner, -1, 0D, 0D, 0D, 0F, 0F, 0F, 0F, 0F, 0, 0, 0, 0, 0);
    }

    /**
     * 结算画面（{@link #RESOLVE}）。
     *
     * @param follow   跟随的实体 id（持续画面用），-1 = 不跟
     * @param duration 持续画面画多久（刻），一次性的填 0
     * @param delay    晚几刻再出
     */
    public static S2CBossWarningPacket resolve(int style, int owner, int follow, double x, double y, double z, float size, float size2,
                                               float dirX, float dirY, float dirZ, int duration, int delay, int color, int color2) {
        return new S2CBossWarningPacket(RESOLVE, owner, follow, x, y, z, size, size2, dirX, dirY, dirZ, duration, delay, color, color2, style);
    }

    /** 结算画面的编号（只对 {@link #RESOLVE} 有意义） */
    public int style() {
        return kind == RESOLVE ? flags : 0;
    }

    /** 持续的结算画面（能撤、所属实体没了就淡掉）；一次性的放完为止 */
    public boolean persistent() {
        return kind == RESOLVE && (flags == FX_VORTEX || flags == FX_LINK);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    private static float finite(float v) {
        return Float.isFinite(v) ? v : 0F;
    }

    private static float clampSize(float v) {
        return Mth.clamp(finite(v), 0F, MAX_SIZE);
    }

    public static void encode(S2CBossWarningPacket p, FriendlyByteBuf buf) {
        buf.writeByte(p.kind);
        buf.writeVarInt(p.owner);
        buf.writeVarInt(Math.max(0, p.follow + 1));
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.size);
        buf.writeFloat(p.size2);
        buf.writeFloat(p.dirX);
        buf.writeFloat(p.dirY);
        buf.writeFloat(p.dirZ);
        buf.writeVarInt(p.duration);
        buf.writeVarInt(p.delay);
        buf.writeInt(p.color);
        buf.writeInt(p.color2);
        buf.writeByte(p.flags);
    }

    public static S2CBossWarningPacket decode(FriendlyByteBuf buf) {
        return new S2CBossWarningPacket(buf.readUnsignedByte(), buf.readVarInt(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readUnsignedByte());
    }

    public static void handle(S2CBossWarningPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BossWarningClient.handle(packet));
    }
}
