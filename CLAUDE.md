# KOREADY Backend

KOREADY API 서버.

## 기술 스택
- Java 21, Spring Boot 4.1.1, Gradle
- Spring Data JPA, Bean Validation, Lombok
- DB: PostgreSQL + PostGIS(공간 질의). 의미 검색(pgvector)은 자유 입력·RAG를 넣을 때 쓰고 지금은 넣지 않는다. 별도 벡터 DB는 두지 않는다
- 인프라: AWS EC2(Spring Boot) + RDS PostgreSQL
- 미정: LLM 제공자. 보류: 임베딩 모델. 임의로 정하지 말고 먼저 확인한다

## 명령
- 로컬 DB(PostgreSQL + PostGIS): `docker compose up -d`. 실행·테스트 전에 먼저 띄운다
- 실행: `./gradlew bootRun` (`local` 프로필. 로그인이 생기기 전에는 헤더 `X-Dev-User-Id`로 사용자를 정한다)
- 테스트: `./gradlew test`
- 장소·통계 적재 (원본 파일이 있는 사람만): 환경변수 `KOREADY_RAW_DIR`에 원본 폴더를 두고 `./gradlew bootRun --args='--spring.profiles.active=loader'`. 끝나면 종료한다

## 패키지
- 루트 패키지: `com.tukorea.gdgoc.koready`
- 국적·카테고리·지역 14개(중심 좌표)는 `meta` 패키지의 `Nationality`·`Category`·`Area`를 쓴다
- 여행을 다루는 API는 `TripService.findMyTrip(userId, tripId)`로 여행을 찾는다 (없거나 다른 사용자의 여행이면 `404 TRIP_NOT_FOUND`)
- 여행 요약(Trip) 응답은 `trip` 패키지의 `TripService.toResponse`로 만든다 (지연 로딩이 있어 트랜잭션 안에서 부른다)
- 장소는 `place.PlaceRepository`로 읽는다. 주소(국문)는 `Place.getAddress()`

## 구현 규칙
- 금액·영업시간·거리·이동시간은 DB 조회나 계산 코드에서 만든다. LLM 응답에서 숫자를 받아 그대로 저장하거나 반환하지 않는다
- 동선(일자 배정·순서·이동시간)은 계산 로직이 정한다. LLM은 조건 파싱과 설명 생성만 맡는다
- 경비 응답은 항목별 범위(최소~최대)와 근거 데이터 출처를 함께 내려준다
- 엔티티·API 필드 이름은 서비스 용어(Trip, TripDay, TripItem, Leg, Place)를 그대로 쓴다
- 장소 추천·검색 쿼리는 `place_classes.excluded = true`인 분류의 장소를 뺀다

## 공통 기반 (`common` 패키지)
API를 만들 때 따로 만들지 말고 이것을 쓴다.
- 오류: `throw new ApiException(ErrorCode.X)`. 응답 `{code, message, fields}`는 `GlobalExceptionHandler`가 만든다. 서비스에서 입력을 검사할 때는 `FieldErrors`로 모아 한 번에 `400 INVALID_REQUEST`를 던진다
- 성공 응답은 감싸지 않는다. 목록은 `@Valid PageQuery`로 받고 `PageResponse`로 준다
- 요청 본문의 정수 필드(인원·ID 등)에 소수(2.7)가 오면 잘라 받지 않고 `400 INVALID_REQUEST`다 (`spring.jackson.deserialization.accept-float-as-int=false`). 자동 구성된 `RestClient`로 받는 외부 API 응답에도 같이 걸리므로, 소수로 올 수 있는 값(ODsay 거리 등)은 `double`·`BigDecimal` 필드로 받는다
- 로그인 사용자: 컨트롤러 파라미터 `@LoginUserId Long userId` (없으면 `401 UNAUTHORIZED`). 인증 필터는 요청 속성 `LoginUserIdArgumentResolver.REQUEST_ATTRIBUTE`에 사용자 ID(Long)를 넣는다
- 요청 언어: 컨트롤러 파라미터 `Language language`. 표시 텍스트는 `language.pick(ko, en)` (영문이 없으면 국문)
- "오늘"·"지금"은 `Clock` 빈(서울)으로 정한다 (`LocalDate.now(clock)`). 기록 시각은 `SeoulTime.of(instant)`로 `+09:00`을 붙여 준다
- 공통 객체: `Code`(코드·표시 이름), `LatLng`, `Money`(금액·통화)
- 엔티티의 `created_at`·`updated_at`은 `@CreationTimestamp`·`@UpdateTimestamp`로 채운다
- 설정값은 `application.properties`의 `koready.*`에 두고 `@ConfigurationProperties` 레코드로 읽는다 (예: `trip.TripProperties`)

## 데이터 적재
- TourAPI: Open API → 배치 적재
- 한국관광 데이터랩 카드 소비 통계: CSV 수동 다운로드 → 배치 적재 (크롤링 금지)
- 교통 운임: 요금 규칙을 따로 적재하지 않는다. ODsay 대중교통 길찾기로 확정한 방문 순서의 연속 구간(Leg)만 조회해 이동시간·운임을 얻는다. 방문 순서를 정하는 데는 쓰지 않는다
- 마스터 데이터는 월 1회 배치, 사용자 일정은 실시간
- 적재 코드는 `loader` 패키지에 있고 `loader` 프로필로 켤 때만 돈다. JdbcTemplate 배치로 자연 키(`content_id` 등)에 upsert하고, 하위 테이블(운영시간·요금·인기도 신호)은 지우고 다시 넣어 다시 돌려도 결과가 같다. 원본·덤프 파일은 저장소에 올리지 않는다
- 운영시간·요금은 원문(`*_text_ko`)을 규칙으로 나누고, 확실히 읽히지 않으면 `UNKNOWN`으로 둔다. 인기도 신호 중 이름 매칭이 필요한 것은 원본 폴더의 매칭 결과 파일(`signals/*.json`)을 읽는다
