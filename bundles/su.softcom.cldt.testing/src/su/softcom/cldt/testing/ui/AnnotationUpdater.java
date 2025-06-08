package su.softcom.cldt.testing.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import su.softcom.cldt.testing.core.ReportParser;
import su.softcom.cldt.testing.utils.CoverageUtils;

public class AnnotationUpdater {
	private static final String PLUGIN_ID = "su.softcom.cldt.testing";
	private static final String MARKER_TYPE = "su.softcom.cldt.testing.coverage";
	private static final ILog LOGGER = Platform.getLog(AnnotationUpdater.class);
	private final CoverageDataManager dataManager;
	private String currentMetric = Messages.CoverageResultView_1;

	public AnnotationUpdater(CoverageDataManager dataManager) {
		this.dataManager = dataManager;
	}

	public void setCurrentMetric(String metric) {
		this.currentMetric = metric;
	}

	public void updateAnnotations(ITextEditor editor) {
		if (editor == null || dataManager.getCoverageData() == null) {
			return;
		}

		IFile file = getFileFromEditor(editor);
		if (file == null) {
			return;
		}

		String filePath = file.getFullPath().toString();
		if (!isFileInAnalysisScope(filePath)) {
			clearAnnotations(editor, filePath);
			clearMarkers(file, filePath);
			return;
		}

		IAnnotationModel model = getAnnotationModel(editor);
		if (model == null) {
			return;
		}

		clearExistingAnnotations(model);
		addAnnotations(model, editor, filePath);
		createCoverageMarkers(file);
	}

	private IFile getFileFromEditor(ITextEditor editor) {
		IEditorInput input = editor.getEditorInput();
		return input != null ? input.getAdapter(IFile.class) : null;
	}

	private boolean isFileInAnalysisScope(String filePath) {
		List<String> analysisScope = dataManager.getAnalysisScope();
		return analysisScope.contains(filePath) || analysisScope.stream().anyMatch(filePath::endsWith);
	}

	private void clearAnnotations(ITextEditor editor, String filePath) {
		IAnnotationModel model = getAnnotationModel(editor);
		if (model != null) {
			clearExistingAnnotations(model);
		}
	}

	private IAnnotationModel getAnnotationModel(ITextEditor editor) {
		IDocumentProvider provider = editor.getDocumentProvider();
		return provider != null ? provider.getAnnotationModel(editor.getEditorInput()) : null;
	}

	private void clearExistingAnnotations(IAnnotationModel model) {
		for (var iterator = model.getAnnotationIterator(); iterator.hasNext();) {
			var annotation = iterator.next();
			if (annotation instanceof CoverageAnnotation) {
				model.removeAnnotation(annotation);
			}
		}
	}

	private void addAnnotations(IAnnotationModel model, ITextEditor editor, String filePath) {
		if (currentMetric.equals(Messages.CoverageResultView_1)) {
			addLineAnnotations(model, editor, filePath);
		} else if (currentMetric.equals(Messages.CoverageResultView_2)) {
			addBranchAnnotations(model, editor, filePath);
		} else if (currentMetric.equals(Messages.CoverageResultView_3)) {
			addFunctionAnnotations(model, editor, filePath);
		}
	}

	private void addLineAnnotations(IAnnotationModel model, ITextEditor editor, String filePath) {
		List<ReportParser.LineCoverage> lines = CoverageUtils.findCoverageForFile(filePath,
				dataManager.getCoverageData());
		List<ReportParser.BranchCoverage> branches = CoverageUtils.findBranchCoverageForFile(filePath,
				dataManager.getBranchCoverage());
		if (lines != null && !lines.isEmpty()) {
			for (ReportParser.LineCoverage line : lines) {
				CoverageAnnotation annotation = createLineAnnotation(line, branches);
				addAnnotationToModel(model, editor, line.lineNumber(), annotation);
			}
		}
	}

	private CoverageAnnotation createLineAnnotation(ReportParser.LineCoverage line,
			List<ReportParser.BranchCoverage> branches) {
		String type;
		String message = "Line " + line.lineNumber() + ": ";
		if (line.executionCount() == 0) {
			type = CoverageAnnotation.TYPE_NOT_COVERED_LINE;
			message += "Not covered";
		} else {
			boolean hasBranches = false;
			boolean allBranchesCovered = true;
			boolean anyBranchCovered = false;
			if (branches != null) {
				for (ReportParser.BranchCoverage branch : branches) {
					if (branch.lineNumber() == line.lineNumber()) {
						hasBranches = true;
						if (branch.covered()) {
							anyBranchCovered = true;
						} else {
							allBranchesCovered = false;
						}
					}
				}
			}
			type = hasBranches && anyBranchCovered && !allBranchesCovered
					? CoverageAnnotation.TYPE_PARTIALLY_COVERED_LINE
					: CoverageAnnotation.TYPE_COVERED_LINE;
			message += hasBranches && anyBranchCovered && !allBranchesCovered ? "Partially covered" : "Covered";
		}
		return new CoverageAnnotation(type, message);
	}

	private void addBranchAnnotations(IAnnotationModel model, ITextEditor editor, String filePath) {
		List<ReportParser.BranchCoverage> branches = CoverageUtils.findBranchCoverageForFile(filePath,
				dataManager.getBranchCoverage());
		if (branches != null && !branches.isEmpty()) {
			HashMap<Integer, List<ReportParser.BranchCoverage>> branchesByLine = groupBranchesByLine(branches);
			for (var entry : branchesByLine.entrySet()) {
				CoverageAnnotation annotation = createBranchAnnotation(entry.getKey(), entry.getValue());
				addAnnotationToModel(model, editor, entry.getKey(), annotation);
			}
		}
	}

	private HashMap<Integer, List<ReportParser.BranchCoverage>> groupBranchesByLine(
			List<ReportParser.BranchCoverage> branches) {
		HashMap<Integer, List<ReportParser.BranchCoverage>> branchesByLine = new HashMap<>();
		for (ReportParser.BranchCoverage branch : branches) {
			branchesByLine.computeIfAbsent(branch.lineNumber(), k -> new ArrayList<>()).add(branch);
		}
		return branchesByLine;
	}

	private CoverageAnnotation createBranchAnnotation(int lineNumber, List<ReportParser.BranchCoverage> lineBranches) {
		String type;
		String message = "Branch at line " + lineNumber + ": ";
		boolean anyCovered = false;
		boolean allCovered = true;
		for (ReportParser.BranchCoverage branch : lineBranches) {
			if (branch.covered()) {
				anyCovered = true;
			} else {
				allCovered = false;
			}
		}
		if (allCovered) {
			type = CoverageAnnotation.TYPE_COVERED_BRANCH;
			message += "Covered";
		} else if (!anyCovered) {
			type = CoverageAnnotation.TYPE_NOT_COVERED_BRANCH;
			message += "Not covered";
		} else {
			type = CoverageAnnotation.TYPE_PARTIALLY_COVERED_BRANCH;
			message += "Partially covered";
		}
		return new CoverageAnnotation(type, message);
	}

	private void addFunctionAnnotations(IAnnotationModel model, ITextEditor editor, String filePath) {
		List<ReportParser.FunctionCoverage> functions = CoverageUtils.findFunctionCoverageForFile(filePath,
				dataManager.getAnnotationFunctionCoverage());
		if (functions != null && !functions.isEmpty()) {
			Map<String, List<ReportParser.FunctionCoverage>> functionsByNameAndLine = new HashMap<>();
			for (ReportParser.FunctionCoverage function : functions) {
				String key = function.name() + ":" + function.signatureLine();
				functionsByNameAndLine.computeIfAbsent(key, k -> new ArrayList<>()).add(function);
			}
			for (var entry : functionsByNameAndLine.entrySet()) {
				int signatureLine = Integer.parseInt(entry.getKey().split(":")[1]);
				List<ReportParser.FunctionCoverage> lineFunctions = entry.getValue();
				CoverageAnnotation annotation = createFunctionAnnotation(signatureLine, lineFunctions);
				addAnnotationToModel(model, editor, signatureLine, annotation);
			}
		}
	}

	private CoverageAnnotation createFunctionAnnotation(int signatureLine,
			List<ReportParser.FunctionCoverage> lineFunctions) {
		boolean anyCovered = lineFunctions.stream().anyMatch(f -> f.executionCount() > 0);
		boolean allCovered = lineFunctions.stream().allMatch(f -> f.executionCount() > 0);
		ReportParser.FunctionCoverage representativeFunction = lineFunctions.get(0);
		String type;
		String message = String.format("Function %s at line %d: ", representativeFunction.name(), signatureLine);
		if (allCovered) {
			type = CoverageAnnotation.TYPE_COVERED_FUNCTION;
			message += "Covered";
		} else if (!anyCovered) {
			type = CoverageAnnotation.TYPE_NOT_COVERED_FUNCTION;
			message += "Not covered";
		} else {
			type = CoverageAnnotation.TYPE_PARTIALLY_COVERED_FUNCTION;
			message += "Partially covered";
		}
		return new CoverageAnnotation(type, message);
	}

	private void addAnnotationToModel(IAnnotationModel model, ITextEditor editor, int lineNumber,
			CoverageAnnotation annotation) {
		try {
			IDocumentProvider provider = editor.getDocumentProvider();
			IEditorInput input = editor.getEditorInput();
			int offset = provider.getDocument(input).getLineOffset(lineNumber - 1);
			int length = provider.getDocument(input).getLineLength(lineNumber - 1);
			model.addAnnotation(annotation, new Position(offset, length));
		} catch (org.eclipse.jface.text.BadLocationException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("Failed to apply annotation due to bad location at line %d", lineNumber), e));
		}
	}

	public void updateOpenEditors() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null) {
			return;
		}

		for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
			if (window == null) {
				continue;
			}
			for (IWorkbenchPage page : window.getPages()) {
				if (page == null) {
					continue;
				}
				for (IEditorReference ref : page.getEditorReferences()) {
					IEditorPart editor = ref.getEditor(true);
					if (editor instanceof ITextEditor) {
						updateAnnotations((ITextEditor) editor);
					}
				}
			}
		}
	}

	public synchronized void createCoverageMarkers(IFile file) {
		if (dataManager.getCoverageData() == null) {
			return;
		}

		String filePath = file.getFullPath().toString();
		if (!isFileInAnalysisScope(filePath)) {
			clearMarkers(file, filePath);
			return;
		}

		try {
			file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
			if (currentMetric.equals(Messages.CoverageResultView_1)) {
				createLineCoverageMarkers(file, filePath);
			} else if (currentMetric.equals(Messages.CoverageResultView_2)) {
				createBranchCoverageMarkers(file, filePath);
			} else if (currentMetric.equals(Messages.CoverageResultView_3)) {
				createFunctionCoverageMarkers(file, filePath);
			}
		} catch (CoreException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("Failed to create coverage markers for file: %s", filePath), e));
		}
	}

	private void createLineCoverageMarkers(IFile file, String filePath) throws CoreException {
		List<ReportParser.LineCoverage> lines = CoverageUtils.findCoverageForFile(filePath,
				dataManager.getCoverageData());
		List<ReportParser.BranchCoverage> branches = CoverageUtils.findBranchCoverageForFile(filePath,
				dataManager.getBranchCoverage());
		if (lines != null && !lines.isEmpty()) {
			for (ReportParser.LineCoverage line : lines) {
				String message;
				int severity;
				if (line.executionCount() == 0) {
					message = "Line " + line.lineNumber() + ": Not covered";
					severity = IMarker.SEVERITY_WARNING;
				} else {
					boolean hasBranches = false;
					boolean allBranchesCovered = true;
					boolean anyBranchCovered = false;
					if (branches != null) {
						for (ReportParser.BranchCoverage branch : branches) {
							if (branch.lineNumber() == line.lineNumber()) {
								hasBranches = true;
								if (branch.covered()) {
									anyBranchCovered = true;
								} else {
									allBranchesCovered = false;
								}
							}
						}
					}
					if (hasBranches && anyBranchCovered && !allBranchesCovered) {
						message = "Line " + line.lineNumber() + ": Partially covered";
						severity = IMarker.SEVERITY_WARNING;
					} else {
						message = "Line " + line.lineNumber() + ": Covered";
						severity = IMarker.SEVERITY_INFO;
					}
				}
				createMarker(file, line.lineNumber(), message, severity);
			}
		}
	}

	private void createBranchCoverageMarkers(IFile file, String filePath) throws CoreException {
		List<ReportParser.BranchCoverage> branches = CoverageUtils.findBranchCoverageForFile(filePath,
				dataManager.getBranchCoverage());
		if (branches != null && !branches.isEmpty()) {
			HashMap<Integer, List<ReportParser.BranchCoverage>> branchesByLine = groupBranchesByLine(branches);
			for (Map.Entry<Integer, List<ReportParser.BranchCoverage>> entry : branchesByLine.entrySet()) {
				int lineNumber = entry.getKey();
				List<ReportParser.BranchCoverage> lineBranches = entry.getValue();
				boolean anyCovered = false;
				boolean allCovered = true;
				for (ReportParser.BranchCoverage branch : lineBranches) {
					if (branch.covered()) {
						anyCovered = true;
					} else {
						allCovered = false;
					}
				}
				String message;
				int severity;
				if (allCovered) {
					message = "Branch at line " + lineNumber + ": Covered";
					severity = IMarker.SEVERITY_INFO;
				} else if (!anyCovered) {
					message = "Branch at line " + lineNumber + ": Not covered";
					severity = IMarker.SEVERITY_WARNING;
				} else {
					message = "Branch at line " + lineNumber + ": Partially covered";
					severity = IMarker.SEVERITY_WARNING;
				}
				createMarker(file, lineNumber, message, severity);
			}
		}
	}

	private void createFunctionCoverageMarkers(IFile file, String filePath) throws CoreException {
		List<ReportParser.FunctionCoverage> functions = CoverageUtils.findFunctionCoverageForFile(filePath,
				dataManager.getAnnotationFunctionCoverage());
		if (functions != null && !functions.isEmpty()) {
			Map<String, List<ReportParser.FunctionCoverage>> functionsByNameAndLine = new HashMap<>();
			for (ReportParser.FunctionCoverage function : functions) {
				String key = function.name() + ":" + function.signatureLine();
				functionsByNameAndLine.computeIfAbsent(key, k -> new ArrayList<>()).add(function);
			}
			for (Map.Entry<String, List<ReportParser.FunctionCoverage>> entry : functionsByNameAndLine.entrySet()) {
				int signatureLine = Integer.parseInt(entry.getKey().split(":")[1]);
				List<ReportParser.FunctionCoverage> lineFunctions = entry.getValue();
				boolean anyCovered = lineFunctions.stream().anyMatch(f -> f.executionCount() > 0);
				boolean allCovered = lineFunctions.stream().allMatch(f -> f.executionCount() > 0);
				String functionName = lineFunctions.get(0).name();
				String message;
				int severity;
				if (allCovered) {
					message = "Function " + functionName + " at line " + signatureLine + ": Covered";
					severity = IMarker.SEVERITY_INFO;
				} else if (!anyCovered) {
					message = "Function " + functionName + " at line " + signatureLine + ": Not covered";
					severity = IMarker.SEVERITY_WARNING;
				} else {
					message = "Function " + functionName + " at line " + signatureLine + ": Partially covered";
					severity = IMarker.SEVERITY_WARNING;
				}
				createMarker(file, signatureLine, message, severity);
			}
		}
	}

	private void createMarker(IFile file, int lineNumber, String message, int severity) throws CoreException {
		IMarker marker = file.createMarker(MARKER_TYPE);
		marker.setAttribute(IMarker.MESSAGE, message);
		marker.setAttribute(IMarker.SEVERITY, severity);
		marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
	}

	private void clearMarkers(IFile file, String filePath) {
		try {
			file.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			LOGGER.log(new Status(IStatus.ERROR, PLUGIN_ID,
					String.format("Failed to delete coverage markers for excluded file: %s", filePath), e));
		}
	}
}