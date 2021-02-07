package com.kookmin.pm.module.mathcing.repository;

import com.kookmin.pm.module.mathcing.domain.Matching;
import com.kookmin.pm.module.mathcing.domain.MatchingParticipant;
import com.kookmin.pm.module.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchingParticipantRepository extends JpaRepository<MatchingParticipant, Long> {
    Optional<MatchingParticipant> findByMemberAndMatching(Member member, Matching matching);
    List<MatchingParticipant> findByMatching(Matching matching);
}
