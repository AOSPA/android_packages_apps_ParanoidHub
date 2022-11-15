package co.aospa.hub.components;

import java.io.File;

import co.aospa.hub.ui.State;

public abstract class Component {

    public abstract String getVersion();
    public abstract void setVersion(String version);
    public abstract String getVersionNumber();
    public abstract void setVersionNumber(String versionNumber);
    public abstract String getBuildType();
    public abstract void setBuildType(String buildType);
    public abstract String getAndroidVersion();
    public abstract void setAndroidVersion(String androidVersion);
    public abstract String getAndroidSpl();
    public abstract void setAndroidSpl(String androidSpl);
    public abstract String getId();
    public abstract void setId(String id);
    public abstract File getFile();
    public abstract void setFile(File file);
}
