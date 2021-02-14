package com.kookmin.pm.module.matchup.repository;

import com.kookmin.pm.module.matchup.domain.MatchUp;
import com.kookmin.pm.module.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchUpRepository extends JpaRepository<MatchUp, Long> {

}
