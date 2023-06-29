package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.ApiRequest;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ApiRequestRepository extends CrudRepository<ApiRequest, Long> {

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM api_request WHERE date < (now() - INTERVAL '1 month')")
    void removeOld();
}
