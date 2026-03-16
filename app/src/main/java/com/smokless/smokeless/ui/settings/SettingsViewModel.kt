package com.smokless.smokeless.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val PREF_NAME = "SmokelessPrefs"
        private const val KEY_STRICT_MODE = "strictMode"
        private const val KEY_DIFFICULTY = "difficultyLevel"
        private const val KEY_PACK_PRICE = "packPrice"
        private const val KEY_CIGS_PER_PACK = "cigsPerPack"
        private const val KEY_CURRENCY = "currency"
        
        const val DEFAULT_PACK_PRICE = 10.0f
        const val DEFAULT_CIGS_PER_PACK = 20
        const val DEFAULT_CURRENCY = "$"
        
        private val DIFFICULTY_DESCRIPTIONS = arrayOf(
            "No difficulty, just beat your average",
            "That's a start",
            "Now we're getting somewhere",
            "You're gonna make it for sure",
            "Are you even smoking?"
        )
    }
    
    private val prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    private val _strictMode = MutableLiveData<Boolean>()
    val strictMode: LiveData<Boolean> = _strictMode
    
    private val _difficultyLevel = MutableLiveData<Int>()
    val difficultyLevel: LiveData<Int> = _difficultyLevel
    
    private val _difficultyDescription = MutableLiveData<String>()
    val difficultyDescription: LiveData<String> = _difficultyDescription
    
    private val _strictModeDescription = MutableLiveData<String>()
    val strictModeDescription: LiveData<String> = _strictModeDescription
    
    private val _packPrice = MutableLiveData<Float>()
    val packPrice: LiveData<Float> = _packPrice
    
    private val _cigsPerPack = MutableLiveData<Int>()
    val cigsPerPack: LiveData<Int> = _cigsPerPack
    
    private val _currency = MutableLiveData<String>()
    val currency: LiveData<String> = _currency
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        val isStrict = prefs.getBoolean(KEY_STRICT_MODE, false)
        val level = prefs.getInt(KEY_DIFFICULTY, 0)
        val price = prefs.getFloat(KEY_PACK_PRICE, DEFAULT_PACK_PRICE)
        val cigsCount = prefs.getInt(KEY_CIGS_PER_PACK, DEFAULT_CIGS_PER_PACK)
        val currencySymbol = prefs.getString(KEY_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
        
        _strictMode.value = isStrict
        _difficultyLevel.value = level
        _packPrice.value = price
        _cigsPerPack.value = cigsCount
        _currency.value = currencySymbol
        updateDescriptions(isStrict, level)
    }
    
    fun setStrictMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRICT_MODE, enabled).apply()
        _strictMode.value = enabled
        updateStrictModeDescription(enabled)
    }
    
    fun setDifficultyLevel(level: Int) {
        prefs.edit().putInt(KEY_DIFFICULTY, level).apply()
        _difficultyLevel.value = level
        updateDifficultyDescription(level)
    }
    
    private fun updateDescriptions(isStrict: Boolean, level: Int) {
        updateStrictModeDescription(isStrict)
        updateDifficultyDescription(level)
    }
    
    private fun updateStrictModeDescription(isStrict: Boolean) {
        _strictModeDescription.value = if (isStrict) {
            "This mode is harder but provides better results"
        } else {
            "This is a gentle mode that cheers you up!\nPerfect for beginners"
        }
    }
    
    private fun updateDifficultyDescription(level: Int) {
        if (level in DIFFICULTY_DESCRIPTIONS.indices) {
            _difficultyDescription.value = DIFFICULTY_DESCRIPTIONS[level]
        }
    }
    
    fun setPackPrice(price: Float) {
        prefs.edit().putFloat(KEY_PACK_PRICE, price).apply()
        _packPrice.value = price
    }
    
    fun setCigsPerPack(count: Int) {
        prefs.edit().putInt(KEY_CIGS_PER_PACK, count).apply()
        _cigsPerPack.value = count
    }
    
    fun setCurrency(symbol: String) {
        prefs.edit().putString(KEY_CURRENCY, symbol).apply()
        _currency.value = symbol
    }
    
    fun getCostPerCigarette(): Float {
        val price = _packPrice.value ?: DEFAULT_PACK_PRICE
        val count = _cigsPerPack.value ?: DEFAULT_CIGS_PER_PACK
        return price / count
    }
}






