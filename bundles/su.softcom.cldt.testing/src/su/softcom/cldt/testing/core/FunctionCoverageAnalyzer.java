package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

record FunctionBoundary(int startLine, int endLine, boolean isLambda, int nestingLevel) {
}

record Block(int startLine, int endLine, int nestingLevel) {
}

public class FunctionCoverageAnalyzer {
	private static final ILog LOGGER = Platform.getLog(FunctionCoverageAnalyzer.class);
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";

	public static void processFunctionCoverage(ReportParser.FileContext context) {
		Map<Integer, String> lineToCommonName = new HashMap<>();
		List<String> demangledNames = new ArrayList<>();
		List<Integer> lcovStartLines = new ArrayList<>();
		for (Map.Entry<Integer, List<ReportParser.MangledDemangledPair>> entry : context.tempFunctionNames()
				.entrySet()) {
			int startLine = entry.getKey();
			List<String> demangledNamesForLine = entry.getValue().stream()
					.map(ReportParser.MangledDemangledPair::demangledName).collect(Collectors.toList());
			String commonName = FunctionNameProcessor.findCommonFunctionName(demangledNamesForLine);
			if (!commonName.isEmpty()) {
				lineToCommonName.put(startLine, commonName);
			}
			lcovStartLines.add(startLine);
			demangledNames.add(commonName.isEmpty() ? demangledNamesForLine.get(0) : commonName);
		}

		for (Map.Entry<Integer, String> entry : lineToCommonName.entrySet()) {
			int startLine = entry.getKey();
			String commonName = entry.getValue();
			List<String> mangledNames = context.tempFunctionNames().getOrDefault(startLine, List.of()).stream()
					.map(ReportParser.MangledDemangledPair::mangledName).collect(Collectors.toList());
			boolean isLambda = FunctionNameProcessor.isLambdaFunction(commonName);
			int signatureLine = findSignatureLine(context.currentFile(), startLine);
			ReportParser.FunctionCoverage func = ReportParser.FunctionCoverage.create(commonName, mangledNames, 0,
					startLine, signatureLine, isLambda);
			context.currentFunctionCoverage().add(func);
		}

		List<ReportParser.FunctionCoverage> annotationFunctions = new ArrayList<>();
		for (Map.Entry<Integer, List<ReportParser.MangledDemangledPair>> entry : context.tempFunctionNames()
				.entrySet()) {
			int startLine = entry.getKey();
			String commonName = lineToCommonName.getOrDefault(startLine, "");
			for (ReportParser.MangledDemangledPair pair : entry.getValue()) {
				int signatureLine = findSignatureLine(context.currentFile(), startLine);
				boolean isLambda = FunctionNameProcessor.isLambdaFunction(pair.demangledName());
				ReportParser.FunctionCoverage func = ReportParser.FunctionCoverage.create(
						commonName.isEmpty() ? pair.demangledName() : commonName, List.of(pair.mangledName()), 0,
						startLine, signatureLine, isLambda);
				annotationFunctions.add(func);
			}
		}

		List<ReportParser.FunctionCoverage> updatedFunctions = new ArrayList<>();
		for (ReportParser.FunctionCoverage func : context.currentFunctionCoverage()) {
			int maxExecutionCount = 0;
			for (String mangled : func.mangledNames()) {
				for (String line : context.fnExecutionLines()) {
					if (line.startsWith(ReportParser.FUNCTION_EXECUTION_PREFIX)) {
						try {
							String[] parts = line.substring(5).split(",");
							if (parts.length >= 2 && parts[1].equals(mangled)) {
								maxExecutionCount = Math.max(maxExecutionCount, Integer.parseInt(parts[0]));
							}
						} catch (Exception e) {
							LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
									String.format("Invalid function coverage format: %s", line), e));
						}
					}
				}
			}
			updatedFunctions.add(new ReportParser.FunctionCoverage(func.name(), func.mangledNames(), maxExecutionCount,
					func.startLine(), func.endLine(), func.signatureLine(), func.isLambda()));
		}
		context.currentFunctionCoverage().clear();
		context.currentFunctionCoverage().addAll(updatedFunctions);

		List<ReportParser.FunctionCoverage> updatedAnnotationFunctions = new ArrayList<>();
		for (ReportParser.FunctionCoverage func : annotationFunctions) {
			boolean updated = false;
			for (String mangled : func.mangledNames()) {
				for (String line : context.fnExecutionLines()) {
					if (line.startsWith(ReportParser.FUNCTION_EXECUTION_PREFIX)) {
						try {
							String[] parts = line.substring(5).split(",");
							if (parts.length >= 2 && parts[1].equals(mangled)) {
								updatedAnnotationFunctions.add(new ReportParser.FunctionCoverage(func.name(),
										func.mangledNames(), Integer.parseInt(parts[0]), func.startLine(),
										func.endLine(), func.signatureLine(), func.isLambda()));
								updated = true;
								break;
							}
						} catch (Exception e) {
							LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
									String.format("Invalid function coverage format: %s", line), e));
						}
					}
				}
				if (updated) {
					break;
				}
			}
			if (!updated) {
				updatedAnnotationFunctions.add(func);
			}
		}
		annotationFunctions.clear();
		annotationFunctions.addAll(updatedAnnotationFunctions);

		if (!context.currentFunctionCoverage().isEmpty()) {
			assignFunctionEndLines(context, lcovStartLines, demangledNames);
			distributeLineCoverage(context);
		} else {
			context.currentNonFunctionLineCoverage().addAll(context.tempLineCoverage());
		}

		context.annotationFunctionCoverage().addAll(annotationFunctions);
	}

	private static void assignFunctionEndLines(ReportParser.FileContext context, List<Integer> lcovStartLines,
			List<String> demangledNames) {
		List<FunctionBoundary> boundaries = parseFunctionBoundaries(context.currentFile(), lcovStartLines,
				demangledNames);

		List<ReportParser.FunctionCoverage> updatedFunctions = new ArrayList<>();
		for (ReportParser.FunctionCoverage func : context.currentFunctionCoverage()) {
			int endLine = func.endLine();
			for (FunctionBoundary boundary : boundaries) {
				if (boundary.startLine() == func.startLine()) {
					endLine = boundary.endLine();
					break;
				}
			}
			if (endLine < func.startLine()) {
				endLine = func.startLine();
			}
			updatedFunctions.add(new ReportParser.FunctionCoverage(func.name(), func.mangledNames(),
					func.executionCount(), func.startLine(), endLine, func.signatureLine(), func.isLambda()));
		}

		context.currentFunctionCoverage().clear();
		context.currentFunctionCoverage().addAll(updatedFunctions);

		context.currentFunctionCoverage().sort((f1, f2) -> Integer.compare(f1.startLine(), f2.startLine()));
		for (int i = 0; i < context.currentFunctionCoverage().size() - 1; i++) {
			ReportParser.FunctionCoverage current = context.currentFunctionCoverage().get(i);
			ReportParser.FunctionCoverage next = context.currentFunctionCoverage().get(i + 1);
			if (current.endLine() >= next.startLine() && !next.isLambda()) {
				ReportParser.FunctionCoverage updatedCurrent = new ReportParser.FunctionCoverage(current.name(),
						current.mangledNames(), current.executionCount(), current.startLine(), next.startLine() - 1,
						current.signatureLine(), current.isLambda());
				context.currentFunctionCoverage().set(i, updatedCurrent);
			}
		}
	}

	private static void distributeLineCoverage(ReportParser.FileContext context) {
		for (ReportParser.LineCoverage lineCov : context.tempLineCoverage()) {
			boolean belongsToFunction = context.currentFunctionCoverage().stream()
					.anyMatch(f -> lineCov.lineNumber() >= f.startLine() && lineCov.lineNumber() <= f.endLine());
			(belongsToFunction ? context.currentLineCoverage() : context.currentNonFunctionLineCoverage()).add(lineCov);
		}
	}

	public static List<FunctionBoundary> parseFunctionBoundaries(String filePath, List<Integer> lcovStartLines,
			List<String> demangledNames) {
		List<FunctionBoundary> boundaries = new ArrayList<>();
		List<Block> blocks = parseBlocks(filePath);

		for (int i = 0; i < lcovStartLines.size(); i++) {
			int startLine = lcovStartLines.get(i);
			String demangledName = i < demangledNames.size() ? demangledNames.get(i) : "";
			boolean isLambda = FunctionNameProcessor.isLambdaFunction(demangledName);

			Block matchingBlock = findMatchingBlock(blocks, startLine);
			boundaries.add(matchingBlock != null
					? new FunctionBoundary(startLine, matchingBlock.endLine(), isLambda, matchingBlock.nestingLevel())
					: new FunctionBoundary(startLine, startLine, isLambda, 0));
		}

		return boundaries;
	}

	private static List<Block> parseBlocks(String filePath) {
		List<Block> blocks = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			List<Integer> startLines = new ArrayList<>();
			List<Integer> nestingLevels = new ArrayList<>();
			int braceLevel = 0;
			boolean inMultiLineComment = false;
			boolean inStringLiteral = false;
			char stringLiteralChar = '\0';

			List<String> lines = reader.lines().collect(Collectors.toList());
			for (int currentLine = 0; currentLine < lines.size(); currentLine++) {
				String line = lines.get(currentLine).trim();
				if (line.startsWith("/*"))
					inMultiLineComment = true;
				if (line.contains("*/")) {
					inMultiLineComment = false;
					continue;
				}
				if (inMultiLineComment || line.startsWith("//"))
					continue;

				String fullLine = lines.get(currentLine);
				for (int i = 0; i < fullLine.length(); i++) {
					char c = fullLine.charAt(i);
					if (!inMultiLineComment && !inStringLiteral && (c == '"' || c == '\'')) {
						inStringLiteral = true;
						stringLiteralChar = c;
					} else if (inStringLiteral && c == stringLiteralChar
							&& (i == 0 || fullLine.charAt(i - 1) != '\\')) {
						inStringLiteral = false;
						stringLiteralChar = '\0';
					}
				}
				if (inStringLiteral)
					continue;

				for (char c : fullLine.toCharArray()) {
					if (!inMultiLineComment && !inStringLiteral) {
						if (c == '{') {
							startLines.add(currentLine + 1);
							nestingLevels.add(braceLevel++);
						} else if (c == '}') {
							if (!startLines.isEmpty()) {
								blocks.add(new Block(startLines.remove(startLines.size() - 1), currentLine + 1,
										nestingLevels.remove(nestingLevels.size() - 1)));
								braceLevel--;
							}
						}
					}
				}
			}

			while (!startLines.isEmpty()) {
				blocks.add(new Block(startLines.remove(startLines.size() - 1), lines.size(),
						nestingLevels.remove(nestingLevels.size() - 1)));
			}
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to parse file for function boundaries: %s", filePath), e));
		}
		return blocks;
	}

	private static Block findMatchingBlock(List<Block> blocks, int startLine) {
		return blocks.stream()
				.filter(block -> block.startLine() == startLine
						|| (block.startLine() <= startLine && block.endLine() >= startLine))
				.min((b1, b2) -> Integer.compare(startLine - b1.startLine(), startLine - b2.startLine())).orElse(null);
	}

	private static int findSignatureLine(String filePath, int bodyLine) {
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			int currentLine = 0;
			boolean inMultiLineComment = false;

			for (String line : reader.lines().toList()) {
				currentLine++;
				String trimmed = line.trim();
				if (trimmed.startsWith("/*"))
					inMultiLineComment = true;
				if (inMultiLineComment) {
					if (trimmed.contains("*/"))
						inMultiLineComment = false;
					continue;
				}
				if (trimmed.startsWith("//") || trimmed.startsWith("///"))
					continue;

				if (currentLine == bodyLine && trimmed.contains("{")) {
					return trimmed.contains(")") ? currentLine : currentLine - 1;
				}
				if (currentLine >= bodyLine && trimmed.contains(")")) {
					return currentLine;
				}
			}
		} catch (IOException e) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format("Failed to parse file for function boundaries: %s", filePath), e));
		}
		return bodyLine;
	}
}