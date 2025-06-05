package su.softcom.cldt.testing.ui;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import su.softcom.cldt.testing.core.Activator;
import su.softcom.cldt.testing.core.CoverageSettingsManager;

public class CoveragePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final ILog LOGGER = Platform.getLog(CoveragePreferencePage.class);
	private static final String OPEN_VIEW_AUTO = "coverage.open_view_auto";
	private static final String GENERATE_REPORT = "coverage.generate_report";
	private static final String CLEAN_PROFILE_DATA = "coverage.clean_profile_data";
	private static final String INCLUDES = "coverage.includes";
	private static final String EXCLUDES = "coverage.excludes";
	private static final String LLVM_COV_PATH = "coverage.llvm_cov_path";
	private static final String LLVM_PROFDATA_PATH = "coverage.llvm_profdata_path";

	private Button openViewAutoCheck;
	private Button generateReportCheck;
	private Button cleanProfileDataCheck;
	private Text includesText;
	private Text excludesText;
	private Text llvmCovPathText;
	private Text llvmProfdataPathText;

	public CoveragePreferencePage() {
	}

	@Override
	public void init(IWorkbench workbench) {
		Activator activator = Activator.getDefault();
		if (activator != null) {
			setPreferenceStore(activator.getPreferenceStore());
		} else {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Plugin is not initialized. Preference store is unavailable."));
			setErrorMessage("Plugin is not initialized. Preference store is unavailable.");
		}
	}

	@Override
	protected Control createContents(Composite parent) {
		if (getPreferenceStore() == null) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					"Preference store is not available. Please check plugin initialization."));
			Label errorLabel = new Label(parent, SWT.NONE);
			errorLabel.setText("Error: Preference store is not available. Please check plugin initialization.");
			return errorLabel;
		}

		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));

		createSessionManagementGroup(mainComposite);
		createCoverageScopeGroup(mainComposite);
		createGeneralGroup(mainComposite);

		return mainComposite;
	}

	private void createSessionManagementGroup(Composite parent) {
		Group sessionGroup = createGroup(parent, Messages.CoveragePreferencePage_15, 2);
		IPreferenceStore store = getPreferenceStore();

		openViewAutoCheck = createCheckbox(sessionGroup, Messages.CoveragePreferencePage_16,
				store.getBoolean(OPEN_VIEW_AUTO));
		generateReportCheck = createCheckbox(sessionGroup, Messages.CoveragePreferencePage_17,
				store.getBoolean(GENERATE_REPORT));
		cleanProfileDataCheck = createCheckbox(sessionGroup, Messages.CoveragePreferencePage_18,
				store.getBoolean(CLEAN_PROFILE_DATA));
	}

	private void createCoverageScopeGroup(Composite parent) {
		Group scopeGroup = createGroup(parent, Messages.CoveragePreferencePage_19, 2);
		IPreferenceStore store = getPreferenceStore();

		includesText = createLabeledText(scopeGroup, Messages.CoveragePreferencePage_20, store.getString(INCLUDES));
		excludesText = createLabeledText(scopeGroup, Messages.CoveragePreferencePage_21, store.getString(EXCLUDES));
	}

	private void createGeneralGroup(Composite parent) {
		Group generalGroup = createGroup(parent, Messages.CoveragePreferencePage_22, 3);
		IPreferenceStore store = getPreferenceStore();

		llvmCovPathText = createLabeledText(generalGroup, Messages.CoveragePreferencePage_23,
				store.getString(LLVM_COV_PATH));
		createBrowseButton(generalGroup, llvmCovPathText, Messages.CoveragePreferencePage_24);

		llvmProfdataPathText = createLabeledText(generalGroup, Messages.CoveragePreferencePage_9,
				store.getString(LLVM_PROFDATA_PATH));
		createBrowseButton(generalGroup, llvmProfdataPathText, Messages.CoveragePreferencePage_10);
	}

	private Group createGroup(Composite parent, String text, int columns) {
		Group group = new Group(parent, SWT.NONE);
		group.setLayout(new GridLayout(columns, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		group.setText(text);
		return group;
	}

	private Button createCheckbox(Group parent, String text, boolean selection) {
		Button checkbox = new Button(parent, SWT.CHECK);
		checkbox.setSelection(selection);
		checkbox.setText(text);
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		return checkbox;
	}

	private Text createLabeledText(Group parent, String labelText, String initialValue) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		Text textField = new Text(parent, SWT.BORDER);
		textField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		textField.setText(initialValue);
		return textField;
	}

	private void createBrowseButton(Group parent, Text targetText, String dialogTitle) {
		Button browseButton = new Button(parent, SWT.PUSH);
		browseButton.setText(Messages.CoveragePreferencePage_25);
		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dialog = new FileDialog(parent.getShell(), SWT.OPEN);
				dialog.setText(dialogTitle);
				String path = dialog.open();
				if (path != null) {
					targetText.setText(path);
				}
			}
		});
	}

	@Override
	protected void performDefaults() {
		IPreferenceStore store = getPreferenceStore();
		openViewAutoCheck.setSelection(store.getDefaultBoolean(OPEN_VIEW_AUTO));
		generateReportCheck.setSelection(store.getDefaultBoolean(GENERATE_REPORT));
		cleanProfileDataCheck.setSelection(store.getDefaultBoolean(CLEAN_PROFILE_DATA));
		includesText.setText(store.getDefaultString(INCLUDES));
		excludesText.setText(store.getDefaultString(EXCLUDES));
		llvmCovPathText.setText(store.getDefaultString(LLVM_COV_PATH));
		llvmProfdataPathText.setText(store.getDefaultString(LLVM_PROFDATA_PATH));
		super.performDefaults();
	}

	@Override
	protected void performApply() {
		savePreferences();
		super.performApply();
	}

	@Override
	public boolean performOk() {
		savePreferences();
		return super.performOk();
	}

	private void savePreferences() {
		IPreferenceStore store = getPreferenceStore();
		store.setValue(OPEN_VIEW_AUTO, openViewAutoCheck.getSelection());
		store.setValue(GENERATE_REPORT, generateReportCheck.getSelection());
		store.setValue(CLEAN_PROFILE_DATA, cleanProfileDataCheck.getSelection());
		store.setValue(INCLUDES, includesText.getText());
		store.setValue(EXCLUDES, excludesText.getText());
		store.setValue(LLVM_COV_PATH, llvmCovPathText.getText());
		store.setValue(LLVM_PROFDATA_PATH, llvmProfdataPathText.getText());
		CoverageSettingsManager.notifySettingsChanged();
	}
}