package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Objects;

@Entity
@Table(name = "train_tag",
        indexes = {
                @Index(name = "train_tag_date_index", columnList = "date", unique = false)
        }
)
public class TrainTag {

    public enum TrainTagType {
        GOOD_OPERATOR(60, true),
        GOOD_RIDE(30, true),
        NEW_TRAIN(60, true),
        EMPTY(30, true),

        BAD_OPERATOR(60, false),
        CROWDED(30, false),
        UNCOMFORTABLE_TEMPS(30, false),
        RECENTLY_OFFLOADED(15, false),
        UNCOMFORTABLE_RIDE(30, false),
        ISOLATED_CARS(30, false),
        WRONG_NUM_CARS(60, false),
        WRONG_DESTINATION(30, false),
        NEEDS_WORK(60, false),
        BROKEN_INTERCOM(60, false),
        DISRUPTIVE_PASSENGER(30, false);

        private int maxNumMinutesActive;
        private boolean isPositive;

        TrainTagType(int maxNumMinutesActive, boolean isPositive) {
            this.maxNumMinutesActive = maxNumMinutesActive;
            this.isPositive = isPositive;
        }

        public int getMaxNumMinutesActive() {
            return maxNumMinutesActive;
        }
        public boolean isPositive() {
            return isPositive;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false,
            columnDefinition = "bigserial not null")
    @Exclude
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String trainId;

    private String realTrainId;

    private String lineCode;

    private String stationCode;

    @Column(nullable = false)
    private Calendar date;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TrainTagType type;

    private Long tweetId;

    public TrainTag() {
    }

    public TrainTag(String userId, String trainId, String realTrainId, String lineCode, String stationCode, TrainTagType type, Long tweetId) {
        this.userId = userId;
        this.trainId = trainId;
        this.realTrainId = realTrainId;
        this.lineCode = lineCode;
        this.stationCode = stationCode;
        this.date = Calendar.getInstance();
        this.type = type;
        this.tweetId = tweetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrainTag trainTag = (TrainTag) o;
        return userId.equals(trainTag.userId) &&
                trainId.equals(trainTag.trainId) &&
                type == trainTag.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, trainId, type);
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTrainId() {
        return trainId;
    }
    public void setTrainId(String trainId) {
        this.trainId = trainId;
    }

    public String getLineCode() {
        return lineCode;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Calendar getDate() {
        return date;
    }

    public TrainTagType getType() {
        return type;
    }

    public Long getTweetId() {
        return tweetId;
    }
}
