package mcp.mobius.opis.events;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

import com.google.common.collect.Table;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.util.ChatMessageComponent;
import mcp.mobius.opis.modOpis;
import mcp.mobius.opis.data.holders.basetypes.AmountHolder;
import mcp.mobius.opis.data.holders.basetypes.CoordinatesChunk;
import mcp.mobius.opis.data.holders.basetypes.SerialDouble;
import mcp.mobius.opis.data.holders.basetypes.SerialInt;
import mcp.mobius.opis.data.holders.basetypes.SerialLong;
import mcp.mobius.opis.data.holders.stats.StatsChunk;
import mcp.mobius.opis.data.holders.stats.StatsChunkMedian;
import mcp.mobius.opis.data.holders.stats.StatsEntity;
import mcp.mobius.opis.data.holders.stats.StatsTickHandler;
import mcp.mobius.opis.data.holders.stats.StatsTileEntity;
import mcp.mobius.opis.data.managers.ChunkManager;
import mcp.mobius.opis.data.managers.EntityManager;
import mcp.mobius.opis.data.managers.GlobalTimingManager;
import mcp.mobius.opis.data.managers.TickHandlerManager;
import mcp.mobius.opis.data.managers.TileEntityManager;
import mcp.mobius.opis.data.server.EntUpdateProfiler;
import mcp.mobius.opis.data.server.NetworkProfiler;
import mcp.mobius.opis.data.server.WorldTickProfiler;
import mcp.mobius.opis.data.server.TickProfiler;
import mcp.mobius.opis.gui.overlay.OverlayStatus;
import mcp.mobius.opis.network.OpisPacketHandler;
import mcp.mobius.opis.network.enums.Message;
import mcp.mobius.opis.network.packets.server.NetDataList;
import mcp.mobius.opis.network.packets.server.NetDataValue;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import mcp.mobius.mobiuscore.profiler.DummyProfiler;
import mcp.mobius.mobiuscore.profiler.ProfilerRegistrar;

public class OpisServerTickHandler implements ITickHandler {

	public long profilerUpdateTickCounter = 0;	
	public int  profilerRunningTicks;
	public long timer500  = System.nanoTime();	
	public long timer1000 = System.nanoTime();
	public long timer5000 = System.nanoTime();
	
	public static OpisServerTickHandler instance;
	
	public OpisServerTickHandler(){
		instance = this;
	}
	
	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if(type.contains(TickType.SERVER)){

			/*
			if (System.nanoTime() - timer500 >  500000000){
				timer500 = System.nanoTime();
				
				for (Player player : PlayerTracker.instance().playersSwing){
					PacketDispatcher.sendPacketToPlayer(Packet_DataValue.create(DataReq.VALUE_TIMING_TICK,     TickProfiler.instance().stats), player);					
				}
			}
			*/
				
			// One second timer
			if (System.nanoTime() - timer1000 > 1000000000L){
				timer1000 = System.nanoTime();

				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_AMOUNT_UPLOAD,   new SerialLong(NetworkProfiler.INSTANCE.dataSizeOut)));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_AMOUNT_DOWNLOAD, new SerialLong(NetworkProfiler.INSTANCE.dataSizeIn)));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_CHUNK_FORCED,    new SerialInt(ChunkManager.instance().getForcedChunkAmount())));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_CHUNK_LOADED,    new SerialInt(ChunkManager.instance().getLoadedChunkAmount())));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_TIMING_TICK,     TickProfiler.instance().stats));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_AMOUNT_TILEENTS, new SerialInt(TileEntityManager.getAmountTileEntities())));
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.VALUE_AMOUNT_ENTITIES, new SerialInt(EntityManager.getAmountEntities())));
				
				OpisPacketHandler.sendPacketToAllSwing(NetDataList.create(Message.LIST_PLAYERS,           EntityManager.getAllPlayers()));
				
				for (Player player : PlayerTracker.instance().playersSwing){
					OpisPacketHandler.validateAndSend(NetDataValue.create(Message.STATUS_ACCESS_LEVEL, new SerialInt(PlayerTracker.instance().getPlayerAccessLevel(player).ordinal())), player);
				}
				
				
				if (modOpis.profilerRun){
					OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.STATUS_RUNNING,   new SerialInt(modOpis.profilerMaxTicks)));
					OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.STATUS_RUN_UPDATE,     new SerialInt(profilerRunningTicks)));
				}
				
				NetworkProfiler.INSTANCE.dataSizeOut = 0L;
				NetworkProfiler.INSTANCE.dataSizeIn  = 0L;
			}
			
			// Five second timer
			if (System.nanoTime() - timer5000 > 5000000000L){
				timer5000 = System.nanoTime();
				updatePlayers();
			}
			
			profilerUpdateTickCounter++;
			
			if (profilerRunningTicks < modOpis.profilerMaxTicks && modOpis.profilerRun){
				profilerRunningTicks++;
			}else if (profilerRunningTicks >= modOpis.profilerMaxTicks && modOpis.profilerRun){
				profilerRunningTicks = 0;
				modOpis.profilerRun = false;
				ProfilerRegistrar.turnOff();				
				
				OpisPacketHandler.sendPacketToAllSwing(NetDataValue.create(Message.STATUS_STOP, new SerialInt(modOpis.profilerMaxTicks)));
				
				for (Player player : PlayerTracker.instance().playersSwing){
					OpisPacketHandler.sendFullUpdate(player);
				}
				
				for(Table.Cell<Integer, String, Double> cell : GlobalTimingManager.INSTANCE.getStatsDim().cellSet()){
					System.out.printf("[ %4d ] %20s : %.3f µs\n", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
				}

				for(Table.Cell<Integer, String, Double> cell : GlobalTimingManager.INSTANCE.getStatsDimPerSection().cellSet()){
					System.out.printf("[ %4d ] %20s : %.3f µs\n", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
				}				
				
				for(String key : NetworkProfiler.INSTANCE.stats.keySet()){
					System.out.printf("[ %s ] : %.3f µs\n", key, NetworkProfiler.INSTANCE.stats.get(key).getGeometricMean());
				}
				
				/*
				for(Table.Cell<CoordinatesChunk, String, Double> cell : GlobalTimingManager.INSTANCE.getStatsChunk().cellSet()){
					System.out.printf("[ %s ] %20s : %.3f µs\n", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
				}
				*/				
				
			}			
		}
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.SERVER);
	}

	@Override
	public String getLabel() {
		return "opis.server.tickhandler";
	}

	private void updatePlayers(){
		for (Player player : PlayerTracker.instance().playerOverlayStatus.keySet()){
			
			if (PlayerTracker.instance().playerOverlayStatus.get(player) == OverlayStatus.CHUNKSTATUS){
				OpisPacketHandler.validateAndSend(NetDataList.create(Message.LIST_CHUNK_LOADED, ChunkManager.instance().getLoadedChunks(PlayerTracker.instance().playerDimension.get(player))), player);	
				
			}
			
			if (PlayerTracker.instance().playerOverlayStatus.get(player) == OverlayStatus.MEANTIME){
				ArrayList<StatsChunk> timingChunks = ChunkManager.instance().getTopChunks(100);
				//OpisPacketHandler.validateAndSend(Packet_DataList.create(DataReq.LIST_TIMING_CHUNK, timingChunks), player);
				OpisPacketHandler.validateAndSend(NetDataList.create(Message.LIST_TIMING_CHUNK, timingChunks), player);
			}
		}
	}
	
}
