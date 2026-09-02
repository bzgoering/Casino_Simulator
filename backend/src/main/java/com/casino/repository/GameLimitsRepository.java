package com.casino.repository;

import com.casino.domain.GameLimits;
import com.casino.domain.GameType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameLimitsRepository extends JpaRepository<GameLimits, GameType> {
}
