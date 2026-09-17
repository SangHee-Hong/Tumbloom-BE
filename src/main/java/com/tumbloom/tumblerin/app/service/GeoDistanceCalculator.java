package com.tumbloom.tumblerin.app.service;

import org.springframework.stereotype.Component;

/**
 * 두 좌표 간 거리 계산 책임을 담당하는 협력 객체.
 * 원래 CouponService에 있던 Haversine 거리 계산 로직을 그대로 옮긴 것으로, 동작 변경 없음.
 */
@Component
public class GeoDistanceCalculator {

    private static final int EARTH_RADIUS_KM = 6371;

    // Haversine 거리 계산 (km)
    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
