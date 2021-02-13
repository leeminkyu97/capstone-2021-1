package com.kookmin.pm.module.league.service;

import com.kookmin.pm.module.category.domain.Category;
import com.kookmin.pm.module.category.repository.CategoryRepository;
import com.kookmin.pm.module.league.domain.*;
import com.kookmin.pm.module.league.dto.LeagueCreateInfo;
import com.kookmin.pm.module.league.dto.LeagueDetails;
import com.kookmin.pm.module.league.dto.LeagueEditInfo;
import com.kookmin.pm.module.league.dto.LeagueSearchCondition;
import com.kookmin.pm.module.league.repository.LeagueParticipantsRepository;
import com.kookmin.pm.module.league.repository.LeagueRepository;

import com.kookmin.pm.module.member.domain.Member;
import com.kookmin.pm.module.member.dto.MemberDetails;
import com.kookmin.pm.module.member.repository.MemberRepository;
import com.kookmin.pm.module.member.service.LookupType;
import com.kookmin.pm.module.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LeagueService {
    private final LeagueRepository leagueRepository;
    private final LeagueParticipantsRepository leagueParticipantsRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final MemberService memberService;

    public Long openLeague(@NonNull Long usn, @NonNull LeagueCreateInfo leagueCreateInfo) {
        Member host = getMemberEntity(usn);

        League league = buildLeagueEntity(leagueCreateInfo, host);

        //TODO::현재 시간 보다 이전 시간을 시작 시간으로 잡을 경우
        if(league.getStartTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException();

        league = leagueRepository.save(league);

        return league.getId();
    }

    public void editLeague(@NonNull Long usn, @NonNull LeagueEditInfo leagueEditInfo) {
        League league = getLeagueEntity(leagueEditInfo.getId());
        Category category = getCategoryEntity(leagueEditInfo.getCategory());

        //TODO::주최자와 회원이 일치하지 않는 경우
        if(!league.getMember().getId().equals(usn))
            throw new RuntimeException();

        //TODO::현재 시간 보다 이전 시간을 시작 시간으로 잡을 경우
        if(leagueEditInfo.getStartTime().isBefore(LocalDateTime.now()))
            throw new RuntimeException();

        //TODO::현재 대회 참가자보다 최대 참가인원수가 작을 경우

        league.editTitle(leagueEditInfo.getTitle());
        league.editDescription(leagueEditInfo.getDescription());
        league.changeActivityArea(leagueEditInfo.getActivityArea());
        league.changeCategory(category);
        league.changeMaxCount(leagueEditInfo.getMaxCount());
        league.changeStartTime(leagueEditInfo.getStartTime());
    }

    public void deleteLeague(@NonNull Long usn, @NonNull Long leagueId) {
        League league = getLeagueEntity(leagueId);

        //TODO::주최자와 신청 회원이 일치하지 않는 경우
        if(!league.getMember().getId().equals(usn))
            throw new RuntimeException();

        //TODO::다른 대회 참가자들에게 대회가 취소되었음을 알려주는 로직 필요

        leagueRepository.delete(league);
    }

    public Long participateLeague(@NonNull Long usn, @NonNull Long leagueId) {
        Member participant = getMemberEntity(usn);
        League league = getLeagueEntity(leagueId);

        //TODO::이미 참가중이거나 신청을 했을 경우
        if(leagueParticipantsRepository.findByMemberAndLeague(participant,league).isPresent())
            throw new RuntimeException();

        LeagueParticipants request = LeagueParticipants.builder()
                .member(participant)
                .league(league)
                .build();

        request = leagueParticipantsRepository.save(request);

        return request.getId();
    }

    public void approveParticipationRequest(@NonNull Long usn, @NonNull Long requestId) {
        LeagueParticipants request = getLeagueParticipantsEntity(requestId);
        League league = request.getLeague();

        //TODO::회원이 호스트가 아닌 경우
        if(!league.getMember().getId().equals(usn))
            throw new RuntimeException();

        //TODO::이미 진행중이거나 끝난 리그인 경우
        if(!league.getStatus().equals(LeagueStatus.SCHEDULED))
            throw new RuntimeException();

        //TODO::이미 참여중인 경우
        if(!request.getStatus().equals(LeagueParticipantsStatus.PENDING))
            throw new RuntimeException();

        request.approveParticipation();
    }

    public void rejectParticipationRequest(@NonNull Long usn, @NonNull Long requestId) {
        LeagueParticipants request = getLeagueParticipantsEntity(requestId);
        League league = request.getLeague();

        //TODO::회원이 호스트가 아닌 경우
        if(!league.getMember().getId().equals(usn))
            throw new RuntimeException();

        //TODO::이미 진행중이거나 끝난 리그인 경우
        if(!league.getStatus().equals(LeagueStatus.SCHEDULED))
            throw new RuntimeException();

        //TODO::이미 참여중인 경우
        if(!request.getStatus().equals(LeagueParticipantsStatus.PENDING))
            throw new RuntimeException();

        leagueParticipantsRepository.delete(request);
    }

    public void quitParticipationRequest(@NonNull Long participantUsn, @NonNull Long requestId) {
        LeagueParticipants request = getLeagueParticipantsEntity(requestId);
        League league = request.getLeague();

        //TODO::회원이 신청자가 아닌 경우
        if(!request.getMember().getId().equals(participantUsn))
            throw new RuntimeException();

        //TODO::이미 진행중이거나 끝난 리그인 경우
        if(!league.getStatus().equals(LeagueStatus.SCHEDULED))
            throw new RuntimeException();

        //TODO::이미 참여중인 경우
        if(!request.getStatus().equals(LeagueParticipantsStatus.PENDING))
            throw new RuntimeException();

        leagueParticipantsRepository.delete(request);
    }

    public LeagueDetails lookupLeague(@NonNull Long leagueId, @NonNull LeagueLookupType lookupType) {
        League league = getLeagueEntity(leagueId);

        if(lookupType.equals(LeagueLookupType.DEFAULT)) {
            return new LeagueDetails(league);
        } else if(lookupType.equals(LeagueLookupType.WITH_HOST)) {
            LeagueDetails leagueDetails = new LeagueDetails(league);
            MemberDetails memberDetails = new MemberDetails(league.getMember());

            leagueDetails.setHost(memberDetails);

            return leagueDetails;
        } else if(lookupType.equals(LeagueLookupType.WITH_PARTICIPANTS)) {
            LeagueDetails leagueDetails = new LeagueDetails(league);
            MemberDetails memberDetails = new MemberDetails(league.getMember());
            leagueDetails.setHost(memberDetails);

            List<Member> participants = leagueRepository
                    .findMemberInLeague(leagueId, LeagueParticipantsStatus.PARTICIPATING);

            List<MemberDetails> participantDetails = new ArrayList<>();

            for(Member participant : participants) {
                participantDetails.add(memberService.lookUpMemberDetails(participant.getId(), LookupType.WITHIMAGE));
            }

            leagueDetails.setParticipants(participantDetails);

            return leagueDetails;
        }

        throw new RuntimeException();
    }

    public Page<LeagueDetails> searchLeague(@NonNull Pageable pageable, @NonNull LeagueSearchCondition searchCondition) {
        return leagueRepository.searchLeague(pageable, searchCondition);
    }

    //TODO::토너먼트는 시작시 매칭을 생성해주는 시퀀스, 리그도 마찬가지로 미리 생성
    public void startLeague(@NonNull Long usn, @NonNull Long leagueId) {
        League league = getLeagueEntity(leagueId);

        if(!league.getMember().getId().equals(usn))
            throw new RuntimeException();

        if(!league.getStatus().equals(LeagueStatus.SCHEDULED))
            throw new RuntimeException();

        league.startLeague();
    }

    private League buildLeagueEntity(@NonNull LeagueCreateInfo leagueCreateInfo, @NonNull Member host) {
        Category category = getCategoryEntity(leagueCreateInfo.getCategory());

        return League.builder()
                .title(leagueCreateInfo.getTitle())
                .description(leagueCreateInfo.getDescription())
                .activityArea(leagueCreateInfo.getActivityArea())
                .maxCount(leagueCreateInfo.getMaxCount())
                .startTime(leagueCreateInfo.getStartTime())
                .leagueType(LeagueType.valueOf(leagueCreateInfo.getLeagueType()))
                .participantType(ParticipantType.valueOf(leagueCreateInfo.getParticipantType()))
                .member(host)
                .category(category)
                .build();
    }

    private League getLeagueEntity(Long id) {
        return leagueRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    private LeagueParticipants getLeagueParticipantsEntity(Long id) {
        return leagueParticipantsRepository.findById(id).orElseThrow(EntityNotFoundException::new);
    }

    private Category getCategoryEntity(String category) {
        return categoryRepository.findByName(category).orElseThrow(EntityNotFoundException::new);
    }

    private Member getMemberEntity(Long usn) {
        return memberRepository.findById(usn).orElseThrow(EntityNotFoundException::new);
    }
}
