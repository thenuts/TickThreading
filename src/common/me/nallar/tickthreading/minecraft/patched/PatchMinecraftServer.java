package me.nallar.tickthreading.minecraft.patched;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.crash.CrashReport;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet4UpdateTime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public abstract class PatchMinecraftServer extends MinecraftServer {
	public ThreadManager threadManager;
	private static float tickTime = 0;
	private AtomicInteger currentWorld;
	private Integer[] dimensionIdsToTick;
	private Runnable tickRunnable;

	public PatchMinecraftServer(File par1File) {
		super(par1File);
	}

	@Override
	public String getServerModName() {
		return "tickthreading-fml";
	}

	@Override
	public void tick() {
		FMLCommonHandler.instance().rescheduleTicks(Side.SERVER);
		long var1 = System.nanoTime();
		AxisAlignedBB.getAABBPool().cleanPool();
		FMLCommonHandler.instance().onPreServerTick();
		++this.tickCounter;

		if (this.startProfiling) {
			this.startProfiling = false;
			this.theProfiler.profilingEnabled = true;
			this.theProfiler.clearProfiling();
		}

		this.theProfiler.startSection("root");
		this.updateTimeLightAndEntities();

		if (this.tickCounter % TickThreading.instance.saveInterval == 0) {
			this.theProfiler.startSection("save");
			this.serverConfigManager.saveAllPlayerData();
			this.saveAllWorlds(true);
			this.theProfiler.endSection();
		}

		this.theProfiler.startSection("tallying");
		this.tickTimeArray[this.tickCounter % 100] = System.nanoTime() - var1;
		this.sentPacketCountArray[this.tickCounter % 100] = Packet.sentID - this.lastSentPacketID;
		this.lastSentPacketID = Packet.sentID;
		this.sentPacketSizeArray[this.tickCounter % 100] = Packet.sentSize - this.lastSentPacketSize;
		this.lastSentPacketSize = Packet.sentSize;
		this.receivedPacketCountArray[this.tickCounter % 100] = Packet.receivedID - this.lastReceivedID;
		this.lastReceivedID = Packet.receivedID;
		this.receivedPacketSizeArray[this.tickCounter % 100] = Packet.receivedSize - this.lastReceivedSize;
		this.lastReceivedSize = Packet.receivedSize;
		this.theProfiler.endStartSection("snooper");

		if (!this.usageSnooper.isSnooperRunning() && this.tickCounter > 100) {
			this.usageSnooper.startSnooper();
		}

		if (this.tickCounter % 6000 == 0) {
			this.usageSnooper.addMemoryStatsToSnooper();
		}

		this.theProfiler.endSection();
		this.theProfiler.endSection();
		FMLCommonHandler.instance().onPostServerTick();
	}

	@Override
	public void updateTimeLightAndEntities() {
		this.theProfiler.startSection("levels");
		int var1;
		long startTime = System.nanoTime();

		dimensionIdsToTick = DimensionManager.getIDs();

		if (threadManager == null) {
			threadManager = new ThreadManager(8, "World Tick");
			currentWorld = new AtomicInteger(0);
			tickRunnable = new TickRunnable(this);
		}

		currentWorld.set(0);

		if (tickCounter < 100 || theProfiler.profilingEnabled || !TickThreading.instance.enableWorldTickThreading) {
			doTick();
		} else {
			int count = Math.min(Runtime.getRuntime().availableProcessors(), dimensionIdsToTick.length);
			for (int i = 0; i < count; i++) {
				threadManager.run(tickRunnable);
			}
			threadManager.waitForCompletion();
		}

		this.theProfiler.endStartSection("dim_unloading");
		DimensionManager.unloadWorlds(worldTickTimes);
		this.theProfiler.endStartSection("connection");
		this.getNetworkThread().networkTick();
		this.theProfiler.endStartSection("players");
		this.serverConfigManager.sendPlayerInfoToAllPlayers();
		this.theProfiler.endStartSection("tickables");

		for (var1 = 0; var1 < this.tickables.size(); ++var1) {
			((IUpdatePlayerListBox) this.tickables.get(var1)).update();
		}

		tickTime = (tickTime * 127 + ((System.nanoTime() - startTime) / 1000000)) / 128;
		this.theProfiler.endSection();
	}

	@Declare
	public static float getTickTime() {
		return tickTime;
	}

	@Declare
	public void doTick() {
		int i;
		while ((i = currentWorld.getAndIncrement()) < dimensionIdsToTick.length) {
			int id = dimensionIdsToTick[i];
			long var2 = System.nanoTime();

			if (id == 0 || this.getAllowNether()) {
				WorldServer var4 = DimensionManager.getWorld(id);
				this.theProfiler.startSection(var4.getWorldInfo().getWorldName());
				this.theProfiler.startSection("pools");
				var4.getWorldVec3Pool().clear();
				this.theProfiler.endSection();

				if (this.tickCounter % 60 == 0) {
					this.theProfiler.startSection("timeSync");
					this.serverConfigManager.sendPacketToAllPlayersInDimension(new Packet4UpdateTime(var4.getTotalWorldTime(), var4.getWorldTime()), var4.provider.dimensionId);
					this.theProfiler.endSection();
				}

				this.theProfiler.startSection("forgeTick");
				FMLCommonHandler.instance().onPreWorldTick(var4);

				CrashReport var6;

				this.theProfiler.endStartSection("worldTick");
				try {
					var4.tick();
				} catch (Throwable var8) {
					var6 = CrashReport.makeCrashReport(var8, "Exception ticking world");
					var4.addWorldInfoToCrashReport(var6);
					throw new ReportedException(var6);
				}

				this.theProfiler.endStartSection("entityTick");
				try {
					var4.updateEntities();
				} catch (Throwable var7) {
					var6 = CrashReport.makeCrashReport(var7, "Exception ticking world entities");
					var4.addWorldInfoToCrashReport(var6);
					throw new ReportedException(var6);
				}

				this.theProfiler.endStartSection("postForgeTick");
				FMLCommonHandler.instance().onPostWorldTick(var4);
				this.theProfiler.endSection();
				this.theProfiler.startSection("tracker");
				var4.getEntityTracker().updateTrackedEntities();
				this.theProfiler.endSection();
				this.theProfiler.endSection();
			}

			worldTickTimes.get(id)[this.tickCounter % 100] = System.nanoTime() - var2;
		}
	}

	@Declare
	public void save() {
		this.saveAllWorlds(false);
	}

	public static class TickRunnable implements Runnable {
		final MinecraftServer minecraftServer;

		public TickRunnable(MinecraftServer minecraftServer) {
			this.minecraftServer = minecraftServer;
		}

		@Override
		public void run() {
			minecraftServer.doTick();
		}
	}
}
