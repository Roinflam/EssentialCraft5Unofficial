package com.flameforge.essentialcraft5.lore;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 战利品函数 {@code essentialcraft5:scroll_volume}（batch12.md §4.1.3）：给源质残卷写卷号（NBT {@code Volume}）。
 * <pre>
 * { "function": "essentialcraft5:scroll_volume", "min": 0, "max": 4, "prefer_unread": 0.75 }
 * </pre>
 * <ul>
 *   <li>{@code min} / {@code max}：卷号范围（0 = 序，1～6 = 卷一～卷六，含两端）。</li>
 *   <li>{@code prefer_unread}：0～1，默认 0.75。有玩家、范围里还有他没有的卷（没读过、也没掉给过他，{@link ScrollVolumes#has}）时，
 *       按这个几率在这些卷里平均抽，否则在整个范围里平均抽。偏向没有的，免得集最后一两卷时一直开出重复的。</li>
 * </ul>
 * 玩家：{@code this_entity}（开箱的人）是服务端玩家就用它，否则取 {@code last_damage_player}（打倒怪物的人）；都没有（漏斗、矿车拉开的箱子）
 * 就当没有玩家，在范围里平均抽。两个参数都只读、不在 {@link #getReferencedContextParams} 里声明：箱子表的参数集里没有 {@code last_damage_player}，
 * 声明了原版校验会报错。有玩家时把抽中的卷记成「掉给过他」（{@link ScrollVolumes#markGiven}），别的不改。
 * <p>
 * {@code general.ancient_scrolls.enabled} 关掉时把数量设成 0（这件物品不掉）。不是源质残卷的物品原样返回。
 * 用在 {@code chests/elder_mrucu}（上古遗迹）和 tools/gen/hoanna_loot.py 生成的城塔、墓穴、审判庭哨兵三张表里。
 * 类型在 {@link ScrollVolumeRegistration} 里用 RegisterEvent 注册（同 {@code loot/RandomTargetBalance}）。
 */
public class ScrollVolumeFunction extends LootItemConditionalFunction {

    public static final ResourceLocation ID = EssentialCraft5.id("scroll_volume");
    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());
    public static final float DEFAULT_PREFER_UNREAD = 0.75F;

    private final int min;
    private final int max;
    private final float preferUnread;

    ScrollVolumeFunction(LootItemCondition[] conditions, int min, int max, float preferUnread) {
        super(conditions);
        this.min = min;
        this.max = max;
        this.preferUnread = preferUnread;
    }

    /** 测试 / 数据生成用：不带条件的函数 */
    public static ScrollVolumeFunction create(int min, int max, float preferUnread) {
        check(min, max, preferUnread);
        return new ScrollVolumeFunction(new LootItemCondition[0], min, max, preferUnread);
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (!stack.is(ECScrolls.ANCIENT_SCROLL.get())) {
            return stack;
        }
        if (!ECScrolls.Config.values().enabled()) {
            stack.setCount(0);
            return stack;
        }
        ServerPlayer player = player(context);
        int volume = pick(player, context.getRandom());
        stack.getOrCreateTag().putInt(ScrollVolumes.TAG, volume);
        if (player != null) {
            ScrollVolumes.markGiven(player, volume);
        }
        return stack;
    }

    /** 抽一卷：有玩家且范围里有他没有的，按 prefer_unread 的几率在没有的里抽，否则整个范围平均抽（gametest/ScrollTests 直接调它看比例） */
    public int pick(@Nullable ServerPlayer player, RandomSource random) {
        if (player != null) {
            List<Integer> unread = new ArrayList<>();
            for (int v = min; v <= max; v++) {
                if (!ScrollVolumes.has(player, v)) {
                    unread.add(v);
                }
            }
            if (!unread.isEmpty() && random.nextFloat() < preferUnread) {
                return unread.get(random.nextInt(unread.size()));
            }
        }
        return min + random.nextInt(max - min + 1);
    }

    @Nullable
    private static ServerPlayer player(LootContext context) {
        Entity opener = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (opener instanceof ServerPlayer player) {
            return player;
        }
        return context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer player ? player : null;
    }

    private static void check(int min, int max, float preferUnread) {
        if (!ScrollVolumes.valid(min) || !ScrollVolumes.valid(max) || min > max) {
            throw new JsonSyntaxException("scroll_volume 的 min / max 要在 0～" + (ScrollVolumes.COUNT - 1) + " 之间且 min ≤ max，实际 " + min + " / " + max);
        }
        if (!(preferUnread >= 0F && preferUnread <= 1F)) {
            throw new JsonSyntaxException("scroll_volume 的 prefer_unread 要在 0～1 之间，实际 " + preferUnread);
        }
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<ScrollVolumeFunction> {

        @Override
        public void serialize(JsonObject json, ScrollVolumeFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("min", function.min);
            json.addProperty("max", function.max);
            json.addProperty("prefer_unread", function.preferUnread);
        }

        @Override
        public ScrollVolumeFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            int min = GsonHelper.getAsInt(json, "min");
            int max = GsonHelper.getAsInt(json, "max");
            float prefer = GsonHelper.getAsFloat(json, "prefer_unread", DEFAULT_PREFER_UNREAD);
            check(min, max, prefer);
            return new ScrollVolumeFunction(conditions, min, max, prefer);
        }
    }

    /** 把类型登记进原版的战利品函数注册表（Forge 自动发现，不用接线；类名带模块前缀，同包的事件处理器不能撞名） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ScrollVolumeRegistration {

        private ScrollVolumeRegistration() {
        }

        @SubscribeEvent
        public static void registerScrollVolume(RegisterEvent event) {
            event.register(Registries.LOOT_FUNCTION_TYPE, ID, () -> TYPE);
        }
    }
}
