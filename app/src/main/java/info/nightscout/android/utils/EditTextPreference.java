package info.nightscout.android.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;

import androidx.annotation.NonNull;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;

import info.nightscout.android.R;

public class EditTextPreference extends androidx.preference.EditTextPreference {

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public EditTextPreference(Context context) {
        super(context);
    }

    private void editTextListener() {

        setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
            @Override
            public void onBindEditText(@NonNull final EditText editText) {
                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        String validationError = validate(editable.toString());
                        editText.setError(validationError);
                        editText.getRootView().findViewById(android.R.id.button1)
                                .setEnabled(validationError == null);
                    }

                });
            }
        });

    }

    private int minCharacters;
    private int maxCharacters;
    private String mode;
    private String illegalCharacters;
    private boolean inverse;

    private void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.EditTextPreference, 0, 0);

        mode = typedArray.getString(R.styleable.EditTextPreference_mode);
        illegalCharacters = typedArray.getString(R.styleable.EditTextPreference_illegalCharacters);
        minCharacters = typedArray.getInt(R.styleable.EditTextPreference_minCharacters, 0);
        maxCharacters = typedArray.getInt(R.styleable.EditTextPreference_maxCharacters, 100);

        typedArray.recycle();

        if (mode == null) mode = "";

        switch (mode) {
            case "url" :
                illegalCharacters = " \n";
                inverse = false;
                break;
            case "secret" :
                illegalCharacters = " \n";
                inverse = false;
                break;
            case "urchin" :
                illegalCharacters = " 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz°•·-`~!@#$%^&*()_+=[]\\{}|;':\",./<>?";
                inverse = true;
                break;
            case "alphanumeric" :
                illegalCharacters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
                inverse = true;
                break;
            default:
                illegalCharacters = illegalCharacters != null ? illegalCharacters : "\n";
                inverse = false;
        }

        editTextListener();
    }

    private String validate(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (inverse ^ illegalCharacters.contains(text.substring(i, i+1)))
                return getContext().getString(R.string.pref_error_text_illegal_characters);
        }

        if (text.length() > maxCharacters)
            return getContext().getString(R.string.pref_error_text_exceeds_length);

        if (text.length() < minCharacters)
            return getContext().getString(R.string.pref_error_api_secret_length);

        return null;
    }

    public String getMode() {
        return mode;
    }
}