package co.aospa.hub.components;

import java.io.File;

import co.aospa.hub.ui.State;

public class ChangelogComponent extends Component {

    private String changelog;
    private String changelogBrief;
    private String version;
    private String versionNumber;
    private String buildType;
    private String androidSpl;
    private String id;

    public String getChangelog() {
        return this.changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public String getChangelogBrief() {
        return this.changelogBrief;
    }

    public void setChangelogBrief(String changelogBrief) {
        this.changelogBrief = changelogBrief;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getVersionNumber() {
        return this.versionNumber;
    }

    @Override
    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    @Override
    public String getBuildType() {
        return this.buildType;
    }

    @Override
    public void setBuildType(String buildType) {
        this.buildType = buildType;
    }

    @Override
    public String getAndroidVersion() {
        return null;
    }

    @Override
    public void setAndroidVersion(String androidVersion) {

    }

    @Override
    public String getAndroidSpl() {
        return null;
    }

    @Override
    public void setAndroidSpl(String androidSpl) { }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public void setFile(File file) { }
}
