package com.jamespizzurro.metrorailserver.domain;

import com.jamespizzurro.metrorailserver.Exclude;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Objects;

@Entity
@Table(name = "station_tag",
        indexes = {
                @Index(name = "station_tag_date_index", columnList = "date", unique = false)
        }
)
public class StationTag {

    public enum StationTagType {
        EMPTY(30, true),
        FRIENDLY_OR_HELPFUL_STAFF(60, true),
        AMPLE_SECURITY(30, true),
        FREE_MASKS_AVAILABLE(1440, true),
        FREE_HAND_SANITIZER_AVAILABLE(1440, true),

        CROWDED(30, false),
        UNCOMFORTABLE_TEMPS(60, false),
        LONG_WAITING_TIME(15, false),
        POSTED_TIMES_INACCURATE(15, false),
        NEEDS_WORK(180, false),
        BROKEN_ELEVATOR(60, false),
        BROKEN_ESCALATOR(60, false),
        UNFRIENDLY_OR_UNHELPFUL_STAFF(60, false),
        SMOKE_OR_FIRE(30, false),
        NO_FREE_MASKS(1440, false),
        NO_FREE_HAND_SANITIZER(1440, false);

        private int maxNumMinutesActive;
        private boolean isPositive;

        StationTagType(int maxNumMinutesActive, boolean isPositive) {
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

    @Column(name = "line_codes")
    @Type(type = "com.jamespizzurro.metrorailserver.StringArrayType")
    private String[] lineCodes;

    @Column(nullable = false)
    private String stationCode;

    @Column(nullable = false)
    private Calendar date;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StationTagType type;

    private Long tweetId;

    public StationTag() {
    }

    public StationTag(String userId, String[] lineCodes, String stationCode, StationTagType type, Long tweetId) {
        this.userId = userId;
        this.lineCodes = lineCodes;
        this.stationCode = stationCode;
        this.date = Calendar.getInstance();
        this.type = type;
        this.tweetId = tweetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StationTag that = (StationTag) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(stationCode, that.stationCode) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, stationCode, type);
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String[] getLineCodes() {
        return lineCodes;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Calendar getDate() {
        return date;
    }

    public StationTagType getType() {
        return type;
    }

    public Long getTweetId() {
        return tweetId;
    }
}
