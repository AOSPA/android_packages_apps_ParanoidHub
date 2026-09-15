<!--
SPDX-FileCopyrightText: Paranoid Android
SPDX-License-Identifier: Apache-2.0
-->

ParanoidHub
===========
Downloads and applies OTA packages.


Server
------
A `GET` to `config_updateServerUrl`, with `ro.aospa.device` substituted in,
returns the builds available for the device:

```json
{
  "updates": [
    {
      "datetime": "1789732800",
      "filename": "aospa-calcite-unofficial-phone1-20260918.zip",
      "url": "https://example.org/aospa-calcite-unofficial-phone1-20260918.zip",
      "recovery_sha256": "619c399d0d335799ec83ad4ce75c4db66f858c87e34dfa8e50dbc6549a28ad67",
      "size": "2253000000",
      "build_type": "Unofficial",
      "version": "Calcite",
      "version_code": "21",
      "android_spl": "2026-09-05",
      "changelog_device": "Updated camera HAL, fixed fingerprint unlock after reboot"
    }
  ]
}
```

Every field is required except `android_spl` and `changelog_device`, numbers
arrive quoted, and one bad entry fails the whole response.

A build is dropped unless `build_type` matches `ro.aospa.build.variant` and
`datetime` is newer than `ro.build.date.utc`. The newest of the rest is
offered.

