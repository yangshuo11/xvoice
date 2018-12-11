package com.example.ddvoice

import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.view.WindowManager


class PreferencesActivity : PreferenceActivity(), Preference.OnPreferenceClickListener {
//    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
//        loadSharedPrefs()
//        return true
//    }
    
    override fun onPreferenceClick(preference: Preference?): Boolean {
        loadSharedPrefs()
        return true
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    
        addPreferencesFromResource(R.layout.preferences)
        
        findPreference(SP_HOME_KEY_WAKE).onPreferenceClickListener = this
        findPreference(SP_VOICE_WAKE).onPreferenceClickListener = this
        findPreference(SP_VOLUME_KEY_WAKE).onPreferenceClickListener = this
        findPreference(SP_EXCLUDE_FROM_RECENTS).onPreferenceClickListener = this
//        findPreference(SP_HB_API_KEY).onPreferenceChangeListener = this
//        findPreference(SP_LIGHT_ON_URL).onPreferenceChangeListener = this
//        findPreference(SP_LIGHT_OFF_URL).onPreferenceChangeListener = this
    }
    
    override fun onPause() {
        super.onPause()
        loadSharedPrefs()
        finish()
    }
}