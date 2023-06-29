package com.jamespizzurro.metrorailserver.domain;

import java.util.Map;
import java.util.Set;

public class StationTagReport {

    private Map<StationTag.StationTagType, Integer> numTagsByType;
    private Set<StationTag.StationTagType> userTaggedTypes;
    private long numPositiveTags;
    private long numNegativeTags;

    public StationTagReport(Map<StationTag.StationTagType, Integer> numTagsByType, Set<StationTag.StationTagType> userTaggedTypes, long numPositiveTags, long numNegativeTags) {
        this.numTagsByType = numTagsByType;
        this.userTaggedTypes = userTaggedTypes;
        this.numPositiveTags = numPositiveTags;
        this.numNegativeTags = numNegativeTags;
    }

    public Map<StationTag.StationTagType, Integer> getNumTagsByType() {
        return numTagsByType;
    }

    public Set<StationTag.StationTagType> getUserTaggedTypes() {
        return userTaggedTypes;
    }

    public long getNumPositiveTags() {
        return numPositiveTags;
    }

    public long getNumNegativeTags() {
        return numNegativeTags;
    }
}
