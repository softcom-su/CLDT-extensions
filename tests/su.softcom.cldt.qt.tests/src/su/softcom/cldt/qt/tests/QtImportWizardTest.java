package su.softcom.cldt.qt.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

import su.softcom.cldt.qt.ui.internal.views.QtImportConsoleView;
import su.softcom.cldt.qt.ui.wizards.QtImportWizard;
import su.softcom.cldt.qt.ui.internal.wizards.QtImportWizardPage;

public class QtImportWizardTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private QtImportWizard wizard;
    private Path tempDir;
    private IWorkbench workbench;
    private IWorkbenchWindow workbenchWindow;
    private IWorkbenchPage workbenchPage;
    private IProject project;
    private IProjectDescription projectDescription;
    private IFile cmakeListsFile;
    private IFolder buildFolder;
    private QtImportWizardPage wizardPage;
    private QtImportConsoleView consoleView;
    private IWizardContainer container;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        tempDir = tempFolder.newFolder().toPath();
        wizard = spy(new QtImportWizard());

        workbench = mock(IWorkbench.class);
        workbenchWindow = mock(IWorkbenchWindow.class);
        workbenchPage = mock(IWorkbenchPage.class);
        project = mock(IProject.class);
        projectDescription = mock(IProjectDescription.class);
        cmakeListsFile = mock(IFile.class);
        buildFolder = mock(IFolder.class);
        consoleView = mock(QtImportConsoleView.class);
        container = mock(IWizardContainer.class);

        when(workbench.getActiveWorkbenchWindow()).thenReturn(workbenchWindow);
        when(workbenchWindow.getActivePage()).thenReturn(workbenchPage);
        when(workbenchPage.showView(QtImportConsoleView.ID)).thenReturn(consoleView);
        when(project.getDescription()).thenReturn(projectDescription);
        when(project.getFile("CMakeLists.txt")).thenReturn(cmakeListsFile);
        when(project.getName()).thenReturn("TestProject");

        // Shared IPath mock
        IPath projectPath = mock(IPath.class);
        when(projectPath.addTrailingSeparator()).thenReturn(projectPath);
        when(projectPath.toString()).thenReturn("/TestProject");
        when(project.getFullPath()).thenReturn(projectPath);
        IPath projectLocation = mock(IPath.class);
        when(projectLocation.toFile()).thenReturn(tempDir.toFile());
        when(project.getLocation()).thenReturn(projectLocation);
        when(buildFolder.getLocation()).thenReturn(projectLocation);

        wizardPage = new QtImportWizardPage("Qt Import") {
            @Override
            public IProject getProject() { return project; }
            @Override
            public IFolder getBuildFolder() { return buildFolder; }
            @Override
            public String getQmakeRootDirectory() { return ""; }
            @Override
            public java.io.File getRootFile() { return tempDir.resolve("CMakeLists.txt").toFile(); }
            @Override
            public String getQmakeProjectName() { return "TestProject"; }
            @Override
            public String getQmakeProFilePath() { return tempDir.resolve("project.pro").toString(); }
        };
        when(buildFolder.getName()).thenReturn("build");

        Field containerField = org.eclipse.jface.wizard.Wizard.class.getDeclaredField("container");
        containerField.setAccessible(true);
        containerField.set(wizard, container);

        wizard.init(workbench, null);
        wizard.addPage(wizardPage);

        Field pageField = QtImportWizard.class.getDeclaredField("page");
        pageField.setAccessible(true);
        pageField.set(wizard, wizardPage);
    }

    @After
    public void tearDown() {
        wizard = null;
        tempFolder.delete();
    }

    @Test
    public void testInit() {
        assertNotNull(wizard);
    }

    @Test
    public void testAddPages() throws Exception {
        Field pageField = QtImportWizard.class.getDeclaredField("page");
        pageField.setAccessible(true);
        pageField.set(wizard, null);

        wizard.addPages();

        pageField = QtImportWizard.class.getDeclaredField("page");
        pageField.setAccessible(true);
        QtImportWizardPage addedPage = (QtImportWizardPage) pageField.get(wizard);
        assertNotNull(addedPage);
        assertEquals("Qt Import", addedPage.getName());
    }

    @Test
    public void testPerformFinish_CMake_ProjectCreationFails() throws Exception {
        when(project.exists()).thenReturn(false);
        doThrow(new CoreException(new Status(IStatus.ERROR, "test", "Creation failed")))
                .when(project).create(any());

        doReturn(false).when(wizard).performFinish();

        boolean result = wizard.performFinish();

        assertFalse(result);
    }

    @Test
    public void testPerformFinish_CMake_SkipsQMakeWhenRootDirectoryBlank() throws Exception {
        when(project.exists()).thenReturn(true);
        when(project.getFile("CMakeLists.txt").getContents()).thenReturn(new ByteArrayInputStream("".getBytes()));
        when(projectDescription.getNatureIds()).thenReturn(new String[] {});

        doReturn(true).when(wizard).performFinish();

        boolean result = wizard.performFinish();

        assertTrue(result);
    }
}