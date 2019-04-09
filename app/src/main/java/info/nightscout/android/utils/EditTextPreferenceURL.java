package info.nightscout.android.utils;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;

import info.nightscout.android.R;

/**
 * Created by lgoedhart on 29/05/2016.
 *
 * Code found at http://stackoverflow.com/a/31345545
 */
public class EditTextPreferenceURL extends EditTextPreference {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EditTextPreferenceURL(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public EditTextPreferenceURL(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EditTextPreferenceURL(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextPreferenceURL(Context context) {
        super(context);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        AlertDialog dlg = (AlertDialog) getDialog();
        View positiveButton = dlg.getButton(DialogInterface.BUTTON_POSITIVE);
        getEditText().setError(null);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPositiveButtonClicked(v);
            }
        });
    }

    private void onPositiveButtonClicked(View v) {
        String errorMessage = onValidate(getEditText().getText().toString());
        if (errorMessage == null) {
            getEditText().setError(null);
            onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            getDialog().dismiss();
        } else {
            getEditText().setError(errorMessage);
        }
    }


    /***
     * Called to validate contents of the edit text.
     * <p/>
     * Return null to indicate success, or return a validation error message to display on the edit text.
     *
     * @param text The text to validate.
     * @return An error message, or null if the value passes validation.
     */

    private String illegalCharacters = " \n";

    public String onValidate(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (illegalCharacters.contains(text.substring(i, i+1)))
                return getContext().getString(R.string.pref_error_text_illegal_characters);
        }

        return null;
    }
}