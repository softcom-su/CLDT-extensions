package su.softcom.cldt.qt.ui.internal.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import su.softcom.cldt.qt.common.Messages;


public class QtImportConsoleView extends ViewPart {

    public static final String ID = "su.softcom.cldt.qt.ui.internal.views.QtImportConsoleView";
    private Text consoleText;

    /**
     * Creates the console UI with a read-only text widget.
     * 
     * @param parent The parent composite.
     */
    @Override
    public void createPartControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        consoleText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        consoleText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        consoleText.setText(Messages.qtImportConsoleViewStartMessage + "\n");
    }

    /**
     * Sets focus to the console text widget.
     */
    @Override
    public void setFocus() {
        if (consoleText != null && !consoleText.isDisposed()) {
            consoleText.setFocus();
        }
    }

    /**
     * Appends a message to the console and scrolls to the bottom.
     * 
     * @param message The message to append.
     */
    public void logMessage(String message) {
        if (consoleText != null && !consoleText.isDisposed()) {
            consoleText.append(message + "\n");
            consoleText.setTopIndex(consoleText.getLineCount() - 1);
        }
    }

    /**
     * Appends an error message with scrolls to the bottom.
     * 
     * @param error The error message to append.
     */
    public void logError(String error) {
        if (consoleText != null && !consoleText.isDisposed()) {
            consoleText.append(error + "\n");
            consoleText.setTopIndex(consoleText.getLineCount() - 1);
        }
    }

    /**
     * Clears the console text.
     */
    public void clear() {
        if (consoleText != null && !consoleText.isDisposed()) {
            consoleText.setText("");
        }
    }
}