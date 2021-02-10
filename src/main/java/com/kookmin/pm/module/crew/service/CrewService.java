package com.kookmin.pm.module.crew.service;

import com.kookmin.pm.module.category.domain.Category;
import com.kookmin.pm.module.category.repository.CategoryRepository;
import com.kookmin.pm.module.crew.domain.Crew;
import com.kookmin.pm.module.crew.domain.CrewParticipantStatus;
import com.kookmin.pm.module.crew.domain.CrewParticipants;
import com.kookmin.pm.module.crew.dto.*;
import com.kookmin.pm.module.crew.repository.CrewParticipantsRepository;
import com.kookmin.pm.module.crew.repository.CrewRepository;
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
import org.springframework.util.MultiValueMap;

import javax.persistence.EntityNotFoundException;
import javax.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class CrewService {
    private final CrewRepository crewRepository;
    private final CrewParticipantsRepository crewParticipantsRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final MemberService memberService;

    //TODO::크루명이 유일할 필요가 있는지
    public Long establishCrew(@NonNull String uid, @NonNull CrewCreateInfo crewCreateInfo) {
        Member member = getMemberEntityByUid(uid);
        Category category = getCategoryEntityByName(crewCreateInfo.getCategory());

        Crew crew = buildCrewEntity(crewCreateInfo, member, category);

        crew = crewRepository.save(crew);

        return crew.getId();
    }

    //TODO::활동지역은 여러개 인가
    public void editCrewInfo(@NonNull String uid, @NonNull CrewEditInfo crewEditInfo) {
        Member member = getMemberEntityByUid(uid);
        Crew crew = getCrewEntity(crewEditInfo.getId());

        //TODO::이메일이 서로 다른 경우
        if(!crew.getMember().getUid().equals(uid))
            throw new RuntimeException();

        crew.editName(crewEditInfo.getName());
        crew.editDescription(crewEditInfo.getDescription());
    }

    public CrewDetails lookupCrew(@NonNull Long crewId, @NonNull CrewLookupType type) {
        CrewDetails crewDetails = null;
        Crew crew = getCrewEntity(crewId);

        if(type.equals(CrewLookupType.DEFAULT)) {
            crewDetails = new CrewDetails(crew);
        } else if(type.equals(CrewLookupType.WITH_HOST)) {
            crewDetails = new CrewDetails(crew);
            MemberDetails host = memberService.lookUpMemberDetails(crew.getMember().getUid(),
                    LookupType.WITHALLINFOS);
            crewDetails.setHost(host);
        } else if(type.equals(CrewLookupType.WITH_PARTICIPANTS)){
            crewDetails = new CrewDetails(crew);
            MemberDetails host = memberService.lookUpMemberDetails(crew.getMember().getUid(),
                    LookupType.WITHALLINFOS);
            crewDetails.setHost(host);

            List<Member> participants = crewRepository.findMemberInCrewParticipants(crew.getId());
            List<MemberDetails> participantList = new ArrayList<>();

            for(Member participant : participants) {
                participantList.add(memberService.lookUpMemberDetails(participant.getUid(),
                        LookupType.WITHALLINFOS));
            }

            crewDetails.setParticipants(participantList);
        }

        return crewDetails;
    }

    public Page<CrewDetails> searchCrew(@NonNull Pageable pageable, @NonNull CrewSearchCondition searchCondition) {
        return crewRepository.searchCrew(pageable, searchCondition);
    }

    public Map<String, Object> findCrewParticipateRequest(@NonNull String uid) {
        Map<String, Object> request = new HashMap<>();

        Member member = getMemberEntityByUid(uid);
        List<Crew> crewList = crewRepository.findByMember(member);
        List<String> crewNameList = new ArrayList<>();

        for(Crew crew : crewList)
            crewNameList.add(crew.getName());

        request.put("crew", crewNameList);

        int index = 0;

        for(Crew crew : crewList) {
            List<CrewParticipants> participantsList = crewParticipantsRepository
                    .findByCrewAndStatus(crew, CrewParticipantStatus.PENDING);

            List<CrewParticipantsDetails> participantDetailsList = new ArrayList<>();

            for(CrewParticipants participants : participantsList) {
                participantDetailsList.add(new CrewParticipantsDetails(participants));
            }

            request.put(String.valueOf(index), participantDetailsList);
            index++;
        }

        return request;
    }

    public List<CrewParticipantsDetails> findMyParticiPateRequest(@NonNull String uid) {
        Member member = getMemberEntityByUid(uid);

        List<CrewParticipants> participantsList = crewParticipantsRepository
                .findByMemberAndStatus(member, CrewParticipantStatus.PENDING);

        List<CrewParticipantsDetails> participantsDetailsList = new ArrayList<>();

        for(CrewParticipants participants : participantsList) {
                participantsDetailsList.add(new CrewParticipantsDetails(participants));
        }

        return participantsDetailsList;
    }

    public CrewParticipantsDetails lookupParticipateRequest(@NonNull Long requestId) {
        CrewParticipants participants = getCrewParticipantsEntity(requestId);

        return new CrewParticipantsDetails(participants);
    }

    public void participateCrew(@NonNull String uid, @NonNull Long crewId) {
        Member member = getMemberEntityByUid(uid);
        Crew crew = getCrewEntity(crewId);

        //TODO::이미 참여하거나 신청한 회원인 경우 익셉션 정의 필요
        if(crewParticipantsRepository.findByMemberAndCrew(member, crew).orElse(null) != null)
            throw new RuntimeException();

        //TODO::신청한 사람이 호스트인경우
        if(crew.getMember().getUid().equals(uid))
            throw new RuntimeException();

        //TODO::최대인원을 초과했을 경우
        if(crewParticipantsRepository.countCrewParticipantsByCrewAndStatus(crew,
                CrewParticipantStatus.PARTICIPATING)+1 > crew.getMaxCount()-1)
            throw new RuntimeException();

        CrewParticipants crewParticipants = CrewParticipants.builder()
                .member(member)
                .crew(crew)
                .build();

        crewParticipantsRepository.save(crewParticipants);
    }

    public void approveParticipationRequest(@NonNull String uid, @NonNull Long requestId) {
        CrewParticipants participants = getCrewParticipantsEntity(requestId);
        Crew crew =participants.getCrew();

        //TODO::이미 참여한 회원인 경우 익셉션 정의 필요
        if(participants.getStatus().equals(CrewParticipantStatus.PARTICIPATING))
             throw new RuntimeException();

        //TODO::참가요청을 승인하는 유저가 해당 참가 요청 크루의 호스트가 아닌 경우
        if(!crew.getMember().equals(uid))
            throw new RuntimeException();

        //TODO::최대 인원수를 초과한 경우
        if(crewParticipantsRepository
                .countCrewParticipantsByCrewAndStatus(crew,CrewParticipantStatus.PARTICIPATING) + 1L >= crew.getMaxCount())
            throw new RuntimeException();

        participants.approveParticipation();
    }

    public void rejectParticipationRequest(@NonNull String uid, @NonNull Long requestId) {
        Member host = getMemberEntityByUid(uid);

        CrewParticipants participants = getCrewParticipantsEntity(requestId);

        //TODO::이미 참여한 회원인 경우 익셉션 정의 필요
        if(participants.getStatus().equals(CrewParticipantStatus.PARTICIPATING))
            throw new RuntimeException();

        //TODO::참가요청을 거절하는 유저가 해당 참가 요청 크루의 호스트가 아닌 경우
        if(!participants.getCrew().getMember().equals(host))
            throw new RuntimeException();

        crewParticipantsRepository.delete(participants);
    }

    public void cancelParticipation(@NonNull String uid, @NonNull Long crewId) {
        Member participant = getMemberEntityByUid(uid);
        Crew crew = getCrewEntity(crewId);

        CrewParticipants participants = crewParticipantsRepository.findByMemberAndCrew(participant, crew)
                .orElseThrow(EntityNotFoundException::new);

        //TODO::참가자가 현재 크루에 참가 상태인 경우 다른 크루원들에게 해당 회원이 탈퇴하였음을 알려주는 로직 필요

        crewParticipantsRepository.delete(participants);
    }

    public void deportParticipant(@NonNull String uid,
                                  @NonNull Long crewId,
                                  @NonNull Long participationId) {
        Member host = getMemberEntityByUid(uid);
        Crew crew = getCrewEntity(crewId);
        CrewParticipants participants = getCrewParticipantsEntity(participationId);

        //TODO::회원이 해당 크루의 크루장이 아닌 경우
        if(!crew.getMember().equals(host))
            throw new RuntimeException();

        //TODO::퇴출시키려는 회원이 해당 크루의 참가자가 아닌 경우
        if(!participants.getCrew().equals(crew) || participants.getStatus().equals(CrewParticipantStatus.PENDING))
            throw new RuntimeException();

        //TODO::크루 참가자가 크루장에 의해 퇴출되었음을 알려주는 로직 필요

        crewParticipantsRepository.delete(participants);
    }

    public void removeCrew(@NonNull String uid, @NonNull Long crewId) {
        Member host = getMemberEntityByUid(uid);
        Crew crew = getCrewEntity(crewId);

        if(!crew.getMember().equals(host))
            throw new RuntimeException();

        //TODO::기존 크루 참가자들에게 크루가 삭제되었음을 알려줘야하나?

        crewParticipantsRepository.deleteAllByCrew(crew);
        crewRepository.delete(crew);
    }

    private Crew getCrewEntity(Long id) {
        return crewRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }

    private CrewParticipants getCrewParticipantsEntity(Long id) {
        return crewParticipantsRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }

    private Member getMemberEntityByUid(String uid) {
        return memberRepository.findByUid(uid)
                .orElseThrow(EntityNotFoundException::new);
    }

    private Category getCategoryEntityByName(String categoryName) {
        return categoryRepository.findByName(categoryName)
                .orElseThrow(EntityNotFoundException::new);
    }

    private Crew buildCrewEntity(CrewCreateInfo crewCreateInfo, Member member, Category category) {
        return Crew.builder()
                .name(crewCreateInfo.getName())
                .description(crewCreateInfo.getDescription())
                .activityArea(crewCreateInfo.getActivityArea())
                .maxCount(crewCreateInfo.getMaxCount())
                .member(member)
                .category(category)
                .build();
    }
}
