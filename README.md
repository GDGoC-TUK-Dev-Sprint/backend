# Koready Backend

[Koready](https://github.com/GDGoC-Koready) 서비스의 백엔드 API 서버입니다.


## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| DB | PostgreSQL |
| Cache | Redis (TourAPI 응답 캐싱) |
| ORM | Spring Data JPA |
| Build | Gradle |

## 담당 기능

- TourAPI·공공데이터포털 연동 및 관광지 데이터 캐싱
- 조건 기반 장소 필터링 API
- 좌표·이동시간·영업시간 기반 동선 설계 API
- 국적별 카드소비 통계 기반 경비 예측 API

## 시작하기

### 요구사항

- Java 21
- PostgreSQL (또는 H2로 개발 모드 실행)

### 실행

```bash
./gradlew bootRun
```

### 테스트

```bash
./gradlew test
```

## 프로젝트 구조

```
src/main/java/com/tukorea/gdgoc/koready/
└── KoreadyApplication.java
src/main/resources/
└── application.properties
```
