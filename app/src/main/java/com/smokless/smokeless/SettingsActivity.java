package com.smokless.smokeless;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.smokless.smokeless.databinding.ActivitySettingsBinding;
import com.smokless.smokeless.ui.settings.SettingsViewModel;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        
        setupToolbar();
        setupStrictMode();
        setupDifficultySlider();
        setupSocialLinks();
        observeViewModel();
    }
    
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            finish();
        });
    }
    
    private void setupStrictMode() {
        binding.switchStrictMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setStrictMode(isChecked);
        });
    }
    
    private void setupDifficultySlider() {
        binding.sliderDifficulty.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setDifficultyLevel((int) value);
            }
        });
    }
    
    private void setupSocialLinks() {
        binding.btnGitHub.setOnClickListener(v -> {
            openUrl("https://github.com/thdelmas/Smoke-Less");
        });
        
        binding.btnLinkedIn.setOnClickListener(v -> {
            openUrl("https://www.linkedin.com/in/th%C3%A9ophile-delmas-92275b16b/");
        });
        
        binding.btnEmail.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:contact@theophile.world"));
            startActivity(intent);
        });
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
    
    private void observeViewModel() {
        viewModel.getStrictMode().observe(this, isStrict -> {
            if (binding.switchStrictMode.isChecked() != isStrict) {
                binding.switchStrictMode.setChecked(isStrict);
            }
        });
        
        viewModel.getDifficultyLevel().observe(this, level -> {
            if ((int) binding.sliderDifficulty.getValue() != level) {
                binding.sliderDifficulty.setValue(level);
            }
        });
        
        viewModel.getStrictModeDescription().observe(this, desc -> {
            binding.textStrictModeDesc.setText(desc);
        });
        
        viewModel.getDifficultyDescription().observe(this, desc -> {
            binding.textDifficultyDesc.setText(desc);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
