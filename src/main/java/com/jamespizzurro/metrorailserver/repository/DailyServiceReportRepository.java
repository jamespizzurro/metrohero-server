package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.DailyServiceReport;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Calendar;

@Repository
public interface DailyServiceReportRepository extends CrudRepository<DailyServiceReport, Calendar> {

}
