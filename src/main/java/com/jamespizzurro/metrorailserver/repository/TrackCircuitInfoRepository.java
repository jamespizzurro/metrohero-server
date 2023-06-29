package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrackCircuitInfo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrackCircuitInfoRepository extends CrudRepository<TrackCircuitInfo, Integer> {

}
