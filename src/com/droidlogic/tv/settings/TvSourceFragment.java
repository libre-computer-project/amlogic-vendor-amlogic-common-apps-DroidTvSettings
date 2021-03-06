/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.droidlogic.tv.settings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.Log;

import com.droidlogic.tv.settings.SettingsConstant;
import com.droidlogic.tv.settings.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.droidlogic.app.tv.TvControlManager;
import com.droidlogic.app.tv.DroidLogicTvUtils;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiTvClient;
import android.media.tv.TvInputHardwareInfo;
import java.util.ArrayList;

public class TvSourceFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "TvSourceFragment";
    private static final boolean DEBUG = true;

    private static final int MODE_GLOBAL = 0;
    private static final int MODE_LIVE_TV = 1;
    private int mStartMode = -1;
    private String mStartPackage = null;
    private boolean needDTV = false;

    private final String COMMANDACTION = "action.startlivetv.settingui";
    private static final String DROIDLOGIC_TVINPUT = "com.droidlogic.tvinput";
    private static final int RESULT_OK = -1;
    private int DEV_TYPE_AUDIO_SYSTEM = 5;

    private TvInputManager mTvInputManager;
    private TvControlManager mTvControlManager;
    private HdmiControlManager mHdmiControlManager;
    private HdmiTvClient mTvClient;
    private final InputsComparator mComparator = new InputsComparator();
    private Context mContext;

    public static TvSourceFragment newInstance(Context context) {
        return new TvSourceFragment(context);
    }

    // if Fragment has no nullary constructor, it might throw InstantiationException, so add this constructor.
    // For more details, you can visit http://blog.csdn.net/xplee0576/article/details/43057633 .
    public TvSourceFragment() {}

    public TvSourceFragment(Context context) {
        mContext = context;
        mTvInputManager = (TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);
        mTvControlManager = TvControlManager.getInstance();
        mHdmiControlManager = (HdmiControlManager) context.getSystemService(Context.HDMI_CONTROL_SERVICE);
        if (mHdmiControlManager != null)
            mTvClient = mHdmiControlManager.getTvClient();
    }

    static public boolean CanDebug() {
        return SystemProperties.getBoolean("sys.tvoption.debug", false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Intent intent = null;

        if (mContext != null) {
            intent = ((Activity)mContext).getIntent();
        }

        if (DEBUG)  Log.d(TAG, "onCreatePreferences  intent= "+ intent);
        if (intent != null) {
            mStartMode = intent.getIntExtra("from_live_tv", -1);
            mStartPackage = intent.getStringExtra("requestpackage");
        }
        updatePreferenceFragment();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
            final Preference sourcePreference = preference;
            List<TvInputInfo> inputList = mTvInputManager.getTvInputList();
            for (TvInputInfo input : inputList) {
                if (!input.getId().contains(DROIDLOGIC_TVINPUT)) {
                    continue;
                }

                if (sourcePreference.getKey().equals(input.getId())) {
                    if (DEBUG) Log.d(TAG, "onPreferenceTreeClick:  info=" + input);
                    if (TextUtils.equals(sourcePreference.getTitle(), mContext.getResources().getString(R.string.input_atv))) {
                        DroidLogicTvUtils.setSearchType(mContext, 0);
                    } else if (TextUtils.equals(sourcePreference.getTitle(), mContext.getResources().getString(R.string.input_dtv))) {
                        DroidLogicTvUtils.setSearchType(mContext, 1);
                    }
                    Settings.System.putInt(mContext.getContentResolver(), DroidLogicTvUtils.TV_CURRENT_DEVICE_ID,
                            DroidLogicTvUtils.getHardwareDeviceId(input));
                    if (mStartMode == 1) {
                        Intent intent = new Intent();
                        intent.setAction(COMMANDACTION);
                        intent.putExtra("from_tv_source", true);
                        intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, input.getId());
                        getActivity().sendBroadcast(intent);
                    } else {
                        Intent intent = new Intent(TvInputManager.ACTION_SETUP_INPUTS);
                        intent.putExtra("from_tv_source", true);
                        intent.putExtra(TvInputInfo.EXTRA_INPUT_ID, input.getId());
                        if (mStartPackage != null && mStartPackage.equals("com.droidlogic.mboxlauncher")) {
                            ((Activity)mContext).setResult(RESULT_OK, intent);
                        } else {
                            getPreferenceManager().getContext().startActivity(intent);
                        }
                    }
                   /* if (mStartMode == MODE_LIVE_TV) {
                        ((Activity)mContext).setResult(Activity.RESULT_OK, intent);
                        ((Activity)mContext).finish();
                    } else {
                        getPreferenceManager().getContext().startActivity(intent);
                    }*/
                    // getPreferenceManager().getContext().startActivity(intent);
                   ((Activity)mContext).finish();
                }
            }
        return super.onPreferenceTreeClick(preference);
    }

    private void updatePreferenceFragment() {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(
                themedContext);
        screen.setTitle(R.string.tv_source);
        setPreferenceScreen(screen);

        List<TvInputInfo> inputList = mTvInputManager.getTvInputList();
        Collections.sort(inputList, mComparator);
        List<Preference> preferenceList = new ArrayList<Preference>();
        TvInputInfo dtvInputInfo = null;
        for (TvInputInfo input : inputList) {
            if (!input.getId().contains(DROIDLOGIC_TVINPUT)) {
                continue;
            }

            Preference sourcePreference = new Preference(themedContext);
            sourcePreference.setKey(input.getId());
            sourcePreference.setPersistent(false);
            sourcePreference.setIcon(getIcon(input, isInputEnabled(input)));
            if (input.isPassthroughInput()) {
                if (input.isHidden(themedContext)) {
                    continue;
                }
                CharSequence label = input.loadLabel(themedContext);
                CharSequence customLabel = input.loadCustomLabel(themedContext);
                if (input.getParentId() == null) {
                    for (TvInputInfo inputInfo : inputList) {
                        HdmiDeviceInfo hdmiDeviceInfo = inputInfo.getHdmiDeviceInfo();
                        String parentId = inputInfo.getParentId();
                        if (parentId != null && hdmiDeviceInfo != null) {
                            int phyAddress = hdmiDeviceInfo.getPhysicalAddress();
                            //cascade exists, rename using device name connect directly
                            if ((input.getId().equals(parentId))&& (phyAddress != 0) && (phyAddress & 0xfff) == 0) {
                                customLabel = inputInfo.loadCustomLabel(themedContext);
                                label = inputInfo.loadLabel(themedContext);
                                Log.d(TAG, "HdmiCecDevice connected,set customLable: " + customLabel + " to its parent.");
                            }
                        }
                    }
                    HdmiDeviceInfo avrDeviceInfo = getHdmiAvrDevIfConnected(input);
                    if (avrDeviceInfo != null) {
                        customLabel = avrDeviceInfo.getDisplayName();
                        Log.d(TAG, "using HdmiCecDevice customLebel " + customLabel +" if avr connected.");
                    }
                } else {
                    continue;
                }
                if (TextUtils.isEmpty(customLabel) || customLabel.equals(label)) {
                    sourcePreference.setTitle(label);
                } else {
                    sourcePreference.setTitle(customLabel);
                }
                needDTV = false;
            } else {
                sourcePreference.setTitle(DroidLogicTvUtils.isChina(themedContext) ? R.string.input_atv : R.string.input_long_label_for_tuner);
                needDTV = true;
                dtvInputInfo = input;
            }
            preferenceList.add(sourcePreference);
            if (DroidLogicTvUtils.isChina(themedContext) && needDTV && dtvInputInfo != null) {
                Preference sourcePreferenceDtv = new Preference(themedContext);
                sourcePreferenceDtv.setKey(dtvInputInfo.getId());
                sourcePreferenceDtv.setPersistent(false);
                sourcePreferenceDtv.setIcon(R.drawable.ic_dtv_connected);
                sourcePreferenceDtv.setTitle(R.string.input_dtv);
                if (mTvControlManager.GetHotPlugDetectEnableStatus()) {
                    sourcePreferenceDtv.setEnabled(isInputEnabled(dtvInputInfo));
                }
                preferenceList.add(sourcePreferenceDtv);
            }
        }
        for (Preference sourcePreference : preferenceList) {
            screen.addPreference(sourcePreference);
        }
    }

    private HdmiDeviceInfo getHdmiAvrDevIfConnected(TvInputInfo input) {
        if (mTvInputManager == null ||  mTvClient == null) {
            Log.d(TAG, "mTvInputManager or mTvClient maybe null");
            return null;
        }
        HdmiDeviceInfo avrDeviceInfo = null;
        int portId = 0;
        int deviceId = DroidLogicTvUtils.getHardwareDeviceId(input);
        for (TvInputHardwareInfo tvInputHardwareInfo : mTvInputManager.getHardwareList()) {
            if ((deviceId >= DroidLogicTvUtils.DEVICE_ID_HDMI1) && (deviceId <= DroidLogicTvUtils.DEVICE_ID_HDMI4) && (tvInputHardwareInfo.getDeviceId() == deviceId)) {
                portId = tvInputHardwareInfo.getHdmiPortId();
                break;
            }
        }
        for (HdmiDeviceInfo info : mTvClient.getDeviceList()) {
            if (portId == ((int)info.getPortId()) && (info.getLogicalAddress() == DEV_TYPE_AUDIO_SYSTEM)) {
                avrDeviceInfo = info;
                break;
            }
        }
        return avrDeviceInfo;
    }

    private boolean isInputEnabled(TvInputInfo input) {
        HdmiDeviceInfo hdmiInfo = input.getHdmiDeviceInfo();
        if (hdmiInfo != null) {
            if (DEBUG) Log.d(TAG, "isInputEnabled:  hdmiInfo="+ hdmiInfo);
            return true;
        }

        int deviceId = DroidLogicTvUtils.getHardwareDeviceId(input);
        if (DEBUG) {
            Log.d(TAG, "===== getHardwareDeviceId:tvInputId = " + input.getId());
            Log.d(TAG, "===== deviceId : "+ deviceId);
        }
        TvControlManager.SourceInput tvSourceInput = DroidLogicTvUtils.parseTvSourceInputFromDeviceId(deviceId);
        int connectStatus = -1;
        if (tvSourceInput != null) {
            connectStatus = mTvControlManager.GetSourceConnectStatus(tvSourceInput);
        } else {
            if (DEBUG) {
                Log.w(TAG, "===== cannot find tvSourceInput");
            }
        }

        return !input.isPassthroughInput() || 1 == connectStatus || deviceId == DroidLogicTvUtils.DEVICE_ID_SPDIF;
    }

    private class InputsComparator implements Comparator<TvInputInfo> {
        @Override
        public int compare(TvInputInfo lhs, TvInputInfo rhs) {
            if (lhs == null) {
                return (rhs == null) ? 0 : 1;
            }
            if (rhs == null) {
                return -1;
            }

           /* boolean enabledL = isInputEnabled(lhs);
            boolean enabledR = isInputEnabled(rhs);
            if (enabledL != enabledR) {
                return enabledL ? -1 : 1;
            }*/

            int priorityL = getPriority(lhs);
            int priorityR = getPriority(rhs);
            if (priorityL != priorityR) {
                return priorityR - priorityL;
            }

            String customLabelL = (String) lhs.loadCustomLabel(getContext());
            String customLabelR = (String) rhs.loadCustomLabel(getContext());
            if (!TextUtils.equals(customLabelL, customLabelR)) {
                customLabelL = customLabelL == null ? "" : customLabelL;
                customLabelR = customLabelR == null ? "" : customLabelR;
                return customLabelL.compareToIgnoreCase(customLabelR);
            }

            String labelL = (String) lhs.loadLabel(getContext());
            String labelR = (String) rhs.loadLabel(getContext());
            labelL = labelL == null ? "" : labelL;
            labelR = labelR == null ? "" : labelR;
            return labelL.compareToIgnoreCase(labelR);
        }

        private int getPriority(TvInputInfo info) {
            switch (info.getType()) {
                case TvInputInfo.TYPE_TUNER:
                    return 9;
                case TvInputInfo.TYPE_HDMI:
                    HdmiDeviceInfo hdmiInfo = info.getHdmiDeviceInfo();
                    if (hdmiInfo != null && hdmiInfo.isCecDevice()) {
                        return 8;
                    }
                    return 7;
                case TvInputInfo.TYPE_DVI:
                    return 6;
                case TvInputInfo.TYPE_COMPONENT:
                    return 5;
                case TvInputInfo.TYPE_SVIDEO:
                    return 4;
                case TvInputInfo.TYPE_COMPOSITE:
                    return 3;
                case TvInputInfo.TYPE_DISPLAY_PORT:
                    return 2;
                case TvInputInfo.TYPE_VGA:
                    return 1;
                case TvInputInfo.TYPE_SCART:
                default:
                    return 0;
            }
        }
    }

    private int getIcon(TvInputInfo info, boolean isConnected) {
        switch (info.getType()) {
            case TvInputInfo.TYPE_TUNER:
                if (isConnected) {
                    return DroidLogicTvUtils.isChina(mContext) ? R.drawable.ic_atv_connected : R.drawable.ic_atsc_connected;
                } else {
                    return DroidLogicTvUtils.isChina(mContext) ? R.drawable.ic_atv_disconnected : R.drawable.ic_atsc_disconnected;
                }
            case TvInputInfo.TYPE_HDMI:
                if (isConnected) {
                    return R.drawable.ic_hdmi_connected;
                } else {
                    return R.drawable.ic_hdmi_disconnected;
                }
            case TvInputInfo.TYPE_COMPOSITE:
                if (isConnected) {
                    return R.drawable.ic_av_connected;
                } else {
                    return R.drawable.ic_av_disconnected;
                }
            default:
                 if (isConnected) {
                    return R.drawable.ic_spdif_connected;
                } else {
                    return R.drawable.ic_spdif_disconnected;
                }
         }
     }
}
