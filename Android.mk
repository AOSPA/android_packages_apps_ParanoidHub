LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SDK_VERSION := current
LOCAL_PACKAGE_NAME := ParanoidHub
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-v4 \
    android-support-design \
    android-support-v7-appcompat \
    countly \
    volley

LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res \
    frameworks/support/v7/appcompat/res \
    frameworks/support/design/res

LOCAL_AAPT_FLAGS := --auto-add-overlay \
    --extra-packages android.support.v4 \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.design

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    countly:/libs/countly.jar

include $(BUILD_MULTI_PREBUILT)
