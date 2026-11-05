package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.client.boss.BossFxClientConfig;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.util.ECFlight;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第十三批 13c-Fb「首领与渲染框架」的注册类（batch13.md §9.3）。首领通用件 {@code entity/boss/*}（{@code ECBoss}、{@code BossRoster}、
 * {@code BossDamageBudget}、{@code BossRules}、{@code BossLoot}、{@code BossSkillSlots}、基类 {@code ECBossMonster}）本身不注册任何东西，
 * 这里只登记两条音效、服务端配置 {@code general.mobs.boss}（{@link Config}）、CLIENT 配置 {@code [boss_fx]}（{@link BossFxClientConfig}），
 * 并把首领名单挂到 {@link ECFlight} 的飞行压制器上（参战者不能用本模组给的飞行）。
 * <p>
 * 整屏画面包 {@code network/S2CBossFxPacket} 由预备登记在框架包里，客户端处理在 {@code client/boss/BossFxClient}；渲染基类在 {@code client/render/mob}，
 * 关键帧辅助在 {@code client/model/mob/anim}（客户端）与 {@code entity/anim}（两端都要的动画状态与实体事件号对照）。
 * 音效定义在 tools/sounds/ecbossframework.json（指原版声音），字幕和动作栏文字在 tools/lang/keys/ecbossframework.json。速查 porting_guide §22「13c-Fb」。
 */
public final class ECBossFramework {

    /** 参战者被拽回战场时（指原版末影人传送声，音高压低） */
    public static final RegistryObject<SoundEvent> SOUND_PULL = ECSounds.register("sound.mob.boss.pull");
    /** 首领没被打倒就散去时（指原版信标熄灭声） */
    public static final RegistryObject<SoundEvent> SOUND_DISSIPATE = ECSounds.register("sound.mob.boss.dissipate");

    private ECBossFramework() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置和飞行压制器在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECConfig.client(BossFxClientConfig::define);
        ECFlight.registerSuppressor(BossRoster::isEngaged);
    }

    /** 服务端配置 {@code general.mobs.boss}：所有首领共用的框架数值（每只首领自己的封顶、距离在各自的小节里） */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param nonMeleeMultiplier Boss 档挨的不是近战的伤害（箭、射线、魔法、爆炸）乘多少
         * @param lootShare          打出不少于最大生命这么多比例的有效伤害才分得到掉落、算首杀
         * @param pullDamage         把跑远的参战者拽回战场时扣几点
         * @param abandonTicks       名单空了多少刻后首领散去（只对开了「散去」的首领）
         */
        public record Values(double nonMeleeMultiplier, double lootShare, double pullDamage, int abandonTicks) {
        }

        /** 默认值（batch13.md §5.3「共同」：非近战 ×0.5、每名 ≥ 10% 的参战者一份、名单空 600 刻散去；拉回扣血同全息投影的 5 点） */
        public static final Values DEFAULTS = new Values(0.5D, 0.10D, 5D, 600);

        private static ForgeConfigSpec.DoubleValue nonMeleeMultiplier;
        private static ForgeConfigSpec.DoubleValue lootShare;
        private static ForgeConfigSpec.DoubleValue pullDamage;
        private static ForgeConfigSpec.IntValue abandonTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Rules shared by every boss and mini-boss of this mod.", "本模组所有首领共用的规矩")
                    .push(List.of(ECMobFramework.CONFIG_SECTION, "boss"));
            nonMeleeMultiplier = builder.comment("Bosses (not mini-bosses) take this much of any damage that isn't melee: arrows, rays, magic, explosions.",
                            "Boss（不含小 Boss）挨的不是近战的伤害（箭、射线、魔法、爆炸）乘多少")
                    .defineInRange("non_melee_multiplier", DEFAULTS.nonMeleeMultiplier(), 0D, 1D);
            lootShare = builder.comment("A fighter needs to deal at least this share of the boss's max health to get loot and count a first kill.",
                            "Every such fighter gets their own roll, straight into the inventory.",
                            "参战者打出不少于首领最大生命这个比例的有效伤害，才分得到掉落、算首杀；每人各掷一份，直接进背包")
                    .defineInRange("loot_share", DEFAULTS.lootShare(), 0D, 1D);
            pullDamage = builder.comment("Damage dealt to a fighter dragged back into the arena (0 = none).",
                            "参战者跑远被拽回战场时扣几点（0 = 不扣）")
                    .defineInRange("pull_damage", DEFAULTS.pullDamage(), 0D, 100D);
            abandonTicks = builder.comment("Ticks with nobody on the roster before a summoned boss fades away.",
                            "名单空了多少刻后，召唤出来的首领散去")
                    .defineInRange("abandon_ticks", DEFAULTS.abandonTicks(), 100, 72_000);
            builder.pop(2);
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (nonMeleeMultiplier == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(nonMeleeMultiplier), ECConfig.get(lootShare), ECConfig.get(pullDamage), ECConfig.get(abandonTicks));
        }

        /** 只给 GameTest：在同一段同步代码里设、测、置回 null（别用 ConfigValue.set，文件监视器会冲掉） */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
