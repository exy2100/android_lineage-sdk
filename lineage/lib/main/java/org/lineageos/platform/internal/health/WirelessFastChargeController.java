/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.health;

import android.content.res.Resources;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.health.LineageHealthFeature;
import org.lineageos.platform.internal.R;

import vendor.lineage.health.WirelessFastChargeMode;
import vendor.lineage.health.IWirelessFastCharge;

import java.io.PrintWriter;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

public class WirelessFastChargeController extends LineageHealthFeature {
    private final int[] mChargingSpeedValues;
    private final ContentResolver mContentResolver;
    private final IWirelessFastCharge mWirelessFastCharge;

    // Settings uris
    private final Uri MODE_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.WIRELESS_FAST_CHARGE_MODE);

    public WirelessFastChargeController(Context context, Handler handler) {
        super(context, handler);

        mContentResolver = mContext.getContentResolver();
        mWirelessFastCharge = IWirelessFastCharge.Stub.asInterface(
                ServiceManager.waitForDeclaredService(
                        IWirelessFastCharge.DESCRIPTOR + "/default"));

        Resources res = mContext.getResources();
        mChargingSpeedValues = Stream.of(res.getStringArray(R.array.charging_speed_values))
                .mapToInt(Integer::parseInt)
                .toArray();

        if (mWirelessFastCharge == null) {
            Log.i(TAG, "Lineage Health HAL not found");
            return;
        }
    }

    @Override
    public boolean isSupported() {
        try {
            return mWirelessFastCharge != null && mWirelessFastCharge.getSupportedWirelessFastChargeModes() > 0;
        } catch (RemoteException e) {
            return false;
        }
    }

    public int[] getSupportedWirelessFastChargeModes() {
        try {
            long supportedWirelessFastChargeModes = mWirelessFastCharge.getSupportedWirelessFastChargeModes();

            return IntStream.of(mChargingSpeedValues)
                    .filter(mode -> (supportedWirelessFastChargeModes & mode) != 0)
                    .toArray();
        } catch (RemoteException e) {
            return new int[0];
        }
    }

    public int getWirelessFastChargeMode() {
        int[] supportedWirelessFastChargeModes = getSupportedWirelessFastChargeModes();
        int defaultMode = supportedWirelessFastChargeModes[supportedWirelessFastChargeModes.length - 1];

        int mode = LineageSettings.System.getInt(mContentResolver,
                LineageSettings.System.WIRELESS_FAST_CHARGE_MODE,
                defaultMode);
        if (mode != defaultMode && !ArrayUtils.contains(supportedWirelessFastChargeModes, mode)) {
            return defaultMode;
        }

        return mode;
    }

    public boolean setWirelessFastChargeMode(int mode) {
        putInt(LineageSettings.System.WIRELESS_FAST_CHARGE_MODE, mode);
        return true;
    }

    @Override
    public void onStart() {
        if (mWirelessFastCharge == null) {
            return;
        }

        // Register setting observer
        registerSettings(MODE_URI);

        handleSettingChange();
    }

    private void handleSettingChange() {
        try {
            mWirelessFastCharge.setWirelessFastChargeMode(getWirelessFastChargeMode());
        } catch (Exception e) {
        }
    }

    @Override
    protected void onSettingsChanged(Uri uri) {
        handleSettingChange();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("WirelessFastChargeController Configuration:");
        pw.println("  Mode: " + getWirelessFastChargeMode());
        pw.println();
    }
}
