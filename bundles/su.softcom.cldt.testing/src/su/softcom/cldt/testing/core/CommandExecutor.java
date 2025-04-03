package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.util.List;
import java.util.Map;

public class CommandExecutor {
	public int executeCommand(List<String> command, Map<String, String> env, String outputFile, StringBuilder output)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		if (env != null) {
			pb.environment().putAll(env);
		}
		if (outputFile != null) {
			pb.redirectOutput(new File(outputFile));
		}
		Process process = pb.start();
		if (output != null) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
			}
			try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = errorReader.readLine()) != null) {
					output.append("ERROR: ").append(line).append("\n");
				}
			}
		}
		return process.waitFor();
	}

	public void executeCommand(List<String> command, Map<String, String> env, String outputFile)
			throws IOException, InterruptedException {
		executeCommand(command, env, outputFile, null);
	}
}