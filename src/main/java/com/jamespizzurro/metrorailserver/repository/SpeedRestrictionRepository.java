package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.SpeedRestriction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeedRestrictionRepository extends CrudRepository<SpeedRestriction, Long> {

}
