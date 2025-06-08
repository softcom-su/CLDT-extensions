package su.softcom.cldt.testing.tests.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import su.softcom.cldt.testing.core.CommandExecutor;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CommandExecutorTest {

	private static final List<String> TEST_COMMAND = Arrays.asList("echo", "test");
	private static final String OUTPUT_FILE_NAME = "output.txt";
	private static final String ENV_KEY = "KEY";
	private static final String ENV_VALUE = "VALUE";

	private CommandExecutor executor;
	private ProcessBuilder processBuilder;
	private Process process;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		executor = new CommandExecutor();
		processBuilder = mock(ProcessBuilder.class);
		process = mock(Process.class);
	}

	@Test
	void testExecuteCommandWithOutputFile() throws Exception {
		Path tempDir = Files.createTempDirectory("test");
		String outputFile = tempDir.resolve(OUTPUT_FILE_NAME).toString();

		try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (mock, context) -> {
			when(mock.start()).thenReturn(process);
			when(mock.redirectOutput(new File(outputFile))).thenReturn(mock);
		})) {
			when(process.waitFor()).thenReturn(0);
			executor.executeCommand(TEST_COMMAND, null, outputFile);

			ProcessBuilder constructed = mocked.constructed().get(0);
			verify(constructed).redirectOutput(new File(outputFile));
			verify(constructed).start();
			verify(process).waitFor();
		}
	}

	@Test
	void testExecuteCommandWithOutputCapture() throws Exception {
		StringBuilder output = new StringBuilder();
		InputStream inputStream = new ByteArrayInputStream("test output\n".getBytes());
		InputStream errorStream = new ByteArrayInputStream("error output\n".getBytes());

		try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (mock, context) -> {
			when(mock.start()).thenReturn(process);
		})) {
			when(process.getInputStream()).thenReturn(inputStream);
			when(process.getErrorStream()).thenReturn(errorStream);
			when(process.waitFor()).thenReturn(1);

			int exitCode = executor.executeCommand(TEST_COMMAND, null, null, output);

			assertEquals(1, exitCode);
			assertEquals("test output\nERROR: error output\n", output.toString());

			ProcessBuilder constructed = mocked.constructed().get(0);
			verify(constructed).start();
			verify(process).getInputStream();
			verify(process).getErrorStream();
			verify(process).waitFor();
		}
	}

	@Test
	void testExecuteCommandWithEnvironment() throws Exception {
		Map<String, String> env = new HashMap<>();
		env.put(ENV_KEY, ENV_VALUE);
		Map<String, String> pbEnv = new HashMap<>();

		try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (mock, context) -> {
			when(mock.environment()).thenReturn(pbEnv);
			when(mock.start()).thenReturn(process);
		})) {
			when(process.waitFor()).thenReturn(0);
			executor.executeCommand(TEST_COMMAND, env, null, null);

			assertEquals(ENV_VALUE, pbEnv.get(ENV_KEY));
			ProcessBuilder constructed = mocked.constructed().get(0);
			verify(constructed).environment();
			verify(constructed).start();
			verify(process).waitFor();
		}
	}

	@Test
	void testExecuteCommandThrowsIOException() throws Exception {
		try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (mock, context) -> {
			when(mock.start()).thenThrow(new IOException("Process failed"));
		})) {
			assertThrows(IOException.class, () -> executor.executeCommand(TEST_COMMAND, null, null, null));

			ProcessBuilder constructed = mocked.constructed().get(0);
			verify(constructed).start();
		}
	}

	@Test
	void testConfigureEnvironment() throws Exception {
		Map<String, String> env = new HashMap<>();
		env.put(ENV_KEY, ENV_VALUE);
		Map<String, String> pbEnv = new HashMap<>();
		when(processBuilder.environment()).thenReturn(pbEnv);

		Method configureEnvironment = CommandExecutor.class.getDeclaredMethod("configureEnvironment",
				ProcessBuilder.class, Map.class);
		configureEnvironment.setAccessible(true);
		configureEnvironment.invoke(executor, processBuilder, env);

		assertEquals(ENV_VALUE, pbEnv.get(ENV_KEY));
		verify(processBuilder).environment();
	}

	@Test
	void testConfigureEnvironmentWithNull() throws Exception {
		Map<String, String> pbEnv = new HashMap<>();
		when(processBuilder.environment()).thenReturn(pbEnv);

		Method configureEnvironment = CommandExecutor.class.getDeclaredMethod("configureEnvironment",
				ProcessBuilder.class, Map.class);
		configureEnvironment.setAccessible(true);
		configureEnvironment.invoke(executor, processBuilder, null);

		assertTrue(pbEnv.isEmpty());
		verify(processBuilder, never()).environment();
	}

	@Test
	void testConfigureOutput() throws Exception {
		Path tempDir = Files.createTempDirectory("test");
		String outputFile = tempDir.resolve(OUTPUT_FILE_NAME).toString();

		Method configureOutput = CommandExecutor.class.getDeclaredMethod("configureOutput", ProcessBuilder.class,
				String.class);
		configureOutput.setAccessible(true);
		configureOutput.invoke(executor, processBuilder, outputFile);

		verify(processBuilder).redirectOutput(new File(outputFile));
	}

	@Test
	void testConfigureOutputWithNull() throws Exception {
		Method configureOutput = CommandExecutor.class.getDeclaredMethod("configureOutput", ProcessBuilder.class,
				String.class);
		configureOutput.setAccessible(true);
		configureOutput.invoke(executor, processBuilder, null);

		verify(processBuilder, never()).redirectOutput(any(File.class));
	}

	@Test
	void testCaptureOutput() throws Exception {
		StringBuilder output = new StringBuilder();
		InputStream inputStream = new ByteArrayInputStream("test output\n".getBytes());
		InputStream errorStream = new ByteArrayInputStream("error output\n".getBytes());
		when(process.getInputStream()).thenReturn(inputStream);
		when(process.getErrorStream()).thenReturn(errorStream);

		Method captureOutput = CommandExecutor.class.getDeclaredMethod("captureOutput", Process.class,
				StringBuilder.class);
		captureOutput.setAccessible(true);
		captureOutput.invoke(executor, process, output);

		assertEquals("test output\nERROR: error output\n", output.toString());
		verify(process).getInputStream();
		verify(process).getErrorStream();
	}

	@Test
	void testCaptureOutputWithNull() throws Exception {
		Method captureOutput = CommandExecutor.class.getDeclaredMethod("captureOutput", Process.class,
				StringBuilder.class);
		captureOutput.setAccessible(true);
		captureOutput.invoke(executor, process, null);

		verify(process, never()).getInputStream();
		verify(process, never()).getErrorStream();
	}

	@Test
	void testAppendLines() throws Exception {
		StringBuilder output = new StringBuilder();
		BufferedReader reader = new BufferedReader(new StringReader("line1\nline2\n"));

		Method appendLines = CommandExecutor.class.getDeclaredMethod("appendLines", BufferedReader.class,
				StringBuilder.class, String.class);
		appendLines.setAccessible(true);
		appendLines.invoke(executor, reader, output, "TEST: ");

		assertEquals("TEST: line1\nTEST: line2\n", output.toString());
	}
}