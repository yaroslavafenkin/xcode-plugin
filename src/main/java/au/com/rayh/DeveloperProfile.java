package au.com.rayh;

import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Extension;
import hudson.util.IOUtils;
import hudson.util.Secret;
import jenkins.security.ConfidentialKey;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.UUID;

/**
 * Apple developer profile, which consists of any number of PKCS12 of the private key
 * and the certificate for code signing, and mobile provisioning profiles.
 *
 * @author Kohsuke Kawaguchi
 */
public class DeveloperProfile extends BaseCredentials {
    /**
     * Password of the PKCS12 files inside the profile.
     */
    private Secret password;

    /**
     * Random generated unique ID that identifies this developer profile among others.
     */
    private final String id;

    private final String description;

    @DataBoundConstructor
    public DeveloperProfile(String id, String description, Secret password, FileItem image) throws IOException {
        super(CredentialsScope.GLOBAL);
        if (id==null)
            id = UUID.randomUUID().toString();
        this.id = id;
        this.description = description;
        this.password= password;

        if (image!=null) {
            // for added secrecy, store this in the confidential store
            new ConfidentialKeyImpl(id).store(image);
        }
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Secret getPassword() {
        return password;
    }

    public byte[] getImage() throws IOException {
        return new ConfidentialKeyImpl(id).load();
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {
        @Override
        public String getDisplayName() {
            return "Apple Developer Profile";
        }
    }

    static class ConfidentialKeyImpl extends ConfidentialKey {
        ConfidentialKeyImpl(String id) {
            super(DeveloperProfile.class.getName()+"."+id);
        }

        public void store(FileItem submitted) throws IOException {
            super.store(IOUtils.toByteArray(submitted.getInputStream()));
        }

        public @CheckForNull byte[] load() throws IOException {
            return super.load();
        }
    }
}
