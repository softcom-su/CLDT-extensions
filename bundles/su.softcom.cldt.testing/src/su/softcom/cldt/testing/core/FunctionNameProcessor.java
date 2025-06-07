package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

public class FunctionNameProcessor {
	private static final ILog LOGGER = Platform.getLog(FunctionNameProcessor.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final Map<String, String> DEMANGLE_CACHE = new HashMap<>();
	private static final Pattern MODIFIER_PATTERN = Pattern.compile(
			"\\s*(const|volatile|noexcept|__attribute__\\(.*\\)|__cdecl|__stdcall|__fastcall|inline|&|\\*|\\s*\\)\\s*)\\s*(?=$|\\))");
	private static final Pattern ANONYMOUS_NS_PATTERN = Pattern.compile("\\(anonymous\\s*(namespace)?\\)");
	private static final Pattern RETURN_TYPE_PATTERN = Pattern.compile("^.*?(?=\\s*\\w*(::\\w*\\s*\\<|::\\w*\\())");
	private static final Pattern LAMBDA_ID_PATTERN = Pattern.compile("lambda.*#(\\d+)");

	public static String demangle(String mangledName) {
		if (mangledName == null || mangledName.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Mangled name is null or empty"));
			return mangledName;
		}

		String cleanMangledName = mangledName.replaceAll("^[\\w.-]+:", "");
		if (DEMANGLE_CACHE.containsKey(cleanMangledName)) {
			return DEMANGLE_CACHE.get(cleanMangledName);
		}

		String demangled = executeDemangler(cleanMangledName);
		DEMANGLE_CACHE.put(cleanMangledName, demangled);
		return demangled;
	}

	public static String extractCleanFunctionName(String demangledName) {
		if (demangledName == null || demangledName.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Demangled name is null or empty"));
			return "";
		}

		if (isLambdaFunction(demangledName)) {
			return extractLambdaName(demangledName);
		}

		String cleanedSignature = cleanSignature(demangledName);
		String functionSignature = removeParameters(cleanedSignature);
		return parseFunctionName(functionSignature);
	}

	public static String findCommonFunctionName(List<String> functions) {
		if (functions == null || functions.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Function list is null or empty"));
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
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "No valid clean names extracted from function list"));
			return "";
		}

		for (String cleanName : cleanNames) {
			if (functions.stream().allMatch(func -> {
				String signature = func.indexOf('(') != -1 ? func.substring(0, func.indexOf('(')) : func;
				return signature.contains(cleanName);
			})) {
				return cleanName;
			}
		}

		return extractCleanFunctionName(functions.get(0));
	}

	public static boolean isLambdaFunction(String functionName) {
		if (functionName == null) {
			return false;
		}
		return functionName.contains("lambda") || functionName.contains("(anonymous")
				|| functionName.matches(".*operator\\s*\\(\\)\\s*.*");
	}

	private static String executeDemangler(String mangledName) {
		try {
			ProcessBuilder pb = new ProcessBuilder("llvm-cxxfilt", mangledName);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String demangled = String.join("", reader.lines().toList()).trim();
				if (process.waitFor(10, TimeUnit.SECONDS)) {
					if (process.exitValue() == 0 && !demangled.isEmpty() && !demangled.equals(mangledName)) {
						return demangled;
					} else {
						LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
								String.format("Failed to demangle name: %s, exit code: %d, output: %s", mangledName,
										process.exitValue(), demangled)));
					}
				} else {
					LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
							String.format("Demangling timed out for name: %s", mangledName)));
					process.destroy();
				}
			}
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("IO error while demangling name: %s, error: %s", mangledName, e.getMessage()), e));
		} catch (InterruptedException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("Interrupted while demangling name: %s, error: %s", mangledName, e.getMessage()), e));
			Thread.currentThread().interrupt();
		}
		return mangledName;
	}

	private static String cleanSignature(String demangledName) {
		String cleaned = MODIFIER_PATTERN.matcher(demangledName).replaceAll("");
		cleaned = ANONYMOUS_NS_PATTERN.matcher(cleaned).replaceAll("");
		Matcher returnTypeMatcher = RETURN_TYPE_PATTERN.matcher(cleaned);
		if (returnTypeMatcher.find()) {
			cleaned = cleaned.substring(returnTypeMatcher.end()).trim();
		}
		return cleaned;
	}

	private static String removeParameters(String signature) {
		int parenPos = signature.indexOf('(');
		return parenPos != -1 ? signature.substring(0, parenPos).trim() : signature;
	}

	private static String parseFunctionName(String signature) {
		int lastColonPos = signature.lastIndexOf("::");
		String funcName = lastColonPos != -1 ? signature.substring(lastColonPos + 2) : signature;
		String className = lastColonPos != -1 ? signature.substring(0, lastColonPos) : null;

		if (className != null) {
			int prevColonPos = className.lastIndexOf("::");
			className = prevColonPos != -1 ? className.substring(prevColonPos + 2) : className;
		}

		if (funcName.startsWith("~") && className != null) {
			return "~" + className;
		}

		if (funcName.startsWith("operator")) {
			return parseOperatorName(funcName);
		}

		String result = removeTemplateParameters(funcName);
		if (result.isEmpty()) {
			result = funcName;
		}

		if (className != null && (result.equals(className) || result.contains("<") || result.contains(","))) {
			if (funcName.equals(className)) {
				return className;
			}
			String[] parts = signature.split("::");
			if (parts.length > 1) {
				result = removeTemplateParameters(parts[parts.length - 1]);
			}
		}

		return result.replaceAll("[,\\s*&]+$", "").trim();
	}

	private static String parseOperatorName(String funcName) {
		StringBuilder operatorName = new StringBuilder("operator");
		int depth = 0;
		boolean inTemplate = false;
		boolean afterOperator = false;
		for (int i = "operator".length(); i < funcName.length(); i++) {
			char c = funcName.charAt(i);
			if (c == '<') {
				depth++;
				inTemplate = true;
			} else if (c == '>') {
				depth--;
				if (depth == 0)
					inTemplate = false;
			} else if (!inTemplate) {
				if (!afterOperator && Character.isWhitespace(c)) {
					operatorName.append(' ');
					afterOperator = true;
				} else if (afterOperator && Character.isWhitespace(c)) {
					continue;
				} else {
					operatorName.append(c);
				}
			}
		}
		return operatorName.toString().trim();
	}

	private static String removeTemplateParameters(String name) {
		StringBuilder result = new StringBuilder();
		int depth = 0;
		boolean inTemplate = false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '<' || c == ',') {
				depth++;
				inTemplate = true;
			} else if (c == '>' || (c == ',' && depth == 1)) {
				depth--;
				if (depth == 0)
					inTemplate = false;
			} else if (!inTemplate) {
				result.append(c);
			}
		}
		return result.toString().trim();
	}

	private static String extractLambdaName(String demangledName) {
		Matcher lambdaMatcher = LAMBDA_ID_PATTERN.matcher(demangledName);
		return lambdaMatcher.find() ? "lambda_" + lambdaMatcher.group(1) : "lambda";
	}
}