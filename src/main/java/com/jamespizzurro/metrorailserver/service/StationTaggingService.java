package com.jamespizzurro.metrorailserver.service;

import com.google.common.util.concurrent.AtomicLongMap;
import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.StationTag;
import com.jamespizzurro.metrorailserver.domain.StationTagReport;
import com.jamespizzurro.metrorailserver.repository.StationTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class StationTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(StationTaggingService.class);
    private static final int MAX_ACTIVE_TAGS_PER_USER = 10;

    private Map<StationTag, StationTag> tagById;
    private Map<String /* stationCode */, Map<StationTag.StationTagType, Set<StationTag>>> tagsByTypeByStation;
    private Map<String /* userId */, Set<StationTag>> tagsByUser;
    private AtomicLongMap<String /* stationCode */> numPositiveTagsByStation;
    private AtomicLongMap<String /* stationCode */> numNegativeTagsByStation;

    private final StationTagRepository stationTagRepository;

    @Autowired
    public StationTaggingService(StationTagRepository stationTagRepository) {
        this.stationTagRepository = stationTagRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing station tagging service...");

        this.tagById = new ConcurrentHashMap<>();
        this.tagsByTypeByStation = new ConcurrentHashMap<>();
        this.tagsByUser = new ConcurrentHashMap<>();
        this.numPositiveTagsByStation = AtomicLongMap.create();
        this.numNegativeTagsByStation = AtomicLongMap.create();

        logger.info("...station tagging service initialized!");
    }

    public StationTagReport generateTagReport(String userId, String stationCode) {
        Map<StationTag.StationTagType, Integer> numTagsByType = new EnumMap<>(StationTag.StationTagType.class);
        Set<StationTag.StationTagType> userTaggedByType = null;

        for (StationTag.StationTagType stationTagType : StationTag.StationTagType.values()) {
            int numTags = 0;
            Map<StationTag.StationTagType, Set<StationTag>> tagsByType = this.tagsByTypeByStation.get(stationCode);
            if (tagsByType != null) {
                Set<StationTag> tags = tagsByType.get(stationTagType);
                if (tags != null) {
                    numTags = tags.size();
                }
            }
            numTagsByType.put(stationTagType, numTags);
        }
        numTagsByType = StationUtil.sortByValue(numTagsByType);

        if (userId != null) {
            Set<StationTag> userTags = this.tagsByUser.get(userId);
            if (userTags != null) {
                for (StationTag userTag : userTags) {
                    if (userTag.getStationCode().equals(stationCode)) {
                        if (userTaggedByType == null) {
                            userTaggedByType = EnumSet.noneOf(StationTag.StationTagType.class);
                        }
                        userTaggedByType.add(userTag.getType());
                    }
                }
            }
        }

        return new StationTagReport(numTagsByType, userTaggedByType, getNumPositiveTags(stationCode), getNumNegativeTags(stationCode));
    }

    public StationTagReport tag(String userId, String[] lineCodes, String stationCode, StationTag.StationTagType tagType, Long tweetId) {
        Set<StationTag> userTags = this.tagsByUser.get(userId);
        if (userTags == null || userTags.size() < MAX_ACTIVE_TAGS_PER_USER) {
            StationTag tag = new StationTag(userId, lineCodes, stationCode, tagType, tweetId);
            if (userTags == null) {
                userTags = new HashSet<>();
                this.tagsByUser.put(userId, userTags);
            }
            if (!userTags.contains(tag)) {
                tag = this.stationTagRepository.save(tag);
                this.tagById.put(tag, tag);
                userTags.add(tag);

                Map<StationTag.StationTagType, Set<StationTag>> tagsByType = this.tagsByTypeByStation.get(stationCode);
                if (tagsByType == null) {
                    tagsByType = new EnumMap<>(StationTag.StationTagType.class);
                    this.tagsByTypeByStation.put(stationCode, tagsByType);
                }
                Set<StationTag> tags = tagsByType.computeIfAbsent(tagType, k -> new HashSet<>());
                tags.add(tag);

                if (tagType.isPositive()) {
                    this.numPositiveTagsByStation.getAndIncrement(stationCode);
                } else {
                    this.numNegativeTagsByStation.getAndIncrement(stationCode);
                }
            }
        }

        return generateTagReport(userId, stationCode);
    }

    public StationTagReport untag(String userId, String stationCode, StationTag.StationTagType tagType) {
        Set<StationTag> userTags = this.tagsByUser.get(userId);
        if (userTags != null && userTags.size() > 0) {
            StationTag tag = new StationTag(userId, null, stationCode, tagType, null);
            if (userTags.contains(tag)) {
                tag = this.tagById.remove(tag);
                this.stationTagRepository.deleteById(tag.getId());
                userTags.remove(tag);

                Map<StationTag.StationTagType, Set<StationTag>> tagsByType = this.tagsByTypeByStation.get(stationCode);
                if (tagsByType != null) {
                    Set<StationTag> tags = tagsByType.get(tagType);
                    if (tags != null && tags.remove(tag) && tags.size() <= 0 && tagsByType.remove(tagType) != null && tagsByType.size() <= 0) {
                        this.tagsByTypeByStation.remove(stationCode);
                    }
                }
                if (userTags.size() <= 0) {
                    // free up some memory; this userId may not vote ever again
                    this.tagsByUser.remove(userId);
                }

                if (tagType.isPositive()) {
                    this.numPositiveTagsByStation.getAndDecrement(stationCode);
                } else {
                    this.numNegativeTagsByStation.getAndDecrement(stationCode);
                }
            }
        }

        return generateTagReport(userId, stationCode);
    }

    public long getNumPositiveTags(String stationCode) {
        return this.numPositiveTagsByStation.get(stationCode);
    }

    public long getNumNegativeTags(String stationCode) {
        return this.numNegativeTagsByStation.get(stationCode);
    }

    @Scheduled(fixedDelay=60000)    // every minute
    private void untagExpired() {
        logger.info("Untagging any expired station tags...");
        Iterator<Map.Entry<String, Map<StationTag.StationTagType, Set<StationTag>>>> tagsByTypeByStationIterator = this.tagsByTypeByStation.entrySet().iterator();
        while (tagsByTypeByStationIterator.hasNext()) {
            Map.Entry<String, Map<StationTag.StationTagType, Set<StationTag>>> tagsByTypeByStationEntry = tagsByTypeByStationIterator.next();
            String stationCode = tagsByTypeByStationEntry.getKey();
            Map<StationTag.StationTagType, Set<StationTag>> tagsByType = tagsByTypeByStationEntry.getValue();

            if (tagsByType == null || tagsByType.size() <= 0) {
                tagsByTypeByStationIterator.remove();
                continue;
            }

            Iterator<Map.Entry<StationTag.StationTagType, Set<StationTag>>> tagsByTypeIterator = tagsByType.entrySet().iterator();
            while (tagsByTypeIterator.hasNext()) {
                Map.Entry<StationTag.StationTagType, Set<StationTag>> tagsByTypeEntry = tagsByTypeIterator.next();
                StationTag.StationTagType type = tagsByTypeEntry.getKey();
                Set<StationTag> tags = tagsByTypeEntry.getValue();

                Iterator<StationTag> tagsIterator = tags.iterator();
                while (tagsIterator.hasNext()) {
                    StationTag tag = tagsIterator.next();
                    long minutesDiff = TimeUnit.MILLISECONDS.toMinutes(Calendar.getInstance().getTimeInMillis() - tag.getDate().getTimeInMillis());
                    if (minutesDiff >= type.getMaxNumMinutesActive()) {
                        if (this.tagsByUser.get(tag.getUserId()).remove(tag)) {
                            if (this.tagsByUser.get(tag.getUserId()).size() <= 0) {
                                // free up some memory; this userId may not vote ever again
                                this.tagsByUser.remove(tag.getUserId());
                            }
                        }

                        if (type.isPositive()) {
                            this.numPositiveTagsByStation.getAndDecrement(stationCode);
                        } else {
                            this.numNegativeTagsByStation.getAndDecrement(stationCode);
                        }

                        tagsIterator.remove();
                    }
                }

                if (tags.size() <= 0) {
                    tagsByTypeIterator.remove();
                }
            }

            if (tagsByType.size() <= 0) {
                tagsByTypeByStationIterator.remove();
            }
        }
        logger.info("...successfully untagged any expired station tags!");
    }

    public Map<String, Long> getNumPositiveTagsByStation() {
        return numPositiveTagsByStation.asMap();
    }

    public Map<String, Long> getNumNegativeTagsByStation() {
        return numNegativeTagsByStation.asMap();
    }

    public Map<String, StationTagReport> getStationTagReports() {
        Map<String, StationTagReport> stationTagReports = new HashMap<>(this.tagsByTypeByStation.size());
        for (String stationCode : this.tagsByTypeByStation.keySet()) {
            StationTagReport stationTagReport = generateTagReport(null, stationCode);
            stationTagReports.put(stationCode, stationTagReport);
        }
        return stationTagReports;
    }
}
