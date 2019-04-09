package info.nightscout.android.eula;
/*
 * Copyright (C) 2008 The Android Open Source Project
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


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import info.nightscout.android.R;

/**
 * Displays an EULA ("End User License Agreement") that the user has to accept before
 * using the application. Your application should call {@link Eula#show(android.app.Activity, SharedPreferences)}
 * in the onCreate() method of the first activity. If the user accepts the EULA, it will never
 * be shown again. If the user refuses, {@link android.app.Activity#finish()} is invoked
 * on your activity.
 */
public class Eula {
    private static final String PREFERENCE_EULA_ACCEPTED = "IUNDERSTAND";
    private static AlertDialog aDialog = null;

    /**
     * callback to let the activity know when the user has accepted the EULA.
     */
    public interface OnEulaAgreedTo {

        /**
         * Called when the user has accepted the eula and the dialog closes.
         */
        void onEulaAgreedTo();
        /**
         * Called when the user has refused the eula and the dialog closes.
         */
        void onEulaRefusedTo();
    }

    /**
     * Displays the EULA if necessary. This method should be called from the onCreate()
     * method of your main Activity.
     *
     * @param activity The Activity to finish if the user rejects the EULA.
     * @return Whether the user has agreed already.
     */
    public static boolean show(final Activity activity, final SharedPreferences preferences ) {
        //final SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_EULA,
        //        Activity.MODE_PRIVATE);

        if (!preferences.getBoolean(PREFERENCE_EULA_ACCEPTED, false)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.eula__title);
            builder.setCancelable(true);
            builder.setPositiveButton(R.string.eula__accept, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    accept(preferences);
                    if (activity instanceof OnEulaAgreedTo) {
                        ((OnEulaAgreedTo) activity).onEulaAgreedTo();
                    }
                    aDialog.dismiss();
                    aDialog = null;
                }
            });
            builder.setNegativeButton(R.string.eula__refuse, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                	if (activity instanceof OnEulaAgreedTo) {
                        ((OnEulaAgreedTo) activity).onEulaRefusedTo();
                    }
                    refuse(activity);
                    aDialog.dismiss();
                    aDialog = null;
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                	if (activity instanceof OnEulaAgreedTo) {
                        ((OnEulaAgreedTo) activity).onEulaRefusedTo();
                    }
                    refuse(activity);
                    aDialog.dismiss();
                    aDialog = null;
                }
            });
            builder.setMessage(R.string.eula__message);
            aDialog = builder.create();
            aDialog.show();
           
            return false;
        }
        return true;
    }

    private static void accept(SharedPreferences preferences) {
        preferences.edit().putBoolean(PREFERENCE_EULA_ACCEPTED, true).apply();
    }

    private static void refuse(Activity activity) {
        activity.finish();
    }

}
