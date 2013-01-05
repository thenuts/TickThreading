package me.nallar.tickthreading;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import cpw.mods.fml.common.FMLLog;
import me.nallar.reporting.Reporter;
import net.minecraft.world.World;

@SuppressWarnings ("UnusedDeclaration")
public class Log {
	public static final Logger LOGGER = Logger.getLogger("TickThreading");

	static {
		try {
			LOGGER.setParent(FMLLog.getLogger());
			LOGGER.setUseParentHandlers(true);
			saveToFile(new File("tickthreading.log"), LOGGER);
		} catch (NoClassDefFoundError ignored) {
			// Not running under forge
			LOGGER.setUseParentHandlers(false);
			LOGGER.addHandler(new Handler() {
				private LogFormatter logFormatter = new LogFormatter();

				@Override
				public void publish(LogRecord record) {
					System.out.print(logFormatter.format(record));
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() throws SecurityException {
				}
			});
		}
		LOGGER.setLevel(Level.ALL);
	}

	public static void saveToFile(final File file, Logger... loggers) {
		try {
			RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
			try {
				randomAccessFile.setLength(0);
			} finally {
				randomAccessFile.close();
			}
			Handler handler = new Handler() {
				private LogFormatter logFormatter = new LogFormatter();
				private BufferedWriter outputWriter = new BufferedWriter(new FileWriter(file));
				private int count = 0;

				@Override
				public void publish(LogRecord record) {
					if (record.getLevel().intValue() >= Level.INFO.intValue()) {
						try {
							outputWriter.write(logFormatter.format(record));
							count++;
							if (count > 200) {
								count = 0;
								outputWriter.flush();
							}
						} catch (IOException ignored) {
							// Can't log here, might cause infinite recursion
						}
					}
				}

				@Override
				public void flush() {
					try {
						outputWriter.flush();
					} catch (IOException ignored) {
					}
				}

				@Override
				public void close() throws SecurityException {
					try {
						outputWriter.close();
					} catch (IOException ignored) {
						// ignored - shouldn't log if logging fails
					}
				}
			};
			for (Logger logger : loggers) {
				logger.addHandler(handler);
			}
		} catch (IOException e) {
			Log.severe("Can't write logs to disk", e);
		}
	}

	public static void severe(String msg) {
		LOGGER.severe(msg);
	}

	public static void warning(String msg) {
		LOGGER.warning(msg);
	}

	public static void info(String msg) {
		LOGGER.info(msg);
	}

	public static void config(String msg) {
		LOGGER.config(msg);
	}

	public static void fine(String msg) {
		LOGGER.fine(msg);
	}

	public static void finer(String msg) {
		LOGGER.finer(msg);
	}

	public static void finest(String msg) {
		LOGGER.finest(msg);
	}

	public static void severe(String msg, Throwable t, boolean report) {
		if (report) {
			Reporter.report(t);
		}
		severe(msg, t);
	}

	public static void severe(String msg, Throwable t) {
		LOGGER.log(Level.SEVERE, msg, t);
	}

	public static void warning(String msg, Throwable t) {
		LOGGER.log(Level.WARNING, msg, t);
	}

	public static void info(String msg, Throwable t) {
		LOGGER.log(Level.INFO, msg, t);
	}

	public static void config(String msg, Throwable t) {
		LOGGER.log(Level.CONFIG, msg, t);
	}

	public static void fine(String msg, Throwable t) {
		LOGGER.log(Level.FINE, msg, t);
	}

	public static void finer(String msg, Throwable t) {
		LOGGER.log(Level.FINER, msg, t);
	}

	public static void finest(String msg, Throwable t) {
		LOGGER.log(Level.FINEST, msg, t);
	}

	public static String name(World world) {
		return world.getWorldInfo().getWorldName() + '/' + world.provider.getDimensionName() + (world.isRemote ? "-r" : "");
	}
}
