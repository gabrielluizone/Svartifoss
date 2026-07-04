package com.svartifoss.snfell.view.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.svartifoss.snfell.R;
import com.svartifoss.snfell.view.mainactivity.MainActivity;
import com.matejdro.wearutils.preferences.compat.NumericEditTextPreference;

/**
 * Numeric preference whose edit dialog includes a reset-to-default action.
 */
public class ResettableNumericEditTextPreference extends NumericEditTextPreference {

    private final String defaultValueText;

    public ResettableNumericEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        defaultValueText = readDefaultValue(context, attrs);
    }

    private static String readDefaultValue(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(
                attrs, androidx.preference.R.styleable.Preference);
        try {
            if (ta.hasValue(androidx.preference.R.styleable.Preference_android_defaultValue)) {
                return ta.getString(androidx.preference.R.styleable.Preference_android_defaultValue);
            }
            return "";
        } finally {
            ta.recycle();
        }
    }

    public String getDefaultValueAsString() {
        return defaultValueText != null ? defaultValueText : "";
    }

    @Override
    public PreferenceDialogFragmentCompat createDialog(String key) {
        return ResettableNumericEditTextPreferenceDialog.create(key);
    }

    public static class ResettableNumericEditTextPreferenceDialog
            extends NumericEditTextPreferenceDialog {

        public static ResettableNumericEditTextPreferenceDialog create(String key) {
            ResettableNumericEditTextPreferenceDialog fragment =
                    new ResettableNumericEditTextPreferenceDialog();
            Bundle arguments = new Bundle(1);
            arguments.putString(ARG_KEY, key);
            fragment.setArguments(arguments);
            return fragment;
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            builder.setNeutralButton(R.string.pref_reset_default, null);
        }

        @Override
        public void onStart() {
            super.onStart();
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog == null) {
                return;
            }
            // Separate window: the activity's accent pass never reaches it, so its EditText
            // would keep theme-default (sage) selection handles under a custom accent.
            View dialogRoot = dialog.findViewById(android.R.id.content);
            if (getActivity() instanceof MainActivity && dialogRoot != null) {
                ((MainActivity) getActivity()).applyAccentToView(dialogRoot);
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ResettableNumericEditTextPreference pref =
                        (ResettableNumericEditTextPreference) getPreference();
                if (pref == null) {
                    return;
                }
                String defaultValue = pref.getDefaultValueAsString();
                pref.setText(defaultValue);
                View dialogView = getView();
                if (dialogView != null) {
                    EditText editBox = dialogView.findViewById(android.R.id.edit);
                    if (editBox != null) {
                        editBox.setText(defaultValue);
                        editBox.setSelection(defaultValue.length());
                    }
                }
            });
        }
    }
}
