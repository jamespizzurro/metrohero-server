package com.jamespizzurro.metrorailserver.service;

import com.google.common.collect.Multimap;
import com.jamespizzurro.metrorailserver.IncidentTextParser;
import com.jamespizzurro.metrorailserver.domain.*;
import com.jamespizzurro.metrorailserver.repository.StationProblemRepository;
import com.jamespizzurro.metrorailserver.repository.TrainCarProblemRepository;
import com.jamespizzurro.metrorailserver.repository.TrainProblemRepository;
import com.jamespizzurro.metrorailserver.repository.TrainStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import twitter4j.*;

import javax.annotation.PostConstruct;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TwitterService {

    private static final Logger logger = LoggerFactory.getLogger(TwitterService.class);

    private volatile Map<String, ProblemTweetResponse> stationTwitterProblemMap;
    private volatile Map<String, ProblemTweetResponse> trainTwitterProblemMap;
    private volatile Map<String, Boolean> stationHasTwitterProblemMap;
    private volatile List<StationProblem> mostRecentTweets;

    private final IncidentTextParser incidentTextParser;
    private final StationTaggingService stationTaggingService;
    private final TrainTaggingService trainTaggingService;
    private final StationProblemRepository stationProblemRepository;
    private final TrainProblemRepository trainProblemRepository;
    private final TrainCarProblemRepository trainCarProblemRepository;
    private final TrainStatusRepository trainStatusRepository;

    @Autowired
    public TwitterService(IncidentTextParser incidentTextParser, StationTaggingService stationTaggingService, TrainTaggingService trainTaggingService, StationProblemRepository stationProblemRepository, TrainProblemRepository trainProblemRepository, TrainCarProblemRepository trainCarProblemRepository, TrainStatusRepository trainStatusRepository) {
        this.incidentTextParser = incidentTextParser;
        this.stationTaggingService = stationTaggingService;
        this.trainTaggingService = trainTaggingService;
        this.stationProblemRepository = stationProblemRepository;
        this.trainProblemRepository = trainProblemRepository;
        this.trainCarProblemRepository = trainCarProblemRepository;
        this.trainStatusRepository = trainStatusRepository;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing Twitter service...");

        this.stationTwitterProblemMap = new HashMap<>();
        this.trainTwitterProblemMap = new HashMap<>();

        logger.info("...Twitter service initialized!");
    }

    @Scheduled(fixedDelay = 30000)
    private void update() {
        logger.info("Updating relevant WMATA-related tweets from Twitter...");

        Twitter twitter = TwitterFactory.getSingleton();

        // fetch official WMATA tweets
        List<Status> officialWmataTweets;
        try {
            officialWmataTweets = twitter.getUserTimeline("Metrorailinfo", new Paging(1, 100))
                    .stream()
                    .filter(tweet -> tweet.getInReplyToScreenName() == null || tweet.getInReplyToScreenName().isEmpty())
                    .collect(Collectors.toList());
        } catch (TwitterException e) {
            e.printStackTrace();
            return;
        }

        // fetch unofficial WMATA tweets
        List<Status> unofficialWmataTweets;
        Query query = new Query("wmata OR unsuckdcmetro OR fixwmata OR dcmetrosucks OR metrorailinfo OR railtransitops OR dcmetrohero OR metroheroalerts OR hakunawmata");
        query.setResultType(Query.ResultType.recent);
        query.setCount(100);
        try {
            unofficialWmataTweets = twitter.search(query).getTweets();
        } catch (TwitterException e) {
            e.printStackTrace();
            return;
        }

        // combine lists of WMATA tweets
        List<Status> tweets = new ArrayList<>(unofficialWmataTweets);
        for (Status officialWmataTweet : officialWmataTweets) {
            boolean alreadyExists = false;
            for (Status unofficialWmataTweet : unofficialWmataTweets) {
                if (unofficialWmataTweet.getId() == officialWmataTweet.getId()) {
                    alreadyExists = true;
                    break;
                }
            }
            if (!alreadyExists) {
                tweets.add(officialWmataTweet);
            }
        }

        List<StationProblem> stationProblems = new ArrayList<>();
        List<TrainProblem> trainProblems = new ArrayList<>();
        List<TrainCarProblem> trainCarProblems = new ArrayList<>();

//        // FOR DEVELOPMENT: use this to debug individual tweets
//        try {
//            tweets.clear();
//            tweets.add(twitter.showStatus(Long.parseLong("707935560380325888")));
//        } catch (TwitterException e) {
//            e.printStackTrace();
//        }

        for (Status tweet : tweets) {
            if (this.stationProblemRepository.existsByTwitterId(tweet.getId()) || this.trainProblemRepository.existsByTwitterId(tweet.getId()) || this.trainCarProblemRepository.existsByTwitterId(tweet.getId())) {
                // we've already parsed this tweet
                continue;
            }

            if (tweet.isRetweet()) {
                // retweets, even with new content, do not tend to be original thoughts
                continue;
            }

            if (tweet.getInReplyToStatusId() > 0 && tweet.getInReplyToUserId() != tweet.getUser().getId()) {
                // replies to tweets authored by someone other than the author of this tweet don't tend to be original thoughts either
                continue;
            }

            // clean up tweet text
            String text = tweet.getText();
            text = text.replaceAll("(~|\\|)", "");  // remove keyword characters that could interfere with subsequent parsing
            text = text.replaceAll(" +", " ").trim();   // make sure there's only one space between every word
            final String cleanedText = text;

            if (cleanedText.startsWith("I'm at ")) {
                // ignore annoying check-in tweets from Foursquare
                continue;
            }

            boolean disallowedKeywordPresent = false;
            final String[] disallowedKeywords = {
                    "icymi" /* 'in case you missed it?' we didn't miss anything, so go away */
            };
            for (String disallowedKeyword : disallowedKeywords) {
                if (cleanedText.toLowerCase().contains(disallowedKeyword.toLowerCase())) {
                    disallowedKeywordPresent = true;
                    break;
                }
            }
            if (disallowedKeywordPresent) {
                continue;
            }

//            logger.debug(cleanedText);

            Set<String> lineCodes = this.incidentTextParser.parseTextForLineCodes(cleanedText);

            Calendar createdDate = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            createdDate.setTime(tweet.getCreatedAt());

            Multimap<Set<String>, Set<String>> incidentMap = this.incidentTextParser.parseTextForIncidents(cleanedText, lineCodes);
            for (Map.Entry<Set<String>, Set<String>> incident : incidentMap.entries()) {
                Set<String> stationCodes = incident.getKey();
                Set<String> keywords = incident.getValue();

                // we used any line codes explicitly mentioned in this tweet to filter our station codes,
                // but there may still be some more line codes we can squeeze out from our filtered station codes,
                // especially if no line codes were explicitly mentioned in this tweet
                Set<String> expandedLineCodes = new HashSet<>(lineCodes);
                expandedLineCodes.addAll(this.incidentTextParser.parseStationCodesForLineCodes(stationCodes));

                StationProblem stationProblem = new StationProblem(tweet.getId(), tweet.getText(), createdDate, expandedLineCodes, stationCodes, keywords, tweet.getUser().getId(), tweet.getUser().getScreenName());
                stationProblems.add(stationProblem);
            }

            Map<String, Set<String>> trainIncidentMap = this.incidentTextParser.parseTextForTrainIncidents(cleanedText);
            for (Map.Entry<String, Set<String>> trainIncident : trainIncidentMap.entrySet()) {
                String realTrainId = trainIncident.getKey();
                Set<String> keywords = trainIncident.getValue();

                long estimatedIncidentTime = (createdDate.getTimeInMillis() / 1000) - TimeUnit.MINUTES.toSeconds(1);  // estimate it takes about a minute from when an incident occurs to when someone tweets about it

                List<TrainStatus> possibleTrainStatuses = this.trainStatusRepository.getByObservedDateAndRealTrainId(estimatedIncidentTime, realTrainId);
                if (possibleTrainStatuses == null || possibleTrainStatuses.size() != 1) {
                    // there's either no train with this ID (tweet might be referring to an incident that's actually older than we estimated), or there's actually more than one train with this real ID
                    // either way, we don't know which train this tweet is referring to, so abort
                    continue;
                }
                TrainStatus trainStatus = possibleTrainStatuses.get(0);

                TrainProblem trainProblem = new TrainProblem(tweet.getId(), tweet.getText(), createdDate, lineCodes, trainStatus.getTrainId(), realTrainId, keywords, tweet.getUser().getId(), tweet.getUser().getScreenName());
                trainProblems.add(trainProblem);
            }

            Map<String, Set<String>> trainCarIncidentMap = this.incidentTextParser.parseTextForTrainCarIncidents(cleanedText);
            for (Map.Entry<String, Set<String>> trainCarIncident : trainCarIncidentMap.entrySet()) {
                String trainCarId = trainCarIncident.getKey();
                Set<String> keywords = trainCarIncident.getValue();

                TrainCarProblem trainCarProblem = new TrainCarProblem(tweet.getId(), tweet.getText(), createdDate, lineCodes, trainCarId, keywords, tweet.getUser().getId(), tweet.getUser().getScreenName());
                trainCarProblems.add(trainCarProblem);
            }
        }

        this.stationProblemRepository.saveAll(stationProblems);
        this.trainProblemRepository.saveAll(trainProblems);
        this.trainCarProblemRepository.saveAll(trainCarProblems);

        Calendar fiveMinutesAgo = Calendar.getInstance();
        fiveMinutesAgo.add(Calendar.MINUTE, -5);

        for (StationProblem stationProblem : stationProblems) {
            // translate tweets about potential station problems into votes, when applicable

            if (stationProblem.getDate().before(fiveMinutesAgo)) {
                // for voting purposes, ignore tweets older than 5 minutes ago as they probably aren't relevant anymore,
                // or if they are, there wouldn't be good parity with when the vote expires versus when the tweet "expires"
                continue;
            }

//            logger.debug(" => riders are reporting " + String.join(", ", stationProblem.getKeywords()) + " at " + String.join(", ", stationProblem.getStationCodes()));

            for (String stationCode : stationProblem.getStationCodes()) {
                for (String keyword : stationProblem.getKeywords()) {
                    StationTag.StationTagType tagType = null;
                    switch (keyword) {
                        case "delays":
                        case "long waits":
                            tagType = StationTag.StationTagType.LONG_WAITING_TIME;
                            break;
                        case "crowded conditions":
                            tagType = StationTag.StationTagType.CROWDED;
                            break;
                        case "single-tracking":
                        case "trains bypassing":
                        case "trains turning around":
                            tagType = StationTag.StationTagType.POSTED_TIMES_INACCURATE;
                            break;
                        case "smoke":
                        case "fire":
                        case "fire dept activity":
                            tagType = StationTag.StationTagType.SMOKE_OR_FIRE;
                            break;
                    }
                    if (tagType != null) {
                        stationTaggingService.tag("TWITTER:" + stationProblem.getUserId(), stationProblem.getLineCodes(), stationCode, tagType, stationProblem.getTwitterId());
//                        logger.debug(" => Tagged " + stationCode + " for " + tagType);
                    }
                }
            }
        }

        for (TrainProblem trainProblem : trainProblems) {
            // translate tweets about potential train problems into votes, when applicable

            if (trainProblem.getDate().before(fiveMinutesAgo)) {
                // for voting purposes, ignore tweets older than 5 minutes ago as they probably aren't relevant anymore
                // otherwise we could tag the wrong train or the right train but with wrong metadata, e.g. what line and station it was tagged on/at
                continue;
            }

//            logger.debug(" => riders are reporting " + String.join(", ", trainProblem.getKeywords()) + " on Train " + trainProblem.getTrainId());

            for (String keyword : trainProblem.getKeywords()) {
                TrainTag.TrainTagType tagType = null;
                switch (keyword) {
                    case "good operator":
                        tagType = TrainTag.TrainTagType.GOOD_OPERATOR;
                        break;
                    case "smooth ride":
                        tagType = TrainTag.TrainTagType.GOOD_RIDE;
                        break;
                    case "new train":
                        tagType = TrainTag.TrainTagType.NEW_TRAIN;
                        break;
                    case "empty":
                        tagType = TrainTag.TrainTagType.EMPTY;
                        break;
                    case "bad operator":
                        tagType = TrainTag.TrainTagType.BAD_OPERATOR;
                        break;
                    case "crowded conditions":
                        tagType = TrainTag.TrainTagType.CROWDED;
                        break;
                    case "too hot":
                    case "too cold":
                        tagType = TrainTag.TrainTagType.UNCOMFORTABLE_TEMPS;
                        break;
                    case "offloaded":
                        tagType = TrainTag.TrainTagType.RECENTLY_OFFLOADED;
                        break;
                    case "uncomfortable ride":
                        tagType = TrainTag.TrainTagType.UNCOMFORTABLE_RIDE;
                        break;
                    case "isolated cars":
                        tagType = TrainTag.TrainTagType.ISOLATED_CARS;
                        break;
                    case "wrong number of cars":
                        tagType = TrainTag.TrainTagType.WRONG_NUM_CARS;
                        break;
                    case "wrong destination":
                        tagType = TrainTag.TrainTagType.WRONG_DESTINATION;
                        break;
                    case "needs cleaning":
                    case "malfunctioning":
                        tagType = TrainTag.TrainTagType.NEEDS_WORK;
                        break;
                    case "broken intercom":
                        tagType = TrainTag.TrainTagType.BROKEN_INTERCOM;
                        break;
                    case "disruptive passenger":
                        tagType = TrainTag.TrainTagType.DISRUPTIVE_PASSENGER;
                        break;
                }
                if (tagType != null) {
                    this.trainTaggingService.tag("TWITTER:" + trainProblem.getUserId(), trainProblem.getTrainId(), trainProblem.getRealTrainId(), null, null, tagType, trainProblem.getTwitterId());
//                    logger.debug(" => Tagged Train " + trainProblem.getTrainId() + " for " + tagType);
                }
            }
        }

        // update list of recent tweets for each station
        Map<String, ProblemTweetResponse> newStationTwitterProblemMap = new HashMap<>();
        Map<String, Boolean> stationHasTwitterProblemMap = new HashMap<>();
        List<Object[]> recentTweetsResults = this.stationProblemRepository.getRecent();
        if (recentTweetsResults != null) {
            for (Object[] recentTweetsResult : recentTweetsResults) {
                String stationCode = String.valueOf(recentTweetsResult[0]);
                String keywords = String.valueOf(recentTweetsResult[1]);
                Long[] twitterIds = (Long[]) recentTweetsResult[2];
                Long[] userIds = (Long[]) recentTweetsResult[3];
                Integer[] dates = (Integer[]) recentTweetsResult[4];
                String[] texts = (String[]) recentTweetsResult[5];

                newStationTwitterProblemMap.put(stationCode, new ProblemTweetResponse(keywords, twitterIds, userIds, dates, texts));
                stationHasTwitterProblemMap.put(stationCode, true);
            }
        }
        this.stationTwitterProblemMap = newStationTwitterProblemMap;
        this.stationHasTwitterProblemMap = stationHasTwitterProblemMap;

        // update list of recent tweets for each train
        Map<String, ProblemTweetResponse> newTrainTwitterProblemMap = new HashMap<>();
        List<Object[]> recentTrainTweetsResults = this.trainProblemRepository.getRecent();
        if (recentTrainTweetsResults != null) {
            for (Object[] recentTrainTweetsResult : recentTrainTweetsResults) {
                String trainId = String.valueOf(recentTrainTweetsResult[0]);
                String keywords = String.valueOf(recentTrainTweetsResult[1]);
                Long[] twitterIds = (Long[]) recentTrainTweetsResult[2];
                Long[] userIds = (Long[]) recentTrainTweetsResult[3];
                Integer[] dates = (Integer[]) recentTrainTweetsResult[4];
                String[] texts = (String[]) recentTrainTweetsResult[5];

                newTrainTwitterProblemMap.put(trainId, new ProblemTweetResponse(keywords, twitterIds, userIds, dates, texts));
            }
        }
        this.trainTwitterProblemMap = newTrainTwitterProblemMap;

        Calendar thirtyMinutesAgo = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        thirtyMinutesAgo.add(Calendar.MINUTE, -30);
        List<StationProblem> mostRecentTweets = this.stationProblemRepository.findAllByDateGreaterThanEqualOrderByDateDesc(thirtyMinutesAgo);
        for (StationProblem mostRecentTweet : mostRecentTweets) {
            // HACK: the timezone we pull out of the database incorrectly gets a EST/EDT timezone assigned to it when it's actually in GMT
            // until we can safely switch over this application to use GMT by default, something like this would appear to be necessary
            SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
            dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            SimpleDateFormat dateFormatLocal = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
            try {
                Calendar dateFixed = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
                dateFixed.setTime(dateFormatGmt.parse(dateFormatLocal.format(mostRecentTweet.getDate().getTime())));
                mostRecentTweet.setDate(dateFixed);
            } catch (ParseException e) {
                e.printStackTrace();
                continue;
            }

            mostRecentTweet.setTwitterIdString(String.valueOf(mostRecentTweet.getTwitterId()));
            mostRecentTweet.setUrl("https://twitter.com/" + mostRecentTweet.getUserId() + "/status/" + mostRecentTweet.getTwitterId());
        }
        this.mostRecentTweets = mostRecentTweets;

        logger.info("...successfully updated relevant WMATA-related tweets from Twitter!");
    }

    public Map<String, ProblemTweetResponse> getStationTwitterProblemMap() {
        return stationTwitterProblemMap;
    }

    public Map<String, ProblemTweetResponse> getTrainTwitterProblemMap() {
        return trainTwitterProblemMap;
    }

    public Map<String, Boolean> getStationHasTwitterProblemMap() {
        return stationHasTwitterProblemMap;
    }

    public List<StationProblem> getMostRecentTweets() {
        return mostRecentTweets;
    }
}
