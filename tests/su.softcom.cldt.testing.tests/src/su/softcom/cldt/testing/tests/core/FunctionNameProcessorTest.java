package su.softcom.cldt.testing.tests.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import su.softcom.cldt.testing.core.FunctionNameProcessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FunctionNameProcessorTest {

	@Mock
	private ILog logger;

	@Mock
	private ProcessBuilder processBuilder;

	@Mock
	private Process process;

	private AutoCloseable closeable;
	private MockedStatic<Platform> platformMock;

	@BeforeEach
	void setUp() throws Exception {
		closeable = MockitoAnnotations.openMocks(this);

		platformMock = mockStatic(Platform.class);
		platformMock.when(() -> Platform.getLog(FunctionNameProcessor.class)).thenReturn(logger);

		Field cacheField = FunctionNameProcessor.class.getDeclaredField("DEMANGLE_CACHE");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, String> cache = (Map<String, String>) cacheField.get(null);
		cache.clear();
	}

	@AfterEach
	void tearDown() throws Exception {
		closeable.close();
		platformMock.close();
	}

	@Test
	void testDemangle_NullInput() {
		String result = FunctionNameProcessor.demangle(null);

		assertNull(result);
	}

	@Test
	void testDemangle_EmptyInput() {
		String result = FunctionNameProcessor.demangle("");

		assertEquals("", result);
	}

	@Test
	void testDemangle_SuccessfulDemangling() throws Exception {
		String mangledName = "_Zmain";
		String demangledName = "main()";

		try (MockedConstruction<ProcessBuilder> processBuilderMock = mockConstruction(ProcessBuilder.class,
				(mock, context) -> {
					when(mock.redirectErrorStream(true)).thenReturn(mock);
					when(mock.start()).thenReturn(process);
				})) {
			when(process.getInputStream()).thenReturn(new ByteArrayInputStream(demangledName.getBytes()));
			when(process.waitFor(10, TimeUnit.SECONDS)).thenReturn(true);
			when(process.exitValue()).thenReturn(0);

			String result = FunctionNameProcessor.demangle(mangledName);

			assertEquals(demangledName, result);
			verify(logger, never()).log(any());
		}
	}

	@Test
	void testDemangle_CacheHit() throws Exception {
		String mangledName = "_Zmain";
		String demangledName = "main()";

		Field cacheField = FunctionNameProcessor.class.getDeclaredField("DEMANGLE_CACHE");
		cacheField.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, String> cache = (Map<String, String>) cacheField.get(null);
		cache.put(mangledName, demangledName);

		String result = FunctionNameProcessor.demangle(mangledName);

		assertEquals(demangledName, result);
		verify(logger, never()).log(any());
	}

	@Test
	void testDemangle_IOException() throws Exception {
		String mangledName = "_Zmain";

		try (MockedConstruction<ProcessBuilder> processBuilderMock = mockConstruction(ProcessBuilder.class,
				(mock, context) -> {
					when(mock.redirectErrorStream(true)).thenReturn(mock);
					when(mock.start()).thenThrow(new IOException("Failed to start process"));
				})) {
			String result = FunctionNameProcessor.demangle(mangledName);

			assertEquals(mangledName, result);
			ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
			verify(logger).log(statusCaptor.capture());
			assertEquals(Status.ERROR, statusCaptor.getValue().getSeverity());
			assertTrue(statusCaptor.getValue().getMessage().contains("IO error while demangling name"));
		}
	}

	@Test
	void testExtractCleanFunctionName_NullInput() {
		String result = FunctionNameProcessor.extractCleanFunctionName(null);

		assertEquals("", result);
	}

	@Test
	void testExtractCleanFunctionName_EmptyInput() {
		String result = FunctionNameProcessor.extractCleanFunctionName("");

		assertEquals("", result);
	}

	@Test
	void testExtractCleanFunctionName_LambdaFunction() {
		String demangledName = "lambda#123()";
		String result = FunctionNameProcessor.extractCleanFunctionName(demangledName);

		assertEquals("lambda_123", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testExtractCleanFunctionName_SimpleFunction() {
		String demangledName = "void ns::MyClass::myFunction(int)";
		String result = FunctionNameProcessor.extractCleanFunctionName(demangledName);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testExtractCleanFunctionName_Operator() {
		String demangledName = "MyClass::operator+(int)";
		String result = FunctionNameProcessor.extractCleanFunctionName(demangledName);

		assertEquals("operator+", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testExtractCleanFunctionName_Destructor() {
		String demangledName = "MyClass::~MyClass()";
		String result = FunctionNameProcessor.extractCleanFunctionName(demangledName);

		assertEquals("~MyClass", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testExtractCleanFunctionName_TemplateFunction() {
		String demangledName = "ns::MyClass::myFunction<int>(int)";
		String result = FunctionNameProcessor.extractCleanFunctionName(demangledName);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testFindCommonFunctionName_NullInput() {
		String result = FunctionNameProcessor.findCommonFunctionName(null);

		assertEquals("", result);
	}

	@Test
	void testFindCommonFunctionName_EmptyInput() {
		String result = FunctionNameProcessor.findCommonFunctionName(Collections.emptyList());

		assertEquals("", result);
	}

	@Test
	void testFindCommonFunctionName_SingleFunction() {
		List<String> functions = Arrays.asList("void ns::MyClass::myFunction(int)");
		String result = FunctionNameProcessor.findCommonFunctionName(functions);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testFindCommonFunctionName_CommonName() {
		List<String> functions = Arrays.asList("void ns::MyClass::myFunction(int)",
				"int ns::MyClass::myFunction(double)");
		String result = FunctionNameProcessor.findCommonFunctionName(functions);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testFindCommonFunctionName_NoCommonName() {
		List<String> functions = Arrays.asList("void ns::MyClass::myFunction(int)",
				"int ns::MyClass::otherFunction(double)");
		String result = FunctionNameProcessor.findCommonFunctionName(functions);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testIsLambdaFunction_NullInput() {
		boolean result = FunctionNameProcessor.isLambdaFunction(null);

		assertFalse(result);
		verify(logger, never()).log(any());
	}

	@Test
	void testIsLambdaFunction_LambdaName() {
		boolean result = FunctionNameProcessor.isLambdaFunction("lambda#123()");

		assertTrue(result);
		verify(logger, never()).log(any());
	}

	@Test
	void testIsLambdaFunction_AnonymousNamespace() {
		boolean result = FunctionNameProcessor.isLambdaFunction("(anonymous namespace)::func()");

		assertTrue(result);
		verify(logger, never()).log(any());
	}

	@Test
	void testIsLambdaFunction_OperatorCall() {
		boolean result = FunctionNameProcessor.isLambdaFunction("operator()()");

		assertTrue(result);
		verify(logger, never()).log(any());
	}

	@Test
	void testIsLambdaFunction_RegularFunction() {
		boolean result = FunctionNameProcessor.isLambdaFunction("myFunction()");

		assertFalse(result);
		verify(logger, never()).log(any());
	}

	@Test
	void testCleanSignature() throws Exception {
		Method cleanSignature = FunctionNameProcessor.class.getDeclaredMethod("cleanSignature", String.class);
		cleanSignature.setAccessible(true);

		String demangledName = "const volatile void ns::MyClass::myFunction(int) noexcept";
		String result = (String) cleanSignature.invoke(null, demangledName);

		assertEquals("MyClass::myFunction(int)", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testRemoveParameters() throws Exception {
		Method removeParameters = FunctionNameProcessor.class.getDeclaredMethod("removeParameters", String.class);
		removeParameters.setAccessible(true);

		String signature = "ns::MyClass::myFunction(int, double)";
		String result = (String) removeParameters.invoke(null, signature);

		assertEquals("ns::MyClass::myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testParseFunctionName_SimpleFunction() throws Exception {
		Method parseFunctionName = FunctionNameProcessor.class.getDeclaredMethod("parseFunctionName", String.class);
		parseFunctionName.setAccessible(true);

		String signature = "ns::MyClass::myFunction";
		String result = (String) parseFunctionName.invoke(null, signature);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testParseFunctionName_Destructor() throws Exception {
		Method parseFunctionName = FunctionNameProcessor.class.getDeclaredMethod("parseFunctionName", String.class);
		parseFunctionName.setAccessible(true);

		String signature = "ns::MyClass::~MyClass";
		String result = (String) parseFunctionName.invoke(null, signature);

		assertEquals("~MyClass", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testParseOperatorName() throws Exception {
		Method parseOperatorName = FunctionNameProcessor.class.getDeclaredMethod("parseOperatorName", String.class);
		parseOperatorName.setAccessible(true);

		String funcName = "operator+<T>";
		String result = (String) parseOperatorName.invoke(null, funcName);

		assertEquals("operator+", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testRemoveTemplateParameters() throws Exception {
		Method removeTemplateParameters = FunctionNameProcessor.class.getDeclaredMethod("removeTemplateParameters",
				String.class);
		removeTemplateParameters.setAccessible(true);

		String name = "myFunction<int, double>";
		String result = (String) removeTemplateParameters.invoke(null, name);

		assertEquals("myFunction", result);
		verify(logger, never()).log(any());
	}

	@Test
	void testExtractLambdaName() throws Exception {
		Method extractLambdaName = FunctionNameProcessor.class.getDeclaredMethod("extractLambdaName", String.class);
		extractLambdaName.setAccessible(true);

		String demangledName = "lambda#123()";
		String result = (String) extractLambdaName.invoke(null, demangledName);

		assertEquals("lambda_123", result);
		verify(logger, never()).log(any());
	}
}