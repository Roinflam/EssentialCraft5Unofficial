package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配方与实体预览的数据来源（每个界面实例一份缓存）
 *
 * <p>配方从客户端的配方表查（进服时同步过来），走 EC5 的 {@link RecipeFinder}：工作台、魔力工作台、辐射仓、熔炉，
 * 加上模块用 {@link GuideRecipeViews#register} 登记过画法的类型（米斯莱熔炉、矿石加工、风）。书里画出来的就是当前世界
 * 实际生效的配方 —— 数据包改了配方，书跟着变；删了配方，这一块整块不显示，而不是画一个做不出来的东西。
 * 配方表换了（/reload）时 {@link GuideView} 会重建，界面随之清掉这份缓存。</p>
 *
 * <p>实体预览要真的建一个客户端实体。建实体不便宜，而且同一篇里滚动时每帧都要画，所以按类型（加上块里写的 nbt）缓存，界面关掉就随界面一起丢。
 * 建不出来（类型不存在、构造抛异常、不是活体）记一次失败，之后退化成只显示名字，不再重试。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideMedia {

    /** 查过的配方块 → 要画的配方（可能是空表） */
    private final Map<FigureSpec.Recipes, List<Recipe<?>>> recipes = new HashMap<>();

    private final Map<String, LivingEntity> entities = new HashMap<>();
    private final Map<String, Boolean> failedEntities = new HashMap<>();

    /** 配方表换了：丢掉查过的配方 */
    void clearRecipes() {
        recipes.clear();
    }

    /**
     * 一个配方块要画的全部配方（按 {@link RecipeFinder} 的顺序：内置四种在前、登记的类型按登记先后）
     *
     * @param spec 配方块
     * @return 配方；一个都没有时为空表
     */
    List<Recipe<?>> recipes(FigureSpec.Recipes spec) {
        List<Recipe<?>> cached = recipes.get(spec);
        if (cached == null) {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                // 没进世界时（理论上不会，书只能在世界里打开）别把「查不到」缓存下来
                return List.of();
            }
            RecipeManager manager = level.getRecipeManager();
            RegistryAccess access = level.registryAccess();
            try {
                cached = List.copyOf(RecipeFinder.forPage(manager, access, spec));
            } catch (RuntimeException e) {
                GuideLog.warnOnce("recipe:" + spec, "查配方失败: " + e);
                cached = List.of();
            }
            recipes.put(spec, cached);
        }
        return cached;
    }

    /**
     * 取一个用于预览的实体，建好后合并 nbt（精英变体、颜色；见 {@link EntityPreviewSpec}）。按「类型 + nbt」缓存。
     * nbt 合并失败只记一次日志，退回不合并的实体，不算建失败。
     *
     * @param id  实体类型 id
     * @param nbt SNBT，可空
     * @return 实体；建不出来为 null
     */
    @Nullable
    LivingEntity entity(String id, @Nullable String nbt) {
        String key = EntityPreviewSpec.cacheKey(id, nbt);
        LivingEntity cached = entities.get(key);
        if (cached != null) {
            return cached;
        }
        if (failedEntities.containsKey(key)) {
            return null;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        try {
            Optional<EntityType<?>> type = EntityType.byString(id);
            Entity e = type.isPresent() ? type.get().create(level) : null;
            if (e instanceof LivingEntity living) {
                applyNbt(living, id, nbt);
                entities.put(key, living);
                return living;
            }
            GuideLog.warnOnce("entity:" + id, "实体预览：" + id + (type.isPresent() ? " 不是活体" : " 不存在") + "，只显示名字");
        } catch (Throwable t) {
            GuideLog.warnOnce("entity:" + id, "实体预览：创建 " + id + " 失败，只显示名字 —— " + t);
        }
        failedEntities.put(key, Boolean.TRUE);
        return null;
    }

    /** 先存一份完整数据、把 nbt 并进去再读回来（只写一部分键时别的字段保持原样）；位置、UUID 之类的键不并 */
    private static void applyNbt(LivingEntity living, String id, @Nullable String nbt) {
        CompoundTag parsed = EntityPreviewSpec.parseNbt(nbt);
        if (parsed == null) {
            return;
        }
        try {
            EntityPreviewSpec.merge(living, parsed);
        } catch (Throwable t) {
            GuideLog.warnOnce("entitynbt:" + id + "|" + nbt, "实体预览：" + id + " 合并 nbt 失败，按默认样子显示 —— " + t);
        }
    }

    /** 渲染时抛了异常：这一种（类型 + nbt）以后只显示名字 */
    void markEntityFailed(String id, @Nullable String nbt, Throwable t) {
        String key = EntityPreviewSpec.cacheKey(id, nbt);
        entities.remove(key);
        failedEntities.put(key, Boolean.TRUE);
        GuideLog.warnOnce("entityrender:" + key, "实体预览：渲染 " + id + " 失败，改为只显示名字 —— " + t);
    }

    /**
     * 预览框下的名字：内容写了 name（语言键）就用它，键不存在时记一条警告、退回类型名
     */
    static String entityName(String id, @Nullable String nameKey) {
        if (nameKey != null) {
            if (I18n.exists(nameKey)) {
                return I18n.get(nameKey);
            }
            GuideLog.warnOnce("entityname:" + nameKey, "实体预览：" + id + " 的 name 键 " + nameKey + " 不存在，改用类型名");
        }
        return entityName(id);
    }

    /**
     * 实体类型的本地化名字
     */
    static String entityName(String id) {
        // 内容里的 id 是人写的：格式不对、实体不存在都只退回原文，不能让排版抛出去
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                return id;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            return type == null ? id : type.getDescription().getString();
        } catch (RuntimeException e) {
            return id;
        }
    }
}
