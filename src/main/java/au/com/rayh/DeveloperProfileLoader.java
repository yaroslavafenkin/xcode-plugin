package au.com.rayh;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Installs {@link DeveloperProfile} into the current slave and unlocks its keychain
 * in preparation for the signing that uses it.
 *
 * TODO: destroy identity in the end.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
public class DeveloperProfileLoader extends Builder implements SimpleBuildStep {
    @CheckForNull
    private String profileId;
    @CheckForNull
    private Boolean importIntoExistingKeychain;
    @CheckForNull
    private String keychainName;
    @CheckForNull
    private String keychainPath;
    @CheckForNull
    private String keychainPwd;

    @CheckForNull
    public String getDeveloperProfileId() {
        return profileId;
    }

    @DataBoundSetter
    public void setDeveloperProfileId(String developerProfileId) {
        this.profileId = developerProfileId;
    }

    public Boolean getImportIntoExistingKeychain() {
        return importIntoExistingKeychain == null ? Boolean.valueOf(false) : importIntoExistingKeychain;
    }

    @DataBoundSetter
    public void setImportIntoExistingKeychain(Boolean importIntoExistingKeychain ) {
        this.importIntoExistingKeychain = importIntoExistingKeychain;
    }

    @CheckForNull
    public String getKeychainName() {
        return keychainName;
    }

    @DataBoundSetter
    public void setKeychainName(String keychainName) {
        this.keychainName = keychainName;
    }

    @CheckForNull
    public String getKeychainPath() {
        return keychainPath;
    }

    @DataBoundSetter
    public void setKeychainPath(String keychainPath) {
        this.keychainPath = keychainPath;
    }

    @CheckForNull
    public String getKeychainPwd() {
        return keychainPwd;
    }

    @DataBoundSetter
    public void setKeychainPwd(String keychainPwd) {
        this.keychainPwd = keychainPwd;
    }

    @DataBoundConstructor
    public DeveloperProfileLoader(String profileId) {
	this.profileId = profileId;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        DeveloperProfile dp = getProfile(run.getParent());
        if (dp==null)
            throw new AbortException(Messages.DeveloperProfile_NoDeveloperProfileConfigured());

        Keychain keychain = getKeychain();
        if ( keychain == null ) {
            keychainPath = "jenkins-"+run.getParent().getFullName().replace('/', '-');
            keychainPwd = UUID.randomUUID().toString();
	    importIntoExistingKeychain = false;
        }
	else {
	    EnvVars envs = run.getEnvironment(listener);
            keychainPath = envs.expand(keychain.getKeychainPath());
            keychainPwd = envs.expand(keychain.getKeychainPassword());
	    importIntoExistingKeychain = true;
	}

        // Note: keychain are usualy suffixed with .keychain. If we change we should probably clean up the ones we created

        ArgumentListBuilder args;

        if ( !importIntoExistingKeychain ) {
	    // if the key chain is already present, delete it and start fresh
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            args = new ArgumentListBuilder("security","delete-keychain", keychainPath);
            launcher.launch().cmds(args).stdout(out).join();

            args = new ArgumentListBuilder("security","create-keychain");
            args.add("-p").addMasked(keychainPwd);
            args.add(keychainPath);
            invoke(launcher, listener, args, "Failed to create a keychain");
	}

        args = new ArgumentListBuilder("security","unlock-keychain");
        args.add("-p").addMasked(keychainPwd);
        args.add(keychainPath);
        invoke(launcher, listener, args, "Failed to unlock keychain");

        args = new ArgumentListBuilder("security","list-keychains");
	args.add("-d").add("user");
        args.add("-s").add("login.keychain");
        args.add(keychainPath);
        invoke(launcher, listener, args, "Failed to set keychain search path");

        final FilePath secret = getSecretDir(workspace, keychainPwd);
	final byte[] dpImage = dp.getImage();
	if ( dpImage == null )
	    throw new AbortException(Messages.DeveloperProfile_NoDeveloperProfileConfigured());
        secret.unzipFrom(new ByteArrayInputStream(dpImage));

        // import identities
        for (FilePath id : secret.list("**/*.p12")) {
            args = new ArgumentListBuilder("security","import");
            args.add(id).add("-k",keychainPath);
            args.add("-P").addMasked(dp.getPassword().getPlainText());
            args.add("-T","/usr/bin/codesign");
            args.add("-T","/usr/bin/productsign");
            args.add(keychainPath);
            invoke(launcher, listener, args, "Failed to import identity "+id);
        }

        {
            // display keychain info for potential troubleshooting
            args = new ArgumentListBuilder("security","show-keychain-info");
            args.add(keychainPath);
            ByteArrayOutputStream output = invoke(launcher, listener, args, "Failed to show keychain info");
            listener.getLogger().write(output.toByteArray());
        }

	if ( importIntoExistingKeychain == null || !importIntoExistingKeychain ) {
	    importAppleCert(launcher, listener, workspace);
	}

        // copy provisioning profiles
        VirtualChannel ch = launcher.getChannel();
        FilePath home = ch.call(new GetHomeDirectory());    // TODO: switch to FilePath.getHomeDirectory(ch) when we can
        FilePath profiles = home.child("Library/MobileDevice/Provisioning Profiles");
        profiles.mkdirs();

        for (FilePath mp : secret.list("**/*.mobileprovision")) {
            listener.getLogger().println(Messages.DeveloperProfile_Installing(mp.getName()));
            mp.copyTo(profiles.child(mp.getName()));
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        perform(build, build.getWorkspace(), launcher, listener);

        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public GlobalConfigurationImpl getGlobalConfiguration() {
        return getDescriptor().getGlobalConfiguration();
    }

    public Keychain getKeychain() {
        if ( !StringUtils.isEmpty(keychainName) ) {
            for ( Keychain keychain : getGlobalConfiguration().getKeychains() ) {
                if ( keychain.getKeychainName().equals(keychainName) )
                    return keychain;
            }
        }

        if ( !StringUtils.isEmpty(keychainPath) ) {
            return new Keychain("", keychainPath, keychainPwd, false);
        }

        return null;
    }

    public void importAppleCert(Launcher launcher, TaskListener listener, FilePath workspace) throws IOException, InterruptedException {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	FilePath homeFolder = workspace.getHomeDirectory(workspace.getChannel());
	String homePath = homeFolder.getRemote();
	String cert = homePath + "/AppleWWDRCA.cer";
	launcher
	    .launch()
	    .cmds("security", "import", cert, "-k", keychainPath)
	    .stdout(out)
	    .join();
	listener.getLogger().write(out.toByteArray());
    }

    private ByteArrayOutputStream invoke(Launcher launcher, TaskListener listener, ArgumentListBuilder args, String errorMessage) throws IOException, InterruptedException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (launcher.launch().cmds(args).stdout(output).join()!=0) {
            listener.getLogger().write(output.toByteArray());
            throw new AbortException(errorMessage);
        }
        return output;
    }

    private FilePath getSecretDir(FilePath workspace, String keychainPwd) throws IOException, InterruptedException {
        FilePath secrets = workspace.child("jenkins").child("developer-profiles");
        secrets.mkdirs();
        secrets.chmod(0700);
        return secrets.child(keychainPwd);
    }

    public DeveloperProfile getProfile(Item context) {
        List<DeveloperProfile> profiles = CredentialsProvider
                .lookupCredentials(DeveloperProfile.class, context, Jenkins.getAuthentication());
        for (DeveloperProfile c : profiles) {
            if (c.getId().equals(profileId)) {
                return c;
            }
        }
        // if there's no match, just go with something in the hope that it'll do
        return !profiles.isEmpty() ? profiles.get(0) : null;
    }

    public String getProfileId() {
        return profileId;
    }

    @Extension
    @Symbol("importDeveloperProfile")
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
	GlobalConfigurationImpl globalConfiguration;

        @SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
        @Inject
        void setGlobalConfiguration(GlobalConfigurationImpl c) {
            this.globalConfiguration = c;
	}

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.DeveloperProfile_ImportDeveloperProfile();
        }

        public ListBoxModel doFillProfileIdItems(@AncestorInPath Item context) {
            List<DeveloperProfile> profiles = CredentialsProvider
                    .lookupCredentials(DeveloperProfile.class, context, null);
            ListBoxModel r = new ListBoxModel();
            for (DeveloperProfile p : profiles) {
                r.add(p.getDescription(), p.getId());
            }
            return r;
        }

        public GlobalConfigurationImpl getGlobalConfiguration() {
            return globalConfiguration;
        }

        public String getUUID() {
            return "" + UUID.randomUUID().getMostSignificantBits();
        }
    }

    private static final class GetHomeDirectory extends MasterToSlaveCallable<FilePath,IOException> {
        public FilePath call() throws IOException {
            return new FilePath(new File(System.getProperty("user.home")));
        }

        @Override
        public void checkRoles(RoleChecker roleChecker) throws SecurityException {

        }
    }
}
