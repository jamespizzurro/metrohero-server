package com.jamespizzurro.metrorailserver.repository;

import com.jamespizzurro.metrorailserver.domain.TrainProblem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Calendar;
import java.util.List;

@Repository
public interface TrainProblemRepository extends CrudRepository<TrainProblem, Long> {

    List<TrainProblem> findAllByDateGreaterThanEqualOrderByDateDesc(Calendar cutoff);

    @Query(nativeQuery = true, value =
            "SELECT " +
            "  tweet.train_id AS train_id, " +
            "  array_to_string(array(SELECT DISTINCT unnest(array_accum(tweet.keywords)) ORDER BY 1), ', ') AS keywords, " +
            "  array_agg(tweet.twitter_id ORDER BY tweet.date DESC) AS twitter_ids, " +
            "  array_agg(tweet.user_id ORDER BY tweet.date DESC) AS user_ids, " +
            "  array_agg(cast(extract(EPOCH FROM tweet.date) AS int4) ORDER BY tweet.date DESC) AS dates, " +
            "  array_agg(tweet.text ORDER BY tweet.date DESC) AS texts " +
            "FROM ( " +
            "  SELECT DISTINCT ON (train_id, twitter_id) " +
            "    tweet.train_id, " +
            "    tweet.keywords, " +
            "    tweet.twitter_id, " +
            "    tweet.user_id, " +
            "    tweet.date, " +
            "    tweet.text " +
            "  FROM train_problem_tweet tweet " +
            "  WHERE tweet.date >= (now() AT TIME ZONE 'UTC') - INTERVAL '30 minutes' " +
            "  ORDER BY train_id ASC, tweet.twitter_id DESC " +
            ") AS tweet " +
            "GROUP BY train_id"
    )
    List<Object[]> getRecent();

    boolean existsByTwitterId(Long twitterId);
}
