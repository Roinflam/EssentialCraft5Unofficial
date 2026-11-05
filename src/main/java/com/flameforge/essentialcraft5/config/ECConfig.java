package com.flameforge.essentialcraft5.config;

import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 配置。原作 188 项机器配置（tileentities.*）照搬到 SERVER 配置的 machines.* 下，按机器移植进度逐个加。
 * <p>
 * 用法：每台机器在自己的方块实体里写一个静态嵌套类 {@code Config}，字段是静态的 ConfigValue，
 * 再写一个 {@code static void define(ForgeConfigSpec.Builder b)}（里面 push 自己的小节名、定义字段、pop）；
 * 所在模块的注册类在 init() 里调 {@code ECConfig.machine(XxxBlockEntity.Config::define)}。
 * 规格在 {@link #register} 时一次建好，所以登记必须在主类构造器调 register 之前（模块 init 都在那之前）。
 * <p>
 * 规则：
 * <ul>
 *   <li>玩法数值放 SERVER（每个世界一份，Forge 自动同步给客户端）；纯客户端的显示开关放 CLIENT（{@link #client}，第六批 6a 起，
 *       文件 {@code config/essentialcraft5-client.toml}，每台电脑一份，服务器不管也不同步）。</li>
 *   <li>不要在注册阶段、方块实体构造器里读配置：SERVER 配置进世界才加载。
 *       读值一律用 {@link #get}，未加载时返回默认值（开发环境里直接 get() 会抛异常）。</li>
 *   <li>原作的 guiID、dimensionID、方块/物品 ID 之类在 1.20.1 没有意义，不移植。</li>
 * </ul>
 */
public final class ECConfig {

    /** 原作 ApiCore.GENERATOR_MAX_MRU_GENERIC */
    public static final int GENERATOR_MAX_MRU = 10000;
    /** 原作 ApiCore.DEVICE_MAX_MRU_GENERIC */
    public static final int DEVICE_MAX_MRU = 5000;

    private static final List<Consumer<ForgeConfigSpec.Builder>> GENERAL = new ArrayList<>();
    private static final List<Consumer<ForgeConfigSpec.Builder>> MACHINES = new ArrayList<>();
    private static final List<Consumer<ForgeConfigSpec.Builder>> CLIENT = new ArrayList<>();
    private static ForgeConfigSpec serverSpec;
    /** 没有任何模块登记 CLIENT 小节时为 null（不生成空的 essentialcraft5-client.toml） */
    private static ForgeConfigSpec clientSpec;

    private ECConfig() {
    }

    /** 登记一台机器的配置小节（见类注释） */
    public static void machine(Consumer<ForgeConfigSpec.Builder> section) {
        if (serverSpec != null) {
            throw new IllegalStateException("配置规格已经建好了，机器配置要在模块 init() 里登记");
        }
        MACHINES.add(section);
    }

    /**
     * 登记一段不属于某台机器的通用配置，写进 SERVER 配置的 general 小节（比如首次进服送研究手册）。
     * 用法同 {@link #machine}：字段是静态 ConfigValue，定义时直接 define，不用自己 push 小节；
     * 一组相关的项想放进自己的子小节就在 define 里 push / pop（例：{@code ECProtection.Config} 的 general.protection）。
     */
    public static void general(Consumer<ForgeConfigSpec.Builder> section) {
        if (serverSpec != null) {
            throw new IllegalStateException("配置规格已经建好了，通用配置要在模块 init() 里登记");
        }
        GENERAL.add(section);
    }

    /**
     * 登记一段纯客户端的显示设置（第六批 6a 框架 F6a，batch6a.md §6.1 第 8 条），写进 CLIENT 配置 {@code config/essentialcraft5-client.toml}：
     * 天空装饰、镜头晃动这类「每个人自己的电脑自己定」的东西，服务器不管、不同步。用法同 {@link #general}，一个模块一个子小节（自己 push / pop，
     * 例 M3 的 {@code [hoanna]}）。
     * <p>
     * 读值：专用服上 CLIENT 配置永远不加载，客户端在进标题画面前加载——渲染代码每帧读的，在 {@code ModConfigEvent.Loading / Reloading}
     * 里判 {@link #isClientConfig} 后缓存到静态字段（每帧读 ConfigValue 会装箱、还要查一次 map），其余地方用 {@link #getClient}（没加载时返回默认值）。
     */
    public static void client(Consumer<ForgeConfigSpec.Builder> section) {
        if (serverSpec != null) {
            throw new IllegalStateException("配置规格已经建好了，客户端配置要在模块 init() 里登记");
        }
        CLIENT.add(section);
    }

    public static void register(ModLoadingContext context) {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("General settings.", "通用设置").push("general");
        GENERAL.forEach(section -> section.accept(builder));
        // 框架自己的小节不属于哪个模块、没有模块 init 可以登记，直接写在这里：权限检查 general.protection（ECProtection）、
        // 伤害分类 general.damage_kinds（ECDamageKinds）
        ECProtection.Config.define(builder);
        ECDamageKinds.Config.define(builder);
        builder.pop();
        builder.comment("Machine values, ported from EssentialCraft 4 Unofficial's tileentities.* config.",
                "机器数值，对应原作 tileentities.* 配置").push("machines");
        MACHINES.forEach(section -> section.accept(builder));
        builder.pop();
        serverSpec = builder.build();
        context.registerConfig(ModConfig.Type.SERVER, serverSpec);
        if (!CLIENT.isEmpty()) {
            ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
            CLIENT.forEach(section -> section.accept(client));
            clientSpec = client.build();
            context.registerConfig(ModConfig.Type.CLIENT, clientSpec);
        }
    }

    /** 整份 CLIENT 配置规格；没有模块登记客户端设置、或模组构造完之前为 null */
    public static ForgeConfigSpec clientSpec() {
        return clientSpec;
    }

    /** 这份配置是不是本模组的 CLIENT 配置（{@code ModConfigEvent} 里判断要不要刷新缓存） */
    public static boolean isClientConfig(ModConfig config) {
        return clientSpec != null && config.getSpec() == clientSpec;
    }

    /** 读一个 CLIENT 配置项：没加载（专用服、太早）时返回默认值 */
    public static <T> T getClient(ForgeConfigSpec.ConfigValue<T> value) {
        return clientSpec != null && clientSpec.isLoaded() ? value.get() : value.getDefault();
    }

    /**
     * 整份 SERVER 配置规格（研究手册的配置快照 {@code GuideConfigSnapshot} 遍历它）；模组构造完之前为 null
     */
    public static ForgeConfigSpec spec() {
        return serverSpec;
    }

    private static boolean loaded() {
        return serverSpec != null && serverSpec.isLoaded();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return loaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return loaded() ? value.get() : value.getDefault();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return loaded() ? value.get() : value.getDefault();
    }

    /** 其它类型的 SERVER 配置项（列表之类）：没加载时返回默认值 */
    public static <T> T getValue(ForgeConfigSpec.ConfigValue<T> value) {
        return loaded() ? value.get() : value.getDefault();
    }
}
