package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class CommandExecutor {

	public void executeCommand(List<String> command) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String error = errorReader.lines().collect(Collectors.joining("\n"));
				throw new IOException("Command failed with error: " + error);
			}
		}
	}

	public void executeCommandWithEnv(List<String> command, String envKey, String envValue)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.environment().put(envKey, envValue);
		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String error = errorReader.lines().collect(Collectors.joining("\n"));
				throw new IOException("Command failed with error: " + error);
			}
		}
	}

	public void executeCommandWithRedirect(List<String> command, String outputFile)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectOutput(new File(outputFile));
		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String error = errorReader.lines().collect(Collectors.joining("\n"));
				throw new IOException("Command failed with error: " + error);
			}
		}
	}
}