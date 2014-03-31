package mcp.mobius.opis;

import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import mcp.mobius.mobiuscore.profiler.ProfilerRegistrar;
import mcp.mobius.opis.commands.client.CommandClientShowRenderTick;
import mcp.mobius.opis.commands.client.CommandClientStart;
import mcp.mobius.opis.commands.client.CommandOpis;
import mcp.mobius.opis.commands.server.CommandAddPrivileged;
import mcp.mobius.opis.commands.server.CommandAmountEntities;
import mcp.mobius.opis.commands.server.CommandChunkDump;
import mcp.mobius.opis.commands.server.CommandChunkList;
import mcp.mobius.opis.commands.server.CommandEntityCreate;
import mcp.mobius.opis.commands.server.CommandFrequency;
import mcp.mobius.opis.commands.server.CommandHandler;
import mcp.mobius.opis.commands.server.CommandHelp;
import mcp.mobius.opis.commands.server.CommandKill;
import mcp.mobius.opis.commands.server.CommandKillAll;
import mcp.mobius.opis.commands.server.CommandReset;
import mcp.mobius.opis.commands.server.CommandRmPrivileged;
import mcp.mobius.opis.commands.server.CommandStart;
import mcp.mobius.opis.commands.server.CommandStop;
import mcp.mobius.opis.commands.server.CommandTicks;
import mcp.mobius.opis.commands.server.CommandTimingEntities;
import mcp.mobius.opis.commands.server.CommandTimingTileEntities;
import mcp.mobius.opis.data.client.TickHandlerClientProfiler;
import mcp.mobius.opis.data.holders.basetypes.CoordinatesBlock;
import mcp.mobius.opis.data.server.DeadManSwitch;
import mcp.mobius.opis.data.server.EntUpdateProfiler;
import mcp.mobius.opis.data.server.EntityProfiler;
import mcp.mobius.opis.data.server.HandlerProfiler;
import mcp.mobius.opis.data.server.NetworkProfiler;
import mcp.mobius.opis.data.server.WorldTickProfiler;
import mcp.mobius.opis.data.server.TickProfiler;
import mcp.mobius.opis.data.server.TileEntityProfiler;
import mcp.mobius.opis.events.OpisClientEventHandler;
import mcp.mobius.opis.events.OpisClientTickHandler;
import mcp.mobius.opis.events.OpisServerEventHandler;
import mcp.mobius.opis.events.OpisServerTickHandler;
import mcp.mobius.opis.events.PlayerTracker;
import mcp.mobius.opis.helpers.ModIdentification;
import mcp.mobius.opis.network.OpisConnectionHandler;
import mcp.mobius.opis.network.OpisPacketHandler;
import mcp.mobius.opis.network.enums.AccessLevel;
import mcp.mobius.opis.network.enums.Message;
import mcp.mobius.opis.network.packets.custom.Packet251Extended;
import mcp.mobius.opis.proxy.ProxyServer;
import mcp.mobius.opis.tools.BlockLag;
import mcp.mobius.opis.tools.TileLag;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(modid="Opis", name="Opis", version="1.2.1_dev1_alpha")
@NetworkMod(channels={"Opis", "Opis_Chunk"},clientSideRequired=false, serverSideRequired=false, connectionHandler=OpisConnectionHandler.class, packetHandler=OpisPacketHandler.class)

public class modOpis {

	@Instance("Opis")
	public static modOpis instance;	

	public static Logger log = Logger.getLogger("Opis");	

	@SidedProxy(clientSide="mcp.mobius.opis.proxy.ProxyClient", serverSide="mcp.mobius.opis.proxy.ProxyServer")
	public static ProxyServer proxy;		

	public static int profilerDelay    = 1;
	public static boolean profilerRun  = false; 
	public static int profilerMaxTicks = 250;
	public static boolean microseconds = true;
	private static int lagGenID        = -1;
	public static CoordinatesBlock selectedBlock = null;
	
	public  Configuration config = null;	
	
	public static String commentTables     = "Minimum access level to be able to view tables in /opis command. Valid values : NONE, PRIVILEGED, ADMIN";
	public static String commentOverlays   = "Minimum access level to be able to show overlays in MapWriter. Valid values : NONE, PRIVILEGED, ADMIN";
	public static String commentOpis       = "Minimum access level to be open Opis interface. Valid values : NONE, PRIVILEGED, ADMIN";
	public static String commentPrivileged = "List of players with PRIVILEGED access level.";
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		config = new Configuration(event.getSuggestedConfigurationFile());

		profilerDelay    = config.get(Configuration.CATEGORY_GENERAL, "profiler.delay", 1).getInt();
		lagGenID         = config.get(Configuration.CATEGORY_GENERAL, "laggenerator_id", -1).getInt();
		profilerMaxTicks = config.get(Configuration.CATEGORY_GENERAL, "profiler.maxpts", 250).getInt();
		microseconds     = config.get(Configuration.CATEGORY_GENERAL, "display.microseconds", true).getBoolean(true);
		
		
		String[] users   = config.get("ACCESS_RIGHTS", "privileged", new String[]{}, commentPrivileged).getStringList();
		AccessLevel minTables   = AccessLevel.PRIVILEGED;
		AccessLevel minOverlays = AccessLevel.PRIVILEGED;
		AccessLevel openOpis    = AccessLevel.PRIVILEGED;
		try{ openOpis    = AccessLevel.valueOf(config.get("ACCESS_RIGHTS", "opis",     "NONE", commentTables).getString()); }   catch (IllegalArgumentException e){}
		try{ minTables   = AccessLevel.valueOf(config.get("ACCESS_RIGHTS", "tables",   "NONE", commentTables).getString()); }   catch (IllegalArgumentException e){}
		try{ minOverlays = AccessLevel.valueOf(config.get("ACCESS_RIGHTS", "overlays", "NONE", commentOverlays).getString()); } catch (IllegalArgumentException e){}

		Message.setTablesMinimumLevel(minTables);
		Message.setOverlaysMinimumLevel(minOverlays);
		Message.setOpisMinimumLevel(openOpis);
		
		for (String s : users)
			PlayerTracker.instance().addPrivilegedPlayer(s,false);
		
		config.save();
		
		MinecraftForge.EVENT_BUS.register(new OpisClientEventHandler());
		MinecraftForge.EVENT_BUS.register(new OpisServerEventHandler());
		//Packet.addIdClassMapping(251, true, true, Packet251Extended.class);
	}	
	
	@EventHandler
	public void load(FMLInitializationEvent event) {
		TickRegistry.registerTickHandler(new OpisServerTickHandler(), Side.SERVER);
		TickRegistry.registerTickHandler(new OpisClientTickHandler(), Side.CLIENT);
		
		ProfilerRegistrar.registerProfilerTileEntity(new TileEntityProfiler());
		ProfilerRegistrar.registerProfilerEntity(new EntityProfiler());
		ProfilerRegistrar.registerProfilerHandler(new HandlerProfiler());
		ProfilerRegistrar.registerProfilerWorldTick(new WorldTickProfiler());
		ProfilerRegistrar.registerProfilerTick(TickProfiler.instance());
		ProfilerRegistrar.registerProfilerEntUpdate(new EntUpdateProfiler());
		ProfilerRegistrar.registerProfilerPacket(NetworkProfiler.INSTANCE);

		if (lagGenID != -1){
			Block blockDemo = new BlockLag(lagGenID, Material.wood);
			GameRegistry.registerBlock(blockDemo, "opis.laggen");
			GameRegistry.registerTileEntity(TileLag.class, "opis.laggen");
		}
		
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
        ModIdentification.init();
		proxy.init();
	}	
	
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event){
		event.registerServerCommand(new CommandChunkList());
		event.registerServerCommand(new CommandFrequency());
		event.registerServerCommand(new CommandStart());
		event.registerServerCommand(new CommandStop());		
		event.registerServerCommand(new CommandTimingTileEntities());
		event.registerServerCommand(new CommandTicks());
		event.registerServerCommand(new CommandTimingEntities());
		event.registerServerCommand(new CommandAmountEntities());
		event.registerServerCommand(new CommandKill());
		event.registerServerCommand(new CommandKillAll());		
		event.registerServerCommand(new CommandReset());
		event.registerServerCommand(new CommandHandler());
		event.registerServerCommand(new CommandEntityCreate());
		event.registerServerCommand(new CommandOpis());
		event.registerServerCommand(new CommandAddPrivileged());
		event.registerServerCommand(new CommandRmPrivileged());		
		
		//event.registerServerCommand(new CommandClientTest());
		//event.registerServerCommand(new CommandClientStart());
		//event.registerServerCommand(new CommandClientShowRenderTick());		
		
		event.registerServerCommand(new CommandHelp());
		
		GameRegistry.registerPlayerTracker(PlayerTracker.instance());
		
		DeadManSwitch.startDeadManSwitch(MinecraftServer.getServer());
	}	
}
