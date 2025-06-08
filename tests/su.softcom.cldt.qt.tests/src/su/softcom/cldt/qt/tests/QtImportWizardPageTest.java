package su.softcom.cldt.qt.tests;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Text;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import su.softcom.cldt.qt.ui.internal.wizards.QtImportWizardPage;

@RunWith(MockitoJUnitRunner.class)
public class QtImportWizardPageTest {
    private QtImportWizardPage page;
    private Text rootDirText;
    private Text qmakeProjectNameText;
    private Text projectNameText;
    private Text buildDirText;
    private Text qmakeBuildDirectoryText;
    private Text qmakeRootDirectoryText;
	private File rootFile;

    private static final String qtImportPageErrorDirectoryDoesNotExist = "Error: provided directory does not exist";
    private static final String qtImportPageErrorProjectNameBlank = "Error: project name must not be blank";
    private static final String qtImportPageErrorProjectExists = "Error: project with provided name already exists";

    @Before
    public void setUp() throws Exception {
        page = new QtImportWizardPage("");

        rootDirText = mock(Text.class);
        rootFile = mock(File.class);
        qmakeProjectNameText = mock(Text.class);
        projectNameText = mock(Text.class);
        buildDirText = mock(Text.class);
        qmakeBuildDirectoryText = mock(Text.class);
        qmakeRootDirectoryText = mock(Text.class);

        setPrivateField(page, "rootDirText", rootDirText);
        setPrivateField(page, "qmakeProjectNameText", qmakeProjectNameText);
        setPrivateField(page, "projectNameText", projectNameText);
        setPrivateField(page, "buildDirText", buildDirText);
        setPrivateField(page, "qmakeBuildDirectoryText", qmakeBuildDirectoryText);
        setPrivateField(page, "qmakeRootDirectoryText", qmakeRootDirectoryText);
    }

    @After
    public void tearDown() {
        page = null;
    }

    @Test
    public void testValidateRootDirectory_InvalidDirectory() throws Exception {
        String invalidPath = "/invalid/path";
        File mockedFile = mock(File.class);
        setPrivateField(page, "rootFile", mockedFile);
        when(mockedFile.exists()).thenReturn(false);

        boolean result = invokePrivateBooleanMethod(page, "validateRootDirectory");
        assertFalse(result, "Validation should fail for invalid directory");
        assertEquals(qtImportPageErrorDirectoryDoesNotExist, page.getErrorMessage());
        assertFalse(page.isPageComplete());
    }

    @Test
    public void testValidateRootDirectory_ValidDirectory() throws Exception {
        String validPath = "/valid/path";
        File mockedFile = mock(File.class);
        File cmakeFile = mock(File.class);
        File proFile = mock(File.class);
        setPrivateField(page, "rootFile", mockedFile);
        setPrivateField(page, "rootFile", cmakeFile);
        setPrivateField(page, "qmakeRootFile", proFile);
        when(cmakeFile.exists()).thenReturn(true);
        IWorkspace workspace = mock(IWorkspace.class);
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);

        try (MockedStatic<ResourcesPlugin> mockedStatic = mockStatic(ResourcesPlugin.class)) {
            mockedStatic.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            boolean result = invokePrivateBooleanMethod(page, "validateRootDirectory");
            assertTrue(result, "Validation should pass for valid directory");
            assertNull(page.getErrorMessage());
        }
    }

    @Test
    public void testValidateQmakeProjectName_Blank() throws Exception {
        when(qmakeProjectNameText.getText()).thenReturn("");

        boolean result = invokePrivateBooleanMethod(page, "validateQmakeProjectName");
        assertFalse(result, "Validation should fail for blank QMake project name");
        assertEquals(qtImportPageErrorProjectNameBlank, page.getErrorMessage());
        assertFalse(page.isPageComplete());
    }

    @Test
    public void testValidateProjectName_ExistingProject() throws Exception {
        String existingName = "ExistingProject";
        when(projectNameText.getText()).thenReturn(existingName);
        IWorkspace workspace = mock(IWorkspace.class);
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        IProject project = mock(IProject.class);
        when(workspace.getRoot()).thenReturn(root);
        when(root.getProject(existingName)).thenReturn(project);
        when(project.exists()).thenReturn(true);

        try (MockedStatic<ResourcesPlugin> mockedStatic = mockStatic(ResourcesPlugin.class)) {
            mockedStatic.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            boolean result = invokePrivateBooleanMethod(page, "validateProjectName");
            assertFalse(result, "Validation should fail for existing project name");
            assertEquals(qtImportPageErrorProjectExists, page.getErrorMessage());
            assertFalse(page.isPageComplete());
        }
    }

    @Test
    public void testValidateCMakeLists_Exists() throws Exception {
        String cmakeListsPath = "/path/to";

        try (MockedConstruction<File> mockedFile = mockConstruction(File.class, (mock, context) -> {
            if (context.arguments().contains("CMakeLists.txt")) {
                when(mock.exists()).thenReturn(true);
            }
        })) {
            boolean result = invokePrivateBooleanMethod(page, "validateCMakeLists");
            assertTrue(result, "Validation should pass for existing CMakeLists.txt");
            assertNull(page.getErrorMessage());
        }
    }

    @Test
    public void debugFields() {
        Field[] fields = QtImportWizardPage.class.getDeclaredFields();
        for (Field field : fields) {
            System.out.println("Field: " + field.getName());
        }
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("Target object is null");
        }
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private boolean invokePrivateBooleanMethod(Object target, String methodName, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        Object result = method.invoke(target, args);
        if (result == null) {
            throw new IllegalStateException("Method " + methodName + " returned null; expected boolean");
        }
        return (Boolean) result;
    }

    private Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}