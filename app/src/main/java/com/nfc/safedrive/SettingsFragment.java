package com.nfc.safedrive;

import android.app.FragmentManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.Group;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.app.FragmentTransaction;
import android.widget.ImageView;

import pl.droidsonroids.gif.GifImageView;

public class SettingsFragment extends android.app.Fragment{
    private View rootView;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //rootView = inflater.inflate(R.layout.fragment_home, container, false);
       // Group viewGroup=getActivity().findViewById(R.id.group);
       // viewGroup.setVisibility(View.GONE);

        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       // Group scanner=getActivity().findViewById(R.id.group);
        //scanner.setVisibility(View.INVISIBLE);
        android.app.Fragment settings=new MainSettingsFragment();
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, settings)
                .commit();

    }
    public static class MainSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            // Phone Number Edit Change Listner
            bindPreferenceSummaryToValue(findPreference(getString(R.string.key_dialNo)));

            // SMS Message Edit Change Listner
            bindPreferenceSummaryToValue(findPreference(getString(R.string.key_smsMessage)));

            bindPreferenceSummaryToValue(findPreference("key_driveTime"));
        }
        }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            //onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), "123"));
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String stringValue = newValue.toString();
            if (preference instanceof EditTextPreference) {
                if (preference.getKey().equals("key_dialNo")) {
                    // update the changed gallery name to summary filed
                    preference.setSummary(stringValue);
                }
                else if (preference.getKey().equals("key_smsMessage")) {
                    // update the changed gallery name to summary filed
                    preference.setSummary(stringValue+" My Current Location:https://www.google.com/maps/search/?api=1&query=0.0,0.0");
                }
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };
}
