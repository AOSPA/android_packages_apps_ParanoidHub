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

    @Override
    public String getDeviceChangelog() {
        return null;
    }

    @Override
    public void setDeviceChangelog(String deviceChangelog) { }
}
