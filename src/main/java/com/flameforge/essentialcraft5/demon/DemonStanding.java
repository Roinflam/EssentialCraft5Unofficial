package com.flameforge.essentialcraft5.demon;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.command.DemonStandingCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 契约之誉（第十三批 13a，batch13.md §3.4、D19 A）：和恶魔做成一笔交易（恶魔收下要价、给出阿克罗尼特）声望 +1，打发走不扣也不加；
 * 存在玩家数据 {@code demon_standing}（死亡、换维度保留）。三档，各管各的门槛（{@link #atLeast}，门槛按配置，互不依赖）：
 * <ul>
 *   <li>熟客（8）：召出的恶魔同时开两样要价，交哪样都算；第二样是贵价（{@link DemonTrades#rollFor}）。</li>
 *   <li>契主（20）：每笔多给 {@code patron_xp}（30）点经验，阿克罗尼特照旧（凑齐仍要约 58 笔）。按成交前的声望算，到 20 的那一笔还不给。</li>
 *   <li>血契（40）：每次召唤一次，潜行、手持被俘灵魂右键恶魔，交一个灵魂重抽两样要价（{@code DemonEntity}）。</li>
 *   <li>挚契（70，第十三批 13e）：召唤者本人潜行、主手或副手拿着影晶右键恶魔「立誓」：交 {@code oath_cost}（16）个影晶（背包里分几格都行），
 *       换 1 个恶魔之誓，这次召唤结束（恶魔照打发走离开；不算一笔交易、不加声望）。见 {@code DemonEntity#swearOath}。</li>
 * </ul>
 * {@code enabled} 关掉时照样记数，各档都不起作用、成交也不发提示。
 * <p>
 * 只有召唤者（和创造模式玩家）能交易（第七批 D2），所以声望只能靠自己召、自己交；每笔要真的交出物品。
 * 老存档：手册迁移 v3 按进度补发下限（{@link #LEGACY_FLOOR_PREFIX}{@code 8 / 40}，{@link #init} 登记成迁移动作）。
 * <p>
 * 配置在 {@code general.demon.standing}（{@link Config}，由 {@code ECDemon.Config} 在 demon 小节里调用）。
 */
public final class DemonStanding {

    /** 老存档补下限的迁移动作名前缀，后面接数字：{@code essentialcraft5:demon_standing_floor_8}（有阿克罗尼特的）、{@code _40}（做出过宅男套装的） */
    public static final String LEGACY_FLOOR_PREFIX = EssentialCraft5.MODID + ":demon_standing_floor_";
    /** 迁移表 v3 用到的两个下限（batch13.md §2.6） */
    public static final int[] LEGACY_FLOORS = {8, 40};

    @Nullable
    private static Boolean testEnabled;

    private DemonStanding() {
    }

    /** 契约之誉的档（显示用的顺序） */
    public enum Tier {
        NONE, REGULAR, PATRON, BLOOD,
        /** 挚契（13e）：能立誓换恶魔之誓 */
        OATH;

        /** 这一档要多少声望（按配置；NONE 是 0） */
        public int threshold() {
            return switch (this) {
                case NONE -> 0;
                case REGULAR -> Config.regular();
                case PATRON -> Config.patron();
                case BLOOD -> Config.blood();
                case OATH -> Config.oath();
            };
        }

        /** 档名的语言键（熟客 / 契主 / 血契 / 挚契；NONE 没有档名） */
        public String key() {
            return "gui.essentialcraft5.demon_standing.tier." + name().toLowerCase(Locale.ROOT);
        }

        /** 这一档给什么的语言键（悬停提示、到档提示用） */
        public String perkKey() {
            return "gui.essentialcraft5.demon_standing.perk." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 由 {@code ECDemon.init()} 调用：登记老存档补下限的迁移动作、{@code /ec5 player <p> demon_standing} */
    public static void init() {
        for (int floor : LEGACY_FLOORS) {
            GuideMigration.registerAction(LEGACY_FLOOR_PREFIX + floor, player -> raiseTo(player, floor));
        }
        ECCommands.addPlayerSubcommand(DemonStandingCommands::build);
    }

    /** 玩家的契约之誉；没有玩家数据时是 0 */
    public static int get(@Nullable Player player) {
        if (player == null) {
            return 0;
        }
        IPlayerData data = player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
        return data == null ? 0 : data.getDemonStanding();
    }

    /** 契约之誉开着没有（测试覆盖值优先） */
    public static boolean enabled() {
        Boolean override = testEnabled;
        return override != null ? override : Config.enabled();
    }

    /** standing 够不够 tier 的门槛（关着时一律不够；NONE 永远够） */
    public static boolean atLeast(int standing, Tier tier) {
        if (tier == Tier.NONE) {
            return true;
        }
        return enabled() && standing >= tier.threshold();
    }

    /** 这名玩家够不够 tier（null 按 0 算） */
    public static boolean atLeast(@Nullable Player player, Tier tier) {
        return atLeast(get(player), tier);
    }

    /** standing 到了的最高一档（按档的先后挑最后一个够的；关着时是 NONE） */
    public static Tier tier(int standing) {
        Tier best = Tier.NONE;
        for (Tier tier : Tier.values()) {
            if (tier != Tier.NONE && atLeast(standing, tier)) {
                best = tier;
            }
        }
        return best;
    }

    /**
     * 成交以后（服务端）：先按成交前的声望给契主的经验，再 +1；开着时动作栏写现在多少、到了新的一档另发一句聊天说明。
     *
     * @return 加完以后的声望
     */
    public static int recordTrade(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0;
        }
        int before = Math.max(0, data.getDemonStanding());
        int xp = atLeast(before, Tier.PATRON) ? Math.max(0, Config.patronXp()) : 0;
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        int after = before == Integer.MAX_VALUE ? before : before + 1;
        data.setDemonStanding(after);
        if (!enabled()) {
            return after;
        }
        MutableComponent line = fullLabel(after);
        if (xp > 0) {
            line.append(Component.translatable("message.essentialcraft5.demon_standing.patron_xp", xp));
        }
        player.displayClientMessage(line.withStyle(ChatFormatting.GOLD), true);
        for (Tier tier : Tier.values()) {
            if (tier != Tier.NONE && !atLeast(before, tier) && atLeast(after, tier)) {
                player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_standing.reached",
                                Component.translatable(tier.key()), tier.threshold(), perk(tier))
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        return after;
    }

    /**
     * 「契约之誉 23（契主）」：没到任何一档时只写数。交易界面标题行用的短说法（英文只写 Renown，左边是恶魔名字，写全称会撞上）；
     * 别处用 {@link #fullLabel}。
     */
    public static MutableComponent label(int standing) {
        Tier tier = tier(standing);
        return tier == Tier.NONE
                ? Component.translatable("gui.essentialcraft5.demon_standing.value", standing)
                : Component.translatable("gui.essentialcraft5.demon_standing.value_tier", standing, Component.translatable(tier.key()));
    }

    /** 同 {@link #label}，英文写全称 Demonic Renown（和手册、到档提示一致）：成交后的动作栏、悬停提示、指令输出用 */
    public static MutableComponent fullLabel(int standing) {
        Tier tier = tier(standing);
        return tier == Tier.NONE
                ? Component.translatable("gui.essentialcraft5.demon_standing.value_full", standing)
                : Component.translatable("gui.essentialcraft5.demon_standing.value_full_tier", standing, Component.translatable(tier.key()));
    }

    /** 一档给什么（契主那句带经验数、挚契那句带要交几个影晶） */
    public static Component perk(Tier tier) {
        return switch (tier) {
            case PATRON -> Component.translatable(tier.perkKey(), Config.patronXp());
            case OATH -> Component.translatable(tier.perkKey(), Config.oathCost());
            default -> Component.translatable(tier.perkKey());
        };
    }

    /** 补到至少 floor（迁移动作、指令兜底用）；已经更高的不动 */
    public static void raiseTo(Player player, int floor) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null && data.getDemonStanding() < floor) {
            data.setDemonStanding(floor);
        }
    }

    /** GameTest 用：契约之誉开关的覆盖值（null = 用配置）。同一段同步代码里设、测、置回 */
    public static void setTestEnabledOverride(@Nullable Boolean enabled) {
        testEnabled = enabled;
    }

    /** 服务端配置 {@code general.demon.standing}：开关、四档门槛、贵价倍数、契主经验、立誓要几个影晶 */
    public static final class Config {

        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue regular;
        public static ForgeConfigSpec.IntValue patron;
        public static ForgeConfigSpec.IntValue blood;
        public static ForgeConfigSpec.IntValue oath;
        public static ForgeConfigSpec.DoubleValue premiumMultiplier;
        public static ForgeConfigSpec.IntValue patronXp;
        public static ForgeConfigSpec.IntValue oathCost;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Demonic Renown: +1 for every finished trade with a demon.", "契约之誉：和恶魔每做成一笔交易 +1").push("standing");
            enabled = builder.comment("Demonic Renown on. When off, renown still counts up but no tier does anything.",
                            "Turning it off also stops oaths, so no Demon's Oath can be had and the Calamity Beacon (chapter 23) cannot be crafted:"
                                    + " turn off general.hoanna.portal_enabled as well, or give the beacon another recipe with a data pack.",
                            "契约之誉开关。关掉时照样记数，但各档都不起作用",
                            "关掉以后也立不了誓，拿不到恶魔之誓，第二十三章的天灾信标做不出来：要么一起关掉 general.hoanna.portal_enabled，要么用数据包给天灾信标换配方")
                    .define("enabled", true);
            regular = builder.comment("Renown for Regular: a summoned demon offers two asking prices and either one counts.",
                            "「熟客」要多少：召出的恶魔同时开两样要价，交哪样都算")
                    .defineInRange("regular", 8, 0, 100_000);
            patron = builder.comment("Renown for Patron: extra experience on every trade.", "「契主」要多少：每笔多给经验")
                    .defineInRange("patron", 20, 0, 100_000);
            blood = builder.comment("Renown for Blood Bargain: once per summon, a Captured Soul rerolls both asking prices.",
                            "「血契」要多少：每次召唤一次，交一个被俘灵魂重抽两样要价")
                    .defineInRange("blood", 40, 0, 100_000);
            oath = builder.comment("Renown for the Oath (sworn with Umbral Crystals for a Demon's Oath).", "「挚契」要多少（交影晶立誓换恶魔之誓）")
                    .defineInRange("oath", 70, 0, 100_000);
            premiumMultiplier = builder.comment("Count multiplier of the second, pricier asking price.", "第二样（贵价）要价的数量倍数")
                    .defineInRange("premium_multiplier", 1.5D, 1D, 10D);
            patronXp = builder.comment("Extra experience points per trade from Patron on.", "「契主」起每笔多给多少经验")
                    .defineInRange("patron_xp", 30, 0, 10_000);
            oathCost = builder.comment("Umbral Crystals handed over to swear the Oath.", "立誓要交几个影晶")
                    .defineInRange("oath_cost", 16, 1, 64);
            builder.pop();
        }

        public static boolean enabled() {
            return ECConfig.get(enabled);
        }

        public static int regular() {
            return ECConfig.get(regular);
        }

        public static int patron() {
            return ECConfig.get(patron);
        }

        public static int blood() {
            return ECConfig.get(blood);
        }

        public static int oath() {
            return ECConfig.get(oath);
        }

        public static double premiumMultiplier() {
            return ECConfig.get(premiumMultiplier);
        }

        public static int patronXp() {
            return ECConfig.get(patronXp);
        }

        public static int oathCost() {
            return ECConfig.get(oathCost);
        }
    }
}
