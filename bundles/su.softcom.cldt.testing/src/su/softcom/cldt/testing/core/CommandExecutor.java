package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.util.List;
import java.util.Map;

public class CommandExecutor {
	private static final String ERROR_PREFIX = "ERROR: ";

	public int executeCommand(List<String> command, Map<String, String> env, String outputFile, StringBuilder output)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		configureEnvironment(pb, env);
		configureOutput(pb, outputFile);
		Process process = pb.start();
		captureOutput(process, output);
		return process.waitFor();
	}

	public void executeCommand(List<String> command, Map<String, String> env, String outputFile)
			throws IOException, InterruptedException {
		executeCommand(command, env, outputFile, null);
	}

	private void configureEnvironment(ProcessBuilder pb, Map<String, String> env) {
		if (env != null) {
			pb.environment().putAll(env);
		}
	}

	private void configureOutput(ProcessBuilder pb, String outputFile) {
		if (outputFile != null) {
			pb.redirectOutput(new File(outputFile));
		}
	}

	private void captureOutput(Process process, StringBuilder output) throws IOException {
		if (output == null) {
			return;
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			appendLines(reader, output, "");
		}
		try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
			appendLines(errorReader, output, ERROR_PREFIX);
		}
	}

	private void appendLines(BufferedReader reader, StringBuilder output, String prefix) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			output.append(prefix).append(line).append("\n");
		}
	}
}