package org.citra.citra_emu.features.settings.model.view

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.json.JSONArray
import org.json.JSONObject

class TouchBindingProfileManager(context: Context) {
    
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        CitraApplication.appContext
    )
    
    companion object {
        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PROFILES_LIST = "profiles_list"
        private const val KEY_BINDINGS_PREFIX = "bindings_profile_"
        private const val DEFAULT_PROFILE = "Default"
    }
    
    init {
        if (!getProfiles().contains(DEFAULT_PROFILE)) {
            saveProfile(DEFAULT_PROFILE, emptyList())
        }
    }
    
    fun getCurrentProfile(): String {
        return prefs.getString(KEY_CURRENT_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE
    }
    
    fun setCurrentProfile(profileName: String) {
        prefs.edit().putString(KEY_CURRENT_PROFILE, profileName).apply()
    }
    
    fun getProfiles(): List<String> {
        val json = prefs.getString(KEY_PROFILES_LIST, null)
        return if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                list
            } catch (e: Exception) {
                listOf(DEFAULT_PROFILE)
            }
        } else {
            listOf(DEFAULT_PROFILE)
        }
    }
    
    private fun saveProfilesList(profiles: List<String>) {
        val array = JSONArray()
        profiles.forEach { array.put(it) }
        prefs.edit().putString(KEY_PROFILES_LIST, array.toString()).apply()
    }
    
    fun createProfile(profileName: String): Boolean {
        val profiles = getProfiles().toMutableList()
        if (profiles.contains(profileName)) {
            return false
        }
        profiles.add(profileName)
        saveProfilesList(profiles)
        saveProfile(profileName, emptyList())
        return true
    }
    
    fun deleteProfile(profileName: String): Boolean {
        if (profileName == DEFAULT_PROFILE) {
            return false
        }
        val profiles = getProfiles().toMutableList()
        profiles.remove(profileName)
        saveProfilesList(profiles)
        prefs.edit().remove("$KEY_BINDINGS_PREFIX$profileName").apply()
        
        if (getCurrentProfile() == profileName) {
            setCurrentProfile(DEFAULT_PROFILE)
        }
        return true
    }
    
    fun saveProfile(profileName: String, bindings: List<TouchBinding>) {
        val array = JSONArray()
        bindings.forEach { binding ->
            val obj = JSONObject()
            obj.put("keyCode", binding.keyCode)
            obj.put("axis", binding.axis)
            obj.put("positive", binding.positive)
            obj.put("analog", binding.analog)
            obj.put("threshold", binding.threshold)
            obj.put("x", binding.x)
            obj.put("y", binding.y)
            array.put(obj)
        }
        prefs.edit().putString("$KEY_BINDINGS_PREFIX$profileName", array.toString()).apply()
    }
    
    fun loadProfile(profileName: String): List<TouchBinding> {
        val json = prefs.getString("$KEY_BINDINGS_PREFIX$profileName", null)
        val bindings = mutableListOf<TouchBinding>()
        
        if (json == null) return bindings
        
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                bindings.add(
                    TouchBinding(
                        keyCode = obj.optInt("keyCode", -1),
                        axis = obj.optInt("axis", -1),
                        positive = obj.optBoolean("positive", true),
                        analog = obj.optBoolean("analog", false),
                        threshold = obj.optDouble("threshold", 0.5).toFloat(),
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            bindings.clear()
        }
        
        return bindings
    }
}
