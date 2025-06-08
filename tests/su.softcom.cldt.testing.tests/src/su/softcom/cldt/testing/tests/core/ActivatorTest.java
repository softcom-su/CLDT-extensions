package su.softcom.cldt.testing.tests.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import su.softcom.cldt.testing.core.Activator;
import su.softcom.cldt.testing.core.CoveragePreferenceSettings;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ActivatorTest {

	private static final String PLUGIN_SYMBOLIC_NAME = "su.softcom.cldt.testing";

	private Activator activator;
	@Mock
	private BundleContext bundleContext;
	@Mock
	private Bundle bundle;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		activator = new Activator();
		when(bundle.getSymbolicName()).thenReturn(PLUGIN_SYMBOLIC_NAME);
		when(bundleContext.getBundle()).thenReturn(bundle);

		Field pluginField = Activator.class.getDeclaredField("plugin");
		pluginField.setAccessible(true);
		pluginField.set(null, null);
	}

	@Test
	void testStartSetsPluginAndInitializesDefaults() throws Exception {
		try (MockedStatic<CoveragePreferenceSettings> mockedSettings = mockStatic(CoveragePreferenceSettings.class)) {
			activator.start(bundleContext);

			assertSame(activator, Activator.getDefault());
			mockedSettings.verify(() -> CoveragePreferenceSettings.initializeDefaults());
			verify(bundleContext).getBundle();
		}
	}

	@Test
	void testStartWhenThrowsException() throws Exception {
		doThrow(new RuntimeException("Test exception")).when(bundleContext).getBundle();
		assertThrows(Exception.class, () -> activator.start(bundleContext));
		assertNull(Activator.getDefault(), "Plugin should not be set when start throws an exception");
	}

	@Test
	void testStopClearsPlugin() throws Exception {
		activator.start(bundleContext);
		activator.stop(bundleContext);

		assertNull(Activator.getDefault());
		verify(bundleContext).getBundle();
	}

	@Test
	void testStopWhenThrowsException() throws Exception {
		Activator spyActivator = spy(activator);
		spyActivator.start(bundleContext);
		doThrow(new RuntimeException("Test exception")).when(spyActivator).stop(bundleContext);

		assertThrows(Exception.class, () -> spyActivator.stop(bundleContext));
		assertSame(spyActivator, Activator.getDefault());
	}

	@Test
	void testGetDefaultBeforeStart() {
		assertNull(Activator.getDefault());
	}

	@Test
	void testGetPreferenceStore() throws Exception {
		activator.start(bundleContext);
		assertNotNull(activator.getPreferenceStore());
	}
}