package com.ts.br.event;

import com.ts.br.BlockRestoreMod;
import com.ts.br.command.SimulateGriefCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = BlockRestoreMod.MODID)
public class ModCommandRegister {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SimulateGriefCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}