package com.smu.healyx.user.service;

import com.smu.healyx.user.domain.User;
import com.smu.healyx.user.dto.MyProfileResponse;
import com.smu.healyx.user.dto.UserProfileDto;
import com.smu.healyx.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));

        return UserProfileDto.builder()
                .age(user.getAge() != null ? user.getAge() : 0)
                .gender(user.getGender())
                .insured(user.isHasHealthInsurance())
                .build();
    }

    /** 자동 로그인 및 프로필 화면용 전체 프로필 조회 */
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));

        return MyProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .name(user.getRealName())
                .email(user.getEmail())
                .gender(user.getGender())
                .birthDate(user.getBirthDate())
                .age(user.getAge())
                .insuranceStatus(user.isHasHealthInsurance())
                .preferredLanguage(user.getPreferredLanguage())
                .pushEnabled(user.isPushEnabled())
                .build();
    }
}
