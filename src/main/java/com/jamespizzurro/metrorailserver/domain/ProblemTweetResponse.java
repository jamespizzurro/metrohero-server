package com.jamespizzurro.metrorailserver.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProblemTweetResponse {

    private String keywords;
    private List<ProblemTweetBean> tweets;

    public ProblemTweetResponse(String keywords, List<ProblemTweetBean> tweets) {
        this.keywords = keywords;
        this.tweets = tweets;
    }

    public ProblemTweetResponse(String keywords, Long[] twitterIds, Long[] userIds, Integer[] dates, String[] texts) {
        this.keywords = keywords;

        List<ProblemTweetBean> beans = new ArrayList<>();
        for (int i = 0; i < twitterIds.length; i++) {
            beans.add(new ProblemTweetBean(twitterIds[i], userIds[i], dates[i], texts[i]));
        }
        this.tweets = beans;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProblemTweetResponse that = (ProblemTweetResponse) o;
        return Objects.equals(keywords, that.keywords) &&
                Objects.equals(tweets, that.tweets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keywords, tweets);
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public List<ProblemTweetBean> getTweets() {
        return tweets;
    }

    public void setTweets(List<ProblemTweetBean> tweets) {
        this.tweets = tweets;
    }
}
