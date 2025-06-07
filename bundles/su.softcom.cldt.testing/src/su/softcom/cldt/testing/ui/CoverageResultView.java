package su.softcom.cldt.testing.ui;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.part.ViewPart;
import su.softcom.cldt.testing.core.CoverageDataProcessor;
import su.softcom.cldt.testing.core.CoverageSettingsManager;
import su.softcom.cldt.testing.core.ReportParser;

public class CoverageResultView extends ViewPart {
	public static final String ID = "su.softcom.cldt.testing.ui.CoverageResultView";
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final ILog LOGGER = Platform.getLog(CoverageResultView.class);

	private TreeViewer treeViewer;
	private String selectedCounter = Messages.CoverageResultView_1;
	private CoverageDataProvider dataProvider;
	private List<String> analysisScope;
	private ReportParser.CoverageResult currentCoverageData;
	private IProject project;
	private List<CoverageNode> currentTreeNodes;
	private CoverageDataProcessor dataProcessor;
	private AnnotationUpdater annotationUpdater;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		dataProcessor = new CoverageDataProcessor(selectedCounter);
		annotationUpdater = new AnnotationUpdater(CoverageDataManager.getInstance());
		setupTreeViewer(parent);
		createColumns();
		configureMenu();
		configureContextMenu();
		getViewSite().getPage().addPartListener(new AnnotationUpdateListener(annotationUpdater));
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
		String[] columnNames = CoverageMetricAction.getColumnNamesForCounter(selectedCounter);
		int[] columnWidths = { 250, 150, 150, 150 };
		for (int i = 0; i < columnNames.length; i++) {
			CoverageMetricAction.createColumn(treeViewer, columnNames[i], columnWidths[i], i + 1);
		}
	}

	private void configureMenu() {
		var menuManager = getViewSite().getActionBars().getMenuManager();
		menuManager.add(new CoverageMetricAction(this, Messages.CoverageResultView_1));
		menuManager.add(new CoverageMetricAction(this, Messages.CoverageResultView_2));
		menuManager.add(new CoverageMetricAction(this, Messages.CoverageResultView_3));
	}

	private void configureContextMenu() {
		MenuManager menuManager = new MenuManager();
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(this::fillContextMenu);
		Menu menu = menuManager.createContextMenu(treeViewer.getTree());
		treeViewer.getTree().setMenu(menu);
		getSite().registerContextMenu(menuManager, treeViewer);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(new Action("Копировать") {
			@Override
			public void run() {
				Object[] selectedElements = treeViewer.getStructuredSelection().toArray();
				if (selectedElements.length > 0 && selectedElements[0] instanceof CoverageNode node) {
					String text = collectNodeText(node);
					copyToClipboard(text);
				}
			}
		});
	}

	private String collectNodeText(CoverageNode node) {
		StringBuilder builder = new StringBuilder();
		builder.append(node.getName());
		if (!node.getChildren().isEmpty()) {
			builder.append("\n");
			for (CoverageNode child : node.getChildren()) {
				builder.append(collectNodeText(child)).append("\n");
			}
		}
		return builder.toString().trim();
	}

	private void copyToClipboard(String text) {
		Clipboard clipboard = new Clipboard(treeViewer.getTree().getDisplay());
		TextTransfer textTransfer = TextTransfer.getInstance();
		clipboard.setContents(new Object[] { text }, new Transfer[] { textTransfer });
		clipboard.dispose();
	}

	void refreshColumns() {
		if (treeViewer == null || treeViewer.getTree().isDisposed()) {
			LOGGER.log(
					new Status(IStatus.WARNING, PLUGIN_ID, "Cannot refresh columns: treeViewer is null or disposed"));
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
		List<String> normalizedScope = new ArrayList<>();
		String projectPath = project != null ? project.getLocation().toOSString() + "/" : "";
		for (String path : filteredScope) {
			String normalizedPath = path.replace('\\', '/');
			normalizedScope.add(normalizedPath);
			normalizedScope.add(projectPath + normalizedPath);
		}
		this.analysisScope = normalizedScope;

		if (treeViewer == null || treeViewer.getTree().isDisposed()) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Cannot update coverage results: treeViewer is null or disposed"));
			return;
		}

		CoverageDataManager.getInstance().setCoverageData(coverageResults, this.analysisScope);

		Object[] expandedElements = treeViewer.getExpandedElements();
		currentTreeNodes = dataProcessor.buildCoverageTree(coverageResults, normalizedScope);
		treeViewer.setInput(currentTreeNodes);
		treeViewer.setExpandedElements(expandedElements);
		treeViewer.refresh();

		annotationUpdater.setCurrentMetric(selectedCounter);
		annotationUpdater.updateOpenEditors();

		for (String filePath : normalizedScope) {
			IFile file = project.getFile(filePath);
			if (!file.exists()) {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "File does not exist: " + filePath));
			}
		}
	}

	void updateTreeWithNewMetrics() {
		if (treeViewer == null || treeViewer.getTree().isDisposed() || currentCoverageData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Cannot update tree: treeViewer is null or disposed"));
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
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Cannot refresh: treeViewer is null or disposed"));
			return;
		}

		if (currentCoverageData == null && dataProvider != null) {
			currentCoverageData = dataProvider.getFullCoverageData();
			if (currentCoverageData == null) {
				LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
						"Cannot refresh: dataProvider returned null coverage data"));
				return;
			}
		}

		if (currentCoverageData == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID,
					"Cannot refresh: both coverageData and dataProvider are null"));
			return;
		}

		if (project == null) {
			LOGGER.log(new Status(IStatus.WARNING, PLUGIN_ID, "Cannot refresh: project is null"));
			return;
		}

		List<String> updatedAnalysisScope = CoverageSettingsManager.filterAnalysisScope(analysisScope, project);
		updateCoverageResults(currentCoverageData, updatedAnalysisScope);
	}

	@Override
	public void dispose() {
		getViewSite().getPage().removePartListener(new AnnotationUpdateListener(annotationUpdater));
		super.dispose();
	}

	@Override
	public void setFocus() {
		if (treeViewer != null && !treeViewer.getTree().isDisposed()) {
			treeViewer.getTree().setFocus();
		}
	}

	public String getSelectedCounter() {
		return selectedCounter;
	}

	public void setSelectedCounter(String counter) {
		this.selectedCounter = counter;
	}

	public CoverageDataProcessor getDataProcessor() {
		return dataProcessor;
	}

	public AnnotationUpdater getAnnotationUpdater() {
		return annotationUpdater;
	}

	public interface CoverageDataProvider {
		java.util.Map<String, java.util.Map<String, Object[]>> getCoverageData();

		ReportParser.CoverageResult getFullCoverageData();
	}
}