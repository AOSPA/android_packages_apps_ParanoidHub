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

    private String mVersionMajor;
    private String mVersionMinor;
    private String mBuildVariant;
    private String mId;
    private String mChangelog;

    public Changelog() {
    }

    public void setVersionMajor(String versionMajor) {mVersionMajor = version;}

    public void setVersionMinor(String versionMinor) {mVersionMinor = versionMinor;}

    public void setBuildVariant(String buildVariant) {mBuildVariant = BuildVariant;}

    public void setId(String id) {mId = id;}

    public void setChangelog(String changelog) {mChangelog = changelog;}

    public String getVersionMajor() {return mVersionMajor;}

    public String getVersionMinor() {return mVersionMinor;}

    public String getBuildVariant() {return mBuildVariant;}

    public String getId() {return mId;}

    public String get() {return mChangelog;}

}
