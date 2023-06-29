package com.jamespizzurro.metrorailserver.domain;

import java.util.*;

public class TrackCircuit {

    public static final String INDETERMINATE = "INDETERMINATE";
    public static boolean isNextStationCodeIndeterminate(String nextStationCode) {
        return nextStationCode != null && nextStationCode.equals(TrackCircuit.INDETERMINATE);
    }
    public static boolean isNextStationCodeValid(String nextStationCode) {
        return nextStationCode != null && !isNextStationCodeIndeterminate(nextStationCode);
    }

    private Integer id;
    private Integer trackNumber;
    private String stationCode;
    private Set<String> lineCodes;

    private Set<TrackCircuit> parentNeighbors;
    private Set<TrackCircuit> childNeighbors;
    private Map<TrackCircuit, Set<String>> parentStationCodes;    // ALL of them, not just neighboring stations
    private Map<TrackCircuit, Set<String>> childStationCodes; // ALL of them, not just neighboring stations
    private Set<String> nextParentStationCodes; // nearest station neighbors
    private Set<String> nextChildStationCodes;  // nearest station neighbors

    private int numSecondsEstimatedTrackDelays;

    public TrackCircuit(Integer id, Integer trackNumber, String stationCode, Set<String> lineCodes) {
        this.id = id;
        this.trackNumber = trackNumber;
        this.stationCode = stationCode;
        this.lineCodes = lineCodes;

        this.parentNeighbors = new HashSet<>();
        this.childNeighbors = new HashSet<>();
        this.parentStationCodes = new HashMap<>();
        this.childStationCodes = new HashMap<>();
        this.nextParentStationCodes = new HashSet<>();
        this.nextChildStationCodes = new HashSet<>();

        this.numSecondsEstimatedTrackDelays = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackCircuit that = (TrackCircuit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public Set<String> findParentStationCodes() {
        return findParentStationCodes(new HashSet<>());
    }
    private Set<String> findParentStationCodes(Set<String> stationCodes) {
        if (getStationCode() != null) {
            stationCodes.add(getStationCode());
        }

        for (TrackCircuit parentCircuit : getParentNeighbors()) {
            stationCodes = parentCircuit.findParentStationCodes(stationCodes);
        }

        return stationCodes;
    }
    public Set<String> findChildStationCodes() {
        return findChildStationCodes(new HashSet<>());
    }
    private Set<String> findChildStationCodes(Set<String> stationCodes) {
        if (getStationCode() != null) {
            stationCodes.add(getStationCode());
        }

        for (TrackCircuit childCircuit : getChildNeighbors()) {
            stationCodes = childCircuit.findChildStationCodes(stationCodes);
        }

        return stationCodes;
    }

    public Set<String> findNextParentStationCodes() {
        return findNextParentStationCodes(new HashSet<>());
    }
    private Set<String> findNextParentStationCodes(Set<String> stationCodes) {
        if (getStationCode() != null) {
            stationCodes.add(getStationCode());
            return stationCodes;
        } else if (getParentNeighbors().size() <= 0) {
            return stationCodes;
        }

        for (TrackCircuit parentCircuit : getParentNeighbors()) {
            stationCodes = parentCircuit.findNextParentStationCodes(stationCodes);
        }
        return stationCodes;
    }
    public Set<String> findNextChildStationCodes() {
        return findNextChildStationCodes(new HashSet<>());
    }
    private Set<String> findNextChildStationCodes(Set<String> stationCodes) {
        if (getStationCode() != null) {
            stationCodes.add(getStationCode());
            return stationCodes;
        } else if (getChildNeighbors().size() <= 0) {
            return stationCodes;
        }

        for (TrackCircuit childCircuit : getChildNeighbors()) {
            stationCodes = childCircuit.findNextChildStationCodes(stationCodes);
        }
        return stationCodes;
    }

    public String getNextParentStationCode() {
        if (nextParentStationCodes.size() <= 0) {
            return null;
        } else if (nextParentStationCodes.size() > 1) {
            return TrackCircuit.INDETERMINATE;
        } else {
            return nextParentStationCodes.iterator().next();
        }
    }

    public String getNextChildStationCode() {
        if (nextChildStationCodes.size() <= 0) {
            return null;
        } else if (nextChildStationCodes.size() > 1) {
            return TrackCircuit.INDETERMINATE;
        } else {
            return nextChildStationCodes.iterator().next();
        }
    }

    public boolean isNeighboringStation(String stationCode) {
        return (getNextChildStationCodes().contains(stationCode) || getNextParentStationCodes().contains(stationCode));
    }

    public boolean isOrHasChildOrParentStationCircuit() {
        if (this.stationCode != null) {
            return true;
        }

        for (TrackCircuit childCircuit : this.childNeighbors) {
            if (childCircuit.getStationCode() != null) {
                return true;
            }
        }

        for (TrackCircuit parentCircuit : this.parentNeighbors) {
            if (parentCircuit.getStationCode() != null) {
                return true;
            }
        }

        return false;
    }

    public Integer getId() {
        return id;
    }

    public Integer getTrackNumber() {
        return trackNumber;
    }

    public String getStationCode() {
        return stationCode;
    }

    public Set<String> getLineCodes() {
        return lineCodes;
    }

    public Set<TrackCircuit> getParentNeighbors() {
        return parentNeighbors;
    }

    public Set<TrackCircuit> getChildNeighbors() {
        return childNeighbors;
    }

    public Map<TrackCircuit, Set<String>> getParentStationCodes() {
        return parentStationCodes;
    }

    public Map<TrackCircuit, Set<String>> getChildStationCodes() {
        return childStationCodes;
    }

    public Set<String> getNextParentStationCodes() {
        return nextParentStationCodes;
    }

    public void setNextParentStationCodes(Set<String> nextParentStationCodes) {
        this.nextParentStationCodes = nextParentStationCodes;
    }

    public Set<String> getNextChildStationCodes() {
        return nextChildStationCodes;
    }

    public void setNextChildStationCodes(Set<String> nextChildStationCodes) {
        this.nextChildStationCodes = nextChildStationCodes;
    }

    public int getNumSecondsEstimatedTrackDelays() {
        return numSecondsEstimatedTrackDelays;
    }

    public void setNumSecondsEstimatedTrackDelays(int numSecondsEstimatedTrackDelays) {
        this.numSecondsEstimatedTrackDelays = numSecondsEstimatedTrackDelays;
    }
}
