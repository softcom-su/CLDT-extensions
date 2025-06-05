package su.softcom.cldt.testing.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;

public class CoverageMetricAction extends Action {
	private final CoverageResultView view;
	private final String counter;

	public CoverageMetricAction(CoverageResultView view, String counter) {
		super(counter, AS_RADIO_BUTTON);
		this.view = view;
		this.counter = counter;
		setChecked(view.getSelectedCounter().equals(counter));
	}

	@Override
	public void run() {
		if (!view.getSelectedCounter().equals(counter)) {
			view.setSelectedCounter(counter);
			view.getDataProcessor().setSelectedCounter(counter);
			view.getAnnotationUpdater().setCurrentMetric(counter);
			view.refreshColumns();
			view.updateTreeWithNewMetrics();
			view.getAnnotationUpdater().updateOpenEditors();
		}
	}

	public static String[] getColumnNamesForCounter(String counterType) {
		if (Messages.CoverageResultView_2.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_5, Messages.CoverageResultView_6,
					Messages.CoverageResultView_7, Messages.CoverageResultView_8 };
		}
		if (Messages.CoverageResultView_1.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_9, Messages.CoverageResultView_10,
					Messages.CoverageResultView_11, Messages.CoverageResultView_12 };
		}
		if (Messages.CoverageResultView_3.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_13, Messages.CoverageResultView_14,
					Messages.CoverageResultView_15, Messages.CoverageResultView_16 };
		}
		return new String[] {};
	}

	public static void createColumn(TreeViewer treeViewer, String title, int width, int index) {
		TreeViewerColumn column = new TreeViewerColumn(treeViewer, SWT.NONE);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				if (cell.getElement() instanceof CoverageNode node && node.getCoverageData() != null) {
					Object[] data = node.getCoverageData();
					cell.setText(data.length > index ? data[index].toString() : "");
				}
			}
		});
	}
}