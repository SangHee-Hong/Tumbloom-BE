package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.Cafe;
import com.tumbloom.tumblerin.app.domain.Coupon;
import com.tumbloom.tumblerin.app.domain.CouponManager;
import com.tumbloom.tumblerin.app.domain.RoleType;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.dto.Coupondto.AvailableCafeCouponDto;
import com.tumbloom.tumblerin.app.dto.Coupondto.MyCouponDetailResponse;
import com.tumbloom.tumblerin.app.dto.Coupondto.MyCouponListResponse;
import com.tumbloom.tumblerin.app.repository.CafeRepository;
import com.tumbloom.tumblerin.app.repository.CouponManagerRepository;
import com.tumbloom.tumblerin.app.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * CouponService 리팩토링(거리 계산 책임 분리, 리플렉션 상태 변경 제거) 전
 * 현재 동작을 고정해두기 위한 회귀(regression) 테스트.
 *
 * DB 없이 Repository를 모두 mock 처리하는 순수 단위 테스트이며,
 * 리팩토링 전/후 이 테스트가 수정 없이 그대로 통과해야 "동작이 바뀌지 않았다"는 근거가 된다.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Set<Integer> DISCOUNT_OPTIONS = Set.of(1000, 1100, 1200, 1300, 1400, 1500);
    private static final DateTimeFormatter EXP_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponManagerRepository couponManagerRepository;
    @Mock
    private CafeRepository cafeRepository;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository, couponManagerRepository, cafeRepository);
    }

    private Point point(double lat, double lng) {
        Point p = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat)); // Coordinate(x=lng, y=lat)
        p.setSRID(4326);
        return p;
    }

    private Cafe cafe(Long id, String name, double lat, double lng) {
        return Cafe.builder()
                .id(id)
                .cafeName(name)
                .imageUrl("http://image/" + name)
                .address("서울시 어딘가")
                .detailAddress("1층")
                .businessHours("09:00-21:00")
                .location(point(lat, lng))
                .qrLink("qr-" + name)
                .verificationCode("code-" + name)
                .build();
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .nickname("tester")
                .email("tester@example.com")
                .password("password")
                .roleType(RoleType.USER)
                .build();
    }

    // ===================== issueCoupon =====================

    @Test
    void issueCoupon_정상발급시_재고가_1_감소하고_쿠폰이_생성된다() {
        Cafe cafe = cafe(1L, "카페A", 37.0, 127.0);
        CouponManager cm = CouponManager.builder()
                .id(10L)
                .cafe(cafe)
                .couponQuantity(5)
                .build();
        User loginUser = user(100L);

        when(couponManagerRepository.findByCafeIdForUpdate(1L)).thenReturn(Optional.of(cm));

        MyCouponDetailResponse response = couponService.issueCoupon(1L, loginUser);

        // 재고 1 차감 (현재는 reflection, 리팩토링 후에는 decreaseQuantity())
        assertThat(cm.getCouponQuantity()).isEqualTo(4);

        // 응답 값 검증
        assertThat(response.getCafeName()).isEqualTo("카페A");
        assertThat(response.getImageUrl()).isEqualTo("http://image/카페A");
        assertThat(response.isUsed()).isFalse();
        assertThat(response.getDiscountPrice()).isIn(DISCOUNT_OPTIONS);
        assertThat(response.getContent()).isEqualTo(response.getDiscountPrice() + "원");
        assertThat(response.getExpiredDate())
                .isEqualTo(LocalDate.now().plusMonths(6).format(EXP_FMT));

        // 저장되는 Coupon 엔티티 값 검증
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository, times(1)).save(captor.capture());
        Coupon saved = captor.getValue();
        assertThat(saved.getCouponManager()).isSameAs(cm);
        assertThat(saved.getUser()).isSameAs(loginUser);
        assertThat(saved.getIsUsed()).isFalse();
        assertThat(saved.getDiscountPrice()).isIn(DISCOUNT_OPTIONS);
    }

    @Test
    void issueCoupon_카페의_쿠폰발급정보가_없으면_404() {
        when(couponManagerRepository.findByCafeIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.issueCoupon(999L, user(1L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해당 카페의 쿠폰 발급정보가 없습니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);

        verify(couponRepository, never()).save(any());
    }

    @Test
    void issueCoupon_재고가_0이면_409_소진() {
        Cafe cafe = cafe(1L, "카페A", 37.0, 127.0);
        CouponManager cm = CouponManager.builder().id(10L).cafe(cafe).couponQuantity(0).build();
        when(couponManagerRepository.findByCafeIdForUpdate(1L)).thenReturn(Optional.of(cm));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, user(1L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("해당 카페의 쿠폰이 모두 소진되었습니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);

        verify(couponRepository, never()).save(any());
        assertThat(cm.getCouponQuantity()).isEqualTo(0); // 변경되지 않아야 함
    }

    @Test
    void issueCoupon_재고가_최대치를_초과하면_409_비정상() {
        Cafe cafe = cafe(1L, "카페A", 37.0, 127.0);
        CouponManager cm = CouponManager.builder().id(10L).cafe(cafe).couponQuantity(21).build(); // MAX_COUPON_PER_CAFE(20) 초과
        when(couponManagerRepository.findByCafeIdForUpdate(1L)).thenReturn(Optional.of(cm));

        assertThatThrownBy(() -> couponService.issueCoupon(1L, user(1L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("쿠폰 수량이 비정상적입니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);

        verify(couponRepository, never()).save(any());
        assertThat(cm.getCouponQuantity()).isEqualTo(21); // 변경되지 않아야 함
    }

    // ===================== useMyCoupon =====================

    @Test
    void useMyCoupon_정상사용시_isUsed가_true로_바뀐다() {
        Coupon coupon = Coupon.builder()
                .id(1L)
                .cafeName("카페A")
                .isUsed(false)
                .build();
        when(couponRepository.findByIdAndUser_Id(1L, 100L)).thenReturn(Optional.of(coupon));

        couponService.useMyCoupon(100L, 1L);

        assertThat(coupon.getIsUsed()).isTrue();
    }

    @Test
    void useMyCoupon_쿠폰이_없으면_404() {
        when(couponRepository.findByIdAndUser_Id(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.useMyCoupon(100L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void useMyCoupon_이미_사용된_쿠폰이면_409() {
        Coupon coupon = Coupon.builder()
                .id(1L)
                .cafeName("카페A")
                .isUsed(true)
                .build();
        when(couponRepository.findByIdAndUser_Id(1L, 100L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> couponService.useMyCoupon(100L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이미 사용된 쿠폰입니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(CONFLICT);
    }

    // ===================== listMyCoupons / getMyCoupon =====================

    @Test
    void listMyCoupons_미사용_쿠폰_목록과_개수를_반환한다() {
        Coupon c1 = Coupon.builder().id(1L).cafeName("카페A").isUsed(false).build();
        Coupon c2 = Coupon.builder().id(2L).cafeName("카페B").isUsed(false).build();
        when(couponRepository.findByUser_IdAndIsUsedFalseOrderByIdDesc(100L))
                .thenReturn(List.of(c1, c2));

        MyCouponListResponse response = couponService.listMyCoupons(100L);

        assertThat(response.getUsableCount()).isEqualTo(2);
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems().get(0).getCouponId()).isEqualTo(1L);
        assertThat(response.getItems().get(1).getCouponId()).isEqualTo(2L);
    }

    @Test
    void getMyCoupon_정상조회() {
        Coupon coupon = Coupon.builder()
                .id(1L)
                .cafeName("카페A")
                .isUsed(false)
                .discountPrice(1000) // MyCouponDetailResponse.discountPrice(int)가 null을 언박싱하지 않도록 실제 발급 상태와 동일하게 설정
                .build();
        when(couponRepository.findByIdAndUser_Id(1L, 100L)).thenReturn(Optional.of(coupon));

        MyCouponDetailResponse response = couponService.getMyCoupon(100L, 1L);

        assertThat(response.getCouponId()).isEqualTo(1L);
        assertThat(response.getCafeName()).isEqualTo("카페A");
    }

    @Test
    void getMyCoupon_없으면_404() {
        when(couponRepository.findByIdAndUser_Id(1L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.getMyCoupon(100L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(NOT_FOUND);
    }

    // ===================== listAvailableCafes =====================

    @Test
    void listAvailableCafes_검색어가_없으면_findAllAvailable을_사용한다() {
        when(couponManagerRepository.findAllAvailable()).thenReturn(List.of());

        couponService.listAvailableCafes(null, 37.0, 127.0);
        couponService.listAvailableCafes("   ", 37.0, 127.0);

        verify(couponManagerRepository, times(2)).findAllAvailable();
        verify(couponManagerRepository, never()).searchAvailableByCafeName(anyString());
    }

    @Test
    void listAvailableCafes_검색어가_있으면_searchAvailableByCafeName을_trim해서_사용한다() {
        when(couponManagerRepository.searchAvailableByCafeName("스타벅스")).thenReturn(List.of());

        couponService.listAvailableCafes("  스타벅스  ", 37.0, 127.0);

        verify(couponManagerRepository, times(1)).searchAvailableByCafeName("스타벅스");
        verify(couponManagerRepository, never()).findAllAvailable();
    }

    @Test
    void listAvailableCafes_사용자_위치_기준_거리순으로_정렬된다() {
        double userLat = 37.0, userLng = 127.0;

        // 사용자 위치로부터 위도 오프셋이 커질수록 더 멀어짐 (단순 격자이므로 순서 예측 가능)
        CouponManager far = couponManagerFor(cafe(3L, "먼카페", 37.03, 127.0), 5);
        CouponManager near = couponManagerFor(cafe(1L, "가까운카페", 37.001, 127.0), 5);
        CouponManager mid = couponManagerFor(cafe(2L, "중간카페", 37.01, 127.0), 5);

        when(couponManagerRepository.findAllAvailable()).thenReturn(List.of(far, near, mid));

        List<AvailableCafeCouponDto> result = couponService.listAvailableCafes(null, userLat, userLng);

        assertThat(result).extracting(AvailableCafeCouponDto::getCafeName)
                .containsExactly("가까운카페", "중간카페", "먼카페");
    }

    @Test
    void listAvailableCafes_최대_7개까지만_반환한다() {
        double userLat = 37.0, userLng = 127.0;

        List<CouponManager> managers = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> couponManagerFor(
                        cafe((long) i, "카페" + i, 37.0 + (i + 1) * 0.01, 127.0), 5))
                .toList();

        when(couponManagerRepository.findAllAvailable()).thenReturn(managers);

        List<AvailableCafeCouponDto> result = couponService.listAvailableCafes(null, userLat, userLng);

        assertThat(result).hasSize(7);
        // 정렬 후 앞에서 7개(가장 가까운 카페0~카페6)가 반환되어야 함
        assertThat(result).extracting(AvailableCafeCouponDto::getCafeName)
                .containsExactly("카페0", "카페1", "카페2", "카페3", "카페4", "카페5", "카페6");
    }

    private CouponManager couponManagerFor(Cafe cafe, int quantity) {
        return CouponManager.builder().cafe(cafe).couponQuantity(quantity).build();
    }
}
