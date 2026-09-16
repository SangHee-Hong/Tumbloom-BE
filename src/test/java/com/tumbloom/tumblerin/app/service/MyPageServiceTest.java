package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.Cafe;
import com.tumbloom.tumblerin.app.domain.Favorite;
import com.tumbloom.tumblerin.app.domain.RoleType;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.dto.Userdto.UserFavoriteCafeDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserHomeInfoDTO;
import com.tumbloom.tumblerin.app.dto.Userdto.UserMyPageResponseDTO;
import com.tumbloom.tumblerin.app.repository.CouponRepository;
import com.tumbloom.tumblerin.app.repository.FavoriteRepository;
import com.tumbloom.tumblerin.app.repository.StampRepository;
import com.tumbloom.tumblerin.app.repository.UserRepository;
import com.tumbloom.tumblerin.global.dto.ErrorCode;
import com.tumbloom.tumblerin.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * MyPageService 리팩토링(스탬프 레벨 계산 로직을 별도 클래스로 분리) 전
 * 현재 동작을 고정해두기 위한 회귀(regression) 테스트.
 *
 * 레벨/임계값 계산은 static 유틸리티 메서드이자 getUserInfo/getUserHomeInfo 내부에서도 쓰이므로,
 * 정상 흐름 + 모든 임계값 경계(4/5, 10/11, 20/21, 40/41)를 함께 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private StampRepository stampRepository;
    @Mock
    private FavoriteRepository favoriteRepository;

    private MyPageService myPageService;
    private StampLevel stampLevel;

    @BeforeEach
    void setUp() {
        // StampLevel은 순수 계산 로직이라 mock 대신 실제 구현체를 사용
        stampLevel = new StampLevel();
        myPageService = new MyPageService(userRepository, couponRepository, stampRepository, favoriteRepository, stampLevel);
    }

    private User user(Long id, String nickname) {
        return User.builder()
                .id(id)
                .nickname(nickname)
                .email(id + "@example.com")
                .password("pw")
                .roleType(RoleType.USER)
                .build();
    }

    private Cafe cafe(Long id, String name) {
        return Cafe.builder()
                .id(id)
                .cafeName(name)
                .imageUrl("http://image/" + name)
                .address("서울시 어딘가")
                .detailAddress("1층")
                .businessHours("09:00-21:00")
                .qrLink("qr-" + name)
                .verificationCode("code-" + name)
                .build();
    }

    // ===================== getUserInfo =====================

    @Test
    void getUserInfo_정상조회시_스탬프_쿠폰_즐겨찾기_정보를_모두_집계한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        when(stampRepository.countByUserId(1L)).thenReturn(7);
        when(couponRepository.countByUserId(1L)).thenReturn(2);
        when(couponRepository.countByUserIdAndIsUsedFalse(1L)).thenReturn(1);
        when(favoriteRepository.countByUserId(1L)).thenReturn(3);

        UserMyPageResponseDTO result = myPageService.getUserInfo(1L);

        assertThat(result.getNickname()).isEqualTo("tester");
        assertThat(result.getTumblerCount()).isEqualTo(7);
        assertThat(result.getIssuedCoupons()).isEqualTo(2);
        assertThat(result.getAvailableCoupons()).isEqualTo(1);
        assertThat(result.getFavoriteCafes()).isEqualTo(3);
        assertThat(result.getLevel()).isEqualTo(2); // 7 -> Lv2 (5~10)
        assertThat(result.getStepsLeft()).isEqualTo(4); // 11 - 7
    }

    @Test
    void getUserInfo_존재하지_않는_사용자면_404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.getUserInfo(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===================== getUserHomeInfo =====================

    @Test
    void getUserHomeInfo_정상조회시_웰컴상태와_스탬프상태를_계산한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        when(stampRepository.countByUserId(1L)).thenReturn(10);
        when(couponRepository.countByUserId(1L)).thenReturn(0);

        UserHomeInfoDTO result = myPageService.getUserHomeInfo(1L);

        assertThat(result.getWelcomeStatus().getNickname()).isEqualTo("tester");
        assertThat(result.getWelcomeStatus().getTumblerCount()).isEqualTo("10회");
        assertThat(result.getWelcomeStatus().getSavedWater()).isEqualTo("5.50L");
        assertThat(result.getWelcomeStatus().getSavedTree()).isEqualTo("0.030 그루");

        // availableStampCount = 10 - 0*8 = 10, currentStampCount = 10%8 = 2
        assertThat(result.getStampStatus().getValidStampCnt()).isEqualTo(10);
        assertThat(result.getStampStatus().getCurrentCount()).isEqualTo(2);
        assertThat(result.getStampStatus().isExchangeable()).isTrue(); // 10 >= 8
    }

    @Test
    void getUserHomeInfo_사용가능_스탬프가_8의_배수이면_currentCount는_8이다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        when(stampRepository.countByUserId(1L)).thenReturn(16);
        when(couponRepository.countByUserId(1L)).thenReturn(0);

        UserHomeInfoDTO result = myPageService.getUserHomeInfo(1L);

        // availableStampCount = 16, 16 % 8 == 0 이고 availableStampCount > 0 이므로 8
        assertThat(result.getStampStatus().getCurrentCount()).isEqualTo(8);
        assertThat(result.getStampStatus().isExchangeable()).isTrue();
    }

    @Test
    void getUserHomeInfo_쿠폰발급으로_스탬프가_모두_소진되면_currentCount는_0이고_교환불가() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        when(stampRepository.countByUserId(1L)).thenReturn(8);
        when(couponRepository.countByUserId(1L)).thenReturn(1); // 발급된 쿠폰 1개 = 스탬프 8개 소모

        UserHomeInfoDTO result = myPageService.getUserHomeInfo(1L);

        // availableStampCount = max(8 - 1*8, 0) = 0
        assertThat(result.getStampStatus().getValidStampCnt()).isEqualTo(0);
        assertThat(result.getStampStatus().getCurrentCount()).isEqualTo(0);
        assertThat(result.getStampStatus().isExchangeable()).isFalse();
    }

    @Test
    void getUserHomeInfo_존재하지_않는_사용자면_404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.getUserHomeInfo(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===================== getFavoriteCafes =====================

    @Test
    void getFavoriteCafes_정상조회시_즐겨찾기_카페_목록을_최근순으로_변환한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        Favorite fav1 = Favorite.builder().id(1L).user(user(1L, "tester")).cafe(cafe(10L, "카페A")).build();
        Favorite fav2 = Favorite.builder().id(2L).user(user(1L, "tester")).cafe(cafe(20L, "카페B")).build();
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(fav1, fav2));

        List<UserFavoriteCafeDTO> result = myPageService.getFavoriteCafes(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getCafeName()).isEqualTo("카페A");
        assertThat(result.get(0).getImageUrl()).isEqualTo("http://image/카페A");
        assertThat(result.get(0).getAddress()).isEqualTo("서울시 어딘가");
        assertThat(result.get(0).getBusinessHours()).isEqualTo("09:00-21:00");
        assertThat(result.get(1).getId()).isEqualTo(20L);
    }

    @Test
    void getFavoriteCafes_즐겨찾기가_없으면_빈_리스트를_반환한다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "tester")));
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<UserFavoriteCafeDTO> result = myPageService.getFavoriteCafes(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getFavoriteCafes_존재하지_않는_사용자면_404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageService.getFavoriteCafes(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ===================== getLevel / remainingToNextLevel 임계값 경계 =====================

    @ParameterizedTest(name = "stampCount={0} -> level={1}")
    @CsvSource({
            "0, 1",
            "4, 1",
            "5, 2",
            "10, 2",
            "11, 3",
            "20, 3",
            "21, 4",
            "40, 4",
            "41, 5",
            "1000, 5",
    })
    void getLevel_임계값_경계에서_정확한_레벨을_반환한다(int stampCount, int expectedLevel) {
        assertThat(stampLevel.getLevel(stampCount)).isEqualTo(expectedLevel);
    }

    @ParameterizedTest(name = "stampCount={0} -> remaining={1}")
    @CsvSource({
            "0, 5",
            "4, 1",
            "5, 6",
            "10, 1",
            "11, 10",
            "20, 1",
            "21, 20",
            "40, 1",
            "41, 0",
            "1000, 0",
    })
    void remainingToNextLevel_임계값_경계에서_정확한_잔여치를_반환한다(int stampCount, int expectedRemaining) {
        assertThat(stampLevel.remainingToNextLevel(stampCount)).isEqualTo(expectedRemaining);
    }

    @ParameterizedTest(name = "level={0} -> min={1}, max={2}")
    @CsvSource({
            "1, 0, 4",
            "2, 5, 10",
            "3, 11, 20",
            "4, 21, 40",
            // level 5의 max는 Integer.MAX_VALUE라 별도 테스트로 검증
    })
    void getMinMaxStampsForLevel_레벨별_구간을_반환한다(int level, int expectedMin, int expectedMax) {
        assertThat(stampLevel.getMinStampsForLevel(level)).isEqualTo(expectedMin);
        assertThat(stampLevel.getMaxStampsForLevel(level)).isEqualTo(expectedMax);
    }

    @Test
    void getMaxStampsForLevel_5는_MAX_VALUE이다() {
        assertThat(stampLevel.getMaxStampsForLevel(5)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void getMinMaxStampsForLevel_정의되지_않은_레벨은_0을_반환한다() {
        assertThat(stampLevel.getMinStampsForLevel(0)).isEqualTo(0);
        assertThat(stampLevel.getMaxStampsForLevel(0)).isEqualTo(0);
        assertThat(stampLevel.getMinStampsForLevel(6)).isEqualTo(0);
        assertThat(stampLevel.getMaxStampsForLevel(6)).isEqualTo(0);
    }
}