package au.com.rayh;

import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class Keychain {
    private String keychainName;
    private String keychainPath;
    private Secret keychainPassword;
    private Boolean inSearchPath;

    public Keychain() {
    }

    @DataBoundConstructor
    public Keychain(String keychainName, String keychainPath, Secret keychainPassword, Boolean inSearchPath) {
        this.keychainName = keychainName;
        this.keychainPath = keychainPath;
        this.keychainPassword = keychainPassword;
        this.inSearchPath = inSearchPath;
    }

    public String getKeychainName() {
        return keychainName;
    }

    public void setKeychainName(String keychainName) {
        this.keychainName = keychainName;
    }

    public String getKeychainPath() {
        return keychainPath;
    }

    public void setKeychainPath(String keychainPath) {
        this.keychainPath = keychainPath;
    }

    public Secret getKeychainPassword() {
        return keychainPassword;
    }

    public void setKeychainPassword(Secret keychainPassword) {
        this.keychainPassword = keychainPassword;
    }

    public Boolean isInSearchPath() {
        return inSearchPath;
    }

    public void setInSearchPath(Boolean inSearchPath) {
        this.inSearchPath = inSearchPath;
    }

}
