package com.flameforge.essentialcraft5.network;

/**
 * 装备成长的特效事件种类（{@link S2CGearFxPacket} 的 type，按序号编码）。<b>只追加、不改已有的顺序</b>（改了要升 {@link ECNetwork#PROTOCOL}）；
 * G2 盔甲用的几种先占好位置。每种的 entity / pos / param 含义写在常量上；客户端没登记画法的种类直接忽略。
 */
public enum GearFx {
    /** 升阶 / 升格：pos = 魔力工作台中心，param = 新阶，entity = -1 */
    ASCEND,
    /** 战痕升级：entity = 玩家，pos = 胸口，param = 新等级 */
    SCAR_UP,
    /** 斩杀：entity = 目标，pos = 目标身上，param = 这一下的伤害 */
    EXECUTE,
    /** 吸血：entity = 玩家，pos = 目标身上，param = 回了多少 */
    LIFESTEAL,
    /** 护甲穿透真少减了伤害：entity = 目标，pos = 目标身上，param = 倍数 */
    ARMOR_PEN,
    /** 挂罪印：entity = 目标，pos = 目标头顶，param = 持续刻数 */
    VERDICT_SEAL,
    /** 罪印引爆：entity = 目标，pos = 目标身上，param = 补的伤害（首领 0） */
    VERDICT_BURST,
    /** 元素爆发：entity = 主目标，pos = 爆发中心，param = 元素序号（0 火 1 水 2 地 3 风） */
    ELEMENT_BURST,
    /** 过载充满：entity = 射手，pos = 射手，param = 充能发数 */
    OVERLOAD_READY,
    /** 过载射线爆发：entity = 射手，pos = 爆发中心，param = 半径 */
    OVERLOAD_FIRE,
    /** （G2）魔力防护回涌 */
    SURGE,
    /** （G2）风之风暴步 */
    STORM_STEP,
    /** （G2）虚空残像 */
    AFTERIMAGE,
    /** （G2）审判庭宣判 */
    SENTENCE,
    /** （G2）宅男超载脉冲 */
    OVERCHARGE_PULSE,
    /** 宅男套装喷射推进：entity = 玩家，pos = 脚下，param = 冲刺速度（格 / 刻） */
    JET_DASH,
    /** 虚空胸甲闪避：entity = 玩家，pos = 脚下，param 不用 */
    VOID_DODGE,
    /** 审判庭盔甲定罪（新记上一个目标）：entity = 目标，pos = 目标身上，param = 定罪刻数 */
    CONVICT,
    /** 风之套装空中跳：entity = 玩家，pos = 脚下，param 不用；跳的人自己在本地先画了，客户端收到自己的就跳过 */
    AIR_JUMP,
    /** 饰品封禁场运转中（每次评估发一次）：entity = 穿戴者，pos = 穿戴者脚下，param = 半径；param 0 = 停了 */
    SEAL_FIELD;

    private static final GearFx[] VALUES = values();

    /** 序号 → 种类；认不出的（更新的服务端发来的）返回 null */
    public static GearFx byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
