package net.classiclauncher.launcher.uri;

import java.io.File;

/**
 * Immutable value object representing a parsed {@code classiclauncher://install} URI.
 *
 * <p>
 * Two variants:
 * <ul>
 * <li>{@link Type#REMOTE} — install from a remote manifest URL (the common case)</li>
 * <li>{@link Type#LOCAL} — install from local manifest + JAR files (dev/testing)</li>
 * </ul>
 *
 * <p>
 * Create instances via the static factories {@link #remote(String)} and {@link #local(File, File)}; never construct
 * directly.
 */
public final class ExtensionInstallRequest {

	public enum Type {
		REMOTE, LOCAL
	}

	private final Type type;
	private final String manifestUrl;
	private final File manifestFile;
	private final File jarFile;

	private ExtensionInstallRequest(Type type, String manifestUrl, File manifestFile, File jarFile) {
		this.type = type;
		this.manifestUrl = manifestUrl;
		this.manifestFile = manifestFile;
		this.jarFile = jarFile;
	}

	/**
	 * Creates a REMOTE install request from the given manifest URL.
	 *
	 * @param manifestUrl
	 *            the remote manifest YAML URL; must not be null or empty
	 * @throws IllegalArgumentException
	 *             if {@code manifestUrl} is null or blank
	 */
	public static ExtensionInstallRequest remote(String manifestUrl) {
		if (manifestUrl == null || manifestUrl.trim().isEmpty())
			throw new IllegalArgumentException("manifestUrl must not be null or empty");
		return new ExtensionInstallRequest(Type.REMOTE, manifestUrl.trim(), null, null);
	}

	/**
	 * Creates a LOCAL install request from local manifest and JAR files.
	 *
	 * @param manifest
	 *            the local manifest YAML file; must not be null
	 * @param jar
	 *            the local extension JAR file; must not be null
	 * @throws IllegalArgumentException
	 *             if either argument is null
	 */
	public static ExtensionInstallRequest local(File manifest, File jar) {
		if (manifest == null) throw new IllegalArgumentException("manifest file must not be null");
		if (jar == null) throw new IllegalArgumentException("jar file must not be null");
		return new ExtensionInstallRequest(Type.LOCAL, null, manifest, jar);
	}

	/**
	 * Returns the install type. Never null.
	 */
	public Type getType() {
		return type;
	}

	/**
	 * The remote manifest URL. Non-null only for {@link Type#REMOTE} requests.
	 */
	public String getManifestUrl() {
		return manifestUrl;
	}

	/**
	 * The local manifest YAML file. Non-null only for {@link Type#LOCAL} requests.
	 */
	public File getManifestFile() {
		return manifestFile;
	}

	/**
	 * The local extension JAR file. Non-null only for {@link Type#LOCAL} requests.
	 */
	public File getJarFile() {
		return jarFile;
	}

	/**
	 * Returns {@code true} if this is a remote-URL install request.
	 */
	public boolean isRemote() {
		return type == Type.REMOTE;
	}

	/**
	 * Returns {@code true} if this is a local-file install request.
	 */
	public boolean isLocal() {
		return type == Type.LOCAL;
	}

	@Override
	public String toString() {
		if (type == Type.REMOTE) return "ExtensionInstallRequest{REMOTE, url=" + manifestUrl + "}";
		return "ExtensionInstallRequest{LOCAL, manifest=" + manifestFile + ", jar=" + jarFile + "}";
	}

}
