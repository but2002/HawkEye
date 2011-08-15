package uk.co.oliwali.HawkEye;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import uk.co.oliwali.HawkEye.database.DataManager;
import uk.co.oliwali.HawkEye.entry.DataEntry;
import uk.co.oliwali.HawkEye.util.Config;
import uk.co.oliwali.HawkEye.util.Util;

/**
 * Runnable class for performing a data rollback.
 * This class should always be run in a separate thread to avoid impacting on server performance
 * @author oliverw92
 */
public class Rollback implements Runnable {
	
	public PlayerSession session = null;
	private Iterator<DataEntry> rollbackQueue;
	private List<BlockState> undo = new ArrayList<BlockState>();
	private List<Block> localUndo = new ArrayList<Block>();
	private int timerID;
	private int counter = 0;
	private RollbackType rollbackType = RollbackType.GLOBAL;
	
	/**
	 * @param session {@link PlayerSession} to retrieve rollback results from
	 */
	public Rollback(RollbackType rollbackType, PlayerSession session) {
		
		this.rollbackType = rollbackType;
		this.session = session;
		rollbackQueue = session.getRollbackResults().iterator();
		session.setRollbackUndo(null);
		
		//Check that we actually have results
		if (!rollbackQueue.hasNext()) {
			Util.sendMessage(session.getSender(), "&cNo results found to rollback");
			return;
		}
		
		Util.debug("Starting rollback of " + session.getRollbackResults().size() + " results");
		
		//Start rollback
		session.setDoingRollback(true);
		Util.sendMessage(session.getSender(), "&cAttempting to rollback &7" + session.getRollbackResults().size() + "&c results");
		timerID = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Bukkit.getServer().getPluginManager().getPlugin("HawkEye"), this, 1, 2);
		
	}
	
	/**
	 * Run the rollback.
	 * Contains appropriate methods of catching errors and notifying the player
	 */
	public void run() {
		
		//Start rollback process
		int i = 0;
		while (i < 200 && rollbackQueue.hasNext()) {
			
			DataEntry entry = rollbackQueue.next();
			
			//If the action can't be rolled back, skip this entry
			if (entry.getType() == null || !entry.getType().canRollback())
				continue;
			
			//If the world doesn't exist, skip this entry
			World world = HawkEye.server.getWorld(entry.getWorld());
			if (world == null)
				continue;
			
			//Get some data from the entry
			Location loc = new Location(world, entry.getX(), entry.getY(), entry.getZ());
			Block block = world.getBlockAt(loc);
			
			BlockState state = block.getState();
			//Attempt rollback
			if (rollbackType == RollbackType.GLOBAL && entry.rollback(world.getBlockAt(loc))) {
				undo.add(state);
				
				//Delete data if told to
				if (Config.DeleteDataOnRollback)
					DataManager.deleteEntry(entry.getDataId());
				
				counter++;
			}
			else if (rollbackType == RollbackType.LOCAL && entry.rollbackPlayer(block, (Player)session.getSender())) {
				localUndo.add(block);
				counter++;
			}
			
		}
		
		//Check if rollback is finished
		if (!rollbackQueue.hasNext()) {
			
			//End timer
			Bukkit.getServer().getScheduler().cancelTask(timerID);
			
			//Store undo results and notify player
			if (rollbackType == RollbackType.GLOBAL) {
				session.setRollbackUndo(undo);
				session.setDoingRollback(false);
				Util.sendMessage(session.getSender(), "&cRollback complete, &7" + counter + "&c edits performed");
				Util.sendMessage(session.getSender(), "&cUndo this rollback using &7/hawk undo");
			}
			else {
				session.setLocalRollbackUndo(localUndo);
				Util.sendMessage(session.getSender(), "&cRollback preview complete, &7" + counter + "&c edits performed to you");
				Util.sendMessage(session.getSender(), "&cType &7/hawk apply&c to make these changes permanent or &7/hawk cancel&c to cancel");
			}
			
			Util.debug("Rollback complete, " + counter + " edits performed");
			
		}
		
	}
	
	public enum RollbackType {
		GLOBAL,
		LOCAL
	}

}