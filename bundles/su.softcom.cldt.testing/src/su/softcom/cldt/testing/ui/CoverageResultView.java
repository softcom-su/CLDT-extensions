package su.softcom.cldt.testing.ui;

import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import su.softcom.cldt.testing.core.CoverageDataProcessor;
import su.softcom.cldt.testing.core.CoverageSettingsManager;
import su.softcom.cldt.testing.core.ReportParser;

public class CoverageResultView extends ViewPart {
	public static final String ID = "su.softcom.cldt.testing.ui.CoverageResultView";
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String LINE_COUNTERS = Messages.CoverageResultView_1;
	private static final String BRANCH_COUNTERS = Messages.CoverageResultView_2;
	private static final String FUNCTION_COUNTERS = Messages.CoverageResultView_3;
	private static final String WARNING_NULL_DISPOSED = "Cannot %s: treeViewer is null or disposed";
	private static final String WARNING_NULL_DATA_PROVIDER = "Cannot refresh: both coverageData and dataProvider are null";
	private static final String WARNING_NULL_DATA = "Cannot refresh: dataProvider returned null coverage data";
	private static final String WARNING_NULL_PROJECT = "Cannot refresh: project is null";
	private static final String WARNING_FILE_NOT_FOUND = "File does not exist: %s";
	private static final ILog LOGGER = Platform.getLog(CoverageResultView.class);

	private TreeViewer treeViewer;
	private String selectedCounter = LINE_COUNTERS;
	private CoverageDataProvider dataProvider;
	private List<String> analysisScope;
	private ReportParser.CoverageResult currentCoverageData;
	private IProject project;
	private List<CoverageNode> currentTreeNodes;
	private CoverageDataProcessor dataProcessor;
	private AnnotationUpdater annotationUpdater;
	private final IPartListener editorPartListener = new EditorPartListener();

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		dataProcessor = new CoverageDataProcessor(selectedCounter);
		annotationUpdater = new AnnotationUpdater(CoverageDataManager.getInstance());
		setupTreeViewer(parent);
		createColumns();
		configureMenu();
		getViewSite().getPage().addPartListener(editorPartListener);
	}

	private class EditorPartListener implements IPartListener {
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

	private void setupTreeViewer(Composite parent) {
		treeViewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
		Tree tree = treeViewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);
		treeViewer.setContentProvider(new ITreeContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				return inputElement instanceof List<?> list ? list.toArray() : new Object[0];
			}

			@Override
			public Object[] getChildren(Object parentElement) {
				return parentElement instanceof CoverageNode node ? node.getChildren().toArray() : new Object[0];
			}

			@Override
			public Object getParent(Object element) {
				return null;
			}

			@Override
			public boolean hasChildren(Object element) {
				return element instanceof CoverageNode node && !node.getChildren().isEmpty();
			}
		});
	}

	private void createColumns() {
		clearExistingColumns();
		createElementColumn();
		createCounterColumns();
	}

	private void clearExistingColumns() {
		while (treeViewer.getTree().getColumnCount() > 0) {
			treeViewer.getTree().getColumn(0).dispose();
		}
	}

	private void createElementColumn() {
		TreeViewerColumn column = new TreeViewerColumn(treeViewer, SWT.NONE);
		column.getColumn().setText(Messages.CoverageResultView_4);
		column.getColumn().setWidth(200);
		column.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				if (cell.getElement() instanceof CoverageNode node) {
					cell.setText(node.getName());
				}
			}
		});
	}

	private void createCounterColumns() {
		String[] columnNames = SelectCountersAction.getColumnNamesForCounter(selectedCounter);
		int[] columnWidths = { 250, 150, 150, 150 };
		for (int i = 0; i < columnNames.length; i++) {
			SelectCountersAction.createColumn(treeViewer, columnNames[i], columnWidths[i], i + 1);
		}
	}

	private void configureMenu() {
		var menuManager = getViewSite().getActionBars().getMenuManager();
		menuManager.add(new SelectCountersAction(this, LINE_COUNTERS));
		menuManager.add(new SelectCountersAction(this, BRANCH_COUNTERS));
		menuManager.add(new SelectCountersAction(this, FUNCTION_COUNTERS));
	}

	private class SelectCountersAction extends Action {
		private final String counter;

		public SelectCountersAction(CoverageResultView view, String counter) {
			super(counter, AS_RADIO_BUTTON);
			this.counter = counter;
			setChecked(view.selectedCounter.equals(counter));
		}

		@Override
		public void run() {
			if (!selectedCounter.equals(counter)) {
				selectedCounter = counter;
				dataProcessor.setSelectedCounter(counter);
				annotationUpdater.setCurrentMetric(counter);
				refreshColumns();
				updateTreeWithNewMetrics();
				annotationUpdater.updateOpenEditors();
			}
		}

		public static String[] getColumnNamesForCounter(String counterType) {
			if (BRANCH_COUNTERS.equals(counterType)) {
				return new String[] { Messages.CoverageResultView_5, Messages.CoverageResultView_6,
						Messages.CoverageResultView_7, Messages.CoverageResultView_8 };
			}
			if (LINE_COUNTERS.equals(counterType)) {
				return new String[] { Messages.CoverageResultView_9, Messages.CoverageResultView_10,
						Messages.CoverageResultView_11, Messages.CoverageResultView_12 };
			}
			if (FUNCTION_COUNTERS.equals(counterType)) {
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

	private void refreshColumns() {
		if (treeViewer == null || treeViewer.getTree().isDisposed()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_NULL_DISPOSED, "refresh columns")));
			return;
		}
		treeViewer.getTree().setRedraw(false);
		createColumns();
		treeViewer.getTree().setRedraw(true);
		treeViewer.refresh();
	}

	public void updateCoverageResults(ReportParser.CoverageResult coverageResults, List<String> analysisScope) {
		this.currentCoverageData = coverageResults;
		List<String> filteredScope = project != null
				? CoverageSettingsManager.filterAnalysisScope(analysisScope, project)
				: analysisScope;
		this.analysisScope = filteredScope;

		if (treeViewer == null || treeViewer.getTree().isDisposed()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					String.format(WARNING_NULL_DISPOSED, "update coverage results")));
			return;
		}

		CoverageDataManager.getInstance().setCoverageData(coverageResults, this.analysisScope);

		Object[] expandedElements = treeViewer.getExpandedElements();
		currentTreeNodes = dataProcessor.buildCoverageTree(coverageResults, filteredScope);
		treeViewer.setInput(currentTreeNodes);
		treeViewer.setExpandedElements(expandedElements);
		treeViewer.refresh();

		annotationUpdater.setCurrentMetric(selectedCounter);
		annotationUpdater.updateOpenEditors();

		for (String filePath : filteredScope) {
			IFile file = project.getFile(filePath);
			if (file.exists()) {
				annotationUpdater.createCoverageMarkers(file);
			} else {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_FILE_NOT_FOUND, filePath)));
			}
		}
	}

	private void updateTreeWithNewMetrics() {
		if (treeViewer == null || treeViewer.getTree().isDisposed() || currentCoverageData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_NULL_DISPOSED, "update tree")));
			return;
		}

		Object[] expandedElements = treeViewer.getExpandedElements();
		currentTreeNodes = dataProcessor.buildCoverageTree(currentCoverageData, analysisScope);
		treeViewer.setInput(currentTreeNodes);
		treeViewer.setExpandedElements(expandedElements);
		treeViewer.refresh();
	}

	public void setDataProvider(CoverageDataProvider dataProvider) {
		this.dataProvider = dataProvider;
	}

	public void setProject(IProject project) {
		this.project = project;
	}

	public void setAnalysisScope(List<String> analysisScope) {
		this.analysisScope = analysisScope;
	}

	public void refresh() {
		if (treeViewer == null || treeViewer.getTree().isDisposed()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, String.format(WARNING_NULL_DISPOSED, "refresh")));
			return;
		}

		if (currentCoverageData == null && dataProvider != null) {
			currentCoverageData = dataProvider.getFullCoverageData();
			if (currentCoverageData == null) {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, WARNING_NULL_DATA));
				return;
			}
		}

		if (currentCoverageData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, WARNING_NULL_DATA_PROVIDER));
			return;
		}

		if (project == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, WARNING_NULL_PROJECT));
			return;
		}

		List<String> updatedAnalysisScope = CoverageSettingsManager.filterAnalysisScope(analysisScope, project);
		updateCoverageResults(currentCoverageData, updatedAnalysisScope);
	}

	@Override
	public void dispose() {
		getViewSite().getPage().removePartListener(editorPartListener);
		super.dispose();
	}

	@Override
	public void setFocus() {
		if (treeViewer != null && !treeViewer.getTree().isDisposed()) {
			treeViewer.getTree().setFocus();
		}
	}

	public interface CoverageDataProvider {
		java.util.Map<String, java.util.Map<String, Object[]>> getCoverageData();

		ReportParser.CoverageResult getFullCoverageData();
	}
}