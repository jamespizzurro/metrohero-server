package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrainPredictionAccuracyMeasurement;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TrainPredictionAccuracyMeasurementRepository extends CrudRepository<TrainPredictionAccuracyMeasurement, Long> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM train_prediction_accuracy_measurement WHERE measurement_start_time < (NOW() - INTERVAL '1 month')")
    void removeOld();
}
