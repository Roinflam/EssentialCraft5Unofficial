package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IWindAffinityItem;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.item.WindMageArmorItem;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * 风法师模块（ECWindMage）的 GameTest：三种类型的掉落、大法师套装的掉率（含抢夺）、MRU 箭的寿命 / 伤害归属 / 不碰方块和装饰、
 * 射箭的基础伤害、刷怪判定（蘑菇岛 / 深暗之域、附近数量上限）、刷怪表数据、套装的风之好感系数和 MRU 可见性、类型存读档。
 * <p>
 * 瓶中之风三件是 ECWind 的物品：按注册名从 ForgeRegistries 取，没注册（ECWind 还没接线）就跳过相关断言（porting_guide §6）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WindMageTests {

    private static final String EMPTY = "empty";
    private static final float EPSILON = 1.0E-4F;
    /** 按类型的掉落瓶子（ECWind 注册） */
    private static final List<String> BOTTLES = List.of("bottled_wind", "imprisoned_wind", "wind_keeper");

    private WindMageTests() {
    }

    private static Optional<Item> modItem(String path) {
        ResourceLocation id = EssentialCraft5.id(path);
        return ForgeRegistries.ITEMS.containsKey(id) ? Optional.ofNullable(ForgeRegistries.ITEMS.getValue(id)) : Optional.empty();
    }

    private static WindMageEntity mage(GameTestHelper helper, BlockPos rel, int type) {
        WindMageEntity mage = helper.spawnWithNoFreeWill(ECWindMage.WIND_MAGE.get(), rel);
        mage.setMageType(type);
        return mage;
    }

    /** 一支静止、无重力的 MRU 箭（绝对坐标） */
    private static MRUArrowEntity restingArrow(ServerLevel level, Vec3 at) {
        MRUArrowEntity arrow = new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level);
        arrow.setPos(at.x, at.y, at.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(arrow);
        return arrow;
    }

    // ---------------------------------------------------------------- 掉落

    /** 学徒 / 法师 / 大法师的掉落表对得上，死了分别掉瓶中之风 / 禁锢之风 / 受困之风（ECWind 注册了才断言物品） */
    @GameTest(template = EMPTY)
    public static void windMageTypesDropTheirBottle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int type = 0; type < WindMageEntity.CLASSIC_TYPE_COUNT; type++) {
            WindMageEntity mage = mage(helper, new BlockPos(1 + type, 2, 2), type);
            helper.assertTrue(WindMageEntity.LOOT_TABLES.get(type).equals(mage.getLootTable()),
                    "类型 " + type + " 的掉落表应为 " + WindMageEntity.LOOT_TABLES.get(type) + "，实际 " + mage.getLootTable());
            AABB around = mage.getBoundingBox().inflate(1.5D);
            mage.kill();
            helper.assertFalse(mage.isAlive(), "风法师应被杀死");
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around);
            Optional<Item> bottle = modItem(BOTTLES.get(type));
            if (bottle.isPresent()) {
                long count = drops.stream().filter(e -> e.getItem().is(bottle.get())).mapToInt(e -> e.getItem().getCount()).sum();
                helper.assertTrue(count == 1, "类型 " + type + " 应掉 1 个 " + BOTTLES.get(type) + "，实际 " + count);
            }
            drops.forEach(Entity::discard);
        }
        helper.assertTrue(WindMageEntity.LOOT_TABLES.get(WindMageEntity.MAGE).equals(ECWindMage.WIND_MAGE.get().getDefaultLootTable()),
                "法师的掉落表就是实体类型的默认表");
        helper.succeed();
    }

    /** 大法师套装掉率：没有抢夺 10%，抢夺 III 17.5%（各抽 4000 次看区间）；每次都掉一个受困之风；四件都会出现 */
    @GameTest(template = EMPTY)
    public static void archmageArmorDropRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        LootTable table = server.getLootData().getLootTable(WindMageEntity.LOOT_TABLES.get(WindMageEntity.ARCHMAGE));
        if (table == LootTable.EMPTY) {
            // 表里引用的受困之风（ECWind）没注册时整张表读不进来
            helper.assertFalse(modItem("wind_keeper").isPresent(), "受困之风已注册，大法师掉落表却没读进来");
            helper.succeed();
            return;
        }
        WindMageEntity archmage = mage(helper, new BlockPos(2, 2, 2), WindMageEntity.ARCHMAGE);
        List<Item> armor = List.of(ECWindMage.WIND_MAGE_HOOD.get(), ECWindMage.WIND_MAGE_ROBE.get(),
                ECWindMage.WIND_MAGE_LEGGINGS.get(), ECWindMage.WIND_MAGE_BOOTS.get());
        Item keeper = modItem("wind_keeper").orElse(null);
        int trials = 4000;
        for (int looting : new int[]{0, 3}) {
            Player killer = helper.makeMockSurvivalPlayer();
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            if (looting > 0) {
                sword.enchant(Enchantments.MOB_LOOTING, looting);
            }
            killer.setItemSlot(EquipmentSlot.MAINHAND, sword);
            DamageSource cause = level.damageSources().playerAttack(killer);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, archmage)
                    .withParameter(LootContextParams.ORIGIN, archmage.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, cause)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .create(LootContextParamSets.ENTITY);
            int withArmor = 0;
            int[] perPiece = new int[armor.size()];
            for (int i = 0; i < trials; i++) {
                List<ItemStack> loot = table.getRandomItems(params);
                int pieces = 0;
                int keepers = 0;
                for (ItemStack stack : loot) {
                    int index = armor.indexOf(stack.getItem());
                    if (index >= 0) {
                        pieces += stack.getCount();
                        perPiece[index]++;
                    } else if (keeper != null && stack.is(keeper)) {
                        keepers += stack.getCount();
                    }
                }
                helper.assertTrue(pieces <= 1, "一次最多掉一件套装，实际 " + pieces);
                helper.assertTrue(keeper == null || keepers == 1, "每次都应掉 1 个受困之风，实际 " + keepers);
                withArmor += pieces;
            }
            double rate = (double) withArmor / trials;
            double expected = 0.1D + 0.025D * looting;
            helper.assertTrue(Math.abs(rate - expected) < 0.025D, "抢夺 " + looting + " 时套装掉率应约 " + expected + "，实际 " + rate);
            for (int p = 0; p < perPiece.length; p++) {
                helper.assertTrue(perPiece[p] > withArmor / 8, "四件机会应差不多，第 " + p + " 件只有 " + perPiece[p] + " / " + withArmor);
            }
        }
        archmage.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- MRU 箭

    /** 存在超过 60 tick 就消失（静止、无重力，排除碰撞的影响） */
    @GameTest(template = EMPTY, timeoutTicks = 140)
    public static void mruArrowExpiresAfterSixtyTicks(GameTestHelper helper) {
        MRUArrowEntity arrow = restingArrow(helper.getLevel(), helper.absoluteVec(new Vec3(2.5D, 2.5D, 2.5D)));
        helper.assertTrue(arrow.pickup == AbstractArrow.Pickup.DISALLOWED, "MRU 箭不能捡");
        helper.startSequence()
                .thenExecuteAfter(55, () -> helper.assertFalse(arrow.isRemoved(), "55 tick 时箭还应在，tickCount " + arrow.tickCount))
                .thenExecuteAfter(10, () -> helper.assertTrue(arrow.isRemoved(), "超过 60 tick 应消失，tickCount " + arrow.tickCount))
                .thenSucceed();
    }

    /** 伤害带射手：打中的猪记下伤害来源是射箭的风法师、伤害类型是弹射物；打中后箭消失 */
    @GameTest(template = EMPTY)
    public static void mruArrowDamageCreditsShooter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WindMageEntity shooter = mage(helper, new BlockPos(0, 2, 0), WindMageEntity.APPRENTICE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 4));
        MRUArrowEntity arrow = new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter);
        arrow.setPos(pig.getX(), pig.getY() + 0.4D, pig.getZ() - 2.5D);
        arrow.setNoGravity(true);
        arrow.shoot(0D, 0D, 1D, 1.0F, 0F);
        arrow.setBaseDamage(WindMageEntity.arrowBaseDamage(WindMageEntity.APPRENTICE));
        level.addFreshEntity(arrow);
        helper.assertTrue(arrow.getOwner() == shooter, "箭的主人应是风法师");
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getLastHurtByMob() == shooter, "猪应记下是风法师打的");
            DamageSource source = pig.getLastDamageSource();
            helper.assertTrue(source != null && source.getEntity() == shooter && source.is(DamageTypeTags.IS_PROJECTILE),
                    "伤害来源应是风法师射的弹射物，实际 " + source);
            helper.assertTrue(arrow.isRemoved(), "打中后箭应消失");
        });
    }

    /** 碰到方块就散掉、不调方块的 onProjectileHit：标靶不出信号（对照：原版箭会让标靶出信号） */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void mruArrowDoesNotTriggerTargetBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos mruTarget = new BlockPos(1, 2, 4);
        BlockPos vanillaTarget = new BlockPos(3, 2, 4);
        helper.setBlock(mruTarget, Blocks.TARGET);
        helper.setBlock(vanillaTarget, Blocks.TARGET);

        MRUArrowEntity arrow = new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(1.5D, 2.5D, 1.5D));
        arrow.setPos(start.x, start.y, start.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0D, 0D, 0.8D);
        level.addFreshEntity(arrow);

        Vec3 vanillaStart = helper.absoluteVec(new Vec3(3.5D, 2.5D, 1.5D));
        Arrow control = new Arrow(level, vanillaStart.x, vanillaStart.y, vanillaStart.z);
        control.setNoGravity(true);
        control.setDeltaMovement(0D, 0D, 0.8D);
        level.addFreshEntity(control);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(arrow.isRemoved(), "MRU 箭碰到标靶应消失"))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(mruTarget).getValue(BlockStateProperties.POWER) == 0,
                            "MRU 箭不应让标靶出信号");
                    helper.assertBlockPresent(Blocks.TARGET, mruTarget);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(vanillaTarget).getValue(BlockStateProperties.POWER) > 0,
                        "对照：原版箭应让标靶出信号（测试布置有问题）"))
                .thenExecute(control::discard)
                .thenSucceed();
    }

    /** 停在木压力板里也不触发（checkInsideBlocks 不做事）；对照：原版箭会压下 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void mruArrowDoesNotPressPlates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos mruPlate = new BlockPos(1, 2, 2);
        BlockPos vanillaPlate = new BlockPos(3, 2, 2);
        helper.setBlock(mruPlate.below(), Blocks.STONE);
        helper.setBlock(vanillaPlate.below(), Blocks.STONE);
        helper.setBlock(mruPlate, Blocks.OAK_PRESSURE_PLATE);
        helper.setBlock(vanillaPlate, Blocks.OAK_PRESSURE_PLATE);
        MRUArrowEntity arrow = restingArrow(level, helper.absoluteVec(new Vec3(1.5D, 2.05D, 2.5D)));
        Vec3 at = helper.absoluteVec(new Vec3(3.5D, 2.05D, 2.5D));
        Arrow control = new Arrow(level, at.x, at.y, at.z);
        control.setNoGravity(true);
        control.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(control);
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertFalse(helper.getBlockState(mruPlate).getValue(PressurePlateBlock.POWERED), "MRU 箭不应压下压力板");
                    helper.assertTrue(helper.getBlockState(vanillaPlate).getValue(PressurePlateBlock.POWERED),
                            "对照：原版箭应压下压力板（测试布置有问题）");
                    arrow.discard();
                    control.discard();
                })
                .thenSucceed();
    }

    /** 只打活体生物：盔甲架、展示框不算目标；猪、玩家算 */
    @GameTest(template = EMPTY)
    public static void mruArrowOnlyHitsLivingCreatures(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertFalse(MRUArrowEntity.canHitTarget(new ArmorStand(EntityType.ARMOR_STAND, level)), "不打盔甲架");
        helper.assertFalse(MRUArrowEntity.canHitTarget(new ItemFrame(EntityType.ITEM_FRAME, level)), "不打展示框");
        helper.assertTrue(MRUArrowEntity.canHitTarget(new Pig(EntityType.PIG, level)), "打猪");
        helper.assertTrue(MRUArrowEntity.canHitTarget(helper.makeMockSurvivalPlayer()), "打玩家");
        helper.succeed();
    }

    /** 风法师射出的箭：主人是它、不能捡、基础伤害 (类型 + 1) × 3（按原版箭公式约 5 / 10 / 15） */
    @GameTest(template = EMPTY)
    public static void windMageShootsTypedArrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(3, 2, 3));
        for (int type = 0; type < WindMageEntity.CLASSIC_TYPE_COUNT; type++) {
            WindMageEntity mage = mage(helper, new BlockPos(1, 2, 1), type);
            mage.performRangedAttack(target, 1F);
            List<MRUArrowEntity> arrows = level.getEntitiesOfClass(MRUArrowEntity.class, mage.getBoundingBox().inflate(3D),
                    arrow -> arrow.getOwner() == mage);
            helper.assertTrue(arrows.size() == 1, "类型 " + type + " 应射出 1 支箭，实际 " + arrows.size());
            MRUArrowEntity arrow = arrows.get(0);
            double expected = (type + 1) * 3D;
            helper.assertTrue(arrow.getBaseDamage() == expected, "类型 " + type + " 的基础伤害应为 " + expected + "，实际 " + arrow.getBaseDamage());
            helper.assertTrue(arrow.pickup == AbstractArrow.Pickup.DISALLOWED, "不能捡");
            // 散布（14 − 难度 × 4）会让方向和长度都有一点偏差，只查大概
            helper.assertTrue(Math.abs(arrow.getDeltaMovement().length() - WindMageEntity.ARROW_VELOCITY) < 0.4D,
                    "出手速度应约 1.6，实际 " + arrow.getDeltaMovement().length());
            arrow.discard();
            mage.discard();
        }
        target.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 生成

    private static boolean fillBiome(ServerLevel level, BlockPos center, ResourceKey<Biome> biome) {
        BlockPos from = center.offset(-4, -4, -4);
        BlockPos to = center.offset(4, 4, 4);
        String command = String.format("fillbiome %d %d %d %d %d %d %s", from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ(), biome.location());
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
        return level.getBiome(center).is(biome);
    }

    /** 蘑菇岛、深暗之域里刷怪判定为假（群系用 /fillbiome 临时改掉，同一个 tick 里改回去）；别的群系交给原判定 */
    @GameTest(template = EMPTY)
    public static void spawnRulesExcludeMushroomAndDeepDark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        RandomSource random = level.getRandom();
        EntityType<WindMageEntity> type = ECWindMage.WIND_MAGE.get();
        helper.assertTrue(SpawnPlacements.getPlacementType(type) == SpawnPlacements.Type.ON_GROUND, "风法师应在地面上刷");
        SpawnPlacements.SpawnPredicate<WindMageEntity> biomesOnly = WindMageEntity.spawnRules((t, l, r, p, rand) -> true);

        Holder<Biome> original = level.getBiome(pos);
        Optional<ResourceKey<Biome>> originalKey = original.unwrapKey();
        helper.assertTrue(originalKey.isPresent(), "测试位置应有群系");
        try {
            if (!WindMageEntity.EXCLUDED_BIOMES.contains(originalKey.get())) {
                helper.assertTrue(biomesOnly.test(type, level, MobSpawnType.NATURAL, pos, random),
                        originalKey.get().location() + " 不在排除表里，应交给原判定（放行）");
            }
            for (ResourceKey<Biome> excluded : WindMageEntity.EXCLUDED_BIOMES) {
                helper.assertTrue(fillBiome(level, pos, excluded), "/fillbiome 没能把测试位置改成 " + excluded.location());
                helper.assertFalse(biomesOnly.test(type, level, MobSpawnType.NATURAL, pos, random),
                        excluded.location() + " 里不应刷风法师");
                helper.assertFalse(SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random),
                        excluded.location() + " 里登记的刷怪判定应为假");
            }
        } finally {
            fillBiome(level, pos, originalKey.get());
        }
        helper.assertTrue(level.getBiome(pos).is(originalKey.get()), "群系应已改回 " + originalKey.get().location());
        helper.succeed();
    }

    /** 附近数量上限：半径内已有 max 只时拒绝，少于 max 只时放行，max = 0 一律拒绝 */
    @GameTest(template = EMPTY)
    public static void spawnCapCountsNearbyMages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        List<WindMageEntity> mages = List.of(
                mage(helper, new BlockPos(1, 2, 1), WindMageEntity.APPRENTICE),
                mage(helper, new BlockPos(3, 2, 1), WindMageEntity.MAGE),
                mage(helper, new BlockPos(2, 2, 3), WindMageEntity.ARCHMAGE));
        // 半径取小的，免得数进旁边测试里的风法师
        helper.assertFalse(WindMageEntity.belowNearbyCap(level, center, 3, 4), "已有 3 只、上限 3：应拒绝");
        helper.assertTrue(WindMageEntity.belowNearbyCap(level, center, 4, 4), "已有 3 只、上限 4：应放行");
        helper.assertFalse(WindMageEntity.belowNearbyCap(level, center, 0, 4), "上限 0：不自然生成");
        mages.forEach(Entity::discard);
        helper.assertTrue(WindMageEntity.belowNearbyCap(level, center, 1, 4), "都移除以后应放行");
        helper.assertTrue(WindMageEntity.Config.maxNearby() >= 0 && WindMageEntity.Config.nearbyRadius() >= 8, "配置读数应在范围内");
        helper.succeed();
    }

    /** 刷怪表（forge:add_spawns）已经加进测试所在的主世界群系：怪物类，权重 2，每群 1~6 */
    @GameTest(template = EMPTY)
    public static void overworldSpawnListHasWindMage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> biome = level.getBiome(helper.absolutePos(new BlockPos(2, 2, 2)));
        if (!biome.is(BiomeTags.IS_OVERWORLD)) {
            helper.succeed();
            return;
        }
        MobSpawnSettings.SpawnerData data = biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .filter(spawner -> spawner.type == ECWindMage.WIND_MAGE.get()).findFirst().orElse(null);
        helper.assertTrue(data != null, "主世界群系的怪物刷怪表里应有风法师（forge/biome_modifier/wind_mage_spawns.json）");
        helper.assertTrue(data.getWeight().asInt() == 2 && data.minCount == 1 && data.maxCount == 6,
                "权重 2、每群 1~6，实际 " + data.getWeight().asInt() + "、" + data.minCount + "~" + data.maxCount);
        helper.succeed();
    }

    // ---------------------------------------------------------------- 类型存档

    /** 类型存进 NBT 的 Type、读回来一样；读到越界的值夹到 0~3（3 是第十三批的风暴术士） */
    @GameTest(template = EMPTY)
    public static void mageTypeSavedAndClamped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WindMageEntity mage = new WindMageEntity(ECWindMage.WIND_MAGE.get(), level);
        mage.setMageType(WindMageEntity.ARCHMAGE);
        CompoundTag tag = new CompoundTag();
        mage.addAdditionalSaveData(tag);
        helper.assertTrue(tag.getByte(WindMageEntity.TYPE_KEY) == WindMageEntity.ARCHMAGE, "NBT 里应存类型 2");
        WindMageEntity loaded = new WindMageEntity(ECWindMage.WIND_MAGE.get(), level);
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(loaded.getMageType() == WindMageEntity.ARCHMAGE, "读回来应是大法师");
        tag.putByte(WindMageEntity.TYPE_KEY, (byte) 7);
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(loaded.getMageType() == WindMageEntity.STORM_SORCERER, "越界的类型应夹到 3，实际 " + loaded.getMageType());
        tag.putByte(WindMageEntity.TYPE_KEY, (byte) -3);
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(loaded.getMageType() == WindMageEntity.APPRENTICE, "负数应夹到 0，实际 " + loaded.getMageType());
        mage.discard();
        loaded.discard();
        helper.succeed();
    }

    // ---------------------------------------------------------------- 套装

    /** 套装：皮革护甲值 1/3/2/1、耐久 128/384/256/128；每件风之好感系数 +0.23（四件 +0.92）；兜帽能看见 MRU */
    @GameTest(template = EMPTY)
    public static void windMageArmorStatsAffinityAndVision(GameTestHelper helper) {
        List<WindMageArmorItem> set = List.of(ECWindMage.WIND_MAGE_HOOD.get(), ECWindMage.WIND_MAGE_ROBE.get(),
                ECWindMage.WIND_MAGE_LEGGINGS.get(), ECWindMage.WIND_MAGE_BOOTS.get());
        int[] defense = {1, 3, 2, 1};
        int[] durability = {128, 384, 256, 128};
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        for (int i = 0; i < set.size(); i++) {
            WindMageArmorItem item = set.get(i);
            helper.assertTrue(item.getType() == types[i], "部位不对：" + item);
            helper.assertTrue(item.getDefense() == defense[i], item + " 的护甲值应为 " + defense[i] + "，实际 " + item.getDefense());
            helper.assertTrue(new ItemStack(item).getMaxDamage() == durability[i], item + " 的耐久应为 " + durability[i]);
            helper.assertTrue(item instanceof IWindAffinityItem, item + " 应实现 IWindAffinityItem");
        }

        Player player = helper.makeMockSurvivalPlayer();
        float bare = WindAffinity.coefficient(player);
        helper.assertFalse(MRUVision.canSee(player), "什么都没戴时看不见 MRU");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECWindMage.WIND_MAGE_HOOD.get()));
        helper.assertTrue(Math.abs(WindAffinity.coefficient(player) - bare - 0.23F) < EPSILON,
                "戴兜帽系数应 +0.23，实际 " + bare + " → " + WindAffinity.coefficient(player));
        helper.assertTrue(MRUVision.canSee(player), "戴着兜帽应能看见 MRU（tools/tags/ecwindmage.json 的 mru_vision，要先 runData）");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECWindMage.WIND_MAGE_ROBE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECWindMage.WIND_MAGE_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECWindMage.WIND_MAGE_BOOTS.get()));
        float full = WindAffinity.coefficient(player);
        helper.assertTrue(Math.abs(full - bare - 0.92F) < EPSILON, "穿齐四件系数应 +0.92，实际 " + bare + " → " + full);
        if (Math.abs(bare - WindAffinity.BASE_COEFFICIENT) < EPSILON) {
            helper.assertTrue(WindAffinity.scaled(100, full) == 102, "基础 0.1 时穿齐四件：受困之风 100 → 102，实际 " + WindAffinity.scaled(100, full));
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertFalse(MRUVision.canSee(player), "长袍、护腿、靴子不给 MRU 视野");
        helper.succeed();
    }
}
