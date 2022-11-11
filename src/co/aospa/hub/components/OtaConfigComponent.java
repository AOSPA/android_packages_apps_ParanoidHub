package co.aospa.hub.components;

import java.io.File;

public class OtaConfigComponent extends Component {

    private boolean mEnabled;
    private boolean mWhitelistOnly;

    public void setOtaEnabled(String otaEnabled) {
        boolean enabled = Boolean.parseBoolean(otaEnabled);
        if (enabled != mEnabled) {
            mEnabled = enabled;
        }
    }

    public void setOtaWhitelistOnly(String otaWhitelistOnly) {
        boolean whitelistOnly = Boolean.parseBoolean(otaWhitelistOnly);
        if (whitelistOnly != mWhitelistOnly) {
            mWhitelistOnly = whitelistOnly;
        }
    }

    public boolean isEnabledFromServer() {
        return mEnabled;
    }

    public boolean isWhitelistOnly() {
        return mWhitelistOnly;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public void setVersion(String version) { }

    @Override
    public String getVersionNumber() {
        return null;
    }

    @Override
    public void setVersionNumber(String versionNumber) { }

    @Override
    public String getBuildType() {
        return null;
    }

    @Override
    public void setBuildType(String buildType) { }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public void setId(String id) { }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public void setFile(File file) { }
}
