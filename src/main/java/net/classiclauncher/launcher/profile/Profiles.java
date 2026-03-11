package net.classiclauncher.launcher.profile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;

public class Profiles {

	private final List<Profile> profiles = new ArrayList<>();

	public void load() {
		profiles.clear();
		File dir = LauncherContext.getInstance().resolve("profiles");
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
		if (files == null) return;
		for (File file : files) {
			String id = file.getName().substring(0, file.getName().length() - 4);
			YmlConfig config = new YmlConfig(file);
			config.load();
			profiles.add(Profile.fromConfig(id, config));
		}
	}

	public void add(Profile profile) {
		profiles.add(profile);
		save(profile);
	}

	public void remove(String id) {
		profiles.removeIf(p -> p.getId().equals(id));
		File file = new File(LauncherContext.getInstance().resolve("profiles"), id + ".yml");
		if (file.exists()) file.delete();
	}

	public void update(Profile profile) {
		profiles.replaceAll(p -> p.getId().equals(profile.getId()) ? profile : p);
		save(profile);
	}

	public void save(Profile profile) {
		File dir = LauncherContext.getInstance().resolve("profiles");
		dir.mkdirs();
		YmlConfig config = new YmlConfig(new File(dir, profile.getId() + ".yml"));
		profile.save(config);
	}

	public List<Profile> getAll() {
		return Collections.unmodifiableList(profiles);
	}

	public Optional<Profile> getById(String id) {
		return profiles.stream().filter(p -> p.getId().equals(id)).findFirst();
	}

}
