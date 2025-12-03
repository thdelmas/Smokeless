package com.smokless.smokeless.ui.main;

public class ScoreData {
    
    private final String label;
    private final long value;
    private final double percentage;
    private final boolean isCount;
    
    public ScoreData(String label, long value, double percentage) {
        this(label, value, percentage, false);
    }
    
    public ScoreData(String label, long value, double percentage, boolean isCount) {
        this.label = label;
        this.value = value;
        this.percentage = percentage;
        this.isCount = isCount;
    }
    
    public String getLabel() {
        return label;
    }
    
    public long getValue() {
        return value;
    }
    
    public double getPercentage() {
        return percentage;
    }
    
    public boolean isCount() {
        return isCount;
    }
    
    public String getStatusEmoji() {
        if (percentage >= 100) return "🏆";
        if (percentage >= 80) return "🟢";
        if (percentage >= 60) return "⚪️";
        if (percentage >= 40) return "🟡";
        if (percentage >= 20) return "🟠";
        return "🔴";
    }
    
    public int getColorLevel() {
        if (percentage >= 80) return 4; // green
        if (percentage >= 60) return 3; // white
        if (percentage >= 40) return 2; // yellow
        if (percentage >= 20) return 1; // orange
        return 0; // red
    }
}

