package com.ramteja.calls.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CallRepository extends JpaRepository<Call, UUID> {

    Page<Call> findByAgentNameOrderByCreatedAtDesc(String agentName, Pageable pageable);

    /** Per-agent coaching leaderboard: average score and call count over analyzed calls. */
    @Query("""
           select c.agentName as agentName, avg(c.score) as averageScore, count(c) as calls
           from Call c
           where c.status = :status
           group by c.agentName
           order by avg(c.score) desc
           """)
    List<AgentStats> agentLeaderboard(@Param("status") Call.Status status);

    @Query("select c from Call c where c.status = :status and c.attempts < :maxAttempts")
    List<Call> findRetryable(@Param("status") Call.Status status, @Param("maxAttempts") int maxAttempts);

    interface AgentStats {
        String getAgentName();
        Double getAverageScore();
        Long getCalls();
    }
}
