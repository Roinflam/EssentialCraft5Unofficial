package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.SunkenAltarBlock;
import com.flameforge.essentialcraft5.block.hoanna.HoannaClusterBlock;
import com.flameforge.essentialcraft5.block.hoanna.LeyVentBlock;
import com.flameforge.essentialcraft5.block.hoanna.NestCoreBlock;
import com.flameforge.essentialcraft5.block.hoanna.TideBudBlock;
import com.flameforge.essentialcraft5.block.hoanna.UmbralClusterBlock;
import com.flameforge.essentialcraft5.block.vault.VaultKeyholeBlock;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSummon;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.event.LeyStorm;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.item.CalamityBeaconItem;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.SealedVaultStructure;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultLayout;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.event.ProtectionProbeEvent;
import com.flameforge.essentialcraft5.block.HoannaPortalBlock;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalPlacer;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.compat.curio.CurioSealMixinStatus;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.flameforge.essentialcraft5.gun.GunConfig;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.RadiationBaubleEvents;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleporter;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.WindMageSummons;
import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.item.VoidArmorEvents;
import com.flameforge.essentialcraft5.item.EssentialTool;
import com.flameforge.essentialcraft5.item.EssentialToolEvents;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import com.flameforge.essentialcraft5.teleport.HomewardChannels;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.block.HolopadBlock;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.block.InquisitoriumBeaconBlock;
import com.flameforge.essentialcraft5.blockentity.InquisitoriumBeaconBlockEntity;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.gametest.WindMovePackets;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleport;
import com.flameforge.essentialcraft5.item.MatrixTraits;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixRankData;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.matrix.MatrixResonanceConfig;
import com.flameforge.essentialcraft5.matrix.MatrixTrials;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.corruption.PresenceClaimMemory;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeContracts;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSummon;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemGuardian;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEntity;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.mojang.authlib.GameProfile;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Mohist 系混合端上的保护框架本机自检（第四批框架 F4-16，规格 batch4.md §4.16、§5.1）。
 * <b>只在 {@code -Dessentialcraft5.hybridSelftest=true} 时启用</b>（同 {@link DevShowroom} 的开关写法），正常游玩、纯 Forge、单人都不做任何事。
 * 运行方式：用混合端自检脚本（不随源码公开）（在本机冒烟服副本上开服、装测试插件 EC5Probe、跑完关服、汇总日志）。
 * <p>
 * 本模组是通用模组（policy §2.3a），权限只走 Forge 的 {@code BreakEvent} / {@code EntityPlaceEvent}；这里确认的是「在 Mohist 系混合端上，
 * 真玩家的这两个事件确实被转成 Bukkit 事件、插件的决定写得回来，删方块被插件取消时不复制」。<b>本类不引用任何 Bukkit / Mohist 类</b>：
 * 模拟领地的是测试插件 EC5Probe（不随源码公开），两边只经 JDK 的系统属性交换几行字符串：
 * <ul>
 *   <li>本类写 {@value #PROP_ORIGIN}（测试区原点 {@code x,y,z}）、{@value #PROP_OWNER} / {@value #PROP_OTHER}（两块领地的主人 UUID），插件据此划区、判定；
 *       跑完删掉 {@value #PROP_ORIGIN}，插件就什么都不管了。</li>
 *   <li>插件在 MONITOR 把测试区里的每个事件记成一行追加到 {@value #PROP_EVENTS}（格式 {@code 事件类 键=值 …}，同它的 {@code [EC5Probe]} 日志），
 *       本类每组用例开始前清空、结束后读，用来核对「插件看到的玩家是谁、是不是真玩家、被没被取消」。插件在不在：{@value #PROP_PROBE}。</li>
 * </ul>
 *
 * <h2>测试区</h2>
 * 主世界出生点 S，原点 O = (S.x+100, 200, S.z+100)，五块 10×10×10（x 偏移 0 / 20 / 40 / 60 / 80）：
 * 野外（wild，插件不管）、主人领地（owner，只有主人能动）、别人领地（other，只有另一名玩家能动）、销毁拦截区（destroy：破坏事件放行，
 * Mohist 的 {@code ModsEntityDestroyBlockEvent} 取消）、只拒放置区（placeonly：破坏放行、放置一律拒，模拟 Residence 给某人 destroy=true、place=false）。
 * 区与区之间的空隙也算野外。每块里按用例分 3×3×3 的小格子（{@link #slot}），互不干扰。开跑前把区块强制加载、整片清空，跑完清空并取消强制加载。
 * 主人 {@code EC5TestOwner}（UUID = nameUUIDFromBytes("ec5-selftest:owner")）、另一名 {@code EC5TestOther}（…:other）都是脱离网络的真 {@link ServerPlayer}
 * （同 GameTest 的 {@code ProtectionFrameworkTests.realPlayer}：不进玩家列表、不进世界），主人用在线覆盖（{@link ECProtection#setOnlineOverride}）当成在线。
 *
 * <h2>用例（batch4 §5.1；2026-09-28 范围变更后只留「玩家自己拿物品改方块」和伤害 / 掉落相关的）</h2>
 * C01 canEdit、C02 placeGuarded、C03 breakGuarded、C04 普通假玩家一律拒、C05 机器 checkAsOwner（在线 / 离线）、
 * C12 pvp=false 时无攻击者伤害打得到玩家、C13 击杀不掉落、C14 腐化 #10、C15 销毁拦截区不复制、C17 只拒放置区里玩家本人的 placeGuarded 不复制、
 * C18 射线打玩家：领地插件拦下 PvP 时不加矩阵损伤、野外和无敌帧里照加（第五批 GUNS-4）。
 * 第六批 6a M4（审查修复时补）：C23 日阵灼烧不生成火方块 / 下落方块、插件没收到点火事件；C24 地震推人只在服务端给一次速度（单刻位移远低于
 * Spigot「移动过快」门槛，真客户端的判定仍要实机看）；C25 玩家数据跟着混合端的 {@code changeDimension} 走（主世界 → 海纳尔 → 主世界，
 * 用单独的旅行者玩家）；C26 ξ 腰带打玩家：领地插件拦下 PvP 时不加辐射、野外照加（Hurt 记账、Damage 生效，同 C18）。
 * 第六批 6a M2（审查修复时补，海纳尔之门）：C19 风之石板换门被领地拒绝——门跨在别人领地边上、多格放置事件的第一格在区外（查「只看第一格的插件」这条路），
 * 整扇不变、不扣 MRU；对照空隙里的门换成、扣 MRU；C20 海纳尔侧建门的 Bukkit 放置事件报哪个世界（玩家还在主世界、快照在海纳尔）。
 * 两组的门都摆在 3×3×3 小格子以外（z = 7 / 9 那几排和区间空隙），跑完自己拆掉 / 还原。C21、C22 预留给 M2。
 * 第六批 6b（集成时补）：C27 创世神之杖的全部替换落在只拒放置区（拆得掉、放不上：留空气、圆石进背包、木板不少、只扣拆的 250）；
 * C28 魔力传送器：同维度传一次、插件取消一次（人不动、不扣）、跨维度进海纳尔一次，记 PlayerTeleportEvent 的原因和两头的世界，
 * 并看落点的权限探测（BlockBreakEvent）报的是不是目标世界。要 6b 版测试插件（认 {@value #PROP_CANCEL_TELEPORT}）。
 * 第七批（集成时补）：C13 多两只僵尸（恶魔的 demon 伤害、主人的 soul_capture 伤害打死，都不掉落）；C29 余烬元素剑打玩家：领地插件拦下 PvP 时
 * 不着火、不扣 MRU、没有闪电伤害，野外照加；C30 灵魂刻刀打村民：要 1.2.0 版测试插件（owner 区里玩家打村民的伤害事件也取消），
 * 拦下时村民还在、没有灵魂、工作站名额不放，野外收魂、名额放开。两条都用真近战 {@code Player.attack}（伤害被拦时 {@code hurt} 返回 false、不调 {@code hurtEnemy}）。
 * 第七批（审查修复时补）：C31 虚空套装挨真近战：混合端上挡伤害要等 LivingDamageEvent 才扣 MRU，领地插件拦下 PvP 时四件和身上储存物品一点不扣，野外照扣。
 * 第八批（集成时补，batch8 §9）：C32 全息投影台与 Boss（别人领地里召唤被拒、不消耗；野外召唤成功；拉回传送被插件取消时不拉、不扣血、仍在名单里）；
 * C33 轨道打击打两名玩家（owner 区里的不掉血、不着火、不被推开，区外的掉血、着火）；C34 二分场的 0 伤害探测（要 1.3.0 版测试插件：
 * owner 区里带计分板标签 {@value #PROTECTED_TAG} 的生物挨玩家打也取消；区内的僵尸不减半、没有效果，区外的减半、四种效果都有——
 * 同时验证 0 伤害的 Bukkit 伤害事件照常发给插件、放行后照常发 LivingDamageEvent）；C35 宅男套装挨真近战（同 C31：插件拦下 PvP 时四件和身上储存物品
 * 一点不扣、攻击者不挨反伤，野外照扣、攻击者挨一半的荆棘伤害）。
 * 第十批 10a（集成时补，batch10 §8.2）：C36 饰品封禁场的门（插件拦下的那一下不算交手：不压制、不扣电、不发探测；野外交过手才压，同格再评估走缓存；
 * other 区领地探测被拒；PvP 关着打不出、不压）；C37 饰品压制的 mixin 在混合端照样生效（探针饰品停 tick、护甲 +4 去掉、查不到、同步包带真物品、注入点全部到位）；
 * C43 ζ 护身符（插件拦下时不扣 UBMRU；野外扣费后本 tick 末补清无敌帧，第二下照样打中）；C44 ι 指环（插件拦下时 tick 末不补魔法伤害，野外补一下）。
 * 第十批 10b（batch10 §8.2，不用新版测试插件）：C38 风之号角（生成点全在 owner 区时以召唤者身份的探测被插件拒、不召、号角不消耗；
 * 野外召来一批、都盯着召唤者；召来的法师的箭打旁人在 EC5 自己的 LivingAttackEvent 里取消、插件收不到，打召唤者照中）；
 * C39 打实以后才生效（插件拦下 PvP 时，混沌 / 冰冻 / 魔力透镜的射线和嵌刻武器的近战都不给主人加效果、不扣嵌刻的 MRU；野外照加；
 * 10c 起加削减模块：插件拦下时不叠破甲，野外叠一层）。
 * 第十批权限修补（不用新版测试插件）：C45 σ 指环（原伤害被插件拦下时攻击者不挨反弹，野外照反）；C47 特斯拉线圈只打闯入者（owner 区里的
 * 另一名玩家本人探测被拒、主人探测放行、打中，插件只看到无攻击者的伤害事件；other 区两条都被拒、不打；野外、自己的领地不打；主人离线时只打在线时确认过的格子，
 * 别的格子一条探测都不发）；C48 太阳棱镜只烧敌对生物（玩家、牛不受伤，插件只收到僵尸那条）；
 * C49 药水扩散器有害效果只给敌对生物（驯服的狼、村民不给）；C50 磁化法杖不吸别人领地里的掉落物和经验球（以主人身份的探测被拒）。
 * 第十批 10b 审查后：C54 饰品封禁场默认配置（交手门关）的探测量：T 一直站在 other 区，连续评估 10 次插件只收到 1 条被拒的破坏事件（场按格记被拒 5 分钟）。
 * 第十批 10c-M1（不用新版测试插件）：C40 审判庭信标与哨兵（同 C32：别人领地里召唤被拒、信标还在；野外召出哨兵；拉回传送被插件取消时不拉、不扣血、仍在名单里）。
 * 第十批 10c-M2：C41 回溯模块（单独一名玩家：救下的那次混合端不发 PlayerDeathEvent、人回到快照点、盔甲还在；插件取消传送时不救不扣、照常死亡；
 * 1.4.0 版测试插件的死亡行带玩家名，旧版只数 PlayerDeathEvent 的条数）。
 * 第十批 10c（不用新版测试插件）：C42 力场穹顶（临时把插件原点换成按区块对齐的位置，让两块区块的中心分别落在 owner 区和 other 区：以主人身份的探测
 * owner 区放行、other 区被拒，owner 区那块拦自然刷怪和幻翼、other 区那块照常；结论记在穹顶里，再判不重问）。
 * 权限探测事件（要 1.5.0 版测试插件：破坏事件改在 HIGHEST 记，另在 HIGH / MONITOR 计数，{@value #PROP_NOTICE} 打开时拒绝破坏就给玩家发聊天、动作栏、
 * 标题、音效并关掉那次事件的掉落）：探测发的是 {@link ProtectionProbeEvent}，认得出它的核心只把探测交给非 MONITOR 的监听器、吞掉发给这名玩家的提示、
 * 不占全局破坏事件槽。C55 探测（HIGH 照常判、MONITOR 收不到、提示包一条都不到玩家连接上、结论不变）；C56 真挖方块（{@code gameMode.destroyBlock}：
 * MONITOR 照常收到、被拒时提示照常发到连接上、放行的照常掉落，不分核心）；C57 真挖途中插进一次被拒的探测（这一格照常掉落、MONITOR 只有真挖那条）。
 * 核心认不认探测看分发器和发包入口上有没有探测的标记（{@link #coreProbeMarker()}）：不认时 C55、C57 里只有新核心才成立的那几条
 * 不算失败，整条记成「跳过」（最后一行另报跳过几条），同一个 jar 在旧核心上照样跑完自检。收提示的是单独一名玩家 {@value #NOTICE_NAME}，连接会数包（{@link CountingConnection}）。
 * 第十二批（batch12.md §9.4，不用新版测试插件）：C58 源质镐的 3×3 一半落在 other 区（区里的格子插件拒、不挖不掉不扣钱、每格只问一次，空隙里的照挖照扣；
 * 连带挖本来在 tick 末尾做，这里同 C27 直接调它的 tick 处理器）；C59 归途罗盘的归途点在 other 区（落点探测被拒：不传、不扣钱、不进冷却）。
 * 第十三批 13a（batch13.md §12.4，不用新版测试插件）：C60 矩阵试炼的回响生成点在 other 区（开不起来、什么都不扣；野外开起来以后周围变成别人的领地，
 * 卡满 {@code STALL_TICKS} 刻中止、能量退回）；C61 矩阵阶的门（四阶过海纳尔之门不传、动作栏提示；魔力传送器往海纳尔传返回 RANK_TOO_LOW；
 * 五阶传得过去，从海纳尔回来不看阶）；C62 相性与矩阵特性打玩家（插件拦下 PvP 时不给腐蚀、不扣特性的钱；野外照给，时长乘 {@code pvp_effect_scale}）。
 * 第十三批 13b（batch13.md §12.4，不用新版测试插件）：C63 风之护甲空中跳（只记录：照原版逐包的规则喂客户端轨迹，服务端认出每一次空中跳、整跳连续离地不到 80 刻，漏包的那一组只记认出几次；
 * 会不会被当成飞行踢掉要真客户端，混合端不处理脱离网络的假玩家的移动包）；C64 虚空回击和审判庭定罪碰上插件拦下的攻击
 * （领地里挨打不攒回击、不给攻击者挂缓慢，打领地里的生物不用掉回击，领地里挨打不记定罪；野外照常）。
 * 第十三批 13c（batch13.md §12.4）：C65 首领拉回被插件取消（不拉、不扣血、仍在名单；不取消时拽回、扣血）；C66 别人领地里的五芒星召唤矩阵之影被拒
 * （符、损伤、ESPE 不动），领地主人照常召出；C67 聚能魔像的触发者在聚集体那一格没权限就不出，能落脚的地方都在别人领地里也不出；C68 矩阵游魂抓人被插件拦下时不加矩阵损伤
 * （要 1.6.0 版测试插件：owner 区里带 {@value #RESTRAINED_TAG} 标签的生物打玩家也取消；旧版插件这条记成跳过）。
 * 第十三批 13d（batch13.md §12.4）：C69 海纳尔特产的右键收取、挖辐晶簇在别人领地里被拒；C70 巢穴放怪被插件取消时回 armed、不卡死
 * （要 1.7.0 版测试插件：{@value #PROP_CANCEL_SPAWNS} 写着区名时取消那块区里的生物生成；旧版插件这条记成跳过）；C71 地脉风暴的落晶照常拾取、风暴不伤人；
 * C76（13c 留下的）首领拉回落点在被拉的人动不了的领地里时不拉、满 {@code abandon_ticks} 才移出，首领不走刻时契约照样到点判失败。
 * 第十三批 13e（batch13.md §12.4，不用新版测试插件）：C72 封印宝库的锁孔、祭座在别人领地里右键被插件拦下（不扣结晶 / 魔晶、进度不动，野外照收）；
 * C73 天灾信标的召唤点或任一根灾柱落脚点在别人领地里都不召、信标不消耗；C74 沉城祭坛在别人领地里召唤被拒、不扣材料；
 * C75 全息投影的拉回落点在被拉的人动不了的领地里（不拉不扣血、留名单，满 {@code abandon_ticks} 才移出；审判庭哨兵继承同一套）。
 * 执行器那几组（C06~C11）和机器代做（C16）随执行器等机器一起去掉了，不再测。
 * 开服 {@value #START_DELAY} tick 后开始，每 {@value #STEP_INTERVAL} tick 跑一组，每组前 {@code ECProtection.forget(主人)}。
 * 日志：{@code [EC5自检] Cnn 开始}、（插件的 {@code [EC5Probe]} 行）、{@code [EC5自检] Cnn … 期望 … 实际 … 通过 / 失败}（失败时下面跟「问题」行），
 * 最后一行 {@code [EC5自检] 完成 通过 n/m}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HybridSelfTest {

    /** 开关：{@code -Dessentialcraft5.hybridSelftest=true} */
    public static final String PROPERTY = "essentialcraft5.hybridSelftest";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);

    /** 和 EC5Probe 约定的系统属性（改了要两边一起改） */
    static final String PROP_ORIGIN = "ec5selftest.origin";
    static final String PROP_OWNER = "ec5selftest.owner";
    static final String PROP_OTHER = "ec5selftest.other";
    static final String PROP_EVENTS = "ec5probe.events";
    static final String PROP_PROBE = "ec5probe.enabled";
    /** C28：设成玩家名时，插件取消这名玩家的下一次 PlayerTeleportEvent，取消后自己删掉（6b 版测试插件起） */
    static final String PROP_CANCEL_TELEPORT = "ec5selftest.cancelTeleport";
    /** C34：带这个计分板标签的生物在 owner 区里挨玩家打时，插件也取消伤害事件（1.3.0 版测试插件起） */
    static final String PROTECTED_TAG = "ec5probe_protected";
    /** C68：带这个计分板标签的生物在 owner 区里打玩家时，插件取消伤害事件（模拟领地的「怪物不伤人」，1.6.0 版测试插件起） */
    static final String RESTRAINED_TAG = "ec5probe_restrained";
    /** C70：设成区名时，插件取消落在那块区里的生物生成（领地的「禁止刷怪」，1.7.0 版测试插件起） */
    static final String PROP_CANCEL_SPAWNS = "ec5selftest.cancelSpawnsIn";
    /** C55~C57：为 true 时插件拒绝破坏的同时给玩家发聊天、动作栏、标题、音效，并关掉那次事件的掉落（1.5.0 版测试插件起） */
    static final String PROP_NOTICE = "ec5selftest.noticeDenied";
    /** C55~C57：插件在 HIGH / MONITOR 各收到几条测试区附近的 BlockBreakEvent、拒绝时发过几次提示（1.5.0 版测试插件起；每组清零） */
    static final String PROP_BREAK_HIGH = "ec5probe.breakHigh";
    static final String PROP_BREAK_MONITOR = "ec5probe.breakMonitor";
    static final String PROP_NOTICES = "ec5probe.notices";
    /**
     * 核心认不认权限探测（{@link ProtectionProbeEvent}）主要看这个类：Mohist 系混合端把 Forge 破坏事件转给插件的分发器
     * （判法见 {@link #coreProbeMarker()}；只按名字反射、读它的字节码，不引用它）。
     */
    static final String BLOCK_EVENT_DISPATCHER = "com.mohistmc.eventhandler.dispatcher.BlockEventDispatcher";

    static final UUID OWNER_ID = UUID.nameUUIDFromBytes("ec5-selftest:owner".getBytes(StandardCharsets.UTF_8));
    static final UUID OTHER_ID = UUID.nameUUIDFromBytes("ec5-selftest:other".getBytes(StandardCharsets.UTF_8));
    static final UUID FAKE_ID = UUID.nameUUIDFromBytes("ec5-selftest:fake".getBytes(StandardCharsets.UTF_8));
    static final String OWNER_NAME = "EC5TestOwner";
    static final String OTHER_NAME = "EC5TestOther";
    static final String FAKE_NAME = "[EC5]SelfTestFake";
    /** C25 换维度用的第三名玩家（换完从世界里移除，不影响主人 / 另一名） */
    static final UUID TRAVELLER_ID = UUID.nameUUIDFromBytes("ec5-selftest:traveller".getBytes(StandardCharsets.UTF_8));
    static final String TRAVELLER_NAME = "EC5TestTraveller";
    /** C28 坐传送器的第四名玩家（同 C25：会进别的世界，跑完移除） */
    static final UUID PORTER_ID = UUID.nameUUIDFromBytes("ec5-selftest:porter".getBytes(StandardCharsets.UTF_8));
    static final String PORTER_NAME = "EC5TestPorter";
    /** C55~C57 收拒绝提示的玩家：连接是数包的 {@link CountingConnection}；owner / other 区都不是他的，野外和空隙能动 */
    static final UUID NOTICE_ID = UUID.nameUUIDFromBytes("ec5-selftest:notice".getBytes(StandardCharsets.UTF_8));
    static final String NOTICE_NAME = "EC5TestNotice";
    /** C63 单独建的跳的人 */
    static final UUID JUMPER_ID = UUID.nameUUIDFromBytes("ec5-selftest:jumper".getBytes(StandardCharsets.UTF_8));
    static final String JUMPER_NAME = "EC5TestJumper";

    private static final String TAG = "[EC5自检]";
    private static final int START_DELAY = 100;
    private static final int STEP_INTERVAL = 2;
    private static final int ZONE_STEP = 20;
    private static final int ZONE_SIZE = 10;
    private static final int ZONE_COUNT = 5;
    private static final int WILD = 0;
    private static final int OWN = 1;
    private static final int OTHERS = 2;
    private static final int DESTROY = 3;
    private static final int PLACE_ONLY = 4;
    /** 和插件的区名一致 */
    private static final String[] ZONE_NAMES = {"wild", "owner", "other", "destroy", "placeonly"};
    /** 清空、查实体时比测试区多留的边 */
    private static final int MARGIN = 4;
    /** C60 的一阶测试数据：UBMRU、台子 MRU（不要图谱和祭品，回响 2 只、同时 1 只、60 秒，同 GameTest 的 MatrixTrialTests） */
    private static final int TRIAL_UBMRU = 100;
    private static final int TRIAL_MRU = 1000;
    /** C60：试炼开始前主人身上的 UBMRU、台子里的 MRU */
    private static final int TRIAL_UBMRU_START = 1000;
    private static final int TRIAL_MRU_START = 5000;
    /** C66：每根柱子灌多少 ESPE（12 根合计够转进 30 000 点要的 15 000 有效 ESPE） */
    private static final double C66_PILLAR_ESPE = 10_000D;
    /** C66：两人身上的矩阵损伤（够 18 000 的门槛） */
    private static final int C66_DAMAGE = 30_000;

    /** {@code PlayerList.playersByUUID}（SRG 名，C14 临时把主人登记成在线用） */
    private static final String PLAYERS_BY_UUID = "f_11197_";

    @Nullable
    private static Run run;
    private static int countdown = -1;

    private HybridSelfTest() {
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    @SubscribeEvent
    public static void onSelfTestServerStarted(ServerStartedEvent event) {
        if (!ENABLED) {
            return;
        }
        countdown = START_DELAY;
        EssentialCraft5.LOGGER.info("{} 已启用（-D{}=true），{} tick 后开始；测试插件 EC5Probe {}", TAG, PROPERTY, START_DELAY,
                System.getProperty(PROP_PROBE) != null ? "已加载" : "没有加载（领地判定全部不会生效，用例会失败）");
    }

    @SubscribeEvent
    public static void onSelfTestServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (countdown > 0 && --countdown == 0) {
            run = new Run(event.getServer());
        }
        Run current = run;
        if (current != null && current.tick()) {
            run = null;
        }
    }

    @SubscribeEvent
    public static void onSelfTestServerStopping(ServerStoppingEvent event) {
        Run current = run;
        run = null;
        countdown = -1;
        if (current != null) {
            EssentialCraft5.LOGGER.warn("{} 关服时还没跑完，收尾后放弃", TAG);
            current.teardown();
        }
    }

    // ==================================================================
    // 一次自检
    // ==================================================================

    /**
     * 一组用例的结论：pass 为 null 表示只记录。skipped 为 true 表示「核心不支持，跳过」（只记录、不算失败，汇总里单独数；
     * 用在要新版核心才成立的用例上，同一个 jar 在旧核心上照样能跑完自检）。
     */
    private record Outcome(@Nullable Boolean pass, String expected, String actual, List<String> problems, boolean skipped) {
        Outcome(@Nullable Boolean pass, String expected, String actual, List<String> problems) {
            this(pass, expected, actual, problems, false);
        }

        static Outcome skipped(String expected, String actual, List<String> problems) {
            return new Outcome(null, expected, actual, problems, true);
        }
    }

    private record Case(String id, String title, Supplier<Outcome> body) {
    }

    /** C69 收一排海纳尔特产的结果：拿到的地脉结晶 / 潮晶 / 影晶，收完喷口的充能、潮晶芽的阶段、影晶簇熟没熟，辐晶簇还在不在，剂量涨了多少 */
    private record Harvest(int ley, int tide, int umbral, int charge, int stage, boolean ripe, boolean clusterLeft, int dose) {
        @Override
        public String toString() {
            return "结晶 " + ley + " 潮晶 " + tide + " 影晶 " + umbral + " 喷口 " + charge + " 芽 " + stage + " 成熟 " + ripe
                    + " 辐晶簇在 " + clusterLeft + " 剂量 +" + dose;
        }
    }

    private static final class Run {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<Case> cases = new ArrayList<>();
        private final List<Long> forcedByUs = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private ServerPlayer owner;
        private ServerPlayer other;
        private int passed;
        private int judged;
        private int index = -1;
        private int wait;
        private boolean tornDown;

        Run(MinecraftServer server) {
            this.server = server;
            this.level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            this.origin = new BlockPos(spawn.getX() + 100, 200, spawn.getZ() + 100);
            cases.add(new Case("C01", "canEdit(主人)", this::c01));
            cases.add(new Case("C02", "placeGuarded(主人, 石头, UP)", this::c02));
            cases.add(new Case("C03", "breakGuarded(主人, 泥土)", this::c03));
            cases.add(new Case("C04", "普通 FakePlayer 的 canEdit", this::c04));
            cases.add(new Case("C05", "机器 checkAsOwner：主人在线 / 离线", this::c05));
            cases.add(new Case("C12", "pvp=false：mruDischarge 伤主人，对照 playerAttack(别人)", this::c12));
            cases.add(new Case("C13", "击杀不掉落：mruDischarge / generic / demon / soul_capture 打死僵尸", this::c13));
            cases.add(new Case("C14", "腐化 #10 useMainHand：站野外看着别人领地、手拿骨粉", this::c14));
            cases.add(new Case("C15", "销毁拦截区：breakGuarded / harvestGuarded 被 Mohist 取消", this::c15));
            cases.add(new Case("C17", "只拒放置区：玩家本人 placeGuarded 改唱片机 / 雕纹书架", this::c17));
            cases.add(new Case("C18", "射线打玩家：领地插件拦 PvP 不加矩阵损伤，野外 / 无敌帧照加", this::c18));
            cases.add(new Case("C19", "风之石板换门：门跨在别人领地边上（第一格在区外）整扇不换不扣，对照空隙里的换成", this::c19));
            cases.add(new Case("C20", "海纳尔侧建门：Bukkit 放置事件报的是海纳尔世界（不按主世界同坐标的领地拒）", this::c20));
            cases.add(new Case("C23", "日阵灼烧：掉血着火，不生成火方块 / 下落方块，插件没收到点火事件", this::c23));
            cases.add(new Case("C24", "地震推人：服务端只给一次速度（≤ 0.6 格/刻），远低于移动过快门槛", this::c24));
            cases.add(new Case("C25", "玩家数据跟着 changeDimension 走：主世界 → 海纳尔 → 主世界", this::c25));
            cases.add(new Case("C26", "ξ 腰带打玩家：领地插件拦 PvP 不加辐射，野外照加", this::c26));
            cases.add(new Case("C27", "创世神之杖全部替换落在只拒放置区：留空气、圆石进背包、木板不少、只扣 250", this::c27));
            cases.add(new Case("C28", "魔力传送器：同维度 / 插件取消 / 跨维度，记传送原因，落点探测报目标世界", this::c28));
            cases.add(new Case("C29", "余烬元素剑打玩家：领地插件拦 PvP 时不着火、不扣 MRU、没有闪电伤害，野外照加", this::c29));
            cases.add(new Case("C30", "灵魂刻刀打村民：插件拦「玩家打村民」时村民还在、没有灵魂、工作站不放，野外收魂", this::c30));
            cases.add(new Case("C31", "虚空套装挨真近战：插件拦 PvP 时四件和灵魂物质都不扣 MRU，野外由灵魂物质付 4 × 每点单价（默认 1600）", this::c31));
            cases.add(new Case("C32", "全息投影台：别人领地里召唤被拒、台子还在；野外召唤成功；拉回传送被插件取消就不拉、不扣血、仍在名单里", this::c32));
            cases.add(new Case("C33", "轨道打击：owner 区里的玩家不掉血、不着火、不被推开，区外的掉血、着火", this::c33));
            cases.add(new Case("C34", "二分场：owner 区里带保护标签的僵尸探测被拒、不减半，区外的减半、四种效果", this::c34));
            cases.add(new Case("C35", "宅男套装挨真近战：插件拦 PvP 时一点不扣、攻击者不挨反伤，野外照扣、攻击者挨荆棘", this::c35));
            cases.add(new Case("C36", "饰品封禁场的门：插件拦下的一下不算交手、不压不扣不探测；野外交过手才压；同格探测走缓存；别人领地没权限；PvP 关不压", this::c36));
            cases.add(new Case("C37", "饰品压制 mixin 在混合端生效：探针饰品停 tick、护甲 +4 去掉、findFirstCurio 查不到、同步包带真物品", this::c37));
            cases.add(new Case("C38", "风之号角：生成点全在别人领地时不召、号角还在；野外召来、只盯召唤者；法师的箭打旁人被取消、打召唤者照中", this::c38));
            cases.add(new Case("C39", "打实以后才生效：插件拦 PvP 时透镜（腐蚀 / 冰冻 / 驱散）、嵌刻武器、削减模块都不给效果、不扣 MRU，野外照加", this::c39));
            cases.add(new Case("C40", "审判庭信标：别人领地里召唤被拒、信标还在；野外召出哨兵；拉回传送被插件取消就不拉、不扣血、仍在名单里", this::c40));
            cases.add(new Case("C41", "回溯：救下时插件收不到 PlayerDeathEvent、人回到快照点、盔甲还在；插件取消传送时不救不扣、照常死亡", this::c41));
            cases.add(new Case("C42", "力场穹顶：以主人身份逐区块问领地，owner 区那块拦自然刷怪和幻翼、other 区那块照常刷；结论记在穹顶里不重问", this::c42));
            cases.add(new Case("C43", "ζ 护身符：插件拦下时不扣 UBMRU；野外扣 300、tick 末补清无敌帧，紧接着第二下能打中", this::c43));
            cases.add(new Case("C44", "ι 指环：插件拦下时 tick 末不补魔法伤害；野外多一下 indirect_magic、合计掉原伤害", this::c44));
            cases.add(new Case("C45", "σ 指环：原伤害被插件拦下时攻击者不挨反弹，野外照反", this::c45));
            cases.add(new Case("C47", "特斯拉线圈只打闯入者：owner 区里的另一名玩家打、other 区（主人也被拒）不打、野外和自己的领地不打；主人离线只打确认过的格子", this::c47));
            cases.add(new Case("C48", "太阳棱镜：只烧敌对生物，owner 区里的玩家、牛不受伤", this::c48));
            cases.add(new Case("C49", "药水扩散器：有害效果只给敌对生物，驯服的狼、村民不给", this::c49));
            cases.add(new Case("C50", "磁化法杖：别人领地里的掉落物和经验球不拉、各发一次被拒的破坏探测，区外的照拉", this::c50));
            cases.add(new Case("C54", "饰品封禁场默认配置（不要求交手）：T 一直站在 other 区，连续评估 10 次插件只收到 1 条破坏事件、每次没有权限", this::c54));
            cases.add(new Case("C55", "权限探测（ProtectionProbeEvent）：插件 HIGH 照常判、MONITOR 收不到，拒绝提示不发到玩家连接上，探测结论不变", this::c55));
            cases.add(new Case("C56", "真挖方块（gameMode.destroyBlock）：MONITOR 照常收到、被拒时提示照常发到玩家连接上，放行的照常掉落", this::c56));
            cases.add(new Case("C57", "真挖途中插进一次被拒的探测：全局破坏事件槽不被探测覆盖，这一格照常掉落", this::c57));
            cases.add(new Case("C58", "源质镐 3×3 跨进别人领地：区里那 3 格不挖、不掉、不扣钱，每格只问一次；空隙里 6 格照挖照扣", this::c58));
            cases.add(new Case("C59", "归途罗盘的归途点在别人领地里：到点落点探测被拒，不传、不扣钱、不进冷却", this::c59));
            cases.add(new Case("C60", "矩阵试炼：回响生成点全在别人领地时开不起来、什么都不扣；野外开起来后周围变成别人的领地，卡满中止、能量退回", this::c60));
            cases.add(new Case("C61", "矩阵阶的门：四阶过海纳尔之门不传、动作栏提示；传送器往海纳尔 RANK_TOO_LOW；五阶传得过去，从海纳尔回来不挡", this::c61));
            cases.add(new Case("C62", "相性与矩阵特性打玩家：插件拦 PvP 时没有腐蚀、不扣钱；野外腐蚀时长乘 pvp_effect_scale、扣一次特性的钱", this::c62));
            cases.add(new Case("C63", "风之护甲空中跳：服务端照客户端轨迹认出每一次空中跳、连续离地不到 80 刻（只记录，飞行踢人要真客户端）", this::c63));
            cases.add(new Case("C64", "虚空回击与审判庭定罪：插件拦下的那一下不攒回击、不用掉回击、不记定罪；野外照常", this::c64));
            cases.add(new Case("C65", "首领拉回：插件取消传送时不拉、不扣血、仍在名单里、仍是技能目标；不取消时拽回场地、扣血", this::c65));
            cases.add(new Case("C66", "矩阵之影：别人领地里的五芒星召唤被拒，符、损伤、ESPE 一样不少；领地主人照常召出、照扣", this::c66));
            cases.add(new Case("C67", "聚能魔像：走近的人在聚集体那一格没权限就不出、守护还在等；领地主人走近才出来；能落脚的地方都在别人领地里也不出", this::c67));
            cases.add(new Case("C68", "矩阵游魂抓人被插件拦下（领地「怪物不伤人」）：没打中、不加矩阵损伤；野外照加", this::c68));
            cases.add(new Case("C69", "海纳尔特产：别人领地里右键喷口 / 潮晶芽 / 影晶簇拿不到东西、状态不变，挖辐晶簇被拒不加剂量；野外照收", this::c69));
            cases.add(new Case("C70", "巢穴核心：放怪被插件取消（领地禁止刷怪）时回 armed、进重试冷却、不算战斗中；野外照放", this::c70));
            cases.add(new Case("C71", "地脉风暴：落晶照常生成、碰到就收；露天每秒只加辐射，不掉血、不着火", this::c71));
            cases.add(new Case("C72", "封印宝库：别人领地里右键锁孔 / 祭座被插件拦下，结晶、魔晶不扣、进度不动；野外照收", this::c72));
            cases.add(new Case("C73", "天灾信标：召唤点或任一根灾柱的落脚点在别人领地里都不召，信标还是满的", this::c73));
            cases.add(new Case("C74", "沉城祭坛：别人领地里召唤被拒、潮晶和异变之心不扣；野外过了权限这一步", this::c74));
            cases.add(new Case("C75", "全息投影拉回落点在被拉的人动不了的领地里：不拉不扣血、留名单，满 abandon_ticks 才移出", this::c75));
            cases.add(new Case("C76", "首领拉回落点在被拉的人动不了的领地里：不拉不扣血、留名单，满 abandon_ticks 才移出；首领不走刻时契约照样到点判失败", this::c76));
        }

        /** @return 跑完了 */
        boolean tick() {
            if (wait-- > 0) {
                return false;
            }
            wait = STEP_INTERVAL - 1;
            if (index < 0) {
                setup();
                index = 0;
                return false;
            }
            if (index < cases.size()) {
                runCase(cases.get(index++));
                return false;
            }
            finish();
            return true;
        }

        // ------------------------------------------------------------------ 准备与收尾

        private void setup() {
            System.setProperty(PROP_ORIGIN, origin.getX() + "," + origin.getY() + "," + origin.getZ());
            System.setProperty(PROP_OWNER, OWNER_ID.toString());
            System.setProperty(PROP_OTHER, OTHER_ID.toString());
            System.setProperty(PROP_EVENTS, "");
            int minCx = (origin.getX() - MARGIN) >> 4;
            int maxCx = (origin.getX() + ZONE_STEP * (ZONE_COUNT - 1) + ZONE_SIZE + MARGIN) >> 4;
            int minCz = (origin.getZ() - MARGIN) >> 4;
            int maxCz = (origin.getZ() + ZONE_SIZE + MARGIN) >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
                        level.setChunkForced(cx, cz, true);
                        forcedByUs.add(ChunkPos.asLong(cx, cz));
                    }
                    level.getChunk(cx, cz);   // 同步加载到 FULL，ECLevels.isFullyLoaded 才认
                }
            }
            clearArea();
            owner = realPlayer(OWNER_ID, OWNER_NAME);
            other = realPlayer(OTHER_ID, OTHER_NAME);
            // C12 要让主人挨打：先跑掉 60 tick 的出生无敌（spawnInvulnerableTime 只在 ServerPlayer.tick 里减，同 CorruptionTests.victim）
            for (int i = 0; i <= 60; i++) {
                owner.tick();
            }
            ECProtection.setOnlineOverride(owner);
            String marker = coreProbeMarker();
            EssentialCraft5.LOGGER.info("{} 开始：原点 {}（出生点 {}），区块强制加载 {} 个；服务端 PvP {}；插件 {}；主人 {} {}，另一名 {} {}；核心认权限探测：{}",
                    TAG, origin.toShortString(), level.getSharedSpawnPos().toShortString(), forcedByUs.size(),
                    server.isPvpAllowed() ? "开" : "关", System.getProperty(PROP_PROBE) != null ? "在" : "不在",
                    OWNER_NAME, OWNER_ID, OTHER_NAME, OTHER_ID, marker == null ? "否（C55、C57 记成跳过）" : "是（" + marker + "）");
        }

        private void finish() {
            teardown();
            if (!failed.isEmpty()) {
                EssentialCraft5.LOGGER.info("{} 失败的用例：{}", TAG, String.join(" ", failed));
            }
            if (!skipped.isEmpty()) {
                EssentialCraft5.LOGGER.info("{} 核心不支持、跳过的用例：{}", TAG, String.join(" ", skipped));
            }
            EssentialCraft5.LOGGER.info("{} 完成 通过 {}/{}{}", TAG, passed, judged, skipped.isEmpty() ? "" : "，跳过 " + skipped.size());
        }

        void teardown() {
            if (tornDown) {
                return;
            }
            tornDown = true;
            ECProtection.clearOnlineOverride(OWNER_ID);
            ECProtection.forget(OWNER_ID);
            ECProtection.forget(OTHER_ID);
            System.clearProperty(PROP_ORIGIN);
            System.clearProperty(PROP_EVENTS);
            System.clearProperty(PROP_CANCEL_TELEPORT);
            System.clearProperty(PROP_NOTICE);
            System.clearProperty(PROP_CANCEL_SPAWNS);
            ECProtection.forget(NOTICE_ID);
            try {
                clearArea();
            } catch (RuntimeException e) {
                EssentialCraft5.LOGGER.error("{} 清理测试区出错", TAG, e);
            }
            for (long chunk : forcedByUs) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
            forcedByUs.clear();
        }

        /** 测试区（含边）里的方块全部换成空气（先清空容器，免得掉东西），非玩家实体全部删掉 */
        private void clearArea() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int maxX = origin.getX() + ZONE_STEP * (ZONE_COUNT - 1) + ZONE_SIZE + MARGIN;
            for (int x = origin.getX() - MARGIN; x <= maxX; x++) {
                for (int y = origin.getY() - 2; y <= origin.getY() + ZONE_SIZE + 2; y++) {
                    for (int z = origin.getZ() - MARGIN; z <= origin.getZ() + ZONE_SIZE + MARGIN; z++) {
                        pos.set(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            Clearable.tryClear(level.getBlockEntity(pos));
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                }
            }
            for (Entity entity : level.getEntities((Entity) null, areaBox(), entity -> !(entity instanceof Player))) {
                entity.discard();
            }
        }

        private AABB areaBox() {
            return new AABB(origin.getX() - MARGIN, origin.getY() - 2, origin.getZ() - MARGIN,
                    origin.getX() + ZONE_STEP * (ZONE_COUNT - 1) + ZONE_SIZE + MARGIN + 1, origin.getY() + ZONE_SIZE + 3,
                    origin.getZ() + ZONE_SIZE + MARGIN + 1);
        }

        // ------------------------------------------------------------------ 跑一组

        private void runCase(Case c) {
            EssentialCraft5.LOGGER.info("{} {} 开始 {}", TAG, c.id(), c.title());
            ECProtection.forget(OWNER_ID);
            ECProtection.forget(OTHER_ID);
            System.setProperty(PROP_EVENTS, "");
            resetBreakCounters();
            Outcome outcome;
            try {
                outcome = c.body().get();
            } catch (RuntimeException | LinkageError e) {
                EssentialCraft5.LOGGER.error("{} {} 抛出异常", TAG, c.id(), e);
                outcome = new Outcome(false, "不抛异常", e.toString(), List.of());
            } finally {
                ECProtection.setOnlineOverride(owner);   // C05 会临时撤掉
            }
            String verdict;
            if (outcome.skipped()) {
                skipped.add(c.id());
                verdict = "跳过";
            } else if (outcome.pass() == null) {
                verdict = "记录";
            } else {
                judged++;
                if (outcome.pass() && outcome.problems().isEmpty()) {
                    passed++;
                    verdict = "通过";
                } else {
                    failed.add(c.id());
                    verdict = "失败";
                }
            }
            EssentialCraft5.LOGGER.info("{} {} {} 期望 {} 实际 {} {}", TAG, c.id(), c.title(), outcome.expected(), outcome.actual(), verdict);
            for (String problem : outcome.problems()) {
                EssentialCraft5.LOGGER.info("{} {}   问题：{}", TAG, c.id(), problem);
            }
        }

        // ------------------------------------------------------------------ 用例

        /** C01：canEdit(主人) 允许 / 允许 / 拒；插件三条 BlockBreakEvent，CraftPlayer */
        private Outcome c01() {
            String[] actual = new String[3];
            for (int zone = WILD; zone <= OTHERS; zone++) {
                BlockPos cell = slot(zone, 0).offset(1, 0, 1);
                set(cell, Blocks.STONE.defaultBlockState());
                actual[zone] = name(ECProtection.check(owner, level, cell, owner));
            }
            Probe probe = Probe.read();
            List<String> problems = new ArrayList<>();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                probe.expect(problems, "BlockBreakEvent", zone, OWNER_NAME, "CraftPlayer", zone == OTHERS);
            }
            probe.expectCount(problems, "BlockBreakEvent", 3);
            return verdicts("允许/允许/拒绝", actual, problems);
        }

        /** C02：placeGuarded(主人, 石头, UP) 放 / 放 / 不放且原样；插件 BlockPlaceEvent */
        private Outcome c02() {
            String[] actual = new String[3];
            List<String> problems = new ArrayList<>();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                BlockPos cell = slot(zone, 1).offset(1, 1, 1);
                set(cell.below(), Blocks.STONE.defaultBlockState());
                boolean placed = ECProtection.placeGuarded(owner, level, cell, Blocks.STONE.defaultBlockState(), Direction.UP);
                BlockState after = level.getBlockState(cell);
                actual[zone] = placed ? "放" : "不放";
                boolean expectPlaced = zone != OTHERS;
                if (after.is(Blocks.STONE) != expectPlaced) {
                    problems.add(ZONE_NAMES[zone] + " 返回 " + placed + " 但方块是 " + after);
                }
            }
            Probe probe = Probe.read();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                probe.expect(problems, "BlockPlaceEvent", zone, OWNER_NAME, "CraftPlayer", zone == OTHERS);
            }
            return verdicts("放/放/不放", actual, problems);
        }

        /** C03：breakGuarded(主人, 泥土) 挖 / 挖 / 不挖；挖了的掉一份泥土；插件 BlockBreakEvent（放行的另有 ModsEntityDestroyBlockEvent） */
        private Outcome c03() {
            String[] actual = new String[3];
            List<String> problems = new ArrayList<>();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                BlockPos cell = slot(zone, 2).offset(1, 0, 1);
                set(cell, Blocks.DIRT.defaultBlockState());
                boolean broken;
                int dirt;
                try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                    broken = ECProtection.breakGuarded(owner, level, cell, ItemStack.EMPTY, true);
                    dirt = countItems(spawns.alive(), Items.DIRT);
                    spawns.discardAll();
                }
                actual[zone] = broken ? "挖" : "不挖";
                boolean expectBroken = zone != OTHERS;
                if (level.getBlockState(cell).isAir() != expectBroken) {
                    problems.add(ZONE_NAMES[zone] + " 返回 " + broken + " 但方块是 " + level.getBlockState(cell));
                }
                if (dirt != (expectBroken ? 1 : 0)) {
                    problems.add(ZONE_NAMES[zone] + " 掉了 " + dirt + " 个泥土，应为 " + (expectBroken ? 1 : 0));
                }
            }
            Probe probe = Probe.read();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                probe.expect(problems, "BlockBreakEvent", zone, OWNER_NAME, "CraftPlayer", zone == OTHERS);
            }
            probe.expect(problems, "ModsEntityDestroyBlockEvent", WILD, OWNER_NAME, "CraftPlayer", false);
            probe.expect(problems, "ModsEntityDestroyBlockEvent", OWN, OWNER_NAME, "CraftPlayer", false);
            return verdicts("挖/挖/不挖", actual, problems);
        }

        /** C04：普通 FakePlayer 的 canEdit 拒 ×3，而且插件什么事件都没收到（它的事件混合端不转，ECProtection 根本不发） */
        private Outcome c04() {
            FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(FAKE_ID, FAKE_NAME));
            String[] actual = new String[3];
            for (int zone = WILD; zone <= OTHERS; zone++) {
                BlockPos cell = slot(zone, 3).offset(1, 0, 1);
                set(cell, Blocks.STONE.defaultBlockState());
                actual[zone] = name(ECProtection.check(fake, level, cell, fake));
            }
            List<String> problems = new ArrayList<>();
            Probe.read().expectCount(problems, null, 0);
            return verdicts("拒绝/拒绝/拒绝", actual, problems);
        }

        /** C05：机器 checkAsOwner——主人在线覆盖：同 C01；清掉覆盖：UNKNOWN ×3、没有事件 */
        private Outcome c05() {
            BlockPos machinePos = slot(WILD, 4).offset(1, 1, 1);
            set(machinePos, ECBlocks.CHARGING_CHAMBER.get().defaultBlockState());
            if (!(level.getBlockEntity(machinePos) instanceof MRUBlockEntity machine)) {
                return new Outcome(false, "充能室有方块实体", "没有", List.of());
            }
            machine.setOwner(OWNER_ID, OWNER_NAME);
            BlockPos[] cells = new BlockPos[3];
            for (int zone = WILD; zone <= OTHERS; zone++) {
                cells[zone] = slot(zone, 4);
                set(cells[zone], Blocks.STONE.defaultBlockState());
            }
            List<String> problems = new ArrayList<>();
            String[] online = new String[3];
            for (int zone = WILD; zone <= OTHERS; zone++) {
                online[zone] = name(ECProtection.checkAsOwner(machine, cells[zone]));
            }
            Probe probe = Probe.read();
            for (int zone = WILD; zone <= OTHERS; zone++) {
                probe.expect(problems, "BlockBreakEvent", zone, OWNER_NAME, "CraftPlayer", zone == OTHERS);
            }
            ECProtection.clearOnlineOverride(OWNER_ID);
            ECProtection.forget(OWNER_ID);
            System.setProperty(PROP_EVENTS, "");
            String[] offline = new String[3];
            ECProtection.OwnerState state = ECProtection.ownerState(machine);
            for (int zone = WILD; zone <= OTHERS; zone++) {
                offline[zone] = name(ECProtection.checkAsOwner(machine, cells[zone]));
            }
            if (state != ECProtection.OwnerState.OFFLINE) {
                problems.add("清掉覆盖后主人状态应为 OFFLINE，实际 " + state);
            }
            Probe.read().expectCount(problems, null, 0);
            ECProtection.setOnlineOverride(owner);
            String actual = String.join("/", online) + "；离线 " + String.join("/", offline);
            boolean pass = actual.equals("允许/允许/拒绝；离线 不确定/不确定/不确定");
            return new Outcome(pass, "允许/允许/拒绝；离线 不确定/不确定/不确定", actual, problems);
        }

        /** C12：pvp=false——对照 playerAttack(别人) 伤不到主人；mruDischarge（无攻击者）伤得到；插件看到的是 EntityDamageEvent 没被取消 */
        private Outcome c12() {
            BlockPos at = slot(OWN, 5).offset(1, 1, 1);
            owner.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            owner.setHealth(owner.getMaxHealth());
            List<String> problems = new ArrayList<>();
            if (server.isPvpAllowed()) {
                problems.add("服务端 PvP 是开的（server.properties 要 pvp=false），对照组不成立");
            }
            owner.invulnerableTime = 0;
            float h0 = owner.getHealth();
            boolean control = owner.hurt(level.damageSources().playerAttack(other), 4F);
            float h1 = owner.getHealth();
            Probe controlProbe = Probe.read();
            System.setProperty(PROP_EVENTS, "");
            owner.invulnerableTime = 0;
            boolean discharge = owner.hurt(ECDamageTypes.mruDischarge(level), 4F);
            float h2 = owner.getHealth();
            owner.invulnerableTime = 0;
            owner.setHealth(owner.getMaxHealth());
            Probe probe = Probe.read();
            probe.expect(problems, "EntityDamageEvent", OWN, OWNER_NAME, "CraftPlayer", false);
            for (Map<String, String> event : controlProbe.all()) {
                if (event.get("type").startsWith("EntityDamage") && !"true".equals(event.get("cancelled"))) {
                    problems.add("对照组的伤害事件没被拦：" + event.get("line"));
                }
            }
            String actual = "对照 " + (h1 < h0 ? "掉血 " + fmt(h0 - h1) : "不掉") + "（hurt=" + control + "）；电弧 "
                    + (h2 < h1 ? "掉血 " + fmt(h1 - h2) : "不掉") + "（hurt=" + discharge + "）；插件 " + probe.summary();
            boolean pass = h1 == h0 && h2 < h1;
            return new Outcome(pass, "对照不掉血，电弧掉血，插件 EntityDamageEvent 未取消", actual, problems);
        }

        /**
         * C13：击杀不掉落。三只僵尸都先被主人打一下（playerAttack，lastHurtByPlayerTime = 100；setLastHurtByPlayer 按 tickCount 记、新生成的怪是 0，
         * 算不上「被玩家打过」），再清掉受击无敌：
         * Z1 手拿铁剑（掉落率 1.0，被玩家打过必掉）被 mruDischarge 打死 → 没有掉落物、没有经验；
         * Z2 同样装备被 generic 打死（对照）→ 掉铁剑、有经验；
         * Z3 捡了钻石剑（setGuaranteedDrop，掉落率 2.0）被 mruDischarge 打死 → 只掉钻石剑、没有经验；
         * 第七批：Z4 被恶魔的 demon 伤害打死（恶魔用 {@code ECDemon.DEMON.create}，不放进世界）、Z5 被主人的 soul_capture 伤害打死 → 都没有掉落物、没有经验。
         * 插件：五条 EntityDeathEvent（Z1 的 getKiller 仍是主人，§4.13 已知漏洞，记录）。
         */
        private Outcome c13() {
            BlockPos base = slot(WILD, 6);
            List<String> problems = new ArrayList<>();
            String z1 = killZombie(base.offset(0, 1, 0), Items.IRON_SWORD, false, ECDamageTypes.mruDischarge(level), problems);
            String z2 = killZombie(base.offset(2, 1, 0), Items.IRON_SWORD, false, level.damageSources().generic(), problems);
            String z3 = killZombie(base.offset(1, 1, 2), Items.DIAMOND_SWORD, true, ECDamageTypes.mruDischarge(level), problems);
            DemonEntity demon = ECDemon.DEMON.get().create(level);
            String z4;
            if (demon == null) {
                problems.add("造不出恶魔");
                z4 = "?";
            } else {
                demon.moveTo(base.getX() + 0.5D, base.getY() + 1D, base.getZ() + 2.5D, 0F, 0F);
                z4 = killZombie(base.offset(0, 1, 2), Items.IRON_SWORD, false, ECDamageTypes.demon(demon), problems);
            }
            String z5 = killZombie(base.offset(2, 1, 2), Items.IRON_SWORD, false, ECDamageTypes.soulCapture(owner), problems);
            Probe probe = Probe.read();
            List<String> killers = new ArrayList<>();
            for (Map<String, String> event : probe.all()) {
                if ("EntityDeathEvent".equals(event.get("type"))) {
                    killers.add(event.getOrDefault("killer", "?"));
                }
            }
            if (killers.size() != 5) {
                problems.add("插件应收到 5 条 EntityDeathEvent，实际 " + killers.size());
            }
            String actual = "Z1(电弧) " + z1 + "；Z2(generic) " + z2 + "；Z3(电弧+必掉钻石剑) " + z3 + "；Z4(恶魔) " + z4 + "；Z5(收魂) " + z5
                    + "；插件 getKiller " + killers;
            boolean pass = z1.equals("掉落[] 经验0") && z2.contains("iron_sword") && !z2.endsWith("经验0")
                    && z3.equals("掉落[1 diamond_sword] 经验0") && z4.equals("掉落[] 经验0") && z5.equals("掉落[] 经验0");
            return new Outcome(pass, "Z1 无掉落无经验；Z2 有铁剑有经验；Z3 只掉钻石剑无经验；Z4 恶魔、Z5 收魂 无掉落无经验", actual, problems);
        }

        private String killZombie(BlockPos at, Item weapon, boolean guaranteed, net.minecraft.world.damagesource.DamageSource kill, List<String> problems) {
            set(at.below(), Blocks.STONE.defaultBlockState());
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) {
                problems.add("造不出僵尸");
                return "?";
            }
            zombie.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            level.addFreshEntity(zombie);
            // 装备在加入世界之后设（别的模组在加入世界时可能给怪加东西）；其余格子清空
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            if (guaranteed) {
                zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            } else {
                zombie.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            }
            zombie.hurt(level.damageSources().playerAttack(owner), 1F);
            if (zombie.getLastHurtByMob() != owner) {
                problems.add(at.toShortString() + " 的僵尸没记下被主人打过（lastHurtByMob = " + zombie.getLastHurtByMob() + "）");
            }
            zombie.invulnerableTime = 0;
            List<String> items = new ArrayList<>();
            int xp = 0;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                zombie.hurt(kill, 1000F);
                for (Entity entity : spawns.alive()) {
                    if (entity instanceof ItemEntity item) {
                        ItemStack stack = item.getItem();
                        items.add(stack.getCount() + " " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath());
                    } else if (entity instanceof ExperienceOrb orb) {
                        xp += orb.getValue();
                    }
                }
                spawns.discardAll();
            }
            if (zombie.isAlive()) {
                problems.add(at.toShortString() + " 的僵尸没死（别的模组给了免死？）");
                zombie.discard();
            }
            items.sort(null);
            return "掉落" + items + " 经验" + xp;
        }

        /**
         * C14：腐化 #10（{@code CorruptionBehaviors.useMainHand}，造真的使用物品包交给 {@code connection.handleUseItem}）。主人站在野外（区间空隙），
         * 看着别人领地边上的一格草方块、手拿骨粉 → 插件收到 {@code PlayerInteractEvent RIGHT_CLICK_BLOCK} 并取消，骨粉不耗、那格不变。
         * 对照：同样站法看着主人自己的领地 → 事件不取消。
         * <p>
         * 混合端的 {@code handleUseItem} 开头查 {@code isImmobile()}，而 {@code ServerPlayer.isImmobile} 在混合端上还要求 {@code getBukkitEntity().isOnline()}
         * （= 玩家列表的 {@code playersByUUID} 里有他）。脱离网络的测试玩家不在里面，会在第一行就返回、什么事件都不发（测了等于没测），
         * 所以这一组临时把主人登记进 {@code playersByUUID}（反射，SRG 名 {@value #PLAYERS_BY_UUID}），测完删掉。真玩家本来就在里面，这是还原实际情况。
         */
        private Outcome c14() {
            List<String> problems = new ArrayList<>();
            String own = useMainHandToward(OWN, problems);
            Probe controlProbe = Probe.read();
            System.setProperty(PROP_EVENTS, "");
            String others = useMainHandToward(OTHERS, problems);
            Probe probe = Probe.read();
            Map<String, String> control = controlProbe.first("PlayerInteractEvent");
            Map<String, String> denied = probe.first("PlayerInteractEvent");
            if (control == null) {
                problems.add("对照（看着自己领地）没有收到 PlayerInteractEvent：useMainHand 没走到 Bukkit 事件那一步");
            } else if ("DENY".equals(control.get("useItem"))) {
                problems.add("对照（看着自己领地）的 PlayerInteractEvent 被拒了：" + control.get("line"));
            }
            if (denied == null) {
                problems.add("看着别人领地时没有收到 PlayerInteractEvent");
            } else {
                if (!"RIGHT_CLICK_BLOCK".equals(denied.get("action")) || !"other".equals(denied.get("region"))) {
                    problems.add("PlayerInteractEvent 应是 RIGHT_CLICK_BLOCK、落在别人领地：" + denied.get("line"));
                }
                if (!"DENY".equals(denied.get("useItem"))) {
                    problems.add("看着别人领地时 PlayerInteractEvent 没被取消：" + denied.get("line"));
                }
            }
            String actual = "自己领地 " + own + "；别人领地 " + others + "；插件 " + (denied == null ? "无" : denied.get("line"));
            boolean pass = others.equals("骨粉 3 那格不变");
            return new Outcome(pass, "别人领地：骨粉 3 那格不变，PlayerInteractEvent RIGHT_CLICK_BLOCK 被取消", actual, problems);
        }

        /** 主人站在 zone 西边的空隙里，看着 zone 最西一列的一格草方块，手拿 3 个骨粉，发作一次 #10 */
        private String useMainHandToward(int zone, List<String> problems) {
            BlockPos target = zoneBase(zone).offset(0, 1, 5);
            set(target, Blocks.GRASS_BLOCK.defaultBlockState());
            BlockState before = level.getBlockState(target);
            BlockState aboveBefore = level.getBlockState(target.above());
            double feetX = target.getX() - 2.5D;
            double eyeY = target.getY() + owner.getEyeHeight(owner.getPose());
            // 瞄准草方块西面中心：向东（偏航 -90），往下看
            float pitch = (float) Math.toDegrees(Math.atan2(eyeY - (target.getY() + 0.5D), 2.5D));
            owner.moveTo(feetX, target.getY(), target.getZ() + 0.5D, -90F, pitch);
            owner.setYHeadRot(-90F);
            owner.getInventory().selected = 0;
            owner.getInventory().items.set(0, new ItemStack(Items.BONE_MEAL, 3));
            Map<UUID, ServerPlayer> byUuid = ObfuscationReflectionHelper.getPrivateValue(PlayerList.class, server.getPlayerList(), PLAYERS_BY_UUID);
            boolean registered = byUuid != null && byUuid.putIfAbsent(OWNER_ID, owner) == null;
            try {
                Class<?> behaviors = Class.forName("com.flameforge.essentialcraft5.corruption.CorruptionBehaviors");
                Method useMainHand = behaviors.getDeclaredMethod("useMainHand", ServerPlayer.class);
                useMainHand.setAccessible(true);
                useMainHand.invoke(null, owner);
            } catch (ReflectiveOperationException e) {
                problems.add("调不到 CorruptionBehaviors.useMainHand：" + e);
            } finally {
                if (registered) {
                    byUuid.remove(OWNER_ID, owner);
                }
            }
            if (byUuid == null) {
                problems.add("反射拿不到 PlayerList.playersByUUID");
            }
            ItemStack left = owner.getInventory().items.get(0);
            owner.getInventory().items.set(0, ItemStack.EMPTY);
            boolean unchanged = level.getBlockState(target) == before && level.getBlockState(target.above()) == aboveBefore;
            return "骨粉 " + (left.is(Items.BONE_MEAL) ? left.getCount() : 0) + (unchanged ? " 那格不变" : " 那格变了");
        }

        /**
         * C15：销毁拦截区（插件放行 BlockBreakEvent、取消 Mohist 的 ModsEntityDestroyBlockEvent）：breakGuarded 返回 false、harvestGuarded 返回 null，
         * 方块还在、没有掉落物（先删后掉的修复：旧写法先掉落再删，删被取消 = 白送一份）。对照：野外 harvestGuarded 拿到圆石、方块没了。
         */
        private Outcome c15() {
            BlockPos a = slot(DESTROY, 7);
            BlockPos b = slot(DESTROY, 7).offset(2, 0, 0);
            BlockPos wild = slot(WILD, 7);
            set(a, Blocks.STONE.defaultBlockState());
            set(b, Blocks.STONE.defaultBlockState());
            set(wild, Blocks.STONE.defaultBlockState());
            List<String> problems = new ArrayList<>();
            boolean broken;
            List<ItemStack> harvested;
            List<ItemStack> control;
            int drops;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                broken = ECProtection.breakGuarded(owner, level, a, ItemStack.EMPTY, true);
                harvested = ECProtection.harvestGuarded(owner, level, b, new ItemStack(Items.DIAMOND_PICKAXE));
                drops = countItems(spawns.alive(), null);
                spawns.discardAll();
            }
            Probe probe = Probe.read();
            System.setProperty(PROP_EVENTS, "");
            control = ECProtection.harvestGuarded(owner, level, wild, new ItemStack(Items.DIAMOND_PICKAXE));
            boolean stay = level.getBlockState(a).is(Blocks.STONE) && level.getBlockState(b).is(Blocks.STONE);
            probe.expectAll(problems, "BlockBreakEvent", DESTROY, OWNER_NAME, "CraftPlayer", false, 2);
            probe.expectAll(problems, "ModsEntityDestroyBlockEvent", DESTROY, OWNER_NAME, "CraftPlayer", true, 2);
            if (control == null || control.size() != 1 || !control.get(0).is(Items.COBBLESTONE) || !level.getBlockState(wild).isAir()) {
                problems.add("对照（野外 harvestGuarded）应拿到 1 个圆石、方块没了，实际 " + control + " / " + level.getBlockState(wild));
            }
            String actual = "breakGuarded=" + broken + " harvestGuarded=" + harvested + " 方块" + (stay ? "还在" : "没了") + " 掉落物 " + drops
                    + "；对照 " + control;
            boolean pass = !broken && harvested == null && stay && drops == 0;
            return new Outcome(pass, "breakGuarded=false harvestGuarded=null 方块还在 掉落物 0", actual, problems);
        }

        /**
         * C17：只拒放置区（探测放行、真改被拒，policy §2.1 ⑨）里玩家本人的 placeGuarded 不复制：
         * 先 canEdit 两格 → 允许（BlockBreakEvent 放行）；把装着唱片的唱片机换成石头 → placeGuarded 自己就不许（换掉带方块实体的另一种方块，被拒还原 = 复制），不发事件；
         * 唱片机同方块改状态（HAS_RECORD 关）、雕纹书架同方块改状态（0 号格空）→ 主人的 BlockPlaceEvent 被取消、按快照还原。
         * 结果：两处的方块状态和里面的东西都和原来一样，地上没有掉落物（唱片 1 张、书 1 本，总数守恒）。
         */
        private Outcome c17() {
            BlockPos jukeboxPos = slot(PLACE_ONLY, 8).offset(0, 0, 1);
            BlockPos shelfPos = slot(PLACE_ONLY, 8).offset(2, 0, 1);
            set(jukeboxPos, Blocks.JUKEBOX.defaultBlockState());
            set(shelfPos, Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            List<String> problems = new ArrayList<>();
            if (!(level.getBlockEntity(jukeboxPos) instanceof JukeboxBlockEntity jukebox)
                    || !(level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity shelf)) {
                return new Outcome(false, "唱片机、雕纹书架有方块实体", "没有", List.of());
            }
            jukebox.setFirstItem(new ItemStack(Items.MUSIC_DISC_13));
            shelf.setItem(0, new ItemStack(Items.BOOK));
            BlockState jukeboxBefore = level.getBlockState(jukeboxPos);
            BlockState shelfBefore = level.getBlockState(shelfPos);
            String probeJukebox = name(ECProtection.check(owner, level, jukeboxPos, owner));
            String probeShelf = name(ECProtection.check(owner, level, shelfPos, owner));
            boolean replaced;
            boolean toggledJukebox;
            boolean toggledShelf;
            int drops;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                replaced = ECProtection.placeGuarded(owner, level, jukeboxPos, Blocks.STONE.defaultBlockState(), Direction.UP);
                toggledJukebox = ECProtection.placeGuarded(owner, level, jukeboxPos, jukeboxBefore.setValue(JukeboxBlock.HAS_RECORD, false), Direction.UP);
                toggledShelf = ECProtection.placeGuarded(owner, level, shelfPos,
                        shelfBefore.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(0), false), Direction.UP);
                drops = countItems(spawns.alive(), null);
                spawns.discardAll();
            }
            JukeboxBlockEntity jukeboxAfter = level.getBlockEntity(jukeboxPos) instanceof JukeboxBlockEntity be ? be : null;
            ChiseledBookShelfBlockEntity shelfAfter = level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity be ? be : null;
            int records = jukeboxAfter != null && jukeboxAfter.getFirstItem().is(Items.MUSIC_DISC_13) ? 1 : 0;
            int books = shelfAfter != null ? shelfAfter.count() : 0;
            boolean statesSame = level.getBlockState(jukeboxPos) == jukeboxBefore && level.getBlockState(shelfPos) == shelfBefore;
            Probe probe = Probe.read();
            probe.expectAll(problems, "BlockBreakEvent", PLACE_ONLY, OWNER_NAME, "CraftPlayer", false, 2);
            probe.expectAll(problems, "BlockPlaceEvent", PLACE_ONLY, OWNER_NAME, "CraftPlayer", true, 2);
            String actual = "探测 " + probeJukebox + "/" + probeShelf + "；换石头=" + replaced + " 唱片机改状态=" + toggledJukebox + " 书架改状态=" + toggledShelf
                    + "；状态" + (statesSame ? "不变" : "变了") + " 唱片 " + records + " 书 " + books + " 掉落物 " + drops;
            boolean pass = probeJukebox.equals("允许") && probeShelf.equals("允许") && !replaced && !toggledJukebox && !toggledShelf
                    && statesSame && records == 1 && books == 1 && drops == 0;
            return new Outcome(pass, "探测 允许/允许；三次 placeGuarded 都 false；状态不变 唱片 1 书 1 掉落物 0", actual, problems);
        }

        /**
         * C18：射线打玩家（GUNS-4「打中就加矩阵损伤」在混合端上的判定，{@code gun/RayHitGate}）。另一名玩家用魔力锭手枪（13.3 伤害）打主人：
         * <ol>
         *   <li>主人站在 owner 区（插件当成关了 PvP 的领地：在 LivingHurtEvent 与 LivingDamageEvent 之间取消 {@code EntityDamageByEntityEvent}）→ 不掉血、不加矩阵、没有特效；</li>
         *   <li>主人站在野外 → 掉血、+1333、有特效；马上再打一下（无敌帧，插件这一下没被问到）→ 按「刚被放行过」照加 +1333；</li>
         *   <li>回 owner 区、打玩家倍率 0（只损伤矩阵）→ 仍被插件拦下，不加。</li>
         * </ol>
         * 服务端 PvP 是关的（C12 要），这组临时用反射把主世界的 {@code pvpMode}（混合端给 Level 加的字段，{@code ServerPlayer.canHarmPlayer} 读它）打开，跑完放回；
         * 找不到这个字段就只记录、不判。主人的矩阵损伤跑完放回原值。
         */
        private Outcome c18() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            if (!RayHitGate.isBukkitHybrid()) {
                problems.add("没认出混合端（服务端类加载器里找不到 org.bukkit.Bukkit），RayHitGate 按纯 Forge 判");
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            GunStats stats = GunStats.of(GunType.PISTOL, GunParts.uniform("mru"));
            List<String> fx = new ArrayList<>();
            RayShot.MatrixFxSink sink = (target, intensity) -> fx.add(target.getScoreboardName());
            int original = data.getMatrixDamage();
            BlockPos inClaim = slot(OWN, 6).offset(1, 1, 1);
            BlockPos wild = slot(WILD, 6).offset(1, 1, 1);
            RayShot.Hit denied;
            RayShot.Hit open;
            RayShot.Hit again;
            RayShot.Hit zero;
            int afterDenied;
            int afterWild;
            int afterZero;
            Probe deniedProbe;
            boolean pvpBefore = false;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 1D, null);
                data.setMatrixDamage(0);
                standAt(owner, inClaim);
                denied = RayShot.strike(other, owner, stats, sink);
                afterDenied = data.getMatrixDamage();
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, wild);
                open = RayShot.strike(other, owner, stats, sink);
                again = RayShot.strike(other, owner, stats, sink);
                afterWild = data.getMatrixDamage();
                standAt(owner, inClaim);
                GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 0D, null);
                zero = RayShot.strike(other, owner, stats, sink);
                afterZero = data.getMatrixDamage();
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                GunConfig.setTestOverrides(null, null, null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                data.setMatrixDamage(original);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            String actual = "领地 hurt=" + denied.damaged() + " 矩阵 " + afterDenied + "；野外 hurt=" + open.damaged() + "/" + again.damaged()
                    + " 矩阵 " + afterWild + "；领地倍率 0 矩阵 " + afterZero + "；特效 " + fx.size() + " 次；插件 " + deniedProbe.summary();
            boolean pass = !denied.matrix() && afterDenied == 0
                    && open.damaged() && open.matrix() && !again.damaged() && again.matrix() && afterWild == 2666
                    && !zero.matrix() && afterZero == 2666 && fx.size() == 2;
            return new Outcome(pass, "领地 不加（插件 EntityDamageByEntityEvent 已取消）；野外 +1333、无敌帧再 +1333（共 2666）；领地倍率 0 不加；特效 2 次",
                    actual, problems);
        }

        // ------------------------------------------------------------------ 第六批 6a M2（海纳尔之门）

        /**
         * C19：风之石板换门被领地拒绝（6a M2；审查提的「只看 {@code BlockMultiPlaceEvent.getBlock()} 第一格的领地插件」这条路）。
         * 在别人领地（other 区）东边界上、z = 9 那一排摆一扇 X 朝向 2 × 3 的下界门：内框西边一列（x = 49）在区里，东边一列（x = 50）在区外空隙。
         * 石板按 {@code HoannaPortalShape.cells()} 的顺序改格子（从左下角起，X 朝向的左下角是东边那列），混合端把第一个快照当 Bukkit 事件的
         * {@code getBlock()}——第一格在区外，只看第一格的插件会放行；测试插件逐格查，应当拒。
         * 期望：整扇还是下界门、没有空气、石板 MRU 不变、返回 FAIL；插件正好收到一次 6 格的 BlockMultiPlaceEvent，已取消，第一格 region=none。
         * 对照：owner 区和 other 区之间的空隙里（x 32~35、z = 7）整扇都在区外的门 → 吃掉右键、6 格都是 stable 的海纳尔之门、扣一次 MRU、事件没被取消。
         * 主人的风之好感临时调到够、跑完放回；两扇门跑完拆掉（下界门的随机刻会刷僵尸猪灵，别留给后面的组）。
         */
        private Outcome c19() {
            List<String> problems = new ArrayList<>();
            if (ECHoanna.level(server) == null) {
                return new Outcome(null, "有海纳尔世界", "没有（维度被数据包删了？），石板不换门，跳过", problems);
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            BlockPos straddle = zoneBase(OTHERS).offset(ZONE_SIZE - 1, 1, ZONE_SIZE - 1);   // 内框西边一列最下面（x = 49，区里）
            BlockPos gap = zoneBase(OWN).offset(ZONE_SIZE + 3, 1, ZONE_SIZE - 3);          // x = 33、z = 7：两块领地之间的空隙
            List<BlockPos> straddleCells = buildNetherGate(straddle);
            List<BlockPos> gapCells = buildNetherGate(gap);
            BlockPos firstCell = straddle.east();
            int cost = ECWind.Config.tabletPortalMRU();
            int windBefore = data.getWindPoints();
            int wishesBefore = data.getWindWishes();
            InteractionResult denied;
            InteractionResult opened;
            int mruDenied;
            int mruOpened;
            Probe deniedProbe;
            Probe openedProbe;
            try {
                data.setWindPoints(Math.max(ECWind.Config.tabletPoints(), 1));
                data.setWindWishes(WindWishes.required());
                ItemStack first = fullTablet();
                denied = useTablet(first, straddle, Direction.NORTH);
                mruDenied = tabletMru(first);
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                ItemStack second = fullTablet();
                opened = useTablet(second, gap, Direction.NORTH);
                mruOpened = tabletMru(second);
                openedProbe = Probe.read();
            } finally {
                data.setWindPoints(windBefore);
                data.setWindWishes(wishesBefore);
                owner.getInventory().items.set(0, ItemStack.EMPTY);
            }
            int deniedNether = countCells(straddleCells, state -> state.is(Blocks.NETHER_PORTAL));
            int deniedAir = countCells(straddleCells, BlockState::isAir);
            int openedHoanna = countCells(gapCells, state -> state.getBlock() instanceof HoannaPortalBlock && state.getValue(HoannaPortalBlock.STABLE));
            clearGate(straddle);
            clearGate(gap);

            Map<String, String> deniedEvent = deniedProbe.first("BlockMultiPlaceEvent");
            String firstAt = firstCell.getX() + "," + firstCell.getY() + "," + firstCell.getZ();
            String claimedAt = straddle.getX() + "," + straddle.getY() + "," + straddle.getZ();
            if (deniedProbe.countType("BlockMultiPlaceEvent") != 1 || deniedEvent == null) {
                problems.add("被拒的那次插件应正好收到一次 BlockMultiPlaceEvent：" + deniedProbe.summary());
            } else {
                if (!"true".equals(deniedEvent.get("cancelled")) || !"6".equals(deniedEvent.get("blocks"))) {
                    problems.add("被拒的那次应是 6 格、已取消：" + deniedEvent.get("line"));
                }
                if (!OWNER_NAME.equals(deniedEvent.get("playerName")) || !"CraftPlayer".equals(deniedEvent.get("playerClass"))) {
                    problems.add("事件里的玩家应是主人（CraftPlayer）：" + deniedEvent.get("line"));
                }
                if (!firstAt.equals(deniedEvent.get("at")) || !"none".equals(deniedEvent.get("region"))) {
                    problems.add("第一格应是区外的 " + firstAt + "（region=none），实际 at=" + deniedEvent.get("at") + " region=" + deniedEvent.get("region")
                            + "：用例没摆出「第一格在区外」的情形");
                }
                if (!List.of(deniedEvent.getOrDefault("cells", "").split(";")).contains(claimedAt)) {
                    problems.add("多格事件的格子里应有区里那格 " + claimedAt + "：" + deniedEvent.get("cells"));
                }
            }
            Map<String, String> openedEvent = openedProbe.first("BlockMultiPlaceEvent");
            if (openedProbe.countType("BlockMultiPlaceEvent") != 1 || openedEvent == null
                    || !"false".equals(openedEvent.get("cancelled")) || !"6".equals(openedEvent.get("blocks"))) {
                problems.add("对照那次插件应正好收到一次 6 格、没取消的 BlockMultiPlaceEvent：" + openedProbe.summary());
            }
            String actual = "跨界门 " + denied + " 下界门 " + deniedNether + "/6 空气 " + deniedAir + " MRU " + mruDenied
                    + "；空隙门 " + opened + " 海纳尔之门 " + openedHoanna + "/6 MRU " + mruOpened
                    + "；插件 " + (deniedEvent == null ? "无" : deniedEvent.get("line"));
            boolean pass = denied == InteractionResult.FAIL && deniedNether == 6 && deniedAir == 0 && mruDenied == WindTabletItem.MAX_MRU
                    && opened.consumesAction() && openedHoanna == 6 && mruOpened == WindTabletItem.MAX_MRU - cost;
            return new Outcome(pass, "跨界门 FAIL 下界门 6/6 空气 0 MRU " + WindTabletItem.MAX_MRU + "；空隙门 CONSUME 海纳尔之门 6/6 MRU "
                    + (WindTabletItem.MAX_MRU - cost) + "；插件拒了第一格在区外的 6 格事件", actual, problems);
        }

        /**
         * C20：海纳尔侧建门的 Bukkit 放置事件报哪个世界（6a M2，batch6a §5.2 的待核实项；审查读混合端代码的结论是「快照所在的世界 = 海纳尔」，这里实测）。
         * 过门建落脚门时玩家还在主世界，{@code HoannaPortalPlacer.build} 在海纳尔 captureGuarded、以玩家身份发一次多格放置事件。
         * 在海纳尔、和主世界 other 区（别人领地）同坐标处铺一块 8 × 8 的石头地板（那一片原本要是空气，不是就跳过），主人以自己的身份建门：
         * <ul>
         *   <li>报的是海纳尔 → 测试插件只判主世界 → 建在第一个候选点（地板上）；插件记下正好一次 BlockMultiPlaceEvent，primaryWorld=false、没取消；</li>
         *   <li>报成主世界 → 插件按同坐标的 other 区拒掉地板上的候选点，门建到地板下面很远的地形上（或全拒），事件 primaryWorld=true、已取消。</li>
         * </ul>
         * 要新版测试插件（6a 起记别的世界里同坐标附近的事件，region=otherworld）；旧版什么都不记，这组会报「插件没收到」。
         * 跑完把地板和所有候选点会动到的格子按原样还原。
         */
        private Outcome c20() {
            List<String> problems = new ArrayList<>();
            ServerLevel hoanna = ECHoanna.level(server);
            if (hoanna == null) {
                return new Outcome(null, "有海纳尔世界", "没有（维度被数据包删了？），跳过", problems);
            }
            BlockPos zone = zoneBase(OTHERS);
            for (BlockPos pos : BlockPos.betweenClosed(zone.offset(-2, 0, -2), zone.offset(ZONE_SIZE + 1, 5, ZONE_SIZE + 1))) {
                if (!hoanna.getBlockState(pos).isAir()) {
                    return new Outcome(null, "海纳尔那一片是空气", pos.toShortString() + " 是 " + hoanna.getBlockState(pos) + "（地形太高），跳过", problems);
                }
            }
            Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(zone.offset(1, 0, 1), zone.offset(8, 0, 8))) {
                saved.put(pos.immutable(), hoanna.getBlockState(pos));
                hoanna.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockPos target = zone.offset(4, 1, 4);
            int attempts = ECHoannaPortal.Config.buildAttempts();
            Optional<BlockUtil.FoundRectangle> built;
            HoannaPortalPlacer.Candidate first;
            Probe probe;
            try {
                List<HoannaPortalPlacer.Candidate> candidates = HoannaPortalPlacer.candidates(hoanna, target, Direction.Axis.X, attempts);
                for (HoannaPortalPlacer.Candidate candidate : candidates) {
                    for (BlockPos cell : HoannaPortalPlacer.footprint(candidate, Direction.Axis.X)) {
                        saved.putIfAbsent(cell.immutable(), hoanna.getBlockState(cell));
                    }
                }
                first = candidates.isEmpty() ? null : candidates.get(0);
                if (first == null || first.pos().getY() != target.getY()) {
                    return new Outcome(null, "第一个候选点在地板上", "候选点 " + (first == null ? "没有" : first.pos().toShortString()) + "，跳过", problems);
                }
                System.setProperty(PROP_EVENTS, "");
                built = HoannaPortalPlacer.build(owner, hoanna, target, Direction.Axis.X, attempts);
                probe = Probe.read();
            } finally {
                for (Map.Entry<BlockPos, BlockState> entry : saved.entrySet()) {
                    if (hoanna.getBlockState(entry.getKey()) != entry.getValue()) {
                        hoanna.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
            List<Map<String, String>> places = new ArrayList<>();
            for (Map<String, String> event : probe.all()) {
                if ("BlockMultiPlaceEvent".equals(event.get("type"))) {
                    places.add(event);
                }
            }
            boolean onFloor = built.isPresent() && built.get().minCorner.equals(first.pos());
            Map<String, String> event = places.isEmpty() ? null : places.get(0);
            if (event == null) {
                problems.add("插件没收到 BlockMultiPlaceEvent：测试插件是 6a 以前的旧版（不记别的世界，重打 probe），或混合端没把海纳尔的放置事件转成 Bukkit 事件");
            } else {
                for (Map<String, String> place : places) {
                    if ("true".equals(place.get("primaryWorld"))) {
                        problems.add("混合端把海纳尔侧建门的放置事件报成了主世界（同坐标的领地会管到海纳尔）：" + place.get("line"));
                    }
                }
                if (places.size() != 1 || !"false".equals(event.get("cancelled")) || !"otherworld".equals(event.get("region"))) {
                    problems.add("应正好一次、没取消、region=otherworld：" + probe.summary());
                }
                if (!OWNER_NAME.equals(event.get("playerName")) || !"CraftPlayer".equals(event.get("playerClass"))) {
                    problems.add("事件里的玩家应是主人（CraftPlayer）：" + event.get("line"));
                }
            }
            String actual = "建在 " + built.map(r -> r.minCorner.toShortString()).orElse("（全被拒）") + "（第一个候选点 " + first.pos().toShortString() + "）；插件 "
                    + (event == null ? "无" : "world=" + event.get("world") + " primaryWorld=" + event.get("primaryWorld") + " cancelled=" + event.get("cancelled")
                    + " blocks=" + event.get("blocks") + "，共 " + places.size() + " 条");
            boolean pass = onFloor && event != null && places.size() == 1 && "false".equals(event.get("primaryWorld")) && "false".equals(event.get("cancelled"));
            return new Outcome(pass, "建在地板上的第一个候选点；插件一次 BlockMultiPlaceEvent，world=海纳尔（primaryWorld=false）、没取消", actual, problems);
        }

        /** C19：以 west（内框西边一列最下面一格）摆 X 朝向的 4 × 5 黑曜石框、2 × 3 下界门（门格 flag 18，不连锁更新）；返回内框 6 格 */
        private List<BlockPos> buildNetherGate(BlockPos west) {
            List<BlockPos> inner = new ArrayList<>();
            for (int w = -1; w <= 2; w++) {
                for (int h = -1; h <= 3; h++) {
                    BlockPos pos = west.offset(w, h, 0);
                    if (w == -1 || w == 2 || h == -1 || h == 3) {
                        set(pos, Blocks.OBSIDIAN.defaultBlockState());
                    } else {
                        inner.add(pos);
                    }
                }
            }
            BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
            for (BlockPos pos : inner) {
                level.setBlock(pos, portal, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return inner;
        }

        /** C19：拆掉 {@link #buildNetherGate} 摆的框和门（不连锁更新；海纳尔之门的兴趣点跟着删） */
        private void clearGate(BlockPos west) {
            for (int w = -1; w <= 2; w++) {
                for (int h = -1; h <= 3; h++) {
                    level.setBlock(west.offset(w, h, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }

        private int countCells(List<BlockPos> cells, Predicate<BlockState> test) {
            int n = 0;
            for (BlockPos cell : cells) {
                if (test.test(level.getBlockState(cell))) {
                    n++;
                }
            }
            return n;
        }

        /** C19：满 MRU 的风之石板，放进主人主手（快捷栏第 0 格） */
        private ItemStack fullTablet() {
            ItemStack tablet = new ItemStack(ECWind.WIND_TABLET.get());
            tablet.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(WindTabletItem.MAX_MRU));
            owner.getInventory().selected = 0;
            owner.getInventory().items.set(0, tablet);
            return tablet;
        }

        /** C19：主人拿石板右键 pos 的 face 面（走 ItemStack.useOn → Forge 的 onPlaceItemIntoWorld，同真玩家点方块） */
        private InteractionResult useTablet(ItemStack tablet, BlockPos pos, Direction face) {
            return tablet.useOn(new UseOnContext(level, owner, InteractionHand.MAIN_HAND, tablet,
                    new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
        }

        private static int tabletMru(ItemStack tablet) {
            return tablet.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
        }

        // ------------------------------------------------------------------ 第六批 6a M4（海纳尔天灾 / 辐射）

        /**
         * C23：日阵灼烧（{@code HoannaHazards.sunArray}，不看天灾状态、直接结算一次）。主人站在野外高处（头顶看得见天空）：
         * 掉 1 点、着火；测试区里没有火方块、没有下落方块（原作日阵每人每秒在天上生成一个落地变火的方块，移植删了）；
         * 插件看到的是主人的 EntityDamageEvent 没被取消，没有 BlockIgniteEvent（着火走实体的 setSecondsOnFire，燃烧的实体不点方块）。
         */
        private Outcome c23() {
            List<String> problems = new ArrayList<>();
            standAt(owner, slot(WILD, 24).offset(1, 0, 1));
            owner.clearFire();
            if (!HoannaHazards.exposedToSky(level, owner)) {
                problems.add("测试点头顶看不见天空，日阵判定不成立");
            }
            float h0 = owner.getHealth();
            boolean hit = HoannaHazards.sunArray(owner, level);
            float h1 = owner.getHealth();
            int fireTicks = owner.getRemainingFireTicks();
            AABB box = areaBox();
            int fireBlocks = 0;
            for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                    Mth.floor(box.maxX) - 1, Mth.floor(box.maxY) - 1, Mth.floor(box.maxZ) - 1)) {
                if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                    fireBlocks++;
                }
            }
            int falling = level.getEntitiesOfClass(FallingBlockEntity.class, box.expandTowards(0D, 64D, 0D)).size();
            Probe probe = Probe.read();
            owner.clearFire();
            owner.invulnerableTime = 0;
            owner.setHealth(owner.getMaxHealth());
            probe.expect(problems, "EntityDamageEvent", WILD, OWNER_NAME, "CraftPlayer", false);
            if (probe.countType("BlockIgniteEvent") > 0) {
                problems.add("插件收到了 BlockIgniteEvent：" + probe.summary());
            }
            String actual = "hurt=" + hit + " 掉血 " + fmt(h0 - h1) + " 着火 " + fireTicks + " tick；火方块 " + fireBlocks + "、下落方块 " + falling
                    + "；插件 " + probe.summary();
            boolean pass = hit && h1 < h0 && fireTicks > 0 && fireBlocks == 0 && falling == 0;
            return new Outcome(pass, "掉血、着火，没有火方块和下落方块；插件 EntityDamageEvent 未取消、没有 BlockIgniteEvent", actual, problems);
        }

        /**
         * C24：地震推人（{@code HoannaHazards.earthquake}，强震）。主人站在野外、当成落地：服务端给一次速度（{@code push} + {@code hurtMarked}，
         * 由实体追踪器下发速度包），水平 ≤ {@value HoannaHazards#STRONG_MAX}、竖直 ≤ {@value HoannaHazards#STRONG_UP_MAX}；
         * 单刻位移的平方远低于 Spigot / 混合端的「移动过快」门槛（{@code max(100, (10 × 包数 × 走速 × 10)²)}，走速 0.1 时是 100）。
         * 脱离网络的玩家没有客户端回包，门槛本身判不到：<b>反作弊插件和真客户端的判定要实机看</b>。推人配置在这一组里临时当开着。
         */
        private Outcome c24() {
            List<String> problems = new ArrayList<>();
            standAt(owner, slot(WILD, 25).offset(1, 0, 1));
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = false;
            owner.setOnGround(true);
            RandomSource random = strongQuakeRandom();
            HoannaHazards.Quake quake;
            Vec3 motion;
            boolean marked;
            HoannaHazardsConfig.setTestEarthquakePush(true);
            try {
                quake = HoannaHazards.earthquake(owner, random);
                motion = owner.getDeltaMovement();
                marked = owner.hurtMarked;
            } finally {
                HoannaHazardsConfig.setTestEarthquakePush(null);
                owner.setDeltaMovement(Vec3.ZERO);
                owner.hurtMarked = false;
                owner.setOnGround(false);
            }
            if (quake == null) {
                return new Outcome(false, "推得动（站在地上、生存）", "没推（canBeShaken 为 false）", problems);
            }
            double moved = motion.lengthSqr();
            boolean matches = motion.distanceToSqr(new Vec3(quake.x(), quake.y(), quake.z())) < 1.0E-9D;
            String actual = (quake.strong() ? "强震" : "小踉跄") + " 水平 " + String.format("%.2f", quake.horizontal()) + " 竖直 " + String.format("%.2f", quake.y())
                    + "；速度" + (matches ? "等于冲量" : "不等于冲量：" + motion) + "，下发标记 " + marked + "；单刻位移² " + String.format("%.3f", moved) + "（门槛 100）";
            boolean pass = quake.strong() && matches && marked && quake.horizontal() <= HoannaHazards.STRONG_MAX + 1.0E-9D
                    && quake.y() <= HoannaHazards.STRONG_UP_MAX + 1.0E-9D && moved < 100D;
            return new Outcome(pass, "强震一次，速度 = 冲量、下发标记置上，水平 ≤ 0.6、竖直 ≤ 0.35，单刻位移² < 100", actual, problems);
        }

        /** 第一下 nextFloat 小于强震概率的随机源（种子乘大奇数打散，同 GameTest 的写法） */
        private RandomSource strongQuakeRandom() {
            for (long i = 1; i < 10_000L; i++) {
                long seed = i * 0x9E3779B97F4A7C15L;
                if (RandomSource.create(seed).nextFloat() < HoannaHazards.STRONG_CHANCE) {
                    return RandomSource.create(seed);
                }
            }
            throw new IllegalStateException("找不到强震的种子");
        }

        /**
         * C25：玩家数据跟着真的换维度走（6a 集成修的 {@code PlayerDataProvider}：Forge 在 remove(CHANGED_DIMENSION) 里作废 capability，
         * 这里确认混合端改过的 {@code ServerPlayer.changeDimension}（先发 Bukkit 的 PlayerTeleportEvent、remove 后 revive）之后也还拿得到）。
         * 单独建一名旅行者，剂量设 12345，用海纳尔之门的传送器进海纳尔（y 200）、再回主世界；每次换完都要是同一份数据、剂量不变。
         * 换完把旅行者从所在世界移除（它进过两个世界的玩家表）。changeDimension 返回 null（传送事件被取消、混合端不传送脱离网络的玩家）时只记录。
         */
        private Outcome c25() {
            List<String> problems = new ArrayList<>();
            ServerLevel hoanna = ECHoanna.level(server);
            if (hoanna == null) {
                return new Outcome(null, "有海纳尔世界", "没有（维度被数据包删了？），跳过", problems);
            }
            ServerPlayer traveller = realPlayer(TRAVELLER_ID, TRAVELLER_NAME);
            IPlayerData before = PlayerDataEvents.get(traveller);
            if (before == null) {
                return new Outcome(false, "旅行者有玩家数据", "没有", problems);
            }
            before.setRadiation(12_345);
            Vec3 home = traveller.position();
            String there;
            String back;
            try {
                Entity moved = traveller.changeDimension(hoanna, new HoannaTeleporter(new PortalInfo(new Vec3(home.x, 200D, home.z), Vec3.ZERO, 0F, 0F)));
                if (moved == null) {
                    return new Outcome(null, "换到海纳尔",
                            "changeDimension 返回 null（传送事件被取消，或混合端不传送脱离网络的玩家），只记录；上线前用真玩家换一次世界确认", problems);
                }
                there = dataState(traveller, hoanna, before);
                Entity returned = traveller.changeDimension(level, new HoannaTeleporter(new PortalInfo(home, Vec3.ZERO, 0F, 0F)));
                back = returned == null ? "回程 changeDimension 返回 null" : dataState(traveller, level, before);
            } finally {
                traveller.serverLevel().removePlayerImmediately(traveller, Entity.RemovalReason.DISCARDED);
            }
            if (hoanna.players().contains(traveller) || level.players().contains(traveller)) {
                problems.add("旅行者还留在世界的玩家表里");
            }
            boolean pass = "同一份、剂量 12345".equals(there) && "同一份、剂量 12345".equals(back);
            return new Outcome(pass, "海纳尔 同一份、剂量 12345 / 主世界 同一份、剂量 12345", "海纳尔 " + there + " / 主世界 " + back, problems);
        }

        /** C25：换完以后在不在预期的世界、玩家数据是不是同一份、剂量对不对 */
        private String dataState(ServerPlayer player, ServerLevel expected, IPlayerData before) {
            if (player.level() != expected) {
                return "不在 " + expected.dimension().location() + "（在 " + player.level().dimension().location() + "）";
            }
            IPlayerData now = PlayerDataEvents.get(player);
            if (now == null) {
                return "拿不到玩家数据";
            }
            if (now != before) {
                return "不是同一份数据（剂量 " + now.getRadiation() + "）";
            }
            return now.getRadiation() == 12_345 ? "同一份、剂量 12345" : "同一份、剂量 " + now.getRadiation();
        }

        /**
         * C26：ξ 腰带打玩家（{@code RadiationBaubleEvents}：LivingHurtEvent 记账、LivingDamageEvent 才生效；混合端把 Bukkit 的
         * EntityDamageByEntityEvent 发在两者之间）。另一名玩家戴 ξ、空手打主人 4 点：
         * 主人在 owner 区 → 插件拦下 PvP、不掉血、不加辐射；主人在野外 → 掉血、+{@code targetDose(4)}：每下封顶 {@code general.bauble_limits.xi_dose_per_hit}
         * （默认 5000），且不超过 {@code xi_dose_ceiling}（默认 15000；主人起始剂量 0，这一下推不到上限）。
         * 同 C18 用反射临时打开主世界的 {@code pvpMode}（找不到就只记录）；辐射配置这一组里临时当开着；跑完摘下腰带、剂量放回原值。
         */
        private Outcome c26() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            if (!setBauble(other, ECBaubles.XI_BELT, true)) {
                return new Outcome(false, "给另一名玩家戴上 ξ 腰带", "没有对应的 Curios 槽位", problems);
            }
            int original = data.getRadiation();
            boolean pvpBefore = false;
            float deniedHealth;
            int afterDenied;
            boolean openHurt;
            float openLoss;
            int afterWild;
            Probe deniedProbe;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                HoannaHazardsConfig.setTestRadiationEnabled(true);
                data.setRadiation(0);
                standAt(owner, slot(OWN, 25).offset(1, 1, 1));
                owner.hurt(level.damageSources().playerAttack(other), 4F);
                deniedHealth = owner.getHealth();
                afterDenied = data.getRadiation();
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, slot(WILD, 26).offset(1, 1, 1));
                float healthBefore = owner.getHealth();
                openHurt = owner.hurt(level.damageSources().playerAttack(other), 4F);
                openLoss = healthBefore - owner.getHealth();
                afterWild = data.getRadiation();
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                HoannaHazardsConfig.setTestRadiationEnabled(null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                setBauble(other, ECBaubles.XI_BELT, false);
                data.setRadiation(original);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            String actual = "领地 生命 " + fmt(deniedHealth) + " 剂量 " + afterDenied + "；野外 hurt=" + openHurt + " 掉血 " + fmt(openLoss)
                    + " 剂量 " + afterWild + "；插件 " + deniedProbe.summary();
            boolean pass = deniedHealth == owner.getMaxHealth() && afterDenied == 0 && openHurt && openLoss > 0F
                    && afterWild == RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling());
            return new Outcome(pass, "领地 不掉血、剂量 0（插件 EntityDamageByEntityEvent 已取消）；野外 掉血、剂量 "
                    + RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling())
                    + "（4 点 × 5000，每下封顶 xi_dose_per_hit、总量封顶 xi_dose_ceiling）", actual, problems);
        }

        /**
         * C27：创世神之杖的「全部替换」落在只拒放置区（6b M1，batch6b §8）。主人主手拿满 MRU 的全部替换魔杖，两个点都框在只拒放置区里的一格石头上、
         * 选定橡木木板，背包里放 4 块木板，人站在旁边的区间空隙里；开工后直接调 {@code BuilderJobs} 的服务端 tick 处理器推进到做完
         * （和世界每刻调的是同一个方法，只是在这一组的同步代码里连着调）。
         * 期望：破坏放行、放置被插件取消 → 那一格留空气，圆石进背包，木板一块没少，魔杖只扣拆的 250（不是 300），地上没有掉落物；
         * 插件各一条主人的 BlockBreakEvent（放行）和 BlockPlaceEvent（已取消），BlockPlaceEvent 报的物品不是魔杖（魔杖报的是另一只手）。
         * 跑完把主人的背包和位置放回去。
         */
        private Outcome c27() {
            List<String> problems = new ArrayList<>();
            BlockPos cell = slot(PLACE_ONLY, 17).offset(1, 0, 1);
            set(cell, Blocks.STONE.defaultBlockState());
            ItemStack wand = new ItemStack(ECBuilder.MAGICAL_BUILDER_REPLACE_ALL.get());
            wand.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
            BuilderSelection.setFirst(wand, cell, level.dimension());
            BuilderSelection.setSecond(wand, cell);
            BuilderSelection.setStoredBlock(wand, ForgeRegistries.BLOCKS.getKey(Blocks.OAK_PLANKS));
            int mruBefore = MRUStaffItem.storedMRU(wand);
            Vec3 home = owner.position();
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.MAIN_HAND, wand);
            owner.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 4));
            BlockPos stand = zoneBase(PLACE_ONLY).offset(-3, 1, 1);   // 只拒放置区西边的区间空隙
            owner.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0F, 0F);
            BuilderJobs.StartResult started;
            int ticks = 0;
            int drops;
            int cobble;
            int planks;
            int mruAfter;
            BuilderJobs.Result result;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                started = BuilderJobs.start(owner, InteractionHand.MAIN_HAND);
                TickEvent.ServerTickEvent end = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server);
                while (BuilderJobs.isRunning(OWNER_ID) && ticks < 20) {
                    BuilderJobs.onBuilderServerTick(end);
                    ticks++;
                }
                drops = countItems(spawns.alive(), null);
                spawns.discardAll();
            } finally {
                if (BuilderJobs.isRunning(OWNER_ID)) {
                    BuilderJobs.stop(owner);
                    problems.add("20 刻还没做完，已停下");
                }
                cobble = countInventory(owner, Items.COBBLESTONE);
                planks = countInventory(owner, Items.OAK_PLANKS);
                mruAfter = MRUStaffItem.storedMRU(wand);
                result = BuilderJobs.lastResult(OWNER_ID);
                owner.getInventory().clearContent();
                owner.moveTo(home.x, home.y, home.z, 0F, 0F);
            }
            boolean air = level.getBlockState(cell).isAir();
            Probe probe = Probe.read();
            probe.expectAll(problems, "BlockBreakEvent", PLACE_ONLY, OWNER_NAME, "CraftPlayer", false, 1);
            probe.expectAll(problems, "BlockPlaceEvent", PLACE_ONLY, OWNER_NAME, "CraftPlayer", true, 1);
            // 魔杖报的是没拿魔杖的那只手（这里是空着的副手）：插件不能看到「放下了一根创世神之杖」
            Map<String, String> placeEvent = probe.first("BlockPlaceEvent");
            if (placeEvent != null && placeEvent.getOrDefault("item", "").contains("MAGICAL_BUILDER")) {
                problems.add("BlockPlaceEvent 报的物品是魔杖：" + placeEvent.get("line"));
            }
            int spent = mruBefore - mruAfter;
            String actual = "开工 " + started + "，" + ticks + " 刻做完（" + (result == null ? "没有结果" : result.reason() + " " + result.done() + " 格") + "）；那格"
                    + (air ? "空气" : "是 " + level.getBlockState(cell)) + " 圆石 " + cobble + " 木板 " + planks + " 扣 MRU " + spent + " 掉落物 " + drops;
            boolean pass = started == BuilderJobs.StartResult.STARTED && air && cobble == 1 && planks == 4 && spent == 250 && drops == 0;
            return new Outcome(pass, "开工 STARTED；那格空气 圆石 1 木板 4 扣 MRU 250 掉落物 0；插件 破坏放行 1 条、放置取消 1 条", actual, problems);
        }

        /**
         * C28：魔力传送器（6b M2，batch6b §5.3 / §8）。在 WILD 和 OWN 之间的区间空隙摆一座传送器（41 格结构），落点石头放在 DESTROY 和 PLACE_ONLY
         * 之间的空隙；单独建一名「乘客」站上去，同步调方块实体的服务端 tick（{@link MRUBlockEntity#tickServer}，蓄能刻数覆盖成 3）推进到点：
         * <ol>
         *   <li>同维度：传到落点顶上、正好扣费用；记插件看到的 PlayerTeleportEvent 原因（计划预期 UNKNOWN）和两头的世界；</li>
         *   <li>回到台上再来一次，插件取消这次传送（{@value #PROP_CANCEL_TELEPORT} = 乘客名）：人不动、MRU 不扣、状态 CANCELLED；</li>
         *   <li>跨维度：落点换成海纳尔同坐标附近（y = 原点 + 19）的一块石头：人到了海纳尔、扣掉容量；落点的权限探测（BlockBreakEvent）
         *       报的是海纳尔（region=otherworld、primaryWorld=false），不是主世界同坐标。海纳尔那一片不是空气（地形太高）时这一步只记录。</li>
         * </ol>
         * 跑完把乘客从所在世界移除，拆掉结构和落点，海纳尔那几格还原、取消强制加载。
         */
        private Outcome c28() {
            List<String> problems = new ArrayList<>();
            BlockPos machine = zoneBase(WILD).offset(ZONE_SIZE + 4, 1, 4);   // x = 14：WILD 和 OWN 之间的空隙
            BlockPos floor = zoneBase(DESTROY).offset(ZONE_SIZE + 4, 1, 4);  // x = 74：DESTROY 和 PLACE_ONLY 之间的空隙
            List<BlockPos> placed = new ArrayList<>();
            for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECVoidMachines.TELEPORTER_STRUCTURE.rules().entrySet()) {
                if (!entry.getValue().isOptional()) {
                    BlockPos pos = machine.offset(entry.getKey());
                    set(pos, entry.getValue().displayState());
                    placed.add(pos);
                }
            }
            set(floor, Blocks.STONE.defaultBlockState());
            if (!(level.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be)) {
                return new Outcome(false, "摆出魔力传送器", "机器那格没有方块实体", problems);
            }
            be.structure().checkNow(level, machine, Direction.NORTH);
            if (!be.structure().formed()) {
                return new Outcome(false, "结构成形", "不成形，差 " + be.mismatchOffset(), problems);
            }
            ServerPlayer porter = realPlayer(PORTER_ID, PORTER_NAME);
            ECProtection.forget(PORTER_ID);
            ServerLevel hoanna = ECHoanna.level(server);
            BlockPos hoannaFloor = new BlockPos(floor.getX(), origin.getY() + 19, floor.getZ());
            Map<BlockPos, BlockState> hoannaSaved = new LinkedHashMap<>();
            ChunkPos hoannaChunk = new ChunkPos(hoannaFloor);
            boolean hoannaForced = false;
            String same;
            String cancelled;
            String cross;
            boolean samePass;
            boolean cancelPass;
            Boolean crossPass;
            try {
                // 覆盖值放进 try 里设，和 finally 的清除成对：设上了就一定清掉，不会留给后面的用例
                be.setTestOverrides(null, null, 3, true, false);
                // 第十三批起往海纳尔传要过矩阵阶的门（C61 专门测门），这里只测保护，门填 0
                MatrixRank.setTestOverrides(null, 0, null, null);
                // ① 同维度
                be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, boundGem(GlobalPos.of(level.dimension(), floor)));
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                int cost = be.cost();
                int mru0 = be.getMRUStorage().getMRU();
                System.setProperty(PROP_EVENTS, "");
                standOnTeleporter(porter, be);
                tickTeleporter(be, 6);
                Probe sameProbe = Probe.read();
                Vec3 landing = new Vec3(floor.getX() + 0.5D, floor.getY() + 1D, floor.getZ() + 0.5D);
                boolean arrived = porter.level() == level && porter.position().distanceToSqr(landing) < 1.0E-4D;
                int spent = mru0 - be.getMRUStorage().getMRU();
                Map<String, String> tp = sameProbe.first("PlayerTeleportEvent");
                same = (arrived ? "到了" : "没到（" + porter.position() + "，状态 " + be.status() + "）") + " 扣 " + spent + "/" + cost + " 原因 " + teleportSummary(tp);
                samePass = arrived && spent == cost && tp != null && "false".equals(tp.get("cancelled"));

                // ② 插件取消一次：先让机器看到人走了（清掉「要先走下来」），再站回去
                tickTeleporter(be, 1);
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                int mru1 = be.getMRUStorage().getMRU();
                System.setProperty(PROP_EVENTS, "");
                System.setProperty(PROP_CANCEL_TELEPORT, PORTER_NAME);
                standOnTeleporter(porter, be);
                tickTeleporter(be, 6);
                boolean consumed = System.getProperty(PROP_CANCEL_TELEPORT) == null;
                System.clearProperty(PROP_CANCEL_TELEPORT);
                Probe cancelProbe = Probe.read();
                Map<String, String> tpCancelled = cancelProbe.first("PlayerTeleportEvent");
                boolean stayed = porter.blockPosition().equals(machine.above());
                int spentCancelled = mru1 - be.getMRUStorage().getMRU();
                cancelled = (stayed ? "人没动" : "人动了（" + porter.position() + "）") + " 扣 " + spentCancelled + " 状态 " + be.status()
                        + " 插件" + (consumed ? "取消了一次" : "没取消（旧版测试插件？）") + " 事件 " + teleportSummary(tpCancelled);
                cancelPass = stayed && spentCancelled == 0 && be.status() == MagicalTeleporterBlockEntity.Status.CANCELLED && consumed
                        && tpCancelled != null && "true".equals(tpCancelled.get("cancelled"));

                // ③ 跨维度
                String skip = null;
                if (hoanna == null) {
                    skip = "没有海纳尔世界";
                } else {
                    hoannaForced = !hoanna.getForcedChunks().contains(hoannaChunk.toLong());
                    if (hoannaForced) {
                        hoanna.setChunkForced(hoannaChunk.x, hoannaChunk.z, true);
                    }
                    hoanna.getChunk(hoannaChunk.x, hoannaChunk.z);
                    for (int dy = 0; dy <= 2 && skip == null; dy++) {
                        BlockPos pos = hoannaFloor.above(dy);
                        if (!hoanna.getBlockState(pos).isAir()) {
                            skip = "海纳尔 " + pos.toShortString() + " 是 " + hoanna.getBlockState(pos) + "（地形太高）";
                        }
                    }
                }
                if (skip != null) {
                    cross = "跳过：" + skip;
                    crossPass = null;
                } else {
                    hoannaSaved.put(hoannaFloor, hoanna.getBlockState(hoannaFloor));
                    hoanna.setBlock(hoannaFloor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    // 让机器看到人走了（取消那次留下的「要先走下来」）
                    porter.moveTo(machine.getX() + 0.5D, machine.getY() + 4D, machine.getZ() + 0.5D, 0F, 0F);
                    tickTeleporter(be, 1);
                    be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, boundGem(GlobalPos.of(ECHoanna.LEVEL, hoannaFloor)));
                    be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                    int crossCost = be.cost();
                    int mru2 = be.getMRUStorage().getMRU();
                    System.setProperty(PROP_EVENTS, "");
                    standOnTeleporter(porter, be);
                    tickTeleporter(be, 6);
                    Probe crossProbe = Probe.read();
                    Vec3 hoannaLanding = new Vec3(hoannaFloor.getX() + 0.5D, hoannaFloor.getY() + 1D, hoannaFloor.getZ() + 0.5D);
                    boolean there = porter.level() == hoanna && porter.position().distanceToSqr(hoannaLanding) < 1.0E-4D;
                    int spentCross = mru2 - be.getMRUStorage().getMRU();
                    int probesThere = 0;
                    int probesHere = 0;
                    for (Map<String, String> event : crossProbe.all()) {
                        if ("BlockBreakEvent".equals(event.get("type")) && PORTER_NAME.equals(event.get("playerName"))) {
                            if ("false".equals(event.get("primaryWorld")) && "otherworld".equals(event.get("region"))) {
                                probesThere++;
                            } else {
                                probesHere++;
                            }
                        }
                    }
                    Map<String, String> tpCross = crossProbe.first("PlayerTeleportEvent");
                    cross = (there ? "到了海纳尔" : "没到（在 " + porter.level().dimension().location() + " " + porter.position() + "，状态 " + be.status() + "）")
                            + " 扣 " + spentCross + "/" + crossCost + " 落点探测 海纳尔 " + probesThere + " 条、主世界 " + probesHere + " 条 原因 " + teleportSummary(tpCross);
                    crossPass = there && spentCross == crossCost && probesThere >= 1 && probesHere == 0;
                }
            } finally {
                MatrixRank.clearTestOverrides();
                MagicalTeleporterBlockEntity.setTestOccupant(level, machine, null);
                System.clearProperty(PROP_CANCEL_TELEPORT);
                if (porter.serverLevel().players().contains(porter)) {
                    porter.serverLevel().removePlayerImmediately(porter, Entity.RemovalReason.DISCARDED);
                }
                ECProtection.forget(PORTER_ID);
                for (BlockPos pos : placed) {
                    set(pos, Blocks.AIR.defaultBlockState());
                }
                set(floor, Blocks.AIR.defaultBlockState());
                if (hoanna != null) {
                    for (Map.Entry<BlockPos, BlockState> entry : hoannaSaved.entrySet()) {
                        hoanna.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                    if (hoannaForced) {
                        hoanna.setChunkForced(hoannaChunk.x, hoannaChunk.z, false);
                    }
                }
            }
            if (hoanna != null && hoanna.players().contains(porter)) {
                problems.add("乘客还留在海纳尔的玩家表里");
            }
            String actual = "同维度 " + same + "；取消 " + cancelled + "；跨维度 " + cross;
            boolean pass = samePass && cancelPass && (crossPass == null || crossPass);
            return new Outcome(pass, "同维度 到了、扣满费用；取消 人没动、不扣、CANCELLED；跨维度 到了海纳尔、扣容量、落点探测只报海纳尔", actual, problems);
        }

        // ------------------------------------------------------------------ 第七批（集成时补）

        /**
         * C29：余烬元素剑打玩家（第七批 M2，batch7 §8）。另一名玩家拿一把锻好的剑（火主元素、两对「混沌 + 神圣」余烬：命中着火 + 引雷，满 5000 MRU），
         * 用真近战 {@code Player.attack} 打主人。攻击冷却没走（另一名玩家没 tick 过），这一下只打约 0.2 点，主人不会被打死；
         * 命中效果只看这一下生效没有，和打多少无关：
         * <ol>
         *   <li>主人在 owner 区（插件当成关了 PvP，取消 EntityDamageByEntityEvent）→ {@code hurt} 返回 false、不调 {@code hurtEnemy}：
         *       不着火、剑的 MRU 不变、没有闪电伤害；</li>
         *   <li>主人在野外 → 着火、扣 100（主元素 50 + 余烬 50）、挨一次带攻击者的闪电伤害（数 LivingDamageEvent，伤害生效才算）。</li>
         * </ol>
         * 同 C18 用反射临时打开主世界的 {@code pvpMode}（找不到就只记录）；跑完拿走剑、灭火、清效果、满血。
         */
        private Outcome c29() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            ResourceLocation fire = ForgedSwordData.Element.FIRE.focus();
            ForgedSwordData data = new ForgedSwordData(
                    List.of(EmberKind.CHAOS.id(), EmberKind.DIVINE.id(), EmberKind.CHAOS.id(), EmberKind.DIVINE.id()),
                    List.of(fire, fire, fire, fire), ForgedSwordData.Element.FIRE, ForgedSwordData.Element.FIRE);
            ItemStack sword = ECEmberForge.FORGED_ELEMENTAL_SWORD.get().forged(data);
            sword.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
            int[] lightning = {0};
            Consumer<LivingDamageEvent> counter = event -> {
                if (event.getEntity() == owner && event.getSource().is(DamageTypes.LIGHTNING_BOLT) && event.getSource().getEntity() == other) {
                    lightning[0]++;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, counter);
            boolean pvpBefore = false;
            boolean deniedFire;
            float deniedHealth;
            int deniedMru;
            int deniedLightning;
            boolean wildFire;
            float wildLoss;
            int wildMru;
            int wildLightning;
            Probe deniedProbe;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                other.setItemInHand(InteractionHand.MAIN_HAND, sword);
                owner.clearFire();
                standAt(owner, slot(OWN, 20).offset(1, 1, 1));
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                other.attack(owner);
                deniedFire = owner.getRemainingFireTicks() > 0;
                deniedHealth = owner.getHealth();
                deniedMru = MRUStaffItem.storedMRU(other.getMainHandItem());
                deniedLightning = lightning[0];
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, slot(WILD, 20).offset(1, 1, 1));
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                float before = owner.getHealth();
                other.attack(owner);
                wildFire = owner.getRemainingFireTicks() > 0;
                wildLoss = before - owner.getHealth();
                wildMru = MRUStaffItem.storedMRU(other.getMainHandItem());
                wildLightning = lightning[0] - deniedLightning;
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(counter);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                other.removeAllEffects();
                owner.clearFire();
                owner.removeAllEffects();
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            int full = MRUStaffItem.MAX_MRU;
            String actual = "领地 着火 " + deniedFire + " 生命 " + fmt(deniedHealth) + " MRU " + deniedMru + " 闪电 " + deniedLightning
                    + "；野外 着火 " + wildFire + " 掉血 " + fmt(wildLoss) + " MRU " + wildMru + " 闪电 " + wildLightning + "；插件 " + deniedProbe.summary();
            boolean pass = !deniedFire && deniedHealth == owner.getMaxHealth() && deniedMru == full && deniedLightning == 0
                    && wildFire && wildLoss > 0F && wildMru == full - 2 * ForgedElementalSwordItem.COST && wildLightning == 1;
            return new Outcome(pass, "领地 不着火、不掉血、MRU 5000、没有闪电（插件 EntityDamageByEntityEvent 已取消）；野外 着火、掉血、MRU 4900、闪电 1 次",
                    actual, problems);
        }

        /**
         * C30：灵魂刻刀打村民（第七批 M3，batch7 §8；要 1.2.0 版测试插件：owner 区里「玩家打村民」的 EntityDamageByEntityEvent 也取消）。
         * 两边各摆一只有工作站的农民（堆肥桶，先用 {@code PoiManager.take} 占住名额、村民记忆里写上这个工作站，同 GameTest 的
         * {@code SoulScriberTests.soulScriberCapturesVillager}）、生命 2.5，主人手拿灵魂刻刀用真近战 {@code Player.attack} 打它（攻击冷却清零，约 0.2 点）：
         * <ol>
         *   <li>owner 区：插件取消 → {@code hurt} 返回 false、不调 {@code hurtEnemy}：村民活着、生命不变、没有灵魂、工作站名额仍是 0；</li>
         *   <li>野外：这一下生效 → 刻刀补一下 soul_capture → 村民死亡、掉 1 个村民的灵魂、没有别的掉落、工作站名额放回 1。</li>
         * </ol>
         * 跑完拿走刻刀、删掉还活着的村民、拆掉工作站。
         */
        private Outcome c30() {
            List<String> problems = new ArrayList<>();
            ItemStack held = owner.getMainHandItem().copy();
            VillagerHit denied;
            VillagerHit open;
            Probe deniedProbe;
            try {
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECDemonic.SOUL_SCRIBER.get()));
                denied = scribeVillager(slot(OWN, 21), problems);
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                open = scribeVillager(slot(WILD, 21), problems);
            } finally {
                owner.setItemInHand(InteractionHand.MAIN_HAND, held);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            int cancelledHits = 0;
            for (Map<String, String> event : deniedProbe.all()) {
                if ("EntityDamageByEntityEvent".equals(event.get("type")) && "VILLAGER".equals(event.get("entity"))
                        && "owner".equals(event.get("region")) && "true".equals(event.get("cancelled"))
                        && event.getOrDefault("damager", "").startsWith(OWNER_NAME + "/")) {
                    cancelledHits++;
                }
            }
            if (cancelledHits == 0) {
                problems.add("插件缺少 owner 区主人打村民、已取消的 EntityDamageByEntityEvent（1.2.0 版测试插件才会取消）；实际收到 " + deniedProbe.summary());
            }
            String actual = "领地 " + denied + "；野外 " + open + "；插件 " + deniedProbe.summary();
            boolean pass = denied.alive() && denied.health() == 2.5F && denied.souls() == 0 && denied.freeTickets() == 0
                    && !open.alive() && open.souls() == 1 && open.otherDrops() == 0 && open.freeTickets() == 1;
            return new Outcome(pass, "领地 活着、生命 2.5、灵魂 0、名额 0（插件已取消）；野外 死亡、灵魂 1、别的掉落 0、名额 1", actual, problems);
        }

        /**
         * C31：虚空套装在混合端上等伤害生效再扣 MRU（第七批 M3 审查修复）。主人穿满电的虚空全套（每件 5000）、背包里一块满的红色灵魂物质（120000），
         * 另一名玩家空手用真近战 {@code Player.attack} 打他（攻击冷却没走，约 0.2 点；每件至少按 1 点算，整下挡掉要 4 × {@code general.void_armor.mru_per_damage}，默认 4 × 400 = 1600）。闪避掷数设成 1（不闪）：
         * <ol>
         *   <li>主人在 owner 区：LivingHurtEvent 里只算好、取消这一下；插件取消 EntityDamageByEntityEvent → 走不到 LivingDamageEvent →
         *       四件和灵魂物质的 MRU 一点不变（以前在 LivingHurtEvent 里就扣了，站在禁 PvP 的地方也能被人一下下抽空）；</li>
         *   <li>主人在野外：插件放行 → LivingDamageEvent（伤害 0）→ 灵魂物质付 1600（默认单价），四件自己的不动。</li>
         * </ol>
         * 认不出混合端（{@code RayHitGate.isBukkitHybrid} 为 false）时记成问题。同 C29 用反射临时打开主世界的 {@code pvpMode}；跑完脱掉盔甲、放回背包那一格、满血。
         */
        private Outcome c31() {
            List<String> problems = new ArrayList<>();
            boolean hybrid = RayHitGate.isBukkitHybrid();
            if (!hybrid) {
                problems.add("没认出混合端（RayHitGate.isBukkitHybrid 为 false），等伤害生效再扣这条路没走到");
            }
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            ArmorItem.Type[] types = ArmorItem.Type.values();
            ItemStack[] armor = new ItemStack[types.length];
            ItemStack[] wornBefore = new ItemStack[types.length];
            for (ArmorItem.Type type : types) {
                wornBefore[type.ordinal()] = owner.getItemBySlot(type.getSlot()).copy();
                ItemStack piece = new ItemStack(ECVoidArmor.all().get(type.ordinal()).get());
                piece.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(5000));
                armor[type.ordinal()] = piece;
            }
            ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
            int full = MRUStaffItem.storedMRU(storage);
            ItemStack slotBefore = owner.getInventory().getItem(9).copy();
            boolean pvpBefore = false;
            long before;
            long deniedAvailable;
            long wildAvailable;
            int deniedStorage;
            int[] deniedArmor = new int[types.length];
            int wildStorage;
            int[] wildArmor = new int[types.length];
            Probe deniedProbe;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), armor[type.ordinal()]);
                }
                owner.getInventory().setItem(9, storage);
                // 身上储存物品的合计（以防前面的用例在主人背包里留了别的储存物品，按合计核对）
                before = MRUDrain.availableFromStorages(owner);
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                VoidArmorEvents.setTestDodgeRoll(1F);
                standAt(owner, slot(OWN, 22).offset(1, 1, 1));
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                other.attack(owner);
                deniedStorage = MRUStaffItem.storedMRU(storage);
                deniedAvailable = MRUDrain.availableFromStorages(owner);
                for (ArmorItem.Type type : types) {
                    deniedArmor[type.ordinal()] = MRUStaffItem.storedMRU(armor[type.ordinal()]);
                }
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, slot(WILD, 22).offset(1, 1, 1));
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                other.attack(owner);
                wildStorage = MRUStaffItem.storedMRU(storage);
                wildAvailable = MRUDrain.availableFromStorages(owner);
                for (ArmorItem.Type type : types) {
                    wildArmor[type.ordinal()] = MRUStaffItem.storedMRU(armor[type.ordinal()]);
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                VoidArmorEvents.setTestDodgeRoll(null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), wornBefore[type.ordinal()]);
                }
                owner.getInventory().setItem(9, slotBefore);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            boolean deniedArmorFull = true;
            boolean wildArmorFull = true;
            for (int i = 0; i < types.length; i++) {
                deniedArmorFull &= deniedArmor[i] == 5000;
                wildArmorFull &= wildArmor[i] == 5000;
            }
            String actual = "混合端 " + hybrid + "；领地 储存物品少了 " + (before - deniedAvailable) + "（灵魂物质 " + deniedStorage + "）四件 "
                    + java.util.Arrays.toString(deniedArmor) + "；野外 储存物品少了 " + (before - wildAvailable) + "（灵魂物质 " + wildStorage + "）四件 "
                    + java.util.Arrays.toString(wildArmor) + "；插件 " + deniedProbe.summary();
            int wildCost = 4 * ECVoidArmor.Config.shield().mruPerDamage();
            boolean pass = hybrid && deniedAvailable == before && deniedArmorFull && before - wildAvailable == wildCost && wildArmorFull;
            if (pass && (deniedStorage != full || wildStorage != full - wildCost)) {
                problems.add("合计对得上，但不是灵魂物质付的（主人身上还有别的储存物品）");
            }
            return new Outcome(pass, "混合端 true；领地 储存物品少了 0、四件各 5000（插件 EntityDamageByEntityEvent 已取消）；野外 储存物品少了 " + wildCost + "、四件各 5000",
                    actual, problems);
        }

        // ------------------------------------------------------------------ 第八批（集成时补）

        /** C32~C34 放在测试区最上面一层（slot 都在 y+0~8），x 从 owner 区的西边界算：+21.5 在 owner 区里，+18.5 在 wild 与 owner 之间的空隙（不归任何区） */
        private BlockPos topRow(int dx) {
            return origin.offset(dx, ZONE_SIZE - 1, 8);
        }

        /**
         * C32：全息投影台与 Boss（第八批 M1，batch8 §9）。临时打开「海纳尔以外的维度也能召唤」（测试覆盖，不改配置文件）：
         * <ol>
         *   <li>另一名玩家在 owner 区（只有主人能动）右键投影台：{@code canEdit} 的破坏探测被插件拒 → DENIED，台子还在、没有投影；</li>
         *   <li>主人在野外召唤：OK，台子没了，主人在名单里；</li>
         *   <li>主人走到 30 格外（24~64 格之间，会被拉回），设 {@value #PROP_CANCEL_TELEPORT} 让插件取消下一次传送，推进一次名单：
         *       拉回的传送被取消 → 不拉、不扣血、位置不变，主人仍在名单里（只有跑出 escape_range 才脱战）。</li>
         * </ol>
         * 跑完删掉投影、拆掉台子和垫的石头、撤掉测试覆盖。
         */
        private Outcome c32() {
            List<String> problems = new ArrayList<>();
            BlockPos deniedPad = topRow(ZONE_STEP + 5);
            BlockPos wildPad = topRow(5);
            HologramEntity hologram = null;
            HolopadBlock.Result denied;
            boolean deniedPadKept;
            boolean deniedNoHologram;
            HolopadBlock.Result wild = null;
            boolean wildPadGone = false;
            boolean summonerListed = false;
            boolean stillListed = false;
            float healthLoss = -1F;
            double moved = -1D;
            Probe teleportProbe = null;
            ECHologram.Config.setTestOverrides(ECHologram.Config.DEFAULTS.withOtherDimensions(true));
            // 第十三批起召唤全息投影要过矩阵阶的门（GameTest 测门），这里只测保护，门填 0
            MatrixRank.setTestOverrides(null, null, 0, null);
            try {
                for (BlockPos pad : List.of(deniedPad, wildPad)) {
                    set(pad.below(), Blocks.STONE.defaultBlockState());
                    set(pad, ECHologram.HOLOPAD.get().defaultBlockState());
                }
                standAt(other, deniedPad.offset(0, 0, -2));
                denied = HolopadBlock.summon(other, level, deniedPad).result();
                deniedPadKept = level.getBlockState(deniedPad).is(ECHologram.HOLOPAD.get());
                deniedNoHologram = level.getEntitiesOfClass(HologramEntity.class, new AABB(deniedPad).inflate(8D)).isEmpty();
                Probe deniedProbe = Probe.read();
                deniedProbe.expect(problems, "BlockBreakEvent", OWN, OTHER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");

                standAt(owner, wildPad.offset(0, 0, -2));
                HolopadBlock.Outcome outcome = HolopadBlock.summon(owner, level, wildPad);
                wild = outcome.result();
                hologram = outcome.hologram();
                wildPadGone = level.getBlockState(wildPad).isAir();
                if (hologram != null) {
                    summonerListed = hologram.isParticipant(OWNER_ID);
                    standAt(owner, wildPad.offset(30, 0, 0));
                    Vec3 before = owner.position();
                    float health = owner.getHealth();
                    System.setProperty(PROP_EVENTS, "");
                    System.setProperty(PROP_CANCEL_TELEPORT, OWNER_NAME);
                    hologram.tickRoster();
                    stillListed = hologram.isParticipant(OWNER_ID);
                    healthLoss = health - owner.getHealth();
                    moved = owner.position().distanceTo(before);
                    teleportProbe = Probe.read();
                }
            } finally {
                MatrixRank.clearTestOverrides();
                System.clearProperty(PROP_CANCEL_TELEPORT);
                ECHologram.Config.setTestOverrides(null);
                if (hologram != null && !hologram.isRemoved()) {
                    hologram.discard();
                }
                for (BlockPos pad : List.of(deniedPad, wildPad)) {
                    set(pad, Blocks.AIR.defaultBlockState());
                    set(pad.below(), Blocks.AIR.defaultBlockState());
                }
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            Map<String, String> teleport = teleportProbe == null ? null : teleportProbe.first("PlayerTeleportEvent");
            if (hologram != null && (teleport == null || !"true".equals(teleport.get("cancelled")))) {
                problems.add("插件应收到并取消一次主人的 PlayerTeleportEvent，实际 " + teleportSummary(teleport));
            }
            String actual = "领地 " + denied + " 台子" + (deniedPadKept ? "还在" : "没了") + " 投影" + (deniedNoHologram ? "没有" : "有")
                    + "；野外 " + wild + " 台子" + (wildPadGone ? "没了" : "还在") + " 召唤者在名单 " + summonerListed
                    + "；拉回被取消 仍在名单 " + stillListed + " 掉血 " + fmt(healthLoss) + " 位移 " + String.format("%.2f", moved);
            boolean pass = denied == HolopadBlock.Result.DENIED && deniedPadKept && deniedNoHologram
                    && wild == HolopadBlock.Result.OK && wildPadGone && summonerListed && stillListed && healthLoss == 0F && moved < 1.0E-3D;
            return new Outcome(pass, "领地 DENIED 台子还在 投影没有；野外 OK 台子没了 召唤者在名单 true；拉回被取消 仍在名单 true 掉血 0.0 位移 0.00",
                    actual, problems);
        }

        /**
         * C33：轨道打击（第八批 8-Fa / M3，batch8 §9）。主人放一记（伤害 4、打玩家倍率 1、目标照遥控器的规则），落点在 owner 区西边界上，
         * 两名新玩家（各自先跑掉出生无敌）一名站在 owner 区里（插件当成关了 PvP）、一名站在区外的空隙，各离落点 1.5 格，立刻结算：
         * owner 区里的那名不掉血、不着火、不被推开（插件取消 EntityDamageByEntityEvent），区外的掉血、着火、也不被推开。
         * 同 C29 用反射临时打开主世界的 {@code pvpMode}。
         */
        private Outcome c33() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            ServerPlayer inside = realPlayer(UUID.nameUUIDFromBytes("ec5-selftest:victim-a".getBytes(StandardCharsets.UTF_8)), "EC5TestVictimA");
            ServerPlayer outside = realPlayer(UUID.nameUUIDFromBytes("ec5-selftest:victim-b".getBytes(StandardCharsets.UTF_8)), "EC5TestVictimB");
            // 先跑掉 60 tick 的出生无敌（同 setup 里的主人）
            for (int i = 0; i <= 60; i++) {
                inside.tick();
                outside.tick();
            }
            BlockPos center = topRow(ZONE_STEP);
            standAt(inside, center.offset(1, 0, 0));
            standAt(outside, center.offset(-2, 0, 0));
            standAt(owner, center.offset(0, 0, -2));
            inside.setDeltaMovement(Vec3.ZERO);
            outside.setDeltaMovement(Vec3.ZERO);
            Vec3 insideAt = inside.position();
            Vec3 outsideAt = outside.position();
            boolean pvpBefore = false;
            OrbitalStrikeEntity strike = null;
            Probe probe;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                strike = OrbitalStrikeEntity.spawn(level, new Vec3(center.getX(), center.getY(), center.getZ() + 0.5D), 4F, 1F, 1000, owner,
                        OrbitalRemoteItem.targets(owner));
                if (strike == null) {
                    return new Outcome(false, "打击实体生成出来", "生成被取消", problems);
                }
                strike.detonate(List.of(inside, outside));
                probe = Probe.read();
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                if (strike != null && !strike.isRemoved()) {
                    strike.discard();
                }
            }
            probe.expect(problems, "EntityDamageByEntityEvent", OWN, "EC5TestVictimA", "CraftPlayer", true);
            float insideLoss = inside.getMaxHealth() - inside.getHealth();
            float outsideLoss = outside.getMaxHealth() - outside.getHealth();
            boolean insideFire = inside.getRemainingFireTicks() > 0;
            boolean outsideFire = outside.getRemainingFireTicks() > 0;
            double insideMoved = inside.position().distanceTo(insideAt) + inside.getDeltaMovement().horizontalDistance();
            double outsideMoved = outside.position().distanceTo(outsideAt) + outside.getDeltaMovement().horizontalDistance();
            String actual = "区内 掉血 " + fmt(insideLoss) + " 着火 " + insideFire + " 位移 " + String.format("%.2f", insideMoved)
                    + "；区外 掉血 " + fmt(outsideLoss) + " 着火 " + outsideFire + " 位移 " + String.format("%.2f", outsideMoved) + "；插件 " + probe.summary();
            boolean pass = insideLoss == 0F && !insideFire && insideMoved < 1.0E-3D && outsideLoss > 0F && outsideFire && outsideMoved < 1.0E-3D;
            return new Outcome(pass, "区内 掉血 0.0 不着火 不动（插件 EntityDamageByEntityEvent 已取消）；区外 掉血、着火、不动", actual, problems);
        }

        /**
         * C34：二分场的 0 伤害探测（第八批 8-Fa / M3，batch8 §9；要 1.3.0 版测试插件）。owner 区西边界两侧各一只僵尸（NoAI、带 {@value #PROTECTED_TAG} 标签），
         * 主人射出的那种场（二分枪的目标规则、要探测）放在边界上立刻张开：
         * <ol>
         *   <li>owner 区里那只：探测的 EntityDamageByEntityEvent（伤害 0）被插件取消 → 走不到 LivingDamageEvent → 不减半、没有效果；</li>
         *   <li>空隙里那只：插件放行 → LivingDamageEvent（伤害 0）照常发 → 生命减半、虚弱 / 缓慢 / 挖掘疲劳 / 失明都有。</li>
         * </ol>
         * 这条同时验证「0 伤害的 Bukkit 伤害事件照常发给插件、放行后照常发 LivingDamageEvent」，是二分场在混合端能不能用的前提。
         */
        private Outcome c34() {
            List<String> problems = new ArrayList<>();
            if (!RayHitGate.isBukkitHybrid()) {
                problems.add("没认出混合端（RayHitGate.isBukkitHybrid 为 false），探测要等 LivingDamageEvent 这条路没走到");
            }
            BlockPos center = topRow(ZONE_STEP);
            Zombie inside = EntityType.ZOMBIE.create(level);
            Zombie outside = EntityType.ZOMBIE.create(level);
            if (inside == null || outside == null) {
                return new Outcome(false, "造出两只僵尸", "造不出来", problems);
            }
            DividerFieldEntity field = null;
            Probe probe;
            try {
                inside.moveTo(center.getX() + 1.5D, center.getY(), center.getZ() + 0.5D, 0F, 0F);
                outside.moveTo(center.getX() - 1.5D, center.getY(), center.getZ() + 0.5D, 0F, 0F);
                for (Zombie zombie : List.of(inside, outside)) {
                    zombie.setNoAi(true);
                    zombie.addTag(PROTECTED_TAG);
                    level.addFreshEntity(zombie);
                }
                standAt(owner, center.offset(0, 0, -3));
                field = DividerFieldEntity.spawn(level, new Vec3(center.getX(), center.getY(), center.getZ() + 0.5D), 1000, owner,
                        DividerGunItem::isTarget, true);
                if (field == null) {
                    return new Outcome(false, "二分场生成出来", "生成被取消", problems);
                }
                field.detonate();
                probe = Probe.read();
            } finally {
                if (field != null && !field.isRemoved()) {
                    field.discard();
                }
            }
            String insideState = zombieState(inside);
            String outsideState = zombieState(outside);
            boolean insideUntouched = inside.getHealth() == inside.getMaxHealth() && inside.getActiveEffects().isEmpty();
            boolean outsideDivided = Math.abs(outside.getHealth() - outside.getMaxHealth() / 2F) < 1.0E-4F
                    && outside.hasEffect(MobEffects.WEAKNESS) && outside.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && outside.hasEffect(MobEffects.DIG_SLOWDOWN) && outside.hasEffect(MobEffects.BLINDNESS);
            inside.discard();
            outside.discard();
            int cancelled = 0;
            int allowed = 0;
            for (Map<String, String> event : probe.all()) {
                if ("EntityDamageByEntityEvent".equals(event.get("type")) && "ZOMBIE".equals(event.get("entity"))
                        && event.getOrDefault("damager", "").startsWith(OWNER_NAME + "/")) {
                    if ("owner".equals(event.get("region")) && "true".equals(event.get("cancelled"))) {
                        cancelled++;
                    } else if ("false".equals(event.get("cancelled"))) {
                        allowed++;
                    }
                }
            }
            if (cancelled == 0) {
                problems.add("插件缺少 owner 区主人打带标签僵尸、已取消的 EntityDamageByEntityEvent（1.3.0 版测试插件才会取消）；实际收到 " + probe.summary());
            }
            if (allowed == 0) {
                problems.add("插件没收到区外那只僵尸的 0 伤害 EntityDamageByEntityEvent（放行的）；实际收到 " + probe.summary());
            }
            String actual = "区内 " + insideState + "；区外 " + outsideState + "；插件 " + probe.summary();
            return new Outcome(insideUntouched && outsideDivided, "区内 满血、没有效果（插件已取消探测）；区外 减半、四种效果", actual, problems);
        }

        /** C34：僵尸的生命和效果一句话 */
        private static String zombieState(Zombie zombie) {
            StringJoiner effects = new StringJoiner(",");
            zombie.getActiveEffects().forEach(effect -> effects.add(effect.getDescriptionId().replace("effect.minecraft.", "")));
            return "生命 " + fmt(zombie.getHealth()) + "/" + fmt(zombie.getMaxHealth()) + " 效果 [" + effects + "]";
        }

        /**
         * C35：宅男套装挨真近战（第八批 M2，batch8 §9；同 C31 的环境）。主人穿满电的宅男全套，另一名玩家拿钻石剑、攻击冷却清零后用真近战打他
         * （约 1.4 点：每件至少按 1 点、每点 250，整下挡掉；反伤一半约 0.7，过了 0.5 的门槛）：
         * <ol>
         *   <li>主人在 owner 区：插件取消 EntityDamageByEntityEvent → 走不到 LivingDamageEvent → 四件和身上储存物品一点不扣、攻击者不挨荆棘伤害；</li>
         *   <li>主人在野外：插件放行 → 四件各按 1 点扣、单价 250 × {@code pvp_shield_cost_multiplier}（第十三批起攻击者是别的玩家时加价，默认 2，
         *       一共 4 × 500 = 2000；身上有储存物品时先扣它）、攻击者挨一次攻击者是主人的荆棘伤害。</li>
         * </ol>
         * 同 C29 用反射临时打开主世界的 {@code pvpMode}；跑完脱掉盔甲、拿走剑、两人满血。
         */
        private Outcome c35() {
            List<String> problems = new ArrayList<>();
            boolean hybrid = RayHitGate.isBukkitHybrid();
            if (!hybrid) {
                problems.add("没认出混合端（RayHitGate.isBukkitHybrid 为 false），等伤害生效再扣这条路没走到");
            }
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            ArmorItem.Type[] types = ArmorItem.Type.values();
            ItemStack[] armor = new ItemStack[types.length];
            ItemStack[] wornBefore = new ItemStack[types.length];
            int full = ECComputer.Config.values().maxMru();
            for (ArmorItem.Type type : types) {
                wornBefore[type.ordinal()] = owner.getItemBySlot(type.getSlot()).copy();
                ItemStack piece = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
                piece.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(full));
                armor[type.ordinal()] = piece;
            }
            // 攻击者另起一名（先跑掉出生无敌）：另一名玩家从没 tick 过，出生无敌还在，反伤打不到他（ServerPlayer.hurt 直接返回 false）
            ServerPlayer attacker = realPlayer(UUID.nameUUIDFromBytes("ec5-selftest:attacker".getBytes(StandardCharsets.UTF_8)), "EC5TestAttacker");
            for (int i = 0; i <= 60; i++) {
                attacker.tick();
            }
            int[] thorns = {0};
            Consumer<LivingDamageEvent> counter = event -> {
                if (event.getEntity() == attacker && event.getSource().is(DamageTypes.THORNS) && event.getSource().getEntity() == owner) {
                    thorns[0]++;
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, counter);
            boolean pvpBefore = false;
            long[] paid = new long[2];
            int[] thornHits = new int[2];
            float[] attackerLoss = new float[2];
            Probe deniedProbe = null;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), armor[type.ordinal()]);
                }
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                attacker.setItemInHand(InteractionHand.MAIN_HAND, sword);
                // 手上物品的攻击力修饰符平时在 LivingEntity.tick 的换装检测里挂上；这名玩家不进世界、不走那一步（ServerPlayer.tick 不调它），
                // 照换装检测的做法直接挂上，不然这一刀只按空手的 1 点算（约 0.2 点，反伤不到 0.5 的门槛）
                attacker.getAttributes().addTransientAttributeModifiers(sword.getAttributeModifiers(EquipmentSlot.MAINHAND));
                int[] zones = {OWN, WILD};
                for (int i = 0; i < 2; i++) {
                    long armorBefore = 0;
                    for (ItemStack piece : armor) {
                        armorBefore += MRUStaffItem.storedMRU(piece);
                    }
                    long storages = MRUDrain.availableFromStorages(owner);
                    int thornsBefore = thorns[0];
                    standAt(owner, slot(zones[i], 23).offset(1, 1, 1));
                    standAt(attacker, slot(zones[i], 23).offset(1, 1, 1));
                    attacker.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                    attacker.resetAttackStrengthTicker();
                    attacker.attack(owner);
                    long armorAfter = 0;
                    for (ItemStack piece : armor) {
                        armorAfter += MRUStaffItem.storedMRU(piece);
                    }
                    paid[i] = (armorBefore - armorAfter) + (storages - MRUDrain.availableFromStorages(owner));
                    thornHits[i] = thorns[0] - thornsBefore;
                    attackerLoss[i] = attacker.getMaxHealth() - attacker.getHealth();
                    if (i == 0) {
                        deniedProbe = Probe.read();
                        System.setProperty(PROP_EVENTS, "");
                    }
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(counter);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), wornBefore[type.ordinal()]);
                }
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            if (deniedProbe != null) {
                deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            }
            // 第十三批起攻击者是别的玩家时每件只挡 pvp_shield_max_share、单价 × pvp_shield_cost_multiplier（batch13.md §4.4）；
            // 这一下不到 1 点，每件都按 1 点算，所以伤害填 1
            ECComputer.Config.Attuned attuned = ECComputer.Config.attuned();
            long expectedPaid = 0;
            for (ArmorItem.Type type : types) {
                expectedPaid += com.flameforge.essentialcraft5.item.armor.MRUShield.cost(1F, ComputerArmorItem.share(type) * attuned.pvpShieldMaxShare(),
                        ECComputer.Config.values().mruPerDamage(), attuned.priceMultiplier(0) * attuned.pvpShieldCostMultiplier());
            }
            String actual = "混合端 " + hybrid + "；领地 扣 " + paid[0] + " 荆棘 " + thornHits[0] + " 攻击者掉血 " + fmt(attackerLoss[0])
                    + "；野外 扣 " + paid[1] + " 荆棘 " + thornHits[1] + " 攻击者掉血 " + fmt(attackerLoss[1])
                    + "；插件 " + (deniedProbe == null ? "-" : deniedProbe.summary());
            boolean pass = hybrid && paid[0] == 0 && thornHits[0] == 0 && attackerLoss[0] == 0F
                    && paid[1] == expectedPaid && thornHits[1] == 1 && attackerLoss[1] > 0F;
            return new Outcome(pass, "混合端 true；领地 扣 0 荆棘 0 攻击者掉血 0.0（插件 EntityDamageByEntityEvent 已取消）；野外 扣 " + expectedPaid
                    + " 荆棘 1、攻击者掉血", actual, problems);
        }

        /** 读出当前用例的插件事件并清空，接着记下一段 */
        private Probe takeProbe() {
            Probe probe = Probe.read();
            System.setProperty(PROP_EVENTS, "");
            return probe;
        }

        /**
         * C36：饰品封禁场的门（第十批 10a，batch10 §8.2）。主人穿宅男全套（胸甲满电）、开着场，另起一名玩家 T；同 C18 用反射临时打开主世界的
         * {@code pvpMode}，配置用测试覆盖值（主人自己不失效，其余照默认：要交过手、要领地探测）。主人站在 owner 区和 other 区之间的空隙里，
         * 三块区里 T 站的格子都在半径以内。评估直接调 {@link ComputerSealField#evaluate}，时钟是 {@link CurioSeal#now}：
         * <ol>
         *   <li>T 站 owner 区，主人 {@code hurt(playerAttack(主人))} 打 T：插件取消 EntityDamageByEntityEvent → 交手记录没记 → 「没有交手」，
         *       不压制、不扣电，插件没收到任何 BlockBreakEvent（没交手的人不探测）；</li>
         *   <li>T 站野外，打一下（放行）→ 压制、扣 {@code (mru_base + mru_per_target) × 间隔 / 20}，插件收到一条没被取消的、主人对 T 脚下那格的 BlockBreakEvent；</li>
         *   <li>同一格马上再评估：探测有缓存，插件不再收到破坏事件；</li>
         *   <li>T 站 other 区（打得到，但主人在那里没有建筑权限），打一下放行 → 领地探测被拒 → 「没有权限」，不压制；</li>
         *   <li>关掉 {@code pvpMode}、清掉交手记录再打 → 打不出（hurt 返回 false），「不能互相伤害」，不压制。</li>
         * </ol>
         * 跑完关场、撤掉压制、脱掉盔甲、清交手记录。
         */
        private Outcome c36() {
            List<String> problems = new ArrayList<>();
            if (CurioSeal.availability() != CurioSeal.Availability.AVAILABLE) {
                return new Outcome(false, "压制核心可用", CurioSeal.availability() + " " + CurioSealMixinStatus.problems(), problems);
            }
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            CurioSealConfig.Values cfg = CurioSealConfig.DEFAULTS.toBuilder().affectsSelf(false).requireRecentCombat(true).respectClaims(true).build();
            int expectedCost = (int) ((long) (cfg.mruBase() + cfg.mruPerTarget()) * cfg.evaluateInterval() / 20L);
            ArmorItem.Type[] types = ArmorItem.Type.values();
            ItemStack[] wornBefore = new ItemStack[types.length];
            int full = ECComputer.Config.values().maxMru();
            ServerPlayer target = tickedPlayer("seal-target", "EC5TestSealTgt");
            String targetName = target.getGameProfile().getName();
            int steps = 5;
            String[] states = new String[steps];
            long[] paid = new long[steps];
            boolean[] sealedNow = new boolean[steps];
            boolean[] hurt = new boolean[steps];
            Probe[] probes = new Probe[steps];
            boolean resolvedSealed = false;
            boolean pvpBefore = false;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                CurioSealConfig.setTestOverrides(cfg);
                ItemStack chest = ItemStack.EMPTY;
                for (ArmorItem.Type type : types) {
                    wornBefore[type.ordinal()] = owner.getItemBySlot(type.getSlot()).copy();
                    ItemStack piece = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
                    if (type == ArmorItem.Type.CHESTPLATE) {
                        piece.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(full));
                        chest = piece;
                    }
                    owner.setItemSlot(type.getSlot(), piece);
                }
                ComputerSealField.setOn(owner, true);
                standAt(owner, zoneBase(OWN).offset(15, 4, 4));
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
                BlockPos[] cells = {slot(OWN, 14), slot(WILD, 14), slot(WILD, 14), slot(OTHERS, 14), slot(WILD, 14)};
                takeProbe();
                for (int i = 0; i < steps; i++) {
                    if (i == 4) {
                        pvpMode.setBoolean(level, false);
                        ECCombatTags.forget(OWNER_ID);
                        ECCombatTags.forget(target.getUUID());
                    }
                    if (i != 2) {
                        standAt(target, cells[i].offset(1, 1, 1));
                        hurt[i] = target.hurt(level.damageSources().playerAttack(owner), 1F);
                        target.invulnerableTime = 0;
                    }
                    long now = CurioSeal.now(server) + i;
                    long before = MRUStaffItem.storedMRU(chest) + MRUDrain.availableFromStorages(owner);
                    ComputerSealField.Result result = ComputerSealField.evaluate(owner, List.of(target), now);
                    CurioSeal.resolveForTest(now, target);
                    paid[i] = before - (MRUStaffItem.storedMRU(chest) + MRUDrain.availableFromStorages(owner));
                    states[i] = String.valueOf(result.statusOf(target));
                    sealedNow[i] = result.sealed().contains(target);
                    if (i == 1) {
                        resolvedSealed = CurioSeal.isSealed(target);
                    }
                    probes[i] = takeProbe();
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                CurioSealConfig.setTestOverrides(null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                ComputerSealField.setOn(owner, false);
                ComputerSealField.forgetForTest(owner);
                CurioSeal.releaseForTest(target);
                CurioSeal.releaseForTest(owner);
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), wornBefore[type.ordinal()] == null ? ItemStack.EMPTY : wornBefore[type.ordinal()]);
                }
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            probes[0].expect(problems, "EntityDamageByEntityEvent", OWN, targetName, "CraftPlayer", true);
            probes[0].expectCount(problems, "BlockBreakEvent", 0);
            probes[1].expectAll(problems, "BlockBreakEvent", WILD, OWNER_NAME, "CraftPlayer", false, 1);
            probes[2].expectCount(problems, "BlockBreakEvent", 0);
            probes[3].expectAll(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true, 1);
            probes[4].expectCount(problems, "BlockBreakEvent", 0);
            StringJoiner actual = new StringJoiner("；");
            String[] labels = {"owner 区（插件拦下）", "野外", "野外再评估", "other 区", "PvP 关"};
            for (int i = 0; i < steps; i++) {
                actual.add(labels[i] + " hurt=" + (i == 2 ? "-" : hurt[i]) + " " + states[i] + " 压制 " + sealedNow[i] + " 扣 " + paid[i]);
            }
            actual.add("野外结算后被压制 " + resolvedSealed);
            boolean pass = !hurt[0] && "NO_FIGHT".equals(states[0]) && !sealedNow[0] && paid[0] == 0
                    && hurt[1] && "SEALED".equals(states[1]) && sealedNow[1] && paid[1] == expectedCost && resolvedSealed
                    && "SEALED".equals(states[2]) && paid[2] == expectedCost
                    && hurt[3] && "NO_PERMISSION".equals(states[3]) && !sealedNow[3] && paid[3] == 0
                    && !hurt[4] && "CANT_HARM".equals(states[4]) && !sealedNow[4] && paid[4] == 0;
            return new Outcome(pass, "owner 区 hurt=false NO_FIGHT 不压 扣 0、无探测；野外 SEALED 扣 " + expectedCost + "、一次放行的探测；再评估无新探测；"
                    + "other 区 NO_PERMISSION 扣 0、探测被拒；PvP 关 hurt=false CANT_HARM", actual.toString(), problems);
        }

        /**
         * C54：饰品封禁场默认配置下的探测量（batch10 §15.4、policy §2.4：交手门默认关以后，范围内每个能互伤的人都要探测）。
         * 配置就是默认值（不要求交手、看领地），同 C36 打开 {@code pvpMode}、主人穿宅男全套站在区间空隙里，T 一直站在 other 区（主人在那里没有建筑权限）。
         * 连续评估 10 次（假时刻每次 +20），每次评估前清掉主人在 ECProtection 里的缓存（等于它按真实刻数过期了），看的是场自己的按格缓存：
         * 插件只收到 1 条被取消的、主人对 T 脚下那格的 BlockBreakEvent，T 每次都是「没有权限」，不压制、不扣电。
         */
        private Outcome c54() {
            List<String> problems = new ArrayList<>();
            if (CurioSeal.availability() != CurioSeal.Availability.AVAILABLE) {
                return new Outcome(false, "压制核心可用", CurioSeal.availability() + " " + CurioSealMixinStatus.problems(), problems);
            }
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            CurioSealConfig.Values cfg = CurioSealConfig.DEFAULTS;
            ArmorItem.Type[] types = ArmorItem.Type.values();
            ItemStack[] wornBefore = new ItemStack[types.length];
            int full = ECComputer.Config.values().maxMru();
            ServerPlayer target = tickedPlayer("seal-default-target", "EC5TestSealDef");
            int rounds = 10;
            List<String> states = new ArrayList<>();
            long paid = 0L;
            boolean everSealed = false;
            Probe probe;
            boolean pvpBefore = false;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                CurioSealConfig.setTestOverrides(cfg);
                ItemStack chest = ItemStack.EMPTY;
                for (ArmorItem.Type type : types) {
                    wornBefore[type.ordinal()] = owner.getItemBySlot(type.getSlot()).copy();
                    ItemStack piece = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
                    if (type == ArmorItem.Type.CHESTPLATE) {
                        piece.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(full));
                        chest = piece;
                    }
                    owner.setItemSlot(type.getSlot(), piece);
                }
                ComputerSealField.setOn(owner, true);
                standAt(owner, zoneBase(OWN).offset(15, 4, 4));
                standAt(target, slot(OTHERS, 14).offset(1, 1, 1));
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
                takeProbe();
                long base = CurioSeal.now(server);
                for (int i = 0; i < rounds; i++) {
                    ECProtection.forget(OWNER_ID);
                    long now = base + 20L * i;
                    long before = MRUStaffItem.storedMRU(chest) + MRUDrain.availableFromStorages(owner);
                    ComputerSealField.Result result = ComputerSealField.evaluate(owner, List.of(target), now);
                    CurioSeal.resolveForTest(now, target);
                    paid += before - (MRUStaffItem.storedMRU(chest) + MRUDrain.availableFromStorages(owner));
                    states.add(String.valueOf(result.statusOf(target)));
                    everSealed |= result.sealed().contains(target) || CurioSeal.isSealed(target);
                }
                probe = takeProbe();
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                CurioSealConfig.setTestOverrides(null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                ComputerSealField.setOn(owner, false);
                ComputerSealField.forgetForTest(owner);
                CurioSeal.releaseForTest(target);
                CurioSeal.releaseForTest(owner);
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
                ECProtection.forget(OWNER_ID);
                for (ArmorItem.Type type : types) {
                    owner.setItemSlot(type.getSlot(), wornBefore[type.ordinal()] == null ? ItemStack.EMPTY : wornBefore[type.ordinal()]);
                }
            }
            probe.expectAll(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true, 1);
            boolean allDenied = states.size() == rounds && states.stream().allMatch("NO_PERMISSION"::equals);
            String actual = "状态 " + states + "；扣 " + paid + "；压制过 " + everSealed + "；插件 " + probe.summary();
            boolean pass = allDenied && paid == 0L && !everSealed;
            return new Outcome(pass, "10 次都 NO_PERMISSION、扣 0、不压制；插件只收到 1 条被取消的 BlockBreakEvent", actual, problems);
        }

        // ------------------------------------------------------------------ 权限探测与核心（ProtectionProbeEvent）

        /**
         * C55：权限探测的副作用（policy §2.4）。ECProtection 的探测发 {@link ProtectionProbeEvent}（BreakEvent 的子类），认得出它的核心
         * 只把转出去的 BlockBreakEvent 交给非 MONITOR 的监听器、分发期间吞掉发给这名玩家的提示类包、不写全局破坏事件槽。
         * 插件打开 {@value #PROP_NOTICE}（拒绝时发聊天、动作栏、标题、音效），以收提示的玩家 N（连接会数包）的身份在野外 / owner 区 / other 区各探测一格：
         * <ul>
         *   <li>不分核心：结论 允许 / 拒绝 / 拒绝（和以前一样），HIGH 收到 3 条，插件在 2 次拒绝时都发了提示，记下的 3 条 BlockBreakEvent 玩家、取消状态都对；</li>
         *   <li>要核心认探测：MONITOR 一条都没收到，N 的连接上没有提示类的包。</li>
         * </ul>
         * 核心不认探测（{@link #coreProbeMarker()} 为 null）时，不分核心的那些过了就记成「跳过」，后两条的实际情况照样写进日志。
         */
        private Outcome c55() {
            Outcome stale = requireProbePlugin(1, 5);
            if (stale != null) {
                return stale;
            }
            List<String> problems = new ArrayList<>();
            List<String> coreProblems = new ArrayList<>();
            String marker = coreProbeMarker();
            CountingConnection connection = new CountingConnection();
            ServerPlayer noticed = realPlayer(NOTICE_ID, NOTICE_NAME, connection);
            String[] verdicts = new String[3];
            Map<String, Integer> packets;
            Probe probe;
            try {
                System.setProperty(PROP_NOTICE, "true");
                connection.clear();
                for (int zone = WILD; zone <= OTHERS; zone++) {
                    BlockPos cell = slot(zone, 18).offset(1, 1, 1);
                    set(cell, Blocks.STONE.defaultBlockState());
                    verdicts[zone] = name(ECProtection.check(noticed, level, cell, noticed));
                }
                packets = connection.notices();
                probe = takeProbe();
            } finally {
                System.clearProperty(PROP_NOTICE);
                ECProtection.forget(NOTICE_ID);
            }
            int high = counter(PROP_BREAK_HIGH);
            int monitor = counter(PROP_BREAK_MONITOR);
            int notices = counter(PROP_NOTICES);
            String verdictText = String.join("/", verdicts);
            if (!"允许/拒绝/拒绝".equals(verdictText)) {
                problems.add("探测结论应为 允许/拒绝/拒绝（和以前一样），实际 " + verdictText);
            }
            for (int zone = WILD; zone <= OTHERS; zone++) {
                probe.expect(problems, "BlockBreakEvent", zone, NOTICE_NAME, "CraftPlayer", zone != WILD);
            }
            probe.expectCount(problems, "BlockBreakEvent", 3);
            if (high != 3) {
                problems.add("插件 HIGH 应收到 3 条破坏事件（探测照常交给领地插件判），实际 " + high);
            }
            if (notices != 2) {
                problems.add("插件应在 2 次拒绝时各发一次提示，实际 " + notices);
            }
            if (monitor != 0) {
                coreProblems.add("MONITOR 不应收到探测，实际 " + monitor + " 条");
            }
            if (!packets.isEmpty()) {
                coreProblems.add("探测期间 " + NOTICE_NAME + " 的连接上不应有提示类的包，实际 " + packets);
            }
            String expected = "探测 允许/拒绝/拒绝；HIGH 3 MONITOR 0；插件发提示 2 次、连接上的提示包 {}";
            String actual = "探测 " + verdictText + "；HIGH " + high + " MONITOR " + monitor + "；插件发提示 " + notices + " 次、连接上的提示包 " + packets
                    + "；插件 " + probe.summary();
            return coreGated(marker, expected, actual, problems, coreProblems);
        }

        /**
         * C56：真挖方块不受影响（不分核心，旧核心上同样应通过）。插件打开 {@value #PROP_NOTICE}，N 用 {@code gameMode.destroyBlock} 挖两格泥土
         * （Forge 的 onBlockBreakEvent → 核心转出 BlockBreakEvent → 按全局破坏事件槽决定掉落，和玩家真挖是同一条路）：
         * <ul>
         *   <li>野外那格放行：挖掉、掉 1 份泥土，N 的连接上没有提示类的包；</li>
         *   <li>owner 区那格被拒：泥土还在、不掉，插件发了提示，N 的连接上聊天、动作栏、标题、音效都有；</li>
         *   <li>HIGH、MONITOR 各收到 2 条；记下的 2 条 BlockBreakEvent 一条放行、一条取消。</li>
         * </ul>
         */
        private Outcome c56() {
            Outcome stale = requireProbePlugin(1, 5);
            if (stale != null) {
                return stale;
            }
            List<String> problems = new ArrayList<>();
            CountingConnection connection = new CountingConnection();
            ServerPlayer noticed = realPlayer(NOTICE_ID, NOTICE_NAME, connection);
            survival(noticed);
            BlockPos wildCell = slot(WILD, 19).offset(1, 1, 1);
            BlockPos ownCell = slot(OWN, 19).offset(1, 1, 1);
            set(wildCell, Blocks.DIRT.defaultBlockState());
            set(ownCell, Blocks.DIRT.defaultBlockState());
            boolean wildBroken;
            boolean ownBroken;
            int wildDirt;
            int ownDirt;
            Map<String, Integer> wildPackets;
            Map<String, Integer> ownPackets;
            Probe probe;
            try {
                System.setProperty(PROP_NOTICE, "true");
                connection.clear();
                wildBroken = noticed.gameMode.destroyBlock(wildCell);
                wildPackets = connection.notices();
                wildDirt = takeDrops(wildCell, Items.DIRT);
                connection.clear();
                ownBroken = noticed.gameMode.destroyBlock(ownCell);
                ownPackets = connection.notices();
                ownDirt = takeDrops(ownCell, Items.DIRT);
                probe = takeProbe();
            } finally {
                System.clearProperty(PROP_NOTICE);
                ECProtection.forget(NOTICE_ID);
            }
            int high = counter(PROP_BREAK_HIGH);
            int monitor = counter(PROP_BREAK_MONITOR);
            int notices = counter(PROP_NOTICES);
            if (!wildBroken || !level.getBlockState(wildCell).isAir()) {
                problems.add("野外那格应挖掉：返回 " + wildBroken + "，方块 " + level.getBlockState(wildCell));
            }
            if (wildDirt != 1) {
                problems.add("野外那格应掉 1 份泥土，实际 " + wildDirt);
            }
            if (!wildPackets.isEmpty()) {
                problems.add("野外放行时不应有提示类的包，实际 " + wildPackets);
            }
            if (ownBroken || !level.getBlockState(ownCell).is(Blocks.DIRT)) {
                problems.add("owner 区那格应被拒、泥土还在：返回 " + ownBroken + "，方块 " + level.getBlockState(ownCell));
            }
            if (ownDirt != 0) {
                problems.add("owner 区那格不应掉落，实际 " + ownDirt + " 份泥土");
            }
            for (String kind : NOTICE_KINDS) {
                if (ownPackets.getOrDefault(kind, 0) < 1) {
                    problems.add("被拒的真挖应照常把「" + kind + "」发到 " + NOTICE_NAME + " 的连接上，实际 " + ownPackets);
                }
            }
            if (notices != 1) {
                problems.add("插件应发 1 次提示，实际 " + notices);
            }
            if (high != 2 || monitor != 2) {
                problems.add("真挖的破坏事件 HIGH、MONITOR 应各收到 2 条，实际 HIGH " + high + " MONITOR " + monitor);
            }
            probe.expect(problems, "BlockBreakEvent", WILD, NOTICE_NAME, "CraftPlayer", false);
            probe.expect(problems, "BlockBreakEvent", OWN, NOTICE_NAME, "CraftPlayer", true);
            probe.expectCount(problems, "BlockBreakEvent", 2);
            String actual = "野外 " + (wildBroken ? "挖掉" : "没挖") + " 掉泥土 " + wildDirt + " 提示包 " + wildPackets + "；owner 区 " + (ownBroken ? "挖掉" : "没挖")
                    + " 掉泥土 " + ownDirt + " 提示包 " + ownPackets + "；插件发提示 " + notices + " 次；HIGH " + high + " MONITOR " + monitor + "；插件 " + probe.summary();
            return new Outcome(problems.isEmpty(), "野外 挖掉 掉泥土 1 提示包 {}；owner 区 没挖 掉泥土 0、聊天 / 动作栏 / 标题 / 音效都发到连接上；插件发提示 1 次；HIGH 2 MONITOR 2",
                    actual, problems);
        }

        /**
         * C57：全局破坏事件槽（混合端的 {@code BlockEventDispatcher.blockBreakEvent}）：核心把每次转出去的 BlockBreakEvent 记进这个槽，
         * 玩家真挖完一格时（{@code ServerPlayerGameMode.destroyBlock}）按它的 {@code isDropItems} 决定掉不掉落。N 在野外真挖一格泥土，
         * 途中（Forge 破坏事件分发到最后，LOWEST 监听器里）以 N 的身份探测 owner 区的一格：插件拒绝，并关掉那次事件的掉落。
         * <ul>
         *   <li>不分核心：探测结论「拒绝」，野外那格挖掉了，HIGH 收到 2 条（真挖 + 探测）；</li>
         *   <li>要核心认探测：泥土照常掉 1 份（探测没占槽），MONITOR 只收到真挖那 1 条。旧核心上探测覆盖了槽，这一格不掉落。</li>
         * </ul>
         * EC5 自己不在破坏过程里探测（ECProtection 类注释），这里是故意插进去的，模拟别的模组在玩家挖方块途中触发探测。
         */
        private Outcome c57() {
            Outcome stale = requireProbePlugin(1, 5);
            if (stale != null) {
                return stale;
            }
            List<String> problems = new ArrayList<>();
            List<String> coreProblems = new ArrayList<>();
            String marker = coreProbeMarker();
            CountingConnection connection = new CountingConnection();
            ServerPlayer noticed = realPlayer(NOTICE_ID, NOTICE_NAME, connection);
            survival(noticed);
            BlockPos realCell = slot(WILD, 5).offset(1, 1, 1);
            BlockPos probeCell = slot(OWN, 24).offset(1, 1, 1);
            set(realCell, Blocks.DIRT.defaultBlockState());
            set(probeCell, Blocks.STONE.defaultBlockState());
            String[] nested = new String[1];
            Consumer<BlockEvent.BreakEvent> during = event -> {
                if (nested[0] == null && !(event instanceof ProtectionProbeEvent) && event.getPlayer() == noticed && realCell.equals(event.getPos())) {
                    nested[0] = name(ECProtection.check(noticed, level, probeCell, noticed));
                }
            };
            boolean broken;
            int dirt;
            Probe probe;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, during);
            try {
                System.setProperty(PROP_NOTICE, "true");
                broken = noticed.gameMode.destroyBlock(realCell);
                dirt = takeDrops(realCell, Items.DIRT);
                probe = takeProbe();
            } finally {
                MinecraftForge.EVENT_BUS.unregister(during);
                System.clearProperty(PROP_NOTICE);
                ECProtection.forget(NOTICE_ID);
            }
            int high = counter(PROP_BREAK_HIGH);
            int monitor = counter(PROP_BREAK_MONITOR);
            if (!"拒绝".equals(nested[0])) {
                problems.add("挖的途中对 owner 区那格的探测应为 拒绝，实际 " + nested[0]);
            }
            if (!broken || !level.getBlockState(realCell).isAir()) {
                problems.add("野外那格应挖掉：返回 " + broken + "，方块 " + level.getBlockState(realCell));
            }
            if (high != 2) {
                problems.add("HIGH 应收到 2 条（真挖 + 探测），实际 " + high);
            }
            probe.expect(problems, "BlockBreakEvent", WILD, NOTICE_NAME, "CraftPlayer", false);
            probe.expect(problems, "BlockBreakEvent", OWN, NOTICE_NAME, "CraftPlayer", true);
            if (dirt != 1) {
                coreProblems.add("真挖的那格应照常掉 1 份泥土（探测不该覆盖全局破坏事件槽），实际 " + dirt);
            }
            if (monitor != 1) {
                coreProblems.add("MONITOR 应只收到真挖那 1 条，实际 " + monitor);
            }
            String expected = "途中探测 拒绝；野外 挖掉 掉泥土 1；HIGH 2 MONITOR 1";
            String actual = "途中探测 " + nested[0] + "；野外 " + (broken ? "挖掉" : "没挖") + " 掉泥土 " + dirt + "；HIGH " + high + " MONITOR " + monitor
                    + "；插件 " + probe.summary();
            return coreGated(marker, expected, actual, problems, coreProblems);
        }

        // ------------------------------------------------------------------ 第十二批：源质镐

        /**
         * C58：源质镐的连带挖碰上别人的领地（第十二批，batch12.md §4.4.8、§9.4；不用新版测试插件）。主人背包清空、主手拿满电的源质镐，
         * 在 other 区东边界上竖一面 3×3 石墙：z = 5、y = 5~7，x = 49 那一列在 other 区里，x = 50、51 在区间空隙里（C19 的门在 z = 9、y = 1~5，碰不到）。
         * 对墙的北面左键（服务端收到「开始挖」时发的 Forge 左键事件）、真挖中间那格（x = 50），再直接调 {@code EssentialToolEvents} 的服务端 tick 处理器
         * （同 C27：和世界每刻 END 调的是同一个方法，只是在这一组的同步代码里调）。连带挖的每一格都是主人本人的 {@code gameMode.destroyBlock}。
         * 期望：空隙里 6 格挖掉、掉 6 个圆石，other 区那 3 格还在、那一列没有掉落物；一共只扣 6 格的钱；插件收到主人的 9 条 BlockBreakEvent，
         * other 区 3 条已取消、空隙里（region=none）6 条放行，被拒的格子只问一次。跑完把主人的背包清空、左键记录清掉。
         */
        private Outcome c58() {
            List<String> problems = new ArrayList<>();
            BlockPos center = zoneBase(OTHERS).offset(ZONE_SIZE, 6, 5);   // x = 50：other 区东边的区间空隙
            List<BlockPos> wall = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    wall.add(center.offset(dx, dy, 0));
                }
            }
            for (BlockPos pos : wall) {
                set(pos, Blocks.STONE.defaultBlockState());
            }
            ItemStack pickaxe = EssentialTool.full(ECEssentialTools.ESSENTIAL_PICKAXE.get());
            int cost = ECEssentialTools.Config.values().mruPerBlock();
            Vec3 home = owner.position();
            survival(owner);
            owner.setHealth(owner.getMaxHealth());
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            // 站在墙北边两格、平视墙中间：左键记录万一没记上，视线射线打中的也是北面
            owner.moveTo(center.getX() + 0.5D, center.getY() + 0.5D - owner.getEyeHeight(), center.getZ() - 1.5D, 0F, 0F);
            int toolBefore = EssentialTool.storedMRU(pickaxe);
            long storagesBefore = MRUDrain.availableFromStorages(owner);
            boolean mined;
            int toolAfter;
            long storagesAfter;
            int cobble;
            int cobbleInClaim;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickBlock(owner, center, Direction.NORTH,
                        PlayerInteractEvent.LeftClickBlock.Action.START));
                mined = owner.gameMode.destroyBlock(center);
                EssentialToolEvents.essentialToolServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
                toolAfter = EssentialTool.storedMRU(pickaxe);
                storagesAfter = MRUDrain.availableFromStorages(owner);
                List<Entity> drops = spawns.alive();
                cobble = countItems(drops, Items.COBBLESTONE);
                // other 区那一列是 x ∈ [49, 50)；空隙里的格子掉落物生成在格子中间 ± 0.25，落不进这一列
                List<Entity> inClaim = new ArrayList<>();
                for (Entity drop : drops) {
                    if (drop.getX() < center.getX()) {
                        inClaim.add(drop);
                    }
                }
                cobbleInClaim = countItems(inClaim, null);
                spawns.discardAll();
            } finally {
                EssentialToolEvents.forgetForTest(OWNER_ID);
                owner.getInventory().clearContent();
                owner.moveTo(home.x, home.y, home.z, 0F, 0F);
            }
            int broken = 0;
            int keptInClaim = 0;
            for (BlockPos pos : wall) {
                boolean air = level.getBlockState(pos).isAir();
                if (pos.getX() < center.getX()) {
                    keptInClaim += level.getBlockState(pos).is(Blocks.STONE) ? 1 : 0;
                } else {
                    broken += air ? 1 : 0;
                }
            }
            if (storagesBefore == Long.MAX_VALUE) {
                problems.add("主人身上有无尽的储存物品（或是创造模式），扣钱数不出来");
            }
            long spent = (toolBefore - toolAfter) + (storagesBefore - storagesAfter);
            int allowed = 0;
            int denied = 0;
            Probe probe = Probe.read();
            for (Map<String, String> event : probe.all()) {
                if (!"BlockBreakEvent".equals(event.get("type")) || !OWNER_NAME.equals(event.get("playerName"))) {
                    continue;
                }
                if ("other".equals(event.get("region")) && "true".equals(event.get("cancelled"))) {
                    denied++;
                } else if ("none".equals(event.get("region")) && "false".equals(event.get("cancelled"))) {
                    allowed++;
                } else {
                    problems.add("对不上的破坏事件：" + event.get("line"));
                }
            }
            probe.expectCount(problems, "BlockBreakEvent", 9);
            String actual = "中间那格 " + (mined ? "挖掉" : "没挖") + "；空隙里挖掉 " + broken + "/6，other 区还在 " + keptInClaim + "/3；圆石 " + cobble
                    + "（other 区那一列 " + cobbleInClaim + "）；扣 MRU " + spent + "；插件 放行 " + allowed + " 条、取消 " + denied + " 条：" + probe.summary();
            boolean pass = mined && broken == 6 && keptInClaim == 3 && cobble == 6 && cobbleInClaim == 0 && spent == 6L * cost
                    && allowed == 6 && denied == 3;
            return new Outcome(pass, "中间那格 挖掉；空隙里挖掉 6/6，other 区还在 3/3；圆石 6（other 区那一列 0）；扣 MRU " + 6 * cost
                    + "；插件 放行 6 条、取消 3 条", actual, problems);
        }

        // ------------------------------------------------------------------ 第十二批：归途罗盘

        /**
         * C59：归途罗盘的落点在别人领地里（第十二批，batch12.md §4.5.5、§9.4；不用新版测试插件）。归途点记在 other 区中间
         * （x = 45、z = 5，一块石头，上面两格空气；记点时的权限检查是绑定那一步的事，这里直接写进罗盘 NBT），主人背包清空、主手拿这个罗盘、
         * 物品栏里放一份满的红色灵魂物质付钱，原地起手，再直接调 {@code HomewardChannels.finish}（引导满了时罗盘调的就是它）。
         * 落点走 {@code ECTeleport.check}：以主人身份探测落脚的那几格，插件拒绝。
         * 期望：返回 FAILED、人不动、不扣 MRU、不进冷却、不留在引导表 / 重试表里；插件收到的主人破坏事件全在 other 区、全部已取消（至少一条）。
         * 跑完清掉罗盘的引导和战斗记录、主人的背包。
         */
        private Outcome c59() {
            List<String> problems = new ArrayList<>();
            BlockPos home = zoneBase(OTHERS).offset(5, 1, 5);
            set(home, Blocks.STONE.defaultBlockState());
            set(home.above(), Blocks.AIR.defaultBlockState());
            set(home.above(2), Blocks.AIR.defaultBlockState());
            survival(owner);
            owner.setHealth(owner.getMaxHealth());
            owner.getInventory().clearContent();
            HomewardChannels.forgetForTest(owner);
            ItemStack compass = new ItemStack(ECHomewardCompass.HOMEWARD_COMPASS.get());
            HomewardCompassItem.setHome(compass, level.dimension(), home);
            owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
            owner.getInventory().setItem(1, MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get()));
            Vec3 start = owner.position();
            long storagesBefore = MRUDrain.availableFromStorages(owner);
            InteractionResult started;
            HomewardChannels.Outcome outcome = null;
            boolean moved;
            long storagesAfter;
            boolean cooling;
            boolean lingering;
            try {
                started = compass.use(level, owner, InteractionHand.MAIN_HAND).getResult();
                if (started == InteractionResult.CONSUME) {
                    outcome = HomewardChannels.finish(owner);
                }
                owner.stopUsingItem();
                moved = owner.level() != level || owner.position().distanceToSqr(start) > 1.0E-6D;
                storagesAfter = MRUDrain.availableFromStorages(owner);
                cooling = HomewardCompassItem.coolingDown(owner);
                lingering = HomewardChannels.busy(owner) || HomewardChannels.waitingForTest(owner);
            } finally {
                HomewardChannels.forgetForTest(owner);
                owner.getInventory().clearContent();
                if (owner.level() == level) {
                    owner.moveTo(start.x, start.y, start.z, 0F, 0F);
                }
            }
            if (storagesBefore == Long.MAX_VALUE) {
                problems.add("主人身上有无尽的储存物品（或是创造模式），扣钱数不出来");
            }
            int denied = 0;
            Probe probe = Probe.read();
            for (Map<String, String> event : probe.all()) {
                if (!"BlockBreakEvent".equals(event.get("type")) || !OWNER_NAME.equals(event.get("playerName"))) {
                    continue;
                }
                if ("other".equals(event.get("region")) && "true".equals(event.get("cancelled"))) {
                    denied++;
                } else {
                    problems.add("对不上的破坏事件：" + event.get("line"));
                }
            }
            long spent = storagesBefore - storagesAfter;
            String actual = "起手 " + started + "；结果 " + outcome + "；" + (moved ? "人动了（" + owner.position() + "）" : "人没动") + "；扣 MRU " + spent
                    + "；" + (cooling ? "进了冷却" : "没进冷却") + "；" + (lingering ? "还在引导 / 重试表里" : "不在引导 / 重试表里")
                    + "；插件 取消 " + denied + " 条：" + probe.summary();
            boolean pass = started == InteractionResult.CONSUME && outcome == HomewardChannels.Outcome.FAILED && !moved && spent == 0L
                    && !cooling && !lingering && denied >= 1 && problems.isEmpty();
            return new Outcome(pass, "起手 CONSUME；结果 FAILED；人没动；扣 MRU 0；没进冷却；不在引导 / 重试表里；插件 取消 ≥ 1 条", actual, problems);
        }

        // ------------------------------------------------------------------ 第十三批 13a：矩阵试炼、阶门、相性与特性

        /**
         * C60：矩阵试炼的回响生成点碰上领地（第十三批 13a，batch13.md §3.1.2、§12.4；不用新版测试插件）。阶数据用测试覆盖（一阶：不要图谱和祭品，
         * UBMRU {@value #TRIAL_UBMRU}、台子 MRU {@value #TRIAL_MRU}，回响 2 只、同时 1 只），生成半径临时改成 2~3 格，回响不带 AI，难度当成不是和平。
         * <ol>
         *   <li>台子摆在 other 区正中（四周铺一圈石头当地面）：周围能站的格子以主人身份探测全被插件拒 → 开不起来（NO_ROOM），
         *       什么都不扣、台子没锁、没有回响、不冷却；插件收到的主人破坏事件全在 other 区、全部已取消（至少一条）。</li>
         *   <li>台子摆在野外正中：开得起来、放出第一只回响、扣了能量。然后删掉这只回响，临时把插件原点往西挪两块区（野外那块就成了 other 区），
         *       忘掉主人的探测缓存，再以主人身份把台子周围 7 × 7 的格子先问一遍（都被拒，记进缓存）；接着同步推进试炼
         *       （{@code MatrixTrials.tickAll}，和每刻末调的是同一个入口）：一只都放不出来、卡满 {@code STALL_TICKS} 刻后中止，
         *       能量退回（UBMRU、台子 MRU 回到开始前）、不冷却、台子解锁。先问一遍是因为同一刻里每个调用方只能新探测 16 格，
         *       同步推进时不预先问好，后面的格子都是「还不知道」，不算卡住。</li>
         * </ol>
         * 跑完放回插件原点、主人的矩阵数据和手上的东西，拆掉台子和地面，撤掉各种测试覆盖。
         */
        private Outcome c60() {
            List<String> problems = new ArrayList<>();
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            int rankBefore = data.getMatrixRank();
            int ubmruBefore = data.getUBMRU();
            int reserveBefore = data.getMatrixTrialReserve();
            ItemStack handBefore = owner.getMainHandItem().copy();
            Vec3 home = owner.position();
            String originBefore = System.getProperty(PROP_ORIGIN);
            List<BlockPos> placed = new ArrayList<>();
            String denied;
            String aborted;
            boolean deniedPass;
            boolean abortPass;
            try {
                MatrixRankData.setTestOverride(Map.of(1, new MatrixRankData.Entry(1, MatrixRankData.Codex.NONE, false, -1, List.of(),
                        TRIAL_UBMRU, TRIAL_MRU, 0, new MatrixRankData.Echoes(2, 1, 4, 0, 60))));
                MatrixRank.setTestOverrides(true, null, null, null);
                MatrixRank.setTestPeaceful(false);
                MatrixTrials.setTestOverrides(new int[]{2, 3}, null, null, false, true);
                ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
                SoulStoneItem.bind(stone, owner);
                owner.setItemInHand(InteractionHand.MAIN_HAND, stone);

                // ① 台子在 other 区
                MatrixCrucibleBlockEntity claimed = trialRig(zoneBase(OTHERS).offset(5, 2, 5), placed, data);
                if (claimed == null) {
                    return new Outcome(false, "摆出淬炼台", "other 区那格没有淬炼台的方块实体", problems);
                }
                System.setProperty(PROP_EVENTS, "");
                MatrixTrials.Start start = MatrixTrials.tryStart(owner, claimed);
                Probe deniedProbe = Probe.read();
                int echoesThere = echoesNear(claimed.getBlockPos()).size();
                boolean noTrial = MatrixTrials.trialOf(OWNER_ID) == null;
                int ubmruThere = data.getUBMRU() + data.getMatrixTrialReserve();
                int mruThere = claimed.getMRUStorage().getMRU();
                boolean lockedThere = claimed.lockedBy() != null;
                int cooldownThere = data.getMatrixTrialCooldown();
                int[] breaks = ownerBreaks(deniedProbe, problems);
                denied = start.result() + "；" + (noTrial ? "没有试炼" : "有试炼") + "；回响 " + echoesThere + "；UBMRU " + ubmruThere
                        + " MRU " + mruThere + "；" + (lockedThere ? "台子锁着" : "台子没锁") + "；冷却 " + cooldownThere
                        + "；插件 other 区取消 " + breaks[0] + " 条、别的 " + breaks[1] + " 条";
                deniedPass = start.result() == MatrixTrials.StartResult.NO_ROOM && noTrial && echoesThere == 0
                        && ubmruThere == TRIAL_UBMRU_START && mruThere == TRIAL_MRU_START && !lockedThere && cooldownThere == 0
                        && breaks[0] >= 1 && breaks[1] == 0;

                // ② 台子在野外：开起来，再让周围变成别人的领地
                MatrixCrucibleBlockEntity wild = trialRig(zoneBase(WILD).offset(5, 2, 5), placed, data);
                if (wild == null) {
                    return new Outcome(false, "摆出淬炼台", "野外那格没有淬炼台的方块实体", problems);
                }
                System.setProperty(PROP_EVENTS, "");
                MatrixTrials.Start wildStart = MatrixTrials.tryStart(owner, wild);
                List<MatrixEchoEntity> first = echoesNear(wild.getBlockPos());
                int ubmruPaid = TRIAL_UBMRU_START - (data.getUBMRU() + data.getMatrixTrialReserve());
                int mruPaid = TRIAL_MRU_START - wild.getMRUStorage().getMRU();
                for (MatrixEchoEntity echo : first) {
                    echo.discard();
                }
                System.setProperty(PROP_ORIGIN, (origin.getX() - OTHERS * ZONE_STEP) + "," + origin.getY() + "," + origin.getZ());
                ECProtection.forget(OWNER_ID);
                ECProtection.Config.setMaxProbesPerPlayerTickForTest(1024);
                BlockPos center = wild.getBlockPos();
                int asked = 0;
                int refused = 0;
                Object caller = new Object();
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        if (asked > 0 && asked % 16 == 0) {
                            caller = new Object();   // 每个调用方每刻只能新探测 16 格
                        }
                        asked++;
                        if (ECProtection.check(owner, level, center.offset(dx, 0, dz), caller) == ECProtection.Verdict.DENY) {
                            refused++;
                        }
                    }
                }
                System.setProperty(PROP_EVENTS, "");
                int ticks = 0;
                int limit = MatrixTrials.STALL_TICKS + MatrixTrials.SPAWN_INTERVAL + 40;
                while (MatrixTrials.trialOf(OWNER_ID) != null && ticks < limit) {
                    MatrixTrials.tickAll(server);
                    ticks++;
                }
                Probe abortProbe = Probe.read();
                boolean ended = MatrixTrials.trialOf(OWNER_ID) == null;
                int ubmruBack = data.getUBMRU() + data.getMatrixTrialReserve();
                int mruBack = wild.getMRUStorage().getMRU();
                boolean lockedBack = wild.lockedBy() != null;
                int cooldownBack = data.getMatrixTrialCooldown();
                int echoesBack = echoesNear(center).size();
                int[] stalledBreaks = ownerBreaks(abortProbe, problems);
                aborted = wildStart.result() + "，放出 " + first.size() + " 只、扣 UBMRU " + ubmruPaid + " MRU " + mruPaid
                        + "；先问 " + asked + " 格、被拒 " + refused + "；推进 " + ticks + " 刻" + (ended ? "后中止" : "还没结束")
                        + "；UBMRU " + ubmruBack + " MRU " + mruBack + "；" + (lockedBack ? "台子锁着" : "台子没锁") + "；冷却 " + cooldownBack
                        + "；回响 " + echoesBack + "；推进时插件新收到 other 区取消 " + stalledBreaks[0] + " 条、别的 " + stalledBreaks[1] + " 条";
                abortPass = wildStart.result() == MatrixTrials.StartResult.STARTED && first.size() == 1 && ubmruPaid == TRIAL_UBMRU
                        && mruPaid == TRIAL_MRU && refused == asked && ended && ticks >= MatrixTrials.STALL_TICKS
                        && ubmruBack == TRIAL_UBMRU_START && mruBack == TRIAL_MRU_START && !lockedBack && cooldownBack == 0 && echoesBack == 0
                        && stalledBreaks[1] == 0;
            } finally {
                if (originBefore != null) {
                    System.setProperty(PROP_ORIGIN, originBefore);
                } else {
                    System.clearProperty(PROP_ORIGIN);
                }
                MatrixTrials.clearForTest(OWNER_ID);
                MatrixTrials.clearTestOverrides();
                MatrixRankData.setTestOverride(null);
                MatrixRank.clearTestOverrides();
                ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
                ECProtection.forget(OWNER_ID);
                data.setMatrixRank(rankBefore);
                data.setUBMRU(ubmruBefore);
                data.setMatrixTrialReserve(reserveBefore);
                data.setMatrixTrialCooldown(0);
                owner.setItemInHand(InteractionHand.MAIN_HAND, handBefore);
                owner.moveTo(home.x, home.y, home.z, 0F, 0F);
                for (BlockPos pos : placed) {
                    set(pos, Blocks.AIR.defaultBlockState());
                }
            }
            String actual = "other 区 " + denied + "；野外 " + aborted;
            return new Outcome(deniedPass && abortPass,
                    "other 区 NO_ROOM；没有试炼；回响 0；UBMRU " + TRIAL_UBMRU_START + " MRU " + TRIAL_MRU_START + "；台子没锁；冷却 0；插件 other 区取消 ≥ 1 条、别的 0 条"
                            + "；野外 STARTED，放出 1 只、扣 UBMRU " + TRIAL_UBMRU + " MRU " + TRIAL_MRU + "；先问的格子全被拒；推进 ≥ "
                            + MatrixTrials.STALL_TICKS + " 刻后中止；UBMRU " + TRIAL_UBMRU_START + " MRU " + TRIAL_MRU_START + "；台子没锁；冷却 0；回响 0",
                    actual, problems);
        }

        /**
         * C60：在 at 摆一台淬炼台（脚下和四周 4 格内铺石头地面、上面清出三格空气），台子 MRU {@value #TRIAL_MRU_START}；
         * 主人零阶、UBMRU {@value #TRIAL_UBMRU_START}、没有试炼储备和冷却，站到台子南边 2 格。摆过的格子记进 placed，跑完拆掉
         */
        @Nullable
        private MatrixCrucibleBlockEntity trialRig(BlockPos at, List<BlockPos> placed, IPlayerData data) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos floor = at.offset(dx, -1, dz);
                    set(floor, Blocks.STONE.defaultBlockState());
                    placed.add(floor);
                    for (int dy = 0; dy <= 2; dy++) {
                        set(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            set(at, ECMatrix.MATRIX_CRUCIBLE.get().defaultBlockState());
            placed.add(at);
            if (!(level.getBlockEntity(at) instanceof MatrixCrucibleBlockEntity be)) {
                return null;
            }
            be.getMRUStorage().setMRU(TRIAL_MRU_START);
            data.setMatrixRank(0);
            data.setUBMRU(TRIAL_UBMRU_START);
            data.setMatrixTrialReserve(0);
            data.setMatrixTrialCooldown(0);
            owner.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 2.5D, 180F, 0F);
            owner.setHealth(owner.getMaxHealth());
            return be;
        }

        /** C60：台子 8 格内的回响（活着的） */
        private List<MatrixEchoEntity> echoesNear(BlockPos crucible) {
            return level.getEntitiesOfClass(MatrixEchoEntity.class, new AABB(crucible).inflate(8D), echo -> echo.isAlive());
        }

        /** C60：插件收到的主人破坏事件 {在 other 区、已取消的条数, 别的条数} */
        private int[] ownerBreaks(Probe probe, List<String> problems) {
            int denied = 0;
            int others = 0;
            for (Map<String, String> event : probe.all()) {
                if (!"BlockBreakEvent".equals(event.get("type")) || !OWNER_NAME.equals(event.get("playerName"))) {
                    continue;
                }
                if ("other".equals(event.get("region")) && "true".equals(event.get("cancelled"))) {
                    denied++;
                } else {
                    others++;
                    problems.add("对不上的破坏事件：" + event.get("line"));
                }
            }
            return new int[]{denied, others};
        }

        /**
         * C61：矩阵阶的门（第十三批 13a，batch13.md §2.5、§12.4；不用新版测试插件）。门槛用测试覆盖固定成五阶。
         * <ol>
         *   <li>海纳尔之门：单独建一名旅行者（连接会数包），四阶，直接调过门的入口 {@code HoannaTeleport.travel}（在门里站满等待时间时调的就是它）：
         *       返回 false、人还在主世界、动作栏一条、插件没收到他的传送事件；接着直接调 {@code ServerPlayer.teleportTo} 往海纳尔传
         *       （别的模组、指令走的路，由兜底的门 {@code gates.block_all_entries} 挡），只记录挡没挡住，不算进通过与否；</li>
         *   <li>魔力传送器（同 C28 摆在 WILD 和 OWN 之间的空隙，落点是海纳尔同坐标附近 y = 原点 + 19 的一块石头）：乘客四阶 → 状态 RANK_TOO_LOW、
         *       人不动、MRU 不扣、动作栏一条、插件没收到他的传送事件；乘客五阶 → 到了海纳尔；</li>
         *   <li>从海纳尔回来不挡：乘客在海纳尔、矩阵阶改回零阶，调 {@code HoannaTeleport.travel}：回到主世界（回程只找门不建门，找不到就回重生点）。</li>
         * </ol>
         * 海纳尔那一片不是空气（地形太高）、混合端没把脱离网络的玩家传过去时，后两步只记录。
         * 跑完把两名玩家从所在世界移除，拆掉结构和落点，海纳尔那几格还原、取消强制加载。
         */
        private Outcome c61() {
            List<String> problems = new ArrayList<>();
            ServerLevel hoanna = ECHoanna.level(server);
            if (hoanna == null) {
                return new Outcome(null, "有海纳尔世界", "没有（维度被数据包删了？），跳过", problems);
            }
            if (!ECHoannaPortal.Config.portalEnabledIn(level)) {
                return new Outcome(null, "海纳尔之门开着", "服主关了，跳过", problems);
            }
            CountingConnection travellerConnection = new CountingConnection();
            ServerPlayer traveller = realPlayer(TRAVELLER_ID, TRAVELLER_NAME, travellerConnection);
            CountingConnection porterConnection = new CountingConnection();
            ServerPlayer porter = realPlayer(PORTER_ID, PORTER_NAME, porterConnection);
            BlockPos machine = zoneBase(WILD).offset(ZONE_SIZE + 4, 1, 4);   // 同 C28：WILD 和 OWN 之间的空隙
            BlockPos hoannaFloor = new BlockPos(machine.getX(), origin.getY() + 19, machine.getZ() + 3);
            List<BlockPos> placed = new ArrayList<>();
            Map<BlockPos, BlockState> hoannaSaved = new LinkedHashMap<>();
            ChunkPos hoannaChunk = new ChunkPos(hoannaFloor);
            boolean hoannaForced = false;
            String portal;
            String bypass;
            String low;
            String high;
            String back;
            boolean portalPass;
            boolean lowPass;
            Boolean highPass;
            Boolean backPass;
            try {
                MatrixRank.setTestOverrides(true, 5, null, null);
                ECProtection.forget(TRAVELLER_ID);
                ECProtection.forget(PORTER_ID);

                // ① 海纳尔之门，四阶
                MatrixRank.set(traveller, 4);
                System.setProperty(PROP_EVENTS, "");
                travellerConnection.clear();
                boolean travelled = HoannaTeleport.travel(traveller, traveller.blockPosition());
                Probe portalProbe = Probe.read();
                boolean stayed = traveller.level() == level;
                int bars = travellerConnection.notices().getOrDefault("动作栏", 0);
                int teleports = teleportEvents(portalProbe, TRAVELLER_NAME);
                portal = (travelled ? "传了" : "没传") + "、" + (stayed ? "还在主世界" : "在 " + traveller.level().dimension().location())
                        + "、动作栏 " + bars + "、传送事件 " + teleports;
                portalPass = !travelled && stayed && bars == 1 && teleports == 0;

                // ①′ 兜底的门（gates.block_all_entries）：别的模组、指令走 ServerPlayer.teleportTo，纯 Forge 会发 EntityTravelToDimensionEvent；
                // 混合端改过的传送路径发不发这个事件不确定，这一步只记录（挡没挡、有没有动作栏），不算进通过与否
                if (stayed) {
                    MatrixRank.setTestBlockAllEntries(true);
                    travellerConnection.clear();
                    traveller.teleportTo(hoanna, traveller.getX(), 200D, traveller.getZ(), 0F, 0F);
                    boolean held = traveller.level() == level;
                    bypass = (held ? "被挡、还在主世界" : "没挡住、到了 " + traveller.level().dimension().location())
                            + "、动作栏 " + travellerConnection.notices().getOrDefault("动作栏", 0);
                } else {
                    bypass = "跳过：旅行者已经不在主世界";
                }

                // ② 魔力传送器
                for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECVoidMachines.TELEPORTER_STRUCTURE.rules().entrySet()) {
                    if (!entry.getValue().isOptional()) {
                        BlockPos pos = machine.offset(entry.getKey());
                        set(pos, entry.getValue().displayState());
                        placed.add(pos);
                    }
                }
                if (!(level.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be)) {
                    return new Outcome(false, "摆出魔力传送器", "机器那格没有方块实体", problems);
                }
                be.structure().checkNow(level, machine, Direction.NORTH);
                if (!be.structure().formed()) {
                    return new Outcome(false, "结构成形", "不成形，差 " + be.mismatchOffset(), problems);
                }
                be.setTestOverrides(null, null, 3, true, false);
                hoannaForced = !hoanna.getForcedChunks().contains(hoannaChunk.toLong());
                if (hoannaForced) {
                    hoanna.setChunkForced(hoannaChunk.x, hoannaChunk.z, true);
                }
                hoanna.getChunk(hoannaChunk.x, hoannaChunk.z);
                String terrain = null;
                for (int dy = 0; dy <= 2 && terrain == null; dy++) {
                    BlockPos pos = hoannaFloor.above(dy);
                    if (!hoanna.getBlockState(pos).isAir()) {
                        terrain = "海纳尔 " + pos.toShortString() + " 是 " + hoanna.getBlockState(pos) + "（地形太高）";
                    }
                }
                if (terrain == null) {
                    hoannaSaved.put(hoannaFloor, hoanna.getBlockState(hoannaFloor));
                    hoanna.setBlock(hoannaFloor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
                be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, boundGem(GlobalPos.of(ECHoanna.LEVEL, hoannaFloor)));
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                int mru0 = be.getMRUStorage().getMRU();
                MatrixRank.set(porter, 4);
                System.setProperty(PROP_EVENTS, "");
                porterConnection.clear();
                standOnTeleporter(porter, be);
                tickTeleporter(be, 6);
                Probe lowProbe = Probe.read();
                boolean porterStayed = porter.level() == level && porter.blockPosition().equals(machine.above());
                int spentLow = mru0 - be.getMRUStorage().getMRU();
                int lowBars = porterConnection.notices().getOrDefault("动作栏", 0);
                int lowTeleports = teleportEvents(lowProbe, PORTER_NAME);
                low = "状态 " + be.status() + "、" + (porterStayed ? "人没动" : "人动了（" + porter.position() + "）") + "、扣 " + spentLow
                        + "、动作栏 " + lowBars + "、传送事件 " + lowTeleports;
                lowPass = be.status() == MagicalTeleporterBlockEntity.Status.RANK_TOO_LOW && porterStayed && spentLow == 0 && lowBars == 1 && lowTeleports == 0;

                // 五阶：传过去
                if (terrain != null) {
                    high = "跳过：" + terrain;
                    highPass = null;
                    back = "跳过：没到海纳尔";
                    backPass = null;
                } else {
                    porter.moveTo(machine.getX() + 0.5D, machine.getY() + 4D, machine.getZ() + 0.5D, 0F, 0F);
                    tickTeleporter(be, 1);
                    MatrixRank.set(porter, 5);
                    be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                    standOnTeleporter(porter, be);
                    tickTeleporter(be, 6);
                    boolean there = porter.level() == hoanna;
                    if (!there) {
                        high = "没到（在 " + porter.level().dimension().location() + "，状态 " + be.status() + "），混合端可能不传送脱离网络的玩家，只记录";
                        highPass = be.status() == MagicalTeleporterBlockEntity.Status.RANK_TOO_LOW ? Boolean.FALSE : null;
                        back = "跳过：没到海纳尔";
                        backPass = null;
                    } else {
                        high = "到了海纳尔";
                        highPass = true;
                        // ③ 从海纳尔回来：零阶也不挡
                        MatrixRank.set(porter, 0);
                        porter.setPortalCooldown(0);
                        porterConnection.clear();
                        boolean returned = HoannaTeleport.travel(porter, porter.blockPosition());
                        boolean home = porter.level() == level;
                        Integer gateBars = porterConnection.notices().get("动作栏");
                        back = (returned ? "传了" : "没传") + "、" + (home ? "回到主世界" : "在 " + porter.level().dimension().location())
                                + (gateBars == null ? "" : "、动作栏 " + gateBars);
                        backPass = returned && home;
                    }
                }
            } finally {
                MatrixRank.clearTestOverrides();
                MagicalTeleporterBlockEntity.setTestOccupant(level, machine, null);
                for (ServerPlayer player : List.of(traveller, porter)) {
                    if (player.serverLevel().players().contains(player)) {
                        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                    }
                }
                ECProtection.forget(TRAVELLER_ID);
                ECProtection.forget(PORTER_ID);
                for (BlockPos pos : placed) {
                    set(pos, Blocks.AIR.defaultBlockState());
                }
                for (Map.Entry<BlockPos, BlockState> entry : hoannaSaved.entrySet()) {
                    hoanna.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                if (hoannaForced) {
                    hoanna.setChunkForced(hoannaChunk.x, hoannaChunk.z, false);
                }
            }
            if (hoanna.players().contains(porter) || level.players().contains(porter)) {
                problems.add("乘客还留在世界的玩家表里");
            }
            String actual = "门 " + portal + "；兜底（只记录）" + bypass + "；传送器四阶 " + low + "；五阶 " + high + "；回程 " + back;
            boolean pass = portalPass && lowPass && (highPass == null || highPass) && (backPass == null || backPass);
            return new Outcome(pass, "门 没传、还在主世界、动作栏 1、传送事件 0；传送器四阶 状态 RANK_TOO_LOW、人没动、扣 0、动作栏 1、传送事件 0；"
                    + "五阶 到了海纳尔；回程 传了、回到主世界", actual, problems);
        }

        /** C61：插件收到的某名玩家的 PlayerTeleportEvent 条数 */
        private int teleportEvents(Probe probe, String playerName) {
            int n = 0;
            for (Map<String, String> event : probe.all()) {
                if ("PlayerTeleportEvent".equals(event.get("type")) && playerName.equals(event.get("playerName"))) {
                    n++;
                }
            }
            return n;
        }

        /**
         * C62：相性与矩阵特性打玩家（第十三批 13a，batch13.md §3.2、§12.4；不用新版测试插件）。同 C39 用反射临时打开主世界的 {@code pvpMode}，
         * 另一名玩家九阶混沌（相性 III）、主手神圣重锤（特性 ×{@code holy_mace_trait_multiplier}）、矩阵容量 1000 MRU，打主人 2 点
         * （{@code hurt}，伤害源 playerAttack），立刻结算（{@code MatrixTraits.flush}，正式是本刻末）。腐蚀的基础时长用测试覆盖固定成 60 刻。
         * <ol>
         *   <li>主人在 owner 区（插件取消 EntityDamageByEntityEvent）→ 主人不掉血、没有腐蚀、矩阵容量不扣；</li>
         *   <li>主人在野外 → 腐蚀 60 × 相性倍数 × 重锤倍数 × {@code pvp_effect_scale} 刻（默认 60 × 1.5 × 2 × 0.5 = 90 刻，打生物是 180 刻）、
         *       矩阵容量扣一次特性的钱（{@code trait_mru_cost}，混沌没有折扣）；</li>
         *   <li>叠满的总乘数（batch13.md §12.4）：换元素剑、主人挂着腐蚀，攻击者先中性打 4 点当基准、再九阶混沌打同样的 4 点，
         *       掉血之比 = (1 + 躁动 III × s) × (1 + 特性增伤 × s)，s = {@code pvp_effect_scale}（默认 1.075 × 1.05 ≈ 1.129）。</li>
         * </ol>
         * 跑完放回 pvpMode、两人的矩阵数据，拿走重锤，清效果、满血。
         */
        private Outcome c62() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            IPlayerData attacker = PlayerDataEvents.get(other);
            IPlayerData victim = PlayerDataEvents.get(owner);
            if (attacker == null || victim == null) {
                return new Outcome(false, "两名玩家都有玩家数据", "缺", problems);
            }
            int rankBefore = attacker.getMatrixRank();
            int typeBefore = attacker.getMatrixType();
            int poolBefore = attacker.getMatrixPool();
            int matrixDamageBefore = victim.getMatrixDamage();
            BlockPos inClaim = slot(OWN, 24).offset(1, 1, 1);
            BlockPos wild = slot(WILD, 24).offset(1, 1, 1);
            ItemStack mace = new ItemStack(ECStaffs.HOLY_MACE.get());
            int level3 = MatrixResonance.level(MatrixResonance.RANK_III, MatrixResonance.CHAOS);
            double onPlayer = MatrixTraits.strength(level3, mace, owner);
            double onMob = MatrixTraits.strength(level3, mace, null);
            int expectedTicks = (int) Math.round(60 * onPlayer);
            int cost = MatrixResonanceConfig.traitMruCost();
            boolean pvpBefore = false;
            String denied;
            String open;
            String stacked;
            double stackRatioExpected;
            boolean pass;
            Probe deniedProbe;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                MatrixRank.setTestOverrides(true, null, null, null);
                com.flameforge.essentialcraft5.gun.LensEffects.Config.setTestOverrides(60, 15, true);
                attacker.setMatrixRank(MatrixResonance.RANK_III);
                attacker.setMatrixType(MatrixResonance.CHAOS);
                attacker.setMatrixPool(1000, false);
                other.setItemInHand(InteractionHand.MAIN_HAND, mace);
                MatrixTraits.flush();

                standAt(owner, inClaim);
                owner.removeAllEffects();
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                float before = owner.getHealth();
                owner.hurt(level.damageSources().playerAttack(other), 2F);
                MatrixTraits.flush();
                float deniedLoss = before - owner.getHealth();
                boolean deniedCorroded = owner.hasEffect(com.flameforge.essentialcraft5.registry.ECEffects.CORROSION.get());
                int deniedPool = attacker.getMatrixPool();
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");

                standAt(owner, wild);
                owner.removeAllEffects();
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                before = owner.getHealth();
                owner.hurt(level.damageSources().playerAttack(other), 2F);
                MatrixTraits.flush();
                float openLoss = before - owner.getHealth();
                net.minecraft.world.effect.MobEffectInstance corrosion =
                        owner.getEffect(com.flameforge.essentialcraft5.registry.ECEffects.CORROSION.get());
                int openTicks = corrosion == null ? 0 : corrosion.getDuration();
                int openPool = attacker.getMatrixPool();
                System.setProperty(PROP_EVENTS, "");

                // ③ 叠满的总乘数（batch13.md §3.2 / §12.4）：主手换元素剑（武器倍数 1、没有亡灵加成），主人挂着腐蚀；
                // 先让攻击者是中性打一下当基准，再九阶混沌打同样的一下，比两次掉血
                other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.flameforge.essentialcraft5.registry.ECGear.ELEMENTAL_SWORD.get()));
                owner.removeAllEffects();
                owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.flameforge.essentialcraft5.registry.ECEffects.CORROSION.get(), 400, 0));
                attacker.setMatrixType(MatrixResonance.NEUTRAL);
                float baseLoss = hurtFresh(owner, level.damageSources().playerAttack(other), 4F);
                attacker.setMatrixType(MatrixResonance.CHAOS);
                float stackedLoss = hurtFresh(owner, level.damageSources().playerAttack(other), 4F);
                double scale = AttunementConfig.pvpEffectScale();
                stackRatioExpected = (1D + MatrixResonanceConfig.chaosDamageBonus(level3) * scale)
                        * (1D + MatrixResonanceConfig.chaosTraitBonusVsCorroded() * scale);
                double ratio = baseLoss > 0F ? stackedLoss / baseLoss : 0D;
                stacked = "基准掉血 " + fmt(baseLoss) + " 叠满掉血 " + fmt(stackedLoss) + " 倍数 " + String.format(java.util.Locale.ROOT, "%.4f", ratio);
                boolean stackedPass = baseLoss > 0F && Math.abs(ratio - stackRatioExpected) < 1.0E-3D;

                denied = "掉血 " + fmt(deniedLoss) + " 腐蚀 " + deniedCorroded + " 矩阵容量 " + deniedPool;
                open = "掉血 " + (openLoss > 0F ? ">0" : fmt(openLoss)) + " 腐蚀 " + openTicks + " 刻 矩阵容量 " + openPool;
                pass = deniedLoss == 0F && !deniedCorroded && deniedPool == 1000
                        && openLoss > 0F && openTicks == expectedTicks && openPool == 1000 - cost
                        && Math.abs(onPlayer - onMob * AttunementConfig.pvpEffectScale()) < 1.0E-9D
                        && stackedPass;
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                com.flameforge.essentialcraft5.gun.LensEffects.Config.setTestOverrides(null, null, null);
                MatrixRank.clearTestOverrides();
                MatrixTraits.flush();
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                attacker.setMatrixRank(rankBefore);
                attacker.setMatrixType(typeBefore);
                attacker.setMatrixPool(poolBefore, false);
                victim.setMatrixDamage(matrixDamageBefore);
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                other.removeAllEffects();
                owner.removeAllEffects();
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            String actual = "领地 " + denied + "；野外 " + open + "；强度 对玩家 " + onPlayer + " / 对生物 " + onMob + "；" + stacked;
            return new Outcome(pass, "领地 掉血 0.0 腐蚀 false 矩阵容量 1000（插件 EntityDamageByEntityEvent 已取消）；野外 掉血 >0 腐蚀 " + expectedTicks
                    + " 刻 矩阵容量 " + (1000 - cost) + "；对玩家的强度 = 对生物 × pvp_effect_scale " + AttunementConfig.pvpEffectScale()
                    + "；叠满的倍数 (1 + 躁动 III × s) × (1 + 特性 × s) = " + String.format(java.util.Locale.ROOT, "%.4f", stackRatioExpected), actual, problems);
        }

        /** C62：满血、清掉受击无敌后打一下，返回掉了多少血 */
        private float hurtFresh(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
            victim.setHealth(victim.getMaxHealth());
            victim.invulnerableTime = 0;
            float before = victim.getHealth();
            victim.hurt(source, amount);
            MatrixTraits.flush();
            return before - victim.getHealth();
        }

        /**
         * C63：风之护甲空中跳在混合端上（第十三批 13b，batch13.md §4.3、§12.4；不用新版测试插件）。只记录，不判通过：
         * 「会不会被当成飞行踢掉」要真客户端连上来跳——混合端（CraftBukkit）把不在线的玩家当成不能动（{@code ServerPlayer.isImmobile}
         * 看 {@code getBukkitEntity().isOnline()}），脱离网络的假玩家发进来的移动包整个不处理，这里走不到移动校验和飘浮踢人那一步。
         * <p>
         * 能在这里做的是服务端这一侧：单独建一名跳的人，穿 2 阶风之护甲全套（默认两次空中跳），照客户端的物理逐刻算一整跳的位置
         * （地面起跳 0.42 + 风之护腿 {@value WindGearEvents#LEGGINGS_JUMP_BOOST} → 过了最高点、往下掉到每刻 {@value #C63_PRESS_FALL} 格以上时空中跳，
         * 竖直速度提到 {@code air_jump_velocity} → 再过最高点再跳 → 落地），每刻按原版逐包的规则「收」一个移动包（{@code gametest/WindMovePackets}：
         * 起跳事件、摔落距离往下累加往上清零），再调服务端每刻的两头 {@link WindGearEvents#serverTickStart} / {@link WindGearEvents#serverTick}
         * （正式由 PlayerTickEvent START / END 调）。服务端认不满空中跳、或者整跳连续离地到了原版飘浮踢人线 80 刻，算失败；都对时记成「记录」，等真客户端补测。
         * 另外把同一跳按「每 3 刻漏一个包、下一刻连同它收两个」再喂一遍，只记录认出几次（漏掉的那个包正好是最高点后唯一的下落包时，那一跳旁人看不到风，不算失败）。
         */
        private Outcome c63() {
            List<String> problems = new ArrayList<>();
            int stage = 2;
            ECWindGear.Config.Values values = ECWindGear.Config.values();
            int jumps = values.airJumps(stage);
            double velocity = values.airJumpVelocity();
            if (jumps <= 0) {
                return new Outcome(false, "默认配置下 2 阶全套有空中跳", "air_jumps / air_jump_velocity 把空中跳关了", problems);
            }
            BlockPos at = zoneBase(WILD).offset(4, ZONE_SIZE + 4, 4);
            double x = at.getX() + 0.5D;
            double z = at.getZ() + 0.5D;
            double ground = at.getY();
            // 客户端那边的一整跳：每刻先移动再按重力和空气阻力改速度（原版 LivingEntity.travel），过了最高点、往下掉得够快时按一次空中跳
            List<double[]> path = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                path.add(new double[]{ground, 1D});
            }
            double y = ground;
            double vy = 0.42D + WindGearEvents.LEGGINGS_JUMP_BOOST;
            int pressed = 0;
            for (int t = 0; t < 400; t++) {
                if (t > 0 && vy < -C63_PRESS_FALL && pressed < jumps) {
                    vy = Math.max(vy, velocity);
                    pressed++;
                }
                y += vy;
                vy = (vy - 0.08D) * 0.98D;
                if (y <= ground) {
                    break;
                }
                path.add(new double[]{y, 0D});
            }
            path.add(new double[]{ground, 1D});
            path.add(new double[]{ground, 1D});
            ServerPlayer jumper = realPlayer(JUMPER_ID, JUMPER_NAME);
            int seen;
            int lossy;
            int airborne = 0;
            int longest = 0;
            double peak = ground;
            for (double[] step : path) {
                airborne = step[1] > 0D ? 0 : airborne + 1;
                longest = Math.max(longest, airborne);
                peak = Math.max(peak, step[0]);
            }
            try {
                jumper.getAbilities().mayfly = false;
                jumper.getAbilities().flying = false;
                for (RegistryObject<? extends ArmorItem> piece : List.of(ECWindGear.WIND_HELMET, ECWindGear.WIND_CHESTPLATE, ECWindGear.WIND_LEGGINGS,
                        ECWindGear.WIND_BOOTS)) {
                    ItemStack stack = new ItemStack(piece.get());
                    Attunement.set(stack, stage, 0);
                    jumper.setItemSlot(piece.get().getEquipmentSlot(), stack);
                }
                seen = c63Replay(jumper, x, z, path, 0);
                WindGearEvents.forgetForTest(jumper);
                lossy = c63Replay(jumper, x, z, path, 3);
            } finally {
                WindGearEvents.forgetForTest(jumper);
            }
            String actual = "逐刻挪了 " + path.size() + " 刻（地面起跳 + " + pressed + " 次空中跳，最高 +" + String.format(Locale.ROOT, "%.2f", peak - ground)
                    + " 格），连续离地最长 " + longest + " 刻，服务端认出空中跳 " + seen + " 次；每 3 刻漏一个包时认出 " + lossy
                    + " 次（只记录）；脱离网络的假玩家的移动包核心不处理，移动校验和飘浮踢人要真客户端补测"
                    + (server.isFlightAllowed() ? "；这台服务端 allow-flight 开着，原版不做飘浮踢人判定" : "");
            String expected = "服务端认出 " + jumps + " 次空中跳、连续离地 < 80 刻（只记录；网络那一步要真客户端）";
            if (seen != jumps || pressed != jumps || longest >= 80) {
                return new Outcome(false, expected, actual, problems);
            }
            return new Outcome(null, expected, actual, problems);
        }

        /** C63 里客户端过了最高点、每刻往下掉超过这么多格时才按空中跳（玩家通常是看到开始往下掉才按） */
        private static final double C63_PRESS_FALL = 0.1D;

        /**
         * C63：把一整跳的轨迹（每项 {高度, 着地 1 / 离地 0}）按刻喂给服务端，返回服务端认出的空中跳最多几次。
         * lossEvery 大于 0 时每 lossEvery 刻有一个包晚到：那一刻什么都没收到，下一刻连同它一起收两个。
         */
        private static int c63Replay(ServerPlayer jumper, double x, double z, List<double[]> path, int lossEvery) {
            int seen = 0;
            List<double[]> pending = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                pending.add(path.get(i));
                boolean late = lossEvery > 0 && i % lossEvery == lossEvery - 1 && i + 1 < path.size();
                if (!late) {
                    for (double[] step : pending) {
                        WindMovePackets.packet(jumper, x, step[0], z, step[1] > 0D);
                    }
                    pending.clear();
                }
                WindMovePackets.tick(jumper);
                seen = Math.max(seen, WindGearEvents.serverAirJumps(jumper));
            }
            return seen;
        }

        /**
         * C64：虚空回击、审判庭定罪碰上插件拦下的攻击（第十三批 13b，batch13.md §4.4、§4.6、§12.4；不用新版测试插件）。同 C62 用反射临时打开主世界的 pvpMode。
         * <ol>
         *   <li>闪避以后：另一名玩家穿 2 阶虚空全套（满电），闪避掷数固定成 0（必闪）。他站在 owner 区里挨主人一下（插件取消）→ 没有回击记录、
         *       主人没被挂缓慢；站到野外再挨一下 → 回击 +{@code riposteBonus(2)}、主人缓慢 II {@code round(20 × pvp_effect_scale)} 刻；两下都闪掉，不掉血；</li>
         *   <li>回击：他带着这份回击打 owner 区里一只带保护标签的猪（插件取消）→ 猪不掉血、回击还在；打野外一只带标签的猪 → 回击用掉，
         *       掉血是同一只猪不带回击再挨一下的 (1 + 回击加成) 倍；</li>
         *   <li>定罪：主人穿 0 阶审判庭全套，站在 owner 区里挨他一下（插件取消）→ 主人不掉血、没把他记进定罪表；野外挨一下 → 掉血、记上。</li>
         * </ol>
         * 跑完放回 pvpMode、闪避掷数，清两人的回击 / 定罪记录，脱下盔甲、清效果、满血，删掉猪。
         */
        private Outcome c64() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            ECInquisitorium.Config.Law law = ECInquisitorium.Config.law();
            if (law.convictionTicks() <= 0 || law.convictionMaxTargets() <= 0) {
                return new Outcome(false, "默认配置下定罪开着", "conviction_ticks / conviction_max_targets 把定罪关了", problems);
            }
            BlockPos inClaim = slot(OWN, 24).offset(1, 1, 1);
            BlockPos wild = slot(WILD, 24).offset(1, 1, 1);
            double scale = AttunementConfig.pvpEffectScale();
            double bonus = VoidArmorEvents.riposteBonus(2);
            int dragTicks = (int) Math.round(VoidArmorEvents.DRAG_TICKS * scale);
            Pig pigIn = EntityType.PIG.create(level);
            Pig pigOut = EntityType.PIG.create(level);
            if (pigIn == null || pigOut == null) {
                return new Outcome(false, "造出两只猪", "造不出来", problems);
            }
            boolean pvpBefore = false;
            String dodge;
            String riposte;
            String conviction;
            boolean pass;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                // 另一名玩家要挨打：同主人一样先跑掉 60 tick 的出生无敌（开头只给主人跑了），不然 hurt 直接返回 false、插件也收不到事件
                for (int i = 0; i <= 60; i++) {
                    other.tick();
                }
                VoidArmorEvents.forgetForTest(other);
                InquisitoriumArmorEvents.forgetForTest(owner);
                wearSet(other, List.of(ECVoidArmor.VOID_HELMET, ECVoidArmor.VOID_CHESTPLATE, ECVoidArmor.VOID_LEGGINGS, ECVoidArmor.VOID_BOOTS), 2);

                // ① 闪避以后：owner 区里挨打（插件取消）不攒回击、不给主人挂缓慢；野外照给
                VoidArmorEvents.setTestDodgeRoll(0F);
                System.setProperty(PROP_EVENTS, "");
                standAt(other, inClaim);
                owner.removeAllEffects();
                owner.moveTo(other.getX(), other.getY(), other.getZ() - 1.5D, 0F, 0F);
                float before = other.getHealth();
                other.hurt(level.damageSources().playerAttack(owner), 2F);
                float claimLoss = before - other.getHealth();
                double claimRiposte = VoidArmorEvents.pendingRiposte(other);
                boolean claimDrag = owner.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                Probe.read().expect(problems, "EntityDamageByEntityEvent", OWN, OTHER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");

                standAt(other, wild);
                owner.moveTo(other.getX(), other.getY(), other.getZ() - 1.5D, 0F, 0F);
                before = other.getHealth();
                other.hurt(level.damageSources().playerAttack(owner), 2F);
                float wildLoss = before - other.getHealth();
                double wildRiposte = VoidArmorEvents.pendingRiposte(other);
                MobEffectInstance drag = owner.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                int wildDrag = drag == null ? 0 : drag.getDuration();
                VoidArmorEvents.setTestDodgeRoll(null);
                owner.removeAllEffects();
                System.setProperty(PROP_EVENTS, "");
                dodge = "领地 掉血 " + fmt(claimLoss) + " 回击 " + claimRiposte + " 主人缓慢 " + claimDrag + "；野外 掉血 " + fmt(wildLoss) + " 回击 " + wildRiposte
                        + " 主人缓慢 " + wildDrag + " 刻";
                boolean dodgePass = claimLoss == 0F && claimRiposte == 0D && !claimDrag
                        && wildLoss == 0F && Math.abs(wildRiposte - bonus) < 1.0E-9D && wildDrag == dragTicks;

                // ② 回击：打 owner 区里的猪（插件取消）不用掉；打野外的猪用掉，掉血是不带回击的 (1 + 加成) 倍
                BlockPos pigInAt = slot(OWN, 25).offset(1, 1, 1);
                BlockPos pigOutAt = slot(WILD, 25).offset(1, 1, 1);
                pigIn.moveTo(pigInAt.getX() + 0.5D, pigInAt.getY(), pigInAt.getZ() + 0.5D, 0F, 0F);
                pigOut.moveTo(pigOutAt.getX() + 0.5D, pigOutAt.getY(), pigOutAt.getZ() + 0.5D, 0F, 0F);
                for (Pig pig : List.of(pigIn, pigOut)) {
                    pig.setNoAi(true);
                    pig.addTag(PROTECTED_TAG);
                    level.addFreshEntity(pig);
                }
                other.moveTo(pigIn.getX(), pigIn.getY(), pigIn.getZ() + 1.5D, 180F, 0F);
                float blockedLoss = hurtMob(pigIn, level.damageSources().playerAttack(other), 4F);
                double keptRiposte = VoidArmorEvents.pendingRiposte(other);
                Probe blocked = Probe.read();
                int cancelledPig = 0;
                for (Map<String, String> event : blocked.all()) {
                    if ("EntityDamageByEntityEvent".equals(event.get("type")) && "PIG".equals(event.get("entity")) && "owner".equals(event.get("region"))
                            && "true".equals(event.get("cancelled")) && event.getOrDefault("damager", "").startsWith(OTHER_NAME + "/")) {
                        cancelledPig++;
                    }
                }
                if (cancelledPig == 0) {
                    problems.add("插件缺少 owner 区里打带标签的猪、已取消的 EntityDamageByEntityEvent；实际收到 " + blocked.summary());
                }
                System.setProperty(PROP_EVENTS, "");
                other.moveTo(pigOut.getX(), pigOut.getY(), pigOut.getZ() + 1.5D, 180F, 0F);
                float boostedLoss = hurtMob(pigOut, level.damageSources().playerAttack(other), 4F);
                double usedRiposte = VoidArmorEvents.pendingRiposte(other);
                float baseLoss = hurtMob(pigOut, level.damageSources().playerAttack(other), 4F);
                System.setProperty(PROP_EVENTS, "");
                double ratio = baseLoss > 0F ? boostedLoss / baseLoss : 0D;
                riposte = "领地的猪 掉血 " + fmt(blockedLoss) + " 回击还剩 " + keptRiposte + "；野外的猪 带回击掉血 " + fmt(boostedLoss) + " 不带 " + fmt(baseLoss)
                        + " 倍数 " + String.format(Locale.ROOT, "%.4f", ratio) + " 回击还剩 " + usedRiposte;
                boolean ripostePass = blockedLoss == 0F && Math.abs(keptRiposte - bonus) < 1.0E-9D
                        && baseLoss > 0F && Math.abs(ratio - (1D + bonus)) < 1.0E-3D && usedRiposte == 0D;

                // ③ 定罪：主人穿审判庭全套，owner 区里挨打（插件取消）不记，野外记上
                undress(other);
                VoidArmorEvents.forgetForTest(other);
                wearSet(owner, List.of(ECInquisitorium.INQUISITORIUM_HELMET, ECInquisitorium.INQUISITORIUM_CHESTPLATE, ECInquisitorium.INQUISITORIUM_LEGGINGS,
                        ECInquisitorium.INQUISITORIUM_BOOTS), 0);
                standAt(owner, inClaim);
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                before = owner.getHealth();
                owner.hurt(level.damageSources().playerAttack(other), 4F);
                float convictClaimLoss = before - owner.getHealth();
                boolean convictedInClaim = InquisitoriumArmorEvents.isConvicted(owner, other);
                Probe.read().expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, wild);
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                before = owner.getHealth();
                owner.hurt(level.damageSources().playerAttack(other), 4F);
                float convictWildLoss = before - owner.getHealth();
                boolean convictedWild = InquisitoriumArmorEvents.isConvicted(owner, other);
                System.setProperty(PROP_EVENTS, "");
                conviction = "领地 掉血 " + fmt(convictClaimLoss) + " 定罪 " + convictedInClaim + "；野外 掉血 " + (convictWildLoss > 0F ? ">0" : fmt(convictWildLoss))
                        + " 定罪 " + convictedWild;
                boolean convictionPass = convictClaimLoss == 0F && !convictedInClaim && convictWildLoss > 0F && convictedWild;
                pass = dodgePass && ripostePass && convictionPass;
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                VoidArmorEvents.setTestDodgeRoll(null);
                VoidArmorEvents.forgetForTest(other);
                InquisitoriumArmorEvents.forgetForTest(owner);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                undress(other);
                undress(owner);
                for (ServerPlayer player : List.of(owner, other)) {
                    player.removeAllEffects();
                    player.invulnerableTime = 0;
                    player.setHealth(player.getMaxHealth());
                }
                pigIn.discard();
                pigOut.discard();
            }
            String actual = "闪避 " + dodge + "；回击 " + riposte + "；定罪 " + conviction;
            return new Outcome(pass, "闪避 领地 不掉血、没有回击、主人不缓慢；野外 不掉血、回击 " + bonus + "、主人缓慢 " + dragTicks + " 刻。回击 领地的猪不掉血、回击留着；"
                    + "野外的猪掉血 × " + (1D + bonus) + "、回击用掉。定罪 领地 不掉血、不记；野外 掉血、记上", actual, problems);
        }

        /** C64：满血、清掉受击无敌后打一下，返回掉了多少血 */
        private float hurtMob(LivingEntity mob, DamageSource source, float amount) {
            mob.setHealth(mob.getMaxHealth());
            mob.invulnerableTime = 0;
            float before = mob.getHealth();
            mob.hurt(source, amount);
            return before - mob.getHealth();
        }

        /** C64：穿上一套四件、都是 stage 阶、满电 */
        private void wearSet(ServerPlayer player, List<? extends RegistryObject<? extends ArmorItem>> pieces, int stage) {
            for (RegistryObject<? extends ArmorItem> piece : pieces) {
                ItemStack stack = MRUStaffItem.full(piece.get());
                Attunement.set(stack, stage, 0);
                player.setItemSlot(piece.get().getEquipmentSlot(), stack);
            }
        }

        /** C64：脱下四件盔甲 */
        private void undress(ServerPlayer player) {
            for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        /**
         * 要核心认探测才成立的用例收尾：不分核心的问题（problems）照常判；核心认探测时连 coreProblems 一起判。
         * 核心不认（marker 为 null）时 problems 有问题照样失败，没问题就记成「跳过」，coreProblems 写进实际情况里，不算失败。
         */
        private Outcome coreGated(@Nullable String marker, String expected, String actual, List<String> problems, List<String> coreProblems) {
            if (marker == null) {
                String note = "；核心不支持（分发器和发包入口上都没有权限探测的标记）"
                        + (coreProblems.isEmpty() ? "" : "，旧核心上的表现：" + String.join("；", coreProblems));
                if (!problems.isEmpty()) {
                    return new Outcome(false, expected, actual + note, problems);
                }
                return Outcome.skipped(expected, actual + note, List.of());
            }
            List<String> all = new ArrayList<>(problems);
            all.addAll(coreProblems);
            return new Outcome(all.isEmpty(), expected, actual + "；核心标记 " + marker, all);
        }

        /** 要 major.minor 版以上的测试插件；版本不够时返回失败的结论，够了返回 null */
        @Nullable
        private Outcome requireProbePlugin(int major, int minor) {
            String version = System.getProperty(PROP_PROBE);
            if (probeAtLeast(version, major, minor)) {
                return null;
            }
            return new Outcome(false, major + "." + minor + ".0 版以上的测试插件", "插件版本 " + (version == null ? "没有加载" : version)
                    + "（重打混合端自检的测试插件）", List.of());
        }

        /** 测试玩家的游戏模式摆成生存（真挖要算掉落；创造模式挖了不掉） */
        private void survival(ServerPlayer player) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            }
        }

        /** 这一格附近（1 格内）的某种掉落物有几个，数完删掉（同一 tick 里数，掉落物还没飘走） */
        private int takeDrops(BlockPos cell, Item item) {
            List<Entity> drops = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(cell).inflate(1D), drop -> drop.getItem().is(item)));
            int total = countItems(drops, item);
            for (Entity drop : drops) {
                drop.discard();
            }
            return total;
        }

        // ------------------------------------------------------------------ 第十三批 13c：首领与新怪

        /**
         * C65：首领拉回被插件取消（第十三批 13c，batch13.md §5.3、§12.4；首领通用件 {@link BossRoster}，同 C32 / C40 拉回那一步）。
         * 野外区最上面一层垫一块石头，上面放一只不带 AI、没有契约的矩阵之影（场地中心就是它脚下那块石头），主人（生存）进名单：
         * <ol>
         *   <li>主人站到 30 格外（拉回 {@code pull_range} 24 格和脱战 {@code escape_range} 48 格之间），设 {@value #PROP_CANCEL_TELEPORT}
         *       让插件取消下一次传送，查一次名单：不拉、不扣血、位置不变，主人仍在名单里、仍是技能目标；</li>
         *   <li>对照：同一处不取消再查一次，主人被拽回场地（离中心 3 格内）、掉血，仍在名单里。</li>
         * </ol>
         * 跑完删掉矩阵之影（先清它的契约记录）、拆掉石头、主人回满血，「正在打首领」的记号清掉。
         */
        private Outcome c65() {
            List<String> problems = new ArrayList<>();
            BlockPos pad = topRow(5);
            MatrixShadeEntity shade = null;
            boolean listed = false;
            boolean stillListed = false;
            boolean stillTarget = false;
            boolean pulledListed = false;
            float lossCancelled = -1F;
            float lossPulled = -1F;
            double moved = -1D;
            double pulledOff = -1D;
            Probe cancelProbe = null;
            owner.setGameMode(GameType.SURVIVAL);
            try {
                set(pad, Blocks.STONE.defaultBlockState());
                shade = ECMatrixShade.MATRIX_SHADE.get().create(level);
                if (shade == null) {
                    return new Outcome(false, "生成得了矩阵之影", "create 返回 null", problems);
                }
                shade.moveTo(pad.getX() + 0.5D, pad.getY() + 1D, pad.getZ() + 0.5D, 0F, 0F);
                shade.setNoAi(true);
                if (!level.addFreshEntity(shade)) {
                    return new Outcome(false, "矩阵之影进得了世界", "addFreshEntity 返回 false（被插件或别的模组取消）", problems);
                }
                standAt(owner, pad.above().offset(2, 0, 0));
                shade.bossRoster().add(owner);
                listed = shade.bossRoster().contains(OWNER_ID);
                Vec3 center = shade.bossRoster().center();

                standAt(owner, pad.above().offset(30, 0, 0));
                Vec3 before = owner.position();
                float health = owner.getHealth();
                System.setProperty(PROP_EVENTS, "");
                System.setProperty(PROP_CANCEL_TELEPORT, OWNER_NAME);
                shade.bossRoster().check(level);
                cancelProbe = Probe.read();
                stillListed = shade.bossRoster().contains(OWNER_ID);
                stillTarget = shade.isSkillTarget(owner);
                lossCancelled = health - owner.getHealth();
                moved = owner.position().distanceTo(before);

                System.clearProperty(PROP_CANCEL_TELEPORT);
                standAt(owner, pad.above().offset(30, 0, 0));
                health = owner.getHealth();
                shade.bossRoster().check(level);
                lossPulled = health - owner.getHealth();
                pulledOff = owner.position().distanceTo(center);
                pulledListed = shade.bossRoster().contains(OWNER_ID);
            } finally {
                System.clearProperty(PROP_CANCEL_TELEPORT);
                if (shade != null) {
                    MatrixShadeContracts.forgetForTest(level, shade.getUUID(), null, null);
                    if (!shade.isRemoved()) {
                        shade.discard();
                    }
                }
                BossRoster.forgetFighter(OWNER_ID);
                set(pad, Blocks.AIR.defaultBlockState());
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            Map<String, String> teleport = cancelProbe == null ? null : cancelProbe.first("PlayerTeleportEvent");
            if (teleport == null || !"true".equals(teleport.get("cancelled"))) {
                problems.add("插件应收到并取消一次主人的 PlayerTeleportEvent，实际 " + teleportSummary(teleport));
            }
            String actual = "进名单 " + listed + "；拉回被取消 仍在名单 " + stillListed + " 仍是技能目标 " + stillTarget + " 掉血 " + fmt(lossCancelled)
                    + " 位移 " + String.format("%.2f", moved) + "；不取消 离中心 " + String.format("%.1f", pulledOff) + " 掉血 " + fmt(lossPulled)
                    + " 仍在名单 " + pulledListed;
            boolean pass = listed && stillListed && stillTarget && lossCancelled == 0F && moved < 1.0E-3D
                    && pulledOff >= 0D && pulledOff < 3D && lossPulled > 0F && pulledListed;
            return new Outcome(pass, "进名单 true；拉回被取消 仍在名单 true 仍是技能目标 true 掉血 0.0 位移 0.00；不取消 离中心 < 3 掉血 > 0 仍在名单 true",
                    actual, problems);
        }

        /**
         * C66：领地里召唤矩阵之影被拒（第十三批 13c，batch13.md §5.3.2、§12.4）。owner 区上半部搭一座一档灵魂五芒星
         * （0 档 8 根米斯莱柱 + 1 档 4 根淡青柱，每根灌 {@value #C66_PILLAR_ESPE} ESPE），两人各带 {@value #C66_DAMAGE} 点矩阵损伤、手里两张导流符：
         * <ol>
         *   <li>另一名玩家（不是这块领地的主人）召唤：中心块那一格的破坏探测被插件拒 → DENIED，符还是两张、损伤不少、ESPE 不少、没出怪；</li>
         *   <li>主人召唤：OK，符少一张、损伤少了转进去的量、柱子扣了 ESPE，五芒星上出来一只矩阵之影。</li>
         * </ol>
         * 跑完删掉契约和矩阵之影、拆掉五芒星、两人的矩阵损伤放回原值、手里清空。
         */
        private Outcome c66() {
            List<String> problems = new ArrayList<>();
            BlockPos center = zoneBase(OWN).offset(5, ZONE_SIZE - 2, 5);
            IPlayerData ownerData = PlayerDataEvents.get(owner);
            IPlayerData otherData = PlayerDataEvents.get(other);
            if (ownerData == null || otherData == null) {
                return new Outcome(false, "两人都挂着玩家数据", "主人 " + (ownerData != null) + " 另一名 " + (otherData != null), problems);
            }
            int ownerBefore = ownerData.getMatrixDamage();
            int otherBefore = otherData.getMatrixDamage();
            MatrixShadeSummon.Outcome denied = null;
            MatrixShadeSummon.Outcome allowed = null;
            int deniedConduits = -1;
            int deniedDamage = -1;
            double deniedEspe = -1D;
            boolean deniedNoShade = false;
            int allowedConduits = -1;
            int allowedDamage = -1;
            double allowedEspe = -1D;
            double fullEspe = -1D;
            int tier = -1;
            owner.setGameMode(GameType.SURVIVAL);
            other.setGameMode(GameType.SURVIVAL);
            try {
                clearBox(center.offset(-3, -2, -3), center.offset(3, 4, 3));
                tier = buildPentacle(center);
                fullEspe = pentacleEspe(center);
                ownerData.setMatrixDamage(C66_DAMAGE);
                otherData.setMatrixDamage(C66_DAMAGE);
                ItemStack otherConduits = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
                other.setItemInHand(InteractionHand.MAIN_HAND, otherConduits);
                standAt(other, center.offset(0, 1, 3));
                denied = MatrixShadeSummon.summon(other, level, center, otherConduits);
                deniedConduits = otherConduits.getCount();
                deniedDamage = otherData.getMatrixDamage();
                deniedEspe = pentacleEspe(center);
                deniedNoShade = level.getEntitiesOfClass(MatrixShadeEntity.class, new AABB(center).inflate(8D)).isEmpty();
                Probe deniedProbe = Probe.read();
                deniedProbe.expect(problems, "BlockBreakEvent", OWN, OTHER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");

                ItemStack ownerConduits = new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get(), 2);
                owner.setItemInHand(InteractionHand.MAIN_HAND, ownerConduits);
                standAt(owner, center.offset(0, 1, 3));
                allowed = MatrixShadeSummon.summon(owner, level, center, ownerConduits);
                allowedConduits = ownerConduits.getCount();
                allowedDamage = ownerData.getMatrixDamage();
                allowedEspe = pentacleEspe(center);
            } finally {
                MatrixShadeEntity shade = allowed == null ? null : allowed.shade();
                MatrixShadeContracts.forgetForTest(level, shade == null ? null : shade.getUUID(), OWNER_ID, center);
                MatrixShadeContracts.forgetForTest(level, null, OTHER_ID, null);
                for (MatrixShadeEntity found : level.getEntitiesOfClass(MatrixShadeEntity.class, new AABB(center).inflate(12D))) {
                    MatrixShadeContracts.forgetForTest(level, found.getUUID(), null, null);
                    found.discard();
                }
                BossRoster.forgetFighter(OWNER_ID);
                ownerData.setMatrixDamage(ownerBefore);
                otherData.setMatrixDamage(otherBefore);
                owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                clearBox(center.offset(-3, -2, -3), center.offset(3, 4, 3));
                owner.setHealth(owner.getMaxHealth());
            }
            boolean deniedKept = deniedConduits == 2 && deniedDamage == C66_DAMAGE && Math.abs(deniedEspe - fullEspe) < 1.0E-6D;
            boolean allowedPaid = allowed != null && allowed.shade() != null && allowedConduits == 1
                    && allowedDamage == C66_DAMAGE - allowed.absorbed() && allowed.absorbed() > 0 && allowedEspe < fullEspe - 1.0E-6D;
            String actual = "五芒星 " + tier + " 档；领地 " + (denied == null ? "-" : denied.result()) + " 符 " + deniedConduits + " 损伤 " + deniedDamage
                    + " ESPE " + (Math.abs(deniedEspe - fullEspe) < 1.0E-6D ? "没少" : "少了 " + String.format("%.1f", fullEspe - deniedEspe))
                    + " 出怪 " + !deniedNoShade + "；主人 " + (allowed == null ? "-" : allowed.result()) + " 符 " + allowedConduits + " 损伤 " + allowedDamage
                    + "（转进 " + (allowed == null ? 0 : allowed.absorbed()) + "）ESPE 少了 " + String.format("%.1f", fullEspe - allowedEspe)
                    + " 出怪 " + (allowed != null && allowed.shade() != null);
            boolean pass = tier >= 1 && denied != null && denied.result() == MatrixShadeSummon.Result.DENIED && deniedKept && deniedNoShade
                    && allowed != null && allowed.result() == MatrixShadeSummon.Result.OK && allowedPaid;
            return new Outcome(pass, "五芒星 1 档；领地 DENIED 符 2 损伤 " + C66_DAMAGE + " ESPE 没少 出怪 false；主人 OK 符 1 损伤减掉转进的量 ESPE 扣了 出怪 true",
                    actual, problems);
        }

        /** C66：在 center 搭一档灵魂五芒星（同 GameTest MatrixShadeTests.pentacle），柱子各灌 {@value #C66_PILLAR_ESPE}；返回当场重判的档位 */
        private int buildPentacle(BlockPos center) {
            for (BlockPos offset : ECPentacle.TIER_PATTERNS.get(0).offsets(ECPentacle.PLATING_RULE)) {
                set(center.offset(offset), ECMithriline.MITHRILINE_PLATING.get().defaultBlockState());
            }
            for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
                set(center.offset(offset), ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState());
            }
            for (BlockPos offset : ECPentacle.TIER_PATTERNS.get(1).offsets(ECPentacle.PALE_RULE)) {
                set(center.offset(offset), ECPentacle.PALE_BLOCK.get().defaultBlockState());
            }
            for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(1)) {
                set(center.offset(offset), ECPentacle.PALE_CRYSTAL_PILLAR.get().defaultBlockState());
            }
            set(center, ECPentacle.PLAYER_PENTACLE.get().defaultBlockState());
            for (int t = 0; t <= 1; t++) {
                for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(t)) {
                    if (level.getBlockEntity(center.offset(offset)) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                        pillar.getESPEStorage().setESPE(C66_PILLAR_ESPE);
                    }
                }
            }
            return level.getBlockEntity(center) instanceof PlayerPentacleBlockEntity pentacle ? pentacle.recheckTier() : -1;
        }

        /** C66：一档五芒星 12 根柱子里的 ESPE 合计 */
        private double pentacleEspe(BlockPos center) {
            double sum = 0D;
            for (int t = 0; t <= 1; t++) {
                for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(t)) {
                    if (level.getBlockEntity(center.offset(offset)) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                        sum += pillar.getESPEStorage().getESPE();
                    }
                }
            }
            return sum;
        }

        /** 把 from~to（含）这个盒子里的方块全换成空气 */
        private void clearBox(BlockPos from, BlockPos to) {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (!level.getBlockState(pos).isAir()) {
                    set(pos.immutable(), Blocks.AIR.defaultBlockState());
                }
            }
        }

        /**
         * C67：聚能魔像的触发者在别人领地里不触发（第十三批 13c，batch13.md §5.3.1、§12.4）。other 区上半部铺一块 3×3 石头，
         * 上面放一团 6 000 MRU、守护记成「等着」的聚集体（同新生成的上古遗迹）：
         * <ol>
         *   <li>主人（不是这块领地的主人，生存）站在 3 格外查一次触发：聚集体那一格的破坏探测被插件拒 → 不出魔像、守护还是「等着」；</li>
         *   <li>另一名玩家（领地主人，生存）站同一处查一次：出来一只魔像、守护记成「已出」；</li>
         *   <li>落点那一半：另一团「等着」的聚集体悬在 other 区正下方 3 格的空中（领地外，主人能动），能落脚的只有区里最底层一块 3×3 石头上面
         *       （只往上找 4 格，碰不到上面那块）。主人走近：落点以他的身份探测被插件拒 → 不出魔像、守护还是「等着」。</li>
         * </ol>
         * 跑完删掉魔像和聚集体、拆掉石头、清掉两人的领地结论缓存、落点结论和这两团的重试计时。
         */
        private Outcome c67() {
            List<String> problems = new ArrayList<>();
            BlockPos cell = zoneBase(OTHERS).offset(5, ZONE_SIZE - 3, 5);
            MRUPresenceEntity presence = null;
            List<MRUGolemEntity> golems = new ArrayList<>();
            int deniedGolems = -1;
            MRUGolemGuardian.State deniedState = null;
            int allowedGolems = -1;
            MRUGolemGuardian.State allowedState = null;
            BlockPos beneath = zoneBase(OTHERS).offset(5, -3, 5);
            MRUPresenceEntity hanging = null;
            int claimedGolems = -1;
            MRUGolemGuardian.State claimedState = null;
            owner.setGameMode(GameType.SURVIVAL);
            other.setGameMode(GameType.SURVIVAL);
            try {
                clearBox(cell.offset(-2, -1, -2), cell.offset(2, 3, 2));
                for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, -1, 1))) {
                    set(pos.immutable(), Blocks.STONE.defaultBlockState());
                }
                presence = MRUPresenceEntity.create(level, cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D, 6000, 1F, false, true);
                presence.setGuardian(MRUGolemGuardian.pending(presence.blockPosition()));
                if (!level.addFreshEntity(presence)) {
                    return new Outcome(false, "聚集体进得了世界", "addFreshEntity 返回 false", problems);
                }
                standAt(owner, cell.offset(0, 0, 3));
                List<MRUGolemEntity> first = MRUGolemTrigger.check(owner);
                golems.addAll(first);
                deniedGolems = first.size();
                deniedState = presence.getGuardian().state();
                Probe deniedProbe = Probe.read();
                deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");

                standAt(other, cell.offset(0, 0, 3));
                List<MRUGolemEntity> second = MRUGolemTrigger.check(other);
                golems.addAll(second);
                allowedGolems = second.size();
                allowedState = presence.getGuardian().state();

                ECProtection.forget(OWNER_ID);
                System.setProperty(PROP_EVENTS, "");
                clearBox(beneath.offset(-2, -1, -2), beneath.offset(2, 6, 2));
                for (BlockPos pos : BlockPos.betweenClosed(beneath.offset(-1, 3, -1), beneath.offset(1, 3, 1))) {
                    set(pos.immutable(), Blocks.STONE.defaultBlockState());
                }
                hanging = MRUPresenceEntity.create(level, beneath.getX() + 0.5D, beneath.getY() + 0.5D, beneath.getZ() + 0.5D, 6000, 1F, false, true);
                hanging.setGuardian(MRUGolemGuardian.pending(hanging.blockPosition()));
                if (!level.addFreshEntity(hanging)) {
                    return new Outcome(false, "悬空的聚集体进得了世界", "addFreshEntity 返回 false", problems);
                }
                standAt(owner, beneath.offset(0, 0, 3));
                ECMRUGolem.Config.Values current = ECMRUGolem.Config.values();
                ECMRUGolem.Config.Trigger t = current.trigger();
                ECMRUGolem.Config.setTestOverrides(new ECMRUGolem.Config.Values(
                        new ECMRUGolem.Config.Trigger(t.radius(), t.hoannaChance(), 4, t.retryTicks(), t.abandonDistance()),
                        current.stats(), current.shield(), current.attacks(), current.detector()));
                try {
                    List<MRUGolemEntity> third = MRUGolemTrigger.check(owner);
                    golems.addAll(third);
                    claimedGolems = third.size();
                } finally {
                    ECMRUGolem.Config.setTestOverrides(null);
                }
                claimedState = hanging.getGuardian().state();
                Probe.read().expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            } finally {
                for (MRUGolemEntity golem : golems) {
                    if (!golem.isRemoved()) {
                        golem.discard();
                    }
                }
                if (presence != null) {
                    MRUGolemTrigger.forgetRetry(presence.getUUID());
                    if (!presence.isRemoved()) {
                        presence.discard();
                    }
                }
                if (hanging != null) {
                    MRUGolemTrigger.forgetRetry(hanging.getUUID());
                    MRUGolemTrigger.forgetLanding(hanging.getUUID());
                    if (!hanging.isRemoved()) {
                        hanging.discard();
                    }
                }
                PresenceClaimMemory.forget(OWNER_ID);
                PresenceClaimMemory.forget(OTHER_ID);
                BossRoster.forgetFighter(OTHER_ID);
                clearBox(cell.offset(-2, -1, -2), cell.offset(2, 3, 2));
                clearBox(beneath.offset(-2, -1, -2), beneath.offset(2, 6, 2));
            }
            String actual = "领地外人 魔像 " + deniedGolems + " 守护 " + deniedState + "；领地主人 魔像 " + allowedGolems + " 守护 " + allowedState
                    + "；落点在领地里 魔像 " + claimedGolems + " 守护 " + claimedState;
            boolean pass = deniedGolems == 0 && deniedState == MRUGolemGuardian.State.PENDING
                    && allowedGolems == 1 && allowedState == MRUGolemGuardian.State.SPAWNED
                    && claimedGolems == 0 && claimedState == MRUGolemGuardian.State.PENDING;
            return new Outcome(pass, "领地外人 魔像 0 守护 PENDING；领地主人 魔像 1 守护 SPAWNED；落点在领地里 魔像 0 守护 PENDING", actual, problems);
        }

        /**
         * C68：怪物的一下被插件拦下时不加矩阵损伤（第十三批 13c，batch13.md §5.2、§12.4）。要 1.6.0 版测试插件：owner 区里，
         * 带计分板标签 {@value #RESTRAINED_TAG} 的生物打玩家也取消（模拟领地的「怪物不伤人」）；旧版插件整条记成跳过。
         * 一只不带 AI、带这个标签的矩阵游魂，主人（生存、零阶）：
         * <ol>
         *   <li>主人站在 owner 区，游魂抓他一下：插件取消伤害事件 → 没打中、不掉血、矩阵损伤不变；</li>
         *   <li>主人站到野外，再抓一下：打中、掉血、矩阵损伤加了（零阶不打折，就是 {@code matrix_damage_per_hit}）。</li>
         * </ol>
         * 混合端上插件的伤害事件在 Forge 的 LivingHurtEvent 之后、LivingDamageEvent 之前，加损伤挂在 LivingDamageEvent 上才拦得住。
         * 跑完删掉游魂、主人的矩阵损伤放回原值、回满血。
         */
        private Outcome c68() {
            List<String> problems = new ArrayList<>();
            String version = System.getProperty(PROP_PROBE, "0");
            String[] parts = version.split("\\.");
            boolean supported;
            try {
                supported = Integer.parseInt(parts[0]) > 1 || (Integer.parseInt(parts[0]) == 1 && parts.length > 1 && Integer.parseInt(parts[1]) >= 6);
            } catch (NumberFormatException e) {
                supported = false;
            }
            if (!supported) {
                return Outcome.skipped("1.6.0 版测试插件", "测试插件 " + version + " 不认 " + RESTRAINED_TAG, problems);
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人挂着玩家数据", "没有", problems);
            }
            int before = data.getMatrixDamage();
            int perHit = ECWildMobs.Config.wraithMatrixDamage();
            MatrixWraithEntity wraith = null;
            boolean blockedHit = true;
            float blockedLoss = -1F;
            int blockedAdded = -1;
            boolean wildHit = false;
            float wildLoss = -1F;
            int wildAdded = -1;
            Probe blockedProbe = null;
            owner.setGameMode(GameType.SURVIVAL);
            try {
                data.setMatrixDamage(0);
                wraith = ECWildMobs.MATRIX_WRAITH.get().create(level);
                if (wraith == null) {
                    return new Outcome(false, "生成得了矩阵游魂", "create 返回 null", problems);
                }
                wraith.addTag(RESTRAINED_TAG);
                wraith.setNoAi(true);
                BlockPos claimed = topRow(ZONE_STEP + 3);
                wraith.moveTo(claimed.getX() - 1.5D, claimed.getY(), claimed.getZ() + 0.5D, 0F, 0F);
                if (!level.addFreshEntity(wraith)) {
                    return new Outcome(false, "矩阵游魂进得了世界", "addFreshEntity 返回 false", problems);
                }
                standAt(owner, claimed);
                float health = owner.getHealth();
                System.setProperty(PROP_EVENTS, "");
                blockedHit = wraith.doHurtTarget(owner);
                blockedProbe = Probe.read();
                blockedLoss = health - owner.getHealth();
                blockedAdded = data.getMatrixDamage();

                BlockPos wild = topRow(3);
                wraith.moveTo(wild.getX() - 1.5D, wild.getY(), wild.getZ() + 0.5D, 0F, 0F);
                standAt(owner, wild);
                health = owner.getHealth();
                wildHit = wraith.doHurtTarget(owner);
                wildLoss = health - owner.getHealth();
                wildAdded = data.getMatrixDamage();
            } finally {
                if (wraith != null && !wraith.isRemoved()) {
                    wraith.discard();
                }
                data.setMatrixDamage(before);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            Map<String, String> damage = blockedProbe == null ? null : blockedProbe.first("EntityDamageByEntityEvent");
            if (damage == null || !"true".equals(damage.get("cancelled"))) {
                problems.add("插件应收到并取消一次游魂打主人的 EntityDamageByEntityEvent，实际 " + (damage == null ? "没收到" : damage.get("line")));
            }
            String actual = "领地里 打中 " + blockedHit + " 掉血 " + fmt(blockedLoss) + " 加损伤 " + blockedAdded
                    + "；野外 打中 " + wildHit + " 掉血 " + fmt(wildLoss) + " 加损伤 " + wildAdded;
            boolean pass = !blockedHit && blockedLoss == 0F && blockedAdded == 0 && wildHit && wildLoss > 0F && wildAdded == perHit;
            return new Outcome(pass, "领地里 打中 false 掉血 0.0 加损伤 0；野外 打中 true 掉血 > 0 加损伤 " + perHit, actual, problems);
        }

        // ------------------------------------------------------------------ 第十三批 13d：海纳尔世界（另有 13c 留下的 C76）

        /**
         * C69：海纳尔特产的收取被领地拦得住（第十三批 13d，batch13.md §6.1、§12.4）。整组把这个世界临时当成海纳尔
         * （{@code ECHoanna.setTestHoannaOverride}，挖辐晶簇只在海纳尔加剂量），影晶簇白天的几率临时设 1（测试覆盖，不改配置文件）。
         * other 区和野外各摆一排：满充能的地脉喷口、长满的潮晶芽、成熟的影晶簇、辐晶簇（后三样立在石头上）。主人（不是 other 区的主人，生存、空手、裸身）：
         * <ol>
         *   <li>other 区：三样用 {@code gameMode.useItemOn} 右键（混合端在这里发 PlayerInteractEvent，插件取消），辐晶簇用 {@code gameMode.destroyBlock} 挖
         *       （BlockBreakEvent 被取消）→ 什么都拿不到，喷口仍满充能、芽仍长满、影晶簇仍成熟，辐晶簇还在，剂量不变；</li>
         *   <li>野外：同样四下 → 地脉结晶 3 × {@code crystals_per_charge}、潮晶 1~2、影晶至少 1，三样回到收过的状态；辐晶簇挖掉、剂量 +{@code radiant_mining_dose}。</li>
         * </ol>
         * 跑完拆掉方块、清掉主人背包和地上的掉落物、剂量放回原值。
         */
        private Outcome c69() {
            List<String> problems = new ArrayList<>();
            int doseBefore = Radiation.get(owner);
            owner.setGameMode(GameType.SURVIVAL);
            Harvest denied;
            Harvest wild;
            Probe deniedProbe;
            ECHoanna.setTestHoannaOverride(level);
            ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, 1D);
            try {
                denied = harvestRow(OTHERS);
                deniedProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                wild = harvestRow(WILD);
            } finally {
                ECHoannaResources.Config.setTestOverride(ECHoannaResources.Config.UMBRAL_DAY_CHANCE, null);
                ECHoanna.clearTestOverride();
                clearHarvestRow(OTHERS);
                clearHarvestRow(WILD);
                owner.getInventory().clearContent();
                Radiation.set(owner, doseBefore);
            }
            deniedProbe.expect(problems, "PlayerInteractEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            int ley = LeyVentBlock.MAX_CHARGE * ECHoannaResources.Config.crystalsPerCharge();
            int dose = ECHoannaResources.Config.radiantMiningDose();
            boolean deniedOk = denied.ley() == 0 && denied.tide() == 0 && denied.umbral() == 0 && denied.charge() == LeyVentBlock.MAX_CHARGE
                    && denied.stage() == TideBudBlock.MAX_STAGE && denied.ripe() && denied.clusterLeft() && denied.dose() == 0;
            boolean wildOk = wild.ley() == ley && wild.tide() >= 1 && wild.tide() <= 2 && wild.umbral() >= 1 && wild.charge() == 0
                    && wild.stage() == 0 && !wild.ripe() && !wild.clusterLeft() && wild.dose() == dose;
            return new Outcome(deniedOk && wildOk,
                    "other 区 结晶 0 潮晶 0 影晶 0 喷口 3 芽 3 成熟 true 辐晶簇在 true 剂量 +0；野外 结晶 " + ley
                            + " 潮晶 1~2 影晶 ≥ 1 喷口 0 芽 0 成熟 false 辐晶簇在 false 剂量 +" + dose,
                    "other 区 " + denied + "；野外 " + wild, problems);
        }

        /** C69 的一排：zone 第 2 层，x 1 / 3 / 5 / 7 依次喷口、潮晶芽、影晶簇、辐晶簇（后三样脚下一块石头），主人站在这排南边 2 格 */
        private Harvest harvestRow(int zone) {
            BlockPos vent = zoneBase(zone).offset(1, 2, 5);
            BlockPos bud = vent.offset(2, 0, 0);
            BlockPos umbral = vent.offset(4, 0, 0);
            BlockPos radiant = vent.offset(6, 0, 0);
            clearHarvestRow(zone);
            set(bud.below(), Blocks.STONE.defaultBlockState());
            set(umbral.below(), Blocks.STONE.defaultBlockState());
            set(radiant.below(), Blocks.STONE.defaultBlockState());
            set(vent, ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, LeyVentBlock.MAX_CHARGE));
            set(bud, ECHoannaResources.TIDE_BUD.get().defaultBlockState().setValue(TideBudBlock.STAGE, TideBudBlock.MAX_STAGE));
            set(umbral, ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState().setValue(UmbralClusterBlock.RIPE, true)
                    .setValue(HoannaClusterBlock.FACING, Direction.UP));
            set(radiant, ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState().setValue(HoannaClusterBlock.FACING, Direction.UP));
            standAt(owner, vent.offset(3, -1, -2));
            owner.getInventory().clearContent();
            owner.getInventory().selected = 0;
            Radiation.set(owner, 0);
            for (BlockPos target : new BlockPos[]{vent, bud, umbral}) {
                owner.gameMode.useItemOn(owner, level, owner.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            }
            owner.gameMode.destroyBlock(radiant);
            int dose = Radiation.get(owner);
            Inventory inventory = owner.getInventory();
            int ley = inventory.countItem(ECHoannaMaterials.LEY_CRYSTAL.get()) + takeDrops(vent, ECHoannaMaterials.LEY_CRYSTAL.get());
            int tide = inventory.countItem(ECHoannaMaterials.TIDE_CRYSTAL.get()) + takeDrops(bud, ECHoannaMaterials.TIDE_CRYSTAL.get());
            int crystals = inventory.countItem(ECHoannaMaterials.UMBRAL_CRYSTAL.get()) + takeDrops(umbral, ECHoannaMaterials.UMBRAL_CRYSTAL.get());
            takeDrops(radiant, ECHoannaMaterials.RADIANT_CRYSTAL.get());
            BlockState ventState = level.getBlockState(vent);
            BlockState budState = level.getBlockState(bud);
            BlockState umbralState = level.getBlockState(umbral);
            return new Harvest(ley, tide, crystals,
                    ventState.hasProperty(LeyVentBlock.CHARGE) ? ventState.getValue(LeyVentBlock.CHARGE) : -1,
                    budState.hasProperty(TideBudBlock.STAGE) ? budState.getValue(TideBudBlock.STAGE) : -1,
                    umbralState.hasProperty(UmbralClusterBlock.RIPE) && umbralState.getValue(UmbralClusterBlock.RIPE),
                    level.getBlockState(radiant).is(ECHoannaResources.RADIANT_CLUSTER.get()), dose);
        }

        private void clearHarvestRow(int zone) {
            BlockPos vent = zoneBase(zone).offset(1, 2, 5);
            clearBox(vent.offset(-1, -1, -3), vent.offset(7, 1, 1));
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(vent.offset(-1, -1, -3)).minmax(new AABB(vent.offset(8, 2, 2))))) {
                drop.discard();
            }
        }

        /**
         * C70：巢穴召唤被插件取消时回 armed、不卡死（第十三批 13d，batch13.md §6.4、§12.4）。要 1.7.0 版测试插件：系统属性
         * {@value #PROP_CANCEL_SPAWNS} 写着某块区时，插件取消落在那块区里的生物生成（领地的「禁止刷怪」）；旧版插件整条记成跳过。
         * 测试用的巢穴类型只有 1 只墓穴守灵（{@code HoannaNestTypes.putForTest}），候选玩家只有主人（{@code HoannaNests.setTestPlayers}）：
         * <ol>
         *   <li>owner 区底层铺石头、正中摆核心，插件取消 owner 区里的生成，主人（生存）站在 3 格外，查一次 → 放不出（SPAWN_FAILED），
         *       核心仍是 armed、重试时刻 = 现在 + {@code RETRY_TICKS}、这一波是空的、全服战斗中的巢穴数不变、区里没有守灵；</li>
         *   <li>对照：野外同样摆一处、不取消 → 放出一波（SPAWNED），核心变 active、这一波 1 只。</li>
         * </ol>
         * 跑完收回这一波、拆掉两处核心和石头、去掉测试类型和覆盖。
         */
        private Outcome c70() {
            List<String> problems = new ArrayList<>();
            String version = System.getProperty(PROP_PROBE);
            if (!probeAtLeast(version, 1, 7)) {
                return Outcome.skipped("1.7.0 版测试插件", "测试插件 " + version + " 不会取消生物生成（" + PROP_CANCEL_SPAWNS + "）", problems);
            }
            ResourceLocation typeId = EssentialCraft5.id("selftest_c70");
            HoannaNestType type = new HoannaNestType(typeId,
                    List.of(new HoannaNestType.Member(ECHoannaNativesC.CATACOMB_REVENANT.get(), 1, false)),
                    3, HoannaNestType.DEFAULT_ACTIVATE_RADIUS, HoannaNestType.DEFAULT_LEASH_RADIUS, BuiltInLootTables.EMPTY,
                    HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG);
            BlockPos deniedCore = zoneBase(OWN).offset(5, 1, 5);
            BlockPos wildCore = zoneBase(WILD).offset(5, 1, 5);
            NestCoreBlockEntity.Outcome deniedOutcome = null;
            NestCoreBlock.Phase deniedPhase = null;
            long retryDelta = -1L;
            int deniedWave = -1;
            int activeBefore = HoannaNests.activeCount(level);
            int activeAfterDenied = -1;
            int revenantsLeft = -1;
            NestCoreBlockEntity.Outcome wildOutcome = null;
            NestCoreBlock.Phase wildPhase = null;
            int wildWave = -1;
            Probe deniedProbe = null;
            NestCoreBlockEntity wildBe = null;
            owner.setGameMode(GameType.SURVIVAL);
            HoannaNestTypes.putForTest(typeId, type);
            HoannaNests.setTestPlayers(List.of(owner));
            HoannaNests.setTestPeaceful(false);
            try {
                NestCoreBlockEntity deniedBe = nestCoreAt(deniedCore, typeId);
                standAt(owner, deniedCore.offset(3, 0, 0));
                System.setProperty(PROP_CANCEL_SPAWNS, ZONE_NAMES[OWN]);
                long now = HoannaNests.now(level);
                deniedOutcome = deniedBe.check(level);
                deniedPhase = deniedBe.phase();
                retryDelta = deniedBe.retryAt() - now;
                deniedWave = deniedBe.waveIds().size();
                activeAfterDenied = HoannaNests.activeCount(level);
                revenantsLeft = level.getEntitiesOfClass(CatacombRevenantEntity.class, new AABB(deniedCore).inflate(8D)).size();
                deniedProbe = Probe.read();
                System.clearProperty(PROP_CANCEL_SPAWNS);
                System.setProperty(PROP_EVENTS, "");

                wildBe = nestCoreAt(wildCore, typeId);
                standAt(owner, wildCore.offset(3, 0, 0));
                wildOutcome = wildBe.check(level);
                wildPhase = wildBe.phase();
                wildWave = wildBe.waveIds().size();
            } finally {
                System.clearProperty(PROP_CANCEL_SPAWNS);
                if (wildBe != null) {
                    wildBe.resetAll(level);
                }
                for (CatacombRevenantEntity revenant : level.getEntitiesOfClass(CatacombRevenantEntity.class,
                        new AABB(zoneBase(WILD)).expandTowards(ZONE_STEP * 2D, ZONE_SIZE, ZONE_SIZE).inflate(MARGIN))) {
                    revenant.discard();
                }
                clearBox(deniedCore.offset(-3, -1, -3), deniedCore.offset(3, 3, 3));
                clearBox(wildCore.offset(-3, -1, -3), wildCore.offset(3, 3, 3));
                HoannaNests.setTestPlayers(null);
                HoannaNests.setTestPeaceful(null);
                HoannaNestTypes.clearTest();
            }
            if (deniedProbe != null) {
                Map<String, String> spawn = deniedProbe.first("EntitySpawnEvent");
                if (spawn == null) {
                    spawn = deniedProbe.first("CreatureSpawnEvent");
                }
                if (spawn == null || !"true".equals(spawn.get("cancelled")) || !ZONE_NAMES[OWN].equals(spawn.get("region"))) {
                    problems.add("插件应在 owner 区收到并取消一次生物生成事件，实际 " + (spawn == null ? "没收到" : spawn.get("line")));
                }
            }
            String actual = "领地里 " + deniedOutcome + " 核心 " + deniedPhase + " 重试 +" + retryDelta + " 这一波 " + deniedWave
                    + " 战斗中 " + activeBefore + "→" + activeAfterDenied + " 守灵 " + revenantsLeft
                    + "；野外 " + wildOutcome + " 核心 " + wildPhase + " 这一波 " + wildWave;
            boolean pass = deniedOutcome == NestCoreBlockEntity.Outcome.SPAWN_FAILED && deniedPhase == NestCoreBlock.Phase.ARMED
                    && retryDelta == NestCoreBlockEntity.RETRY_TICKS && deniedWave == 0 && activeAfterDenied == activeBefore && revenantsLeft == 0
                    && wildOutcome == NestCoreBlockEntity.Outcome.SPAWNED && wildPhase == NestCoreBlock.Phase.ACTIVE && wildWave == 1;
            return new Outcome(pass, "领地里 SPAWN_FAILED 核心 ARMED 重试 +" + NestCoreBlockEntity.RETRY_TICKS
                    + " 这一波 0 战斗中不变 守灵 0；野外 SPAWNED 核心 ACTIVE 这一波 1", actual, problems);
        }

        /** C70：在 core 摆一块巢穴核心（底下一层 7 × 7 石头），类型记成 typeId */
        private NestCoreBlockEntity nestCoreAt(BlockPos core, ResourceLocation typeId) {
            clearBox(core.offset(-3, -1, -3), core.offset(3, 3, 3));
            for (BlockPos pos : BlockPos.betweenClosed(core.offset(-3, -1, -3), core.offset(3, -1, 3))) {
                set(pos.immutable(), Blocks.STONE.defaultBlockState());
            }
            set(core, ECHoannaFramework.NEST_CORE.get().defaultBlockState());
            if (!(level.getBlockEntity(core) instanceof NestCoreBlockEntity be)) {
                throw new IllegalStateException("巢穴核心没有方块实体：" + core.toShortString());
            }
            be.setType(typeId);
            return be;
        }

        /**
         * C71：地脉风暴在混合端里照常落晶、不伤人（第十三批 13d，batch13.md §6.2、§12.4）。把这个世界临时当成海纳尔、照指令的入口开一场地脉风暴，
         * 落晶几率临时设 1、落点 2~3 格（测试覆盖）。主人（生存、裸身、剂量清零）站在野外区顶上一块 7 × 7 石台中间（头顶露天）：
         * <ol>
         *   <li>给他掷一次落晶：生成了（插件没拦下）、记在他名下；碰一下：落晶没了、背包多 {@code crystalsFor} 个地脉结晶、名下计数回到 0；</li>
         *   <li>风暴里的一秒（{@code LeyStorm.exposure}）：剂量 +{@code ley_storm_radiation}；再照每秒的入口（{@code HoannaHazards.tickPlayer}）走 5 秒：不掉血、不着火。</li>
         * </ol>
         * 跑完停掉风暴、去掉覆盖、拆掉石台、清掉背包、剂量放回原值。
         */
        private Outcome c71() {
            List<String> problems = new ArrayList<>();
            int doseBefore = Radiation.get(owner);
            BlockPos pad = topRow(5);
            boolean spawned = false;
            int ownedAfterSpawn = -1;
            boolean removed = false;
            int crystals = -1;
            int expectedCrystals = -1;
            int ownedAfter = -1;
            int exposure = -1;
            float loss = -1F;
            boolean burning = true;
            LeyStarfallEntity starfall = null;
            owner.setGameMode(GameType.SURVIVAL);
            ECHoanna.setTestHoannaOverride(level);
            LeyStorm.setTestStarfallChance(1F);
            LeyStorm.setTestStarfallDistance(2, 3);
            try {
                for (BlockPos pos : BlockPos.betweenClosed(pad.offset(-3, 0, -3), pad.offset(3, 0, 3))) {
                    set(pos.immutable(), Blocks.STONE.defaultBlockState());
                }
                standAt(owner, pad.above());
                owner.getInventory().clearContent();
                Radiation.set(owner, 0);
                if (!HoannaEvents.start(level, HoannaEvent.LEY_STORM, 1200)) {
                    return new Outcome(false, "开得了地脉风暴", "HoannaEvents.start 返回 false", problems);
                }
                starfall = LeyStorm.trySpawnStarfall(owner, RandomSource.create(20261101L));
                spawned = starfall != null && !starfall.isRemoved();
                ownedAfterSpawn = LeyStorm.owned(OWNER_ID);
                if (starfall != null) {
                    expectedCrystals = LeyStorm.crystalsFor(level);
                    // 落地以前碰到不收（下落只是客户端的画法），这里当成已经落地
                    starfall.landNowForTest();
                    starfall.playerTouch(owner);
                    removed = starfall.isRemoved();
                    crystals = owner.getInventory().countItem(ECHoannaMaterials.LEY_CRYSTAL.get());
                    ownedAfter = LeyStorm.owned(OWNER_ID);
                }
                exposure = LeyStorm.exposure(owner, level);
                float health = owner.getHealth();
                for (int i = 0; i < 5; i++) {
                    HoannaHazards.tickPlayer(owner);
                }
                loss = health - owner.getHealth();
                burning = owner.isOnFire();
            } finally {
                HoannaEvents.stop(level);
                LeyStorm.setTestStarfallChance(null);
                LeyStorm.setTestStarfallDistance(-1, -1);
                ECHoanna.clearTestOverride();
                if (starfall != null && !starfall.isRemoved()) {
                    starfall.discard();
                }
                clearBox(pad.offset(-3, 0, -3), pad.offset(3, 0, 3));
                owner.getInventory().clearContent();
                Radiation.set(owner, doseBefore);
                owner.setHealth(owner.getMaxHealth());
            }
            int radiation = HoannaHazardsConfig.leyStormRadiation();
            String actual = "落晶生成 " + spawned + " 名下 " + ownedAfterSpawn + "；碰到 消失 " + removed + " 结晶 " + crystals + " 名下 " + ownedAfter
                    + "；一秒剂量 +" + exposure + "；5 秒 掉血 " + fmt(loss) + " 着火 " + burning;
            boolean pass = spawned && ownedAfterSpawn == 1 && removed && crystals == expectedCrystals && ownedAfter == 0
                    && exposure == radiation && loss == 0F && !burning;
            return new Outcome(pass, "落晶生成 true 名下 1；碰到 消失 true 结晶 " + expectedCrystals + " 名下 0；一秒剂量 +" + radiation
                    + "；5 秒 掉血 0.0 着火 false", actual, problems);
        }

        /**
         * C72：封印宝库的锁孔、祭座在别人领地里右键被插件拦下（第十三批 13e，batch13.md §6.4、§12.4）。照结构生成用的拼件摆一座宝库，
         * 前厅（锁孔和四座祭座）整个落在 other 区里（地面 = 区底 + 8，宝库深度 8，锁孔在区里第 1 层）：
         * <ol>
         *   <li>主人（生存，不是这块领地的主人）手持地脉结晶右键锁孔、再手持第一座该放的魔晶右键那座祭座：插件取消两次交互，
         *       结晶、魔晶一个不少，锁孔没亮、核心的进度还是 0、没记失败、没掉血；</li>
         *   <li>对照：拆掉、在野外同样摆一座，同样两下：锁孔收走 1 个结晶并亮起，祭座收走 1 个魔晶、进度到 1。</li>
         * </ol>
         * 宝库的右键全走原版交互事件，不另调权限探测，所以这条测的就是「领地插件拦得到这几次右键」。跑完拆掉宝库、清背包和掉落物。
         */
        private Outcome c72() {
            List<String> problems = new ArrayList<>();
            owner.setGameMode(GameType.SURVIVAL);
            VaultTry denied;
            VaultTry wild;
            Probe deniedProbe;
            try {
                System.setProperty(PROP_EVENTS, "");
                denied = vaultTry(OTHERS);
                deniedProbe = Probe.read();
                clearVault(OTHERS);
                wild = vaultTry(WILD);
            } finally {
                clearVault(OTHERS);
                clearVault(WILD);
                owner.getInventory().clearContent();
                owner.setHealth(owner.getMaxHealth());
            }
            deniedProbe.expect(problems, "PlayerInteractEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            boolean deniedOk = denied.found() && denied.leyUsed() == 0 && denied.crystalUsed() == 0 && !denied.keyed() && denied.step() == 0
                    && denied.fails() == 0 && denied.loss() == 0F;
            boolean wildOk = wild.found() && wild.leyUsed() == 1 && wild.keyed() && wild.crystalUsed() == 1 && wild.step() == 1;
            return new Outcome(deniedOk && wildOk,
                    "other 区 结晶 -0 魔晶 -0 锁孔亮 false 进度 0 失败 0 掉血 0.0；野外 结晶 -1 锁孔亮 true 魔晶 -1 进度 1",
                    "other 区 " + denied + "；野外 " + wild, problems);
        }

        private record VaultTry(boolean found, int leyUsed, int crystalUsed, boolean keyed, int step, int fails, float loss) {
        }

        /** C72 的一座：锁孔在 zone 的 (5, 1, 6)，祭座在它东南西北 3 格 */
        private VaultLayout.Plan vaultPlan(int zone) {
            BlockPos keyhole = zoneBase(zone).offset(5, 1, 6);
            // 深度按地面的绝对高度定（地面 − 56，夹在 8~20），自检区在高空，倒推出让前厅地板正好在区底的地面
            int floor = keyhole.getY() - 1;
            int ground = floor + VaultLayout.MAX_DEPTH;
            for (int g = floor + VaultLayout.MIN_DEPTH; g <= floor + VaultLayout.MAX_DEPTH; g++) {
                if (g - VaultLayout.depthFor(g) == floor) {
                    ground = g;
                    break;
                }
            }
            return VaultLayout.plan(keyhole.getX() - VaultLayout.ANTE_OFFSET, ground, keyhole.getZ() - VaultLayout.ANTE_OFFSET, level.getMinBuildHeight());
        }

        private VaultTry vaultTry(int zone) {
            VaultLayout.Plan plan = vaultPlan(zone);
            BlockPos corePos = SealedVaultStructure.placeDirect(level, plan.ox(), plan.ground(), plan.oz(), 0x72C72L + zone);
            VaultCoreBlockEntity core = VaultCoreBlockEntity.at(level, corePos);
            if (core == null || !plan.keyhole().equals(core.keyholePos())) {
                return new VaultTry(false, -1, -1, false, -1, -1, -1F);
            }
            Direction side = Direction.Plane.HORIZONTAL.stream()
                    .filter(d -> VaultCoreBlockEntity.pedestalSide(level, plan.pedestal(d)) == d && expectedAt(core, d))
                    .findFirst().orElse(Direction.NORTH);
            Item crystal = core.puzzle().expected(0).crystal();
            standAt(owner, plan.keyhole().offset(1, 0, 1));
            owner.getInventory().clearContent();
            owner.getInventory().selected = 0;
            float health = owner.getHealth();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get(), 4));
            useOn(plan.keyhole());
            int leyUsed = 4 - owner.getInventory().countItem(ECHoannaMaterials.LEY_CRYSTAL.get());
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crystal, 4));
            useOn(plan.pedestal(side));
            int crystalUsed = 4 - owner.getInventory().countItem(crystal);
            BlockState keyhole = level.getBlockState(plan.keyhole());
            boolean keyed = keyhole.hasProperty(VaultKeyholeBlock.KEYED) && keyhole.getValue(VaultKeyholeBlock.KEYED);
            return new VaultTry(true, leyUsed, crystalUsed, keyed, core.step(), core.failCount(OWNER_ID), health - owner.getHealth());
        }

        /** 这座祭座是不是第一个该放的（按核心里存的谜题） */
        private boolean expectedAt(VaultCoreBlockEntity core, Direction side) {
            BlockState state = level.getBlockState(core.pedestalPos(side));
            return state.hasProperty(VaultPedestalBlock.ELEMENT) && state.getValue(VaultPedestalBlock.ELEMENT) == core.puzzle().expected(0);
        }

        private void useOn(BlockPos target) {
            owner.gameMode.useItemOn(owner, level, owner.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
        }

        private void clearVault(int zone) {
            BoundingBox box = vaultPlan(zone).bounds();
            BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
            BlockPos max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
            clearBox(min, max);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(min).minmax(new AABB(max)))) {
                drop.discard();
            }
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(min).minmax(new AABB(max)).inflate(4D))) {
                mob.discard();
            }
        }

        /**
         * C73：天灾信标在别人领地里被拒、不消耗（第十三批 13e，batch13.md §5.3、§12.4）。当成海纳尔、此刻日阵、矩阵阶的门关掉，
         * 主人（生存）手持满的天灾信标：
         * <ol>
         *   <li>召唤点在 other 区正中（第 1 层；平整度按 25 × 25 抽列高，底下铺一整片 25 × 25 的石头）：五处探测里中心那处被插件拒 → DENIED，信标还在手里、还是满的，没有首领；</li>
         *   <li>召唤点在 owner 区和 other 区之间的空地（谁的领地都不是），东边那根灾柱的落脚点（中心 +12 格）落进 other 区：同样 DENIED、什么都不扣。</li>
         * </ol>
         * 不跑「野外召出来」的对照（GameTest {@code CalamityHeartTests} 已覆盖；在这里召出来要多清一场首领）。跑完拆掉两片石头、去掉覆盖。
         */
        private Outcome c73() {
            List<String> problems = new ArrayList<>();
            owner.setGameMode(GameType.SURVIVAL);
            ECHoanna.setTestHoannaOverride(level);
            CalamityHeartSummon.setTestCalamity(HoannaEvent.SUN_ARRAY);
            MatrixRank.setTestOverrides(false, null, null, null);
            BlockPos centerIn = zoneBase(OTHERS).offset(5, 1, 5);
            BlockPos centerGap = zoneBase(OWN).offset(15, 1, 5);
            CalamityHeartSummon.Outcome inside;
            CalamityHeartSummon.Outcome pillar;
            boolean beaconKept;
            Probe deniedProbe;
            try {
                System.setProperty(PROP_EVENTS, "");
                inside = heartTry(centerIn);
                deniedProbe = Probe.read();
                beaconKept = beaconFull();
                pillar = heartTry(centerGap);
                beaconKept &= beaconFull();
            } finally {
                ECHoanna.clearTestOverride();
                CalamityHeartSummon.clearTestCalamity();
                MatrixRank.clearTestOverrides();
                heartSlab(centerIn, Blocks.AIR.defaultBlockState());
                heartSlab(centerGap, Blocks.AIR.defaultBlockState());
                owner.getInventory().clearContent();
            }
            deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            boolean pass = inside.result() == CalamityHeartSummon.Result.DENIED && inside.heart() == null
                    && pillar.result() == CalamityHeartSummon.Result.DENIED && pillar.heart() == null && beaconKept;
            if (inside.heart() != null) {
                inside.heart().discard();
            }
            if (pillar.heart() != null) {
                pillar.heart().discard();
            }
            return new Outcome(pass, "区里 DENIED 无首领；灾柱落进区里 DENIED 无首领；信标还在、是满的",
                    "区里 " + inside.result() + " 首领 " + (inside.heart() != null) + "；灾柱落进区里 " + pillar.result() + " 首领 " + (pillar.heart() != null)
                            + "；信标还在、是满的 " + beaconKept, problems);
        }

        private CalamityHeartSummon.Outcome heartTry(BlockPos floor) {
            // 平整度按中心 25 × 25 抽列高：自检区在高空，铺一整片石头当地面（跑完拆掉）
            heartSlab(floor, Blocks.STONE.defaultBlockState());
            standAt(owner, floor.offset(2, 0, 0));
            owner.getInventory().clearContent();
            owner.getInventory().selected = 0;
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECCalamityHeart.CALAMITY_BEACON.get()));
            ECProtection.forget(OWNER_ID);
            return CalamityHeartSummon.summon(owner, level, floor, owner.getMainHandItem());
        }

        private void heartSlab(BlockPos floor, BlockState state) {
            int half = 12;
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-half, -1, -half), floor.offset(half, -1, half))) {
                if (!level.getBlockState(pos).equals(state)) {
                    set(pos.immutable(), state);
                }
            }
        }

        private boolean beaconFull() {
            ItemStack held = owner.getMainHandItem();
            return held.is(ECCalamityHeart.CALAMITY_BEACON.get()) && held.getCount() == 1 && !CalamityBeaconItem.isSpent(held);
        }

        /**
         * C74：沉城祭坛在别人领地里召唤被拒、不扣材料（第十三批 13e，batch13.md §6.4、§12.4）。other 区第 1 层摆一座祭坛，
         * 主人（生存）手持潮晶 ×4、背包里异变之心 ×1，照祭坛的入口召一次：祭坛那一格以他的身份探测被插件拒 → DENIED，
         * 潮晶、异变之心一个不少，祭坛没记今天，没有领主。对照：野外同一处摆法，过了权限这一步（到「找不到水」那一步才停，BLOCKED），同样不扣。
         */
        private Outcome c74() {
            List<String> problems = new ArrayList<>();
            owner.setGameMode(GameType.SURVIVAL);
            BlockPos inside = zoneBase(OTHERS).offset(5, 1, 5);
            BlockPos wild = zoneBase(WILD).offset(5, 1, 5);
            SunkenAltarBlock.Outcome denied;
            SunkenAltarBlock.Outcome open;
            int[] deniedLeft;
            int[] wildLeft;
            Probe deniedProbe;
            BossDaily.Stamp today = BossDaily.now(server);
            try {
                System.setProperty(PROP_EVENTS, "");
                denied = altarTry(inside, today);
                deniedProbe = Probe.read();
                deniedLeft = altarMaterials();
                open = altarTry(wild, today);
                wildLeft = altarMaterials();
            } finally {
                set(inside, Blocks.AIR.defaultBlockState());
                set(wild, Blocks.AIR.defaultBlockState());
                owner.getInventory().clearContent();
            }
            deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            if (denied.tyrant() != null) {
                denied.tyrant().discard();
            }
            if (open.tyrant() != null) {
                open.tyrant().discard();
            }
            boolean pass = denied.result() == SunkenAltarBlock.Result.DENIED && denied.tyrant() == null && deniedLeft[0] == 4 && deniedLeft[1] == 1
                    && open.result() != SunkenAltarBlock.Result.DENIED && open.tyrant() == null && wildLeft[0] == 4 && wildLeft[1] == 1;
            return new Outcome(pass, "区里 DENIED 潮晶 4 异变之心 1 无领主；野外过了权限（不是 DENIED）、找不到水不召、不扣",
                    "区里 " + denied.result() + " 潮晶 " + deniedLeft[0] + " 异变之心 " + deniedLeft[1] + " 领主 " + (denied.tyrant() != null)
                            + "；野外 " + open.result() + " 潮晶 " + wildLeft[0] + " 异变之心 " + wildLeft[1], problems);
        }

        private SunkenAltarBlock.Outcome altarTry(BlockPos pos, BossDaily.Stamp today) {
            set(pos, ECSunkenDistrict.SUNKEN_ALTAR.get().defaultBlockState());
            standAt(owner, pos.offset(2, 0, 0));
            owner.getInventory().clearContent();
            owner.getInventory().selected = 0;
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), 4));
            owner.getInventory().add(new ItemStack(ECMobDrops.ABERRANT_HEART.get(), 1));
            ECProtection.forget(OWNER_ID);
            return SunkenAltarBlock.summon(owner, level, pos, today);
        }

        private int[] altarMaterials() {
            return new int[]{owner.getInventory().countItem(ECHoannaMaterials.TIDE_CRYSTAL.get()), owner.getInventory().countItem(ECMobDrops.ABERRANT_HEART.get())};
        }

        /**
         * C75：全息投影的拉回落点在被拉的人动不了的领地里（第十三批 13e-T，batch13.md「13e 已定事项」第 2 条定为 A）。other 区第 1 层垫一块石头当投影台原位，
         * 上面放一只不带 AI 的全息投影（召唤者 = 主人，拉回落点就是那一格，在 other 区里）；主人站到 30 格外：
         * 查一次名单 → 落点以他的身份探测被插件拒，不拉、不扣血、位置不变，仍在名单里，按拉不回来记 {@code ROSTER_INTERVAL} 刻；
         * 接着连查到拉不回来满 {@code general.hologram.abandon_ticks}，才被移出名单（差一次时还在）。审判庭哨兵继承同一套，不另跑。
         */
        private Outcome c75() {
            List<String> problems = new ArrayList<>();
            BlockPos pad = zoneBase(OTHERS).offset(5, 1, 5);
            HologramEntity boss = null;
            boolean stillListed = false;
            int stranded = -1;
            float loss = -1F;
            double moved = -1D;
            int checksToDrop = -1;
            boolean listedBeforeLast = false;
            int abandon = ECHologram.Config.values().abandonTicks();
            Probe deniedProbe = null;
            owner.setGameMode(GameType.SURVIVAL);
            try {
                clearBox(pad.offset(-2, 0, -2), pad.offset(2, 4, 2));
                set(pad.below(), Blocks.STONE.defaultBlockState());
                boss = ECHologram.HOLOGRAM.get().create(level);
                if (boss == null) {
                    return new Outcome(false, "全息投影进得了世界", "生成失败", problems);
                }
                boss.moveTo(pad.getX() + 0.5D, pad.getY() + HologramEntity.SUMMON_HEIGHT, pad.getZ() + 0.5D, 0F, 0F);
                boss.setNoAi(true);
                standAt(owner, pad.offset(2, 0, 0));
                boss.prepareSummon(owner, pad);
                if (!level.addFreshEntity(boss)) {
                    return new Outcome(false, "全息投影进得了世界", "addFreshEntity 失败", problems);
                }
                BlockPos away = pad.offset(-30, 0, 0);
                standAt(owner, away);
                Vec3 before = owner.position();
                float health = owner.getHealth();
                System.setProperty(PROP_EVENTS, "");
                boss.tickRoster();
                deniedProbe = Probe.read();
                stillListed = boss.isParticipant(OWNER_ID);
                stranded = boss.strandedTicks(OWNER_ID);
                loss = health - owner.getHealth();
                moved = owner.position().distanceTo(before);
                int checks = 1;
                while (boss.isParticipant(OWNER_ID) && checks < 1000) {
                    listedBeforeLast = true;
                    boss.tickRoster();
                    checks++;
                }
                checksToDrop = boss.isParticipant(OWNER_ID) ? -1 : checks;
            } finally {
                if (boss != null && !boss.isRemoved()) {
                    boss.discard();
                }
                clearBox(pad.offset(-2, -1, -2), pad.offset(2, 4, 2));
                owner.getInventory().clearContent();
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            if (deniedProbe != null) {
                deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            }
            int expectedChecks = (abandon + HologramEntity.ROSTER_INTERVAL - 1) / HologramEntity.ROSTER_INTERVAL;
            String actual = "落点被拒 仍在名单 " + stillListed + " 记拉不回来 " + stranded + " 刻 掉血 " + fmt(loss) + " 位移 " + String.format("%.2f", moved)
                    + "；查 " + checksToDrop + " 次后移出（差一次时在名单 " + listedBeforeLast + "）";
            boolean pass = stillListed && stranded == HologramEntity.ROSTER_INTERVAL && loss == 0F && moved < 1.0E-3D && checksToDrop == expectedChecks
                    && listedBeforeLast;
            return new Outcome(pass, "落点被拒 仍在名单 true 记拉不回来 " + HologramEntity.ROSTER_INTERVAL + " 刻 掉血 0.0 位移 0.00；查 " + expectedChecks
                    + " 次后移出（差一次时在名单 true）", actual, problems);
        }

        /**
         * C76：首领拉回的落点在被拉的人动不了的领地里（第十三批 13c 留下的一条，batch13.md §12.4）。
         * <ol>
         *   <li>other 区第 4 层垫一块石头，上面放一只不带 AI、没有契约的矩阵之影（拉回落点就在这块石头上面，在 other 区里），主人（生存，不是这块领地的主人）
         *       进名单后站到 30 格外（拉回距离和脱战距离之间）：查一次名单 → 落点以他的身份探测被插件拒，不拉、不扣血、位置不变，仍在名单里、仍是技能目标；
         *       接着连查到拉不回来的时间满 {@code abandon_ticks}，才被移出名单（差一次时还在）。</li>
         *   <li>契约：再放一只、给主人记一份契约（不转损伤、不扣 ESPE），主人站在拉回距离以外，这只矩阵之影一刻也不走（同混合端实体激活距离以外不 tick），
         *       照每秒的入口（{@code checkSummoners}，和服务端每刻末调的是同一个）推 {@code summoner_away_ticks} 刻 → 契约判失败、关掉；差一秒时还开着。</li>
         * </ol>
         * 跑完删掉两只矩阵之影和契约记录、拆掉石头、主人回满血、清掉「正在打首领」的记号和背包。
         */
        private Outcome c76() {
            List<String> problems = new ArrayList<>();
            BlockPos pad = zoneBase(OTHERS).offset(5, 3, 5);
            MatrixShadeEntity shade = null;
            MatrixShadeEntity contracted = null;
            boolean listed = false;
            boolean stillListed = false;
            boolean stillTarget = false;
            float loss = -1F;
            double moved = -1D;
            int checksToDrop = -1;
            boolean listedBeforeLast = false;
            int abandon = ECBossFramework.Config.values().abandonTicks();
            int awayLimit = ECMatrixShade.Config.values().summon().summonerAwayTicks();
            boolean openBeforeLast = false;
            boolean openAfter = true;
            int shadeTicks = -1;
            Probe deniedProbe = null;
            owner.setGameMode(GameType.SURVIVAL);
            try {
                clearBox(pad.offset(-2, 0, -2), pad.offset(2, 4, 2));
                set(pad, Blocks.STONE.defaultBlockState());
                shade = shadeAt(pad);
                if (shade == null) {
                    return new Outcome(false, "矩阵之影进得了世界", "生成或 addFreshEntity 失败", problems);
                }
                standAt(owner, pad.above().offset(2, 0, 0));
                shade.bossRoster().add(owner);
                listed = shade.bossRoster().contains(OWNER_ID);

                BlockPos away = pad.above().offset(-30, 0, 0);
                standAt(owner, away);
                Vec3 before = owner.position();
                float health = owner.getHealth();
                System.setProperty(PROP_EVENTS, "");
                shade.bossRoster().check(level);
                deniedProbe = Probe.read();
                stillListed = shade.bossRoster().contains(OWNER_ID);
                stillTarget = shade.isSkillTarget(owner);
                loss = health - owner.getHealth();
                moved = owner.position().distanceTo(before);
                int checks = 1;
                while (shade.bossRoster().contains(OWNER_ID) && checks < 1000) {
                    listedBeforeLast = true;
                    shade.bossRoster().check(level);
                    checks++;
                }
                checksToDrop = shade.bossRoster().contains(OWNER_ID) ? -1 : checks;
                BossRoster.forgetFighter(OWNER_ID);

                contracted = shadeAt(pad);
                if (contracted == null) {
                    return new Outcome(false, "第二只矩阵之影进得了世界", "生成或 addFreshEntity 失败", problems);
                }
                MatrixShadeContracts.open(level, contracted, owner, 0, pad, 0D);
                standAt(owner, away);
                int startTicks = contracted.tickCount;
                MatrixShadeContracts contracts = MatrixShadeContracts.get(server);
                int elapsed = 0;
                while (elapsed + MatrixShadeContracts.CHECK_INTERVAL < awayLimit) {
                    contracts.checkSummoners(server, MatrixShadeContracts.CHECK_INTERVAL);
                    elapsed += MatrixShadeContracts.CHECK_INTERVAL;
                }
                openBeforeLast = MatrixShadeContracts.isOpen(level, contracted.getUUID());
                contracts.checkSummoners(server, MatrixShadeContracts.CHECK_INTERVAL);
                openAfter = MatrixShadeContracts.isOpen(level, contracted.getUUID());
                shadeTicks = contracted.tickCount - startTicks;
            } finally {
                for (MatrixShadeEntity entity : new MatrixShadeEntity[]{shade, contracted}) {
                    if (entity != null) {
                        MatrixShadeContracts.forgetForTest(level, entity.getUUID(), OWNER_ID, pad);
                        if (!entity.isRemoved()) {
                            entity.discard();
                        }
                    }
                }
                BossRoster.forgetFighter(OWNER_ID);
                clearBox(pad.offset(-2, 0, -2), pad.offset(2, 4, 2));
                owner.getInventory().clearContent();
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            if (deniedProbe != null) {
                deniedProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            }
            int expectedChecks = (abandon + BossRoster.CHECK_INTERVAL - 1) / BossRoster.CHECK_INTERVAL;
            String actual = "进名单 " + listed + "；落点被拒 仍在名单 " + stillListed + " 仍是技能目标 " + stillTarget + " 掉血 " + fmt(loss)
                    + " 位移 " + String.format("%.2f", moved) + "；查 " + checksToDrop + " 次后移出（差一次时在名单 " + listedBeforeLast + "）"
                    + "；契约 差一秒时开着 " + openBeforeLast + " 到点后开着 " + openAfter + " 首领走了 " + shadeTicks + " 刻";
            boolean pass = listed && stillListed && stillTarget && loss == 0F && moved < 1.0E-3D && checksToDrop == expectedChecks && listedBeforeLast
                    && openBeforeLast && !openAfter && shadeTicks == 0;
            return new Outcome(pass, "进名单 true；落点被拒 仍在名单 true 仍是技能目标 true 掉血 0.0 位移 0.00；查 " + expectedChecks
                    + " 次后移出（差一次时在名单 true）；契约 差一秒时开着 true 到点后开着 false 首领走了 0 刻", actual, problems);
        }

        /** C76：pad 上面放一只不带 AI 的矩阵之影；放不进世界返回 null */
        @Nullable
        private MatrixShadeEntity shadeAt(BlockPos pad) {
            MatrixShadeEntity shade = ECMatrixShade.MATRIX_SHADE.get().create(level);
            if (shade == null) {
                return null;
            }
            shade.moveTo(pad.getX() + 0.5D, pad.getY() + 1D, pad.getZ() + 0.5D, 0F, 0F);
            shade.setNoAi(true);
            return level.addFreshEntity(shade) ? shade : null;
        }


        // ------------------------------------------------------------------ 第十批 10c：审判庭信标与哨兵

        /**
         * C40：审判庭信标与审判庭哨兵（第十批 10c-M1，batch10 §5.10.4~5、§8.2；同 C32 的写法）。临时把信标能召唤的维度换成这个世界、
         * 固定成夜里（{@code ECInquisitorium.Rules} 与 {@code InquisitoriumBeaconBlock.setTestNight} 的测试覆盖，不改配置文件和世界时间），
         * 两个信标都记着自己所在那一格的坐标（到坐标的距离是 0）：
         * <ol>
         *   <li>另一名玩家在 owner 区（只有主人能动）右键信标：{@code canEdit} 的破坏探测被插件拒 → DENIED，信标还在、没有哨兵；</li>
         *   <li>主人在野外召唤：OK，信标没了，主人在名单里；</li>
         *   <li>主人走到 30 格外（24~64 格之间，会被拉回），设 {@value #PROP_CANCEL_TELEPORT} 让插件取消下一次传送，推进一次名单：
         *       拉回的传送被取消 → 不拉、不扣血、位置不变，主人仍在名单里（只有跑出 escape_range 才脱战）。</li>
         * </ol>
         * 跑完删掉哨兵、拆掉信标和垫的石头、撤掉测试覆盖。
         */
        private Outcome c40() {
            List<String> problems = new ArrayList<>();
            BlockPos deniedBeacon = topRow(ZONE_STEP + 7);
            BlockPos wildBeacon = topRow(7);
            InquisitoriumSentinelEntity sentinel = null;
            InquisitoriumBeaconBlock.Result denied;
            boolean deniedKept;
            boolean deniedNoSentinel;
            InquisitoriumBeaconBlock.Result wild = null;
            boolean wildGone = false;
            boolean summonerListed = false;
            boolean stillListed = false;
            float healthLoss = -1F;
            double moved = -1D;
            Probe teleportProbe = null;
            ECInquisitorium.Rules.setTestOverrides(ECInquisitorium.Rules.DEFAULTS.withSummonDimensions(List.of(level.dimension().location().toString())));
            InquisitoriumBeaconBlock.setTestNight(true);
            try {
                for (BlockPos pad : List.of(deniedBeacon, wildBeacon)) {
                    set(pad.below(), Blocks.STONE.defaultBlockState());
                    set(pad, ECInquisitorium.INQUISITORIUM_BEACON.get().defaultBlockState());
                    if (level.getBlockEntity(pad) instanceof InquisitoriumBeaconBlockEntity beacon) {
                        beacon.setTarget(new InquisitoriumCoordinatesItem.Target(level.dimension().location(), pad.getX(), pad.getZ()));
                    }
                }
                standAt(other, deniedBeacon.offset(0, 0, -2));
                denied = InquisitoriumBeaconBlock.summon(other, level, deniedBeacon).result();
                deniedKept = level.getBlockState(deniedBeacon).is(ECInquisitorium.INQUISITORIUM_BEACON.get());
                deniedNoSentinel = level.getEntitiesOfClass(InquisitoriumSentinelEntity.class, new AABB(deniedBeacon).inflate(8D)).isEmpty();
                Probe deniedProbe = Probe.read();
                deniedProbe.expect(problems, "BlockBreakEvent", OWN, OTHER_NAME, "CraftPlayer", true);
                System.setProperty(PROP_EVENTS, "");

                standAt(owner, wildBeacon.offset(0, 0, -2));
                InquisitoriumBeaconBlock.Outcome outcome = InquisitoriumBeaconBlock.summon(owner, level, wildBeacon);
                wild = outcome.result();
                sentinel = outcome.sentinel();
                wildGone = level.getBlockState(wildBeacon).isAir();
                if (sentinel != null) {
                    summonerListed = sentinel.isParticipant(OWNER_ID);
                    standAt(owner, wildBeacon.offset(30, 0, 0));
                    Vec3 before = owner.position();
                    float health = owner.getHealth();
                    System.setProperty(PROP_EVENTS, "");
                    System.setProperty(PROP_CANCEL_TELEPORT, OWNER_NAME);
                    sentinel.tickRoster();
                    stillListed = sentinel.isParticipant(OWNER_ID);
                    healthLoss = health - owner.getHealth();
                    moved = owner.position().distanceTo(before);
                    teleportProbe = Probe.read();
                }
            } finally {
                System.clearProperty(PROP_CANCEL_TELEPORT);
                ECInquisitorium.Rules.setTestOverrides(null);
                InquisitoriumBeaconBlock.setTestNight(null);
                if (sentinel != null && !sentinel.isRemoved()) {
                    sentinel.discard();
                }
                for (BlockPos pad : List.of(deniedBeacon, wildBeacon)) {
                    set(pad, Blocks.AIR.defaultBlockState());
                    set(pad.below(), Blocks.AIR.defaultBlockState());
                }
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            Map<String, String> teleport = teleportProbe == null ? null : teleportProbe.first("PlayerTeleportEvent");
            if (sentinel != null && (teleport == null || !"true".equals(teleport.get("cancelled")))) {
                problems.add("插件应收到并取消一次主人的 PlayerTeleportEvent，实际 " + teleportSummary(teleport));
            }
            String actual = "领地 " + denied + " 信标" + (deniedKept ? "还在" : "没了") + " 哨兵" + (deniedNoSentinel ? "没有" : "有")
                    + "；野外 " + wild + " 信标" + (wildGone ? "没了" : "还在") + " 召唤者在名单 " + summonerListed
                    + "；拉回被取消 仍在名单 " + stillListed + " 掉血 " + fmt(healthLoss) + " 位移 " + String.format("%.2f", moved);
            boolean pass = denied == InquisitoriumBeaconBlock.Result.DENIED && deniedKept && deniedNoSentinel
                    && wild == InquisitoriumBeaconBlock.Result.OK && wildGone && summonerListed && stillListed && healthLoss == 0F && moved < 1.0E-3D;
            return new Outcome(pass, "领地 DENIED 信标还在 哨兵没有；野外 OK 信标没了 召唤者在名单 true；拉回被取消 仍在名单 true 掉血 0.0 位移 0.00",
                    actual, problems);
        }

        // ------------------------------------------------------------------ 第十批 10c：回溯模块

        /**
         * C41：回溯模块在混合端的死亡事件顺序（第十批 10c-M2，batch10 §5.11.1、§8.2）。混合端的 {@code ServerPlayer.die} 先发 Forge 的
         * {@code LivingDeathEvent}、被取消就返回，Bukkit 的 {@code PlayerDeathEvent} 在后面。用单独的一名玩家（穿齐宅男套装、头盔装回溯、胸甲自存 20 万 MRU，
         * 先跑掉出生无敌；这组会让它死一次，不能用主人），野外区 z = 9 那一排（3×3×3 小格子用不到）摆两块石头 A、B：
         * <ol>
         *   <li>站 A 记一条 5 秒前的快照（生命 14），站到 B 挨一下 1000 点魔法伤害（indirect_magic）：救下——人回到 A、生命 14、四件盔甲都在、扣 mru_cost、加矩阵损伤、进冷却；
         *       插件收不到 PlayerDeathEvent，收到一条没被取消的 PlayerTeleportEvent；</li>
         *   <li>清掉冷却、在 A 再记一条、回到 B，设 {@value #PROP_CANCEL_TELEPORT} 让插件取消下一次传送，再挨一下：传送被取消 → 不救、不扣、不进冷却，照常死亡，
         *       插件收到这名玩家的 PlayerDeathEvent（1.4.0 版测试插件起这一行带玩家名；旧版只数条数）。这一步临时打开 keepInventory，死了不撒东西。</li>
         * </ol>
         * 跑完拆掉石头、删掉附近的掉落物和经验球、放回 keepInventory。
         */
        private Outcome c41() {
            List<String> problems = new ArrayList<>();
            UUID id = UUID.nameUUIDFromBytes("ec5-selftest:rewinder".getBytes(StandardCharsets.UTF_8));
            String name = "EC5TestRewinder";
            ServerPlayer rewinder = realPlayer(id, name);
            for (int i = 0; i <= 60; i++) {
                rewinder.tick();
            }
            com.flameforge.essentialcraft5.item.module.ModuleType rewind = com.flameforge.essentialcraft5.registry.ECSurvivalModules.REWIND;
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                ItemStack piece = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
                if (type == ArmorItem.Type.HELMET) {
                    piece = com.flameforge.essentialcraft5.item.module.ArmorModules.install(piece, rewind);
                }
                rewinder.setItemSlot(type.getSlot(), piece);
            }
            int startMru = 200_000;
            rewinder.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(startMru));
            com.flameforge.essentialcraft5.item.module.SurvivalModuleConfig.Rewind cfg = com.flameforge.essentialcraft5.item.module.SurvivalModuleConfig.rewind();
            BlockPos floorA = zoneBase(WILD).offset(2, 4, 9);
            BlockPos floorB = zoneBase(WILD).offset(6, 4, 9);
            Vec3 standA = new Vec3(floorA.getX() + 0.5D, floorA.getY() + 1D, floorA.getZ() + 0.5D);
            net.minecraft.world.level.GameRules.BooleanValue keepInventory = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
            boolean keepBefore = keepInventory.get();
            String version = System.getProperty(PROP_PROBE, "0");
            String[] parts = version.split("\\.");
            boolean namedDeaths;
            try {
                namedDeaths = Integer.parseInt(parts[0]) > 1 || (Integer.parseInt(parts[0]) == 1 && parts.length > 1 && Integer.parseInt(parts[1]) >= 4);
            } catch (NumberFormatException e) {
                namedDeaths = false;
            }
            boolean rescued;
            double offA;
            float health1;
            boolean armorKept;
            int mru1;
            int matrix1;
            int cooldown1;
            Probe first;
            boolean died;
            int mru2;
            int cooldown2;
            Probe second;
            try {
                set(floorA, Blocks.STONE.defaultBlockState());
                set(floorB, Blocks.STONE.defaultBlockState());
                rewindStand(rewinder, floorA, 14F);
                com.flameforge.essentialcraft5.item.module.RewindEvents.record(rewinder, level.getGameTime() - 100L);
                rewindStand(rewinder, floorB, rewinder.getMaxHealth());
                System.setProperty(PROP_EVENTS, "");
                rewinder.hurt(rewindSpell(), 1000F);
                first = Probe.read();
                rescued = !rewinder.isDeadOrDying();
                offA = rewinder.position().distanceTo(standA);
                health1 = rewinder.getHealth();
                boolean kept = com.flameforge.essentialcraft5.item.module.ArmorModules.has(rewinder.getItemBySlot(EquipmentSlot.HEAD), rewind);
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    kept &= rewinder.getItemBySlot(type.getSlot()).getItem() == ECComputer.armor().get(type.ordinal()).get();
                }
                armorKept = kept;
                mru1 = rewindMru(rewinder);
                matrix1 = PlayerDataEvents.get(rewinder) == null ? -1 : PlayerDataEvents.get(rewinder).getMatrixDamage();
                cooldown1 = com.flameforge.essentialcraft5.item.module.ModuleCooldowns.remaining(rewinder, rewind);

                com.flameforge.essentialcraft5.item.module.ModuleCooldowns.clear(rewinder, rewind);
                rewindStand(rewinder, floorA, rewinder.getMaxHealth());
                com.flameforge.essentialcraft5.item.module.RewindEvents.record(rewinder, level.getGameTime() - 100L);
                rewindStand(rewinder, floorB, rewinder.getMaxHealth());
                keepInventory.set(true, server);
                System.setProperty(PROP_EVENTS, "");
                System.setProperty(PROP_CANCEL_TELEPORT, name);
                rewinder.hurt(rewindSpell(), 1000F);
                second = Probe.read();
                died = rewinder.isDeadOrDying();
                mru2 = rewindMru(rewinder);
                cooldown2 = com.flameforge.essentialcraft5.item.module.ModuleCooldowns.remaining(rewinder, rewind);
            } finally {
                System.clearProperty(PROP_CANCEL_TELEPORT);
                keepInventory.set(keepBefore, server);
                set(floorA, Blocks.AIR.defaultBlockState());
                set(floorB, Blocks.AIR.defaultBlockState());
                for (Entity entity : level.getEntities((Entity) null, new AABB(floorA).minmax(new AABB(floorB)).inflate(4D),
                        entity -> entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.ExperienceOrb)) {
                    entity.discard();
                }
                com.flameforge.essentialcraft5.item.module.RewindEvents.forgetForTest(rewinder);
                ECProtection.forget(id);
            }
            int deaths1 = first.countType("PlayerDeathEvent");
            Map<String, String> teleport1 = first.first("PlayerTeleportEvent");
            if (deaths1 != 0) {
                problems.add("救下的那次插件不该收到 PlayerDeathEvent，实际 " + first.summary());
            }
            if (teleport1 == null || !"false".equals(teleport1.get("cancelled"))) {
                problems.add("救下的那次插件应收到一条没被取消的 PlayerTeleportEvent，实际 " + teleportSummary(teleport1));
            }
            int deaths2 = second.countType("PlayerDeathEvent");
            Map<String, String> teleport2 = second.first("PlayerTeleportEvent");
            if (teleport2 == null || !"true".equals(teleport2.get("cancelled"))) {
                problems.add("第二次插件应收到并取消一次 PlayerTeleportEvent，实际 " + teleportSummary(teleport2));
            }
            if (namedDeaths) {
                second.expect(problems, "PlayerDeathEvent", WILD, name, "CraftPlayer", false);
            } else if (deaths2 == 0) {
                problems.add("没救下的那次插件应收到 PlayerDeathEvent，实际 " + second.summary());
            }
            String actual = "救下 " + rescued + " 离快照点 " + String.format("%.2f", offA) + " 生命 " + fmt(health1) + " 盔甲在 " + armorKept
                    + " 扣 " + (startMru - mru1) + " 矩阵损伤 " + matrix1 + " 冷却 " + cooldown1 + " 死亡事件 " + deaths1
                    + "；传送被取消 死了 " + died + " 再扣 " + (mru1 - mru2) + " 冷却 " + cooldown2 + " 死亡事件 " + deaths2;
            boolean pass = rescued && offA < 0.1D && health1 == 14F && armorKept && mru1 == startMru - cfg.mruCost() && matrix1 == cfg.matrixDamage()
                    && cooldown1 == cfg.cooldownTicks() && deaths1 == 0
                    && died && mru2 == mru1 && cooldown2 == 0 && deaths2 >= 1;
            return new Outcome(pass, "救下 true 离快照点 0.00 生命 14.0 盔甲在 true 扣 " + cfg.mruCost() + " 矩阵损伤 " + cfg.matrixDamage()
                    + " 冷却 " + cfg.cooldownTicks() + " 死亡事件 0；传送被取消 死了 true 再扣 0 冷却 0 死亡事件 1", actual, problems);
        }

        /** C41：一下别人放过来的魔法伤害（indirect_magic、不带实体）；宅男胸甲对 magic 类型整下免疫，用它打不出致命一击 */
        private net.minecraft.world.damagesource.DamageSource rewindSpell() {
            return new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC));
        }

        /** C41：站到 floor 那格顶上、着地、生命 health、清掉受击无敌 */
        private void rewindStand(ServerPlayer player, BlockPos floor, float health) {
            player.moveTo(floor.getX() + 0.5D, floor.getY() + 1D, floor.getZ() + 0.5D, 0F, 0F);
            player.setOnGround(true);
            player.setHealth(health);
            player.invulnerableTime = 0;
        }

        /** C41：胸甲自存的 MRU */
        private int rewindMru(ServerPlayer player) {
            return player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
        }

        // ------------------------------------------------------------------ 第十批 10c：力场穹顶

        /**
         * C42：力场穹顶以主人身份逐区块问领地（第十批 10c，batch10 §5.13、§8.2）。穹顶探测的是「区块中心、穹顶那一层」，区块中心不一定落在
         * 10×10 的区里，所以这一组临时把插件的原点（{@value #PROP_ORIGIN}）换成按区块对齐的 O'（原点所在区块的西北角，仍在强制加载的范围里）：
         * owner 区 [O'+20, O'+30) 里正好有一块区块的中心（O'.x + 24），other 区 [O'+40, O'+50) 里正好有下一块的中心（O'.x + 40），z 都是 O'.z + 8，
         * 穹顶和探测都在 O'.y + 5 那一层。穹顶放在两块区块交界、不属于任何区的空隙里（O'.x + 32），半径用实例钩子缩到 7，只碰到这两块区块。
         * 主人在线（在线覆盖）、MRU 满，判一次：
         * <ol>
         *   <li>插件正好收到 2 条主人的 BlockBreakEvent：owner 区那条放行、other 区那条被取消；没有放置事件；</li>
         *   <li>穹顶在工作，1 块可拦、1 块不可拦；owner 区那块里自然刷出的僵尸放置检查被拒、幻翼的生成被取消，other 区那块照常；</li>
         *   <li>清掉主人在 ECProtection 里的探测缓存再判一次：结论记在穹顶里，插件一条事件都收不到。</li>
         * </ol>
         * 跑完拆掉穹顶、把原点放回去。
         */
        private Outcome c42() {
            List<String> problems = new ArrayList<>();
            BlockPos aligned = new BlockPos(origin.getX() & ~15, origin.getY(), origin.getZ() & ~15);
            BlockPos domeAt = aligned.offset(32, 5, 8);
            BlockPos ownSpawn = aligned.offset(28, 5, 8);
            BlockPos otherSpawn = aligned.offset(36, 5, 8);
            String originBefore = System.getProperty(PROP_ORIGIN);
            String state = "-";
            int[] counts = {-1, -1, -1};
            boolean[] allowed = new boolean[2];
            boolean[] phantomCancelled = new boolean[2];
            Probe first = null;
            Probe second = null;
            try {
                System.setProperty(PROP_ORIGIN, aligned.getX() + "," + aligned.getY() + "," + aligned.getZ());
                set(domeAt, com.flameforge.essentialcraft5.registry.ECForceDome.FORCE_DOME.get().defaultBlockState());
                if (!(level.getBlockEntity(domeAt) instanceof com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity dome)) {
                    return new Outcome(false, "穹顶放得下", "没有方块实体", problems);
                }
                dome.setOwner(OWNER_ID, OWNER_NAME);
                dome.markPlaced(level.getGameTime());
                dome.setRadiusForTest(7);
                dome.getMRUStorage().setMRU(dome.getMRUStorage().getMaxMRU());
                takeProbe();
                dome.evaluateNow();
                first = takeProbe();
                state = String.valueOf(dome.status());
                counts = new int[] {dome.blockableChunks(), dome.deniedChunks(), dome.unconfirmedChunks()};
                allowed[0] = net.minecraftforge.event.ForgeEventFactory.checkSpawnPlacements(EntityType.ZOMBIE, level,
                        net.minecraft.world.entity.MobSpawnType.NATURAL, ownSpawn, level.getRandom(), true);
                allowed[1] = net.minecraftforge.event.ForgeEventFactory.checkSpawnPlacements(EntityType.ZOMBIE, level,
                        net.minecraft.world.entity.MobSpawnType.NATURAL, otherSpawn, level.getRandom(), true);
                phantomCancelled[0] = phantomCancelledAt(ownSpawn.above(10));
                phantomCancelled[1] = phantomCancelledAt(otherSpawn.above(10));
                ECProtection.forget(OWNER_ID);
                dome.evaluateNow();
                second = takeProbe();
            } finally {
                if (originBefore != null) {
                    System.setProperty(PROP_ORIGIN, originBefore);
                } else {
                    System.clearProperty(PROP_ORIGIN);
                }
                set(domeAt, Blocks.AIR.defaultBlockState());
                ECProtection.forget(OWNER_ID);
            }
            first.expect(problems, "BlockBreakEvent", OWN, OWNER_NAME, "CraftPlayer", false);
            first.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            first.expectCount(problems, "BlockBreakEvent", 2);
            first.expectCount(problems, "BlockPlaceEvent", 0);
            second.expectCount(problems, null, 0);
            String actual = "状态 " + state + " 可拦/不可拦/未确认 " + counts[0] + "/" + counts[1] + "/" + counts[2]
                    + "；owner 区刷怪放行 " + allowed[0] + " 幻翼取消 " + phantomCancelled[0]
                    + "；other 区刷怪放行 " + allowed[1] + " 幻翼取消 " + phantomCancelled[1]
                    + "；插件 第一次 " + first.summary() + " 第二次 " + second.summary();
            boolean pass = "WORKING".equals(state) && counts[0] == 1 && counts[1] == 1 && counts[2] == 0
                    && !allowed[0] && phantomCancelled[0] && allowed[1] && !phantomCancelled[1];
            return new Outcome(pass, "WORKING 1/1/0；owner 区刷怪被拒、幻翼取消；other 区照常；插件第一次 2 条破坏事件（owner 放行、other 取消），第二次没有事件",
                    actual, problems);
        }

        /** 新建一只幻翼站到 at、按自然刷出走一遍 FinalizeSpawn（不进世界）：生成有没有被取消 */
        private boolean phantomCancelledAt(BlockPos at) {
            net.minecraft.world.entity.monster.Phantom phantom = EntityType.PHANTOM.create(level);
            if (phantom == null) {
                return false;
            }
            phantom.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0F, 0F);
            net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(phantom, level, level.getCurrentDifficultyAt(at),
                    net.minecraft.world.entity.MobSpawnType.NATURAL, null, null);
            boolean cancelled = phantom.isSpawnCancelled();
            phantom.discard();
            return cancelled;
        }

        /**
         * C37：饰品压制的 mixin 在混合端照样生效（第十批 10a，batch10 §8.2，同 GameTest 的 CurioSealCoreTests）。用 {@code CuriosApi.registerCurio}
         * 把鹦鹉螺壳登记成探针饰品（每次 curioTick 计数、给 +4 护甲；只对登记之后新建的物品生效），另起一名玩家戴进腰带槽：
         * 压制前每刻 tick、护甲 +4、{@code findFirstCurio} 查得到；{@code hold + resolve} 压制以后 tick 不再增加、护甲回到基础、查不到；
         * 在遮罩作用域里反射构造的 {@code SPacketSyncStack} 带的是真物品（作用域外是空的）；另外注入点状态 {@link CurioSealMixinStatus#allApplied} 为真。
         */
        private Outcome c37() {
            List<String> problems = new ArrayList<>();
            if (!CurioSealMixinStatus.allApplied()) {
                problems.add("注入点没有全部到位：" + CurioSealMixinStatus.problems());
            }
            if (CurioSeal.availability() != CurioSeal.Availability.AVAILABLE) {
                return new Outcome(false, "压制核心可用", CurioSeal.availability() + " " + CurioSealMixinStatus.problems(), problems);
            }
            SealProbeCurio.register();
            ServerPlayer player = tickedPlayer("seal-probe", "EC5TestSealProbe");
            standAt(player, slot(WILD, 15).offset(1, 1, 1));
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (inventory == null || inventory.getStacksHandler("belt").isEmpty()) {
                return new Outcome(false, "玩家有 Curios 腰带槽", "没有", problems);
            }
            int ticksBefore;
            int ticksSealed;
            double armorBase;
            double armorWorn;
            double armorSealed;
            boolean foundBefore;
            boolean foundSealed;
            String packetInside = "-";
            String packetOutside = "-";
            try {
                armorBase = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                inventory.setEquippedCurio("belt", 0, new ItemStack(Items.NAUTILUS_SHELL));
                CuriosCompat.invalidate(player);
                curiosTick(player);
                curiosTick(player);
                ticksBefore = SealProbeCurio.ticks(player);
                armorWorn = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                foundBefore = inventory.findFirstCurio(Items.NAUTILUS_SHELL).isPresent();
                long now = CurioSeal.now(server);
                CurioSeal.hold(player, player, now + 1000);
                CurioSeal.resolveForTest(now, player);
                curiosTick(player);
                curiosTick(player);
                curiosTick(player);
                ticksSealed = SealProbeCurio.ticks(player);
                armorSealed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                foundSealed = inventory.findFirstCurio(Items.NAUTILUS_SHELL).isPresent();
                try {
                    Class<?> packetClass = Class.forName("top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack");
                    Class<?> typeClass = Class.forName("top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack$HandlerType");
                    Object equipment = typeClass.getEnumConstants()[0];
                    java.lang.reflect.Constructor<?> constructor = packetClass.getConstructor(int.class, String.class, int.class, ItemStack.class,
                            typeClass, net.minecraft.nbt.CompoundTag.class);
                    java.lang.reflect.Field stackField = packetClass.getDeclaredField("stack");
                    stackField.setAccessible(true);
                    Object[] inside = new Object[1];
                    CurioSeal.masking(handler -> {
                        try {
                            inside[0] = stackField.get(constructor.newInstance(player.getId(), "belt", 0, ItemStack.EMPTY, equipment,
                                    new net.minecraft.nbt.CompoundTag()));
                        } catch (ReflectiveOperationException e) {
                            inside[0] = e.toString();
                        }
                    }).accept(inventory);
                    Object outside = stackField.get(constructor.newInstance(player.getId(), "belt", 0, ItemStack.EMPTY, equipment,
                            new net.minecraft.nbt.CompoundTag()));
                    packetInside = inside[0] instanceof ItemStack stack ? (stack.isEmpty() ? "空" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()) : String.valueOf(inside[0]);
                    packetOutside = outside instanceof ItemStack stack ? (stack.isEmpty() ? "空" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()) : String.valueOf(outside);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    problems.add("反射构造同步包出错：" + e);
                }
            } finally {
                CurioSeal.releaseForTest(player);
                inventory.setEquippedCurio("belt", 0, ItemStack.EMPTY);
                CuriosCompat.invalidate(player);
            }
            String actual = "压制前 tick " + ticksBefore + " 护甲 " + armorBase + "→" + armorWorn + " 查得到 " + foundBefore
                    + "；压制后 tick " + ticksSealed + " 护甲 " + armorSealed + " 查得到 " + foundSealed
                    + "；同步包 作用域里 " + packetInside + " 作用域外 " + packetOutside + "；注入点全部到位 " + CurioSealMixinStatus.allApplied();
            boolean pass = ticksBefore == 2 && armorWorn == armorBase + 4D && foundBefore
                    && ticksSealed == ticksBefore && armorSealed == armorBase && !foundSealed
                    && "nautilus_shell".equals(packetInside) && "空".equals(packetOutside) && CurioSealMixinStatus.allApplied();
            return new Outcome(pass, "压制前 tick 2、护甲 +4、查得到；压制后 tick 不增、护甲回基础、查不到；同步包作用域里 nautilus_shell、作用域外空；注入点全部到位",
                    actual, problems);
        }

        /**
         * Curios 的每刻处理（饰品 tick、属性、本模组的槽位对账都挂在 LivingTickEvent 上；这名玩家不进世界，手动发），再把 tickCount 加一
         * （有的 Curios 构建按 tickCount 缓存查询结果，不加的话缓存不过期）
         */
        private void curiosTick(ServerPlayer player) {
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(player));
            player.tickCount++;
        }

        // ------------------------------------------------------------------ 第十批 10b

        /**
         * C38：风之号角（第十批 10b-M1，batch10 §8.2）。临时不看夜里 / 露天 / 维度（{@code WindHornItem.Config} 的测试覆盖，不改世界时间），
         * 生成点放在各区 z = 9 那一排（3×3×3 小格子以外）、y + 5 垫一排石头、站在 y + 6：
         * <ol>
         *   <li>另一名玩家（临时受眷顾）站在 owner 区吹号角，候选生成点全在 owner 区：以他身份的破坏探测被插件拒 → NO_SPACE，号角还在、没有待召唤的一批；</li>
         *   <li>主人（临时受眷顾）站在野外吹号角，候选点在野外：成功、号角没了，不等 5 秒直接生成这一批：大法师 + 随从，都盯着主人；</li>
         *   <li>另起一名旁观的玩家站在野外，拿一只召来的法师当射手造一支 MRU 箭打他：EC5 在 LivingAttackEvent（HIGHEST）取消，不掉血、插件收不到伤害事件；
         *       同样打主人：照中、掉血。</li>
         * </ol>
         * 跑完移除法师、清掉批记录、冷却和受眷顾，拆掉石头，撤掉测试覆盖。
         */
        private Outcome c38() {
            List<String> problems = new ArrayList<>();
            int row = 9;
            BlockPos ownBase = zoneBase(OWN).offset(0, 5, row);
            BlockPos wildBase = zoneBase(WILD).offset(0, 5, row);
            List<BlockPos> floors = new ArrayList<>();
            for (int dx = 0; dx < ZONE_SIZE; dx++) {
                floors.add(ownBase.offset(dx, 0, 0));
                floors.add(wildBase.offset(dx, 0, 0));
            }
            List<BlockPos> ownSpots = List.of(ownBase.offset(3, 1, 0), ownBase.offset(5, 1, 0), ownBase.offset(7, 1, 0));
            List<BlockPos> wildSpots = List.of(wildBase.offset(3, 1, 0), wildBase.offset(5, 1, 0), wildBase.offset(7, 1, 0));
            IPlayerData ownerData = PlayerDataEvents.get(owner);
            IPlayerData otherData = PlayerDataEvents.get(other);
            if (ownerData == null || otherData == null) {
                return new Outcome(false, "两名玩家都有玩家数据", "缺玩家数据", problems);
            }
            boolean ownerWindbound = ownerData.isWindbound();
            boolean otherWindbound = otherData.isWindbound();
            ServerPlayer bystander = realPlayer(UUID.nameUUIDFromBytes("ec5-selftest:bystander".getBytes(StandardCharsets.UTF_8)), "EC5TestBystander");
            for (int i = 0; i <= 60; i++) {
                bystander.tick();
            }
            List<WindMageEntity> mages = List.of();
            WindHornItem.Refusal denied = null;
            WindHornItem.Refusal wild = null;
            int deniedHorns = -1;
            boolean deniedNoBatch = false;
            int wildHorns = -1;
            int archmages = 0;
            boolean allOnOwner = false;
            boolean bystanderHit = true;
            float bystanderLoss = -1F;
            boolean ownerHit = false;
            float ownerLoss = -1F;
            Probe wildProbe = null;
            Probe arrowProbe = null;
            WindHornItem.Config.setTestOverrides(true, 0);
            try {
                for (BlockPos floor : floors) {
                    set(floor, Blocks.STONE.defaultBlockState());
                }
                ownerData.setWindbound(true);
                otherData.setWindbound(true);
                ownerData.setWindHornCooldown(0L);
                otherData.setWindHornCooldown(0L);

                standAt(other, ownBase.offset(1, 1, 0));
                ItemStack otherHorn = new ItemStack(ECWind.WIND_HORN.get());
                System.setProperty(PROP_EVENTS, "");
                denied = WindHornItem.blow(other, otherHorn, ownSpots);
                deniedHorns = otherHorn.getCount();
                deniedNoBatch = !WindMageSummons.hasBatch(OTHER_ID);
                Probe deniedProbe = Probe.read();
                deniedProbe.expect(problems, "BlockBreakEvent", OWN, OTHER_NAME, "CraftPlayer", true);

                standAt(owner, wildBase.offset(1, 1, 0));
                ItemStack ownerHorn = new ItemStack(ECWind.WIND_HORN.get());
                System.setProperty(PROP_EVENTS, "");
                wild = WindHornItem.blow(owner, ownerHorn, wildSpots);
                wildHorns = ownerHorn.getCount();
                wildProbe = Probe.read();
                if (wild == WindHornItem.Refusal.NONE) {
                    mages = WindMageSummons.spawnPending(owner);
                    allOnOwner = !mages.isEmpty();
                    for (WindMageEntity mage : mages) {
                        if (mage.getMageType() == WindMageEntity.ARCHMAGE) {
                            archmages++;
                        }
                        allOnOwner &= mage.getTarget() == owner;
                    }
                }
                if (!mages.isEmpty()) {
                    WindMageEntity shooter = mages.get(0);
                    standAt(bystander, wildBase.offset(8, 1, 0));
                    standAt(owner, wildBase.offset(1, 1, 0));
                    System.setProperty(PROP_EVENTS, "");
                    float before = bystander.getHealth();
                    bystanderHit = bystander.hurt(level.damageSources().arrow(new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter), shooter), 4F);
                    bystanderLoss = before - bystander.getHealth();
                    before = owner.getHealth();
                    ownerHit = owner.hurt(level.damageSources().arrow(new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level, shooter), shooter), 4F);
                    ownerLoss = before - owner.getHealth();
                    arrowProbe = Probe.read();
                }
            } finally {
                for (WindMageEntity mage : mages) {
                    if (!mage.isRemoved()) {
                        mage.discard();
                    }
                }
                WindMageSummons.clear(OWNER_ID);
                WindMageSummons.clear(OTHER_ID);
                WindHornItem.Config.clearTestOverrides();
                ownerData.setWindbound(ownerWindbound);
                otherData.setWindbound(otherWindbound);
                ownerData.setWindHornCooldown(0L);
                otherData.setWindHornCooldown(0L);
                owner.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
                other.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
                for (BlockPos floor : floors) {
                    set(floor, Blocks.AIR.defaultBlockState());
                }
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            if (wildProbe != null) {
                wildProbe.expect(problems, "BlockBreakEvent", WILD, OWNER_NAME, "CraftPlayer", false);
            }
            if (arrowProbe != null) {
                for (Map<String, String> event : arrowProbe.all()) {
                    if (event.get("line").contains("EC5TestBystander")) {
                        problems.add("旁人那一下应在 EC5 里就取消、插件收不到，实际收到：" + event.get("line"));
                    }
                }
            }
            int expectedMages = 1 + WindHornItem.Config.escorts();
            String actual = "领地 " + denied + " 号角 " + deniedHorns + " 无批 " + deniedNoBatch
                    + "；野外 " + wild + " 号角 " + wildHorns + " 法师 " + mages.size() + " 大法师 " + archmages + " 都盯主人 " + allOnOwner
                    + "；打旁人 " + bystanderHit + " 掉血 " + fmt(bystanderLoss) + "；打主人 " + ownerHit + " 掉血 " + (ownerLoss > 0F ? ">0" : fmt(ownerLoss));
            boolean pass = denied == WindHornItem.Refusal.NO_SPACE && deniedHorns == 1 && deniedNoBatch
                    && wild == WindHornItem.Refusal.NONE && wildHorns == 0 && mages.size() == expectedMages && archmages == 1 && allOnOwner
                    && !bystanderHit && bystanderLoss == 0F && ownerHit && ownerLoss > 0F;
            return new Outcome(pass, "领地 NO_SPACE 号角 1 无批 true；野外 NONE 号角 0 法师 " + expectedMages
                    + " 大法师 1 都盯主人 true；打旁人 false 掉血 0.0；打主人 true 掉血 >0", actual, problems);
        }

        /**
         * C39：打实以后才生效（第十批 10b-M3，batch10 §8.2）。同 C18 用反射临时打开主世界的 {@code pvpMode}，另一名玩家打主人：
         * <ol>
         *   <li><b>透镜</b>：魔力锭手枪分别装混沌 / 冰冻 / 魔力透镜各打一下（{@code RayShot.strike}，每下之前清掉受击无敌；主人先挂一个 30 秒迅捷）。
         *       主人在 owner 区（插件取消 EntityDamageByEntityEvent）→ 没有腐蚀、冰冻刻 0、迅捷还在；野外 → 腐蚀、冰冻刻 15、迅捷被剥掉。</li>
         *   <li><b>余烬嵌刻</b>：另一名玩家拿嵌着「阴燃 + 腐化」（虚弱）的铁剑，背包里一块存 1000 MRU 的暗之灵魂物质，真近战 {@code Player.attack}，
         *       然后立刻结算（{@code InlayEvents.flush}，正式是本刻末）。owner 区 → 主人不虚弱、MRU 1000；野外 → 虚弱、MRU 950。</li>
         *   <li><b>削减模块</b>（第十批 10c-M3）：另一名玩家换上穿齐四件、胸甲装着削减模块的宅男套装，近战打主人 2 点（{@code hurt}，伤害源 playerAttack），
         *       立刻结算（{@code ShredEvents.flush}，正式是本刻末）。owner 区 → 主人没有破甲；野外 → 破甲 I。</li>
         * </ol>
         * 三段的插件都要收到一次被取消的 EntityDamageByEntityEvent（owner 区）。
         * 透镜、嵌刻、枪的配置都用测试覆盖值；跑完放回 pvpMode、主人的矩阵损伤，拿走剑和灵魂物质、换回另一名玩家原来的盔甲，清效果、冰冻刻、满血。
         */
        private Outcome c39() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            int originalMatrix = data.getMatrixDamage();
            BlockPos inClaim = slot(OWN, 23).offset(1, 1, 1);
            BlockPos wild = slot(WILD, 23).offset(1, 1, 1);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            com.flameforge.essentialcraft5.item.EmberInlayItem.setInlay(sword,
                    com.flameforge.essentialcraft5.item.EmberInlayItem.Pair.of(EmberKind.COMMON, EmberKind.CORRUPTED));
            ItemStack matter = new ItemStack(ECItems.DARK_SOUL_MATTER.get());
            matter.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(1000));
            boolean pvpBefore = false;
            String[] denied;
            String[] open;
            Probe lensProbe;
            Probe inlayProbe;
            Probe shredProbe;
            ItemStack[] otherArmor = new ItemStack[4];
            net.minecraft.world.item.ArmorItem.Type[] armorTypes = net.minecraft.world.item.ArmorItem.Type.values();
            for (int i = 0; i < armorTypes.length; i++) {
                otherArmor[i] = other.getItemBySlot(armorTypes[i].getSlot()).copy();
            }
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                GunConfig.setTestOverrides(GunConfig.DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 1D, null);
                com.flameforge.essentialcraft5.gun.LensEffects.Config.setTestOverrides(60, 15, true);
                com.flameforge.essentialcraft5.item.EmberInlayItem.Config.setTestOverrides(true, 50, 10);
                if (!owner.canFreeze()) {
                    problems.add("主人这时冻不上（身上有皮革装备？），冰冻那一项不准");
                }
                String[] deniedLens = lensHits(inClaim);
                lensProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                String[] openLens = lensHits(wild);
                System.setProperty(PROP_EVENTS, "");

                other.setItemInHand(InteractionHand.MAIN_HAND, sword);
                other.getInventory().setItem(9, matter);
                standAt(owner, inClaim);
                owner.removeAllEffects();
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                other.attack(owner);
                com.flameforge.essentialcraft5.item.ember.InlayEvents.flush();
                boolean deniedWeak = owner.hasEffect(MobEffects.WEAKNESS);
                int deniedMru = storedMru(matter);
                inlayProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, wild);
                owner.removeAllEffects();
                other.moveTo(owner.getX(), owner.getY(), owner.getZ() - 1.5D, 0F, 0F);
                other.attack(owner);
                com.flameforge.essentialcraft5.item.ember.InlayEvents.flush();
                boolean openWeak = owner.hasEffect(MobEffects.WEAKNESS);
                int openMru = storedMru(matter);
                System.setProperty(PROP_EVENTS, "");

                // 削减模块：另一名玩家穿齐四件、胸甲装削减模块
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                for (net.minecraft.world.item.ArmorItem.Type type : armorTypes) {
                    ItemStack piece = new ItemStack(ECComputer.armor().get(type.ordinal()).get());
                    if (type == net.minecraft.world.item.ArmorItem.Type.CHESTPLATE) {
                        piece = com.flameforge.essentialcraft5.item.module.ArmorModules.install(piece,
                                com.flameforge.essentialcraft5.registry.ECCombatModules.SHRED);
                    }
                    other.setItemSlot(type.getSlot(), piece);
                }
                com.flameforge.essentialcraft5.item.module.ModuleCache.invalidate(other);
                if (!com.flameforge.essentialcraft5.item.module.ModuleCache.active(other,
                        com.flameforge.essentialcraft5.registry.ECCombatModules.SHRED)) {
                    problems.add("另一名玩家的削减模块没生效，削减那一项不准");
                }
                com.flameforge.essentialcraft5.item.module.combat.ShredEvents.flush();
                standAt(owner, inClaim);
                owner.removeAllEffects();
                owner.invulnerableTime = 0;
                owner.hurt(level.damageSources().playerAttack(other), 2F);
                com.flameforge.essentialcraft5.item.module.combat.ShredEvents.flush();
                boolean deniedShred = owner.hasEffect(com.flameforge.essentialcraft5.registry.ECEffects.ARMOR_SHRED.get());
                shredProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");
                standAt(owner, wild);
                owner.removeAllEffects();
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
                owner.hurt(level.damageSources().playerAttack(other), 2F);
                com.flameforge.essentialcraft5.item.module.combat.ShredEvents.flush();
                net.minecraft.world.effect.MobEffectInstance openShredEffect =
                        owner.getEffect(com.flameforge.essentialcraft5.registry.ECEffects.ARMOR_SHRED.get());
                int openShred = openShredEffect == null ? 0 : openShredEffect.getAmplifier() + 1;
                System.setProperty(PROP_EVENTS, "");
                denied = new String[]{deniedLens[0], deniedLens[1], deniedLens[2], String.valueOf(deniedWeak), String.valueOf(deniedMru),
                        String.valueOf(deniedShred)};
                open = new String[]{openLens[0], openLens[1], openLens[2], String.valueOf(openWeak), String.valueOf(openMru),
                        String.valueOf(openShred)};
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                GunConfig.setTestOverrides(null, null, null);
                com.flameforge.essentialcraft5.gun.LensEffects.Config.setTestOverrides(null, null, null);
                com.flameforge.essentialcraft5.item.EmberInlayItem.Config.setTestOverrides(null, null, null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                data.setMatrixDamage(originalMatrix);
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                other.getInventory().setItem(9, ItemStack.EMPTY);
                for (int i = 0; i < armorTypes.length; i++) {
                    other.setItemSlot(armorTypes[i].getSlot(), otherArmor[i]);
                }
                com.flameforge.essentialcraft5.item.module.ModuleCache.invalidate(other);
                other.removeAllEffects();
                owner.removeAllEffects();
                owner.setTicksFrozen(0);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
            }
            lensProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            inlayProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            shredProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            String actual = "领地 腐蚀 " + denied[0] + " 冰冻刻 " + denied[1] + " 迅捷还在 " + denied[2] + " 虚弱 " + denied[3] + " MRU " + denied[4]
                    + " 破甲 " + denied[5]
                    + "；野外 腐蚀 " + open[0] + " 冰冻刻 " + open[1] + " 迅捷还在 " + open[2] + " 虚弱 " + open[3] + " MRU " + open[4]
                    + " 破甲等级 " + open[5];
            boolean pass = denied[0].equals("false") && denied[1].equals("0") && denied[2].equals("true") && denied[3].equals("false") && denied[4].equals("1000")
                    && denied[5].equals("false")
                    && open[0].equals("true") && open[1].equals("15") && open[2].equals("false") && open[3].equals("true") && open[4].equals("950")
                    && open[5].equals("1");
            return new Outcome(pass, "领地 腐蚀 false 冰冻刻 0 迅捷还在 true 虚弱 false MRU 1000 破甲 false（插件 EntityDamageByEntityEvent 已取消）；"
                    + "野外 腐蚀 true 冰冻刻 15 迅捷还在 false 虚弱 true MRU 950 破甲等级 1", actual, problems);
        }

        /** C39 的透镜那一段：主人站到 at、挂 30 秒迅捷，另一名玩家用混沌 / 冰冻 / 魔力透镜的手枪各打一下；返回 {有没有腐蚀, 冰冻刻, 迅捷还在不在} */
        private String[] lensHits(BlockPos at) {
            standAt(owner, at);
            owner.removeAllEffects();
            owner.setTicksFrozen(0);
            owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            RayShot.MatrixFxSink sink = (target, intensity) -> {
            };
            for (String lens : List.of("chaos", "frozen", "pure")) {
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
                RayShot.strike(other, owner, GunStats.of(GunType.PISTOL, GunParts.uniform("mru").withLens(lens)), sink);
            }
            boolean corroded = owner.hasEffect(com.flameforge.essentialcraft5.registry.ECEffects.CORROSION.get());
            return new String[]{String.valueOf(corroded), String.valueOf(owner.getTicksFrozen()), String.valueOf(owner.hasEffect(MobEffects.MOVEMENT_SPEED))};
        }

        /** 物品自己存的 MRU（没有 MRU 能力时 -1） */
        private static int storedMru(ItemStack stack) {
            return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
        }

        /**
         * C43：ζ 护身符（第十批饰品平衡，混合端在 {@code actuallyHurt} 之后才把无敌帧设成 20）。同 C18 打开 {@code pvpMode}，主人戴 ζ、UBMRU 1000，
         * 打另起的玩家 T 2 点：
         * <ol>
         *   <li>T 在 owner 区：插件取消 EntityDamageByEntityEvent → 走不到 LivingDamageEvent → 不扣 UBMRU、T 不掉血；</li>
         *   <li>T 在野外：放行 → 扣 300；刚打完 T 的无敌帧是混合端设的值，{@code flushZeta}（每 tick 末跑的那一步）以后为 0，紧接着的第二下照样掉血。</li>
         * </ol>
         */
        private Outcome c43() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            IPlayerData data = PlayerDataEvents.get(owner);
            if (data == null) {
                return new Outcome(false, "主人有玩家数据", "没有", problems);
            }
            if (!setBauble(owner, ECBaubles.ZETA_AMULET, true)) {
                return new Outcome(false, "主人戴上 ζ", "主人没有项链槽", problems);
            }
            ServerPlayer target = tickedPlayer("zeta-target", "EC5TestZetaTgt");
            String targetName = target.getGameProfile().getName();
            int ubmruBefore = data.getUBMRU();
            boolean pvpBefore = false;
            int[] spent = new int[2];
            float[] loss = new float[2];
            int invulAfterHit = -1;
            int invulAfterFlush = -1;
            float secondLoss = 0F;
            Probe deniedProbe = null;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                int[] zones = {OWN, WILD};
                for (int i = 0; i < 2; i++) {
                    data.setUBMRU(1000);
                    com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushZeta();
                    standAt(owner, slot(WILD, 16).offset(1, 1, 2));
                    standAt(target, slot(zones[i], 16).offset(1, 1, 1));
                    target.hurt(level.damageSources().playerAttack(owner), 2F);
                    spent[i] = 1000 - data.getUBMRU();
                    loss[i] = target.getMaxHealth() - target.getHealth();
                    if (i == 0) {
                        deniedProbe = takeProbe();
                    } else {
                        invulAfterHit = target.invulnerableTime;
                        com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushZeta();
                        invulAfterFlush = target.invulnerableTime;
                        float healthBefore = target.getHealth();
                        target.hurt(level.damageSources().playerAttack(owner), 2F);
                        secondLoss = healthBefore - target.getHealth();
                        com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushZeta();
                    }
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                setBauble(owner, ECBaubles.ZETA_AMULET, false);
                data.setUBMRU(ubmruBefore);
                // 野外放行的那几下记了交手，别带到后面看交手记录的用例里
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
            }
            if (deniedProbe != null) {
                deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, targetName, "CraftPlayer", true);
            }
            String actual = "领地 扣 " + spent[0] + " 掉血 " + fmt(loss[0]) + "；野外 扣 " + spent[1] + " 掉血 " + fmt(loss[1])
                    + " 打完无敌帧 " + invulAfterHit + " 补清后 " + invulAfterFlush + " 第二下掉血 " + fmt(secondLoss)
                    + "；插件 " + (deniedProbe == null ? "-" : deniedProbe.summary());
            boolean pass = spent[0] == 0 && loss[0] == 0F && spent[1] == BaubleEffects.ZETA_COST && loss[1] > 0F
                    && invulAfterFlush == 0 && secondLoss > 0F;
            return new Outcome(pass, "领地 扣 0 掉血 0.0（插件已取消）；野外 扣 " + BaubleEffects.ZETA_COST + "、掉血，补清后无敌帧 0、第二下掉血", actual, problems);
        }

        /**
         * C44：ι 指环（第十批饰品平衡：拆出的 10% 魔法伤害等 {@code LivingDamageEvent} 确认打实才在 tick 末补）。同 C18 打开 {@code pvpMode}，
         * 主人戴 ι 打另起的玩家 T 4 点：
         * <ol>
         *   <li>T 在 owner 区：插件取消 → 没确认 → {@code flushIota} 打出 0 下，T 不掉血，插件只收到那一条被取消的伤害事件；</li>
         *   <li>T 在野外：先挨 3.6，{@code flushIota} 再打 1 下 0.4（无视护甲的 indirect_magic，记在主人头上、照样过插件），合计约 4。</li>
         * </ol>
         */
        private Outcome c44() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            if (!setBauble(owner, ECBaubles.IOTA_RING, true)) {
                return new Outcome(false, "主人戴上 ι", "主人没有戒指槽", problems);
            }
            ServerPlayer target = tickedPlayer("iota-target", "EC5TestIotaTgt");
            String targetName = target.getGameProfile().getName();
            boolean pvpBefore = false;
            int[] dealt = new int[2];
            float[] firstLoss = new float[2];
            float[] totalLoss = new float[2];
            Probe deniedProbe = null;
            Probe openProbe = null;
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushIota();
                int[] zones = {OWN, WILD};
                for (int i = 0; i < 2; i++) {
                    standAt(owner, slot(WILD, 17).offset(1, 1, 2));
                    standAt(target, slot(zones[i], 17).offset(1, 1, 1));
                    target.hurt(level.damageSources().playerAttack(owner), 4F);
                    firstLoss[i] = target.getMaxHealth() - target.getHealth();
                    dealt[i] = com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushIota();
                    totalLoss[i] = target.getMaxHealth() - target.getHealth();
                    if (i == 0) {
                        deniedProbe = takeProbe();
                    } else {
                        openProbe = takeProbe();
                    }
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                setBauble(owner, ECBaubles.IOTA_RING, false);
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(target.getUUID());
            }
            if (deniedProbe != null) {
                deniedProbe.expectAll(problems, "EntityDamageByEntityEvent", OWN, targetName, "CraftPlayer", true, 1);
            }
            if (openProbe != null) {
                openProbe.expectAll(problems, "EntityDamageByEntityEvent", WILD, targetName, "CraftPlayer", false, 2);
            }
            String actual = "领地 补 " + dealt[0] + " 下 掉血 " + fmt(totalLoss[0]) + "；野外 先掉 " + fmt(firstLoss[1]) + " 补 " + dealt[1]
                    + " 下 合计 " + fmt(totalLoss[1]) + "；插件（领地）" + (deniedProbe == null ? "-" : deniedProbe.summary());
            boolean pass = dealt[0] == 0 && totalLoss[0] == 0F && dealt[1] == 1 && firstLoss[1] > 0F && totalLoss[1] > firstLoss[1]
                    && Math.abs(totalLoss[1] - 4F) < 0.6F;
            return new Outcome(pass, "领地 补 0 下 掉血 0.0（插件已取消）；野外 先掉约 3.6、补 1 下、合计约 4", actual, problems);
        }

        /** 另起一名真玩家（不进世界），先跑掉 60 刻出生无敌，才挨得了伤害 */
        private ServerPlayer tickedPlayer(String key, String name) {
            ServerPlayer player = realPlayer(UUID.nameUUIDFromBytes(("ec5-selftest:" + key).getBytes(StandardCharsets.UTF_8)), name);
            for (int i = 0; i <= 60; i++) {
                player.tick();
            }
            return player;
        }

        /**
         * C45：σ 指环的反弹（第十批权限修补 A5）。打开 {@code pvpMode}，σ 的掷骰固定成必中（测试覆盖值）：
         * <ol>
         *   <li>主人戴 σ 站 owner 区，攻击者在区外打主人一下：原伤害被插件取消（EntityDamageByEntityEvent cancelled）→ 主人没挨到，
         *       本 tick 末结算时攻击者不挨反弹；</li>
         *   <li>主人站野外：原伤害放行 → 结算时攻击者挨原样的 4 点（反弹是一次记在主人头上的 playerAttack，照样过插件）。</li>
         * </ol>
         * 改之前反弹在 LivingHurtEvent 里当场打出，早于插件对原伤害的判断，第一种情况攻击者照样挨反弹。
         */
        private Outcome c45() {
            List<String> problems = new ArrayList<>();
            java.lang.reflect.Field pvpMode;
            try {
                pvpMode = net.minecraft.world.level.Level.class.getDeclaredField("pvpMode");
                pvpMode.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "找不到（" + e + "），跳过", problems);
            }
            if (!setBauble(owner, ECBaubles.SIGMA_RING, true)) {
                return new Outcome(false, "主人戴上 σ", "主人没有戒指槽", problems);
            }
            ServerPlayer attacker = tickedPlayer("sigma-attacker", "EC5TestSigmaAtk");
            boolean pvpBefore = false;
            int[] reflected = new int[2];
            float[] attackerLoss = new float[2];
            Probe deniedProbe = null;
            com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.setSigmaRollForTest(0F);
            try {
                pvpBefore = pvpMode.getBoolean(level);
                pvpMode.setBoolean(level, true);
                com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushSigma();
                int[] zones = {OWN, WILD};
                for (int i = 0; i < 2; i++) {
                    standAt(owner, slot(zones[i], 10).offset(1, 1, 1));
                    standAt(attacker, slot(WILD, 10).offset(1, 1, 2));
                    owner.hurt(level.damageSources().playerAttack(attacker), 4F);
                    reflected[i] = com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.flushSigma();
                    attackerLoss[i] = attacker.getMaxHealth() - attacker.getHealth();
                    if (i == 0) {
                        deniedProbe = Probe.read();
                        System.setProperty(PROP_EVENTS, "");
                    }
                }
            } catch (IllegalAccessException e) {
                return new Outcome(null, "混合端的 Level.pvpMode", "改不了（" + e + "），跳过", problems);
            } finally {
                com.flameforge.essentialcraft5.item.bauble.BaubleCombatEvents.setSigmaRollForTest(null);
                try {
                    pvpMode.setBoolean(level, pvpBefore);
                } catch (IllegalAccessException e) {
                    problems.add("pvpMode 放不回去：" + e);
                }
                setBauble(owner, ECBaubles.SIGMA_RING, false);
                owner.invulnerableTime = 0;
                owner.setHealth(owner.getMaxHealth());
                ECCombatTags.forget(OWNER_ID);
                ECCombatTags.forget(attacker.getUUID());
            }
            if (deniedProbe != null) {
                deniedProbe.expect(problems, "EntityDamageByEntityEvent", OWN, OWNER_NAME, "CraftPlayer", true);
            }
            String actual = "领地 反弹 " + reflected[0] + " 攻击者掉血 " + fmt(attackerLoss[0]) + "；野外 反弹 " + reflected[1]
                    + " 攻击者掉血 " + fmt(attackerLoss[1]) + "；插件 " + (deniedProbe == null ? "-" : deniedProbe.summary());
            boolean pass = reflected[0] == 0 && attackerLoss[0] == 0F && reflected[1] == 1 && attackerLoss[1] > 0F;
            return new Outcome(pass, "领地 反弹 0 攻击者掉血 0.0（原伤害被插件取消）；野外 反弹 1、攻击者掉血", actual, problems);
        }

        /**
         * C47：特斯拉线圈只打闯入者（第十批权限修补 A1：被打的人在那格没有权限，而且那格是线圈主人能动的）。线圈摆在野外（只调判定和 strike，不用成形），
         * 主人记成 EC5TestOwner：
         * <ol>
         *   <li>主人在线，另起的玩家站 owner 区：以他本人身份的探测被插件拒、再以主人身份的探测放行 → 打中（插件只看到无攻击者的 EntityDamageEvent）；</li>
         *   <li>同一人站 other 区（第三个人的领地）：他被拒、主人也被拒 → 不打，两条 BlockBreakEvent 都是 cancelled；</li>
         *   <li>同一人站野外：他的探测放行 → 不打，也不再以主人身份探测；另一名玩家站 other 区（他自己的领地）：放行 → 不打；</li>
         *   <li>主人下线：owner 区刚才那格（在线时确认过）照打；owner 区另一格不打，插件一条破坏事件都收不到。</li>
         * </ol>
         */
        private Outcome c47() {
            List<String> problems = new ArrayList<>();
            BlockPos coilPos = slot(WILD, 11).offset(1, 1, 1);
            set(coilPos.below(), Blocks.STONE.defaultBlockState());
            set(coilPos, com.flameforge.essentialcraft5.registry.ECTesla.MRU_COIL.get().defaultBlockState());
            if (!(level.getBlockEntity(coilPos) instanceof com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity coil)) {
                return new Outcome(false, "线圈方块实体", "没有", problems);
            }
            coil.setOwner(OWNER_ID, OWNER_NAME);
            coil.getMRUStorage().setMRU(coil.getMRUStorage().getMaxMRU());
            ServerPlayer intruder = tickedPlayer("intruder", "EC5TestIntruder");
            BlockPos ownCell = slot(OWN, 11).offset(1, 1, 1);
            String inClaim;
            String inOthers;
            String wild;
            String own;
            String offlineSame;
            String offlineFresh;
            boolean struck;
            boolean struckOffline;
            float loss;
            int ownerProbesInWild = 0;
            Probe claimProbe;
            Probe othersProbe;
            Probe offlineProbe;
            try {
                if (ECProtection.ownerState(coil) != ECProtection.OwnerState.ONLINE) {
                    problems.add("测试前提：主人应在线，实际 " + ECProtection.ownerState(coil));
                }
                standAt(intruder, ownCell);
                boolean hit = coil.mayStrikePlayer(intruder);
                inClaim = name(coil.standingPermission(intruder));
                struck = hit && coil.strike(intruder, level.getGameTime());
                loss = intruder.getMaxHealth() - intruder.getHealth();
                claimProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");

                standAt(intruder, slot(OTHERS, 12).offset(1, 1, 1));
                inOthers = name(coil.standingPermission(intruder)) + (coil.mayStrikePlayer(intruder) ? "（打）" : "（不打）");
                othersProbe = Probe.read();
                System.setProperty(PROP_EVENTS, "");

                standAt(intruder, slot(WILD, 12).offset(1, 1, 1));
                wild = name(coil.standingPermission(intruder)) + (coil.mayStrikePlayer(intruder) ? "（打）" : "（不打）");
                standAt(other, slot(OTHERS, 11).offset(1, 1, 1));
                own = name(coil.standingPermission(other)) + (coil.mayStrikePlayer(other) ? "（打）" : "（不打）");
                for (Map<String, String> event : Probe.read().all()) {
                    if (OWNER_NAME.equals(event.get("playerName"))) {
                        ownerProbesInWild++;
                    }
                }
                System.setProperty(PROP_EVENTS, "");

                ECProtection.clearOnlineOverride(OWNER_ID);
                ECProtection.forget(OWNER_ID);
                if (ECProtection.ownerState(coil) != ECProtection.OwnerState.OFFLINE) {
                    problems.add("测试前提：主人应离线，实际 " + ECProtection.ownerState(coil));
                }
                standAt(intruder, ownCell);
                boolean again = coil.mayStrikePlayer(intruder);
                struckOffline = again && coil.strike(intruder, level.getGameTime());
                offlineSame = again ? "打" : "不打";
                standAt(intruder, slot(OWN, 14).offset(1, 1, 1));
                offlineFresh = coil.mayStrikePlayer(intruder) ? "打" : "不打";
                offlineProbe = Probe.read();
            } finally {
                ECProtection.setOnlineOverride(owner);
                set(coilPos, Blocks.AIR.defaultBlockState());
            }
            claimProbe.expect(problems, "BlockBreakEvent", OWN, "EC5TestIntruder", "CraftPlayer", true);
            claimProbe.expect(problems, "BlockBreakEvent", OWN, OWNER_NAME, "CraftPlayer", false);
            claimProbe.expectCount(problems, "BlockBreakEvent", 2);
            claimProbe.expect(problems, "EntityDamageEvent", OWN, "EC5TestIntruder", "CraftPlayer", false);
            othersProbe.expect(problems, "BlockBreakEvent", OTHERS, "EC5TestIntruder", "CraftPlayer", true);
            othersProbe.expect(problems, "BlockBreakEvent", OTHERS, OWNER_NAME, "CraftPlayer", true);
            othersProbe.expectCount(problems, "BlockBreakEvent", 2);
            othersProbe.expectCount(problems, "EntityDamageEvent", 0);
            if (ownerProbesInWild != 0) {
                problems.add("被打的人放行时不该以主人身份探测，实际 " + ownerProbesInWild + " 条");
            }
            offlineProbe.expectCount(problems, "BlockBreakEvent", 0);
            String actual = "在线：领地里 " + inClaim + (struck ? " 打中 掉血 " + fmt(loss) : " 没打") + "；别人领地 " + inOthers + "；野外 " + wild
                    + "；自己的领地 " + own + "；离线：确认过的格子 " + offlineSame + (struckOffline ? "（打中）" : "") + "，没确认过的 " + offlineFresh
                    + "；插件（领地）" + claimProbe.summary() + "（别人领地）" + othersProbe.summary() + "（离线）" + offlineProbe.summary();
            boolean pass = inClaim.equals("拒绝") && struck && loss > 0F && inOthers.equals("拒绝（不打）") && wild.equals("允许（不打）")
                    && own.equals("允许（不打）") && offlineSame.equals("打") && struckOffline && offlineFresh.equals("不打");
            return new Outcome(pass, "在线：领地里 拒绝 打中；别人领地 拒绝（不打）；野外 允许（不打）；自己的领地 允许（不打）；离线：确认过的格子 打（打中），没确认过的 不打",
                    actual, problems);
        }

        /**
         * C48：太阳棱镜只烧敌对生物（第十批权限修补 A2）。owner 区里一名玩家、一头没起名的牛、一只僵尸，直接交给光束的烧法
         * （{@code SolarPrismBlockEntity.burn}，测试玩家不在世界的实体表里，按范围查不到）：只有僵尸受伤着火，插件只看到僵尸那一条 FIRE_TICK。
         * 改之前三个都烧，玩家和牛那两条插件拦不住（无攻击者）。
         */
        private Outcome c48() {
            List<String> problems = new ArrayList<>();
            BlockPos base = slot(OWN, 12);
            ServerPlayer victim = tickedPlayer("prism-victim", "EC5TestPrism");
            standAt(victim, base.offset(1, 1, 1));
            set(base.offset(0, 0, 0), Blocks.STONE.defaultBlockState());
            set(base.offset(2, 0, 2), Blocks.STONE.defaultBlockState());
            net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(level);
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (cow == null || zombie == null) {
                return new Outcome(false, "造出牛和僵尸", "造不出来", problems);
            }
            cow.moveTo(base.getX() + 0.5D, base.getY() + 1D, base.getZ() + 0.5D, 0F, 0F);
            cow.setNoAi(true);
            zombie.moveTo(base.getX() + 2.5D, base.getY() + 1D, base.getZ() + 2.5D, 0F, 0F);
            zombie.setNoAi(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            level.addFreshEntity(cow);
            level.addFreshEntity(zombie);
            Probe probe;
            try {
                com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity.burn(level, List.of(victim, cow, zombie));
                probe = Probe.read();
            } finally {
                cow.discard();
                zombie.discard();
            }
            float victimLoss = victim.getMaxHealth() - victim.getHealth();
            float cowLoss = cow.getMaxHealth() - cow.getHealth();
            float zombieLoss = zombie.getMaxHealth() - zombie.getHealth();
            int damageEvents = 0;
            for (Map<String, String> event : probe.all()) {
                if (event.get("type").startsWith("EntityDamage")) {
                    damageEvents++;
                    if (!"ZOMBIE".equals(event.get("entity"))) {
                        problems.add("插件不该收到僵尸以外的伤害事件：" + event.get("line"));
                    }
                }
            }
            String actual = "玩家 掉血 " + fmt(victimLoss) + " 着火 " + (victim.getRemainingFireTicks() > 0) + "；牛 掉血 " + fmt(cowLoss)
                    + "；僵尸 掉血 " + fmt(zombieLoss) + "；插件伤害事件 " + damageEvents + " 条 " + probe.summary();
            boolean pass = victimLoss == 0F && victim.getRemainingFireTicks() <= 0 && cowLoss == 0F && zombieLoss > 0F && damageEvents == 1;
            return new Outcome(pass, "玩家、牛 掉血 0.0 不着火；僵尸 掉血；插件只收到僵尸那 1 条", actual, problems);
        }

        /**
         * C49：药水扩散器的有害效果只给敌对生物（第十批权限修补 A3）。owner 区里驯服的狼、村民、僵尸（都放进世界），按扩散器的判定给缓慢：
         * 只有僵尸中。效果不走伤害事件，混合端上没有插件事件参与，这一组确认的是判定在混合端上和纯 Forge 一样（有益效果照旧都给）。
         */
        private Outcome c49() {
            List<String> problems = new ArrayList<>();
            BlockPos base = slot(OWN, 13);
            net.minecraft.world.entity.animal.Wolf wolf = EntityType.WOLF.create(level);
            Villager villager = EntityType.VILLAGER.create(level);
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (wolf == null || villager == null || zombie == null) {
                return new Outcome(false, "造出狼、村民、僵尸", "造不出来", problems);
            }
            wolf.setTame(true);
            wolf.setOwnerUUID(OTHER_ID);
            List<net.minecraft.world.entity.LivingEntity> mobs = List.of(wolf, villager, zombie);
            StringJoiner got = new StringJoiner(" ");
            try {
                int i = 0;
                for (net.minecraft.world.entity.LivingEntity mob : mobs) {
                    mob.moveTo(base.getX() + 0.5D + i, base.getY() + 1D, base.getZ() + 0.5D, 0F, 0F);
                    if (mob instanceof net.minecraft.world.entity.Mob m) {
                        m.setNoAi(true);
                    }
                    level.addFreshEntity(mob);
                    i++;
                }
                net.minecraft.world.effect.MobEffectInstance slowness = new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200);
                net.minecraft.world.effect.MobEffectInstance speed = new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200);
                for (net.minecraft.world.entity.LivingEntity mob : mobs) {
                    boolean harmful = com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity.shouldApply(mob, slowness);
                    boolean helpful = com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity.shouldApply(mob, speed);
                    if (harmful) {
                        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(slowness));
                    }
                    got.add(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath() + (mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? "有缓慢" : "无缓慢")
                            + (helpful ? "" : "（有益效果也不给？）"));
                    if (!helpful) {
                        problems.add(mob.getType() + " 应照常能得到有益效果");
                    }
                }
            } finally {
                mobs.forEach(Entity::discard);
            }
            String actual = got.toString();
            return new Outcome(actual.equals("wolf无缓慢 villager无缓慢 zombie有缓慢"), "wolf无缓慢 villager无缓慢 zombie有缓慢", actual, problems);
        }

        /**
         * C50：磁化法杖不吸别人领地里的掉落物和经验球（第十批权限修补 A4）。主人拿着法杖站在 owner 区和 other 区之间的空隙里，
         * other 区边上一个掉落物、一颗经验球（不同的两格），空隙里也各一个（都在 12 格内、看得见）：other 区那两格以主人身份探测被插件拒 → 不动；
         * 空隙里的照拉。
         */
        private Outcome c50() {
            List<String> problems = new ArrayList<>();
            BlockPos zoneOther = zoneBase(OTHERS);
            BlockPos stand = zoneOther.offset(-5, 1, 5);
            // 前面的用例可能在这一带留下方块（会挡住视线、法杖就不发探测），先把玩家到几个掉落物之间清空，清掉了什么记进结果
            List<String> cleared = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(zoneOther.offset(-9, 1, 4), zoneOther.offset(2, 4, 6))) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    cleared.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + "@" + pos.subtract(zoneOther).toShortString());
                    set(pos.immutable(), Blocks.AIR.defaultBlockState());
                }
            }
            set(stand.below(), Blocks.STONE.defaultBlockState());
            standAt(owner, stand);
            ItemEntity inClaim = new ItemEntity(level, zoneOther.getX() + 1.5D, zoneOther.getY() + 1.2D, zoneOther.getZ() + 5.5D, new ItemStack(Items.COBBLESTONE));
            ItemEntity open = new ItemEntity(level, stand.getX() - 3.5D, stand.getY() + 0.2D, stand.getZ() + 0.5D, new ItemStack(Items.COBBLESTONE));
            net.minecraft.world.entity.ExperienceOrb orbInClaim = new net.minecraft.world.entity.ExperienceOrb(level,
                    zoneOther.getX() + 1.5D, zoneOther.getY() + 1.2D, zoneOther.getZ() + 4.5D, 3);
            net.minecraft.world.entity.ExperienceOrb orbOpen = new net.minecraft.world.entity.ExperienceOrb(level,
                    stand.getX() - 3.5D, stand.getY() + 0.2D, stand.getZ() - 0.5D, 3);
            List<Entity> spawned = List.of(inClaim, open, orbInClaim, orbOpen);
            boolean inMoved;
            boolean openMoved;
            boolean orbInMoved;
            boolean orbOpenMoved;
            Probe probe;
            try {
                for (Entity entity : spawned) {
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.setNoGravity(true);
                    level.addFreshEntity(entity);
                }
                com.flameforge.essentialcraft5.item.MagnetizingStaffItem.pulse(level, owner);
                inMoved = inClaim.getDeltaMovement().lengthSqr() > 1.0E-6D;
                openMoved = open.getDeltaMovement().lengthSqr() > 1.0E-6D;
                orbInMoved = orbInClaim.getDeltaMovement().lengthSqr() > 1.0E-6D;
                orbOpenMoved = orbOpen.getDeltaMovement().lengthSqr() > 1.0E-6D;
                probe = Probe.read();
            } finally {
                spawned.forEach(Entity::discard);
            }
            int deniedInOthers = 0;
            for (Map<String, String> event : probe.all()) {
                if ("BlockBreakEvent".equals(event.get("type")) && ZONE_NAMES[OTHERS].equals(event.get("region"))
                        && OWNER_NAME.equals(event.get("playerName")) && "true".equals(event.get("cancelled"))) {
                    deniedInOthers++;
                }
            }
            if (deniedInOthers != 2) {
                problems.add("other 区应以主人身份各探测掉落物、经验球那一格一次（共 2 条被拒的 BlockBreakEvent），实际 " + deniedInOthers + "：" + probe.summary());
            }
            String actual = "other 区的掉落物 " + (inMoved ? "被拉" : "不动") + " 经验球 " + (orbInMoved ? "被拉" : "不动")
                    + "；空隙里的掉落物 " + (openMoved ? "被拉" : "不动") + " 经验球 " + (orbOpenMoved ? "被拉" : "不动") + "；插件 " + probe.summary()
                    + (cleared.isEmpty() ? "" : "；开始前清掉 " + cleared);
            return new Outcome(!inMoved && !orbInMoved && openMoved && orbOpenMoved,
                    "other 区的掉落物 不动 经验球 不动；空隙里的掉落物 被拉 经验球 被拉（插件 other 区 主人 2 条 BlockBreakEvent cancelled=true）", actual, problems);
        }

        /** C30 一边的结果 */
        private record VillagerHit(boolean alive, float health, int souls, int otherDrops, int freeTickets) {
            @Override
            public String toString() {
                return (alive ? "活着" : "死了") + " 生命 " + fmt(health) + " 灵魂 " + souls + " 别的掉落 " + otherDrops + " 名额 " + freeTickets;
            }
        }

        /** C30：在 base 这个小格子里摆工作站和村民（生命 2.5），主人拿着刻刀打一下 */
        private VillagerHit scribeVillager(BlockPos base, List<String> problems) {
            BlockPos feet = base.offset(1, 1, 1);
            BlockPos station = base.offset(2, 1, 2);
            set(feet.below(), Blocks.STONE.defaultBlockState());
            set(station.below(), Blocks.STONE.defaultBlockState());
            set(station, Blocks.COMPOSTER.defaultBlockState());
            PoiManager poi = level.getPoiManager();
            // 换方块时原版把兴趣点登记排到下一个任务里；这里直接登记（同类型再登记一次是空操作）
            if (poi.getType(station).isEmpty()) {
                poi.add(station, BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.FARMER));
            }
            Optional<BlockPos> taken = poi.take(holder -> holder.is(PoiTypes.FARMER), (holder, pos) -> pos.equals(station), station, 1);
            if (taken.isEmpty() || poi.getFreeTickets(station) != 0) {
                problems.add(base.toShortString() + " 没占到堆肥桶的名额（剩 " + poi.getFreeTickets(station) + "）");
            }
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                problems.add("造不出村民");
                set(station, Blocks.AIR.defaultBlockState());
                return new VillagerHit(false, 0F, 0, 0, -1);
            }
            villager.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0F, 0F);
            villager.setNoAi(true);
            level.addFreshEntity(villager);
            villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), station));
            villager.setHealth(2.5F);
            owner.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() - 0.5D, 0F, 0F);
            owner.resetAttackStrengthTicker();
            int souls = 0;
            int others = 0;
            try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
                owner.attack(villager);
                for (Entity entity : spawns.alive()) {
                    if (entity instanceof ItemEntity item && CapturedSoulItem.matches(item.getItem(), EntityType.VILLAGER)) {
                        souls += item.getItem().getCount();
                    } else if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                        others++;
                    }
                }
                spawns.discardAll();
            }
            int free = poi.getFreeTickets(station);
            VillagerHit hit = new VillagerHit(villager.isAlive(), villager.getHealth(), souls, others, free);
            if (villager.isAlive()) {
                villager.discard();
            }
            set(station, Blocks.AIR.defaultBlockState());
            return hit;
        }

        /** C28：落点宝石 */
        private ItemStack boundGem(GlobalPos target) {
            ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
            BoundGemItem.bind(gem, target, false);
            return gem;
        }

        /** C28：把乘客挪到传送器正上方，登记成「站在这台上的人」（测试玩家不在实体表里，同 GameTest 的 standOn） */
        private void standOnTeleporter(ServerPlayer porter, MagicalTeleporterBlockEntity be) {
            BlockPos pos = be.getBlockPos();
            porter.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, 0F, 0F);
            MagicalTeleporterBlockEntity.setTestOccupant(level, pos, porter);
        }

        /** C28：同步推进传送器 n 次服务端 tick（和世界每刻调的是同一个入口） */
        private void tickTeleporter(MagicalTeleporterBlockEntity be, int n) {
            for (int i = 0; i < n; i++) {
                MRUBlockEntity.tickServer(level, be.getBlockPos(), be.getBlockState(), be);
            }
        }

        /** C28：PlayerTeleportEvent 的摘要（原因、两头的世界、是否被取消） */
        private String teleportSummary(@Nullable Map<String, String> event) {
            if (event == null) {
                return "插件没收到 PlayerTeleportEvent";
            }
            return event.get("cause") + " " + event.get("from") + "→" + event.get("to") + " cancelled=" + event.get("cancelled");
        }

        /** C27：背包（含副手、盔甲格）里某种物品的总数 */
        private int countInventory(ServerPlayer player, Item item) {
            int n = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(item)) {
                    n += stack.getCount();
                }
            }
            return n;
        }

        /** 把饰品放进 / 拿出它那种 Curios 槽的第 0 格（不走 canEquip，同 GameTest 的 wear），并让 EC5 的饰品缓存作废 */
        private boolean setBauble(ServerPlayer player, RegistryObject<? extends SpecialBaubleItem> item, boolean wear) {
            SpecialBaubleItem bauble = item.get();
            ICuriosItemHandler inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            ICurioStacksHandler handler = inventory == null ? null : inventory.getCurios().get(bauble.slot());
            if (handler == null || handler.getSlots() < 1) {
                return false;
            }
            handler.getStacks().setStackInSlot(0, wear ? new ItemStack(bauble) : ItemStack.EMPTY);
            CuriosCompat.invalidate(player);
            return true;
        }

        /** 让玩家站到 pos 这一格中间、满血、清掉受击无敌 */
        private void standAt(ServerPlayer player, BlockPos pos) {
            player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0F, 0F);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
        }

        // ------------------------------------------------------------------ 工具

        private BlockPos zoneBase(int zone) {
            return origin.offset(zone * ZONE_STEP, 0, 0);
        }

        /** 第 zone 块里第 i 个 3×3×3 小格子的最小角（i = 0..26） */
        private BlockPos slot(int zone, int i) {
            return zoneBase(zone).offset((i % 3) * 3, (i / 9) * 3, ((i / 3) % 3) * 3);
        }

        private void set(BlockPos pos, BlockState state) {
            Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        /** 脱离网络的真 ServerPlayer（同 ProtectionFrameworkTests.realPlayer：连接是假的，发给它的包只进队列） */
        private ServerPlayer realPlayer(UUID id, String name) {
            return realPlayer(id, name, new Connection(PacketFlow.SERVERBOUND));
        }

        /** 同 {@link #realPlayer(UUID, String)}，连接由调用方给（C55~C57 用会数包的 {@link CountingConnection}） */
        private ServerPlayer realPlayer(UUID id, String name, Connection connection) {
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, name));
            new ServerGamePacketListenerImpl(server, connection, player);
            BlockPos at = zoneBase(WILD);
            player.moveTo(at.getX() + 0.5D, at.getY() + ZONE_SIZE + 1, at.getZ() + 0.5D, 0F, 0F);
            return player;
        }

        private Outcome verdicts(String expected, String[] actual, List<String> problems) {
            String joined = String.join("/", actual);
            return new Outcome(joined.equals(expected), expected, joined, problems);
        }
    }

    /**
     * C37 的探针饰品（同 GameTest 的 CurioSealCoreTests.ProbeCurio 的一部分）：服务端每次 curioTick 计数，戴着给 +4 护甲。
     * 登记到鹦鹉螺壳上，Curios 只对登记之后新建的物品挂能力。
     */
    private static final class SealProbeCurio implements top.theillusivec4.curios.api.type.capability.ICurioItem {
        private static final SealProbeCurio INSTANCE = new SealProbeCurio();
        private static boolean registered;
        private final Map<UUID, Integer> ticks = new HashMap<>();

        static synchronized void register() {
            if (!registered) {
                registered = true;
                CuriosApi.registerCurio(Items.NAUTILUS_SHELL, INSTANCE);
            }
        }

        static int ticks(Player player) {
            return INSTANCE.ticks.getOrDefault(player.getUUID(), 0);
        }

        @Override
        public void curioTick(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
            if (slotContext.entity() != null && !slotContext.entity().level().isClientSide) {
                ticks.merge(slotContext.entity().getUUID(), 1, Integer::sum);
            }
        }

        @Override
        public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
                getAttributeModifiers(top.theillusivec4.curios.api.SlotContext slotContext, UUID uuid, ItemStack stack) {
            com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier> map =
                    com.google.common.collect.HashMultimap.create();
            map.put(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, new net.minecraft.world.entity.ai.attributes.AttributeModifier(uuid,
                    "essentialcraft5:selftest_seal_probe", 4D, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            return map;
        }
    }

    // ==================================================================
    // 权限探测与核心（C55~C57）
    // ==================================================================

    /** 提示类包的四个类别（{@link #noticeKind}） */
    private static final List<String> NOTICE_KINDS = List.of("聊天", "动作栏", "标题", "音效");

    /**
     * 核心认不认权限探测：下面任一处带 probe 字样（不分大小写）就算认，返回找到的标记（「类.成员」或「类 引用 …」，逗号分隔），都没有返回 null：
     * <ul>
     *   <li>{@value #BLOCK_EVENT_DISPATCHER} 自己声明的方法或字段；</li>
     *   <li>它的字节码引用的类或成员名（认探测的逻辑可能放在它调用的别的类里），读的是常量池里的名字；</li>
     *   <li>{@code ServerGamePacketListenerImpl} 声明的字段（吞提示包要在发包入口按连接计数，核心补丁加的字段不混淆）。</li>
     * </ul>
     * 分发器的类找不到（不是 Mohist 系混合端）时只看最后一条。只按名字反射、读字节码，不引用 Mohist 的类。
     */
    @Nullable
    static String coreProbeMarker() {
        Set<String> found = new LinkedHashSet<>();
        Class<?> dispatcher = null;
        for (ClassLoader loader : new ClassLoader[]{HybridSelfTest.class.getClassLoader(), MinecraftServer.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                dispatcher = Class.forName(BLOCK_EVENT_DISPATCHER, false, loader);
                break;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // 换下一个类加载器
            }
        }
        if (dispatcher != null) {
            String simple = dispatcher.getSimpleName();
            try {
                for (Method method : dispatcher.getDeclaredMethods()) {
                    if (method.getName().toLowerCase(Locale.ROOT).contains("probe")) {
                        found.add(simple + "." + method.getName() + "()");
                    }
                }
                for (Field field : dispatcher.getDeclaredFields()) {
                    if (field.getName().toLowerCase(Locale.ROOT).contains("probe")) {
                        found.add(simple + "." + field.getName());
                    }
                }
            } catch (LinkageError | SecurityException e) {
                EssentialCraft5.LOGGER.warn("{} 读不了 {} 的成员：{}", TAG, BLOCK_EVENT_DISPATCHER, e.toString());
            }
            String referenced = probeReference(dispatcher);
            if (referenced != null) {
                found.add(simple + " 引用 " + referenced);
            }
        }
        try {
            for (Field field : ServerGamePacketListenerImpl.class.getDeclaredFields()) {
                if (field.getName().toLowerCase(Locale.ROOT).contains("probe")) {
                    found.add("ServerGamePacketListenerImpl." + field.getName());
                }
            }
        } catch (LinkageError | SecurityException e) {
            EssentialCraft5.LOGGER.warn("{} 读不了 ServerGamePacketListenerImpl 的字段：{}", TAG, e.toString());
        }
        return found.isEmpty() ? null : String.join(",", found);
    }

    /** 这个类的字节码（常量池）里第一个带 probe 字样的名字（类名、成员名都算）；读不到或没有返回 null */
    @Nullable
    private static String probeReference(Class<?> type) {
        String resource = type.getSimpleName() + ".class";
        byte[] bytes = null;
        try (java.io.InputStream in = type.getResourceAsStream(resource)) {
            if (in != null) {
                bytes = in.readAllBytes();
            }
        } catch (java.io.IOException | RuntimeException e) {
            EssentialCraft5.LOGGER.warn("{} 读不了 {} 的字节码：{}", TAG, type.getName(), e.toString());
        }
        if (bytes == null) {
            return null;
        }
        // 常量池里的名字是连续的 ASCII（Modified UTF-8），按 ISO-8859-1 原样转成字符串再找
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[A-Za-z0-9_/$]*(?i:probe)[A-Za-z0-9_/$]*")
                .matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        return matcher.find() ? matcher.group() : null;
    }

    /** 测试插件版本（如 1.5.0）不低于 major.minor */
    private static boolean probeAtLeast(@Nullable String version, int major, int minor) {
        if (version == null) {
            return false;
        }
        String[] parts = version.trim().split("\\.");
        try {
            int a = Integer.parseInt(parts[0]);
            int b = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return a > major || (a == major && b >= minor);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 插件的三个计数清零（每组开始前） */
    private static void resetBreakCounters() {
        System.setProperty(PROP_BREAK_HIGH, "0");
        System.setProperty(PROP_BREAK_MONITOR, "0");
        System.setProperty(PROP_NOTICES, "0");
    }

    /** 读插件的一个计数；读不出来 -1 */
    private static int counter(String property) {
        try {
            return Integer.parseInt(System.getProperty(property, "0").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 提示类的包属于哪一类：系统 / 玩家聊天 → 聊天；动作栏（含 overlay 的系统消息）→ 动作栏；标题、副标题、标题动画、清标题 → 标题；
     * 音效（含跟着实体的）→ 音效；别的包 null。
     */
    @Nullable
    static String noticeKind(@Nullable Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket chat) {
            return chat.overlay() ? "动作栏" : "聊天";
        }
        if (packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundDisguisedChatPacket) {
            return "聊天";
        }
        if (packet instanceof ClientboundSetActionBarTextPacket) {
            return "动作栏";
        }
        if (packet instanceof ClientboundSetTitleTextPacket || packet instanceof ClientboundSetSubtitleTextPacket
                || packet instanceof ClientboundSetTitlesAnimationPacket || packet instanceof ClientboundClearTitlesPacket) {
            return "标题";
        }
        if (packet instanceof ClientboundSoundPacket || packet instanceof ClientboundSoundEntityPacket) {
            return "音效";
        }
        return null;
    }

    /**
     * 会数包的假连接（C55~C57）：发到这条连接上的提示类包按 {@link #noticeKind} 分类计数，其余照旧交给父类（没连上，只进队列）。
     * 服务端发给玩家的包都经 {@code ServerGamePacketListenerImpl.send} 到这里；认得出权限探测的核心在那个入口就把探测期间的提示包丢掉，
     * 所以这里数到的就是真会发给客户端的。
     */
    private static final class CountingConnection extends Connection {
        private final Map<String, Integer> notices = new LinkedHashMap<>();

        CountingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            String kind = noticeKind(packet);
            if (kind != null) {
                notices.merge(kind, 1, Integer::sum);
            }
            super.send(packet, listener);
        }

        /** 到现在为止数到的提示包（类别 → 条数，没有就是空表） */
        Map<String, Integer> notices() {
            return new LinkedHashMap<>(notices);
        }

        void clear() {
            notices.clear();
        }
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    private static String name(ECProtection.Verdict verdict) {
        return switch (verdict) {
            case ALLOW -> "允许";
            case DENY -> "拒绝";
            case UNKNOWN -> "不确定";
        };
    }

    private static String fmt(float value) {
        return String.format("%.1f", value);
    }

    /** 实体里某种物品的掉落物总数；item 为 null 时数所有掉落物 */
    private static int countItems(List<Entity> entities, @Nullable Item item) {
        int total = 0;
        for (Entity entity : entities) {
            if (entity instanceof ItemEntity drop && (item == null || drop.getItem().is(item))) {
                total += drop.getItem().getCount();
            }
        }
        return total;
    }

    /**
     * 插件记下的事件（{@value #PROP_EVENTS}，一行一个 {@code 事件类 键=值 …}）。
     * 常用键：{@code player=名字/UUID/CraftPlayer|CraftFakePlayer}、{@code region=wild|owner|other|destroy|placeonly|none}、{@code cancelled=true|false}，
     * 另有 {@code action}、{@code useItem}、{@code entity}、{@code killer}、{@code cause} 等，见 EC5Probe。
     */
    private static final class Probe {
        private final List<Map<String, String>> events;

        private Probe(List<Map<String, String>> events) {
            this.events = events;
        }

        static Probe read() {
            String raw = System.getProperty(PROP_EVENTS, "");
            List<Map<String, String>> events = new ArrayList<>();
            for (String line : raw.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.trim().split(" ");
                Map<String, String> event = new HashMap<>();
                event.put("type", parts[0]);
                event.put("line", line.trim());
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    if (eq > 0) {
                        event.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                    }
                }
                String player = event.get("player");
                if (player != null) {
                    String[] who = player.split("/");
                    event.put("playerName", who[0]);
                    event.put("playerClass", who.length > 2 ? who[2] : "");
                }
                events.add(event);
            }
            return new Probe(events);
        }

        List<Map<String, String>> all() {
            return events;
        }

        @Nullable
        Map<String, String> first(String type) {
            for (Map<String, String> event : events) {
                if (type.equals(event.get("type"))) {
                    return event;
                }
            }
            return null;
        }

        /** 至少有一条这样的事件（type、区、玩家名、玩家类、是否被取消都对上） */
        void expect(List<String> problems, String type, int zone, String playerName, String playerClass, boolean cancelled) {
            if (count(type, zone, playerName, playerClass, cancelled) == 0) {
                problems.add("插件缺少 " + type + " region=" + ZONE_NAMES[zone] + " player=" + playerName + "(" + playerClass + ") cancelled=" + cancelled
                        + "；实际收到 " + summary());
            }
        }

        /** 这种事件正好 n 条，而且每条都对得上 */
        void expectAll(List<String> problems, String type, int zone, String playerName, String playerClass, boolean cancelled, int n) {
            int matching = count(type, zone, playerName, playerClass, cancelled);
            int any = countType(type);
            if (matching != n || any != n) {
                problems.add("插件应收到 " + n + " 条 " + type + " region=" + ZONE_NAMES[zone] + " player=" + playerName + "(" + playerClass + ") cancelled=" + cancelled
                        + "，对得上的 " + matching + " 条、这类共 " + any + " 条；实际收到 " + summary());
            }
        }

        /** 这种事件（type 为 null = 所有事件）正好 n 条 */
        void expectCount(List<String> problems, @Nullable String type, int n) {
            int actual = type == null ? events.size() : countType(type);
            if (actual != n) {
                problems.add("插件应收到 " + n + " 条" + (type == null ? "事件" : " " + type) + "，实际 " + actual + "：" + summary());
            }
        }

        private int count(String type, int zone, String playerName, String playerClass, boolean cancelled) {
            int n = 0;
            for (Map<String, String> event : events) {
                if (type.equals(event.get("type")) && ZONE_NAMES[zone].equals(event.get("region")) && playerName.equals(event.get("playerName"))
                        && playerClass.equals(event.get("playerClass")) && String.valueOf(cancelled).equals(event.get("cancelled"))) {
                    n++;
                }
            }
            return n;
        }

        private int countType(String type) {
            int n = 0;
            for (Map<String, String> event : events) {
                if (type.equals(event.get("type"))) {
                    n++;
                }
            }
            return n;
        }

        /** 一行摘要：事件类(玩家类,区,cancelled) … */
        String summary() {
            if (events.isEmpty()) {
                return "[]";
            }
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Map<String, String> event : events) {
                joiner.add(event.get("type") + "(" + event.getOrDefault("playerName", event.getOrDefault("entity", "-")) + ","
                        + event.getOrDefault("region", "-") + ",cancelled=" + event.getOrDefault("cancelled", "-") + ")");
            }
            return joiner.toString();
        }
    }
}
