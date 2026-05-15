package com.snail.snail_race.repository;

import com.snail.snail_race.domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {
    Optional<Result> findByVideo_Id(Long videoId);
}
