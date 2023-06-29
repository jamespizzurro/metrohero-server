package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrainStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TrainStatusRepository extends CrudRepository<TrainStatus, Long> {

    @Query(nativeQuery = true,
            value = "SELECT * " +
                    "FROM train_status " +
                    "WHERE observed_date = ( " +
                    "  SELECT max(observed_date) " +
                    "  FROM train_status " +
                    "  WHERE observed_date <= to_timestamp(CAST(?1 AS float)) " +
                    ")"
    )
    List<TrainStatus> getByObservedDate(long timestamp);

    @Query(nativeQuery = true,
            value = "SELECT * " +
                    "FROM train_status " +
                    "WHERE observed_date = ( " +
                    "  SELECT max(observed_date) " +
                    "  FROM train_status " +
                    "  WHERE observed_date <= to_timestamp(CAST(?1 AS float)) " +
                    ") AND real_train_id = CAST(?2 AS text)"
    )
    List<TrainStatus> getByObservedDateAndRealTrainId(long timestamp, String realTrainId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM train_status WHERE observed_date < (NOW() - INTERVAL '24 months')")
    void removeOld();
}
