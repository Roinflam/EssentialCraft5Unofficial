package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.command.CorruptionCommands;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;

/**
 * 第三批玩家相关框架（腐化效果数据 G9、风之好感 G11）在 mod 构造期要登记的东西：
 * SERVER 配置 {@code general.corruption}、{@code general.wind_affinity}，以及 {@code /ec5 player <targets> corruption}。
 * <p>
 * 由 {@code ECRegistries.register()} 调一次 {@link #init()}（和各模块的 init 一样，必须在 ECConfig.register 之前）。
 * 没调的时候配置读默认值（CorruptionEffects / WindAffinity 里对未登记的配置项判了空），指令不存在。
 */
public final class PlayerSystems {

    private static boolean initialized;

    private PlayerSystems() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ECConfig.general(CorruptionEffects.Config::define);
        ECConfig.general(WindAffinity.Config::define);
        ECCommands.addPlayerSubcommand(CorruptionCommands::build);
    }
}
