/*
 * Copyright (C) 2020 Paranoid Android
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
package co.aospa.hub.model;

public class Changelog {

    private String mVersion;
    private String mVersionNumber;
    private String mBuildType;
    private String mId;
    private String mChangelog;
    private String mChangelogBrief;

    public Changelog() {
    }

    public void setVersion(String version) {mVersion = version;}

    public void setVersionNumber(String versionNumber) {mVersionNumber = versionNumber;}

    public void setBuildType(String buildType) {mBuildType = buildType;}

    public void setId(String id) {mId = id;}

    public void setChangelog(String changelog) {mChangelog = changelog;}

    public void setChangelogBrief(String changelog) {mChangelogBrief = changelog;}

    public String getVersion() {return mVersion;}

    public String getVersionNumber() {return mVersionNumber;}

    public String getBuildType() {return mBuildType;}

    public String getId() {return mId;}

    public String get() {return mChangelog;}

    public String getBrief() {return mChangelogBrief;}

}
