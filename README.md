# Koready Backend

[Koready](https://github.com/GDGoC-Koready) 서비스의 백엔드 API 서버입니다.

> 전체 서비스 소개는 [Koready README](https://github.com/GDGoC-Koready/Koready)를 참고하세요.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| DB | PostgreSQL + PostGIS (로컬은 Docker) |
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
- Docker Desktop (로컬 DB: PostgreSQL + PostGIS)

### 로컬 DB

장소·통계 데이터는 팀에서 나눠 주는 덤프 파일로 받는다. 원본 파일이나 API 키는 필요 없다.

1. Docker Desktop을 설치하고 켠다
2. 이미지를 받는다: `docker compose pull`
3. 덤프 파일(`koready-YYYYMMDD.sql.gz`)을 `db/init/`에 넣는다. 한 번에 하나만 둔다
4. `docker compose up -d`. 볼륨이 비어 있는 첫 실행 때 PostGIS 확장을 만들고 덤프를 자동으로 복원한다. `docker compose ps`에서 `healthy`가 되면 끝이다

덤프를 넣기 전에 DB를 띄운 적이 있거나 새 덤프로 바꿀 때는 볼륨을 지우고 다시 띄운다. 이전 덤프 파일은 `db/init/`에서 지운다. 로컬에서 만든 사용자·여행 데이터도 함께 지워진다.

```bash
docker compose down -v
docker compose up -d
```

| 접속 | 값 |
|------|-----|
| 호스트·포트 | `localhost:5432` |
| DB 이름·사용자·비밀번호 | `koready` · `koready` · `koready` |

5432 포트를 이미 쓰고 있으면 환경변수 `DB_PORT`(예: `5433`)를 두고 띄운다. compose와 Spring이 같은 변수를 읽는다. 비밀번호는 `DB_PASSWORD`로 바꿀 수 있다.

덤프에 들어 있는 테이블은 장소(`place_classes`, `places`, `place_opening_hours`, `place_fees`, `place_signals`)와 데이터랩 통계(`datalab_*` 4개)다. 사용자·여행 등 나머지 테이블은 `bootRun` 때 JPA가 만든다. `place_classes.excluded`가 `true`인 분류의 장소는 추천·장소 검색에서 뺀다.

### 실행

```bash
docker compose up -d   # 로컬 DB를 먼저 띄운다
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
