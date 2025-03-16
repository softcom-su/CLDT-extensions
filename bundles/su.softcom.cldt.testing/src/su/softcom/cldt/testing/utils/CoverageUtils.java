package su.softcom.cldt.testing.utils;

public class CoverageUtils {
	public static String removeFirstSegment(String path, int number) {
		if (path == null || path.isEmpty()) {
			throw new IllegalArgumentException("Path cannot be null or empty");
		}
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		for (int i = 0; i < number; i++) {
			int firstSlashIndex = path.indexOf('/');
			if (firstSlashIndex != -1) {
				path = path.substring(firstSlashIndex + 1);
			}
		}
		return path;
	}
}