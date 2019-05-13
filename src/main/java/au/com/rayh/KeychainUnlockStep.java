package au.com.rayh;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.Secret;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
public class KeychainUnlockStep extends Builder implements SimpleBuildStep {
    @CheckForNull
    private String keychainName;
    @CheckForNull
    private String keychainPath;
    @CheckForNull
    private Secret keychainPwd;

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
    public Secret getKeychainPwd() {
        return keychainPwd;
    }

    @DataBoundSetter
    public void setKeychainPwd(Secret keychainPwd) {
        this.keychainPwd = keychainPwd;
    }

    @DataBoundConstructor
    public KeychainUnlockStep(String keychainName) {
	this.keychainName = keychainName;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
	EnvVars envs = run.getEnvironment(listener);
	String _keychainName = envs.expand(this.keychainName);
	String _keychainPath = envs.expand(this.keychainPath);
	String _keychainPwd = envs.expand(Secret.toString(this.keychainPwd));

        Keychain keychain = getKeychain(_keychainName);
        _keychainPath = envs.expand(keychain.getKeychainPath());
        _keychainPwd = envs.expand(Secret.toString(keychain.getKeychainPassword()));

        ArgumentListBuilder args;

        args = new ArgumentListBuilder("security", "unlock-keychain");
        args.add("-p").addMasked(_keychainPwd);
        args.add(_keychainPath);
        invoke(launcher, listener, args, "Failed to unlock keychain");
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

    public Keychain getKeychain(String keychainName) {
        if ( !StringUtils.isEmpty(keychainName) ) {
            for ( Keychain keychain : getGlobalConfiguration().getKeychains() ) {
                if ( keychain.getKeychainName().equals(keychainName) )
                    return keychain;
            }
        }

        if ( !StringUtils.isEmpty(this.keychainPath) ) {
            Keychain newKeychain = new Keychain();
            newKeychain.setKeychainPassword(this.keychainPwd);
            return newKeychain;
        }

        return null;
    }

    private ByteArrayOutputStream invoke(Launcher launcher, TaskListener listener, ArgumentListBuilder args, String errorMessage) throws IOException, InterruptedException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (launcher.launch().cmds(args).stdout(output).join()!=0) {
            listener.getLogger().write(output.toByteArray());
            throw new AbortException(errorMessage);
        }
        return output;
    }

    @Extension
    @Symbol("unlockMacOSKeychain")
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
            return Messages.OSXKeychainUnclockStep_DisplayName();
        }

        public GlobalConfigurationImpl getGlobalConfiguration() {
            return globalConfiguration;
        }

        public String getUUID() {
            return "" + UUID.randomUUID().getMostSignificantBits();
        }

        public FormValidation doCheckKeychainPath(@QueryParameter String value, @QueryParameter String keychainName) {
            if ( StringUtils.isEmpty(keychainName) && StringUtils.isEmpty(value) ) {
                return FormValidation.error(Messages.DeveloperProfileLoader_MustSpecifyKeychainPath());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckKeychainPwd(@QueryParameter Secret value, @QueryParameter String keychainName) {
            if ( StringUtils.isEmpty(keychainName) && StringUtils.isEmpty(Secret.toString(value)) ) {
                return FormValidation.error(Messages.DeveloperProfileLoader_MustSpecifyKeychainPwd());
            }
            return FormValidation.ok();
        }
    }
}
