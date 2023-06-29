package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.DestinationCodeMapping;
import com.jamespizzurro.metrorailserver.domain.DestinationCodeMappingPrimaryKey;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationCodeMappingRepository extends CrudRepository<DestinationCodeMapping, DestinationCodeMappingPrimaryKey> {

}
