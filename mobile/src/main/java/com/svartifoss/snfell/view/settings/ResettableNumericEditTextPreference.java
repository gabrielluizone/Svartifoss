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
import com.svartifoss.snfell.common.AppearanceNumericRanges;
import com.svartifoss.snfell.view.mainactivity.MainActivity;
import com.matejdro.wearutils.preferences.compat.NumericEditTextPreference;

import kotlin.ranges.IntRange;

/**
 * Numeric preference whose edit dialog includes a reset-to-default action.
 *
 * <p>The reset <em>applies</em> and closes the dialog, rather than filling the field in and waiting
 * for OK. Two reasons, and the first is that the other design did not work: the neutral button used
 * to call {@code setText} directly, which persists the value but never runs
 * {@link androidx.preference.Preference#callChangeListener}, so nothing that reacts to the setting
 * - the watch-face preview, the fragment's own dependency wiring - was ever told. It also tried to
 * fill in the field through {@code getView()}, which is always {@code null} on a
 * {@link PreferenceDialogFragmentCompat}: that class builds its content view for the AlertDialog in
 * {@code onCreateDialog} and never sets it as the fragment's view. So the box kept showing the old
 * number, and pressing OK immediately after Reset wrote that old number straight back over the
 * default - the user had to reset, dismiss, reopen and press OK to make it stick.
 *
 * <p>The second reason is that one tap is what a "Reset to default" button should cost. Cancel no
 * longer undoes a reset, which is the accepted trade: it is an explicit action on a single number
 * the user can simply retype.
 *
 * <p>It also clamps. Every one of these fields used to accept any integer at all, while the watch
 * clamped whatever it read - so an out-of-range number looked accepted, rendered exactly as
 * intended, and then made the whole theme unsubmittable much later with a message naming a setting
 * that had never objected. {@link AppearanceNumericRanges} is the one place those bounds live now,
 * and the dialog states the range rather than making the user discover it.
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

    /**
     * Clamps whatever is about to be stored.
     *
     * Overridden on the preference rather than in the dialog because every path that persists a
     * value - OK, the reset button, a programmatic write - goes through this one method, and a
     * bound only one of them honours is not a bound.
     */
    @Override
    public void setText(String text) {
        super.setText(clampToRange(text));
    }

    /** The range this key accepts, or {@code null} when it declares none. */
    public IntRange getAllowedRange() {
        return AppearanceNumericRanges.INSTANCE.rangeFor(getKey());
    }

    private String clampToRange(String text) {
        IntRange range = getAllowedRange();
        if (range == null || text == null) {
            return text;
        }
        try {
            int parsed = Integer.parseInt(text.trim());
            int clamped = AppearanceNumericRanges.INSTANCE.clamp(getKey(), parsed);
            return clamped == parsed ? text : String.valueOf(clamped);
        } catch (NumberFormatException notANumber) {
            // Left exactly as typed: the base class already decides what a non-numeric entry
            // means, and inventing a number for it here would be a second, quieter opinion.
            return text;
        }
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
            // Stated before the number is typed rather than discovered afterwards - the failure
            // this replaces was a bound that nothing on screen ever mentioned.
            ResettableNumericEditTextPreference pref =
                    (ResettableNumericEditTextPreference) getPreference();
            IntRange range = pref != null ? pref.getAllowedRange() : null;
            if (range != null) {
                builder.setMessage(getString(
                        R.string.pref_numeric_range, range.getFirst(), range.getLast()));
            }
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

                // The field is on the *dialog*, not on the fragment - getView() is null here. Set
                // it before persisting so that whatever reads the dialog back sees the value that
                // is about to be applied, rather than the one being replaced.
                EditText editBox = dialog.findViewById(android.R.id.edit);
                if (editBox != null) {
                    editBox.setText(defaultValue);
                    editBox.setSelection(defaultValue.length());
                }

                // Exactly what pressing OK does: ask first, then persist. Going through
                // callChangeListener is the whole point - it is what tells the preview, the
                // dependency wiring and anything else watching that this value moved. setText
                // alone persists in silence.
                if (pref.callChangeListener(defaultValue)) {
                    pref.setText(defaultValue);
                }
                dialog.dismiss();
            });
        }
    }
}
