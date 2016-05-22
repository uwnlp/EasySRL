package edu.uw.easysrl.util;

import java.lang.reflect.Field;

public class LibraryUtil {
	private LibraryUtil() {
	}

	public static void setLibraryPath(String libraryPath) {
		System.setProperty("java.library.path", libraryPath);
		try {
			final Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
