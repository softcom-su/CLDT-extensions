package su.softcom.cldt.testing.ui;

import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.ITextEditor;

public class AnnotationUpdateListener implements IPartListener {
	private final AnnotationUpdater annotationUpdater;

	public AnnotationUpdateListener(AnnotationUpdater annotationUpdater) {
		this.annotationUpdater = annotationUpdater;
	}

	@Override
	public void partOpened(IWorkbenchPart part) {
		if (part instanceof ITextEditor editor) {
			annotationUpdater.updateAnnotations(editor);
		}
	}

	@Override
	public void partActivated(IWorkbenchPart part) {
	}

	@Override
	public void partBroughtToTop(IWorkbenchPart part) {
	}

	@Override
	public void partClosed(IWorkbenchPart part) {
	}

	@Override
	public void partDeactivated(IWorkbenchPart part) {
	}
}