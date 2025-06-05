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
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
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
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import su.softcom.cldt.core.CMakeCorePlugin;
import su.softcom.cldt.core.cmake.ICMakeProject;
import su.softcom.cldt.core.cmake.Target;
import su.softcom.cldt.testing.utils.CoverageUtils;
import su.softcom.cldt.ui.dialogs.ProjectSelectionDialog;

public class CoverageTab extends AbstractLaunchConfigurationTab {
	private static final Logger LOGGER = Logger.getLogger(CoverageTab.class.getName());
	private static final String PROJECT_NAME_KEY = "projectName"; //$NON-NLS-1$
	private static final String TARGET_NAME_KEY = "targetName"; //$NON-NLS-1$
	private static final String ANALYSIS_SCOPE_KEY = "analysisScope"; //$NON-NLS-1$
	private static final String SELECTED_FOLDERS_KEY = "selectedFolders"; //$NON-NLS-1$
	private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList("c", "h", "cpp", "hpp", "cc", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"cxx", "hh", "hxx", "C", "inl", "tcc", "c++", "ipp", "cu", "cppm", "ixx", "tpp", "ihh", "cuh")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$
	private static final Set<String> EXCLUDED_FOLDERS = new HashSet<>(
			Arrays.asList("build", "test", "tests", "unit_test", "test_suite")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	private Text projectText;
	private ComboViewer targetComboViewer;
	private Combo targetCombo;
	private CheckboxTreeViewer treeViewer;

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
		treeViewer = new CheckboxTreeViewer(filesGroup, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		Tree tree = treeViewer.getTree();
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.heightHint = 200;
		gridData.widthHint = 400;
		tree.setLayoutData(gridData);
		treeViewer.setContentProvider(new ITreeContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				return getChildren(inputElement);
			}

			@Override
			public Object[] getChildren(Object parentElement) {
				if (parentElement instanceof IProject project) {
					try {
						return Arrays.stream(project.members())
								.filter(member -> !EXCLUDED_FOLDERS.contains(member.getName()))
								.filter(member -> member instanceof IFolder
										|| (member instanceof IFile file && file.getFileExtension() != null
												&& ALLOWED_EXTENSIONS.contains(file.getFileExtension().toLowerCase())))
								.filter(member -> member instanceof IFile || hasValidFiles(member)).toArray();
					} catch (CoreException e) {
						LOGGER.log(Level.SEVERE, "Error getting project members", e); //$NON-NLS-1$
						return new Object[0];
					}
				} else if (parentElement instanceof IFolder folder) {
					try {
						return Arrays.stream(folder.members())
								.filter(member -> !EXCLUDED_FOLDERS.contains(member.getName()))
								.filter(member -> member instanceof IFolder
										|| (member instanceof IFile file && file.getFileExtension() != null
												&& ALLOWED_EXTENSIONS.contains(file.getFileExtension().toLowerCase())))
								.filter(member -> member instanceof IFile || hasValidFiles(member)).toArray();
					} catch (CoreException e) {
						LOGGER.log(Level.SEVERE, "Error getting folder members", e); //$NON-NLS-1$
						return new Object[0];
					}
				}
				return new Object[0];
			}

			@Override
			public Object getParent(Object element) {
				if (element instanceof IResource resource) {
					return resource.getParent();
				}
				return null;
			}

			@Override
			public boolean hasChildren(Object element) {
				if (element instanceof IFolder || element instanceof IProject) {
					return hasValidFiles(element);
				}
				return false;
			}

			private boolean hasValidFiles(Object element) {
				IResource[] members;
				try {
					members = element instanceof IProject project ? project.members() : ((IFolder) element).members();
				} catch (CoreException e) {
					LOGGER.log(Level.SEVERE, "Error checking valid files", e); //$NON-NLS-1$
					return false;
				}
				return Arrays.stream(members).filter(member -> !EXCLUDED_FOLDERS.contains(member.getName()))
						.anyMatch(member -> (member instanceof IFile file && file.getFileExtension() != null
								&& ALLOWED_EXTENSIONS.contains(file.getFileExtension().toLowerCase()))
								|| (member instanceof IFolder && hasValidFiles(member)));
			}
		});
		treeViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IResource resource) {
					return resource.getName();
				}
				return super.getText(element);
			}
		});
		treeViewer.setComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof IFolder && e2 instanceof IFile) {
					return -1;
				} else if (e1 instanceof IFile && e2 instanceof IFolder) {
					return 1;
				}
				return e1.toString().compareToIgnoreCase(e2.toString());
			}
		});
		treeViewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(org.eclipse.jface.viewers.CheckStateChangedEvent event) {
				Object element = event.getElement();
				boolean checked = event.getChecked();
				treeViewer.setChecked(element, checked);
				if (element instanceof IFolder || element instanceof IProject) {
					try {
						List<IResource> items = new ArrayList<>();
						collectFilesAndFolders(element, items);
						for (IResource item : items) {
							treeViewer.setChecked(item, checked);
						}
					} catch (CoreException e) {
						LOGGER.log(Level.SEVERE, "Error updating check state for folder", e); //$NON-NLS-1$
					}
				}

				updateParentCheckState(element, checked);
				updateLaunchConfigurationDialog();
			}

			private void updateParentCheckState(Object element, boolean checked) {
				ITreeContentProvider provider = (ITreeContentProvider) treeViewer.getContentProvider();
				Object parent = provider.getParent(element);
				while (parent != null) {
					if (checked) {
						treeViewer.setChecked(parent, true);
					} else {

						if (!hasCheckedChildren(parent)) {
							treeViewer.setChecked(parent, false);
						}
					}
					parent = provider.getParent(parent);
				}
			}

			private boolean hasCheckedChildren(Object element) {
				try {
					Object[] children = ((ITreeContentProvider) treeViewer.getContentProvider()).getChildren(element);
					for (Object child : children) {
						if (treeViewer.getChecked(child)) {
							return true;
						}
					}
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "Error checking children state", e); //$NON-NLS-1$
				}
				return false;
			}
		});

		Composite buttonComposite = new Composite(filesGroup, SWT.NONE);
		GridLayout buttonLayout = new GridLayout(2, false);
		buttonComposite.setLayout(buttonLayout);
		buttonComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false));

		Button selectAllButton = new Button(buttonComposite, SWT.PUSH);
		selectAllButton.setText(Messages.CoverageTab_33);
		selectAllButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
		selectAllButton.addSelectionListener(widgetSelectedAdapter(e -> {
			if (treeViewer.getInput() instanceof IProject project) {
				try {
					List<IResource> allItems = new ArrayList<>();
					collectFilesAndFolders(project, allItems);
					treeViewer.setCheckedElements(allItems.toArray());
					updateLaunchConfigurationDialog();
				} catch (CoreException ex) {
					LOGGER.log(Level.SEVERE, "Error selecting all files and folders", ex); //$NON-NLS-1$
				}
			}
		}));

		Button deselectAllButton = new Button(buttonComposite, SWT.PUSH);
		deselectAllButton.setText(Messages.CoverageTab_44);
		deselectAllButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
		deselectAllButton.addSelectionListener(widgetSelectedAdapter(e -> {
			treeViewer.setCheckedElements(new Object[0]);
			updateLaunchConfigurationDialog();
		}));
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

	private void createProjectButton(Composite parent) {
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
			treeViewer.setInput(null);
			return;
		}
		ICMakeProject cmakeProject = CMakeCorePlugin.getDefault().getProject(projectName);
		if (cmakeProject != null) {
			targetComboViewer.setInput(cmakeProject.getTargets());
			treeViewer.setInput(cmakeProject.getProject());
		} else {
			targetComboViewer.setInput(null);
			treeViewer.setInput(null);
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(PROJECT_NAME_KEY, getCurrentProject());
		configuration.setAttribute(TARGET_NAME_KEY, ""); //$NON-NLS-1$
		configuration.setAttribute(ANALYSIS_SCOPE_KEY, new ArrayList<>());
		configuration.setAttribute(SELECTED_FOLDERS_KEY, new ArrayList<>());
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String projectName = configuration.getAttribute(PROJECT_NAME_KEY, getCurrentProject());
			projectText.setText(projectName);
			updateTargets();
			String targetName = configuration.getAttribute(TARGET_NAME_KEY, ""); //$NON-NLS-1$
			targetCombo.setText(targetName);
			List<String> savedFiles = configuration.getAttribute(ANALYSIS_SCOPE_KEY, new ArrayList<>());
			List<String> savedFolders = configuration.getAttribute(SELECTED_FOLDERS_KEY, new ArrayList<>());
			if (treeViewer.getInput() instanceof IProject project) {
				List<IResource> allItems = new ArrayList<>();
				collectFilesAndFolders(project, allItems);
				for (IResource item : allItems) {
					String path = CoverageUtils.removeFirstSegment(item.getFullPath().toString(), 1);
					if (item instanceof IFile && savedFiles.contains(path)) {
						treeViewer.setChecked(item, true);
					} else if (item instanceof IFolder && savedFolders.contains(path)) {
						treeViewer.setChecked(item, true);
					}
				}
			}
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error initializing from configuration", e); //$NON-NLS-1$
		}
	}

	private void collectFilesAndFolders(Object resource, List<IResource> items) throws CoreException {
		if (resource instanceof IFile file) {
			String extension = file.getFileExtension();
			if (extension != null && ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
				items.add(file);
			}
		} else if (resource instanceof IProject project) {
			for (IResource member : project.members()) {
				if (!EXCLUDED_FOLDERS.contains(member.getName())) {
					if (member instanceof IFolder && hasValidFiles(member)) {
						items.add(member);
					}
					collectFilesAndFolders(member, items);
				}
			}
		} else if (resource instanceof IFolder folder) {
			if (!EXCLUDED_FOLDERS.contains(folder.getName()) && hasValidFiles(folder)) {
				items.add(folder);
				for (IResource member : folder.members()) {
					if (!EXCLUDED_FOLDERS.contains(member.getName())) {
						collectFilesAndFolders(member, items);
					}
				}
			}
		}
	}

	private boolean hasValidFiles(IResource resource) {
		IResource[] members;
		try {
			members = resource instanceof IProject project ? project.members() : ((IFolder) resource).members();
		} catch (CoreException e) {
			LOGGER.log(Level.SEVERE, "Error checking valid files", e); //$NON-NLS-1$
			return false;
		}
		return Arrays.stream(members).filter(member -> !EXCLUDED_FOLDERS.contains(member.getName()))
				.anyMatch(member -> (member instanceof IFile file && file.getFileExtension() != null
						&& ALLOWED_EXTENSIONS.contains(file.getFileExtension().toLowerCase()))
						|| (member instanceof IFolder && hasValidFiles(member)));
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(PROJECT_NAME_KEY, projectText.getText());
		configuration.setAttribute(TARGET_NAME_KEY, targetCombo.getText());
		List<String> analysisScope = new ArrayList<>();
		List<String> selectedFolders = new ArrayList<>();
		for (Object element : treeViewer.getCheckedElements()) {
			if (element instanceof IFile file) {
				analysisScope.add(CoverageUtils.removeFirstSegment(file.getFullPath().toString(), 1));
			} else if (element instanceof IFolder folder) {
				selectedFolders.add(CoverageUtils.removeFirstSegment(folder.getFullPath().toString(), 1));
			}
		}
		configuration.setAttribute(ANALYSIS_SCOPE_KEY, analysisScope);
		configuration.setAttribute(SELECTED_FOLDERS_KEY, selectedFolders);
	}

	@Override
	public String getName() {
		return Messages.CoverageTab_25;
	}

	public static String getCurrentProject() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null)
			return ""; //$NON-NLS-1$
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
		return ""; //$NON-NLS-1$
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