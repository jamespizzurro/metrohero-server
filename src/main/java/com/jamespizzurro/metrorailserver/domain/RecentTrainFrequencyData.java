package com.jamespizzurro.metrorailserver.domain;

public class RecentTrainFrequencyData {

    private String times;
    private String allAverageTrainFrequencies;
    private String rdAverageTrainFrequencies;
    private String orAverageTrainFrequencies;
    private String svAverageTrainFrequencies;
    private String blAverageTrainFrequencies;
    private String ylAverageTrainFrequencies;
    private String grAverageTrainFrequencies;
    private String allExpectedTrainFrequencies;
    private String rdExpectedTrainFrequencies;
    private String orExpectedTrainFrequencies;
    private String svExpectedTrainFrequencies;
    private String blExpectedTrainFrequencies;
    private String ylExpectedTrainFrequencies;
    private String grExpectedTrainFrequencies;

    public RecentTrainFrequencyData(Object[] values) {
        this.times = String.valueOf(values[0]);

        this.allAverageTrainFrequencies = String.valueOf(values[1]);
        this.rdAverageTrainFrequencies = String.valueOf(values[2]);
        this.orAverageTrainFrequencies = String.valueOf(values[3]);
        this.svAverageTrainFrequencies = String.valueOf(values[4]);
        this.blAverageTrainFrequencies = String.valueOf(values[5]);
        this.ylAverageTrainFrequencies = String.valueOf(values[6]);
        this.grAverageTrainFrequencies = String.valueOf(values[7]);

        this.allExpectedTrainFrequencies = String.valueOf(values[8]);
        this.rdExpectedTrainFrequencies = String.valueOf(values[9]);
        this.orExpectedTrainFrequencies = String.valueOf(values[10]);
        this.svExpectedTrainFrequencies = String.valueOf(values[11]);
        this.blExpectedTrainFrequencies = String.valueOf(values[12]);
        this.ylExpectedTrainFrequencies = String.valueOf(values[13]);
        this.grExpectedTrainFrequencies = String.valueOf(values[14]);
    }
}
