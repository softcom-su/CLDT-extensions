package su.softcom.cldt.qt.common;

import org.eclipse.osgi.util.NLS;

/**
 * Messages for Qt bundle
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = Messages.class.getPackageName() + ".messages"; //$NON-NLS-1$

	public static String qtImportPageTitleName;
	public static String qtImportPageDescription;
	public static String qtImportPageProjectGroupText;
	public static String qtImportPageProjectNameLabel;
	public static String qtImportPageProjectProjectRootDirLabel;
	public static String qtImportPageBrowseButtonText;
	public static String qtImportPageBuildSettingsText;
	public static String qtImportPageSetDefaultBuildDirectory;
	public static String qtImportPageBuildLabelText;
	public static String qtImportPageErrorBlankBuildDirectory;
	public static String qtImportPageErrorDirectoryDoesNotExist;
	public static String qtImportPageErrorNoCMakeLists;
	public static String qtImportPageErrorBlankProjectName;
	public static String qtImportPageErrorProjectAlreadyExists;
	
	public static String qtPropertyPageModules;
	public static String qtPropertyPageSettings;
	public static String qtPropertyPageUseAutoMoc;
	public static String qtPropertyPageUseAutoRcc;
	public static String qtPropertyPageUseAutoUic;
	public static String qtPropertyPageModulesList;
	public static String qtPropertyPageErrorNoModulesSelected;

	public static String qtImportExtra;

	public static String qtImportProjectPro;
	
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
