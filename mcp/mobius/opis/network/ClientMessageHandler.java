package mcp.mobius.opis.network;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;

import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;

import cpw.mods.fml.common.network.PacketDispatcher;
import mcp.mobius.mobiuscore.profiler.ProfilerRegistrar;
import mcp.mobius.opis.modOpis;
import mcp.mobius.opis.data.client.DataCache;
import mcp.mobius.opis.data.client.TickHandlerClientProfiler;
import mcp.mobius.opis.data.holders.ISerializable;
import mcp.mobius.opis.data.holders.stats.StatsTickHandler;
import mcp.mobius.opis.data.managers.ChunkManager;
import mcp.mobius.opis.data.managers.MetaManager;
import mcp.mobius.opis.data.managers.TickHandlerManager;
import mcp.mobius.opis.events.OpisClientTickHandler;
import mcp.mobius.opis.gui.overlay.OverlayMeanTime;
import mcp.mobius.opis.gui.overlay.entperchunk.OverlayEntityPerChunk;
import mcp.mobius.opis.network.enums.Message;
import mcp.mobius.opis.network.packets.client.Packet_ReqData;
import mcp.mobius.opis.swing.SwingUI;
import mcp.mobius.opis.data.holders.basetypes.AmountHolder;
import mcp.mobius.opis.data.holders.basetypes.CoordinatesBlock;
import mcp.mobius.opis.data.holders.basetypes.CoordinatesChunk;
import mcp.mobius.opis.data.holders.basetypes.SerialDouble;
import mcp.mobius.opis.data.holders.basetypes.SerialInt;
import mcp.mobius.opis.data.holders.basetypes.SerialLong;
import mcp.mobius.opis.data.holders.basetypes.TicketData;
import mcp.mobius.opis.network.enums.AccessLevel;
import mcp.mobius.opis.network.enums.Message;
import mcp.mobius.opis.network.enums.Packets;

public class ClientMessageHandler {
	
	private static ClientMessageHandler _instance;
	private ClientMessageHandler(){}
	
	public static ClientMessageHandler instance(){
		if(_instance == null)
			_instance = new ClientMessageHandler();			
		return _instance;
	}
	
	public void handle(Message cmd){
		if (cmd == Message.CLIENT_START_PROFILING){
			modOpis.log.log(Level.INFO, "Started profiling");
			
			MetaManager.reset();		
			modOpis.profilerRun = true;
			ProfilerRegistrar.turnOn();		
		}
		else if (cmd == Message.CLIENT_SHOW_RENDER_TICK){
			modOpis.log.log(Level.INFO, "=== RENDER TICK ===");
			ArrayList<StatsTickHandler> stats = TickHandlerManager.getCumulatedStats();
			for (StatsTickHandler stat : stats){
				System.out.printf("%s \n", stat);
			}
		}
		
		else if (cmd == Message.CLIENT_SHOW_SWING){
			SwingUI.instance().showUI();		
		}
		
		else if (cmd == Message.CLIENT_CLEAR_SELECTION){
			modOpis.selectedBlock = null;			
		}
	}
	
	public void handle(Message msg, ArrayList<ISerializable> data){
		
		if (msg == Message.LIST_TIMING_CHUNK){
			ChunkManager.setChunkMeanTime(data);
		}
		
		else if (msg == Message.LIST_CHUNK_ENTITIES){
			OverlayEntityPerChunk.instance().setEntStats(data);
			OverlayEntityPerChunk.instance().setupEntTable();		
		}
		else if (msg == Message.LIST_CHUNK_TILEENTS){
			OverlayMeanTime.instance().setupTable(data);	 
		}			     
		
		else if (msg == Message.LIST_CHUNK_LOADED){
			ChunkManager.setLoadedChunks(data);	 
		}			
	}
	
	public void handle(Message msg, ISerializable data){
		if (msg == Message.VALUE_TIMING_TILEENTS){
			SwingUI.instance().getPanelSummary().setTimingTileEntsTotal(((SerialDouble)data).value);
			SwingUI.instance().getPanelTimingTileEnts().getLblSummary().setText(String.format("Total update time : %.3f µs", ((SerialDouble)data).value));			
		}
		
		else if (msg == Message.VALUE_TIMING_ENTITIES){
			SwingUI.instance().getPanelSummary().setTimingEntitiesTotal(((SerialDouble)data).value);
			SwingUI.instance().getPanelTimingEntities().getLblSummary().setText(String.format("Total update time : %.3f µs", ((SerialDouble)data).value));
		}
		
		else if (msg == Message.VALUE_TIMING_HANDLERS){
			SwingUI.instance().getPanelSummary().setTimingHandlersTotal(((SerialDouble)data).value);
			SwingUI.instance().getPanelTimingHandlers().getLblSummary().setText(String.format("Total update time : %.3f µs", ((SerialDouble)data).value));			
		}
	
		else if (msg == Message.VALUE_TIMING_WORLDTICK){
			SwingUI.instance().getPanelSummary().setTimingWorldTickTotal(((SerialDouble)data).value);
		}
	
		else if (msg == Message.VALUE_TIMING_ENTUPDATE)
			SwingUI.instance().getPanelSummary().setTimingEntUpdateTotal(((SerialDouble)data).value);						

		else if (msg == Message.STATUS_START){
			SwingUI.instance().setTextRunButton("Running...");
			SwingUI.instance().getPanelSummary().setProgressBar(0, ((SerialInt)data).value, 0);
		}
		     
		else if (msg == Message.STATUS_STOP){
			SwingUI.instance().setTextRunButton("Run Opis");
			SwingUI.instance().getPanelSummary().setProgressBar(0, ((SerialInt)data).value, ((SerialInt)data).value);
		}
		     
		else if (msg == Message.STATUS_RUN_UPDATE){
			SwingUI.instance().getPanelSummary().setProgressBar(-1, -1, ((SerialInt)data).value);			
		}
		     
		else if (msg == Message.STATUS_RUNNING){
			SwingUI.instance().setTextRunButton("Running...");
			SwingUI.instance().getPanelSummary().setProgressBar(-1, ((SerialInt)data).value, -1);
		}			     
	
		else if (msg == Message.STATUS_TIME_LAST_RUN){
			long serverLastRun = ((SerialLong)data).value;
			if (serverLastRun == 0){
				SwingUI.instance().getPanelSummary().getLblTimeStamp().setText("Last run : <Never>");
			} else {
				long clientLastRun = serverLastRun + DataCache.instance().getClockScrew();
		        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		        Date resultdate = new Date(clientLastRun);
				
		        SwingUI.instance().getPanelSummary().getLblTimeStamp().setText(String.format("Last run : %s", sdf.format(resultdate)));
			}
	
		}	
	
		else if(msg == Message.CLIENT_HIGHLIGHT_BLOCK){
			modOpis.selectedBlock = (CoordinatesBlock)data;
			SwingUI.instance().getPanelTimingTileEnts().getBtnReset().setEnabled(true);
		}
	}
}
