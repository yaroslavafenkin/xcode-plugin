/*
 * The MIT License
 *
 * Copyright (c) 2011 Ray Yamamoto Hilton
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package au.com.rayh;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.CopyOnWriteList;
import hudson.util.QuotedStringTokenizer;
import hudson.plugins.xcode.XcodeInstallation;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.io.ObjectStreamException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSArray;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

/**
 * @author Ray Hilton
 */
@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
public class XCodeBuilder extends Builder implements SimpleBuildStep {

    private static final int SIGTERM = 143;
    private static final String DEVELOPMENT_ENV = "Development";
    private static final String PRODUCTION_ENV = "Production";
    private static final String DEV_SIGNING_CERTIFICATE_SELECTOR = "iOS Developer";
    private static final String DIST_SIGNING_CERTIFICATE_SELECTOR = "iOS Distribution";

    /**
     * @since 1.0
     */
    public final Boolean cleanBeforeBuild;
    /**
     * @since 1.3
     */
    public final Boolean cleanTestReports;
    /**
     * @since 1.0
     */
    public final String configuration;
    /**
     * @since 1.0
     */
    public final String target;
    /**
     * @since 1.0
     */
    public final String sdk;
    /**
     * @since 1.1
     */
    public final String symRoot;
    /**
     * @since 1.2
     */
    public final String buildDir;
    /**
     * @since 1.0
     */
    public final String xcodeProjectPath;
    /**
     * @since 1.0
     */
    public final String xcodeProjectFile;
    /**
     * @since 1.3
     */
    public final String xcodebuildArguments;
    /**
     * @since 1.2
     */
    public final String xcodeSchema;
    /**
     * @since 1.2
     */
    public final String xcodeWorkspaceFile;
    /**
     * @since 1.0
     */
    public final String cfBundleVersionValue;
    /**
     * @since 1.0
     */
    public final String cfBundleShortVersionStringValue;
    /**
     * @since 1.0
     */
    public final Boolean buildIpa;
    /**
     * @since 1.4.12
     */
    public final String ipaExportMethod;
    /**
     * @since 1.0
     */
    public final Boolean generateArchive;
    /**
     * @since 2.0.1
     */
    public final Boolean noConsoleLog;
    /**
     * @since 2.0.1
     */
    public final String logfileOutputDirectory;
    /**
     * @since 1.5
     **/
    public final Boolean unlockKeychain;
    /**
     * @since 1.4
     */
    public final String keychainName;
    /**
     * @since 1.0
     */
    public final String keychainPath;
    /**
     * @since 1.0
     */
    public final String keychainPwd;
    /**
     * @since 1.4.12
     */
    public final String developmentTeamName;
    /**
     * @since 1.4.12
     */
    public final String developmentTeamID;
    /**
     * @since 1.4
     */
    public final Boolean allowFailingBuildResults;
    /**
     * @since 1.4
     */
    public final String ipaName;
    /**
     * @since 1.4
     */
    public final String ipaOutputDirectory;
    /**
     * @since 1.4
     */
    public Boolean provideApplicationVersion;
    /**
     * @since 1.4
     */
    public final Boolean changeBundleID;
    /**
     * @since 1.4
     */
    public final String bundleID;
    /**
     * @since 1.4
     */
    public final String bundleIDInfoPlistPath;
    /**
     * @since 1.4
     */
    public final Boolean interpretTargetAsRegEx;
    /**
     * @deprecated 2.0.3
     *
    public final String ipaManifestPlistUrl;
     */
    /**
     * @since 2.0.1
     */
    public final String signingMethod;
    /**
     * @since 2.0.1
     */
    public ArrayList<ProvisioningProfile> provisioningProfiles = new ArrayList<>();
    /*
     * @since 2.0.3
     */
    public final String xcodeName;
    /*
     * @since 2.0.3
     */
    public final Boolean uploadBitcode;
    /*
     * @since 2.0.3
     */
    public final Boolean uploadSymbols;
    /*
     * @since 2.0.3
     */
    public final Boolean compileBitcode;
    /*
     * @since 2.0.3
     */
    public final String thinning;
    /*
     * @since 2.0.3
     */
    public final Boolean embedOnDemandResourcesAssetPacksInBundle;
    /*
     * @since 2.0.3
     */
    public final String onDemandResourcesAssetPacksBaseURL;
    /*
     * @since 2.0.3
     */
    public final String appURL;
    /*
     * @since 2.0.3
     */
    public final String displayImageURL;
    /*
     * @since 2.0.3
     */
    public final String fullSizeImageURL;
    /*
     * @since 2.0.3
     */
    public final String assetPackManifestURL;

    /**
     * @since 2.0.3
     */
    Boolean skipBuildStep;

    public void setSkipBuildStep(Boolean skipBuildStep) {
        this.skipBuildStep = skipBuildStep;
    }

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean noConsoleLog, String logfileOutputDirectory, Boolean cleanBeforeBuild, 
    		Boolean cleanTestReports, String configuration,
    		String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
    		String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
    		String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
    		String xcodeSchema, String buildDir, String developmentTeamName, String developmentTeamID, Boolean allowFailingBuildResults,
    		String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
    		String bundleIDInfoPlistPath, Boolean interpretTargetAsRegEx, String ipaExportMethod,
		String signingMethod, ArrayList<ProvisioningProfile> provisioningProfiles, String xcodeName,
		Boolean uploadBitcode, Boolean uploadSymbols, Boolean compileBitcode, String thinning,
		Boolean embedOnDemandResourcesAssetPacksInBundle, String onDemandResourcesAssetPacksBaseURL,
		String appURL, String displayImageURL, String fullSizeImageURL,
		String assetPackManifestURL) {

        this.buildIpa = buildIpa;
        this.generateArchive = generateArchive;
        this.noConsoleLog = noConsoleLog;
        this.logfileOutputDirectory = logfileOutputDirectory;
        this.sdk = sdk;
        this.target = target;
        this.cleanBeforeBuild = cleanBeforeBuild;
        this.cleanTestReports = cleanTestReports;
        this.configuration = configuration;
        this.xcodeProjectPath = xcodeProjectPath;
        this.xcodeProjectFile = xcodeProjectFile;
        this.xcodebuildArguments = xcodebuildArguments;
        this.keychainName = keychainName;
        this.xcodeWorkspaceFile = xcodeWorkspaceFile;
        this.xcodeSchema = xcodeSchema;
        this.developmentTeamName = developmentTeamName;
        this.developmentTeamID = developmentTeamID;
        this.cfBundleVersionValue = cfBundleVersionValue;
        this.cfBundleShortVersionStringValue = cfBundleShortVersionStringValue;
        this.unlockKeychain = unlockKeychain;
        this.keychainPath = keychainPath;
        this.keychainPwd = keychainPwd;
        this.symRoot = symRoot;
        this.buildDir = buildDir;
        this.allowFailingBuildResults = allowFailingBuildResults;
        this.ipaName = ipaName;
        this.ipaOutputDirectory = ipaOutputDirectory;
        this.provideApplicationVersion = provideApplicationVersion;
        this.changeBundleID = changeBundleID;
        this.bundleID = bundleID;
        this.bundleIDInfoPlistPath = bundleIDInfoPlistPath;
        this.interpretTargetAsRegEx = interpretTargetAsRegEx;
        //this.ipaManifestPlistUrl = null;
        this.ipaExportMethod = ipaExportMethod;
        this.signingMethod = signingMethod;
        this.provisioningProfiles = provisioningProfiles;
	this.xcodeName = xcodeName;
	this.uploadBitcode = uploadBitcode;
	this.uploadSymbols = uploadSymbols;
	this.compileBitcode = compileBitcode;
	this.thinning = thinning;
	this.embedOnDemandResourcesAssetPacksInBundle = embedOnDemandResourcesAssetPacksInBundle;
	this.onDemandResourcesAssetPacksBaseURL = onDemandResourcesAssetPacksBaseURL;

	this.appURL = appURL;
	this.displayImageURL = displayImageURL;
	this.fullSizeImageURL = fullSizeImageURL;
	this.assetPackManifestURL = assetPackManifestURL;

	this.skipBuildStep = false;
    }

    @Deprecated
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean noConsoleLog, String logfileOutputDirectory, Boolean cleanBeforeBuild,
			Boolean cleanTestReports, String configuration,
			String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
			String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
			String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
			String xcodeSchema, String buildDir, String developmentTeamName, String developmentTeamID, Boolean allowFailingBuildResults,
			String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
			String bundleIDInfoPlistPath, String ipaManifestPlistUrl, Boolean interpretTargetAsRegEx, String ipaExportMethod,
			String signingMethod, ArrayList<ProvisioningProfile> provisioningProfiles, String xcodeName) {
	this(buildIpa, generateArchive, noConsoleLog, logfileOutputDirectory, cleanBeforeBuild, cleanTestReports, configuration,
		target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
		cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
		keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
		xcodeSchema, buildDir, developmentTeamName, developmentTeamID, allowFailingBuildResults,
		ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
		bundleIDInfoPlistPath, interpretTargetAsRegEx, ipaExportMethod,
		signingMethod, provisioningProfiles, xcodeName, true, true, true, null, false, null, ipaManifestPlistUrl, null, null, null);
    }

    @Deprecated
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean noConsoleLog, String logfileOutputDirectory, Boolean cleanBeforeBuild,
                        Boolean cleanTestReports, String configuration,
                        String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
                        String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
                        String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
                        String xcodeSchema, String buildDir, String developmentTeamName, String developmentTeamID, Boolean allowFailingBuildResults,
                        String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
                        String bundleIDInfoPlistPath, String ipaManifestPlistUrl, Boolean interpretTargetAsRegEx, String ipaExportMethod,
                        Boolean manualSigning, ArrayList<ProvisioningProfile>provisioningProfiles) {
        this(buildIpa, generateArchive, noConsoleLog, logfileOutputDirectory, cleanBeforeBuild, cleanTestReports, configuration,
                target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
                cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
                keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
                xcodeSchema, buildDir, developmentTeamName, developmentTeamID, allowFailingBuildResults,
                ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
                bundleIDInfoPlistPath, ipaManifestPlistUrl, interpretTargetAsRegEx, ipaExportMethod, (manualSigning ? "manual" : "automatic"), provisioningProfiles, null);
    }

    @Deprecated
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean noConsoleLog, String logfileOutputDirectory, Boolean cleanBeforeBuild,
                        Boolean cleanTestReports, String configuration,
                        String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
                        String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
                        String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
                        String xcodeSchema, String buildDir, String developmentTeamName, String developmentTeamID, Boolean allowFailingBuildResults,
                        String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
                        String bundleIDInfoPlistPath, String ipaManifestPlistUrl, Boolean interpretTargetAsRegEx, String ipaExportMethod) {
        this(buildIpa, generateArchive, noConsoleLog, logfileOutputDirectory, cleanBeforeBuild, cleanTestReports, configuration,
                target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
                cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
                keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
                xcodeSchema, buildDir, developmentTeamName, developmentTeamID, allowFailingBuildResults,
                ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
                bundleIDInfoPlistPath, ipaManifestPlistUrl, interpretTargetAsRegEx, ipaExportMethod, true, null);
    }

    @Deprecated
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean cleanBeforeBuild, Boolean cleanTestReports, String configuration,
                        String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
                        String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
                        String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
                        String xcodeSchema, String buildDir, String developmentTeamName, String developmentTeamID, Boolean allowFailingBuildResults,
                        String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
                        String bundleIDInfoPlistPath, String ipaManifestPlistUrl, Boolean interpretTargetAsRegEx, String ipaExportMethod) {

        this(buildIpa, generateArchive, false, null, cleanBeforeBuild, cleanTestReports, configuration,
                target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
                cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
                keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
                xcodeSchema, buildDir, developmentTeamName, developmentTeamID, allowFailingBuildResults,
                ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
                bundleIDInfoPlistPath, ipaManifestPlistUrl, interpretTargetAsRegEx, ipaExportMethod);
    }

    @Deprecated
    public XCodeBuilder(Boolean buildIpa, Boolean generateArchive, Boolean cleanBeforeBuild, Boolean cleanTestReports, String configuration,
                        String target, String sdk, String xcodeProjectPath, String xcodeProjectFile, String xcodebuildArguments,
                        String embeddedProfileFile, String cfBundleVersionValue, String cfBundleShortVersionStringValue, Boolean unlockKeychain,
                        String keychainName, String keychainPath, String keychainPwd, String symRoot, String xcodeWorkspaceFile,
                        String xcodeSchema, String configurationBuildDir, String codeSigningIdentity, Boolean allowFailingBuildResults,
                        String ipaName, Boolean provideApplicationVersion, String ipaOutputDirectory, Boolean changeBundleID, String bundleID,
                        String bundleIDInfoPlistPath, String ipaManifestPlistUrl, Boolean interpretTargetAsRegEx, Boolean signIpaOnXcrun) {

        this(buildIpa, generateArchive, false, null, cleanBeforeBuild, cleanTestReports, configuration,
                target, sdk, xcodeProjectPath, xcodeProjectFile, xcodebuildArguments,
                cfBundleVersionValue, cfBundleShortVersionStringValue, unlockKeychain,
                keychainName, keychainPath, keychainPwd, symRoot, xcodeWorkspaceFile,
                xcodeSchema, configurationBuildDir, "", "", allowFailingBuildResults,
                ipaName, provideApplicationVersion, ipaOutputDirectory, changeBundleID, bundleID,
                bundleIDInfoPlistPath, ipaManifestPlistUrl, interpretTargetAsRegEx, "ad-hoc");
    }

    @SuppressWarnings("unused")
    private Object readResolve() throws ObjectStreamException {
        if (provideApplicationVersion == null) {
            if (!StringUtils.isEmpty(cfBundleVersionValue)
                || !StringUtils.isEmpty(cfBundleShortVersionStringValue)) {
                provideApplicationVersion = true;
            }
        }
        return this;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath filePath, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
		boolean result = _perform(build, filePath, launcher, build.getEnvironment(listener), listener);
		if (!result) {
		    throw new AbortException(Messages.XCodeBuilder_AbortXcodeBuildFailed());
        }
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		return _perform(build, build.getWorkspace(), launcher, build.getEnvironment(listener), listener);
	}

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    private boolean _perform(Run<?,?> build, FilePath projectRoot, Launcher launcher, EnvVars envs, TaskListener listener) throws InterruptedException, IOException {

        // check that the configured tools exist
        if (!new FilePath(projectRoot.getChannel(), getGlobalConfiguration().getXcodebuildPath()).exists()) {
            listener.fatalError(Messages.XCodeBuilder_xcodebuildNotFound(getGlobalConfiguration().getXcodebuildPath()));
            return false;
        }
        if (!new FilePath(projectRoot.getChannel(), getGlobalConfiguration().getAgvtoolPath()).exists()) {
            listener.fatalError(Messages.XCodeBuilder_avgtoolNotFound(getGlobalConfiguration().getAgvtoolPath()));
            return false;
        }

        // Start expanding all string variables in parameters
        // NOTE: we currently use variable shadowing to avoid having to rewrite all code (and break pull requests), this will be cleaned up at later stage.
        String configuration = envs.expand(this.configuration);
        String target = envs.expand(this.target);
        String sdk = envs.expand(this.sdk);
        String symRoot = envs.expand(this.symRoot);
        String buildDir = envs.expand(this.buildDir);
        String xcodeProjectPath = envs.expand(this.xcodeProjectPath);
        String xcodeProjectFile = envs.expand(this.xcodeProjectFile);
        String xcodebuildArguments = envs.expand(this.xcodebuildArguments);
        String xcodeSchema = envs.expand(this.xcodeSchema);
        String xcodeWorkspaceFile = envs.expand(this.xcodeWorkspaceFile);
        String cfBundleVersionValue = envs.expand(this.cfBundleVersionValue);
        String cfBundleShortVersionStringValue = envs.expand(this.cfBundleShortVersionStringValue);
        String ipaName = envs.expand(this.ipaName);
        String ipaOutputDirectory = envs.expand(this.ipaOutputDirectory);
        String bundleID = envs.expand(this.bundleID);
        String bundleIDInfoPlistPath = envs.expand(this.bundleIDInfoPlistPath);
        //String ipaManifestPlistUrl = envs.expand(this.ipaManifestPlistUrl);
        String ipaExportMethod = envs.expand(this.ipaExportMethod);
	String thinning = envs.expand(this.thinning);
	String onDemandResourcesAssetPacksBaseURL = envs.expand(this.onDemandResourcesAssetPacksBaseURL);
	String appURL = envs.expand(this.appURL);
	String displayImageURL = envs.expand(this.displayImageURL);
	String fullSizeImageURL = envs.expand(this.fullSizeImageURL);
	String assetPackManifestURL = envs.expand(this.assetPackManifestURL);
        // End expanding all string variables in parameters

        // Set the working directory
        if (!StringUtils.isEmpty(xcodeProjectPath)) {
            projectRoot = projectRoot.child(xcodeProjectPath);
        }
        listener.getLogger().println(Messages.XCodeBuilder_workingDir(projectRoot));

        if (!StringUtils.isEmpty(this.xcodeName)) {
            Jenkins jenkinsInstance = Jenkins.getInstance();
            XcodeInstallation.DescriptorImpl descriptor = (XcodeInstallation.DescriptorImpl)jenkinsInstance.getDescriptor(XcodeInstallation.class);
            XcodeInstallation[] installations = descriptor.getInstallations();
            if ( installations != null ) {
                for ( XcodeInstallation installation : installations ) {
                    if ( installation.getName().equals(this.xcodeName) ) {
                        envs.put("DEVELOPER_DIR", installation.getHome());
			listener.getLogger().println(Messages.XCodeBuilder_XcodeToolsDir(installation.getHome()));
                        break;
                    }
                }
            }
        }

        // Infer as best we can the build platform
        String buildPlatform = "iphoneos";
        if (!StringUtils.isEmpty(sdk)) {
            if (StringUtils.contains(sdk.toLowerCase(), "iphonesimulator")) {
                // Building for the simulator
                buildPlatform = "iphonesimulator";
            }
        }

        // Set the build directory and the symRoot
        //
        String symRootValue = null;
        if (!StringUtils.isEmpty(symRoot)) {
            try {
                // If not empty we use the Token Expansion to replace it
                // https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
                symRootValue = TokenMacro.expandAll(build, projectRoot, listener, symRoot).trim();
            } catch (MacroEvaluationException e) {
                listener.error(Messages.XCodeBuilder_symRootMacroError(e.getMessage()));
                return false;
            }
        }

        String buildDirValue = null;
        FilePath buildDirectory;
        if (!StringUtils.isEmpty(buildDir)) {
            try {
                buildDirValue = TokenMacro.expandAll(build, projectRoot, listener, buildDir).trim();
            } catch (MacroEvaluationException e) {
                listener.error(Messages.XCodeBuilder_buildDirMacroError(e.getMessage()));
                return false;
            }
        }

        if (buildDirValue != null) {
            // If there is a BUILD_DIR, that overrides any use of SYMROOT. Does not require the build platform and the configuration.
            buildDirectory = new FilePath(projectRoot.getChannel(), buildDirValue);
        } else if (symRootValue != null) {
            // If there is a SYMROOT specified, compute the build directory from that.
            buildDirectory = new FilePath(projectRoot.getChannel(), symRootValue).child(configuration + "-" + buildPlatform);
        } else {
            // Assume its a build for the handset, not the simulator.
            buildDirectory = projectRoot.child("build").child(configuration + "-" + buildPlatform);
        }
	listener.getLogger().println("buildDirectory: " + buildDirectory.absolutize());

        // XCode Version
        int returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getXcodebuildPath(), "-version").stdout(listener).pwd(projectRoot).join();
        if (returnCode > 0) {
            listener.fatalError(Messages.XCodeBuilder_xcodeVersionNotFound());
            return false; // We fail the build if XCode isn't deployed
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Try to read CFBundleShortVersionString from project
        listener.getLogger().println(Messages.XCodeBuilder_fetchingCFBundleShortVersionString());
        String cfBundleShortVersionString = "";
        returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "mvers", "-terse1").stdout(output).pwd(projectRoot).join();
        // only use this version number if we found it
        if (returnCode == 0)
            cfBundleShortVersionString = output.toString().trim();
        if (StringUtils.isEmpty(cfBundleShortVersionString))
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringNotFound());
        else
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringFound(cfBundleShortVersionString));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringValue(cfBundleShortVersionString));

        output.reset();

        // Try to read CFBundleVersion from project
        listener.getLogger().println(Messages.XCodeBuilder_fetchingCFBundleVersion());
        String cfBundleVersion = "";
        returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "vers", "-terse").stdout(output).pwd(projectRoot).join();
        // only use this version number if we found it
        if (returnCode == 0)
            cfBundleVersion = output.toString().trim();
        if (StringUtils.isEmpty(cfBundleVersion))
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionNotFound());
        else
            listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionFound(cfBundleVersion));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionValue(cfBundleVersion));

        String buildDescription = cfBundleShortVersionString + " (" + cfBundleVersion + ")";
        XCodeAction a = new XCodeAction(buildDescription);
        build.addAction(a);

        // Update the bundle ID
        if (this.changeBundleID != null && this.changeBundleID) {
        	listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdentifierChanged(bundleIDInfoPlistPath, bundleID));
        	returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c",  "Set :CFBundleIdentifier " + bundleID, bundleIDInfoPlistPath).stdout(listener).pwd(projectRoot).join();

        	if (returnCode > 0) {
        		listener.fatalError(Messages.XCodeBuilder_CFBundleIdentifierInfoPlistNotFound(bundleIDInfoPlistPath));
        		return false;
        	}
        }

        // Update the Marketing version (CFBundleShortVersionString)
        if (this.provideApplicationVersion != null && this.provideApplicationVersion && !StringUtils.isEmpty(cfBundleShortVersionStringValue)) {
            try {
                // If not empty we use the Token Expansion to replace it
                // https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
                cfBundleShortVersionString = TokenMacro.expandAll(build, projectRoot, listener, cfBundleShortVersionStringValue);
                listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringUpdate(cfBundleShortVersionString));
                returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "new-marketing-version", cfBundleShortVersionString).stdout(listener).pwd(projectRoot).join();
                if (returnCode > 0) {
                    listener.fatalError(Messages.XCodeBuilder_CFBundleShortVersionStringUpdateError(cfBundleShortVersionString));
                    return false;
                }
            } catch (MacroEvaluationException e) {
                listener.fatalError(Messages.XCodeBuilder_CFBundleShortVersionStringMacroError(e.getMessage()));
                // Fails the build
                return false;
            }
        }

        // Update the Technical version (CFBundleVersion)
        if (this.provideApplicationVersion != null && this.provideApplicationVersion && !StringUtils.isEmpty(cfBundleVersionValue)) {
            try {
                // If not empty we use the Token Expansion to replace it
                // https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
                cfBundleVersion = TokenMacro.expandAll(build, projectRoot, listener, cfBundleVersionValue);
                listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionUpdate(cfBundleVersion));
                returnCode = launcher.launch().envs(envs).cmds(getGlobalConfiguration().getAgvtoolPath(), "new-version", "-all", cfBundleVersion).stdout(listener).pwd(projectRoot).join();
                if (returnCode > 0) {
                    listener.fatalError(Messages.XCodeBuilder_CFBundleVersionUpdateError(cfBundleVersion));
                    return false;
                }
            } catch (MacroEvaluationException e) {
                listener.fatalError(Messages.XCodeBuilder_CFBundleVersionMacroError(e.getMessage()));
                // Fails the build
                return false;
            }
        }

        listener.getLogger().println(Messages.XCodeBuilder_CFBundleShortVersionStringUsed(cfBundleShortVersionString));
        listener.getLogger().println(Messages.XCodeBuilder_CFBundleVersionUsed(cfBundleVersion));

        // Clean build directories
        if (cleanBeforeBuild) {
            listener.getLogger().println(Messages.XCodeBuilder_cleaningBuildDir(buildDirectory.absolutize().getRemote()));
            buildDirectory.deleteRecursive();
        }

        // remove test-reports and *.ipa
        if (cleanTestReports != null && cleanTestReports) {
            listener.getLogger().println(Messages.XCodeBuilder_cleaningTestReportsDir(projectRoot.child("test-reports").absolutize().getRemote()));
            projectRoot.child("test-reports").deleteRecursive();
		}

        if (unlockKeychain != null && unlockKeychain) {
            // Let's unlock the keychain
            Keychain keychain = getKeychain();
            if(keychain == null)
            {
                listener.fatalError(Messages.XCodeBuilder_keychainNotConfigured());
                return false;
            }
            String keychainPath = envs.expand(keychain.getKeychainPath());
            String keychainPwd = envs.expand(keychain.getKeychainPassword());
            launcher.launch().envs(envs).cmds("/usr/bin/security", "list-keychains", "-s", keychainPath).stdout(listener).pwd(projectRoot).join();
            launcher.launch().envs(envs).cmds("/usr/bin/security", "default-keychain", "-d", "user", "-s", keychainPath).stdout(listener).pwd(projectRoot).join();
            if (StringUtils.isEmpty(keychainPwd))
                returnCode = launcher.launch().envs(envs).cmds("/usr/bin/security", "unlock-keychain", keychainPath).stdout(listener).pwd(projectRoot).join();
            else
                returnCode = launcher.launch().envs(envs).cmds("/usr/bin/security", "unlock-keychain", "-p", keychainPwd, keychainPath).masks(false, false, false, true, false).stdout(listener).pwd(projectRoot).join();

            if (returnCode > 0) {
                listener.fatalError(Messages.XCodeBuilder_unlockKeychainFailed());
                return false;
            }

            // Show the keychain info after unlocking, if not, OS X will prompt for the keychain password
            launcher.launch().envs(envs).cmds("/usr/bin/security", "show-keychain-info", keychainPath).stdout(listener).pwd(projectRoot).join();
        }

        // display useful setup information
        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoLineDelimiter());
        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableCertificates());
        /*returnCode =*/ launcher.launch().envs(envs).cmds("/usr/bin/security", "find-identity", "-p", "codesigning", "-v").stdout(listener).pwd(projectRoot).join();

	String developmentTeamID = null;
        Boolean archiveAutomaticSigning = false;
        if ( signingMethod != null && signingMethod.equals("readFromProject") ) {
	    provisioningProfiles = new ArrayList<>();
	    listener.getLogger().println("Read signing information from Xcode Project.");
	    XcodeProject xcodeProject = null;
	    ArrayList<FilePath> projectLocations = new ArrayList<FilePath>();
	    // Retrieve target from Xcode project.
	    FilePath projectLocation = null;
	    if ( !StringUtils.isEmpty(xcodeProjectFile) ) {
		// Retrieve provisioning profile information from Xcode project file.
		projectLocation = projectRoot.child(xcodeProjectFile);
		if ( !projectLocation.exists() || !projectLocation.isDirectory() ) {
		    listener.getLogger().println("Could not read information from " + projectLocation.absolutize().getRemote());
		    projectLocation = null;
		}
	    }
	    if ( projectLocation == null ) {
		// Retrieve xcodeproj from current working directory.
		List<FilePath> xcodeProjects = projectRoot.list(new XcodeProjectFileFilter());
		if (xcodeProjects == null) {
		    listener.fatalError(Messages.XCodeBuilder_NoArchivesInBuildDirectory(projectRoot.absolutize().getRemote()));
		    return false;
		}

            	for (FilePath xcodeProjectDir : xcodeProjects) {
		    // Xcode build generates an error if there are multiple xcodeproj.
		    projectLocation = xcodeProjectDir;
		    break;
		}
	    }
            // Retrieve provisioning profile information from specific Xcode project file.
            HashMap<String, ProjectScheme> xcodeSchemes = XcodeProjectParser.listXcodeSchemes(projectLocation);
            if ( !StringUtils.isEmpty(xcodeSchema) ) {
		listener.getLogger().println("Read information from scheme " + xcodeSchema);
                // Retrieve target from specific Xcode scheme.
                ProjectScheme projectScheme = xcodeSchemes.get(xcodeSchema);
                if ( projectScheme == null ) {
		    listener.getLogger().println("Could not get information from scheme " + xcodeSchema);
                    return false;
                }
		String referencedContainerLocation = projectScheme.referencedContainer.replaceAll("^container:", "");
                projectLocation = projectRoot.child(referencedContainerLocation);
		target = projectScheme.blueprintName;
		listener.getLogger().println("Read project information from " + projectLocation.absolutize().getRemote());
		projectLocations.add(projectLocation);
            }
            else {
		if ( xcodeSchemes.size() == 1 ) {
		    for ( Map.Entry<String, ProjectScheme> entry: xcodeSchemes.entrySet() ) {
			ProjectScheme projectScheme = entry.getValue();
		    	xcodeSchema = projectScheme.blueprintName;
		    }
		}
		if ( !StringUtils.isEmpty(xcodeWorkspaceFile) ) {
                    // Retrieve target from Xcode workspace.
		    listener.getLogger().println("Read information from workspace " + xcodeWorkspaceFile);
                    List<String> projectList = XcodeProjectParser.parseXcodeWorkspace(projectRoot.child("/" + xcodeWorkspaceFile + ".xcworkspace"));
                    for ( String location : projectList ) {
			xcodeProject = XcodeProjectParser.parseXcodeProject(projectRoot.child("/" + location));
                	String projectName = projectRoot.child("/" + location).getBaseName().replaceAll(".xcodeproj$", "");
                	if ( !StringUtils.isEmpty(target) ) {
			    if ( target.equals(projectName) ) {
				// Add the location of specific projects.
				projectLocations.add(projectRoot.child("/" + location));
				break;
			    }
               		}
		        else {
			    // Add the location of all projects.
			    projectLocations.add(projectRoot.child("/" + location));
			}
            	    }
                }
	        else {
		    // Using Xcode project file information.
		    listener.getLogger().println("Using Xcode project file information " + xcodeSchema);
		    projectLocations.add(projectLocation);
		}
	    }
	    for ( FilePath examineLocation : projectLocations ) {
                // Parse Xcode project file.
                xcodeProject = XcodeProjectParser.parseXcodeProject(examineLocation);
                if ( xcodeProject == null ) {
		    listener.getLogger().println("Could not read project information from " + examineLocation.absolutize().getRemote());
                    return false;      
                }
		// Examine all targets.
		for ( String key : xcodeProject.projectTarget.keySet() ) {
		    ProjectTarget projectTarget = xcodeProject.projectTarget.get(key);
		    String exportConfiguration = null;
		    if ( !StringUtils.isEmpty(ipaExportMethod) ) {
			if ( ipaExportMethod.equals("app-store") ) {
			    exportConfiguration = "Release";
			}
                        else if ( ipaExportMethod.equals("enterprise") ) {
                            exportConfiguration = "Enterprise";
                        }
			else if ( ipaExportMethod.equals("ad-hoc") ) {
			    exportConfiguration = "AdHoc";
			}
			else {
			    exportConfiguration = "Debug";
			}
                    }
		    else if ( StringUtils.isEmpty(configuration) ) {
			exportConfiguration = projectTarget.defaultConfigurationName;
		    }
		    Boolean automaticSigning = projectTarget.provisioningStyle.equals("Automatic");
                    if ( projectTarget.testTargetID == null ) {
                        // The target is not a test.
                        archiveAutomaticSigning = automaticSigning;
                    }
		    BuildConfiguration buildConfiguration = projectTarget.buildConfiguration.get(exportConfiguration);
		    if ( buildConfiguration == null ) {
			listener.getLogger().println("Could not get build configuration (" + exportConfiguration + ") from " + examineLocation.absolutize().getRemote());
			buildConfiguration = projectTarget.buildConfiguration.get("Release");
			if ( buildConfiguration == null ) {
			    return false;
			}
			else {
			    // Fallback to Release configuretion.`
			    listener.getLogger().println("Useing Release configuration for build.");
			}
		    }
		    if ( buildConfiguration.developmentTeamId != null ) {
			developmentTeamID = buildConfiguration.developmentTeamId;
			listener.getLogger().println("Found developmentTeamID (" + developmentTeamID + ") from " + examineLocation.absolutize().getRemote());
		    }
		    if ( !automaticSigning ) {
			String provisioningProfileUUID = buildConfiguration.provisioningProfileUUID;
			String provisioningProfileSpecifier = buildConfiguration.provisioningProfileSpecifier;
			String bundleIdentifier = null;
			if ( buildConfiguration.bundleIdentifier != null ) {
			    bundleIdentifier = buildConfiguration.bundleIdentifier;
			}
			else {
			    // Placeholder replacement.
			    // Currentry only support "${TARGET_NAME}" and "$(TARGET_NAME)"
			    String productName = buildConfiguration.productName;
			    productName = productName.replaceAll(Pattern.quote("${TARGET_NAME}"), key);
			    productName = productName.replaceAll(Pattern.quote("$(TARGET_NAME)"), key);
			    InfoPlist infoPlist = XcodeProjectParser.parseInfoPlist(projectRoot.child("/" + buildConfiguration.infoPlistFile));
			    if ( infoPlist == null ) {
				listener.getLogger().println("Could not read information from " + projectRoot.toString() + "/" + buildConfiguration.infoPlistFile);
				return false;
			    }
			    // Placeholder replacement.
			    // Currentry only support "$(PRODUCT_NAME:rfc1034identifier)"
			    bundleIdentifier = infoPlist.getCfBundleIdentifier();
			    productName = productName.replaceAll(" ", "-");
			    bundleIdentifier = bundleIdentifier.replaceAll(Pattern.quote("$(PRODUCT_NAME:rfc1034identifier)"), productName);
			}
			// PROVISIONING_PROFILE(UUID) or PROVISIONING_PROFILE_SPECIFIER
			String provisioningProfileIdentifier = null;
			if ( provisioningProfileSpecifier != null ) {
			    // We will use SPECIFIE instead UUID.
			    provisioningProfileIdentifier = provisioningProfileSpecifier;
			}
			else {
			    provisioningProfileIdentifier = provisioningProfileUUID;
			}
			if ( provisioningProfileIdentifier != null ) {
			    provisioningProfiles.add(new ProvisioningProfile(bundleIdentifier, provisioningProfileIdentifier));
			}
		    }
		}
	    }
	    for ( ProvisioningProfile rp : provisioningProfiles ) {
		listener.getLogger().println("UUID/SPECIFIER                       CFbundleIdentifier");
		listener.getLogger().println(rp.getProvisioningProfileUUID() + " " + rp.getProvisioningProfileAppId());
	    }
        }
        else {
            // If developmentTeamName is set the developmentTeamID is invalid.
	    if ( StringUtils.isEmpty(developmentTeamName) ) {
                developmentTeamID = envs.expand(this.developmentTeamID);
            }
	    if (StringUtils.isEmpty(developmentTeamID)) {
	        Team team = getDevelopmentTeam();
                if (team == null) {
		    listener.getLogger().println(Messages.XCodeBuilder_teamNotConfigured());
	        } else {
		    developmentTeamID = envs.expand(team.getTeamID());
		    if (!StringUtils.isEmpty(developmentTeamID)) {
		        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoCanFindCertificates());
		        /*returnCode =*/
		        launcher.launch().envs(envs).cmds("/usr/bin/security", "find-certificate", "-a", "-c", developmentTeamID, "-Z", "|", "grep", "^SHA-1").stdout(listener).pwd(projectRoot).join();
		        // We could fail here, but this doesn't seem to work as it should right now (output not properly redirected. We might need a parser)
		    }
	        }
	    }
        }

        listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableSDKs());
        /*returnCode =*/ launcher.launch().envs(envs).cmds(getGlobalConfiguration().getXcodebuildPath(), "-showsdks").stdout(listener).pwd(projectRoot).join();

        XcodeBuildListParser xcodebuildListParser;
        {
            List<String> commandLine = Lists.newArrayList(getGlobalConfiguration().getXcodebuildPath());
            commandLine.add("-list");
            // xcodebuild -list -workspace $workspace
            listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableSchemes());
            if (!StringUtils.isEmpty(xcodeWorkspaceFile)) {
                commandLine.add("-workspace");
                commandLine.add(xcodeWorkspaceFile + ".xcworkspace");
            } else if (!StringUtils.isEmpty(xcodeProjectFile)) {
                commandLine.add("-project");
                commandLine.add(xcodeProjectFile);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(baos).pwd(projectRoot).start().joinWithTimeout(10, TimeUnit.SECONDS, listener);
            String xcodeBuildListOutput = baos.toString("UTF-8");
            listener.getLogger().println(xcodeBuildListOutput);
            boolean timedOut = returnCode == SIGTERM;
            if (returnCode > 0 && !timedOut) return false;

            xcodebuildListParser = new XcodeBuildListParser(xcodeBuildListOutput);
        }

        XcodeBuildHelpParser xcodebuildHelpParser;
        {
            List<String> commandLine = Lists.newArrayList(getGlobalConfiguration().getXcodebuildPath());
            commandLine.add("-help");
            // xcodebuild -help
            listener.getLogger().println(Messages.XCodeBuilder_DebugInfoAvailableParameters());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(baos).pwd(projectRoot).start().joinWithTimeout(10, TimeUnit.SECONDS, listener);
            String xcodeBuildHelpOutput = baos.toString("UTF-8");
            boolean timedOut = returnCode == SIGTERM;
            if (returnCode > 0 && !timedOut) return false;

            xcodebuildHelpParser = new XcodeBuildHelpParser(xcodeBuildHelpOutput);
        }
	Boolean haveAllowProvisioningUpdates = false;
	List<String> availableParameters = xcodebuildHelpParser.getParameters();
	if (availableParameters.isEmpty()) {
	    listener.getLogger().println(Messages.XCodeBuilder_NoAvailableParameters());
	    haveAllowProvisioningUpdates = false;
	}
	else {
	    listener.getLogger().println(StringUtils.join(availableParameters, "\n"));
	    if(availableParameters.contains("-allowProvisioningUpdates")){
		haveAllowProvisioningUpdates = true;
	    }
	}
	listener.getLogger().println(Messages.XCodeBuilder_DebugInfoLineDelimiter());

        // Build
	if ( skipBuildStep == null || !skipBuildStep ) {
	    StringBuilder xcodeReport = new StringBuilder(Messages.XCodeBuilder_invokeXcodebuild());
	    JenkinsXCodeBuildOutputParser reportGenerator = new JenkinsXCodeBuildOutputParser(projectRoot, listener);
	    List<String> commandLine = Lists.newArrayList(getGlobalConfiguration().getXcodebuildPath());

	    // Prioritizing schema over target setting
	    if (!StringUtils.isEmpty(xcodeSchema)) {
		commandLine.add("-scheme");
		commandLine.add(xcodeSchema);
		xcodeReport.append(", scheme: ").append(xcodeSchema);
	    } else if (StringUtils.isEmpty(target)) {
		// When target is empty always build all targets.
		commandLine.add("-alltargets");
		xcodeReport.append("target: ALL");
	    } else if(interpretTargetAsRegEx != null && interpretTargetAsRegEx) {
		if(xcodebuildListParser.getTargets().isEmpty()) {
		    listener.getLogger().println(Messages.XCodeBuilder_NoTargetsFoundInConfig());
		    return false;
		}
		Collection<String> matchedTargets = Collections2.filter(xcodebuildListParser.getTargets(),
									Predicates.containsPattern(target));

		if (matchedTargets.isEmpty()) {
		    listener.getLogger().println(Messages.XCodeBuilder_NoMatchingTargetsFound());
		    return false;
		}

		for (String matchedTarget : matchedTargets) {
		    commandLine.add("-target");
		    commandLine.add(matchedTarget);
		    xcodeReport.append("target: ").append(matchedTarget);
		}
	    } else {
		commandLine.add("-target");
		commandLine.add(target);
		xcodeReport.append("target: ").append(target);
	    }

	    if (!StringUtils.isEmpty(sdk)) {
		commandLine.add("-sdk");
		commandLine.add(sdk);
		xcodeReport.append(", sdk: ").append(sdk);
	    } else {
		xcodeReport.append(", sdk: DEFAULT");
	    }

	    // Prioritizing workspace over project setting
	    if (!StringUtils.isEmpty(xcodeWorkspaceFile)) {
		commandLine.add("-workspace");
		commandLine.add(xcodeWorkspaceFile + ".xcworkspace");
		xcodeReport.append(", workspace: ").append(xcodeWorkspaceFile);
	    } else if (!StringUtils.isEmpty(xcodeProjectFile)) {
		commandLine.add("-project");
		commandLine.add(xcodeProjectFile);
		xcodeReport.append(", project: ").append(xcodeProjectFile);
	    } else {
		xcodeReport.append(", project: DEFAULT");
	    }

	    if (!StringUtils.isEmpty(configuration)) {
		commandLine.add("-configuration");
		commandLine.add(configuration);
		xcodeReport.append(", configuration: ").append(configuration);
	    }

	    if (cleanBeforeBuild) {
		commandLine.add("clean");
		xcodeReport.append(", clean: YES");
	    } else {
		xcodeReport.append(", clean: NO");
	    }

	    //Bug JENKINS-30362
	    //Generating an archive builds the project twice
	    //commandLine.add("build");
	    FilePath archiveLocation = buildDirectory.absolutize().child(xcodeSchema + ".xcarchive");
	    if(buildIpa || generateArchive){
		commandLine.add("archive");
		commandLine.add("-archivePath");
		commandLine.add(archiveLocation.getRemote());
		xcodeReport.append(", archive:YES");
	    }else{
		xcodeReport.append(", archive:NO");
		commandLine.add("build");
	    }
	    //END Bug JENKINS-30362

	    if(noConsoleLog != null && noConsoleLog){
		xcodeReport.append(", consolelog:NO");
		reportGenerator.setConsoleLog(false);
	    }else{
		xcodeReport.append(", consolelog:YES");
	    }
	    if(!StringUtils.isEmpty(logfileOutputDirectory)) {
		xcodeReport.append(", logfileOutputDirectory: ").append(logfileOutputDirectory);
		reportGenerator.setLogfilePath(buildDirectory,logfileOutputDirectory);
	    }
    
	    if (!StringUtils.isEmpty(symRootValue)) {
		commandLine.add("SYMROOT=" + symRootValue);
		xcodeReport.append(", symRoot: ").append(symRootValue);
	    } else {
		xcodeReport.append(", symRoot: DEFAULT");
	    }

	    // BUILD_DIR
	    if (!StringUtils.isEmpty(buildDirValue)) {
		commandLine.add("BUILD_DIR=" + buildDirValue);
		xcodeReport.append(", buildDir: ").append(buildDirValue);
	    } else {
		xcodeReport.append(", buildDir: DEFAULT");
	    }

	    // handle code signing identities
	    if (!StringUtils.isEmpty(developmentTeamID)) {
		commandLine.add("DEVELOPMENT_TEAM=" + developmentTeamID);
		xcodeReport.append(", developmentTeamID: ").append(developmentTeamID);
	    } else {
		xcodeReport.append(", developmentTeamID: AUTOMATIC");
	    }

	    // Allow updating signing assets
	    if ( haveAllowProvisioningUpdates && ( archiveAutomaticSigning || signingMethod == null || signingMethod.equals("automatic") ) ) {
		commandLine.add("-allowProvisioningUpdates");
		xcodeReport.append(", allowProvisioningUpdates: YES");
	    }

	    // Additional (custom) xcodebuild arguments
	    if (!StringUtils.isEmpty(xcodebuildArguments)) {
		commandLine.addAll(splitXcodeBuildArguments(xcodebuildArguments));
	    }

	    listener.getLogger().println(xcodeReport.toString());
	    returnCode = launcher.launch().envs(envs).cmds(commandLine).stdout(reportGenerator.getOutputStream()).pwd(projectRoot).join();
	    if (allowFailingBuildResults != null && !allowFailingBuildResults) {
		if (reportGenerator.getExitCode() != 0) return false;
		if (returnCode > 0) return false;
	    }
	}

        // Package IPA
        if (buildIpa) {

            if (!buildDirectory.exists() || !buildDirectory.isDirectory()) {
                listener.fatalError(Messages.XCodeBuilder_NotExistingBuildDirectory(buildDirectory.absolutize().getRemote()));
                return false;
            }

            // clean IPA
            FilePath ipaOutputPath = null;
            if (ipaOutputDirectory != null && ! StringUtils.isEmpty(ipaOutputDirectory)) {
            	ipaOutputPath = buildDirectory.child(ipaOutputDirectory);

            	// Create if non-existent
            	if (! ipaOutputPath.exists()) {
            		ipaOutputPath.mkdirs();
            	}
            }

            if (ipaOutputPath == null) {
            	ipaOutputPath = buildDirectory;
            }

            listener.getLogger().println(Messages.XCodeBuilder_cleaningIPA());
            for (FilePath path : ipaOutputPath.list("*.ipa")) {
                path.delete();
            }
            listener.getLogger().println(Messages.XCodeBuilder_cleaningDSYM());
            for (FilePath path : ipaOutputPath.list("*-dSYM.zip")) {
                path.delete();
            }
            // packaging IPA
            listener.getLogger().println(Messages.XCodeBuilder_packagingIPA());

	    // Writeing exportOptions.plist
	    Boolean manualSigning = (!archiveAutomaticSigning && signingMethod != null && (signingMethod.equals("manual") || signingMethod.equals("readFromProject")));
	    NSDictionary exportOptionsPlist = new NSDictionary();
	    exportOptionsPlist.put("signingStyle", manualSigning ? "manual" : "automatic");
	    exportOptionsPlist.put("method", ipaExportMethod);
	    if ( !StringUtils.isEmpty(developmentTeamID) ) {
		exportOptionsPlist.put("teamID", developmentTeamID);
	    }
	    if ( manualSigning ) {
		exportOptionsPlist.put("signingCertificate", ipaExportMethod.equals("development") ? DEV_SIGNING_CERTIFICATE_SELECTOR : DIST_SIGNING_CERTIFICATE_SELECTOR);
		if ( provisioningProfiles.size() > 0 ) {
		    NSDictionary provisioningProfileDict = new NSDictionary();
		    for ( ProvisioningProfile pp : provisioningProfiles ) {
			String provisioningProfileAppId = envs.expand(pp.getProvisioningProfileAppId());
			if ( !StringUtils.isEmpty(provisioningProfileAppId) &&
			     provisioningProfileAppId.endsWith(".plist") ) {
			    // If provisioningProfileAppId is an Info.plist file,
			    //  obtain the Bundle ID from Info.plist and use it.
			    try {
				output.reset();
				returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIdentifier", projectRoot.absolutize().child(provisioningProfileAppId).getRemote()).stdout(output).pwd(projectRoot).join();
				if (returnCode == 0) {
				    provisioningProfileAppId = output.toString().trim();
				    listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdReplaceWith(provisioningProfileAppId));
				}
				else {
				    // When Info.plist generated by Xcodebuild is specified.
				    output.reset();
				    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :ApplicationProperties:CFBundleIdentifier", projectRoot.absolutize().child(provisioningProfileAppId).getRemote()).stdout(output).pwd(projectRoot).join();
				    if (returnCode == 0) {
					provisioningProfileAppId = output.toString().trim();
					listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdReplaceWith(provisioningProfileAppId));
				    }
				}
			    }
			    catch(Exception ex) {
				listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdFailedGetInInfoPlist(projectRoot.absolutize().child(provisioningProfileAppId).getRemote(), ex.toString()));
			    }
			}
			String provisioningProfileUUID = envs.expand(pp.getProvisioningProfileUUID());
			if ( !StringUtils.isEmpty(provisioningProfileUUID) &&
			     provisioningProfileUUID.endsWith(".mobileprovision") ) {
			    // If provisioningProfileUUID  is an .mobileprovision file,
			    //  obtain the profile UUID from .mobileprovision and use it.
			    try {
				output.reset();
				returnCode = launcher.launch().envs(envs).cmds("/bin/sh", "-c", "/usr/libexec/PlistBuddy -c \"Print :UUID\" /dev/stdin <<< $(/usr/bin/security cms -D -i \"" + projectRoot.absolutize().child(provisioningProfileUUID).getRemote() + "\")").stdout(output).pwd(projectRoot).join();
				if (returnCode == 0) {
				    // Perhaps it is useful to copy the mobileprovision to the library here.
				    // /Users/${HOME}/Library/MobileDevice/Provisioning Profiles/
				    provisioningProfileUUID = output.toString().trim();
				    listener.getLogger().println(Messages.XCodeBuilder_ProfileUUIDReplaceWith(provisioningProfileUUID));
				}
			    }
			    catch(Exception ex) {
				listener.getLogger().println(Messages.XCodeBuilder_CFBundleIdFailedGetInMobileProvision(projectRoot.absolutize().child(provisioningProfileAppId).getRemote(), ex.toString()));
			    }
			}
			provisioningProfileDict.put(provisioningProfileAppId, provisioningProfileUUID);
		    }
		    exportOptionsPlist.put("provisioningProfiles", provisioningProfileDict);
		}
	    }
	    exportOptionsPlist.put("iCloudContainerEnvironment", ipaExportMethod.equals("app-store") ? PRODUCTION_ENV : DEVELOPMENT_ENV);
	    // Extra options
	    if ( ipaExportMethod.equals("app-store") ) { 
		exportOptionsPlist.put("uploadBitcode", uploadBitcode);
		exportOptionsPlist.put("uploadSymbols", uploadSymbols);
	    }
	    else {
		if ( !StringUtils.isEmpty(thinning) ) {
		    exportOptionsPlist.put("thinning", thinning);
		}
		exportOptionsPlist.put("compileBitcode", compileBitcode);
		if ( (embedOnDemandResourcesAssetPacksInBundle == null ||
		      embedOnDemandResourcesAssetPacksInBundle) &&
		      !StringUtils.isEmpty(onDemandResourcesAssetPacksBaseURL) ) {
		    exportOptionsPlist.put("embedOnDemandResourcesAssetPacksInBundle", false);
		    exportOptionsPlist.put("onDemandResourcesAssetPacksBaseURL", onDemandResourcesAssetPacksBaseURL);
		}
		if ( !StringUtils.isEmpty(appURL) ) {
		    NSDictionary manifestPlistOprions = new NSDictionary();
		    manifestPlistOprions.put("appURL", appURL);
		    if ( !StringUtils.isEmpty(displayImageURL) ) {
			manifestPlistOprions.put("displayImageURL", displayImageURL);
		    }
		    if ( !StringUtils.isEmpty(fullSizeImageURL) ) {
			manifestPlistOprions.put("fullSizeImageURL", fullSizeImageURL);
		    }
		    if ( !StringUtils.isEmpty(assetPackManifestURL) ) {
			manifestPlistOprions.put("assetPackManifestURL", assetPackManifestURL);
		    }
		    exportOptionsPlist.put("manifest", manifestPlistOprions);
		}
	    }
	    ByteArrayOutputStream stream = new ByteArrayOutputStream();
	    PropertyListParser.saveAsXML(exportOptionsPlist, stream);
	    FilePath exportOptionsPlistLocation = ipaOutputPath.child(ipaExportMethod + ((developmentTeamID == null || StringUtils.isEmpty(developmentTeamID)) ? "AUTOMATIC" : developmentTeamID) + "ExportOptions.plist");
	    exportOptionsPlistLocation.write(stream.toString(), "UTF-8");

            List<FilePath> archives = buildDirectory.list(new XCArchiveFileFilter());
            // FilePath is based on File.listFiles() which can randomly fail | http://stackoverflow.com/questions/3228147/retrieving-the-underlying-error-when-file-listfiles-return-null
            if (archives == null) {
                listener.fatalError(Messages.XCodeBuilder_NoArchivesInBuildDirectory(buildDirectory.absolutize().getRemote()));
                return false;
            }

            for (FilePath archive : archives) {
                String version = "";
                String shortVersion = "";

                try {
                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :ApplicationProperties:CFBundleVersion", archive.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(projectRoot).join();
                    if (returnCode == 0) {
                        version = output.toString().trim();
                    }

                    output.reset();
                    returnCode = launcher.launch().envs(envs).cmds("/usr/libexec/PlistBuddy", "-c", "Print :ApplicationProperties:CFBundleShortVersionString", archive.absolutize().child("Info.plist").getRemote()).stdout(output).pwd(projectRoot).join();
                    if (returnCode == 0) {
                        shortVersion = output.toString().trim();
                    }
                }
                catch(Exception ex) {
                    listener.getLogger().println(Messages.XCodeBuilder_FailedToGetVersionFromInfoPlist(ex.toString()));
                    return false;
                }

               	if (StringUtils.isEmpty(version) && StringUtils.isEmpty(shortVersion)) {
               		listener.getLogger().println(Messages.XCodeBuilder_MarketingAndTechnicalVersionNotFound());
               		return false;
               	}

                String lastModified = new SimpleDateFormat("yyyy.MM.dd").format(new Date(archive.lastModified()));

                String baseName = archive.getBaseName().replaceAll(" ", "_") + (shortVersion.isEmpty() ? "" : "-" + shortVersion) + (version.isEmpty() ? "" : "-" + version);
                // If custom .ipa name pattern has been provided, use it and expand version and build date variables
                if (! StringUtils.isEmpty(ipaName)) {
                	EnvVars customVars = new EnvVars(
                		"BASE_NAME", archive.getBaseName().replaceAll(" ", "_"),
                		"VERSION", version,
                		"SHORT_VERSION", shortVersion,
                		"BUILD_DATE", lastModified
                	);
                    baseName = customVars.expand(ipaName);
                }

                String ipaFileName = baseName + ".ipa";
                FilePath ipaLocation = ipaOutputPath.child(ipaFileName);

                FilePath payload = ipaOutputPath.child("Payload");
                payload.deleteRecursive();
                payload.mkdirs();

                listener.getLogger().println(Messages.XCodeBuilder_PackagingArchiveToIpa(archive.getBaseName(), ipaLocation.absolutize().getRemote()));
                if (buildPlatform.contains("simulator")) {
                    listener.getLogger().println(Messages.XCodeBuilder_warningPackagingIPAForSimulatorSDK(sdk));
                }


                List<String> packageCommandLine = new ArrayList<>();
                packageCommandLine.add(getGlobalConfiguration().getXcodebuildPath());
                packageCommandLine.addAll(Lists.newArrayList("-exportArchive", "-archivePath", archive.absolutize().getRemote(), "-exportPath", ipaOutputPath.absolutize().getRemote(), "-exportOptionsPlist", exportOptionsPlistLocation.absolutize().getRemote()));
                if (archiveAutomaticSigning || signingMethod == null || (!signingMethod.equals("manual") && !signingMethod.equals("readFromProject"))) {
		    if (haveAllowProvisioningUpdates)
                	packageCommandLine.add("-allowProvisioningUpdates");
                }
                returnCode = launcher.launch().envs(envs).stdout(listener).pwd(projectRoot).cmds(packageCommandLine).join();
                if (returnCode > 0) {
                    listener.getLogger().println(Messages.XCodeBuilder_FailedToBuildIpa(ipaLocation.absolutize().getRemote()));
                    return false;
                }
                //rename exported ipa
                FilePath exportedIpa = ipaOutputPath.child(archive.getBaseName() + ".ipa");
                if (exportedIpa.exists()) {
                    exportedIpa.renameTo(ipaLocation);
                }


                // also zip up the symbols, if present
                listener.getLogger().println(Messages.XCodeBuilder_ArchivingDSYM());
                //List<FilePath> dSYMs = buildDirectory.absolutize().child(configuration + "-" + buildPlatform).list(new DSymFileFilter());
                //List<FilePath> dSYMs = buildDirectory.absolutize().child(xcodeSchema + ".xcarchive/dSYMs").list(new DSymFileFilter());
		List<FilePath> dSYMs = archive.absolutize().child("dSYMs").list(new DSymFileFilter());

                if (dSYMs == null || dSYMs.isEmpty()) {
                    //listener.getLogger().println(Messages.XCodeBuilder_NoDSYMFileFound(buildDirectory.absolutize().child(configuration + "-" + buildPlatform)));
                    //listener.getLogger().println(Messages.XCodeBuilder_NoDSYMFileFound(buildDirectory.absolutize().child(xcodeSchema + ".xcarchive/dSYMs")));
		    listener.getLogger().println(Messages.XCodeBuilder_NoDSYMFileFound(archive.absolutize().child("dSYMs")));
                } else {
                    for (FilePath dSYM : dSYMs) {
                        returnCode = launcher.launch()
                                .envs(envs)
                                .stdout(listener)
                                .pwd(buildDirectory)
                                .cmds("ditto",
                                        "-c",
                                        "-k",
                                        "--keepParent",
                                        "-rsrc",
                                        dSYM.absolutize().getRemote(),
                                        ipaOutputPath.child(baseName + "-dSYM.zip")
                                                .absolutize()
                                                .getRemote())
                                .join();

                        if (returnCode > 0) {
                            listener.getLogger().println(Messages.XCodeBuilder_zipFailed(baseName));
                            return false;
                        }
                    }
                }
                payload.deleteRecursive();
            }
        }

        return true;
    }

    public Keychain getKeychain() {
        if(!StringUtils.isEmpty(keychainName)) {
            for (Keychain keychain : getGlobalConfiguration().getKeychains()) {
                if(keychain.getKeychainName().equals(keychainName))
                    return keychain;
            }
        }

        if(!StringUtils.isEmpty(keychainPath)) {
            return new Keychain("", keychainPath, keychainPwd, false);
        }

        return null;
    }

    public Team getDevelopmentTeam() {
        if(!StringUtils.isEmpty(developmentTeamName)) {
            for (Team team : getGlobalConfiguration().getTeams()) {
                if(team.getTeamName().equals(developmentTeamName))
                    return team;
            }
        }

        if(!StringUtils.isEmpty(developmentTeamID)) {
            return new Team("", developmentTeamID);
        }

        return null;
    }

    static List<String> splitXcodeBuildArguments(String xcodebuildArguments) {
        if (xcodebuildArguments == null || xcodebuildArguments.length() == 0) {
            return new ArrayList<>(0);
        }

        final QuotedStringTokenizer tok = new QuotedStringTokenizer(xcodebuildArguments);
        final List<String> result = new ArrayList<>();
        while(tok.hasMoreTokens())
            result.add(tok.nextToken());

        return result;
    }

    public GlobalConfigurationImpl getGlobalConfiguration() {
    	return getDescriptor().getGlobalConfiguration();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    @Symbol("xcodeBuild")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	GlobalConfigurationImpl globalConfiguration;

        // backward compatibility
        @Deprecated
        private transient String xcodebuildPath;
        private transient String agvtoolPath;
        private transient String xcrunPath;
        private transient CopyOnWriteList<Keychain> keychains;
        private transient CopyOnWriteList<Team> teams;

        public DescriptorImpl() {
            load();
        }

        @SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
        @Inject
        void setGlobalConfiguration(GlobalConfigurationImpl c) {
            this.globalConfiguration = c;
            {// data migration from old format
                boolean modified = false;
                if (xcodebuildPath!=null) {
                    c.setXcodebuildPath(xcodebuildPath);
                    modified = true;
                }
                if (agvtoolPath!=null) {
                    c.setAgvtoolPath(agvtoolPath);
                    modified = true;
                }
                if (xcrunPath!=null) {
                    c.setXcrunPath(xcrunPath);
                    modified = true;
                }
                if (keychains!=null) {
                    c.setKeychains(new ArrayList<>(keychains.getView()));
                    modified = true;
                }
                if (teams!=null) {
                    c.setTeams(new ArrayList<>(teams.getView()));
                    modified = true;
                }
                if (modified) {
                    c.save();
                    save(); // delete the old values from the disk now that the new values are committed
                }
            }
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

	@Override
	public String getDisplayName() {
	    return Messages.XCodeBuilder_xcode();
	}

	public GlobalConfigurationImpl getGlobalConfiguration() {
	    return globalConfiguration;
	}

	public String getUUID() {
	    return "" + UUID.randomUUID().getMostSignificantBits();
	}

	public FormValidation doCheckXcodeSchema(@QueryParameter String value, @QueryParameter Boolean generateArchive, @QueryParameter Boolean buildIpa) {
	    if ( generateArchive || buildIpa ) {
		if ( StringUtils.isEmpty(value) ) {
		    return FormValidation.error(Messages.XCodeBuilder_NeedSchema());
		}
	    }
	    return FormValidation.ok();
	}

	public FormValidation doCheckOnDemandResourcesAssetPacksBaseURL(@QueryParameter String value, @QueryParameter Boolean embedOnDemandResourcesAssetPacksInBundle) {
	    if ( StringUtils.isEmpty(value) && !embedOnDemandResourcesAssetPacksInBundle ) {
		return FormValidation.error(Messages.XCodeBuilder_NeedOnDemandResourcesURL());
	    }
	    return FormValidation.ok();
	}
    }
}
