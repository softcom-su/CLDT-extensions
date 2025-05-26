package su.softcom.cldt.testing.core;

import org.eclipse.jface.preference.IPreferenceStore;

public class CoveragePreferenceSettings {
	private static final String OPEN_VIEW_AUTO = "coverage.open_view_auto";
	private static final String GENERATE_REPORT = "coverage.generate_report";
	private static final String CLEAN_PROFILE_DATA = "coverage.clean_profile_data";
	private static final String INCLUDES = "coverage.includes";
	private static final String EXCLUDES = "coverage.excludes";
	private static final String LLVM_COV_PATH = "coverage.llvm_cov_path";
	private static final String LLVM_PROFDATA_PATH = "coverage.llvm_profdata_path";
	private static final String COVERED_TEXT = "coverage.covered.text";
	private static final String COVERED_HIGHLIGHT = "coverage.covered.highlight";
	private static final String COVERED_OVERVIEW = "coverage.covered.overview";
	private static final String COVERED_COLOR = "coverage.covered.color";
	private static final String NOT_COVERED_TEXT = "coverage.not_covered.text";
	private static final String NOT_COVERED_HIGHLIGHT = "coverage.not_covered.highlight";
	private static final String NOT_COVERED_OVERVIEW = "coverage.not_covered.overview";
	private static final String NOT_COVERED_COLOR = "coverage.not_covered.color";

	private static final boolean DEFAULT_OPEN_VIEW_AUTO = true;
	private static final boolean DEFAULT_GENERATE_REPORT = true;
	private static final boolean DEFAULT_CLEAN_PROFILE_DATA = false;
	private static final String DEFAULT_INCLUDES = "*";
	private static final String DEFAULT_EXCLUDES = "";
	private static final String DEFAULT_LLVM_COV_PATH = "/usr/bin/llvm-cov";
	private static final String DEFAULT_LLVM_PROFDATA_PATH = "/usr/bin/llvm-profdata";
	private static final boolean DEFAULT_COVERED_TEXT = true;
	private static final boolean DEFAULT_COVERED_HIGHLIGHT = true;
	private static final boolean DEFAULT_COVERED_OVERVIEW = true;
	private static final String DEFAULT_COVERED_COLOR = "152,251,152";
	private static final boolean DEFAULT_NOT_COVERED_TEXT = true;
	private static final boolean DEFAULT_NOT_COVERED_HIGHLIGHT = true;
	private static final boolean DEFAULT_NOT_COVERED_OVERVIEW = true;
	private static final String DEFAULT_NOT_COVERED_COLOR = "240,128,128";

	private static final String ERROR_ACTIVATOR_NOT_INITIALIZED = "Activator is not initialized";

	private CoveragePreferenceSettings() {
	}

	private static IPreferenceStore getStore() {
		Activator activator = Activator.getDefault();
		if (activator == null) {
			throw new IllegalStateException(ERROR_ACTIVATOR_NOT_INITIALIZED);
		}
		return activator.getPreferenceStore();
	}

	public static boolean isOpenViewAuto() {
		return getStore().getBoolean(OPEN_VIEW_AUTO);
	}

	public static void setOpenViewAuto(boolean value) {
		getStore().setValue(OPEN_VIEW_AUTO, value);
	}

	public static boolean isGenerateReportAfterBuild() {
		return getStore().getBoolean(GENERATE_REPORT);
	}

	public static void setGenerateReportAfterBuild(boolean value) {
		getStore().setValue(GENERATE_REPORT, value);
	}

	public static boolean isCleanProfileData() {
		return getStore().getBoolean(CLEAN_PROFILE_DATA);
	}

	public static void setCleanProfileData(boolean value) {
		getStore().setValue(CLEAN_PROFILE_DATA, value);
	}

	public static String getIncludes() {
		return getStore().getString(INCLUDES);
	}

	public static void setIncludes(String value) {
		getStore().setValue(INCLUDES, value);
	}

	public static String getExcludes() {
		return getStore().getString(EXCLUDES);
	}

	public static void setExcludes(String value) {
		getStore().setValue(EXCLUDES, value);
	}

	public static String getLlvmCovPath() {
		return getStore().getString(LLVM_COV_PATH);
	}

	public static void setLlvmCovPath(String value) {
		getStore().setValue(LLVM_COV_PATH, value);
	}

	public static String getLlvmProfdataPath() {
		return getStore().getString(LLVM_PROFDATA_PATH);
	}

	public static void setLlvmProfdataPath(String value) {
		getStore().setValue(LLVM_PROFDATA_PATH, value);
	}

	public static boolean isCoveredText() {
		return getStore().getBoolean(COVERED_TEXT);
	}

	public static void setCoveredText(boolean value) {
		getStore().setValue(COVERED_TEXT, value);
	}

	public static boolean isCoveredHighlight() {
		return getStore().getBoolean(COVERED_HIGHLIGHT);
	}

	public static void setCoveredHighlight(boolean value) {
		getStore().setValue(COVERED_HIGHLIGHT, value);
	}

	public static boolean isCoveredOverview() {
		return getStore().getBoolean(COVERED_OVERVIEW);
	}

	public static void setCoveredOverview(boolean value) {
		getStore().setValue(COVERED_OVERVIEW, value);
	}

	public static String getCoveredColor() {
		return getStore().getString(COVERED_COLOR);
	}

	public static void setCoveredColor(String value) {
		getStore().setValue(COVERED_COLOR, value);
	}

	public static boolean isNotCoveredText() {
		return getStore().getBoolean(NOT_COVERED_TEXT);
	}

	public static void setNotCoveredText(boolean value) {
		getStore().setValue(NOT_COVERED_TEXT, value);
	}

	public static boolean isNotCoveredHighlight() {
		return getStore().getBoolean(NOT_COVERED_HIGHLIGHT);
	}

	public static void setNotCoveredHighlight(boolean value) {
		getStore().setValue(NOT_COVERED_HIGHLIGHT, value);
	}

	public static boolean isNotCoveredOverview() {
		return getStore().getBoolean(NOT_COVERED_OVERVIEW);
	}

	public static void setNotCoveredOverview(boolean value) {
		getStore().setValue(NOT_COVERED_OVERVIEW, value);
	}

	public static String getNotCoveredColor() {
		return getStore().getString(NOT_COVERED_COLOR);
	}

	public static void setNotCoveredColor(String value) {
		getStore().setValue(NOT_COVERED_COLOR, value);
	}

	public static void initializeDefaults() {
		IPreferenceStore store = getStore();
		store.setDefault(OPEN_VIEW_AUTO, DEFAULT_OPEN_VIEW_AUTO);
		store.setDefault(GENERATE_REPORT, DEFAULT_GENERATE_REPORT);
		store.setDefault(CLEAN_PROFILE_DATA, DEFAULT_CLEAN_PROFILE_DATA);
		store.setDefault(INCLUDES, DEFAULT_INCLUDES);
		store.setDefault(EXCLUDES, DEFAULT_EXCLUDES);
		store.setDefault(LLVM_COV_PATH, DEFAULT_LLVM_COV_PATH);
		store.setDefault(LLVM_PROFDATA_PATH, DEFAULT_LLVM_PROFDATA_PATH);
		store.setDefault(COVERED_TEXT, DEFAULT_COVERED_TEXT);
		store.setDefault(COVERED_HIGHLIGHT, DEFAULT_COVERED_HIGHLIGHT);
		store.setDefault(COVERED_OVERVIEW, DEFAULT_COVERED_OVERVIEW);
		store.setDefault(COVERED_COLOR, DEFAULT_COVERED_COLOR);
		store.setDefault(NOT_COVERED_TEXT, DEFAULT_NOT_COVERED_TEXT);
		store.setDefault(NOT_COVERED_HIGHLIGHT, DEFAULT_NOT_COVERED_HIGHLIGHT);
		store.setDefault(NOT_COVERED_OVERVIEW, DEFAULT_NOT_COVERED_OVERVIEW);
		store.setDefault(NOT_COVERED_COLOR, DEFAULT_NOT_COVERED_COLOR);
	}
}