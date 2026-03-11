package net.classiclauncher.launcher.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class VersionType {

	private final String id;
	private final String name;

}
