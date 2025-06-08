package su.softcom.cldt.testing.ui;

import org.eclipse.jface.text.source.Annotation;

public class CoverageAnnotation extends Annotation {
	private static final String BASE_TYPE = "su.softcom.cldt.testing.coverage.";
	public static final String TYPE_COVERED_LINE = BASE_TYPE + "covered.line";
	public static final String TYPE_NOT_COVERED_LINE = BASE_TYPE + "not_covered.line";
	public static final String TYPE_PARTIALLY_COVERED_LINE = BASE_TYPE + "partially_covered.line";
	public static final String TYPE_COVERED_BRANCH = BASE_TYPE + "covered.branch";
	public static final String TYPE_NOT_COVERED_BRANCH = BASE_TYPE + "not_covered.branch";
	public static final String TYPE_PARTIALLY_COVERED_BRANCH = BASE_TYPE + "partially_covered.branch";
	public static final String TYPE_COVERED_FUNCTION = BASE_TYPE + "covered.function";
	public static final String TYPE_NOT_COVERED_FUNCTION = BASE_TYPE + "not_covered.function";
	public static final String TYPE_PARTIALLY_COVERED_FUNCTION = BASE_TYPE + "partially_covered.function";

	public CoverageAnnotation(String type, String text) {
		super(type, false, text);
	}
}