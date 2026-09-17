package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.Cafe;
import com.tumbloom.tumblerin.app.domain.Coupon;
import com.tumbloom.tumblerin.app.domain.CouponManager;
import com.tumbloom.tumblerin.app.domain.User;
import com.tumbloom.tumblerin.app.dto.Coupondto.AvailableCafeCouponDto;
import com.tumbloom.tumblerin.app.dto.Coupondto.MyCouponDetailResponse;
import com.tumbloom.tumblerin.app.dto.Coupondto.MyCouponDto;
import com.tumbloom.tumblerin.app.dto.Coupondto.MyCouponListResponse;
import com.tumbloom.tumblerin.app.repository.CafeRepository;
import com.tumbloom.tumblerin.app.repository.CouponManagerRepository;
import com.tumbloom.tumblerin.app.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponManagerRepository couponManagerRepository;
    private final CafeRepository cafeRepository;
    private final GeoDistanceCalculator geoDistanceCalculator;

    private static final int MAX_COUPON_PER_CAFE = 20;
    private static final DateTimeFormatter EXP_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final int[] DISCOUNT_OPTIONS = {1000, 1100, 1200, 1300, 1400, 1500};
    private static final Random random = new Random();

    @Transactional(readOnly = true)
    public List<AvailableCafeCouponDto> listAvailableCafes(String cafeName, double userLat, double userLng) {
        var managers = (cafeName == null || cafeName.isBlank())
                ? couponManagerRepository.findAllAvailable()
                : couponManagerRepository.searchAvailableByCafeName(cafeName.trim());

        // 거리순 정렬 후 Top 7만 반환
        return managers.stream()
                .map(AvailableCafeCouponDto::from)
                .sorted((a, b) -> Double.compare(
                        geoDistanceCalculator.distanceKm(userLat, userLng, a.getLatitude(), a.getLongitude()),
                        geoDistanceCalculator.distanceKm(userLat, userLng, b.getLatitude(), b.getLongitude())
                ))
                .limit(7)
                .toList();
    }

    /**
     * 쿠폰 발급: /api/cafes/{cafeId}/coupons
     * - 남은 수량(0 초과) 확인 후 1 감소
     * - 쿠폰 생성 (할인금액: 1000~1500 중 랜덤, 100단위)
     */
    @Transactional
    public MyCouponDetailResponse issueCoupon(Long cafeId, User loginUser) {
        CouponManager cm = couponManagerRepository.findByCafeIdForUpdate(cafeId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "해당 카페의 쿠폰 발급정보가 없습니다."));

        if (cm.getCouponQuantity() <= 0) {
            throw new ResponseStatusException(CONFLICT, "해당 카페의 쿠폰이 모두 소진되었습니다.");
        }
        if (cm.getCouponQuantity() > MAX_COUPON_PER_CAFE) {
            throw new ResponseStatusException(CONFLICT, "쿠폰 수량이 비정상적입니다.");
        }

        Cafe cafe = cm.getCafe();

        cm.decreaseQuantity();

        int discount = DISCOUNT_OPTIONS[random.nextInt(DISCOUNT_OPTIONS.length)];

        Coupon coupon = Coupon.builder()
                .couponManager(cm)
                .user(loginUser)
                .cafeName(cafe.getCafeName())
                .expiredDate(LocalDate.now().plusMonths(6).format(EXP_FMT))
                .isUsed(false)
                .content(discount + "원")
                .discountPrice(discount)
                .imageUrl(cafe.getImageUrl())
                .build();
        couponRepository.save(coupon);

        return MyCouponDetailResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public MyCouponListResponse listMyCoupons(Long userId) {
        List<MyCouponDto> items = couponRepository.findByUser_IdAndIsUsedFalseOrderByIdDesc(userId)
                .stream().map(MyCouponDto::from).toList();
        return MyCouponListResponse.builder()
                .usableCount(items.size())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public MyCouponDetailResponse getMyCoupon(Long userId, Long couponId) {
        Coupon c = couponRepository.findByIdAndUser_Id(couponId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return MyCouponDetailResponse.from(c);
    }

    @Transactional
    public void useMyCoupon(Long userId, Long couponId) {
        Coupon c = couponRepository.findByIdAndUser_Id(couponId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(c.getIsUsed())) {
            throw new ResponseStatusException(CONFLICT, "이미 사용된 쿠폰입니다.");
        }
        c.markAsUsed();
    }
}
