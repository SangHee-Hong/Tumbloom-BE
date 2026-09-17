package com.tumbloom.tumblerin.app.service;

import org.springframework.stereotype.Component;

/**
 * 스탬프 개수에 따른 레벨 산정 책임을 담당하는 협력 객체.
 * 원래 MyPageService에 static으로 있던 레벨 계산 로직을 그대로 옮긴 것으로, 동작 변경 없음.
 */
@Component
public class StampLevel {

    public int getLevel(int stampCount) {
        if (stampCount <= 4) return 1; // Lv1
        else if (stampCount <= 10) return 2; // Lv2
        else if (stampCount <= 20) return 3; // Lv3
        else if (stampCount <= 40) return 4; // Lv4
        else return 5; // Lv5
    }

    public int remainingToNextLevel(int stampCount) {
        if (stampCount <= 4) return 5 - stampCount;
        else if (stampCount <= 10) return 11 - stampCount;
        else if (stampCount <= 20) return 21 - stampCount;
        else if (stampCount <= 40) return 41 - stampCount;
        else return 0;
    }

    public int getMinStampsForLevel(int level) {
        return switch (level) {
            case 1 -> 0;
            case 2 -> 5;
            case 3 -> 11;
            case 4 -> 21;
            case 5 -> 41;
            default -> 0;
        };
    }

    public int getMaxStampsForLevel(int level) {
        return switch (level) {
            case 1 -> 4;
            case 2 -> 10;
            case 3 -> 20;
            case 4 -> 40;
            case 5 -> Integer.MAX_VALUE;
            default -> 0;
        };
    }
}
