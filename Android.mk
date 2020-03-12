LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_USE_AAPT2 := true

LOCAL_STATIC_ANDROID_LIBRARIES := \
    com.google.android.material_material \
    androidx.core_core \
    androidx.appcompat_appcompat \
    androidx.cardview_cardview \
    androidx.preference_preference \
    androidx.recyclerview_recyclerview \

LOCAL_RESOURCE_DIR := \
    $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := ParanoidHub
LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRODUCT_MODULE := true
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include frameworks/base/packages/SettingsLib/common.mk

include $(BUILD_PACKAGE)
