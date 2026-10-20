package com.ts.br.entity;

import com.ts.br.api.animation.IAnimatableState;
import com.ts.br.block.GhostType;
import com.ts.br.config.ModConfig;
import com.ts.br.registry.ModEntities;
import com.ts.br.util.GhostBlockTracker;
import com.ts.br.util.RestoreUpdateQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class GhostBlockEntity extends Entity implements IAnimatableState {

    public static final int BEGIN_TICKS = 15;     // 0.75s
    public static final int MAIN_ANIM_TICKS = 50; // 2.5s

    // 階段常數定義
    public static final byte STAGE_BEGIN = 0;
    public static final byte STAGE_WAITING = 1;
    public static final byte STAGE_MAIN_ANIM = 2;

    private static final EntityDataAccessor<Long> DATA_SPAWN_TIME =
            SynchedEntityData.defineId(GhostBlockEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_DEATH_TIME =
            SynchedEntityData.defineId(GhostBlockEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<BlockState> DATA_ORIGINAL_STATE =
            SynchedEntityData.defineId(GhostBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> DATA_TINT_COLOR =
            SynchedEntityData.defineId(GhostBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_GHOST_TYPE =
            SynchedEntityData.defineId(GhostBlockEntity.class, EntityDataSerializers.STRING);

    private BlockPos targetPos = BlockPos.ZERO;
    private CompoundTag originalTileTag = null;

    private int startDelayTicks = 300;
    private int currentAgeTicks = 0;
    private boolean hasPrePlacedBlock = false;
    private boolean hasRemovedForDisappear = false;

    public GhostBlockEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GhostBlockEntity(Level level, BlockPos pos, BlockState state, CompoundTag tileTag, int startDelayTicks, GhostType type) {
        this(ModEntities.GB.get(), level);
        this.targetPos = pos.immutable();
        this.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        this.originalTileTag = tileTag;
        this.startDelayTicks = startDelayTicks;

        long now = level.getGameTime();
        int endHoldTicks = ModConfig.END_HOLD_TICKS.get();
        long initialDeathTime = now + startDelayTicks + MAIN_ANIM_TICKS + endHoldTicks;

        this.entityData.set(DATA_SPAWN_TIME, now);
        this.entityData.set(DATA_DEATH_TIME, initialDeathTime);

        MapColor mapColor = state.getMapColor(level, pos);
        int resolvedColor = 0xFFFFFF;
        if (mapColor != MapColor.NONE && mapColor.col != 0) {
            resolvedColor = mapColor.col;
        }

        this.entityData.set(DATA_ORIGINAL_STATE, state);
        this.entityData.set(DATA_TINT_COLOR, resolvedColor);
        this.entityData.set(DATA_GHOST_TYPE, type.getSerializedName());

        if (!level.isClientSide() && type == GhostType.DISAPPEAR) {
            GhostBlockTracker.register(this.targetPos, this);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPAWN_TIME, 0L);
        builder.define(DATA_DEATH_TIME, 0L);
        builder.define(DATA_ORIGINAL_STATE, Blocks.AIR.defaultBlockState());
        builder.define(DATA_TINT_COLOR, 0xFFFFFF);
        builder.define(DATA_GHOST_TYPE, GhostType.RESTORE.getSerializedName());
    }

    public BlockPos getTargetPos() {
        if (this.targetPos == null || this.targetPos.equals(BlockPos.ZERO)) {
            return this.blockPosition();
        }
        return this.targetPos;
    }

    public BlockState getOriginalState() {
        return this.entityData.get(DATA_ORIGINAL_STATE);
    }

    public int getTintColor() {
        return this.entityData.get(DATA_TINT_COLOR);
    }

    public GhostType getGhostType() {
        return "disappear".equals(this.entityData.get(DATA_GHOST_TYPE)) ? GhostType.DISAPPEAR : GhostType.RESTORE;
    }

    public long getSpawnTime() {
        return this.entityData.get(DATA_SPAWN_TIME);
    }

    public long getDeathTime() {
        return this.entityData.get(DATA_DEATH_TIME);
    }

    public long getMainAnimStartTime() {
        int endHoldTicks = ModConfig.END_HOLD_TICKS.get();
        return this.getDeathTime() - (MAIN_ANIM_TICKS + endHoldTicks);
    }

    public boolean isMainAnimStarted() {
        return this.level().getGameTime() >= this.getMainAnimStartTime();
    }

    // ==================== 補齊渲染端與 API 缺少的方法 ====================

    /**
     * 依據世界時間計算當前所處階段
     */
    public byte getStage() {
        if (this.isMainAnimStarted()) {
            return STAGE_MAIN_ANIM;
        }
        long elapsed = this.level().getGameTime() - this.getSpawnTime();
        if (elapsed < BEGIN_TICKS) {
            return STAGE_BEGIN;
        }
        return STAGE_WAITING;
    }

    /**
     * 實作 IAnimatableState：動態取得當前動畫名稱
     */
    @Override
    public String getCurrentAnimationName() {
        boolean isDisappear = (this.getGhostType() == GhostType.DISAPPEAR);
        if (this.isMainAnimStarted()) {
            return isDisappear ? "gbca2" : "gbca";
        }
        return isDisappear ? "begin2" : "begin";
    }

    @Override
    public float getAnimationTimeSeconds(float partialTick) {
        double now = (double) this.level().getGameTime() + (double) partialTick;
        if (this.isMainAnimStarted()) {
            // 主階段 (gbca / gbca2): 0.0s ~ 2.5s
            double elapsed = (now - (double) this.getMainAnimStartTime()) / 20.0D;
            return (float) Math.min(2.5D, Math.max(0.0D, elapsed));
        } else {
            // 開場階段 (begin / begin2): 0.0s ~ 0.75s
            double elapsed = (now - (double) this.getSpawnTime()) / 20.0D;
            return (float) Math.min(0.75D, Math.max(0.0D, elapsed));
        }
    }

    public void triggerEarlyDisappear() {
        long now = this.level().getGameTime();
        int endHoldTicks = ModConfig.END_HOLD_TICKS.get();
        long newDeathTime = now + MAIN_ANIM_TICKS + endHoldTicks;

        if (newDeathTime < this.getDeathTime()) {
            this.entityData.set(DATA_DEATH_TIME, newDeathTime);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && this.getGhostType() == GhostType.DISAPPEAR) {
            GhostBlockTracker.unregister(this.getTargetPos());
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        // 客戶端讀取 Client Config 設定的渲染距離
        int maxDist = 64;
        if (this.level().isClientSide()) {
            maxDist = ModConfig.RENDER_DISTANCE_BLOCKS.get();
        }
        return distanceSqr < (double) (maxDist * maxDist);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.currentAgeTicks++;

            long now = this.level().getGameTime();
            long deathTime = this.getDeathTime();
            long mainStartTime = this.getMainAnimStartTime();

            int bufferDelay = ModConfig.BUFFER_DELAY_TICKS.get();
            int endHoldTicks = ModConfig.END_HOLD_TICKS.get();

            // 1. 消失型（終界使者放下方塊）：在主流程開始 bufferDelay ticks 後抹除真方塊
            if (this.getGhostType() == GhostType.DISAPPEAR) {
                if (now >= (mainStartTime + bufferDelay) && !this.hasRemovedForDisappear) {
                    this.removeBlockForDisappear();
                    this.hasRemovedForDisappear = true;
                }
            } 
            // 2. 出現型（怪物破壞還原）：提前放置方塊
            else {
                if (now >= (deathTime - endHoldTicks - bufferDelay) && !this.hasPrePlacedBlock) {
                    this.handlePrePlace();
                    this.hasPrePlacedBlock = true;
                }
            }

            // 3. 到達死亡時間，銷毀實體
            if (now >= deathTime) {
                if (this.getGhostType() == GhostType.DISAPPEAR && !this.hasRemovedForDisappear) {
                    this.removeBlockForDisappear();
                } else if (this.getGhostType() == GhostType.RESTORE && !this.hasPrePlacedBlock) {
                    this.handlePrePlace();
                }
                this.discard();
            }
        }
    }

    private void removeBlockForDisappear() {
        BlockPos pos = this.getTargetPos();
        Level level = this.level();
        BlockState current = level.getBlockState(pos);

        if (!current.isAir() && current.is(this.getOriginalState().getBlock())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
        }
    }

    private void handlePrePlace() {
        BlockPos pos = this.getTargetPos();
        Level level = this.level();
        BlockState currentState = level.getBlockState(pos);

        if (currentState.canBeReplaced()) {
            this.pushEntitiesOut(pos);

            BlockState toRestore = this.getOriginalState();

            if (toRestore.hasProperty(BlockStateProperties.WATERLOGGED)) {
                boolean hasWater = currentState.getFluidState().is(Fluids.WATER);
                toRestore = toRestore.setValue(BlockStateProperties.WATERLOGGED, hasWater || toRestore.getValue(BlockStateProperties.WATERLOGGED));
            }

            // 第一階段：完全靜默放回 (Flag 18 = UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE)
            // 嚴禁在此時發送任何鄰近或形狀更新，確保所有方塊先在世界上就位
            level.setBlock(pos, toRestore, 18);

            // 還原方塊資料 (NBT)
            if (this.originalTileTag != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    be.loadWithComponents(this.originalTileTag, level.registryAccess());
                    be.setChanged();
                }
            }

            if (toRestore.hasProperty(BlockStateProperties.WATERLOGGED) && toRestore.getValue(BlockStateProperties.WATERLOGGED)) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }

            // 加入統一更新佇列，交給第二階段統一處理
            if (level instanceof ServerLevel serverLevel) {
                RestoreUpdateQueue.enqueue(serverLevel, pos);
            }
        } else {
            this.dropAsItem(pos);
        }
    }

    private void pushEntitiesOut(BlockPos pos) {
        AABB blockBox = new AABB(pos);
        List<Entity> entities = this.level().getEntities((Entity) null, blockBox.inflate(1.0E-4), Entity::isAlive);

        for (Entity entity : entities) {
            if (entity != this && entity.getBoundingBox().intersects(blockBox)) {
                double targetTopY = pos.getY() + 1.0;
                if (entity.getY() < targetTopY) {
                    entity.setPos(entity.getX(), Math.max(entity.getY(), targetTopY), entity.getZ());
                }

                Vec3 entityPos = entity.position();
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                double dx = entityPos.x - blockCenter.x;
                double dz = entityPos.z - blockCenter.z;
                double dist = Math.sqrt(dx * dx + dz * dz);

                double pushX = dist > 1.0E-4 ? (dx / dist) * 0.25 : 0;
                double pushZ = dist > 1.0E-4 ? (dz / dist) * 0.25 : 0;

                Vec3 currentMotion = entity.getDeltaMovement();
                entity.setDeltaMovement(
                        currentMotion.x + pushX,
                        Math.max(currentMotion.y, 0.42),
                        currentMotion.z + pushZ
                );
                entity.hurtMarked = true;
                entity.hasImpulse = true;
            }
        }
    }

    private void dropAsItem(BlockPos pos) {
        if (this.getGhostType() == GhostType.DISAPPEAR) return;
        BlockState toRestore = this.getOriginalState();
        if (toRestore.isAir()) return;

        Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockEntity tempBe = null;
        if (this.originalTileTag != null) {
            tempBe = BlockEntity.loadStatic(pos, toRestore, this.originalTileTag, level.registryAccess());
            if (tempBe instanceof net.minecraft.world.Container container) {
                Containers.dropContents(level, pos, container);
            }
        }

        LootParams.Builder params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        if (tempBe != null) {
            params.withOptionalParameter(LootContextParams.BLOCK_ENTITY, tempBe);
        }

        List<ItemStack> drops = toRestore.getDrops(params);
        for (ItemStack dropStack : drops) {
            if (!dropStack.isEmpty()) {
                ItemEntity dropEntity = new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + 1.1,
                        pos.getZ() + 0.5,
                        dropStack
                );
                dropEntity.setDefaultPickUpDelay();
                dropEntity.setDeltaMovement(0, 0.25, 0);
                level.addFreshEntity(dropEntity);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.currentAgeTicks = tag.getInt("CurrentAgeTicks");
        this.startDelayTicks = tag.getInt("StartDelayTicks");
        this.entityData.set(DATA_SPAWN_TIME, tag.getLong("SpawnTime"));
        this.entityData.set(DATA_DEATH_TIME, tag.getLong("DeathTime"));

        this.entityData.set(DATA_TINT_COLOR, tag.getInt("TintColor"));
        this.entityData.set(DATA_GHOST_TYPE, tag.getString("GhostType"));
        this.entityData.set(DATA_ORIGINAL_STATE, NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("OriginalState")));

        if (tag.contains("TargetPos")) {
            this.targetPos = NbtUtils.readBlockPos(tag, "TargetPos").orElse(this.blockPosition());
        }
        if (tag.contains("OriginalTileTag")) {
            this.originalTileTag = tag.getCompound("OriginalTileTag");
        }
        this.hasPrePlacedBlock = tag.getBoolean("HasPrePlacedBlock");
        this.hasRemovedForDisappear = tag.getBoolean("HasRemovedForDisappear");

        if (!this.level().isClientSide() && this.getGhostType() == GhostType.DISAPPEAR) {
            GhostBlockTracker.register(this.getTargetPos(), this);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("CurrentAgeTicks", this.currentAgeTicks);
        tag.putInt("StartDelayTicks", this.startDelayTicks);
        tag.putLong("SpawnTime", this.getSpawnTime());
        tag.putLong("DeathTime", this.getDeathTime());

        tag.putInt("TintColor", this.entityData.get(DATA_TINT_COLOR));
        tag.putString("GhostType", this.entityData.get(DATA_GHOST_TYPE));
        tag.put("OriginalState", NbtUtils.writeBlockState(this.getOriginalState()));

        tag.put("TargetPos", NbtUtils.writeBlockPos(this.getTargetPos()));
        if (this.originalTileTag != null) {
            tag.put("OriginalTileTag", this.originalTileTag);
        }
        tag.putBoolean("HasPrePlacedBlock", this.hasPrePlacedBlock);
        tag.putBoolean("HasRemovedForDisappear", this.hasRemovedForDisappear);
    }

    // 傷害與傳送完全免疫
    @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return true; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override public void kill() {}
    @Override public boolean isAttackable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canUsePortal(boolean allowVehicles) { return false; }
    @Override public Entity changeDimension(net.minecraft.world.level.portal.DimensionTransition transition) { return null; }

    @Override
    public void teleportTo(double x, double y, double z) {
        BlockPos pos = this.getTargetPos();
        super.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relatives, float yRot, float xRot) {
        BlockPos pos = this.getTargetPos();
        super.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Collections.emptySet(), 0.0F, 0.0F);
        return false;
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (this.targetPos != null && !this.targetPos.equals(BlockPos.ZERO)) {
            BlockPos pos = this.targetPos;
            super.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        } else {
            super.moveTo(x, y, z, yRot, xRot);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (this.targetPos != null && !this.targetPos.equals(BlockPos.ZERO)) {
            BlockPos pos = this.targetPos;
            super.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        } else {
            super.setPos(x, y, z);
        }
    }

    @Override public void push(Entity entity) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean isPushable() { return false; }
    @Override public boolean canCollideWith(Entity entity) { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean startRiding(Entity vehicle, boolean force) { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return false; }
    @Override public void setDeltaMovement(Vec3 motion) { super.setDeltaMovement(Vec3.ZERO); }
}