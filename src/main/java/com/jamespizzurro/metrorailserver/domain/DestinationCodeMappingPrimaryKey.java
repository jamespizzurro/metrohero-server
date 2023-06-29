package com.jamespizzurro.metrorailserver.domain;

import java.io.Serializable;
import java.util.Objects;

public class DestinationCodeMappingPrimaryKey implements Serializable {

    private String destinationCode;
    private Integer directionNumber;

    public DestinationCodeMappingPrimaryKey() {
    }

    public DestinationCodeMappingPrimaryKey(String destinationCode, Integer directionNumber) {
        this.destinationCode = destinationCode;
        this.directionNumber = directionNumber;
    }

    public DestinationCodeMappingPrimaryKey(DestinationCodeMapping destinationCodeMapping) {
        this.destinationCode = destinationCodeMapping.getDestinationCode();
        this.directionNumber = destinationCodeMapping.getDirectionNumber();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestinationCodeMappingPrimaryKey that = (DestinationCodeMappingPrimaryKey) o;
        return Objects.equals(destinationCode, that.destinationCode) &&
                Objects.equals(directionNumber, that.directionNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationCode, directionNumber);
    }
}
