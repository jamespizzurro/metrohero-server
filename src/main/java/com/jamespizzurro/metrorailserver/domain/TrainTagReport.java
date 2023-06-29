package com.jamespizzurro.metrorailserver.domain;

import java.util.Map;
import java.util.Set;

public class TrainTagReport {

    private Map<TrainTag.TrainTagType, Integer> numTagsByType;
    private Set<TrainTag.TrainTagType> userTaggedTypes;
    private long numPositiveTags;
    private long numNegativeTags;

    public TrainTagReport(Map<TrainTag.TrainTagType, Integer> numTagsByType, Set<TrainTag.TrainTagType> userTaggedTypes, long numPositiveTags, long numNegativeTags) {
        this.numTagsByType = numTagsByType;
        this.userTaggedTypes = userTaggedTypes;
        this.numPositiveTags = numPositiveTags;
        this.numNegativeTags = numNegativeTags;
    }

    public Map<TrainTag.TrainTagType, Integer> getNumTagsByType() {
        return numTagsByType;
    }

    public Set<TrainTag.TrainTagType> getUserTaggedTypes() {
        return userTaggedTypes;
    }

    public long getNumPositiveTags() {
        return numPositiveTags;
    }

    public long getNumNegativeTags() {
        return numNegativeTags;
    }
}
