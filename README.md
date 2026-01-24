# Tumbloom-BE
멋쟁이사자처럼 중앙해커톤 '텀블룸'팀의 백엔드 레포지토리입니다.

---

# 🌱 Tumblerin

<img width="5760" height="3240" alt="KakaoTalk_Photo_2025-08-25-19-58-33" src="https://github.com/user-attachments/assets/b184760a-764a-4b0b-b512-d6ce3d2cd9b9" />

## 🚀 Project Introduction
> **Tumblerin (텀블러인)**  
환경을 생각하는 텀블러 이용 문화를 확산하기 위해 제작된 서비스입니다.  
사용자는 카페를 방문해 본인의 텀블러를 인증하고, 혜택 및 포인트를 적립할 수 있습니다.

---

## 🛠 Tech Stack

<div align="center">

### Frontend
<!-- Frontend -->
![React](https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-FFD700?style=for-the-badge&logo=javascript&logoColor=black)
![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)
![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)

### Backend
<!-- Backend -->
![Java](https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/SpringBoot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)

### Infra
<!-- Infra -->
![AWS](https://img.shields.io/badge/AWS-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![GoogleCloud](https://img.shields.io/badge/Google%20Cloud-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)

### Tools
<!-- Tools -->
![Figma](https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)

</div>

---

## 📂 Repository Structure
- **Backend**: [`Tumbloom-BE`](https://github.com/Likelion-at-SMWU-13th/Tumbloom-BE.git)  
- **Frontend**: [`Tumbloom-FE`](https://github.com/Likelion-at-SMWU-13th/Tumbloom-FE.git)  
- **Server**: [`https://tumbloom.store/`](https://tumbloom.store)  
- **Client**: [`https://tumbloom-fe.vercel.app`](https://tumbloom-fe.vercel.app)  

---

## 🤝 Contributors
<br>

| 이름     | 개발분야  | 개인 레포                                         | 역할                    |
| -------- | --------- | ------------------------------------------------- | ------------------------- |
| 🦁이현정 | Back-end | [hyhy-j](https://github.com/hyhy-j)  | 내 주변 Cafe  탐색, 필터 조회 및 키워드 검색 / 즐겨찾기 생성 및 제거, CICD (Docker-github action) |
| 🦁서문지 | Back-end | [SEOMUNJI](https://github.com/SEOMUNJI)  | QR 인증, 쿠폰 시스템 구현 (쿠폰 발급 및 관리), CICD (Docker-github action) |
| 🦁홍상희 | Back-end | [SangHee-Hong](https://github.com/SangHee-Hong)  | 유저 인증 시스템, AI 카페 추천, 마이페이지, AWS 배포, CICD (Docker-github action) |

<br/>

---
## 🗂️ 프로젝트 구조

```
📦 
├─ .gitattributes
├─ .gitignore
├─ README.md
├─ build.gradle
├─ gradle
│  └─ wrapper
│     ├─ gradle-wrapper.jar
│     └─ gradle-wrapper.properties
├─ gradlew
├─ gradlew.bat
├─ settings.gradle
└─ src
   ├─ main
   │  ├─ java
   │  │  └─ com
   │  │     └─ tumbloom
   │  │        └─ tumblerin
   │  │           ├─ TumblerinApplication.java
   │  │           ├─ app
   │  │           │  ├─ controller
   │  │           │  │  ├─ AuthController.java
   │  │           │  │  ├─ CafeController.java
   │  │           │  │  ├─ CafeVerificationController.java
   │  │           │  │  ├─ CouponController.java
   │  │           │  │  ├─ FavoriteController.java
   │  │           │  │  └─ MyPageController.java
   │  │           │  ├─ domain
   │  │           │  │  ├─ Cafe.java
   │  │           │  │  ├─ Coupon.java
   │  │           │  │  ├─ CouponManager.java
   │  │           │  │  ├─ Favorite.java
   │  │           │  │  ├─ Menu.java
   │  │           │  │  ├─ Preference
   │  │           │  │  │  ├─ ExtraOption.java
   │  │           │  │  │  ├─ PreferredMenu.java
   │  │           │  │  │  └─ VisitPurpose.java
   │  │           │  │  ├─ RefreshToken.java
   │  │           │  │  ├─ RoleType.java
   │  │           │  │  ├─ Stamp.java
   │  │           │  │  ├─ User.java
   │  │           │  │  └─ UserPreference.java
   │  │           │  ├─ dto
   │  │           │  │  ├─ Authdto
   │  │           │  │  │  ├─ LoginRequestDTO.java
   │  │           │  │  │  ├─ RefreshRequestDTO.java
   │  │           │  │  │  ├─ SignupRequestDTO.java
   │  │           │  │  │  └─ TokenResponseDTO.java
   │  │           │  │  ├─ Cafedto
   │  │           │  │  │  ├─ CafeBatchCreateRequestDTO.java
   │  │           │  │  │  ├─ CafeCreateRequestDTO.java
   │  │           │  │  │  ├─ CafeDetailResponseDTO.java
   │  │           │  │  │  ├─ CafeListResponseDTO.java
   │  │           │  │  │  └─ CafeRecommendDTO.java
   │  │           │  │  ├─ Coupondto
   │  │           │  │  │  ├─ AvailableCafeCouponDto.java
   │  │           │  │  │  ├─ MyCouponDetailResponse.java
   │  │           │  │  │  ├─ MyCouponDto.java
   │  │           │  │  │  └─ MyCouponListResponse.java
   │  │           │  │  ├─ Userdto
   │  │           │  │  │  ├─ UserFavoriteCafeDTO.java
   │  │           │  │  │  ├─ UserHomeInfoDTO.java
   │  │           │  │  │  ├─ UserMyPageResponseDTO.java
   │  │           │  │  │  └─ UserPreferenceDTO.java
   │  │           │  │  └─ Verifydto
   │  │           │  │     ├─ VerificationCodeVerifyRequestDTO.java
   │  │           │  │     └─ VerificationCodeVerifyResponseDTO.java
   │  │           │  ├─ repository
   │  │           │  │  ├─ CafeRepository.java
   │  │           │  │  ├─ CouponManagerRepository.java
   │  │           │  │  ├─ CouponRepository.java
   │  │           │  │  ├─ FavoriteRepository.java
   │  │           │  │  ├─ MenuRepository.java
   │  │           │  │  ├─ RefreshTokenRepository.java
   │  │           │  │  ├─ StampRepository.java
   │  │           │  │  ├─ UserPreferenceRepository.java
   │  │           │  │  └─ UserRepository.java
   │  │           │  ├─ security
   │  │           │  │  ├─ CustomUserDetails.java
   │  │           │  │  └─ CustomUserDetailsService.java
   │  │           │  └─ service
   │  │           │     ├─ CafeRecommendationMappingService.java
   │  │           │     ├─ CafeRecommendationService.java
   │  │           │     ├─ CafeService.java
   │  │           │     ├─ CafeVerificationService.java
   │  │           │     ├─ CouponService.java
   │  │           │     ├─ FavoriteService.java
   │  │           │     ├─ MyPageService.java
   │  │           │     ├─ OpenAIEmbeddingService.java
   │  │           │     ├─ UserPreferenceService.java
   │  │           │     └─ UserService.java
   │  │           └─ global
   │  │              ├─ config
   │  │              │  ├─ SecurityConfig.java
   │  │              │  ├─ SwaggerConfig.java
   │  │              │  └─ WebConfig.java
   │  │              ├─ dto
   │  │              │  ├─ ApiResponseTemplate.java
   │  │              │  ├─ ErrorCode.java
   │  │              │  └─ SuccessCode.java
   │  │              ├─ exception
   │  │              │  ├─ BusinessException.java
   │  │              │  └─ GlobalExceptionHandler.java
   │  │              └─ security
   │  │                 ├─ JwtAuthenticationFilter.java
   │  │                 ├─ JwtTokenProvider.java
   │  │                 └─ SecurityConstants.java
   │  └─ resources
   │     ├─ application.properties
   │     └─ application.yml
   └─ test
      └─ java
         └─ com
            └─ tumbloom
               └─ tumblerin
                  └─ TumblerinApplicationTests.java
```
©generated by [Project Tree Generator](https://woochanleee.github.io/project-tree-generator)
