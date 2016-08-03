LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := com_comp4905_jasonfleischer_midimusic_audio_NDKFunct
LOCAL_CFLAGS := -std=c++11 -fexceptions
LOCAL_LDFLAGS := -Wl,--build-id
LOCAL_LDLIBS := \
	-llog \

LOCAL_SRC_FILES := \
	/Users/SILVER/Documents/CCCU/Year2/SemesterB/AST20613CSD/FYP/TrueTone/app/src/main/jni/Android.mk \
	/Users/SILVER/Documents/CCCU/Year2/SemesterB/AST20613CSD/FYP/TrueTone/app/src/main/jni/Application.mk \
	/Users/SILVER/Documents/CCCU/Year2/SemesterB/AST20613CSD/FYP/TrueTone/app/src/main/jni/com_comp4905_jasonfleischer_midimusic_audio_NDKFunct.cpp \

LOCAL_C_INCLUDES += /Users/SILVER/Documents/CCCU/Year2/SemesterB/AST20613CSD/FYP/TrueTone/app/src/main/jni
LOCAL_C_INCLUDES += /Users/SILVER/Documents/CCCU/Year2/SemesterB/AST20613CSD/FYP/TrueTone/app/src/debug/jni

include $(BUILD_SHARED_LIBRARY)
