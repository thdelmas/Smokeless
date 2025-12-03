package com.smokless.smokeless.ui.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class SettingsViewModel extends AndroidViewModel {
    
    private static final String PREF_NAME = "SmokelessPrefs";
    private static final String KEY_STRICT_MODE = "strictMode";
    private static final String KEY_DIFFICULTY = "difficultyLevel";
    
    private final SharedPreferences prefs;
    
    private final MutableLiveData<Boolean> strictMode = new MutableLiveData<>();
    private final MutableLiveData<Integer> difficultyLevel = new MutableLiveData<>();
    private final MutableLiveData<String> difficultyDescription = new MutableLiveData<>();
    private final MutableLiveData<String> strictModeDescription = new MutableLiveData<>();
    
    private static final String[] DIFFICULTY_DESCRIPTIONS = {
        "No difficulty, just beat your average",
        "That's a start",
        "Now we're getting somewhere",
        "You're gonna make it for sure",
        "Are you even smoking?"
    };
    
    public SettingsViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }
    
    private void loadSettings() {
        boolean isStrict = prefs.getBoolean(KEY_STRICT_MODE, false);
        int level = prefs.getInt(KEY_DIFFICULTY, 0);
        
        strictMode.setValue(isStrict);
        difficultyLevel.setValue(level);
        updateDescriptions(isStrict, level);
    }
    
    public LiveData<Boolean> getStrictMode() {
        return strictMode;
    }
    
    public LiveData<Integer> getDifficultyLevel() {
        return difficultyLevel;
    }
    
    public LiveData<String> getDifficultyDescription() {
        return difficultyDescription;
    }
    
    public LiveData<String> getStrictModeDescription() {
        return strictModeDescription;
    }
    
    public void setStrictMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_STRICT_MODE, enabled).apply();
        strictMode.setValue(enabled);
        updateStrictModeDescription(enabled);
    }
    
    public void setDifficultyLevel(int level) {
        prefs.edit().putInt(KEY_DIFFICULTY, level).apply();
        difficultyLevel.setValue(level);
        updateDifficultyDescription(level);
    }
    
    private void updateDescriptions(boolean isStrict, int level) {
        updateStrictModeDescription(isStrict);
        updateDifficultyDescription(level);
    }
    
    private void updateStrictModeDescription(boolean isStrict) {
        if (isStrict) {
            strictModeDescription.setValue("This mode is harder but provides better results");
        } else {
            strictModeDescription.setValue("This is a gentle mode that cheers you up!\nPerfect for beginners");
        }
    }
    
    private void updateDifficultyDescription(int level) {
        if (level >= 0 && level < DIFFICULTY_DESCRIPTIONS.length) {
            difficultyDescription.setValue(DIFFICULTY_DESCRIPTIONS[level]);
        }
    }
}

