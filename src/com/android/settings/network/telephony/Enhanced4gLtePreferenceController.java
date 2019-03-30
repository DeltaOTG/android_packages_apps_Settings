/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.network.telephony;

import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.ims.ImsManager;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference controller for "Enhanced 4G LTE"
 */
public class Enhanced4gLtePreferenceController extends TelephonyTogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private Preference mPreference;
    private TelephonyManager mTelephonyManager;
    private CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mCarrierConfig;
    @VisibleForTesting
    ImsManager mImsManager;
    private PhoneCallStateListener mPhoneStateListener;
    private final List<On4gLteUpdateListener> m4gLteListeners;
    private final CharSequence[] mVariantTitles;
    private final CharSequence[] mVariantSumaries;

    public Enhanced4gLtePreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        m4gLteListeners = new ArrayList<>();
        mPhoneStateListener = new PhoneCallStateListener(Looper.getMainLooper());
        mVariantTitles = context.getResources()
                .getTextArray(R.array.enhanced_4g_lte_mode_title_variant);
        mVariantSumaries = context.getResources()
                .getTextArray(R.array.enhanced_4g_lte_mode_sumary_variant);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(subId);
        final boolean isVisible = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && mImsManager != null && carrierConfig != null
                && mImsManager.isVolteEnabledByPlatform()
                && mImsManager.isVolteProvisionedOnDevice()
                && MobileNetworkUtils.isImsServiceStateReady(mImsManager)
                && !carrierConfig.getBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL);
        return isVisible
                ? (is4gLtePrefEnabled() ? AVAILABLE : AVAILABLE_UNSEARCHABLE)
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        mPhoneStateListener.register(mSubId);
    }

    @Override
    public void onStop() {
        mPhoneStateListener.unregister();
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        final SwitchPreference switchPreference = (SwitchPreference) preference;
        final int variant4glteTitleIndex = mCarrierConfig.getInt(
                CarrierConfigManager.KEY_ENHANCED_4G_LTE_TITLE_VARIANT_INT);

        // Default index 0 indicates the default title/sumary string
        CharSequence enhanced4glteModeTitle = mVariantTitles[0];
        CharSequence enhanced4glteModeSummary = mVariantSumaries[0];
        if (variant4glteTitleIndex >= 0 && variant4glteTitleIndex < mVariantTitles.length) {
            enhanced4glteModeTitle = mVariantTitles[variant4glteTitleIndex];
        }
        if (variant4glteTitleIndex >= 0 && variant4glteTitleIndex < mVariantSumaries.length) {
            enhanced4glteModeSummary = mVariantSumaries[variant4glteTitleIndex];
        }
        switchPreference.setTitle(enhanced4glteModeTitle);
        switchPreference.setSummary(enhanced4glteModeSummary);
        switchPreference.setEnabled(is4gLtePrefEnabled());
        switchPreference.setChecked(mImsManager.isEnhanced4gLteModeSettingEnabledByUser()
                && mImsManager.isNonTtyOrTtyOnVolteEnabled());
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mImsManager.setEnhanced4gLteModeSetting(isChecked);
        for (final On4gLteUpdateListener lsn : m4gLteListeners) {
            lsn.on4gLteUpdated();
        }
        return true;
    }

    @Override
    public boolean isChecked() {
        return mImsManager.isEnhanced4gLteModeSettingEnabledByUser();
    }

    public Enhanced4gLtePreferenceController init(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mImsManager = ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(mSubId));
        }

        return this;
    }

    public Enhanced4gLtePreferenceController addListener(On4gLteUpdateListener lsn) {
        m4gLteListeners.add(lsn);
        return this;
    }

    private boolean is4gLtePrefEnabled() {
        return mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && mTelephonyManager.getCallState(mSubId) == TelephonyManager.CALL_STATE_IDLE
                && mImsManager != null
                && mImsManager.isNonTtyOrTtyOnVolteEnabled()
                && mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL);
    }

    private class PhoneCallStateListener extends PhoneStateListener {

        public PhoneCallStateListener(Looper looper) {
            super(looper);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            updateState(mPreference);
        }

        public void register(int subId) {
            mSubId = subId;
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
        }

        public void unregister() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }

    /**
     * Update other preferences when 4gLte state is changed
     */
    public interface On4gLteUpdateListener {
        void on4gLteUpdated();
    }
}