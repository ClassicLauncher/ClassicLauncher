package net.classiclauncher.launcher.launch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.utano.librarymanager.LibraryManager;
import dev.utano.librarymanager.Repository;
import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.platform.Platform;

/**
 * Generic JAR-based launch strategy that reads a {@code libs.yml} file, downloads dependencies via
 * {@link LibraryManager}, and invokes the JVM.
 *
 * <p>
 * Suitable for games that distribute a {@code libs.yml} dependency manifest alongside their launcher JAR.
 * Minecraft-specific logic belongs in the extension's {@code MinecraftJavaLaunchStrategy} instead.
 */
public final class JarLaunchStrategy implements LaunchStrategy {

	private static final Logger log = LogManager.getLogger(JarLaunchStrategy.class);

	public static final JarLaunchStrategy INSTANCE = new JarLaunchStrategy();

	private static final String CONFIG_FILE = "libs.yml";
	private static final String BASE_DIR = ".";

	/**
	 * Classpath built during {@link #prepare}; read during {@link #buildCommand}.
	 */
	private volatile String classpath = "";

	private JarLaunchStrategy() {
	}

	@Override
	public void prepare(LaunchContext ctx, LaunchProgress progress) throws IOException {
		progress.log("Initialising library manager...");

		YmlConfig yamlConfig = new YmlConfig(BASE_DIR, CONFIG_FILE);
		yamlConfig.load();

		LibraryManager libManager = new LibraryManager(BASE_DIR);
		libManager.loadFromYml(yamlConfig);
		libManager.addRepository(new Repository("https://repo.maven.apache.org/maven2/"));
		libManager.addRepository(new Repository("https://repo1.maven.org/maven2/"));
		libManager.addRepository(new Repository("https://oss.sonatype.org/content/groups/public/"));
		libManager.addRepository(new Repository("https://jfrog.bintray.com/public"));

		progress.log("Downloading libraries...");
		try {
			libManager.downloadLibraries();
		} catch (IOException e) {
			progress.log("[ERROR] Library download failed: " + e.getMessage());
			log.error("Library download failed", e);
		}

		classpath = libManager.getClasspath();
		progress.log("Classpath resolved: " + classpath);
	}

	@Override
	public List<String> buildCommand(LaunchContext ctx) {
		String javaExe = (ctx.getJre() != null) ? ctx.getJre().getExecutablePath() : "java";

		List<String> command = new ArrayList<>();
		command.add(javaExe);

		// Platform-specific JVM flags
		if (Platform.current() == Platform.MACOS) {
			command.add("-XstartOnFirstThread");
			command.add("-Djava.awt.headless=true");
		}

		// Profile extra JVM arguments
		String profileJvmArgs = ctx.getProfile().getJvmArguments();
		if (profileJvmArgs != null && !profileJvmArgs.isEmpty()) {
			for (String arg : profileJvmArgs.split("\\s+")) {
				if (!arg.isEmpty()) command.add(arg);
			}
		}

		command.add("-cp");
		command.add(classpath + File.pathSeparator + "Client-0.0.1-DEV.jar");
		command.add("net.classiclauncher.client.Main");

		return command;
	}

}
