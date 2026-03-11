package net.classiclauncher.launcher.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class Version {

	private final String version;
	private final VersionType type;
	private final long releaseTimestamp;

	/**
	 * URL of the version JSON. Null for locally-discovered versions without a remote source.
	 */
	private final String url;

}
