package com.ts.br.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

public class SimulateGriefCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("br_sim")
                .requires(source -> source.hasPermission(2))
                // 1. 標準挖掘
                .then(Commands.literal("break")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> executeBreak(ctx, resolveExecutor(ctx.getSource())))
                        .then(Commands.argument("entity", EntityArgument.entity())
                            .executes(ctx -> executeBreak(ctx, EntityArgument.getEntity(ctx, "entity")))
                        )
                    )
                )
                // 2. 標準放置
                .then(Commands.literal("place")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                            .executes(ctx -> executePlace(ctx, resolveExecutor(ctx.getSource())))
                            .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> executePlace(ctx, EntityArgument.getEntity(ctx, "entity")))
                            )
                        )
                    )
                )
                // 3. 最差情境測試 A：事件提早發送 (現場為空氣，但 Snapshot 帶有將放置的方塊狀態)
                .then(Commands.literal("bad_place_before")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                            .executes(ctx -> executeBadPlaceBefore(ctx, resolveExecutor(ctx.getSource())))
                            .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> executeBadPlaceBefore(ctx, EntityArgument.getEntity(ctx, "entity")))
                            )
                        )
                    )
                )
                // 4. 最差情境測試 B：模擬安德傳入地面座標 (事件座標為地面，但方塊落在上方)
                .then(Commands.literal("bad_place_ground")
                    .then(Commands.argument("ground_pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                            .executes(ctx -> executeBadPlaceGround(ctx, resolveExecutor(ctx.getSource())))
                            .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(ctx -> executeBadPlaceGround(ctx, EntityArgument.getEntity(ctx, "entity")))
                            )
                        )
                    )
                )
        );
    }

    /**
     * 取得合法的測試實體：
     * 1. 優先尋找指令發動者半徑 32 格內的第一隻現存實體生物 (非玩家)。
     * 2. 若周圍完全沒有生物，在伺服端動態生成並加入一隻合法的殭屍實體。
     */
    private static Entity resolveExecutor(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Entity sourceEntity = source.getEntity();
        BlockPos origin = sourceEntity != null ? sourceEntity.blockPosition() : BlockPos.containing(source.getPosition());

        // 搜尋周圍 32 格內現存的活體怪物
        List<Monster> nearbyMonsters = level.getEntitiesOfClass(Monster.class, new AABB(origin).inflate(32.0));
        for (Monster m : nearbyMonsters) {
            if (m.isAlive()) {
                return m;
            }
        }

        // 周圍無怪時，真正將一隻殭屍生成至伺服器世界（具備合法 UUID 與連線生命週期）
        Zombie testZombie = EntityType.ZOMBIE.create(level);
        if (testZombie != null) {
            testZombie.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            testZombie.setNoAi(true);
            testZombie.setSilent(true);
            testZombie.setCustomName(Component.literal("§e[BR-TestDummy]"));
            testZombie.setCustomNameVisible(true);
            level.addFreshEntity(testZombie);
            return testZombie;
        }

        return source.getEntity();
    }

    private static int executeBreak(CommandContext<CommandSourceStack> ctx, Entity executor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            source.sendFailure(Component.literal("§c目標格是空氣！"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§a[BR-Sim] 模擬破壞: " + pos.toShortString() + " (實體: " + executor.getName().getString() + ")"), true);
        level.destroyBlock(pos, true, executor);
        return 1;
    }

    private static int executePlace(CommandContext<CommandSourceStack> ctx, Entity executor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockState toPlace = BlockStateArgument.getBlock(ctx, "block").getState();

        BlockPos againstPos = pos.below();
        BlockState placedAgainst = level.getBlockState(againstPos);

        source.sendSuccess(() -> Component.literal("§a[BR-Sim] 標準模擬放置: " + toPlace.getBlock().getName().getString() + " 於 " + pos.toShortString() + " (實體: " + executor.getName().getString() + ")"), true);

        // 1. 先落子
        level.setBlock(pos, toPlace, 3);

        // 2. 發布事件（此時 Snapshot 讀到的是世界裡的 toPlace）
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(snapshot, placedAgainst, executor);
        NeoForge.EVENT_BUS.post(placeEvent);

        return 1;
    }

    private static int executeBadPlaceBefore(CommandContext<CommandSourceStack> ctx, Entity executor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        BlockState toPlace = BlockStateArgument.getBlock(ctx, "block").getState();

        source.sendSuccess(() -> Component.literal("§6[BR-Sim:最差測試A] 模擬事件提早發送 (實體: " + executor.getName().getString() + ")"), true);

        // 先把目標方塊填進世界建立 Snapshot，隨後立刻轉回空氣，以模擬「Snapshot 記載了目標方塊，但現場世界為空氣」的情境
        BlockState original = level.getBlockState(pos);
        level.setBlock(pos, toPlace, 18);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, original, 18);

        // 發送放置事件（此時現場是空氣，但 snapshot.getState() 是 toPlace）
        BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), executor);
        NeoForge.EVENT_BUS.post(placeEvent);

        // 事件處理完畢後，才落地方塊
        level.setBlock(pos, toPlace, 3);
        return 1;
    }

    private static int executeBadPlaceGround(CommandContext<CommandSourceStack> ctx, Entity executor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos groundPos = BlockPosArgument.getLoadedBlockPos(ctx, "ground_pos");
        BlockPos actualTargetPos = groundPos.above();
        BlockState toPlace = BlockStateArgument.getBlock(ctx, "block").getState();

        source.sendSuccess(() -> Component.literal("§d[BR-Sim:最差測試B] 模擬傳入地面 " + groundPos.toShortString() + "，實放於 " + actualTargetPos.toShortString() + " (實體: " + executor.getName().getString() + ")"), true);

        // 先把方塊放到上方真實格
        level.setBlock(actualTargetPos, toPlace, 3);

        // 故意傳入【地面 groundPos】的 Snapshot
        BlockSnapshot groundSnapshot = BlockSnapshot.create(level.dimension(), level, groundPos);
        BlockEvent.EntityPlaceEvent badEvent = new BlockEvent.EntityPlaceEvent(groundSnapshot, level.getBlockState(groundPos), executor);

        NeoForge.EVENT_BUS.post(badEvent);
        return 1;
    }
}