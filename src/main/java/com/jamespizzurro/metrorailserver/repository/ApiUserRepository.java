package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.ApiUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiUserRepository extends CrudRepository<ApiUser, String> {

}
