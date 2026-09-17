package com.tumbloom.tumblerin.app.controller;
import com.tumbloom.tumblerin.app.dto.Cafedto.CafeRecommendDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserFavoriteCafeDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserHomeInfoDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserPreferenceDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserMyPageResponseDTO;
import com.tumbloom.tumblerin.app.security.CustomUserDetails;
import com.tumbloom.tumblerin.app.service.CafeRecommendationService;
import com.tumbloom.tumblerin.app.service.MyPageService;
import com.tumbloom.tumblerin.app.service.StampLevel;
import com.tumbloom.tumblerin.app.service.UserPreferenceService;
import com.tumbloom.tumblerin.global.dto.ApiResponseTemplate;
import com.tumbloom.tumblerin.global.dto.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyPageController {

    private final UserPreferenceService userPreferenceService;
    private final CafeRecommendationService cafeRecommendationService;
    private final MyPageService myPageService;
    private final StampLevel stampLevel;

    @Operation(summary = "마이페이지 내 정보 조회",
            description = """
                    사용자 정보, 레벨, 쿠폰 수, 즐겨찾기 수, 상위 선호도 3개를 반환합니다.
                    - remainingSteps: 다음 단계까지 남은 횟수
                    - levelProgress: 0~1 범위의 레벨 바 수치
                    """)
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponseTemplate<UserMyPageResponseDTO>> getUserInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UserMyPageResponseDTO userinfo = myPageService.getUserInfo(userDetails.getUser().getId());
        UserPreferenceDTO preference = userPreferenceService.getPreference(userDetails.getUser().getId());

        List<String> combined = new ArrayList<>();
        combined.addAll(preference.getPreferredMenus());
        combined.addAll(preference.getVisitPurposes());
        combined.addAll(preference.getExtraOptions());

        List<String> topPreferences = combined.stream()
                .limit(3)
                .collect(Collectors.toList());

        userinfo.setTopPreferences(topPreferences);

        int min = stampLevel.getMinStampsForLevel(userinfo.getLevel());
        int max = stampLevel.getMaxStampsForLevel(userinfo.getLevel());
        double progress = (double)(userinfo.getTumblerCount() - min) / (max - min);
        progress = Math.min(progress, 1.0);
        progress = Math.round(progress * 100.0) / 100.0;
        userinfo.setLevelProgress(progress);

        return ApiResponseTemplate.success(SuccessCode.RESOURCE_RETRIEVED, userinfo);
    }

    @Operation(summary = "마이 페이지 선호도 저장/업데이트",
            description = """
                    사용자의 선호도를 저장하거나 업데이트합니다.
                    가능한 값:
                    - visitPurposes: "EMOTIONAL_ATMOSPHERE", "STUDY_WORKSPACE", "CHAT_MEETING", "HOT_PLACE", "EVENT"
                    - preferredMenus: "SPECIALTY", "DESSERT", "DECAF", "SEASON_MENU", "BRUNCH"
                    - extraOptions: "FRANCHISE", "PET_FRIENDLY", "OUTDOOR_TERRACE", "ECO_LOCAL", "UNIQUE_THEME"
                    """)
    @ApiResponse(responseCode = "200", description = "성공")
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponseTemplate<Void>> savePreference(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UserPreferenceDTO dto) {

        userPreferenceService.saveOrUpdate(userDetails.getUser().getId(), dto);
        return ApiResponseTemplate.success(SuccessCode.RESOURCE_UPDATED, null);
    }

    @Operation(summary = "(참고)선호도 조회", description = "사용자의 선호도를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponseTemplate<UserPreferenceDTO>> getPreference(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UserPreferenceDTO preference = userPreferenceService.getPreference(userDetails.getUser().getId());
        return ApiResponseTemplate.success(SuccessCode.RESOURCE_RETRIEVED, preference);
    }

    @Operation(summary = "홈 화면 AI 카페 추천 목록 조회", description = "사용자 취향 기반 AI 카페 추천 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/cafe-recommendations")
    public ResponseEntity<ApiResponseTemplate<List<CafeRecommendDTO>>> getCafeRecommendations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<CafeRecommendDTO> recommendations = cafeRecommendationService.recommendCafesForUser(userDetails.getUser().getId());
        return ApiResponseTemplate.success(SuccessCode.RESOURCE_RETRIEVED, recommendations);
    }

    @Operation(summary = "홈 화면 유저 정보 조회",
            description = "히어로 섹션과 도장판 정보를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/home")
    public ResponseEntity<ApiResponseTemplate<UserHomeInfoDTO>> getStamps(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UserHomeInfoDTO homeInfo = myPageService.getUserHomeInfo(userDetails.getUser().getId());
        return ApiResponseTemplate.success(SuccessCode.RESOURCE_RETRIEVED, homeInfo);
    }

    @Operation(summary = "마이페이지 즐겨찾기 카페 목록 조회", description = "사용자가 즐겨찾기한 카페 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/favorites")
    public ResponseEntity<ApiResponseTemplate<List<UserFavoriteCafeDTO>>> getFavoriteCafes(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<UserFavoriteCafeDTO> favoriteCafes = myPageService.getFavoriteCafes(userDetails.getUser().getId());
        return ApiResponseTemplate.success(SuccessCode.RESOURCE_RETRIEVED, favoriteCafes);
    }
}
