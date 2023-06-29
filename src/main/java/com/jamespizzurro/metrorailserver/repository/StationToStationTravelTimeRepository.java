package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.StationToStationTravelTime;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StationToStationTravelTimeRepository extends CrudRepository<StationToStationTravelTime, String> {

}
