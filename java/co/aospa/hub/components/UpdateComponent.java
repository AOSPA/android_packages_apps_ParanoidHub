package co.aospa.hub.components;

import co.aospa.hub.ui.State;

public class UpdateComponent extends Component {

    private String fileName;
    private long timestamp;
    private long fileSize;
    private String downloadUrl;
    private String version;
    private String versionNumber;
    private String buildType;
    private String id;

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getDownloadUrl() {
        return this.downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
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
    public String getId() {
        return this.id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }
}
