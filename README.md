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

`bootRun`은 `local` 프로필로 돈다. 로그인이 생기기 전에는 로그인이 필요한 API에 헤더 `X-Dev-User-Id: {사용자 ID}`를 붙여 부른다 (`local`·`test` 프로필에서만 받는다). 사용자는 `bootRun`을 한 번 띄워 테이블이 생긴 뒤 SQL로 만든다.

```bash
docker compose exec db psql -U koready -d koready -c "INSERT INTO users (name, created_at, updated_at) VALUES ('시험 사용자', now(), now()) RETURNING id;"
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



## 오류 수정본
로그아웃 API 추가 및 수정 

## 작업 내용
- POST /api/auth/logout 추가
  - body: { "refreshToken": "..." }
  - 성공 시 204 No Content, 해당 기기의 리프레시 토큰을 DB에서 삭제
  - 이미 삭제됐거나 없는 토큰이어도 204 (여러 번 호출해도 같은 결과)
  - 액세스 토큰이 만료돼도 로그아웃할 수 있게 Authorization 헤더는 필요 없음
  - refreshToken이 비어 있으면 400 INVALID_REQUEST
- 로그아웃 통합 테스트(LogoutTest) 추가

## 앱(프론트) 참고
- 로그아웃 응답이 성공이든 실패든 로컬 토큰을 지우고 로그인 화면으로 이동
- 로그아웃 요청은 401 재발급 인터셉터에서 제외

## 확인 필요
- [ ] ./gradlew test 통과 확인 (로컬 Docker DB 필요)

## 이번 PR에서 안 한 것 (회의 안건)
- 인증 오류를 ErrorCode로 통일 (LOGIN_FAILED, INVALID_REFRESH_TOKEN, LOGIN_ID_TAKEN)
- 아이디 규칙의 하이픈 허용 여부


