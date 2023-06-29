package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "system_metrics",
        indexes = {
                @Index(name = "system_metrics_date", columnList = "date", unique = true)
        }
)
public class SystemMetrics {

    public enum TrainFrequencyStatus {
        OK,
        SLOW,
        DELAYED
    }

    public enum TrendStatus {
        DECREASING,
        NEUTRAL,
        INCREASING
    }

    @Transient
    private transient static final Logger logger = LoggerFactory.getLogger(SystemMetrics.class);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Exclude
    private Long id;

    @OneToMany(cascade=CascadeType.ALL)
    private Map<String, LineMetrics> lineMetricsByLine;

    @Column(nullable = false)
    private Calendar date;

    public SystemMetrics() {
    }

    public SystemMetrics(Map<String, LineMetrics> lineMetricsByLine, Calendar date) {
        this.lineMetricsByLine = lineMetricsByLine;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public Map<String, LineMetrics> getLineMetricsByLine() {
        return lineMetricsByLine;
    }

    public void setLineMetricsByLine(Map<String, LineMetrics> lineMetricsByLine) {
        this.lineMetricsByLine = lineMetricsByLine;
    }

    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    @MappedSuperclass
    private static class Metrics {

        private int numTrains;
        private int numCars;
        private int numEightCarTrains;
        private Integer numDelayedTrains = 0;
        private Integer minimumTrainDelay;
        private Integer averageTrainDelay;
        private Integer medianTrainDelay;
        private Integer maximumTrainDelay;
        private Double averageMinimumHeadways;
        private Double averageTrainFrequency;
        private Double averagePlatformWaitTime;
        private TrainFrequencyStatus trainFrequencyStatus;
        private Double expectedTrainFrequency;
        private Double expectedPlatformWaitTime;
        private TrendStatus platformWaitTimeTrendStatus;
        private Double averageOnTimePerformance;
        private Double averageHeadwayAdherence;
        private Integer expectedNumTrains;
        private Double averageScheduleAdherence;
        private Double standardDeviationTrainFrequency;
        private Double expectedStandardDeviationTrainFrequency;
        @Exclude private Boolean shouldExcludeFromReports = false;

        public void calculateWaitTimeStatus() {
            if (this.getAverageTrainFrequency() != null) {
                double slowThreshold = this.getExpectedTrainFrequency() + 1d;
                double delayThreshold = this.getExpectedTrainFrequency() + 2d;

                if (this.getAverageTrainFrequency() <= slowThreshold) {
                    this.setTrainFrequencyStatus(TrainFrequencyStatus.OK);
                } else if ((slowThreshold < this.getAverageTrainFrequency()) && (this.getAverageTrainFrequency() <= delayThreshold)) {
                    this.setTrainFrequencyStatus(TrainFrequencyStatus.SLOW);
                } else {
                    this.setTrainFrequencyStatus(TrainFrequencyStatus.DELAYED);
                }
            } else {
                this.setTrainFrequencyStatus(null);
            }
        }

        public int getNumTrains() {
            return numTrains;
        }

        public void setNumTrains(int numTrains) {
            this.numTrains = numTrains;
        }

        public int getNumCars() {
            return numCars;
        }

        public void setNumCars(int numCars) {
            this.numCars = numCars;
        }

        public int getNumEightCarTrains() {
            return numEightCarTrains;
        }

        public void setNumEightCarTrains(int numEightCarTrains) {
            this.numEightCarTrains = numEightCarTrains;
        }

        public Integer getNumDelayedTrains() {
            return numDelayedTrains;
        }

        public void setNumDelayedTrains(Integer numDelayedTrains) {
            this.numDelayedTrains = numDelayedTrains;
        }

        public Integer getMinimumTrainDelay() {
            return minimumTrainDelay;
        }

        public void setMinimumTrainDelay(Integer minimumTrainDelay) {
            this.minimumTrainDelay = minimumTrainDelay;
        }

        public Integer getAverageTrainDelay() {
            return averageTrainDelay;
        }

        public void setAverageTrainDelay(Integer averageTrainDelay) {
            this.averageTrainDelay = averageTrainDelay;
        }

        public Integer getMedianTrainDelay() {
            return medianTrainDelay;
        }

        public void setMedianTrainDelay(Integer medianTrainDelay) {
            this.medianTrainDelay = medianTrainDelay;
        }

        public Integer getMaximumTrainDelay() {
            return maximumTrainDelay;
        }

        public void setMaximumTrainDelay(Integer maximumTrainDelay) {
            this.maximumTrainDelay = maximumTrainDelay;
        }

        public Double getAverageMinimumHeadways() {
            return averageMinimumHeadways;
        }

        public void setAverageMinimumHeadways(Double averageMinimumHeadways) {
            this.averageMinimumHeadways = averageMinimumHeadways;
        }

        public Double getAverageTrainFrequency() {
            return averageTrainFrequency;
        }

        public void setAverageTrainFrequency(Double averageTrainFrequency) {
            this.averageTrainFrequency = averageTrainFrequency;
        }

        public Double getAveragePlatformWaitTime() {
            return averagePlatformWaitTime;
        }

        public void setAveragePlatformWaitTime(Double averagePlatformWaitTime) {
            this.averagePlatformWaitTime = averagePlatformWaitTime;
        }

        public TrainFrequencyStatus getTrainFrequencyStatus() {
            return trainFrequencyStatus;
        }

        public void setTrainFrequencyStatus(TrainFrequencyStatus trainFrequencyStatus) {
            this.trainFrequencyStatus = trainFrequencyStatus;
        }

        public Double getExpectedTrainFrequency() {
            return expectedTrainFrequency;
        }

        public void setExpectedTrainFrequency(Double expectedTrainFrequency) {
            this.expectedTrainFrequency = expectedTrainFrequency;
        }

        public Double getExpectedPlatformWaitTime() {
            return expectedPlatformWaitTime;
        }

        public void setExpectedPlatformWaitTime(Double expectedPlatformWaitTime) {
            this.expectedPlatformWaitTime = expectedPlatformWaitTime;
        }

        public TrendStatus getPlatformWaitTimeTrendStatus() {
            return platformWaitTimeTrendStatus;
        }

        public void setPlatformWaitTimeTrendStatus(TrendStatus platformWaitTimeTrendStatus) {
            this.platformWaitTimeTrendStatus = platformWaitTimeTrendStatus;
        }

        public Double getAverageOnTimePerformance() {
            return averageOnTimePerformance;
        }

        public void setAverageOnTimePerformance(Double averageOnTimePerformance) {
            this.averageOnTimePerformance = averageOnTimePerformance;
        }

        public Double getAverageHeadwayAdherence() {
            return averageHeadwayAdherence;
        }

        public void setAverageHeadwayAdherence(Double averageHeadwayAdherence) {
            this.averageHeadwayAdherence = averageHeadwayAdherence;
        }

        public Integer getExpectedNumTrains() {
            return expectedNumTrains;
        }

        public void setExpectedNumTrains(Integer expectedNumTrains) {
            this.expectedNumTrains = expectedNumTrains;
        }

        public Double getAverageScheduleAdherence() {
            return averageScheduleAdherence;
        }

        public void setAverageScheduleAdherence(Double averageScheduleAdherence) {
            this.averageScheduleAdherence = averageScheduleAdherence;
        }

        public Double getStandardDeviationTrainFrequency() {
            return standardDeviationTrainFrequency;
        }

        public void setStandardDeviationTrainFrequency(Double standardDeviationTrainFrequency) {
            this.standardDeviationTrainFrequency = standardDeviationTrainFrequency;
        }

        public Double getExpectedStandardDeviationTrainFrequency() {
            return expectedStandardDeviationTrainFrequency;
        }

        public void setExpectedStandardDeviationTrainFrequency(Double expectedStandardDeviationTrainFrequency) {
            this.expectedStandardDeviationTrainFrequency = expectedStandardDeviationTrainFrequency;
        }

        public Boolean getShouldExcludeFromReports() {
            return shouldExcludeFromReports;
        }

        public void setShouldExcludeFromReports(Boolean shouldExcludeFromReports) {
            this.shouldExcludeFromReports = shouldExcludeFromReports;
        }
    }

    @Entity
    @Table(name = "line_metrics",
            indexes = {
                    @Index(name = "line_metrics_date", columnList = "date"),
                    @Index(name = "line_metrics_line_code", columnList = "lineCode")
            }
    )
    public static class LineMetrics extends Metrics {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        @Column(name = "id", nullable = false)
        @Exclude
        private Long id;

        @Column(nullable = false)
        private String lineCode;

        private Integer budgetedNumCars;
        private Integer budgetedNumTrains;
        private Integer budgetedNumEightCarTrains;

        @Transient
        private List<ServiceGap> serviceGaps;

        @OneToMany(cascade=CascadeType.ALL)
        private Map<Integer, DirectionMetrics> directionMetricsByDirection = new LinkedHashMap<>();

        @Column(nullable = false)
        private Calendar date;

        public LineMetrics() {
        }

        public LineMetrics(String lineCode, Calendar date) {
            this.lineCode = lineCode;
            this.serviceGaps = new ArrayList<>();
            this.date = date;
        }

        public Long getId() {
            return id;
        }

        public String getLineCode() {
            return lineCode;
        }

        public void setLineCode(String lineCode) {
            this.lineCode = lineCode;
        }

        public List<ServiceGap> getServiceGaps() {
            return serviceGaps;
        }

        public void setServiceGaps(List<ServiceGap> serviceGaps) {
            this.serviceGaps = serviceGaps;
        }

        public Integer getBudgetedNumCars() {
            return budgetedNumCars;
        }

        public void setBudgetedNumCars(Integer budgetedNumCars) {
            this.budgetedNumCars = budgetedNumCars;
        }

        public Integer getBudgetedNumTrains() {
            return budgetedNumTrains;
        }

        public void setBudgetedNumTrains(Integer budgetedNumTrains) {
            this.budgetedNumTrains = budgetedNumTrains;
        }

        public Integer getBudgetedNumEightCarTrains() {
            return budgetedNumEightCarTrains;
        }

        public void setBudgetedNumEightCarTrains(Integer budgetedNumEightCarTrains) {
            this.budgetedNumEightCarTrains = budgetedNumEightCarTrains;
        }

        public Map<Integer, DirectionMetrics> getDirectionMetricsByDirection() {
            return directionMetricsByDirection;
        }

        public void setDirectionMetricsByDirection(Map<Integer, DirectionMetrics> directionMetricsByDirection) {
            this.directionMetricsByDirection = directionMetricsByDirection;
        }

        public Calendar getDate() {
            return date;
        }

        public void setDate(Calendar date) {
            this.date = date;
        }
    }

    @Entity
    @Table(name = "direction_metrics",
            indexes = {
                    @Index(name = "direction_metrics_date", columnList = "date"),
                    @Index(name = "direction_metrics_line_code", columnList = "lineCode"),
                    @Index(name = "direction_metrics_direction_number", columnList = "directionNumber")
            }
    )
    public static class DirectionMetrics extends Metrics {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        @Column(name = "id", nullable = false)
        @Exclude
        private Long id;

        @Column(nullable = false)
        private String lineCode;

        @Column(nullable = false)
        private Integer directionNumber;

        private String direction;
        private String towardsStationName;

        private transient List<Integer> delayTimes = new ArrayList<>();
        private transient List<Double> etas = new ArrayList<>();
        private transient List<Double> onTimePerformances = new ArrayList<>();

        @Column(nullable = false)
        private Calendar date;

        public DirectionMetrics() {
        }

        public DirectionMetrics(String lineCode, Integer directionNumber, Calendar date) {
            this.lineCode = lineCode;
            this.directionNumber = directionNumber;
            this.date = date;
        }

        public Long getId() {
            return id;
        }

        public String getLineCode() {
            return lineCode;
        }

        public void setLineCode(String lineCode) {
            this.lineCode = lineCode;
        }

        public Integer getDirectionNumber() {
            return directionNumber;
        }

        public void setDirectionNumber(Integer directionNumber) {
            this.directionNumber = directionNumber;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getTowardsStationName() {
            return towardsStationName;
        }

        public void setTowardsStationName(String towardsStationName) {
            this.towardsStationName = towardsStationName;
        }

        public List<Integer> getDelayTimes() {
            return delayTimes;
        }

        public List<Double> getEtas() {
            return etas;
        }

        public List<Double> getOnTimePerformances() {
            return onTimePerformances;
        }

        public Calendar getDate() {
            return date;
        }

        public void setDate(Calendar date) {
            this.date = date;
        }
    }
}
