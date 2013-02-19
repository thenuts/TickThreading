package me.nallar.tickthreading.minecraft.commands;

import java.lang.reflect.Field;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class DumpCommand extends Command {
	public static String name = "dump";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender commandSender) {
		return !TickThreading.instance.requireOpForDumpCommand || super.canCommandSenderUseCommand(commandSender);
	}

	@Override
	public void processCommand(final ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		int x = 0;
		int y = 0;
		int z = 0;
		try {
			if (commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
			x = Integer.parseInt(arguments.remove(0));
			y = Integer.parseInt(arguments.remove(0));
			z = Integer.parseInt(arguments.remove(0));
			if (!arguments.isEmpty()) {
				world = DimensionManager.getWorld(Integer.parseInt(arguments.remove(0)));
			}
		} catch (Exception e) {
			world = null;
		}
		if (world == null) {
			sendChat(commandSender, "Usage: /dump x y z [world=currentworld]");
		}
		sendChat(commandSender, dump(new TableFormatter(commandSender), world, x, y, z).toString());
	}

	public static TableFormatter dump(TableFormatter tf, World world, int x, int y, int z) {
		StringBuilder sb = tf.sb;
		int blockId = world.getBlockIdWithoutLoad(x, y, z);
		if (blockId < 1) {
			sb.append("No loaded block at ").append(Log.name(world)).append("x,y,z").append(x).append(',').append(y).append(',').append(z);
		}
		TileEntity toDump = world.getTEWithoutLoad(x, y, z);
		if (toDump == null) {
			sb.append("No tile entity at ").append(Log.name(world)).append("x,y,z").append(x).append(',').append(y).append(',').append(z);
			return tf;
		}
		tf
				.heading("Field")
				.heading("Value");
		Class<?> clazz = toDump.getClass();
		do {
			for (Field field : clazz.getDeclaredFields()) {
				field.setAccessible(true);
				tf.row(field.getName());
				try {
					String value = field.get(toDump).toString();
					tf.row(value.substring(0, Math.min(value.length(), 32)));
				} catch (IllegalAccessException e) {
					tf.row(e.getMessage());
				}
			}
		} while ((clazz = clazz.getSuperclass()) != Object.class);
		tf.finishTable();
		return tf;
	}
}