package com.flameforge.essentialcraft5.hoanna.event;

import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;

/**
 * 海纳尔的四种天灾（原作 {@code WorldEventSunArray / Fumes / Darkness / Earthquake}，第六批 6a 框架 F6a；服务端逻辑在 M4 的 {@code HoannaEvents}，
 * 画面在 M3）。id 是网络包和存档（M4 的 SavedData）里用的编号，<b>上线后不许改</b>；未知 id 当 {@link #NONE}。
 * <p>
 * 掷骰（M4，batch6a.md §5.4）：海纳尔里有人、没有事件时每 20 tick 一次——先 {@code random.nextInt(4)} 挑一种（{@link #rollable()} 的顺序），
 * 再 {@code nextFloat() ≤ chance × chance_multiplier} 才开始；日阵 {@link #dawnOnly()}，常规掷骰永远不成立，只在「黎明」（日序号变了）单独掷一次，
 * 概率同样是 1/4 × {@link #chance()}（原作 {@code WorldEventSunArray.possibilityOfHappening} 要求 worldTime % 24000 == 0，等于只在日出那一刻掷）。
 * 合计约每 6 个游戏日一次（日阵 7.5%、毒雾约 5.1%、黑暗约 1.5%、地震约 3.0% / 日）。
 * <p>
 * 画面的淡入淡出时长在 {@code hoanna/HoannaFade}（地震 60 / 100 tick，其余 200 / 200）。
 * 语言键（文字在 {@code tools/lang/keys/echoanna.json}）：开始 / 结束的聊天提示 {@link #startKey()} / {@link #endKey()}（原作颜色：开始红、结束绿，
 * 原作写死的英文见 tools/lang/hardcoded_strings.md），名字 {@link #nameKey()}（事件提示条、指令反馈用）。
 * <p>
 * 第十三批 13d 加了第五种 {@link #LEY_STORM 地脉风暴}（id 5，batch13.md §6.2）：<b>不进 {@link #rollable()}</b>，常规掷骰仍是四种、频率不变；
 * 它只在大潮夜的黄昏单独掷一次（掷法、惩罚、画面在 13d-H2）。按事件分支的地方对它都有显式分支或 default，没接上之前它没有惩罚、没有画面。
 * 它的文字键（名字、开始 / 结束提示）在 {@code tools/lang/keys/echoannastorm.json}。
 */
public enum HoannaEvent {

    NONE(0, "none", 0F, 0),
    /** 日阵：白天露天灼烧（每 20 tick 1 点火焰伤害 + 着火 10 秒，不落火）；持续整个白天 */
    SUN_ARRAY(1, "sun_array", 0.3F, 12_000),
    /** 毒雾：飘动的黄绿雾团（实体 poison_fume），靠近中毒、辐射猛涨 */
    FUMES(2, "fumes", 0.000175F, 12_000),
    /** 黑暗：天光 ×0.22、方块光 ×0.88（火把照常亮），纯客户端画面；持续 1~3 个游戏日 */
    DARKNESS(3, "darkness", 0.00005F, 24_000),
    /** 地震：服务端每 20 tick 小踉跄、偶尔强震（{@code S2CHoannaJoltPacket}），镜头只晃画面 */
    EARTHQUAKE(4, "earthquake", 0.0001F, 5_000),
    /**
     * 地脉风暴（第十三批 13d）：大潮夜黄昏单独掷 0.3，持续 6 000 刻、截到黎明；落晶、露天辐射、浮游棱晶追加（batch13.md §6.2）。
     * {@link #chance()} 填 0：它不走常规掷骰，几率是配置 {@code general.hoanna_events.ley_storm_chance}
     */
    LEY_STORM(5, "ley_storm", 0F, 6_000);

    /** 常规掷骰先 nextInt(4) 挑的四种（等概率，顺序不影响结果），固定按 id 排；地脉风暴不在里面 */
    private static final HoannaEvent[] ROLLABLE = {SUN_ARRAY, FUMES, DARKNESS, EARTHQUAKE};
    private static final HoannaEvent[] BY_ID = values();

    private final int id;
    private final String name;
    private final float chance;
    private final int baseDuration;

    HoannaEvent(int id, String name, float chance, int baseDuration) {
        this.id = id;
        this.name = name;
        this.chance = chance;
        this.baseDuration = baseDuration;
    }

    /** 包和存档里的编号（0 = 没有事件） */
    public int id() {
        return id;
    }

    /** 小写名（配置键 {@code <key>_enabled}、指令参数、语言键都用它） */
    public String key() {
        return name;
    }

    /** 被挑中以后真的发生的概率（原作 possibilityOfHappening；再乘服务端配置 chance_multiplier） */
    public float chance() {
        return chance;
    }

    /** 日阵只在黎明掷（见类注释） */
    public boolean dawnOnly() {
        return this == SUN_ARRAY;
    }

    /** 不带随机的时长（黑暗是一个游戏日的基数，实际 × 1~3，见 {@link #duration}） */
    public int baseDuration() {
        return baseDuration;
    }

    /** 这一次持续多少 tick（黑暗 24 000 × (1~3)，原作 {@code WorldEventDarkness} L41；其余固定） */
    public int duration(RandomSource random) {
        return this == DARKNESS ? baseDuration * (random.nextInt(3) + 1) : baseDuration;
    }

    public boolean isNone() {
        return this == NONE;
    }

    /** 开始时的聊天提示键（红色） */
    public String startKey() {
        return "message.essentialcraft5.hoanna_event." + name + ".start";
    }

    /** 结束时的聊天提示键（绿色） */
    public String endKey() {
        return "message.essentialcraft5.hoanna_event." + name + ".end";
    }

    /** 事件名键（提示条、指令反馈） */
    public String nameKey() {
        return "hoanna_event.essentialcraft5." + name;
    }

    /** 未知编号（新客户端连旧服、存档被改）一律当没有事件 */
    public static HoannaEvent fromId(int id) {
        return id >= 0 && id < BY_ID.length && BY_ID[id].id == id ? BY_ID[id] : NONE;
    }

    /** 按小写名找（指令用）；找不到返回 null */
    @Nullable
    public static HoannaEvent byName(String name) {
        for (HoannaEvent event : BY_ID) {
            if (event.name.equals(name)) {
                return event;
            }
        }
        return null;
    }

    /** 常规掷骰先 nextInt(4) 挑的四种（不含 NONE），每次返回新数组 */
    public static HoannaEvent[] rollable() {
        return ROLLABLE.clone();
    }
}
