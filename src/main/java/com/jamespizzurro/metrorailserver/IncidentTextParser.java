package com.jamespizzurro.metrorailserver;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jamespizzurro.metrorailserver.domain.TrackCircuit;
import com.jamespizzurro.metrorailserver.service.TrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class IncidentTextParser {

    private static final Logger logger = LoggerFactory.getLogger(IncidentTextParser.class);

    private static final String TRAIN_NUMBER_REGEX = "(?i)(\\b(Trains? ?|T-?)(#|No\\.?|num|number)?) ?(\\d{3})(\\D|\\b)";
    private static final String TRAIN_CAR_NUMBER_REGEX = "(?i)(\\b(Trains? ?|T-?|Cars? ?)(#|No\\.?|num|number)?) ?(\\d{4})(\\D|\\b)";

    private final TrainService trainService;

    private Map<String, String> stationProblemKeywordsMap;
    private Map<String, String> trainProblemKeywordsMap;

    @Autowired
    public IncidentTextParser(TrainService trainService) {
        this.trainService = trainService;
    }

    @PostConstruct
    private void init() {
        logger.info("Initializing incident text parser service...");

        this.stationProblemKeywordsMap = buildStationProblemKeywordsMap();
        this.trainProblemKeywordsMap = buildTrainProblemKeywordsMap();

        logger.info("...incident text parser service initialized!");
    }

    public Set<String> parseTextForLineCodes(String text) {
        Set<String> lineCodes = new HashSet<>();

        if (Pattern.compile("\\b(RED|Red|RL|RD)\\b").matcher(text).find() || Pattern.compile("\\b(red line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            lineCodes.add("RD");
        }

        if (Pattern.compile("\\b(ORANGE|Orange|Org|OL|OR)\\b").matcher(text).find() || Pattern.compile("\\b(orange line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            lineCodes.add("OR");
        }

        if (Pattern.compile("\\b(SILVER|Silver|Sil|Slv|SL|SV)\\b").matcher(text).find() || Pattern.compile("\\b(silver line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            if (!text.toLowerCase().contains("silver s" /* Silver Spring station */)) {
                lineCodes.add("SV");
            }
        }

        if (Pattern.compile("\\b(BLUE|Blue|Blu|BL)\\b").matcher(text).find() || Pattern.compile("\\b(blue line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            lineCodes.add("BL");
        }

        if (Pattern.compile("\\b(YELLOW|Yellow|Yel|YL)\\b").matcher(text).find() || Pattern.compile("\\b(yellow line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            lineCodes.add("YL");
        }

        if (Pattern.compile("\\b(GREEN|Green|Grn|GL|GR)\\b").matcher(text).find() || Pattern.compile("\\b(green line)\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
            lineCodes.add("GR");
        }

        Pattern pattern = Pattern.compile(TRAIN_NUMBER_REGEX);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String trainId = matcher.group(4);
            if (StringUtils.isEmpty(trainId)) {
                continue;
            }

            String derivedLineCode = StationUtil.getLineCodeFromRealTrainId(trainId);
            if (derivedLineCode != null && !derivedLineCode.equals("N/A")) {
                lineCodes.add(derivedLineCode);
            }
        }

        return lineCodes;
    }

    public Multimap<Set<String>, Set<String>> parseTextForIncidents(String text, Set<String> lineCodes) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Multimap<Set<String>, Set<String>> incidentMap = ArrayListMultimap.create();

        String textToParse = text;
        textToParse = textToParse.replaceAll("(?i) +(@|at or near|in approach to|at|in|into|on|near|outside|inside|by|around|past|pass(ing|ed) through|bypass(ing|ed)|skipp(ing|ed)|express(ing|ed)|enter(ing|ed)|exit(ing|ed)) +(the +)?", "|");
        textToParse = textToParse.replaceAll("(?i) +(be?twe?e?n|fro?m|b/t|b/w) +", "~");

        List<String> rangeFragments = new ArrayList<>();
        List<String> nonRangeFragments = new ArrayList<>();

        String[] fragments = textToParse.split(String.format("((?<=%1$s)|(?=%1$s))", "(~|\\|)"));
        for (int f0 = 0, f1 = 1, f2 = 2; f2 < fragments.length; f0++, f1++, f2++) {
            String leftFragment = fragments[f0];
            String middleFragment = fragments[f1];
            String rightFragment = fragments[f2];

            if (Character.isDigit(rightFragment.charAt(0))) {
                // there's no reason why the right fragment should start with a digit
                // (this filters out stuff false positives like "at 11:45pm", which we don't care about)
                continue;
            }

            if (middleFragment.equals("|")) {
                nonRangeFragments.add(leftFragment);
                nonRangeFragments.add(rightFragment);
            } else if (middleFragment.equals("~")) {
                rangeFragments.add(leftFragment);
                rangeFragments.add(rightFragment);
            }
        }

        // process any range fragments
        for (int j = 0, k = 1; j < k && k < rangeFragments.size(); j += 2, k += 2) {
            String possibleKeywordText = rangeFragments.get(j);
            String possibleStationText = rangeFragments.get(k);

            possibleStationText = possibleStationText.replaceAll(" +due to +", " ");    // "due to" != "to"
            String[] possibleStationNames = possibleStationText.split("(?i)( +and +| *& *| +to +| +through +| +thru +)");
            if (possibleStationNames.length <= 0) {
                continue;
            }

            Set<String> stationCodes = new HashSet<>();

            for (int l = 0, m = 1; l < m && m < possibleStationNames.length; l += 2, m += 2) {
                String possibleFromStationNames = possibleStationNames[l];
                for (String possibleFromStationName : possibleFromStationNames.split("/")) {
                    String[] possibleFromStationCodes = StationUtil.getStationCodesFromText(possibleFromStationName, false);
                    if (possibleFromStationCodes == null) {
                        continue;
                    }

                    String possibleToStationNames = possibleStationNames[m];
                    for (String possibleToStationName : possibleToStationNames.split("/")) {
                        String[] possibleToStationCodes = StationUtil.getStationCodesFromText(possibleToStationName, false);
                        if (possibleToStationCodes == null) {
                            continue;
                        }

                        Set<String> bestStationCodes = null;

                        for (String fromStationCode : possibleFromStationCodes) {
                            for (String toStationCode : possibleToStationCodes) {
                                Set<String> candidateStationCodes = this.trainService.getStationCodes(fromStationCode, toStationCode);
                                if (candidateStationCodes == null) {
                                    continue;
                                }

                                if (bestStationCodes == null || candidateStationCodes.size() < bestStationCodes.size()) {   // when in doubt, pick the shortest route
                                    bestStationCodes = candidateStationCodes;
                                }
                            }
                        }

                        if (bestStationCodes == null) {
                            continue;
                        }

                        stationCodes.addAll(bestStationCodes);
                    }
                }
            }

            filterStationCodes(lineCodes, stationCodes);

            if (stationCodes.isEmpty()) {
                continue;
            }

            Map<String, String> keywordsPresent = new HashMap<>();
            this.stationProblemKeywordsMap.entrySet().stream().filter(problemKeyword -> Pattern.compile(problemKeyword.getKey().toLowerCase()).matcher(possibleKeywordText.toLowerCase()).find()).forEach(problemKeyword -> keywordsPresent.put(problemKeyword.getKey(), problemKeyword.getValue()));
            Set<String> keywordGroupsPresent = new HashSet<>(keywordsPresent.values());

            incidentMap.put(stationCodes, keywordGroupsPresent);
        }

        // process any non-range fragments
        for (int j = 0, k = 1; j < k && k < nonRangeFragments.size(); j += 2, k += 2) {
            String possibleKeywordText = nonRangeFragments.get(j);
            String possibleStationText = nonRangeFragments.get(k);

            Set<String> stationCodes = new HashSet<>();

            String[] possibleStationNames = possibleStationText.split("(?i)(,? +and +|,? *& *|,? +n?or +| *, *)");
            for (String possibleStationName : possibleStationNames) {
                String[] possibleStationCodes = StationUtil.getStationCodesFromText(possibleStationName, false);
                if (possibleStationCodes == null) {
                    continue;
                }

                stationCodes.addAll(Arrays.asList(possibleStationCodes));
            }

            filterStationCodes(lineCodes, stationCodes);

            if (stationCodes.isEmpty()) {
                continue;
            }

            Map<String, String> keywordsPresent = new HashMap<>();
            this.stationProblemKeywordsMap.entrySet().stream().filter(problemKeyword -> Pattern.compile(problemKeyword.getKey().toLowerCase()).matcher(possibleKeywordText.toLowerCase()).find()).forEach(problemKeyword -> keywordsPresent.put(problemKeyword.getKey(), problemKeyword.getValue()));
            Set<String> keywordGroupsPresent = new HashSet<>(keywordsPresent.values());

            incidentMap.put(stationCodes, keywordGroupsPresent);
        }

        if (incidentMap.size() == 1) {
            // if there's only one station mentioned, we can (probably) safely expand our keyword search to the entire text

            Map<String, String> keywordsPresent = new HashMap<>();
            this.stationProblemKeywordsMap.entrySet().stream().filter(problemKeyword -> Pattern.compile(problemKeyword.getKey().toLowerCase()).matcher(text.toLowerCase()).find()).forEach(problemKeyword -> keywordsPresent.put(problemKeyword.getKey(), problemKeyword.getValue()));
            Set<String> keywordGroupsPresent = new HashSet<>(keywordsPresent.values());

            Set<String> key = incidentMap.entries().iterator().next().getKey();
            incidentMap.removeAll(key);
            incidentMap.put(key, keywordGroupsPresent);
        }

        return incidentMap;
    }

    public Set<String> parseStationCodesForLineCodes(Set<String> stationCodes) {
        Set<String> lineCodes = new HashSet<>(Arrays.asList("RD", "OR", "SV", "BL", "YL", "GR"));

        for (String stationCode : stationCodes) {
            TrackCircuit stationTrackCircuit = this.trainService.getStationTrackCircuitMap().get(stationCode + "_1");  // HACK: it doesn't matter which track (1 or 2) we use here
            lineCodes.retainAll(stationTrackCircuit.getLineCodes());
        }

        return lineCodes;
    }

    public Map<String, Set<String>> parseTextForTrainIncidents(String text) {
        Map<String, Set<String>> incidentsByTrainId = new HashMap<>();

        Pattern pattern = Pattern.compile(TRAIN_NUMBER_REGEX);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String trainId = matcher.group(4);
            if (StringUtils.isEmpty(trainId)) {
                continue;
            }

            Set<String> keywords = this.trainProblemKeywordsMap.entrySet().stream().filter(problemKeyword -> Pattern.compile(problemKeyword.getKey().toLowerCase()).matcher(text.toLowerCase()).find()).map(Map.Entry::getValue).collect(Collectors.toSet());
            incidentsByTrainId.put(trainId, keywords);
        }

        return incidentsByTrainId;
    }

    public Map<String, Set<String>> parseTextForTrainCarIncidents(String text) {
        Map<String, Set<String>> incidentsByTrainCarId = new HashMap<>();

        Pattern pattern = Pattern.compile(TRAIN_CAR_NUMBER_REGEX);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String trainCarId = matcher.group(4);
            if (StringUtils.isEmpty(trainCarId)) {
                continue;
            }

            Set<String> keywords = this.trainProblemKeywordsMap.entrySet().stream().filter(problemKeyword -> Pattern.compile(problemKeyword.getKey().toLowerCase()).matcher(text.toLowerCase()).find()).map(Map.Entry::getValue).collect(Collectors.toSet());
            incidentsByTrainCarId.put(trainCarId, keywords);
        }

        return incidentsByTrainCarId;
    }

    private static void filterStationCodes(Set<String> lineCodes, Set<String> stationCodes) {
        // there's not a guaranteed one-to-one relationship between station name and station code
        // however, if we have line codes, we can filter out at least some of the excess station codes

        if (lineCodes == null || lineCodes.isEmpty()) {
            return;
        }

        // Fort Totten
        if (stationCodes.contains("B06") || stationCodes.contains("E06")) {
            if (!lineCodes.contains("RD")) {
                stationCodes.remove("B06");
            }

            if (!lineCodes.contains("YL") && !lineCodes.contains("GR")) {
                stationCodes.remove("E06");
            }
        }

        // Gallery Place
        if (stationCodes.contains("B01") || stationCodes.contains("F01")) {
            if (!lineCodes.contains("RD")) {
                stationCodes.remove("B01");
            }

            if (!lineCodes.contains("YL") && !lineCodes.contains("GR")) {
                stationCodes.remove("F01");
            }
        }

        // L'Enfant Plaza
        if (stationCodes.contains("D03") || stationCodes.contains("F03")) {
            if (!lineCodes.contains("OR") && !lineCodes.contains("SV") && !lineCodes.contains("BL")) {
                stationCodes.remove("D03");
            }

            if (!lineCodes.contains("YL") && !lineCodes.contains("GR")) {
                stationCodes.remove("F03");
            }
        }

        // Metro Center
        if (stationCodes.contains("A01") || stationCodes.contains("C01")) {
            if (!lineCodes.contains("RD")) {
                stationCodes.remove("A01");
            }

            if (!lineCodes.contains("OR") && !lineCodes.contains("SV") && !lineCodes.contains("BL")) {
                stationCodes.remove("C01");
            }
        }
    }

    private static Map<String, String> buildStationProblemKeywordsMap() {
        Map<String, String> problemKeywordsMap = new HashMap<>();

        problemKeywordsMap.put("delay", "delays");
        problemKeywordsMap.put("single(\\-| )?track", "single-tracking");
        problemKeywordsMap.put("holding", "trains holding");
        problemKeywordsMap.put("wait", "long waits");
        problemKeywordsMap.put("headway", "long waits");
        problemKeywordsMap.put("jammed", "jams");
        problemKeywordsMap.put("(off|un|de)(\\-| )?(board|load|train)", "trains offloading");
        problemKeywordsMap.put("sitting", "long waits");
        problemKeywordsMap.put("stuck", "being stuck");
        problemKeywordsMap.put("stopped", "being stopped");
        problemKeywordsMap.put("stalled", "being stopped");
        problemKeywordsMap.put("slow", "trains moving slowly");
        problemKeywordsMap.put("speed restriction", "trains moving slowly");
        problemKeywordsMap.put("[0-9]+\\+? ?mph", "train speed problems");
        problemKeywordsMap.put("packed", "crowded conditions");
        problemKeywordsMap.put("crowd", "crowded conditions");
        problemKeywordsMap.put("sardine", "crowded conditions");
        problemKeywordsMap.put("full platform", "crowded conditions");
        problemKeywordsMap.put("platform (is )?full", "crowded conditions");
        problemKeywordsMap.put("congestion", "congestion");
        problemKeywordsMap.put("back(ed) up", "congestion");
        problemKeywordsMap.put("(?=.*\\btrain(s)?\\b)(?=.*\\bout of service\\b).*", "trains out of service");
        problemKeywordsMap.put("(?=.*\\btrain(s)?\\b)(?=.*\\bOOS\\b).*", "trains out of service");
        problemKeywordsMap.put("pass(ing|ed) through", "trains bypassing");
        problemKeywordsMap.put("bypass(ing|ed)", "trains bypassing");
        problemKeywordsMap.put("skipp(ing|ed)", "trains bypassing");
        problemKeywordsMap.put("express(ing|ed)", "trains bypassing");
        problemKeywordsMap.put("(short(\\-| )turn|turn(ing)? around)", "trains turning around");
        problemKeywordsMap.put("suspend(ing|ed)", "service suspensions");
        problemKeywordsMap.put("(train|door).?malfunction", "train malfunctions");
        problemKeywordsMap.put("disabled", "disabled trains");
        problemKeywordsMap.put("burning insulator", "track problems");
        problemKeywordsMap.put("arch?ing insulator", "track problems");
        problemKeywordsMap.put("insulator arch?ing", "track problems");
        problemKeywordsMap.put("(down(ed)?|broken|bobbing|chipped|cracked|malfunctioning|bent|warped|missing|defective) (track )?(switch|signal|circuit|(third(\\-| )?)?rail|fastener|bolt)", "track problems");
        problemKeywordsMap.put("(track|switch|signal|circuit|rail) (problem|condition|issue)", "track problems");
        problemKeywordsMap.put("(track|switch|signal|circuit|rail) (work|repair)", "track work");
        problemKeywordsMap.put("trackwork", "track work");
        problemKeywordsMap.put("\\b(power outage|no power|power out|power problem)", "power problems");
        problemKeywordsMap.put("smoke", "smoke");
        problemKeywordsMap.put("fire (?!(dep(ar)?t(ment)?)|(truck))", "fire");
        problemKeywordsMap.put("fire ((dep(ar)?t(ment)?)|(truck))", "fire dept activity");
        problemKeywordsMap.put("police|\\btpas\\b", "police activity");
        problemKeywordsMap.put("derailment", "train derailments");
        problemKeywordsMap.put("(sick|ill) (passenger|rider|customer)", "medical emergency");
        problemKeywordsMap.put("medical emergency", "medical emergency");

        return problemKeywordsMap;
    }

    private static Map<String, String> buildTrainProblemKeywordsMap() {
        Map<String, String> problemKeywordsMap = new HashMap<>();

        problemKeywordsMap.put("(excellent|exemplary|great|good|friendly|best|nice|happy|helpful|awesome|bad ?ass|sweet|cool|lit|dope|pleasant|polite|kind) (train )?(operator|driver|conductor)", "good operator");
        problemKeywordsMap.put("(excellent|good|great|smooth|comfortable|nice|uneventful|comfy|easy|quiet|quick|fast|speedy|zippy|pleasant) (ride|trip)", "smooth ride");
        problemKeywordsMap.put("7\\d\\d\\d|7k|new ?train", "new train");
        problemKeywordsMap.put("empty", "empty");
        problemKeywordsMap.put("(bad|mean|unfriendly|worst|grumpy|rude|unhelpful|shitty|crappy|annoying|angry|salty|crabby|crotchety) (train )?(operator|driver|conductor)", "bad operator");
        problemKeywordsMap.put("packed", "crowded conditions");
        problemKeywordsMap.put("crowd", "crowded conditions");
        problemKeywordsMap.put("sardine", "crowded conditions");
        problemKeywordsMap.put("full train", "crowded conditions");
        problemKeywordsMap.put("train (is )?full", "crowded conditions");
        problemKeywordsMap.put("hot(\\-| )?car", "too hot");
        problemKeywordsMap.put("cold(\\-| )?car", "too cold");
        problemKeywordsMap.put("(off|un|de)(\\-| )?(board|load|train)", "offloaded");
        problemKeywordsMap.put("\\b(bumpy|shake?y|rough|turbulent|uncomfortable|jerky|nauseous)", "uncomfortable ride");
        problemKeywordsMap.put("isolated", "isolated cars");
        problemKeywordsMap.put("(wrong|incorrect) (#|no\\.?|num|number) of cars", "wrong number of cars");
        problemKeywordsMap.put("(#|no\\.?|num|number) of cars.?(wrong|incorrect) ", "wrong number of cars");
        problemKeywordsMap.put("(wrong|incorrect) destination", "wrong destination");
        problemKeywordsMap.put("destination.?is (wrong|incorrect)", "wrong destination");
        problemKeywordsMap.put("\\b(trash|refuge|dirty|unclean|leak|loose|sticky|rattling|mess|reeks|reaks|smells|pee|poo|shit|piss|crap|urine|biohazard|feces|fecal|caca|dooty|doo(\\-| )doo)|condom", "needs cleaning");
        problemKeywordsMap.put("(train|door).?malfunction", "malfunctioning");
        problemKeywordsMap.put("(?=.*(broken|garbled|jarbled|jumbled|incomprehensible|unintelligible|non(\\-| )?functioning))(?=.*(PA|intercom|announcement|voice|operator|driver|conductor))", "broken intercom");

        problemKeywordsMap.put("delay", "delays");
        problemKeywordsMap.put("single(\\-| )?track", "single-tracking");
        problemKeywordsMap.put("holding", "holding");
        problemKeywordsMap.put("wait", "holding");
        problemKeywordsMap.put("sitting", "holding");
        problemKeywordsMap.put("stuck", "being stuck");
        problemKeywordsMap.put("stopped", "being stopped");
        problemKeywordsMap.put("stalled", "being stopped");
        problemKeywordsMap.put("slow", "moving slowly");
        problemKeywordsMap.put("speed restriction", "moving slowly");
        problemKeywordsMap.put("[0-9]+\\+? ?mph", "speed problems");
        problemKeywordsMap.put("congestion", "congestion");
        problemKeywordsMap.put("back(ed) up", "congestion");
        problemKeywordsMap.put("out of service|OOS", "out of service");
        problemKeywordsMap.put("pass(ing|ed) through", "bypassing stations");
        problemKeywordsMap.put("bypass(ing|ed)", "bypassing stations");
        problemKeywordsMap.put("skipp(ing|ed)", "bypassing stations");
        problemKeywordsMap.put("express(ing|ed)", "bypassing stations");
        problemKeywordsMap.put("(short(\\-| )turn|turn(ing)? around)", "turning around early");
        problemKeywordsMap.put("suspend(ing|ed)", "service suspensions");
        problemKeywordsMap.put("disabled", "disabled");
        problemKeywordsMap.put("\\b(power outage|no power|power out|power problem|no lights|dark|emergency light)", "power problem");
        problemKeywordsMap.put("smoke", "smoke");
        problemKeywordsMap.put("fire (?!(dep(ar)?t(ment)?)|(truck))", "fire");
        problemKeywordsMap.put("fire ((dep(ar)?t(ment)?)|(truck))", "fire dept activity");
        problemKeywordsMap.put("police|\\btpas\\b", "police activity");
        problemKeywordsMap.put("derail(ed|ment)", "derailment");
        problemKeywordsMap.put("(sick|ill) (passenger|rider|customer)", "medical emergency");
        problemKeywordsMap.put("medical emergency", "medical emergency");

        return problemKeywordsMap;
    }
}
