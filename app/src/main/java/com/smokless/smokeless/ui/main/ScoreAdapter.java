package com.smokless.smokeless.ui.main;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.smokless.smokeless.R;
import com.smokless.smokeless.util.TimeFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ScoreAdapter extends RecyclerView.Adapter<ScoreAdapter.ScoreViewHolder> {
    
    private List<ScoreData> scores = new ArrayList<>();
    private final DecimalFormat percentFormat = new DecimalFormat("0");
    
    public void setScores(List<ScoreData> newScores) {
        this.scores = newScores != null ? newScores : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_score, parent, false);
        return new ScoreViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        ScoreData score = scores.get(position);
        holder.bind(score, percentFormat, position);
    }
    
    @Override
    public int getItemCount() {
        return scores.size();
    }
    
    static class ScoreViewHolder extends RecyclerView.ViewHolder {
        
        private final CircularProgressIndicator progressRing;
        private final TextView textPercent;
        private final TextView textLabel;
        private final TextView textStatus;
        private final TextView textValue;
        
        ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            progressRing = itemView.findViewById(R.id.progressRing);
            textPercent = itemView.findViewById(R.id.textPercent);
            textLabel = itemView.findViewById(R.id.textLabel);
            textStatus = itemView.findViewById(R.id.textStatus);
            textValue = itemView.findViewById(R.id.textValue);
        }
        
        void bind(ScoreData score, DecimalFormat format, int position) {
            Context context = itemView.getContext();
            
            // Set the label (simplified without scope suffix)
            String label = getSimplifiedLabel(score.getLabel());
            textLabel.setText(label);
            
            // Set progress ring and percentage
            int progressValue = (int) Math.min(score.getPercentage(), 100);
            progressRing.setProgress(progressValue);
            
            // Get status colors based on performance
            int[] statusColors = getStatusColors(context, score.getPercentage());
            int primaryColor = statusColors[0];
            int dimColor = statusColors[1];
            
            // Apply colors to progress ring
            progressRing.setIndicatorColor(primaryColor);
            
            // Display values
            if (score.isCount()) {
                textValue.setText(String.valueOf(score.getValue()));
                textPercent.setText("×");
                textPercent.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
            } else {
                textValue.setText(TimeFormatter.format(score.getValue()));
                textPercent.setText(format.format(Math.min(score.getPercentage(), 999)) + "%");
                textPercent.setTextColor(primaryColor);
            }
            
            // Set value color based on status
            textValue.setTextColor(primaryColor);
            
            // Show status badge for exceptional performance
            String statusText = getStatusText(score.getPercentage());
            if (statusText != null && !score.isCount()) {
                textStatus.setText(statusText);
                textStatus.setTextColor(primaryColor);
                textStatus.setVisibility(View.VISIBLE);
            } else {
                textStatus.setVisibility(View.GONE);
            }
        }
        
        private String getSimplifiedLabel(String fullLabel) {
            // Convert "Best of Month" -> "Best", "Average of Week" -> "Average", etc.
            if (fullLabel.startsWith("Best")) return "Best";
            if (fullLabel.startsWith("Average")) return "Average";
            if (fullLabel.startsWith("Median")) return "Median";
            if (fullLabel.startsWith("Count")) return "Sessions";
            return fullLabel;
        }
        
        private String getStatusText(double percentage) {
            if (percentage >= 150) return "CHAMPION";
            if (percentage >= 120) return "EXCELLENT";
            if (percentage >= 100) return "GOAL MET";
            if (percentage >= 80) return "CLOSE";
            return null;
        }
        
        private int[] getStatusColors(Context context, double percentage) {
            if (percentage >= 100) {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_champion),
                    ContextCompat.getColor(context, R.color.status_champion_dim)
                };
            } else if (percentage >= 80) {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_strong),
                    ContextCompat.getColor(context, R.color.status_strong_dim)
                };
            } else if (percentage >= 60) {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_steady),
                    ContextCompat.getColor(context, R.color.status_steady_dim)
                };
            } else if (percentage >= 40) {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_building),
                    ContextCompat.getColor(context, R.color.status_building_dim)
                };
            } else if (percentage >= 20) {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_starting),
                    ContextCompat.getColor(context, R.color.status_starting_dim)
                };
            } else {
                return new int[]{
                    ContextCompat.getColor(context, R.color.status_reset),
                    ContextCompat.getColor(context, R.color.status_reset_dim)
                };
            }
        }
    }
}
