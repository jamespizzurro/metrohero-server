package com.jamespizzurro.metrorailserver.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardHistoryResponse {

    private Map<String, DashboardLineHistory> dashboardLineHistories;
    private String firstTimestamp;

    public DashboardHistoryResponse(List<Object[]> dashboardLineHistoriesData, String firstTimestamp) {
        this.dashboardLineHistories = new HashMap<>();
        for (Object[] dashboardLineHistoryData : dashboardLineHistoriesData) {
            String lineCode = String.valueOf(dashboardLineHistoryData[0]);
            this.dashboardLineHistories.put(lineCode, new DashboardLineHistory(dashboardLineHistoryData));
        }

        this.firstTimestamp = firstTimestamp;
    }

    public Map<String, DashboardLineHistory> getDashboardLineHistories() {
        return dashboardLineHistories;
    }

    public void setDashboardLineHistories(Map<String, DashboardLineHistory> dashboardLineHistories) {
        this.dashboardLineHistories = dashboardLineHistories;
    }

    public String getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(String firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }
}
