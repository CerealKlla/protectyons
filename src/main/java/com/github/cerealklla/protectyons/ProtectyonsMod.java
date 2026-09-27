package com.github.cerealklla.protectyons;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.protectyons.enforcement.VoxelProtectionListener;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(ProtectyonsMod.MODID)
public class ProtectyonsMod {
    public static final String MODID = "protectyons";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProtectyonsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Game-bus listener (not the mod bus above) -- this is the entire mod's job: cancel
        // protected solid-block break/place attempts. See VoxelProtectionListener's own doc.
        NeoForge.EVENT_BUS.register(new VoxelProtectionListener());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Protectyons common setup");
    }
}
