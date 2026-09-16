package com.tumbloom.tumblerin.app.service;

import com.tumbloom.tumblerin.app.domain.RefreshToken;
import com.tumbloom.tumblerin.app.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

/**
 * RefreshToken 영속성 관리 책임을 담당하는 협력 객체.
 * 원래 UserService에 있던 RefreshToken 저장/조회/삭제/일치 검증 로직을 그대로 옮긴 것으로, 동작 변경 없음.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    public void issue(String email, String refreshToken) {
        RefreshToken tokenEntity = RefreshToken.builder()
                .email(email)
                .refreshToken(refreshToken)
                .expiryDate(new Date())
                .build();

        refreshTokenRepository.save(tokenEntity);
    }

    public Optional<RefreshToken> findByEmail(String email) {
        return refreshTokenRepository.findById(email);
    }

    public boolean matches(RefreshToken storedToken, String refreshToken) {
        return storedToken.getRefreshToken().equals(refreshToken);
    }

    public void delete(RefreshToken storedToken) {
        refreshTokenRepository.delete(storedToken);
    }
}
