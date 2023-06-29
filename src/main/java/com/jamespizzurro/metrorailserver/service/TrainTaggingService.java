package com.jamespizzurro.metrorailserver.service;

import com.google.common.util.concurrent.AtomicLongMap;
import com.jamespizzurro.metrorailserver.StationUtil;
import com.jamespizzurro.metrorailserver.domain.TrainStatus;
import com.jamespizzurro.metrorailserver.domain.TrainTag;
import com.jamespizzurro.metrorailserver.domain.TrainTagReport;
import com.jamespizzurro.metrorailserver.repository.TrainTagRepository;
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
public class TrainTaggingService {

    private static final Logger logger = LoggerFactory.getLogger(TrainTaggingService.class);
    private static final int MAX_ACTIVE_TAGS_PER_USER = 5;

    private Map<TrainTag, TrainTag> tagById;
    private Map<String /* trainId */, Map<TrainTag.TrainTagType, Set<TrainTag>>> tagsByTypeByTrain;
    private Map<String /* userId */, Set<TrainTag>> tagsByUser;
    private AtomicLongMap<String /* trainId */> numPositiveTagsByTrain;
    private AtomicLongMap<String /* trainId */> numNegativeTagsByTrain;

    private final TrainTagRepository trainTagRepository;

    @Autowired
    public TrainTaggingService(TrainTagRepository trainTagRepository) {
        this.trainTagRepository = trainTagRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing train tagging service...");

        this.tagById = new ConcurrentHashMap<>();
        this.tagsByTypeByTrain = new ConcurrentHashMap<>();
        this.tagsByUser = new ConcurrentHashMap<>();
        this.numPositiveTagsByTrain = AtomicLongMap.create();
        this.numNegativeTagsByTrain = AtomicLongMap.create();

        logger.info("...train tagging service initialized!");
    }

    public TrainTagReport generateTagReport(String userId, String trainId) {
        Map<TrainTag.TrainTagType, Integer> numTagsByType = new EnumMap<>(TrainTag.TrainTagType.class);
        Set<TrainTag.TrainTagType> userTaggedByType = null;

        for (TrainTag.TrainTagType trainTagType : TrainTag.TrainTagType.values()) {
            int numTags = 0;
            Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByType = this.tagsByTypeByTrain.get(trainId);
            if (tagsByType != null) {
                Set<TrainTag> tags = tagsByType.get(trainTagType);
                if (tags != null) {
                    numTags = tags.size();
                }
            }
            numTagsByType.put(trainTagType, numTags);
        }
        numTagsByType = StationUtil.sortByValue(numTagsByType);

        if (userId != null) {
            Set<TrainTag> userTags = this.tagsByUser.get(userId);
            if (userTags != null) {
                for (TrainTag userTag : userTags) {
                    if (userTag.getTrainId().equals(trainId)) {
                        if (userTaggedByType == null) {
                            userTaggedByType = EnumSet.noneOf(TrainTag.TrainTagType.class);
                        }
                        userTaggedByType.add(userTag.getType());
                    }
                }
            }
        }

        return new TrainTagReport(numTagsByType, userTaggedByType, getNumPositiveTags(trainId), getNumNegativeTags(trainId));
    }

    public Map<TrainTag.TrainTagType, Integer> getNumTrainTagsByType(String trainId) {
        Map<TrainTag.TrainTagType, Integer> numTagsByType = new EnumMap<>(TrainTag.TrainTagType.class);

        for (TrainTag.TrainTagType trainTagType : TrainTag.TrainTagType.values()) {
            Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByType = this.tagsByTypeByTrain.get(trainId);
            if (tagsByType != null) {
                Set<TrainTag> tags = tagsByType.get(trainTagType);
                if (tags != null) {
                    numTagsByType.put(trainTagType, tags.size());
                }
            }
        }
        numTagsByType = StationUtil.sortByValue(numTagsByType);

        return numTagsByType;
    }

    public TrainTagReport tag(String userId, String trainId, String realTrainId, String lineCode, String stationCode, TrainTag.TrainTagType tagType, Long tweetId) {
        Set<TrainTag> userTags = this.tagsByUser.get(userId);
        if (userTags == null || userTags.size() < MAX_ACTIVE_TAGS_PER_USER) {
            TrainTag tag = new TrainTag(userId, trainId, realTrainId, lineCode, stationCode, tagType, tweetId);
            if (userTags == null) {
                userTags = new HashSet<>();
                this.tagsByUser.put(userId, userTags);
            }
            if (!userTags.contains(tag)) {
                tag = this.trainTagRepository.save(tag);
                this.tagById.put(tag, tag);
                userTags.add(tag);

                Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByType = this.tagsByTypeByTrain.get(trainId);
                if (tagsByType == null) {
                    tagsByType = new EnumMap<>(TrainTag.TrainTagType.class);
                    this.tagsByTypeByTrain.put(trainId, tagsByType);
                }
                Set<TrainTag> tags = tagsByType.computeIfAbsent(tagType, k -> new HashSet<>());
                tags.add(tag);

                if (tagType.isPositive()) {
                    this.numPositiveTagsByTrain.getAndIncrement(trainId);
                } else {
                    this.numNegativeTagsByTrain.getAndIncrement(trainId);
                }
            }
        }

        return generateTagReport(userId, trainId);
    }

    public TrainTagReport untag(String userId, String trainId, TrainTag.TrainTagType tagType) {
        Set<TrainTag> userTags = this.tagsByUser.get(userId);
        if (userTags != null && userTags.size() > 0) {
            TrainTag tag = new TrainTag(userId, trainId, null, null, null, tagType, null);
            if (userTags.contains(tag)) {
                tag = this.tagById.remove(tag);
                this.trainTagRepository.deleteById(tag.getId());
                userTags.remove(tag);

                Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByType = this.tagsByTypeByTrain.get(trainId);
                if (tagsByType != null) {
                    Set<TrainTag> tags = tagsByType.get(tagType);
                    if (tags != null && tags.remove(tag) && tags.size() <= 0 && tagsByType.remove(tagType) != null && tagsByType.size() <= 0) {
                        this.tagsByTypeByTrain.remove(trainId);
                    }
                }
                if (userTags.size() <= 0) {
                    // free up some memory; this userId may not vote ever again
                    this.tagsByUser.remove(userId);
                }

                if (tagType.isPositive()) {
                    this.numPositiveTagsByTrain.getAndDecrement(trainId);
                } else {
                    this.numNegativeTagsByTrain.getAndDecrement(trainId);
                }
            }
        }

        return generateTagReport(userId, trainId);
    }

    public long getNumPositiveTags(String stationCode) {
        return this.numPositiveTagsByTrain.get(stationCode);
    }

    public long getNumNegativeTags(String stationCode) {
        return this.numNegativeTagsByTrain.get(stationCode);
    }

    @Scheduled(fixedDelay=60000)    // every minute
    private void untagExpired() {
        logger.info("Untagging any expired train tags...");
        Iterator<Map.Entry<String, Map<TrainTag.TrainTagType, Set<TrainTag>>>> tagsByTypeByTrainIterator = this.tagsByTypeByTrain.entrySet().iterator();
        while (tagsByTypeByTrainIterator.hasNext()) {
            Map.Entry<String, Map<TrainTag.TrainTagType, Set<TrainTag>>> tagsByTypeByTrainEntry = tagsByTypeByTrainIterator.next();
            String trainId = tagsByTypeByTrainEntry.getKey();
            Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByType = tagsByTypeByTrainEntry.getValue();

            if (tagsByType == null || tagsByType.size() <= 0) {
                tagsByTypeByTrainIterator.remove();
                continue;
            }

            Iterator<Map.Entry<TrainTag.TrainTagType, Set<TrainTag>>> tagsByTypeIterator = tagsByType.entrySet().iterator();
            while (tagsByTypeIterator.hasNext()) {
                Map.Entry<TrainTag.TrainTagType, Set<TrainTag>> tagsByTypeEntry = tagsByTypeIterator.next();
                TrainTag.TrainTagType type = tagsByTypeEntry.getKey();
                Set<TrainTag> tags = tagsByTypeEntry.getValue();

                Iterator<TrainTag> tagsIterator = tags.iterator();
                while (tagsIterator.hasNext()) {
                    TrainTag tag = tagsIterator.next();
                    long minutesDiff = TimeUnit.MILLISECONDS.toMinutes(Calendar.getInstance().getTimeInMillis() - tag.getDate().getTimeInMillis());
                    if (minutesDiff >= type.getMaxNumMinutesActive()) {
                        if (this.tagsByUser.get(tag.getUserId()).remove(tag)) {
                            if (this.tagsByUser.get(tag.getUserId()).size() <= 0) {
                                // free up some memory; this userId may not vote ever again
                                this.tagsByUser.remove(tag.getUserId());
                            }
                        }

                        if (type.isPositive()) {
                            this.numPositiveTagsByTrain.getAndDecrement(trainId);
                        } else {
                            this.numNegativeTagsByTrain.getAndDecrement(trainId);
                        }

                        tagsIterator.remove();
                    }
                }

                if (tags.size() <= 0) {
                    tagsByTypeIterator.remove();
                }
            }

            if (tagsByType.size() <= 0) {
                tagsByTypeByTrainIterator.remove();
            }
        }
        logger.info("...successfully untagged any expired train tags!");
    }

    public Map<String, TrainTagReport> getTrainTagReports() {
        Map<String, TrainTagReport> trainTagReports = new HashMap<>(this.tagsByTypeByTrain.size());
        for (String trainId : this.tagsByTypeByTrain.keySet()) {
            TrainTagReport trainTagReport = generateTagReport(null, trainId);
            trainTagReports.put(trainId, trainTagReport);
        }
        return trainTagReports;
    }

    public int getNumActiveNegativeTrainTags(String lineCode) {
        int sum = 0;

        for (Set<TrainTag> trainTags : this.tagsByUser.values()) {
            for (TrainTag trainTag : trainTags) {
                if (!trainTag.getType().isPositive() && trainTag.getLineCode().equals(lineCode)) {
                    sum++;
                }
            }
        }

        return sum;
    }

    public void migrateTrainTags(TrainStatus sourceTrain, TrainStatus targetTrain) {
        // NOTE:
        // this will overwrite all of the target train's tags with the source train's tags,
        // and also remove all of the source train's tags

        for (Set<TrainTag> trainTags : this.tagsByUser.values()) {
            List<TrainTag> trainTagsToAdd = new ArrayList<>();

            Iterator<TrainTag> trainTagIterator = trainTags.iterator();
            while (trainTagIterator.hasNext()) {
                TrainTag trainTag = trainTagIterator.next();

                if (trainTag.getTrainId().equals(sourceTrain.getTrainId())) {
                    trainTagIterator.remove();

                    trainTag.setTrainId(targetTrain.getTrainId());
                    trainTagsToAdd.add(trainTag);
                }
            }

            trainTags.addAll(trainTagsToAdd);
        }

        Map<TrainTag.TrainTagType, Set<TrainTag>> tagsByTrainTypeForSourceTrain = this.tagsByTypeByTrain.get(sourceTrain.getTrainId());
        if (tagsByTrainTypeForSourceTrain != null) {
            // changes to this.tagsByUser above modified the contents of each set for each train type
            // that means we just need to re-create each set here...
            tagsByTrainTypeForSourceTrain.replaceAll((k, v) -> new HashSet<>(v));

            // ...and reassign to the correct train
            this.tagsByTypeByTrain.put(targetTrain.getTrainId(), tagsByTrainTypeForSourceTrain);
            this.tagsByTypeByTrain.remove(sourceTrain.getTrainId());
        }

        long numPositiveTagsForSourceTrain = this.numPositiveTagsByTrain.get(sourceTrain.getTrainId());
        this.numPositiveTagsByTrain.put(targetTrain.getTrainId(), numPositiveTagsForSourceTrain);
        this.numPositiveTagsByTrain.remove(sourceTrain.getTrainId());

        long numNegativeTagsForSourceTrain = this.numNegativeTagsByTrain.get(sourceTrain.getTrainId());
        this.numNegativeTagsByTrain.put(targetTrain.getTrainId(), numNegativeTagsForSourceTrain);
        this.numNegativeTagsByTrain.remove(sourceTrain.getTrainId());

        targetTrain.setNumPositiveTags(sourceTrain.getNumPositiveTags());
        sourceTrain.setNumPositiveTags(0);

        targetTrain.setNumNegativeTags(sourceTrain.getNumNegativeTags());
        sourceTrain.setNumNegativeTags(0);
    }
}
