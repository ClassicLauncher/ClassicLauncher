package net.classiclauncher.launcher.extension;

import java.util.ArrayList;
import java.util.List;

import dev.utano.ymlite.config.YmlConfig;

/**
 * Locally persisted state for an installed extension.
 *
 * <p>
 * One record is stored per installed extension at {@code <dataDir>/extensions/<id>.yml}. The record holds enough
 * information to re-download the extension on the next launch (via {@link #getManifestUrl()}) and to add its JARs to
 * the classloader ({@link #getGroupId()}, {@link #getArtifactId()}, {@link #getInstalledVersion()}).
 */
public final class ExtensionRecord {

	private final String id;
	private final String manifestUrl;
	private final String name;
	private final String description;
	private final String pageUrl;
	private final String minLauncherVersion;
	private final String groupId;
	private final String artifactId;
	private final String installedVersion;
	private final List<String> repositoryUrls;
	private final List<ExtensionManifest.DependencySpec> dependencies;
	/**
	 * Manifest URLs of other extensions that must be loaded before this one.
	 */
	private final List<String> requiredExtensionUrls;
	private boolean enabled;
	private boolean autoUpdate;

	public ExtensionRecord(String id, String manifestUrl, String name, String description, String pageUrl,
			String minLauncherVersion, String groupId, String artifactId, String installedVersion,
			List<String> repositoryUrls, List<ExtensionManifest.DependencySpec> dependencies,
			List<String> requiredExtensionUrls, boolean enabled, boolean autoUpdate) {
		this.id = id;
		this.manifestUrl = manifestUrl;
		this.name = name;
		this.description = description;
		this.pageUrl = pageUrl;
		this.minLauncherVersion = minLauncherVersion;
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.installedVersion = installedVersion;
		this.repositoryUrls = repositoryUrls != null ? repositoryUrls : new ArrayList<String>();
		this.dependencies = dependencies != null ? dependencies : new ArrayList<ExtensionManifest.DependencySpec>();
		this.requiredExtensionUrls = requiredExtensionUrls != null ? requiredExtensionUrls : new ArrayList<String>();
		this.enabled = enabled;
		this.autoUpdate = autoUpdate;
	}

	// ── Getters ───────────────────────────────────────────────────────────────

	public String getId() {
		return id;
	}

	public String getManifestUrl() {
		return manifestUrl;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getPageUrl() {
		return pageUrl;
	}

	public String getMinLauncherVersion() {
		return minLauncherVersion;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getInstalledVersion() {
		return installedVersion;
	}

	public List<String> getRepositoryUrls() {
		return repositoryUrls;
	}

	public List<ExtensionManifest.DependencySpec> getDependencies() {
		return dependencies;
	}

	public List<String> getRequiredExtensionUrls() {
		return requiredExtensionUrls;
	}

	/**
	 * Returns the Maven coordinate key ({@code groupId:artifactId}) that uniquely identifies this extension.
	 */
	public String getCoordinateKey() {
		return groupId + ":" + artifactId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isAutoUpdate() {
		return autoUpdate;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setAutoUpdate(boolean autoUpdate) {
		this.autoUpdate = autoUpdate;
	}

	public boolean hasPageUrl() {
		return pageUrl != null && !pageUrl.trim().isEmpty();
	}

	// ── Persistence ───────────────────────────────────────────────────────────

	/**
	 * Serializes this record into {@code config} and saves the backing file.
	 *
	 * @param config
	 *            a loaded (or freshly created) {@link YmlConfig} for the record file
	 */
	public void save(YmlConfig config) {
		config.set("manifestUrl", manifestUrl != null ? manifestUrl : "");
		config.set("name", name != null ? name : "");
		config.set("description", description != null ? description : "");
		config.set("pageUrl", hasPageUrl() ? pageUrl : "");
		config.set("minLauncherVersion", minLauncherVersion != null ? minLauncherVersion : "0.0.0");
		config.set("groupId", groupId != null ? groupId : "");
		config.set("artifactId", artifactId != null ? artifactId : "");
		config.set("installedVersion", installedVersion != null ? installedVersion : "");
		config.set("enabled", String.valueOf(enabled));
		config.set("autoUpdate", String.valueOf(autoUpdate));

		for (int i = 0; i < repositoryUrls.size(); i++) {
			config.set("repositories." + i + ".url", repositoryUrls.get(i));
		}

		for (int i = 0; i < dependencies.size(); i++) {
			ExtensionManifest.DependencySpec dep = dependencies.get(i);
			config.set("dependencies." + i + ".groupId", dep.getGroupId());
			config.set("dependencies." + i + ".artifactId", dep.getArtifactId());
			config.set("dependencies." + i + ".version", dep.getVersion());
			for (int j = 0; j < dep.getRepositoryUrls().size(); j++) {
				config.set("dependencies." + i + ".repositories." + j + ".url", dep.getRepositoryUrls().get(j));
			}
		}

		for (int i = 0; i < requiredExtensionUrls.size(); i++) {
			config.set("requiredExtensions." + i + ".url", requiredExtensionUrls.get(i));
		}

		config.save();
	}

	/**
	 * Deserializes an {@link ExtensionRecord} from a previously saved {@link YmlConfig}.
	 *
	 * @param id
	 *            the record ID (derived from the file name)
	 * @param config
	 *            a loaded config for the record file
	 * @return the deserialized record
	 */
	public static ExtensionRecord fromConfig(String id, YmlConfig config) {
		String manifestUrl = config.getString("manifestUrl", "");
		String name = config.getString("name", "Unknown");
		String description = config.getString("description", "");
		String pageUrl = config.getString("pageUrl", "");
		String minLauncherVersion = config.getString("minLauncherVersion", "0.0.0");
		String groupId = config.getString("groupId", "");
		String artifactId = config.getString("artifactId", "");
		String installedVersion = config.getString("installedVersion", "");
		boolean enabled = "true".equalsIgnoreCase(config.getString("enabled", "true"));
		boolean autoUpdate = "true".equalsIgnoreCase(config.getString("autoUpdate", "true"));

		List<String> repositoryUrls = new ArrayList<>();
		for (int i = 0;; i++) {
			String url = config.getString("repositories." + i + ".url", "");
			if (url.isEmpty()) break;
			repositoryUrls.add(url);
		}

		List<ExtensionManifest.DependencySpec> dependencies = new ArrayList<>();
		for (int i = 0;; i++) {
			String depGroupId = config.getString("dependencies." + i + ".groupId", "");
			if (depGroupId.isEmpty()) break;
			String depArtifactId = config.getString("dependencies." + i + ".artifactId", "");
			String depVersion = config.getString("dependencies." + i + ".version", "");
			List<String> depRepos = new ArrayList<>();
			for (int j = 0;; j++) {
				String repoUrl = config.getString("dependencies." + i + ".repositories." + j + ".url", "");
				if (repoUrl.isEmpty()) break;
				depRepos.add(repoUrl);
			}
			dependencies.add(new ExtensionManifest.DependencySpec(depGroupId, depArtifactId, depVersion, depRepos));
		}

		List<String> requiredExtensionUrls = new ArrayList<>();
		for (int i = 0;; i++) {
			String reqUrl = config.getString("requiredExtensions." + i + ".url", "");
			if (reqUrl.isEmpty()) break;
			requiredExtensionUrls.add(reqUrl);
		}

		return new ExtensionRecord(id, manifestUrl, name, description, pageUrl, minLauncherVersion, groupId, artifactId,
				installedVersion, repositoryUrls, dependencies, requiredExtensionUrls, enabled, autoUpdate);
	}

	/**
	 * Creates a new {@link ExtensionRecord} from a freshly fetched {@link ExtensionManifest}. The manifest URL is read
	 * from {@link ExtensionManifest#getManifestUrl()}. The record is enabled and set to auto-update by default.
	 *
	 * @param id
	 *            unique UUID string for this record
	 * @param manifest
	 *            the parsed manifest (must have {@link ExtensionManifest#getManifestUrl()} set)
	 * @return a new record ready to be saved
	 */
	public static ExtensionRecord fromManifest(String id, ExtensionManifest manifest) {
		List<String> requiredExtensionUrls = new ArrayList<>();
		for (ExtensionManifest.RequiredExtensionSpec req : manifest.getRequiredExtensions()) {
			requiredExtensionUrls.add(req.getUrl());
		}

		return new ExtensionRecord(id, manifest.getManifestUrl(), manifest.getName(), manifest.getDescription(),
				manifest.getPageUrl(), manifest.getMinLauncherVersion(), manifest.getGroupId(),
				manifest.getArtifactId(), manifest.getVersion(), new ArrayList<>(manifest.getRepositoryUrls()),
				new ArrayList<>(manifest.getDependencies()), requiredExtensionUrls, true, true);
	}

}
