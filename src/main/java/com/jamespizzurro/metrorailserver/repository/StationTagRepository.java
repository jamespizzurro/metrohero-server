package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.StationTag;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StationTagRepository extends CrudRepository<StationTag, Long> {

}
