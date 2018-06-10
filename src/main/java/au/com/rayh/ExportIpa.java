package au.com.rayh;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExportIpa extends Builder implements SimpleBuildStep {
    public final Boolean buildIpa;
    public final Boolean generateArchive;
    public final Boolean noConsoleLog;
    public final String logfileOutputDirectory;
    public final Boolean cleanBeforeBuild;
    public final Boolean cleanTestReports;
    public final String configuration;
    public final String target;
    public final String sdk;
    public final String xcodeProjectPath;
    public final String xcodeProjectFile;
    public final String xcodebuildArguments;
    public final String cfBundleVersionValue;
    public final String cfBundleShortVersionStringValue;
    public final Boolean unlockKeychain;
    public final String keychainName;
    public final String keychainPath;
    public final String keychainPwd;
    public final String symRoot;
    public final String xcodeWorkspaceFile;
    public final String xcodeSchema;
    public final String archiveDir;
    public final String developmentTeamName;
    public final String developmentTeamID;
    public final Boolean allowFailingBuildResults;
    public final String ipaName;
    public final Boolean provideApplicationVersion;
    public final String ipaOutputDirectory;
    public final Boolean changeBundleID;
    public final String bundleID;
    public final String bundleIDInfoPlistPath;
    public final Boolean interpretTargetAsRegEx;
    public final String ipaExportMethod;
    public final String signingMethod;
    public final ArrayList<ProvisioningProfile> provisioningProfiles;
    public final String xcodeName;
    public final Boolean uploadBitcode;
    public final Boolean uploadSymbols;
    public final Boolean compileBitcode;
    public final String thinning;
    public final Boolean packResourcesAsset;
    public final String resourcesAssetURL;
    public final String appURL;
    public final String displayImageURL;
    public final String fullSizeImageURL;
    public final String assetPackManifestURL;

    @DataBoundConstructor
    public ExportIpa(String xcodeProjectPath, String xcodeProjectFile,
                Boolean unlockKeychain, String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
                String xcodeSchema, String archiveDir, String developmentTeamName, String developmentTeamID,
                String ipaName, String ipaOutputDirectory,
                String ipaExportMethod,
                String signingMethod, ArrayList<ProvisioningProfile> provisioningProfiles, String xcodeName,
		Boolean uploadBitcode, Boolean uploadSymbols, Boolean compileBitcode, String thinning,
		Boolean packResourcesAsset, String resourcesAssetURL,
		String appURL, String displayImageURL, String fullSizeImageURL,
		String assetPackManifestURL) {
        this.buildIpa = true;
        this.generateArchive = false;
        this.noConsoleLog = true;
        this.logfileOutputDirectory = null;
        this.sdk = null;
        this.target = null;
        this.cleanBeforeBuild = false;
        this.cleanTestReports = false;
        this.configuration = null;
        this.xcodeProjectPath = xcodeProjectPath;
        this.xcodeProjectFile = xcodeProjectFile;
        this.xcodebuildArguments = null;
        this.keychainName = keychainName;
        this.xcodeWorkspaceFile = xcodeWorkspaceFile;
        this.xcodeSchema = xcodeSchema;
        this.developmentTeamName = developmentTeamName;
        this.developmentTeamID = developmentTeamID;
        this.cfBundleVersionValue = null;
        this.cfBundleShortVersionStringValue = null;
        this.unlockKeychain = unlockKeychain;
        this.keychainPath = keychainPath;
        this.keychainPwd = keychainPwd;
        this.symRoot = symRoot;
        this.archiveDir = archiveDir;
        this.allowFailingBuildResults = false;
        this.ipaName = ipaName;
        this.ipaOutputDirectory = ipaOutputDirectory;
        this.provideApplicationVersion = false;
        this.changeBundleID = false;
        this.bundleID = null;
        this.bundleIDInfoPlistPath = null;
        this.interpretTargetAsRegEx = false;
        this.ipaExportMethod = ipaExportMethod;
        this.signingMethod = signingMethod;
        this.provisioningProfiles = provisioningProfiles;
	this.xcodeName = xcodeName;
        this.uploadBitcode = uploadBitcode;
        this.uploadSymbols = uploadSymbols;
        this.compileBitcode = compileBitcode;
        this.thinning = thinning;
        this.packResourcesAsset = packResourcesAsset;
        this.resourcesAssetURL = resourcesAssetURL;
        this.appURL = appURL;
        this.displayImageURL = displayImageURL;
        this.fullSizeImageURL = fullSizeImageURL;
        this.assetPackManifestURL = assetPackManifestURL;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath filePath, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        _perform(build, filePath, launcher, build.getEnvironment(listener), listener);
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    private boolean _perform(Run<?,?> build, FilePath filePath, Launcher launcher, EnvVars envs, TaskListener listener) throws InterruptedException, IOException {
	XCodeBuilder builder = new XCodeBuilder(buildIpa, generateArchive, noConsoleLog, logfileOutputDirectory, cleanBeforeBuild, cleanTestReports, configuration,
                target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
                cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
                keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
                xcodeSchema, archiveDir, developmentTeamName, developmentTeamID, allowFailingBuildResults,
                ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
                bundleIDInfoPlistPath, interpretTargetAsRegEx, ipaExportMethod, signingMethod, provisioningProfiles, xcodeName,
		uploadBitcode, uploadSymbols, compileBitcode, thinning,
		packResourcesAsset, resourcesAssetURL,
		appURL, displayImageURL, fullSizeImageURL, assetPackManifestURL);
		
	builder.setSkipBuildStep(true);
	builder.perform(build, filePath, launcher, listener);
	return true;
    }

    public GlobalConfigurationImpl getGlobalConfiguration() {
        return getDescriptor().getGlobalConfiguration();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    @Symbol("exportIpa")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        GlobalConfigurationImpl globalConfiguration;

        @Override
        public String getDisplayName() {
            return Messages.ExportIpa_ExportIpa();
        }

        public DescriptorImpl() {
            load();
        }

        @SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
        @Inject
        void setGlobalConfiguration(GlobalConfigurationImpl c) {
            this.globalConfiguration = c;
        }

        public GlobalConfigurationImpl getGlobalConfiguration() {
            return globalConfiguration;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getUUID() {
            return "" + UUID.randomUUID().getMostSignificantBits();
        }

        public FormValidation doCheckXcodeSchema(@QueryParameter String value) {
            if ( value.isEmpty() ) {
                return FormValidation.warning(Messages.XCodeBuilder_NeedSchema());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckResourcesAssetURL(@QueryParameter String value, @QueryParameter Boolean packResourcesAsset) {
            if ( StringUtils.isEmpty(value) && !packResourcesAsset ) {
                return FormValidation.error(Messages.XCodeBuilder_NeedOnDemandResourcesURL());
            }
            return FormValidation.ok();
        }
    }
}
