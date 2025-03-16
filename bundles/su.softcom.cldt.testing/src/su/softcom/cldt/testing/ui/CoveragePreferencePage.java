package su.softcom.cldt.testing.ui;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class CoveragePreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private Text includesText;
	private Text excludesText;

	public CoveragePreferencePage() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
	}

	@Override
	public void createControl(Composite parent) {
		Composite mainComposite = new Composite(parent, SWT.NONE);
		mainComposite.setLayout(new GridLayout(1, false));
		setControl(mainComposite);

		createSessionManagementGroup(mainComposite);
		createCoverageRuntimeGroup(mainComposite);
	}

	private void createSessionManagementGroup(Composite parent) {
		Group sessionGroup = new Group(parent, SWT.NONE);
		sessionGroup.setLayout(new GridLayout(2, false));
		sessionGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		sessionGroup.setText(Messages.CoveragePreferencePage_0);

		createSessionManagementButtons(sessionGroup);
	}

	private void createSessionManagementButtons(Group sessionGroup) {
		createCheckbox(sessionGroup, Messages.CoveragePreferencePage_1, true);
		createCheckbox(sessionGroup, Messages.CoveragePreferencePage_2, true);
		createCheckbox(sessionGroup, Messages.CoveragePreferencePage_3, true);
	}

	private void createCheckbox(Group parent, String text, boolean selection) {
		Button checkbox = new Button(parent, SWT.CHECK);
		checkbox.setSelection(selection);
		checkbox.setText(text);
		checkbox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
	}

	private void createCoverageRuntimeGroup(Composite parent) {
		Group coverageGroup = new Group(parent, SWT.NONE);
		coverageGroup.setLayout(new GridLayout(2, false));
		coverageGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		coverageGroup.setText(Messages.CoveragePreferencePage_4);

		createCoverageRuntimeFields(coverageGroup);
	}

	private void createCoverageRuntimeFields(Group coverageGroup) {
		createLabelAndText(coverageGroup, Messages.CoveragePreferencePage_5, includesText);
		createLabelAndText(coverageGroup, Messages.CoveragePreferencePage_6, excludesText);
	}

	private void createLabelAndText(Group parent, String labelText, Text textField) {
		Label label = new Label(parent, SWT.NONE);
		label.setText(labelText);
		textField = new Text(parent, SWT.BORDER);
		textField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
	}

	@Override
	protected void createFieldEditors() {
		// TODO Auto-generated method stub
	}
}