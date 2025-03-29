package su.softcom.cldt.testing.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageTab extends AbstractLaunchConfigurationTab {
	private static final Logger LOGGER = Logger.getLogger(CoverageTab.class.getName());
	private CheckboxTableViewer tableViewer;
	public static List<String> analysisScope = new ArrayList<>();
	public static String iproject = getCurrentProject();

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));

		Label fileLabel = new Label(composite, SWT.NONE);
		fileLabel.setText(Messages.CoverageTab_0);
		fileLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1));

		tableViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
		tableViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		tableViewer.addCheckStateListener(event -> updateLaunchConfigurationDialog());

		String currentProjectName = getCurrentProject();
		iproject = currentProjectName;

		if (currentProjectName != null) {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(currentProjectName);
			if (project != null) {
				populateFileList(project);
			} else {
				LOGGER.log(Level.SEVERE, "Project not found: {0}", currentProjectName);
			}
		} else {
			LOGGER.log(Level.WARNING, "No active project selected.");
		}

		setControl(composite);
	}

	public static String getCurrentProject() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			ISelection selection = window.getSelectionService().getSelection();
			if (selection instanceof IStructuredSelection) {
				Object element = ((IStructuredSelection) selection).getFirstElement();
				if (element instanceof IAdaptable) {
					return ((IAdaptable) element).getAdapter(IProject.class).getName();
				}
			}
		}
		return null;
	}

	private void populateFileList(IProject project) {
		try {
			List<String> files = new ArrayList<>();
			collectFiles(project, files);
			for (String file : files) {
				tableViewer.add(CoverageUtils.removeFirstSegment(file, 1));
			}
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error populating file list", e);
		}
	}

	private static final Set<String> EXCLUDED_FILES_AND_FOLDERS;

	static {
		EXCLUDED_FILES_AND_FOLDERS = new HashSet<>(
				Arrays.asList("build", "tests", ".project", ".settings", "CMakeLists.txt", "README.md"));
	}

	private void collectFiles(IResource resource, List<String> files) throws CoreException {
		if (EXCLUDED_FILES_AND_FOLDERS.contains(resource.getName())) {
			return;
		}
		if (resource instanceof IFile) {
			files.add(resource.getFullPath().toString());
		} else if (resource instanceof IProject) {
			for (IResource member : ((IProject) resource).members()) {
				collectFiles(member, files);
			}
		} else if (resource instanceof IFolder) {
			for (IResource member : ((IFolder) resource).members()) {
				collectFiles(member, files);
			}
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute("analysisScope", new ArrayList<>());
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			List<String> savedScope = configuration.getAttribute("analysisScope", new ArrayList<>());
			analysisScope.clear();
			analysisScope.addAll(savedScope);
			tableViewer.setCheckedElements(savedScope.toArray());
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error initializing from configuration", e);
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		analysisScope.clear();
		Object[] checkedElements = tableViewer.getCheckedElements();
		for (Object element : checkedElements) {
			analysisScope.add(element.toString());
		}
		configuration.setAttribute("analysisScope", analysisScope);
	}

	@Override
	public String getName() {
		return Messages.CoverageTab_1;
	}
}