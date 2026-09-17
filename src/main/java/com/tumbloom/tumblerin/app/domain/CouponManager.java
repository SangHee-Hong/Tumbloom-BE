package com.tumbloom.tumblerin.app.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cafe_id", nullable = false)
    private Cafe cafe;

    @Column(nullable = false)
    private int couponQuantity;

    // 쿠폰 발급 시 재고 1 차감 (기존 CouponService의 리플렉션 필드 조작을 대체)
    public void decreaseQuantity() {
        this.couponQuantity--;
    }
}
