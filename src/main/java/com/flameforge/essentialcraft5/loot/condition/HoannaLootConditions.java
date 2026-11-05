package com.flameforge.essentialcraft5.loot.condition;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * 海纳尔的五个战利品条件（第十三批 13d，batch13.md §6.1、§6.4；HS §2.1.2）。方块掉落表、怪物掉落表、巢穴奖励表都能用；
 * {@code tools/check_progression.py} 把前四个当「有条件、不算运气」，{@code from_nest} 当巢穴来源。
 * <ul>
 *   <li>{@code essentialcraft5:hoanna_event}{event}：掷表的世界是海纳尔，而且当前天灾就是它；{@code event} 写事件名
 *       （{@code sun_array} / {@code fumes} / {@code darkness} / {@code earthquake} / {@code ley_storm}）或 {@code any}（有任意一种，含地脉风暴）。</li>
 *   <li>{@code essentialcraft5:sky_visible}：原点上方一格看得见天（{@code canSeeSky}，按天光判断：玻璃挡不住、实心屋顶挡得住）。</li>
 *   <li>{@code essentialcraft5:night_or_darkness}：这个世界的日内刻在 [13 000, 23 000)，或者海纳尔正在黑暗天灾中。</li>
 *   <li>{@code essentialcraft5:hoanna_tide}{min}：{@code HoannaTide.value(世界) ≥ min}（潮汐关掉时恒 0.5）。</li>
 *   <li>{@code essentialcraft5:from_nest}：THIS_ENTITY（被打死的怪）是巢穴放出来的（{@code SpawnOrigin.Origin.NEST}）。</li>
 * </ul>
 * 写法例：{@code {"condition": "essentialcraft5:hoanna_event", "event": "sun_array"}}、{@code {"condition": "essentialcraft5:hoanna_tide", "min": 0.75}}。
 * datagen / 代码建表用 {@link #hoannaEvent}、{@link #skyVisible}、{@link #nightOrDarkness}、{@link #hoannaTide}、{@link #fromNest}；
 * 判定本身是静态方法（{@link #eventMatches}、{@link #skyVisibleAt}、{@link #isNightOrDarkness}、{@link #isNight}、{@link #isFromNest}），
 * 方块右键收取、刷怪谓词要同一口径时直接调。
 * 类型在 {@link HoannaLootConditionRegistration} 里用 RegisterEvent 登记（嵌套类名带前缀，免得和 loot 包里别的 Registration 撞名）。
 */
public final class HoannaLootConditions {

    public static final ResourceLocation HOANNA_EVENT_ID = EssentialCraft5.id("hoanna_event");
    public static final ResourceLocation SKY_VISIBLE_ID = EssentialCraft5.id("sky_visible");
    public static final ResourceLocation NIGHT_OR_DARKNESS_ID = EssentialCraft5.id("night_or_darkness");
    public static final ResourceLocation HOANNA_TIDE_ID = EssentialCraft5.id("hoanna_tide");
    public static final ResourceLocation FROM_NEST_ID = EssentialCraft5.id("from_nest");

    public static final LootItemConditionType HOANNA_EVENT = new LootItemConditionType(new EventCondition.Serializer());
    public static final LootItemConditionType SKY_VISIBLE = new LootItemConditionType(new SkyVisibleCondition.Serializer());
    public static final LootItemConditionType NIGHT_OR_DARKNESS = new LootItemConditionType(new NightOrDarknessCondition.Serializer());
    public static final LootItemConditionType HOANNA_TIDE = new LootItemConditionType(new TideCondition.Serializer());
    public static final LootItemConditionType FROM_NEST = new LootItemConditionType(new FromNestCondition.Serializer());

    /** {@code hoanna_event} 的 {@code event} 写这个时：有任意一种天灾就成立 */
    public static final String ANY_EVENT = "any";
    /** 夜里：日内刻 [NIGHT_START, NIGHT_END) */
    public static final int NIGHT_START = 13_000;
    public static final int NIGHT_END = 23_000;
    private static final long DAY_TICKS = 24_000L;

    private HoannaLootConditions() {
    }

    // ================================================================== 构造（datagen / 代码建表）

    /** {@code hoanna_event}：当前天灾是 event（null 或 NONE = {@code any}） */
    public static LootItemCondition.Builder hoannaEvent(@Nullable HoannaEvent event) {
        EventCondition condition = new EventCondition(event == null || event.isNone() ? null : event);
        return () -> condition;
    }

    public static LootItemCondition.Builder skyVisible() {
        return () -> SkyVisibleCondition.INSTANCE;
    }

    public static LootItemCondition.Builder nightOrDarkness() {
        return () -> NightOrDarknessCondition.INSTANCE;
    }

    public static LootItemCondition.Builder hoannaTide(float min) {
        TideCondition condition = new TideCondition(min);
        return () -> condition;
    }

    public static LootItemCondition.Builder fromNest() {
        return () -> FromNestCondition.INSTANCE;
    }

    // ================================================================== 判定（静态，别处要同一口径时直接调）

    /** 这个世界是海纳尔，而且当前天灾是 event（null = 任意一种） */
    public static boolean eventMatches(ServerLevel level, @Nullable HoannaEvent event) {
        HoannaEvent current = HoannaEvents.current(level);
        return event == null ? !current.isNone() : current == event;
    }

    /** 原点上方一格看得见天 */
    public static boolean skyVisibleAt(ServerLevel level, Vec3 origin) {
        return level.canSeeSky(BlockPos.containing(origin).above());
    }

    /** 纯函数：日内时间算不算夜里（[13 000, 23 000)，按一天取模） */
    public static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, DAY_TICKS);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    /** 这个世界此刻是夜里，或者（是海纳尔且）正在黑暗天灾中 */
    public static boolean isNightOrDarkness(ServerLevel level) {
        return isNight(level.getDayTime()) || HoannaEvents.isActive(level, HoannaEvent.DARKNESS);
    }

    /** 这只怪是巢穴放出来的 */
    public static boolean isFromNest(@Nullable Entity entity) {
        return entity != null && SpawnOrigin.of(entity) == SpawnOrigin.Origin.NEST;
    }

    // ================================================================== 五个条件

    /** {@code essentialcraft5:hoanna_event}；{@code event} 为 null 表示 {@code any} */
    public record EventCondition(@Nullable HoannaEvent event) implements LootItemCondition {

        @Override
        public LootItemConditionType getType() {
            return HOANNA_EVENT;
        }

        @Override
        public boolean test(LootContext context) {
            return eventMatches(context.getLevel(), event);
        }

        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<EventCondition> {

            @Override
            public void serialize(JsonObject json, EventCondition condition, JsonSerializationContext context) {
                json.addProperty("event", condition.event == null ? ANY_EVENT : condition.event.key());
            }

            @Override
            public EventCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                String raw = GsonHelper.getAsString(json, "event").toLowerCase(Locale.ROOT);
                if (ANY_EVENT.equals(raw)) {
                    return new EventCondition(null);
                }
                HoannaEvent event = HoannaEvent.byName(raw);
                if (event == null || event.isNone()) {
                    throw new JsonSyntaxException("hoanna_event 的 event 只能是 sun_array / fumes / darkness / earthquake / ley_storm / any：" + raw);
                }
                return new EventCondition(event);
            }
        }
    }

    /** {@code essentialcraft5:sky_visible} */
    public static final class SkyVisibleCondition implements LootItemCondition {

        public static final SkyVisibleCondition INSTANCE = new SkyVisibleCondition();

        private SkyVisibleCondition() {
        }

        @Override
        public LootItemConditionType getType() {
            return SKY_VISIBLE;
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.ORIGIN);
        }

        @Override
        public boolean test(LootContext context) {
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            return origin != null && skyVisibleAt(context.getLevel(), origin);
        }

        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<SkyVisibleCondition> {

            @Override
            public void serialize(JsonObject json, SkyVisibleCondition condition, JsonSerializationContext context) {
            }

            @Override
            public SkyVisibleCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                return INSTANCE;
            }
        }
    }

    /** {@code essentialcraft5:night_or_darkness} */
    public static final class NightOrDarknessCondition implements LootItemCondition {

        public static final NightOrDarknessCondition INSTANCE = new NightOrDarknessCondition();

        private NightOrDarknessCondition() {
        }

        @Override
        public LootItemConditionType getType() {
            return NIGHT_OR_DARKNESS;
        }

        @Override
        public boolean test(LootContext context) {
            return isNightOrDarkness(context.getLevel());
        }

        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<NightOrDarknessCondition> {

            @Override
            public void serialize(JsonObject json, NightOrDarknessCondition condition, JsonSerializationContext context) {
            }

            @Override
            public NightOrDarknessCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                return INSTANCE;
            }
        }
    }

    /** {@code essentialcraft5:hoanna_tide}{min} */
    public record TideCondition(float min) implements LootItemCondition {

        @Override
        public LootItemConditionType getType() {
            return HOANNA_TIDE;
        }

        @Override
        public boolean test(LootContext context) {
            return HoannaTide.value(context.getLevel()) >= min;
        }

        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<TideCondition> {

            @Override
            public void serialize(JsonObject json, TideCondition condition, JsonSerializationContext context) {
                json.addProperty("min", condition.min);
            }

            @Override
            public TideCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                float min = GsonHelper.getAsFloat(json, "min");
                if (!Float.isFinite(min)) {
                    throw new JsonSyntaxException("hoanna_tide 的 min 要是有限的数：" + min);
                }
                return new TideCondition(min);
            }
        }
    }

    /** {@code essentialcraft5:from_nest} */
    public static final class FromNestCondition implements LootItemCondition {

        public static final FromNestCondition INSTANCE = new FromNestCondition();

        private FromNestCondition() {
        }

        @Override
        public LootItemConditionType getType() {
            return FROM_NEST;
        }

        @Override
        public Set<LootContextParam<?>> getReferencedContextParams() {
            return Set.of(LootContextParams.THIS_ENTITY);
        }

        @Override
        public boolean test(LootContext context) {
            return isFromNest(context.getParamOrNull(LootContextParams.THIS_ENTITY));
        }

        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<FromNestCondition> {

            @Override
            public void serialize(JsonObject json, FromNestCondition condition, JsonSerializationContext context) {
            }

            @Override
            public FromNestCondition deserialize(JsonObject json, JsonDeserializationContext context) {
                return INSTANCE;
            }
        }
    }

    /** 把五个类型登记进原版的战利品条件注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class HoannaLootConditionRegistration {

        private HoannaLootConditionRegistration() {
        }

        @SubscribeEvent
        public static void registerHoannaLootConditions(RegisterEvent event) {
            event.register(Registries.LOOT_CONDITION_TYPE, HOANNA_EVENT_ID, () -> HOANNA_EVENT);
            event.register(Registries.LOOT_CONDITION_TYPE, SKY_VISIBLE_ID, () -> SKY_VISIBLE);
            event.register(Registries.LOOT_CONDITION_TYPE, NIGHT_OR_DARKNESS_ID, () -> NIGHT_OR_DARKNESS);
            event.register(Registries.LOOT_CONDITION_TYPE, HOANNA_TIDE_ID, () -> HOANNA_TIDE);
            event.register(Registries.LOOT_CONDITION_TYPE, FROM_NEST_ID, () -> FROM_NEST);
        }
    }
}
