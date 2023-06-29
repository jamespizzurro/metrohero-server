package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.RailIncident;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RailIncidentRepository extends CrudRepository<RailIncident, Long> {

}
