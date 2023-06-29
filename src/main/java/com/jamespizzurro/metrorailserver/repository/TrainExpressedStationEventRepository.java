package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrainExpressedStationEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainExpressedStationEventRepository extends CrudRepository<TrainExpressedStationEvent, Long> {

}
