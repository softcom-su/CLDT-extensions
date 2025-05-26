package su.softcom.cldt.testing.core;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class DemangledNameUtils {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final ILog LOGGER = Platform.getLog(DemangledNameUtils.class);
	private static final String WARNING_NO_COMMON_NAME = "No common name found for %s..., using %s";

	public static String extractCleanFunctionName(String demangledName) {
		if (demangledName == null || demangledName.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Empty or null demangled name"));
			return "";
		}

		if (demangledName.contains("{lambda") || demangledName.equals("lambda")) {
			return "lambda";
		}

		int parenPos = demangledName.indexOf('(');
		String cleanName = parenPos != -1 ? demangledName.substring(0, parenPos).trim() : demangledName;

		int lastColonPos = cleanName.lastIndexOf("::");
		String funcName;
		String className = null;
		if (lastColonPos != -1) {
			funcName = cleanName.substring(lastColonPos + 2);
			className = cleanName.substring(0, lastColonPos);
			int prevColonPos = className.lastIndexOf("::");
			className = prevColonPos != -1 ? className.substring(prevColonPos + 2) : className;
		} else {
			funcName = cleanName;
		}

		if (funcName.startsWith("~") && className != null) {
			return "~" + className;
		}
		if (funcName.startsWith("operator")) {
			return funcName.replaceAll("\\s+", "");
		}

		StringBuilder templateFreeName = new StringBuilder();
		int depth = 0;
		for (char c : funcName.toCharArray()) {
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (depth == 0) {
				templateFreeName.append(c);
			}
		}

		String result = templateFreeName.toString().trim();
		if (className != null && result.equals(className)) {
			return className;
		}

		return result.isEmpty() ? funcName : result;
	}

	public static String findCommonFunctionName(List<String> functions) {
		if (functions == null || functions.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Empty function list"));
			return "";
		}

		Set<String> cleanNames = new HashSet<>();
		for (String func : functions) {
			String cleanName = extractCleanFunctionName(func);
			if (!cleanName.isEmpty()) {
				cleanNames.add(cleanName);
			}
		}

		if (cleanNames.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_NO_COMMON_NAME,
					functions.get(0).substring(0, Math.min(functions.get(0).length(), 50)), "")));
			return "";
		}

		for (String cleanName : cleanNames) {
			boolean allContain = true;
			for (String func : functions) {
				String funcWithoutParams = func.indexOf('(') != -1 ? func.substring(0, func.indexOf('(')) : func;
				if (!funcWithoutParams.contains(cleanName)) {
					allContain = false;
					break;
				}
			}
			if (allContain) {
				return cleanName;
			}
		}

		String firstCleanName = extractCleanFunctionName(functions.get(0));
		LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_NO_COMMON_NAME,
				functions.get(0).substring(0, Math.min(functions.get(0).length(), 50)), firstCleanName)));
		return firstCleanName;
	}
}