package com.jamespizzurro.metrorailserver;

import info.debatty.java.stringsimilarity.JaroWinkler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class StationUtil {

    private static final Logger logger = LoggerFactory.getLogger(StationUtil.class);

    private static JaroWinkler jw = new JaroWinkler();

    private static Map<String, String[]> stationCodeMap = new HashMap<>();
    private static Map<String, String> stationAbbreviationMap = new HashMap<>();
    private static Map<String, String[]> stationNameMap = new HashMap<>();
    static {
        Scanner scanner = new Scanner(Thread.currentThread().getContextClassLoader().getResourceAsStream("stations.csv"));
        while (scanner.hasNextLine()) {
            String row = scanner.nextLine();
            String[] rowData = row.split(",");
            if (rowData.length != 3) {
                logger.error("Malformed row data in stations.csv: " + row);
            }
            String[] stationCodes = rowData[0].split(Pattern.quote("|"));
            if (stationCodes.length <= 0) {
                logger.error("Malformed station codes in stations.csv: " + rowData[0]);
            }
            String stationAbbreviation = rowData[1];
            String[] stationNames = rowData[2].split(Pattern.quote("|"));
            if (stationNames.length <= 0) {
                logger.error("Malformed station names in stations.csv: " + rowData[1]);
            }

            for (String stationCode : stationCodes) {
                stationCodeMap.put(stationCode, stationNames);
            }
            for (String stationCode : stationCodes) {
                stationAbbreviationMap.put(stationCode, stationAbbreviation);
            }
            for (String stationName : stationNames) {
                stationNameMap.put(stationName, stationCodes);
            }
        }
        scanner.close();
    }

    public static String[] getStationCodesFromText(String text, boolean isCleanText) {
        String bestStationName = null;
        String[] bestStationCodes = null;
        double greatestSimilarity = 0;

        for (Map.Entry<String, String[]> entry : stationNameMap.entrySet()) {
            String possibleStationName = entry.getKey();
            String[] possibleStationCodes = entry.getValue();

            String textToParse = text;
            String possibleStationNameToParse = possibleStationName;
            if (isCleanText) {
                possibleStationNameToParse = possibleStationNameToParse.toLowerCase();
            } else {
                // for unclean text, e.g. from tweets, we need to be a little more careful
                // that's because this text often contains more words than just those that make up a station name

                // if there's a hashtag, remove it and add spaces between each capital letter
                if (textToParse.contains("#")) {
                    textToParse = textToParse.replaceAll("#([A-Z]+[a-z]*)([A-Z][a-z]+)", "$1 $2");
                    textToParse = textToParse.replaceAll("#", "");
                }

                String[] textToParseSplit = textToParse.trim().split("[\\s-–—]+");
                String[] possibleStationNameSplit = possibleStationName.trim().split("[\\s-–—]+");
                if (textToParseSplit.length < possibleStationNameSplit.length) {
                    continue;
                } else if (textToParseSplit.length > possibleStationNameSplit.length) {
                    textToParseSplit = Arrays.copyOfRange(textToParseSplit, 0, possibleStationNameSplit.length);
                }
                textToParse = String.join(" ", textToParseSplit);

                if (textToParseSplit.length == 1 && textToParse.length() < 5 && !textToParse.toLowerCase().equals(possibleStationName.toLowerCase())) {
                    // one word station names less than 5 characters in length must match exactly
                    // e.g. NoMa, DCA, etc.
                    continue;
                } else {
                    boolean areAllFirstCharactersEqual = true;
                    for (int i = 0; i < textToParseSplit.length; i++) {
                        if (StringUtils.isEmpty(textToParseSplit[i]) || !String.valueOf(textToParseSplit[i].charAt(0)).toLowerCase().equals(String.valueOf(possibleStationNameSplit[i].charAt(0)).toLowerCase())) {
                            areAllFirstCharactersEqual = false;
                            break;
                        }
                    }
                    if (!areAllFirstCharactersEqual) {
                        continue;
                    }
                }
            }

            double distance = jw.similarity(textToParse, possibleStationNameToParse);
            if (distance > greatestSimilarity) {
                bestStationName = possibleStationNameToParse;
                bestStationCodes = possibleStationCodes;
                greatestSimilarity = distance;
            }
        }

//        if (bestStationName != null) {
//            logger.warn(text + " => " + bestStationName + " (" + greatestSimilarity + ")");
//        }

        if (greatestSimilarity >= 0.8) {
            return bestStationCodes;
        } else {
            return null;
        }
    }

    public static boolean isCurrentTimeBetween(String startTimeString, String endTimeString) {
        return isTimeBetween(Calendar.getInstance(), startTimeString, endTimeString);
    }

    // SOURCE: http://stackoverflow.com/a/28208889/1072621
    public static boolean isTimeBetween(Calendar calendar, String startTimeString, String endTimeString) {
        try {
            String reg = "^([0-1][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])$";
            if (startTimeString.matches(reg) && endTimeString.matches(reg)) {
                boolean valid;

                java.util.Date startTime = new SimpleDateFormat("HH:mm:ss").parse(startTimeString);
                Calendar startCalendar = (Calendar) calendar.clone();
                startCalendar.setTime(startTime);

                java.util.Date currentTime = new SimpleDateFormat("HH:mm:ss").parse(new SimpleDateFormat("HH:mm:ss").format(calendar.getTime()));
                Calendar currentCalendar = (Calendar) calendar.clone();
                currentCalendar.setTime(currentTime);

                java.util.Date endTime = new SimpleDateFormat("HH:mm:ss").parse(endTimeString);
                Calendar endCalendar = (Calendar) calendar.clone();
                endCalendar.setTime(endTime);

                if (currentTime.compareTo(endTime) < 0) {
                    currentCalendar.add(Calendar.DATE, 1);
                    currentTime = currentCalendar.getTime();
                }

                if (startTime.compareTo(endTime) < 0) {
                    startCalendar.add(Calendar.DATE, 1);
                    startTime = startCalendar.getTime();
                }

                if (currentTime.before(startTime)) {
                    valid = false;
                } else {
                    if (currentTime.after(endTime)) {
                        endCalendar.add(Calendar.DATE, 1);
                        endTime = endCalendar.getTime();
                    }

                    valid = currentTime.before(endTime);
                }

                return valid;
            } else {
                logger.warn("Not a valid time; expecting HH:mm:ss format");
                return false;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getStationName(String stationCode) {
        return getOptimalStationName(stationCode, null);
    }
    public static String getOptimalStationName(String stationCode, Integer maxLength) {
        String[] stationNames = stationCodeMap.get(stationCode);
        if (stationNames == null) {
            return null;
        }

        if (maxLength == null) {
            return stationNames[0];
        } else if (maxLength <= 0) {
            return null;
        }

        for (String stationName : stationNames) {
            if (stationName.length() <= maxLength) {
                return stationName;
            }
        }

        return null;
    }

    public static String getStationAbbreviation(String stationCode) {
        return (stationCode != null) ? stationAbbreviationMap.get(stationCode) : null;
    }

    public static String getDirectionName(String lineCode, Integer directionNumber) {
        String directionName = null;

        switch (lineCode) {
            case "RD":
                if (directionNumber == 1) {
                    directionName = "Eastbound";
                } else if (directionNumber == 2) {
                    directionName = "Westbound";
                }
                break;
            case "OR":
                if (directionNumber == 1) {
                    directionName = "Eastbound";
                } else if (directionNumber == 2) {
                    directionName = "Westbound";
                }
                break;
            case "SV":
                if (directionNumber == 1) {
                    directionName = "Eastbound";
                } else if (directionNumber == 2) {
                    directionName = "Westbound";
                }
                break;
            case "BL":
                if (directionNumber == 1) {
                    directionName = "Eastbound";
                } else if (directionNumber == 2) {
                    directionName = "Westbound";
                }
                break;
            case "YL":
                if (directionNumber == 1) {
                    directionName = "Northbound";
                } else if (directionNumber == 2) {
                    directionName = "Southbound";
                }
                break;
            case "GR":
                if (directionNumber == 1) {
                    directionName = "Northbound";
                } else if (directionNumber == 2) {
                    directionName = "Southbound";
                }
                break;
        }

        return directionName;
    }

    public static String getLineName(String lineCode) {
        String lineName = null;

        switch (lineCode) {
            case "RD":
                lineName = "Red";
                break;
            case "OR":
                lineName = "Orange";
                break;
            case "SV":
                lineName = "Silver";
                break;
            case "BL":
                lineName = "Blue";
                break;
            case "YL":
                lineName = "Yellow";
                break;
            case "GR":
                lineName = "Green";
                break;
        }

        return lineName;
    }

    public static String getLineCodeFromRealTrainId(String realTrainId) {
        if (StringUtils.isEmpty(realTrainId)) {
            return null;
        }

        String lineCode;

        String firstDigitOfTrainId = String.valueOf(realTrainId.charAt(0));
        switch (firstDigitOfTrainId) {
            case "1":
            case "2":
                lineCode = "RD";
                break;
            case "3":
                lineCode = "YL";
                break;
            case "4":
                lineCode = "BL";
                break;
            case "5":
                lineCode = "GR";
                break;
            case "6":
                lineCode = "SV";
                break;
            case "9":
                lineCode = "OR";
                break;
            default:
                lineCode = "N/A";
                break;
        }

        return lineCode;
    }

    // SOURCE: http://stackoverflow.com/a/2581754
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        return sortByValue(map, true);
    }
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map, boolean shouldReverse) {
        Map<K, V> result = new LinkedHashMap<>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();
        Comparator<Map.Entry<K, V>> comparator = shouldReverse ? Map.Entry.comparingByValue(Collections.reverseOrder()) : Map.Entry.comparingByValue();
        st.sorted(comparator).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    public static Map<String, String[]> getStationCodeMap() {
        return stationCodeMap;
    }
}
