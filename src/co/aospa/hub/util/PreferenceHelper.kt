package co.aospa.hub.util

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import android.text.TextUtils
import co.aospa.hub.HubApp

class PreferenceHelper(context: Context) {

    private val mContext: Context = context

    @SuppressLint("ApplySharedPref")
    fun saveIntValue(key: String?, value: Int) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.putInt(key, value)
        edit.commit()
    }

    @SuppressLint("ApplySharedPref")
    fun saveBooleanValue(key: String?, value: Boolean) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.putBoolean(key, value)
        edit.commit()
    }

    @SuppressLint("ApplySharedPref")
    fun saveLongValue(key: String?, value: Long) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.putLong(key, value)
        edit.commit()
    }

    @SuppressLint("ApplySharedPref")
    fun removeSharePreferences(value: String?) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.remove(value)
        edit.commit()
    }

    fun getIntValueByKey(key: String?, value: Int): Int {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getInt(key, value)
    }

    fun getIntValueByKey(key: String?): Int {
        return getIntValueByKey(key, -1)
    }

    fun getLongValueByKey(key: String?, value: Long): Long {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getLong(key, value)
    }

    fun getLongValueByKey(key: String?): Long {
        return getLongValueByKey(key, -1)
    }

    @SuppressLint("ApplySharedPref")
    fun saveStringValue(key: String?, value: String?) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.putString(key, value)
        edit.commit()
    }

    fun getStringValueByKey(key: String?): String? {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getString(key, null)
    }

    @SuppressLint("ApplySharedPref")
    fun saveByteArrayValue(key: String?, value: ByteArray?) {
        val edit = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
        edit.putString(key, String(value!!))
        edit.commit()
    }

    fun getByteArrayValueByKey(key: String?): ByteArray? {
        val string = PreferenceManager.getDefaultSharedPreferences(mContext).getString(key, null)
        return if (TextUtils.isEmpty(string)) {
            null
        } else string!!.toByteArray()
    }

    fun getBooleanValueByKey(key: String?): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(key, false)
    }

}