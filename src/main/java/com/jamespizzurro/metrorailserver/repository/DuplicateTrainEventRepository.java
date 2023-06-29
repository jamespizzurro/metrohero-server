package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.DuplicateTrainEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DuplicateTrainEventRepository extends CrudRepository<DuplicateTrainEvent, Long> {

}
