package su.softcom.cldt.testing.ui;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;
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
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.core.cmake.Target;
import su.softcom.cldt.testing.utils.CoverageUtils;
import su.softcom.cldt.ui.dialogs.ProjectSelectionDialog;

public class CoverageTab extends AbstractLaunchConfigurationTab {
	private static final Logger LOGGER = Logger.getLogger(CoverageTab.class.getName());
	private static final String PROJECT_NAME_KEY = "projectName";
	private static final String TARGET_NAME_KEY = "targetName";
	private static final String ANALYSIS_SCOPE_KEY = "analysisScope";
	private static final Set<String> EXCLUDED_FILES_AND_FOLDERS = new HashSet<>(
			Arrays.asList("build", "tests", ".project", ".settings", "CMakeLists.txt", "README.md"));
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("cpp", "h", "c", "hpp");

	private Text projectText;
	private ComboViewer targetComboViewer;
	private Combo targetCombo;
	private CheckboxTableViewer tableViewer;

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		createProjectField(composite);
		createTargetField(composite);
		createFileSelectionTable(composite);
		setControl(composite);
	}

	private void createProjectField(Composite parent) {
		Group projectGroup = createGroup(parent, Messages.CoverageTab_6, 2);
		projectText = createTextField(projectGroup);
		projectText.addModifyListener(event -> {
			updateLaunchConfigurationDialog();
			updateTargets();
		});
		createProjectButton(projectGroup);
	}

	private void createTargetField(Composite parent) {
		Group targetGroup = createGroup(parent, Messages.CoverageTab_9, 1);
		targetComboViewer = new ComboViewer(targetGroup, SWT.READ_ONLY);
		targetCombo = targetComboViewer.getCombo();
		targetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		setupTargetComboViewer();
		targetCombo.addSelectionListener(widgetSelectedAdapter(e -> updateLaunchConfigurationDialog()));
	}

	private void createFileSelectionTable(Composite parent) {
		Group filesGroup = createGroup(parent, Messages.CoverageTab_10, 1);
		Label fileLabel = new Label(filesGroup, SWT.NONE);
		fileLabel.setText(Messages.CoverageTab_11);
		fileLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		tableViewer = CheckboxTableViewer.newCheckList(filesGroup, SWT.BORDER);
		tableViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());
		tableViewer.addCheckStateListener(event -> updateLaunchConfigurationDialog());
	}

	private Group createGroup(Composite parent, String text, int columns) {
		Group group = new Group(parent, SWT.NONE);
		group.setLayout(new GridLayout(columns, false));
		group.setLayoutData(new GridData(SWT.FILL, columns == 1 ? SWT.FILL : SWT.TOP, true, false));
		group.setText(text);
		return group;
	}

	private Text createTextField(Group parent) {
		Text text = new Text(parent, SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return text;
	}

	private void createProjectButton(Group parent) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(Messages.CoverageTab_7);
		button.addSelectionListener(widgetSelectedAdapter(e -> {
			ProjectSelectionDialog dialog = new ProjectSelectionDialog(getShell());
			dialog.setInput(ResourcesPlugin.getWorkspace().getRoot());
			dialog.setTitle(Messages.CoverageTab_8);
			if (dialog.open() == Window.OK) {
				IProject project = (IProject) dialog.getFirstResult();
				if (project != null) {
					projectText.setText(project.getName());
					updateTargets();
				}
			}
		}));
	}

	private void setupTargetComboViewer() {
		targetComboViewer.setContentProvider(
				(IStructuredContentProvider) input -> input instanceof List<?> list ? list.toArray() : new Object[0]);
		targetComboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return element instanceof Target target ? target.getName() : super.getText(element);
			}
		});
	}

	private void updateTargets() {
		String projectName = projectText.getText();
		if (projectName.isBlank()) {
			targetComboViewer.setInput(null);
			tableViewer.setInput(null);
			return;
		}
		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(projectName);
		if (cmakeProject != null) {
			targetComboViewer.setInput(cmakeProject.getTargets());
			populateFileList(cmakeProject.getProject());
		} else {
			targetComboViewer.setInput(null);
			tableViewer.setInput(null);
		}
	}

	private void populateFileList(IProject project) {
		try {
			List<String> files = new ArrayList<>();
			collectFiles(project, files);
			List<String> displayFiles = files.stream().map(file -> CoverageUtils.removeFirstSegment(file, 1)).toList();
			tableViewer.setInput(displayFiles);
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error populating file list", e);
		}
	}

	private void collectFiles(IResource resource, List<String> files) throws CoreException {
		if (resource.getName().startsWith(".") || EXCLUDED_FILES_AND_FOLDERS.contains(resource.getName())) {
			return;
		}
		if (resource instanceof IFile file) {
			String extension = file.getFileExtension();
			if (extension != null && ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
				files.add(file.getFullPath().toString());
			}
		} else if (resource instanceof IProject project) {
			for (IResource member : project.members()) {
				collectFiles(member, files);
			}
		} else if (resource instanceof IFolder folder) {
			for (IResource member : folder.members()) {
				collectFiles(member, files);
			}
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(PROJECT_NAME_KEY, getCurrentProject());
		configuration.setAttribute(TARGET_NAME_KEY, "");
		configuration.setAttribute(ANALYSIS_SCOPE_KEY, new ArrayList<>());
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String projectName = configuration.getAttribute(PROJECT_NAME_KEY, getCurrentProject());
			projectText.setText(projectName);
			updateTargets();
			String targetName = configuration.getAttribute(TARGET_NAME_KEY, "");
			targetCombo.setText(targetName);
			List<String> savedScope = configuration.getAttribute(ANALYSIS_SCOPE_KEY, new ArrayList<>());
			tableViewer.setCheckedElements(savedScope.toArray());
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error initializing from configuration", e);
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(PROJECT_NAME_KEY, projectText.getText());
		configuration.setAttribute(TARGET_NAME_KEY, targetCombo.getText());
		List<String> analysisScope = new ArrayList<>();
		for (Object element : tableViewer.getCheckedElements()) {
			analysisScope.add(element.toString());
		}
		configuration.setAttribute(ANALYSIS_SCOPE_KEY, analysisScope);
	}

	@Override
	public String getName() {
		return Messages.CoverageTab_25;
	}

	public static String getCurrentProject() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null)
			return "";
		ISelection selection = window.getSelectionService().getSelection();
		if (selection instanceof IStructuredSelection structuredSelection) {
			Object element = structuredSelection.getFirstElement();
			if (element instanceof IAdaptable adaptable) {
				IProject project = adaptable.getAdapter(IProject.class);
				if (project != null) {
					return project.getName();
				}
			}
		}
		return "";
	}

	@Override
	public boolean isValid(ILaunchConfiguration launchConfig) {
		String projectName = projectText.getText();
		String targetName = targetCombo.getText();
		if (projectName.isBlank()) {
			setErrorMessage(Messages.CoverageTab_27);
			return false;
		}
		if (targetName.isBlank()) {
			setErrorMessage(Messages.CoverageTab_28);
			return false;
		}
		setErrorMessage(null);
		return true;
	}
}