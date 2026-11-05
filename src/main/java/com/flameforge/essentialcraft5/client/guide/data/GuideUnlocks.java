package com.flameforge.essentialcraft5.client.guide.data;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * 按客户端进度做的两件事：条目 / 分类的<b>解锁</b>（JSON 里写了 advancement 的，完成前锁着）和步骤的<b>完成</b>
 * （写了 complete 的条目，完成后目录里打勾）。自带内容只有「古卷」一章用前者（残卷每卷一篇，读过那一卷才打开，第十二批 batch12.md §4.1.9），
 * 别的章只用后者、不锁任何东西；前者也留给数据包作者。
 * <p>
 * 原作研究书按物品 NBT 的等级（tier 0~4）解锁分类，要合成升级。移植后改成用进度：服务端本来就会把进度同步给客户端，
 * 不用另做网络包，数据包作者也能自己加进度来控制。
 *
 * <h2>客户端什么时候有进度数据（按 1.20.1 源码核对过）</h2>
 * <ul>
 *   <li>不用打开进度界面。服务端 PlayerAdvancements 在玩家登录（及 /reload）后的第一个玩家 tick（ServerPlayer.tick 末尾的
 *       flushDirty，isFirstPacket = true）发一个带重置标记的 ClientboundUpdateAdvancementsPacket，客户端 ClientAdvancements.update
 *       把「可见」的进度和完成情况整份写进来；之后每完成一个进度，同一个 tick 内再增量推送。进度界面只负责显示和上报选中的页签。</li>
 *   <li>「可见」由 AdvancementVisibilityEvaluator 决定：<b>已完成的进度一定可见</b>（它自己完成就算，不管有没有 display、是不是 hidden），
 *       所以 {@link #isDone} 查的进度只要完成了，客户端就一定知道。没 display 的进度（data/essentialcraft5/advancements/guide/ 下那些）
 *       在完成前不会发给客户端，查不到按未完成算，这正是想要的结果；它们也不会出现在进度界面里（没有 display 的根进度不建页签）。</li>
 *   <li>刚进世界的头一两个 tick 里还没收到包，这时一律按未完成算，界面每帧重查，收到后自然变过来。</li>
 * </ul>
 * 完成情况存在 ClientAdvancements 的私有字段 progress 里（原版只通过监听器往外推，没有 getter；监听器被进度界面占用，不能抢）。
 * 这里按<b>字段类型</b>找：优先取泛型是 Map&lt;Advancement, AdvancementProgress&gt; 的非静态字段，读不到泛型时取唯一的那个 Map 字段
 * （1.20.1 里另外几个字段是 Minecraft、遥测、AdvancementList、监听器、选中页签），
 * 不依赖字段名，开发环境和正式环境（SRG 名）都能用；MC 的类对模组的反射是开放的（Forge 的 ObfuscationReflectionHelper 也是这么 setAccessible 的）。
 * 找不到时：解锁按全部已解锁处理（这只是说明书，锁错了比放开更糟），完成按全部未完成处理（少打几个勾无伤大雅），只报一次错。
 */
public final class GuideUnlocks {

    @Nullable
    private static Field progressField;
    private static boolean lookupFailed;

    private GuideUnlocks() {
    }

    /** 条目 / 分类是否解锁：没写解锁进度的一直解锁；读不到客户端进度表时也当作解锁 */
    public static boolean isUnlocked(@Nullable ResourceLocation advancement) {
        if (advancement == null) {
            return true;
        }
        Boolean done = state(advancement);
        return done == null || done;
    }

    /** 步骤的完成进度是否已完成：没写的、读不到客户端进度表的，都按未完成算 */
    public static boolean isDone(@Nullable ResourceLocation advancement) {
        if (advancement == null) {
            return false;
        }
        return Boolean.TRUE.equals(state(advancement));
    }

    /** 进度完成了没有；读不到进度表时返回 null（由调用方决定按哪边算） */
    @Nullable
    private static Boolean state(ResourceLocation advancement) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        ClientAdvancements advancements = connection.getAdvancements();
        Advancement target = advancements.getAdvancements().get(advancement);
        if (target == null) {
            // 客户端只收到可见的进度；已完成的一定可见，所以查不到的肯定没完成
            return false;
        }
        Map<Advancement, AdvancementProgress> progress = progress(advancements);
        if (progress == null) {
            return null;
        }
        AdvancementProgress state = progress.get(target);
        return state != null && state.isDone();
    }

    /** 解锁条件的说明：「完成进度「X」后解锁」，进度客户端还不知道时只说「完成某个进度」 */
    public static Component hint(ResourceLocation advancement) {
        return hint(advancement, null);
    }

    /**
     * 同 {@link #hint(ResourceLocation)}，内容里写了 {@code locked_hint} 时用它（调用方已经展开好占位符）。
     * 隐藏进度没有 display，默认那句只能说「完成某个进度」，所以锁在隐藏进度上的条目要写 locked_hint 告诉玩家去哪做（第十二批 batch12.md §4.1.9）
     */
    public static Component hint(ResourceLocation advancement, @Nullable Component custom) {
        if (custom != null) {
            return custom;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            Advancement target = connection.getAdvancements().getAdvancements().get(advancement);
            if (target != null && target.getDisplay() != null) {
                return Component.translatable("guide.essentialcraft5.locked.advancement", target.getDisplay().getTitle());
            }
        }
        return Component.translatable("guide.essentialcraft5.locked.unknown");
    }

    private static boolean isProgressMap(Type type) {
        return type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length == 2
                && parameterized.getActualTypeArguments()[0] == Advancement.class
                && parameterized.getActualTypeArguments()[1] == AdvancementProgress.class;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<Advancement, AdvancementProgress> progress(ClientAdvancements advancements) {
        if (progressField == null && !lookupFailed) {
            Field found = null;
            for (Field field : ClientAdvancements.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                // 泛型是 Map<Advancement, AdvancementProgress> 的最可靠（别的模组用 mixin 往这个类里加 Map 字段也不会认错）；
                // 泛型信息读不到时退而取第一个 Map 字段
                if (isProgressMap(field.getGenericType())) {
                    found = field;
                    break;
                }
                if (found == null) {
                    found = field;
                }
            }
            if (found != null) {
                try {
                    found.setAccessible(true);
                    progressField = found;
                } catch (RuntimeException e) {
                    EssentialCraft5.LOGGER.error("[指南] 读不到客户端进度，按进度解锁的条目将全部显示、步骤不打勾", e);
                    lookupFailed = true;
                }
            }
            if (progressField == null && !lookupFailed) {
                EssentialCraft5.LOGGER.error("[指南] ClientAdvancements 里找不到进度表，按进度解锁的条目将全部显示、步骤不打勾");
                lookupFailed = true;
            }
        }
        if (progressField == null) {
            return null;
        }
        try {
            return (Map<Advancement, AdvancementProgress>) progressField.get(advancements);
        } catch (IllegalAccessException | RuntimeException e) {
            lookupFailed = true;
            progressField = null;
            EssentialCraft5.LOGGER.error("[指南] 读客户端进度失败，按进度解锁的条目将全部显示、步骤不打勾", e);
            return null;
        }
    }
}
