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
package co.aospa.hub.util

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import android.text.TextUtils

class PreferenceHelper(context: Context) {

    private val mContext: Context = context

    @SuppressLint("ApplySharedPref")
    fun saveIntValue(key: String?, value: Int) {
       PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putInt(key, value)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun saveBooleanValue(key: String?, value: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putBoolean(key, value)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun saveLongValue(key: String?, value: Long) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putLong(key, value)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun removeSharePreferences(value: String?) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .remove(value)
            .commit()
    }

    fun getIntValueByKey(key: String?, value: Int): Int {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
            .getInt(key, value)
    }

    fun getIntValueByKey(key: String?): Int {
        return getIntValueByKey(key, -1)
    }

    fun getLongValueByKey(key: String?, value: Long): Long {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
            .getLong(key, value)
    }

    fun getLongValueByKey(key: String?): Long {
        return getLongValueByKey(key, -1)
    }

    @SuppressLint("ApplySharedPref")
    fun saveStringValue(key: String?, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putString(key, value)
            .commit()
    }

    fun getStringValueByKey(key: String?): String? {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
            .getString(key, null)
    }

    @SuppressLint("ApplySharedPref")
    fun saveByteArrayValue(key: String?, value: ByteArray?) {
        PreferenceManager.getDefaultSharedPreferences(mContext)
            .edit()
            .putString(key, String(value!!))
            .commit()
    }

    fun getByteArrayValueByKey(key: String?): ByteArray? {
        val string = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getString(key, null)
        return if (TextUtils.isEmpty(string)) {
            null
        } else string!!.toByteArray()
    }

    fun getBooleanValueByKey(key: String?): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(mContext)
            .getBoolean(key, false)
    }

}