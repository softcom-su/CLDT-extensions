package su.softcom.cldt.testing.ui;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class CoverageResultView extends ViewPart {

	public static final String ID = "su.softcom.cldt.testing.ui.CoverageResultView";
	private static final ILog LOGGER = Platform.getLog(CoverageResultView.class);

	private static final String LINE_COUNTERS = Messages.CoverageResultView_1;
	private static final String BRANCH_COUNTERS = Messages.CoverageResultView_2;
	private static final String FUNCTION_COUNTERS = Messages.CoverageResultView_3;

	private TableViewer tableViewer;
	private String selectedCounter = LINE_COUNTERS;
	private CoverageDataProvider dataProvider;
	private List<String> analysisScope;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		setupTableViewer(parent);
		createColumns();
		configureMenu();
	}

	private void setupTableViewer(Composite parent) {
		tableViewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		tableViewer.setContentProvider(new ArrayContentProvider());
	}

	private void createColumns() {
		clearExistingColumns();
		createElementColumn();
		createCounterColumns();
	}

	private void clearExistingColumns() {
		while (tableViewer.getTable().getColumnCount() > 0) {
			tableViewer.getTable().getColumn(0).dispose();
		}
	}

	private void createElementColumn() {
		TableViewerColumn elementColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		elementColumn.getColumn().setText(Messages.CoverageResultView_4);
		elementColumn.getColumn().setWidth(200);
		elementColumn.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Object[] data = (Object[]) cell.getElement();
				cell.setText(CoverageUtils.removeFirstSegment(data[0].toString(), 3));
			}
		});
	}

	private void createCounterColumns() {
		String[] columnNames = getColumnNamesForCounter(selectedCounter);
		int[] columnWidths = { 250, 150, 150, 150 };

		for (int i = 0; i < columnNames.length; i++) {
			createColumn(columnNames[i], columnWidths[i], i + 1);
		}
	}

	private String[] getColumnNamesForCounter(String counterType) {
		if (BRANCH_COUNTERS.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_5, Messages.CoverageResultView_6,
					Messages.CoverageResultView_7, Messages.CoverageResultView_8 };
		} else if (LINE_COUNTERS.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_9, Messages.CoverageResultView_10,
					Messages.CoverageResultView_11, Messages.CoverageResultView_12 };
		} else if (FUNCTION_COUNTERS.equals(counterType)) {
			return new String[] { Messages.CoverageResultView_13, Messages.CoverageResultView_14,
					Messages.CoverageResultView_15, Messages.CoverageResultView_16 };
		} else {
			return new String[] {};
		}
	}

	private void createColumn(String title, int width, int index) {
		TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.NONE);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Object[] data = (Object[]) cell.getElement();
				cell.setText(data.length > index ? data[index].toString() : "");
			}
		});
	}

	private void configureMenu() {
		getViewSite().getActionBars().getMenuManager().add(new SelectCountersAction(LINE_COUNTERS));
		getViewSite().getActionBars().getMenuManager().add(new SelectCountersAction(BRANCH_COUNTERS));
		getViewSite().getActionBars().getMenuManager().add(new SelectCountersAction(FUNCTION_COUNTERS));
	}

	private class SelectCountersAction extends org.eclipse.jface.action.Action {
		private final String counter;

		public SelectCountersAction(String counter) {
			super(counter, AS_RADIO_BUTTON);
			this.counter = counter;
			setChecked(selectedCounter.equals(counter));
		}

		@Override
		public void run() {
			if (!selectedCounter.equals(counter)) {
				selectedCounter = counter;
				refreshColumns();
				loadCoverageResults();
			}
		}
	}

	private void refreshColumns() {
		tableViewer.getTable().setRedraw(false);
		createColumns();
		tableViewer.getTable().setRedraw(true);
		tableViewer.refresh();
	}

	private void loadCoverageResults() {
		if (dataProvider != null) {
			Map<String, Map<String, Object[]>> coverageResults = dataProvider.getCoverageData();
			updateCoverageResults(coverageResults, analysisScope);
		}
	}

	public void updateCoverageResults(Map<String, Map<String, Object[]>> coverageResults, List<String> analysisScope) {
		tableViewer.getTable().removeAll();
		if (coverageResults == null || coverageResults.isEmpty()) {
			LOGGER.log(new Status(IStatus.WARNING, "su.softcom.cldt.testing",
					"Coverage results are null or empty in updateCoverageResults"));
			return;
		}

		boolean useFiltering = analysisScope != null && !analysisScope.isEmpty();
		List<Object[]> data = coverageResults.entrySet().stream().filter(
				entry -> !useFiltering || analysisScope.contains(CoverageUtils.removeFirstSegment(entry.getKey(), 4)))
				.map(entry -> {
					Map<String, Object[]> counters = entry.getValue();
					Object[] counterData = counters.get(selectedCounter);
					return counterData != null ? counterData : new Object[0];
				}).filter(counterData -> counterData.length > 0).collect(Collectors.toList());
		tableViewer.setInput(data.toArray(new Object[0][]));
	}

	public void setDataProvider(CoverageDataProvider dataProvider) {
		this.dataProvider = dataProvider;
	}

	public void setAnalysisScope(List<String> analysisScope) {
		this.analysisScope = analysisScope;
	}

	@Override
	public void setFocus() {
		tableViewer.getTable().setFocus();
	}

	public interface CoverageDataProvider {
		Map<String, Map<String, Object[]>> getCoverageData();
	}
}