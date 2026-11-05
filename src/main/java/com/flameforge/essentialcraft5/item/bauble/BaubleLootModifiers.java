package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;

/**
 * ο 护身符、τ 指环的全局战利品修改器（1.20 没有 HarvestDropsEvent；τ 原作用 LivingDropsEvent，改成只动掉落表，见 {@link BaubleEffects#tauBonus}）。
 * <p>
 * 实例 JSON：{@code data/essentialcraft5/loot_modifiers/omicron_amulet.json}、{@code tau_ring.json}；
 * 登记列表：{@code data/forge/loot_modifiers/global_loot_modifiers.json}（本模组唯一的一份，以后别的模块要加修改器就往 entries 里追加）。
 * 条件都在代码里判断（JSON 的 conditions 留空）：
 * <ul>
 *   <li>ο：有 {@code block_state}（方块掉落）、{@code this_entity} 是戴着 ο 的玩家、方块在他的触及距离内。
 *       箱子开出来的战利品没有 block_state，不受影响；精准采集挖出的方块本身是方块物品，不加。
 *       以下不加：{@code ECProtection.isGuardedBreak()} 期间（岩浆法杖等本模组的法杖替玩家挖，
 *       以后 drops=true 的调用方也一样）；有 {@code block_entity} 参数的（方块实体里的动态掉落原样还回来：陶罐的 4 块陶片 / 砖，
 *       模组方块用动态条目掉内容物的同理；副作用只有营火掉的木炭不再 +1）；被挖方块自己的物品形态、方块又没长成的
 *       （{@link BaubleEffects#isReplantedForm}：红石线、绊线、没长成的作物）。</li>
 *   <li>τ：有 {@code damage_source}（生物死亡掉落）、{@code killer_entity} 是戴着 τ 的玩家（原作 getTrueSource，射箭杀的也算）、死者不是玩家；
 *       死者是首领（{@code #forge:bosses}）时默认不加（{@code bauble_limits.tau_affects_bosses}）。</li>
 * </ul>
 * 编解码器在 {@link BaubleLootRegistration} 里用 RegisterEvent 登记（同 loot/NotDisabledCondition 的写法）。
 */
public final class BaubleLootModifiers {

    public static final ResourceLocation OMICRON_ID = EssentialCraft5.id("omicron_amulet");
    public static final ResourceLocation TAU_ID = EssentialCraft5.id("tau_ring");

    private BaubleLootModifiers() {
    }

    /** ο 护身符：方块掉落里的非方块物品每组 +1，每次 500 UBMRU */
    public static final class OmicronModifier extends LootModifier {
        public static final Codec<OmicronModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, OmicronModifier::new));

        public OmicronModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            // 机器、法杖代挖（breakGuarded 整段）和方块实体里的动态掉落（陶罐的陶片）不加，见类注释
            if (ECProtection.isGuardedBreak() || context.hasParam(LootContextParams.BLOCK_ENTITY)) {
                return generatedLoot;
            }
            Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
            if (state != null && origin != null && entity instanceof Player player && SpecialBaubleItem.isWearer(player)) {
                BaubleEffects.omicronBonus(player, BlockPos.containing(origin), state, generatedLoot);
            }
            return generatedLoot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    /** τ 指环：生物自己的掉落每组 +0~2，每次 1000 UBMRU */
    public static final class TauModifier extends LootModifier {
        public static final Codec<TauModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, TauModifier::new));

        public TauModifier(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            Entity killer = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
            if (context.hasParam(LootContextParams.DAMAGE_SOURCE) && victim != null && !(victim instanceof Player)
                    && killer instanceof Player player && SpecialBaubleItem.isWearer(player)) {
                BaubleEffects.tauBonus(player, victim, generatedLoot, context.getRandom());
            }
            return generatedLoot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    /** 把两个编解码器登记进 Forge 的全局战利品修改器注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class BaubleLootRegistration {

        private BaubleLootRegistration() {
        }

        @SubscribeEvent
        public static void registerBaubleLootModifiers(RegisterEvent event) {
            event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, helper -> {
                helper.register(OMICRON_ID, OmicronModifier.CODEC);
                helper.register(TAU_ID, TauModifier.CODEC);
            });
        }
    }
}
