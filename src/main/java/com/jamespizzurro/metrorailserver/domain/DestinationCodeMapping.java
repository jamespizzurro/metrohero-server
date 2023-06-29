package com.jamespizzurro.metrorailserver.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "destination_code_mapping")
@IdClass(DestinationCodeMappingPrimaryKey.class)
public class DestinationCodeMapping {

    @Id
    private String destinationCode;

    @Id
    private Integer directionNumber;

    private String lineCode;

    private String destinationStationCode;

    public DestinationCodeMapping() {}

    public DestinationCodeMapping(String destinationCode, Integer directionNumber, String lineCode, String destinationStationCode) {
        this.destinationCode = destinationCode;
        this.directionNumber = directionNumber;
        this.lineCode = lineCode;
        this.destinationStationCode = destinationStationCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DestinationCodeMapping that = (DestinationCodeMapping) o;
        return Objects.equals(destinationCode, that.destinationCode) &&
                Objects.equals(directionNumber, that.directionNumber) &&
                Objects.equals(lineCode, that.lineCode) &&
                Objects.equals(destinationStationCode, that.destinationStationCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationCode, directionNumber, lineCode, destinationStationCode);
    }

    public String getDestinationCode() {
        return destinationCode;
    }

    public void setDestinationCode(String destinationCode) {
        this.destinationCode = destinationCode;
    }

    public Integer getDirectionNumber() {
        return directionNumber;
    }

    public void setDirectionNumber(Integer directionNumber) {
        this.directionNumber = directionNumber;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getDestinationStationCode() {
        return destinationStationCode;
    }

    public void setDestinationStationCode(String destinationStationCode) {
        this.destinationStationCode = destinationStationCode;
    }
}
