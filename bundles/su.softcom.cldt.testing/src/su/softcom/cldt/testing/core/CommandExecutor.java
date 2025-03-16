package su.softcom.cldt.testing.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class CommandExecutor {

	public void executeCommand(String command) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec(new String[] { "bash", "-c", command });
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String error = errorReader.lines().collect(Collectors.joining("\n"));
				throw new IOException("Command failed with error: " + error);
			}
		}
	}
}