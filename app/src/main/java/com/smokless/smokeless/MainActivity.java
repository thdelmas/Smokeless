package com.smokless.smokeless;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smokless.smokeless.databinding.ActivityMainBinding;
import com.smokless.smokeless.ui.main.MainViewModel;
import com.smokless.smokeless.ui.main.ScoreAdapter;
import com.smokless.smokeless.ui.main.ScoreData;
import com.smokless.smokeless.util.TimeFormatter;

import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    
    private ScoreAdapter statsAdapter;
    
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final DecimalFormat percentFormat = new DecimalFormat("0.0");
    
    private String currentPeriod = "month";
    
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            viewModel.refreshData();
            refreshHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        setupToolbar();
        setupRecyclerView();
        setupChipGroup();
        setupFab();
        observeViewModel();
    }
    
    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
    
    private void setupRecyclerView() {
        statsAdapter = new ScoreAdapter();
        binding.recyclerStats.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStats.setAdapter(statsAdapter);
    }
    
    private void setupChipGroup() {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            
            int checkedId = checkedIds.get(0);
            
            if (checkedId == R.id.chipToday) {
                currentPeriod = "day";
                updatePeriodHeader("☀️", "Today");
            } else if (checkedId == R.id.chipWeek) {
                currentPeriod = "week";
                updatePeriodHeader("📋", "This Week");
            } else if (checkedId == R.id.chipMonth) {
                currentPeriod = "month";
                updatePeriodHeader("📆", "This Month");
            } else if (checkedId == R.id.chipYear) {
                currentPeriod = "year";
                updatePeriodHeader("📅", "This Year");
            } else if (checkedId == R.id.chipAllTime) {
                currentPeriod = "all";
                updatePeriodHeader("📊", "All Time");
            }
            
            updateStatsForPeriod();
        });
        
        // Set initial selection
        binding.chipMonth.setChecked(true);
    }
    
    private void updatePeriodHeader(String icon, String title) {
        binding.textPeriodIcon.setText(icon);
        binding.textPeriodTitle.setText(title);
    }
    
    private void updateStatsForPeriod() {
        List<ScoreData> scores = null;
        
        switch (currentPeriod) {
            case "day":
                scores = viewModel.getDayScores().getValue();
                break;
            case "week":
                scores = viewModel.getWeekScores().getValue();
                break;
            case "month":
                scores = viewModel.getMonthScores().getValue();
                break;
            case "year":
                scores = viewModel.getYearScores().getValue();
                break;
            case "all":
                scores = viewModel.getAllTimeScores().getValue();
                break;
        }
        
        if (scores != null) {
            statsAdapter.setScores(scores);
            updatePeriodCount(scores);
        }
    }
    
    private void updatePeriodCount(List<ScoreData> scores) {
        // Find count from scores
        for (ScoreData score : scores) {
            if (score.isCount()) {
                long count = score.getValue();
                binding.textPeriodCount.setText(count + (count == 1 ? " session" : " sessions"));
                return;
            }
        }
        binding.textPeriodCount.setText("0 sessions");
    }
    
    private void setupFab() {
        binding.fabSmoke.setOnClickListener(v -> {
            viewModel.recordSmoke();
            updateButtonState(0);
        });
    }
    
    private void observeViewModel() {
        viewModel.getCurrentScore().observe(this, score -> {
            binding.textViewCurrentScore.setText(TimeFormatter.format(score));
        });
        
        viewModel.getCurrentPercentage().observe(this, percentage -> {
            binding.textViewPercentage.setText(percentFormat.format(percentage) + "%");
            binding.progressIndicator.setProgress((int) Math.min(percentage, 100));
            updateButtonState(percentage);
            updateProgressColor(percentage);
        });
        
        viewModel.getCurrentGoal().observe(this, goal -> {
            binding.textViewGoalLabel.setText("Target: " + TimeFormatter.formatShort(goal));
        });
        
        // Observe all period scores and update when the selected period changes
        viewModel.getAllTimeScores().observe(this, scores -> {
            if ("all".equals(currentPeriod)) {
                statsAdapter.setScores(scores);
                updatePeriodCount(scores);
                updateRecordCards(scores);
            }
            // Always update record cards from all-time data
            updateRecordCards(scores);
        });
        
        viewModel.getYearScores().observe(this, scores -> {
            if ("year".equals(currentPeriod)) {
                statsAdapter.setScores(scores);
                updatePeriodCount(scores);
            }
        });
        
        viewModel.getMonthScores().observe(this, scores -> {
            if ("month".equals(currentPeriod)) {
                statsAdapter.setScores(scores);
                updatePeriodCount(scores);
            }
        });
        
        viewModel.getWeekScores().observe(this, scores -> {
            if ("week".equals(currentPeriod)) {
                statsAdapter.setScores(scores);
                updatePeriodCount(scores);
            }
        });
        
        viewModel.getDayScores().observe(this, scores -> {
            if ("day".equals(currentPeriod)) {
                statsAdapter.setScores(scores);
                updatePeriodCount(scores);
            }
            // Update "Today's Progress" cards
            updateTodayCards(scores);
        });
    }
    
    private void updateProgressColor(double percentage) {
        int colorRes;
        if (percentage >= 100) {
            colorRes = R.color.status_champion;
        } else if (percentage >= 80) {
            colorRes = R.color.status_strong;
        } else if (percentage >= 60) {
            colorRes = R.color.status_steady;
        } else if (percentage >= 40) {
            colorRes = R.color.status_building;
        } else if (percentage >= 20) {
            colorRes = R.color.status_starting;
        } else {
            colorRes = R.color.status_reset;
        }
        
        binding.progressIndicator.setIndicatorColor(ContextCompat.getColor(this, colorRes));
        binding.textViewCurrentScore.setTextColor(ContextCompat.getColor(this, colorRes));
        binding.textViewPercentage.setTextColor(ContextCompat.getColor(this, colorRes));
    }
    
    private void updateTodayCards(List<ScoreData> scores) {
        if (scores == null || scores.isEmpty()) return;
        
        for (ScoreData score : scores) {
            String label = score.getLabel().toLowerCase();
            
            if (label.contains("best")) {
                binding.textBestTodayValue.setText(TimeFormatter.formatShort(score.getValue()));
                int progress = (int) Math.min(score.getPercentage(), 100);
                binding.progressBestToday.setProgress(progress);
            } else if (label.contains("count")) {
                binding.textCountTodayValue.setText(String.valueOf(score.getValue()));
                // Reverse logic for count - fewer is better
                int progress = score.getValue() > 0 ? Math.max(0, 100 - (int)(score.getValue() * 10)) : 100;
                binding.progressCountToday.setProgress(progress);
            }
        }
    }
    
    private void updateRecordCards(List<ScoreData> scores) {
        if (scores == null || scores.isEmpty()) return;
        
        for (ScoreData score : scores) {
            String label = score.getLabel().toLowerCase();
            
            if (label.contains("best")) {
                binding.textAllTimeBest.setText(TimeFormatter.formatShort(score.getValue()));
            } else if (label.contains("count")) {
                binding.textTotalSessions.setText(String.valueOf(score.getValue()));
            }
        }
    }
    
    private void updateButtonState(double percentage) {
        int colorRes;
        String text;
        
        if (percentage >= 100) {
            colorRes = R.color.status_champion;
            text = "Keep Going! 🏆";
        } else if (percentage >= 80) {
            colorRes = R.color.status_strong;
            text = "Almost There!";
        } else if (percentage >= 60) {
            colorRes = R.color.status_steady;
            text = "Going Strong";
        } else if (percentage >= 40) {
            colorRes = R.color.status_building;
            text = "Stay Strong";
        } else if (percentage >= 20) {
            colorRes = R.color.status_starting;
            text = "I Smoked";
        } else {
            colorRes = R.color.accent_amber;
            text = "I Smoked";
        }
        
        binding.fabSmoke.setBackgroundTintList(
            ContextCompat.getColorStateList(this, colorRes)
        );
        binding.fabSmoke.setText(text);
        
        // Update text color for contrast
        int textColor = (colorRes == R.color.status_building || colorRes == R.color.accent_amber) 
            ? R.color.black : R.color.black;
        binding.fabSmoke.setTextColor(ContextCompat.getColor(this, textColor));
        binding.fabSmoke.setIconTint(ContextCompat.getColorStateList(this, textColor));
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshData();
        refreshHandler.postDelayed(refreshRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
