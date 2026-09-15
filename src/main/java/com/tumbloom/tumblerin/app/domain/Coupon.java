package com.tumbloom.tumblerin.app.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "couponmanager_id", nullable = false)
    private CouponManager couponManager;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String cafeName;

    @Column
    private String expiredDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column
    private String content;

    @Column
    private Integer discountPrice;

    private String imageUrl;

    // 쿠폰 사용 처리 (기존 CouponService의 리플렉션 필드 조작을 대체)
    public void markAsUsed() {
        this.isUsed = true;
    }
}
