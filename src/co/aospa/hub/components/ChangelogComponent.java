/*
 * Copyright (C) 2022 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.aospa.hub.components;

import java.io.File;

public class ChangelogComponent extends Component {

    private String changelog;
    private String version;
    private String versionNumber;
    private String buildType;
    private String id;

    public String getChangelog() {
        return this.changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
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
    public void setAndroidVersion(String androidVersion) { }

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

    @Override
    public String getDeviceChangelog() {
        return null;
    }

    @Override
    public void setDeviceChangelog(String deviceChangelog) { }
}
