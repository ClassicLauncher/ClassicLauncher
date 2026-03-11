package net.classiclauncher.launcher.game;

import lombok.Builder;
import lombok.Getter;

/**
 * Describes one optional version-filter checkbox shown in the Profile Editor's version section.
 *
 * <p>
 * Versions whose {@code VersionType.id} matches {@link #typeId} are included only when the corresponding checkbox is
 * ticked.
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * VersionFilterOption.builder().typeId("snapshot").label("Enable experimental development versions (\"snapshots\")")
 * 		.build()
 * }</pre>
 */
@Getter
@Builder
public class VersionFilterOption {

	/**
	 * The {@code VersionType} identifier that this filter controls (e.g. {@code "snapshot"}).
	 */
	private final String typeId;

	/**
	 * The checkbox label shown in the Profile Editor UI.
	 */
	private final String label;

}
