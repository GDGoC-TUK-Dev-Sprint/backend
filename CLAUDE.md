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

## 패키지 구조
루트 패키지 `com.tukorea.gdgoc.koready`. 도메인별로 나누고, 도메인 안은 계층으로 나눈다 (DDD-lite).

```text
domain/<도메인>/       trip · place · candidate · meta · user · expense · exchange · datalab · favorite
    controller/       @RestController. 요청 받기·응답 상태만, 로직은 서비스에
    service/          @Service. 트랜잭션과 흐름 (조회 → 엔티티 메서드 → 저장·외부 API 호출)
    repository/       JpaRepository, JdbcTemplate 쿼리 (조회 행 레코드는 그 클래스 안에 두고 그 도메인 서비스만 쓴다)
    entity/           JPA 엔티티와 그 도메인의 enum·값 객체 (예: TripStatus, TripConditions)
    dto/              요청·응답 레코드 (~Request, ~Response, ~Item, ~Query)
    config/           그 도메인 설정값 @ConfigurationProperties 레코드 (~Properties)
global/
    config/           전역 설정 (Clock, Web MVC)
    exception/        ApiException, ErrorCode, GlobalExceptionHandler
    auth/             로그인 사용자 (@LoginUserId, 인증 필터)
    web/              요청 언어 (Language)
    dto/              공통 객체 (Code, LatLng, Money, PageQuery, PageResponse)
    util/             SearchText, SeoulTime
infra/<외부 서비스>/   외부 API 클라이언트 (예: infra/naver, infra/odsay). 처음 붙이는 사람이 만든다
loader/               데이터 적재 배치 (loader 프로필)
```

- 하위 패키지는 넣을 클래스가 있을 때만 만든다 (`candidate`는 엔티티가 없고, `user`·`expense` 등은 아직 엔티티만 있다)
- 새 API는 다루는 개념의 도메인에 넣고, 새 개념이면 도메인을 더한다. 여러 도메인을 쓰는 API는 결과의 주인 도메인에 둔다 (장소 후보 `GET /api/trips/{tripId}/candidates`는 `candidate`)
- 한 도메인에 API 묶음이 여럿이면 계층 패키지는 그대로 두고 클래스를 나눈다 (`place`의 `LodgingController`·`LodgingService`)
- 외부 API 클라이언트는 `infra`에 두고 서비스가 부른다. 키·시간 제한·실패 변환(`502 EXTERNAL_API_FAILED`)을 그 클라이언트 한곳에서 한다. 제공자가 바뀔 수 있는 연동(LLM)은 인터페이스를 두고 구현을 `infra`에 둔다
- 다른 패키지에서 쓰지 않는 클래스·메서드는 package-private으로 둔다 (컨트롤러·설정 클래스 등)
- 테스트는 대상과 같은 패키지에 둔다. MockMvc로 API를 보는 테스트는 도메인 패키지 바로 아래에 둔다 (`domain/trip/TripCreateTest`)

### 도메인 규칙 (DDD-lite)
- 엔티티에는 setter를 두지 않는다. 만들기는 정적 팩터리(`Trip.create`), 바꾸기는 이름 있는 메서드로 하고, 검사(확정한 여행은 바꿀 수 없음 등)도 그 메서드 안에 둔다. 기본 생성자는 `protected`
- 애그리거트: `Trip`이 루트이고 `TripDay`·`TripItem`·`Leg`가 그 아래다. 이것들을 만들고 바꾸고 지우는 코드(동선·일정 편집)는 `trip` 도메인에 두고, 다른 도메인은 `Trip`을 거쳐 다룬다
- 다른 도메인의 Repository는 조회에만 쓴다 (예: `PlaceRepository`). 다른 도메인의 엔티티를 만들거나 바꾸는 일은 그 도메인 서비스 메서드를 부른다
- 의존은 한 방향이다. `place`·`user`는 다른 도메인을 쓰지 않고, 지금은 `meta` → `place`, `trip` → `meta`·`place`·`user`, `candidate` → `trip`·`place`·`meta`, `expense` → `trip`, `favorite` → `place`·`user`다. 거꾸로 써야 할 것 같으면 순환이 생기니 쓰는 쪽(위)에서 조합한다
- 컨트롤러는 엔티티를 반환하지 않는다. 응답은 dto로 바꿔 준다
- 클래스 이름은 서비스 용어(아래 "구현 규칙")에 접미사 `~Controller`·`~Service`·`~Repository`·`~Request`·`~Response`·`~Properties`를 붙인다

### 다른 도메인에서 쓰는 것
- 국적·카테고리·지역 14개(중심 좌표)는 `domain.meta.entity`의 `Nationality`·`Category`·`Area`를 쓴다
- 여행을 다루는 API는 `TripService.findMyTrip(userId, tripId)`로 여행을 찾는다 (없거나 다른 사용자의 여행이면 `404 TRIP_NOT_FOUND`)
- 여행 요약(Trip) 응답은 `TripService.toResponse`로 만든다 (지연 로딩이 있어 트랜잭션 안에서 부른다)
- 여행을 만들거나 바꾸는 `TripService` 메서드(만들기·조건 다시 저장)는 먼저 `lockUser`로 사용자 행을 잠근다. 확정처럼 여행을 바꾸는 코드도 이 잠금을 거친다 (겹친 요청끼리 한쪽 변경이 덮어써지지 않게)
- 장소는 `domain.place.repository.PlaceRepository`로 읽는다. 주소(국문)는 `Place.getAddress()`, JdbcTemplate으로 읽은 행은 `Place.address(addr1, addr2)`
- 목록·카드·지도의 장소 요약(PlaceSummary·Image) 응답은 `domain.place.dto.PlaceSummary`를 쓴다
- 하루 최대 장소 수·권장 장소 수는 `domain.candidate.config.CandidateProperties`(`koready.candidate.*`)에서 읽는다

## 구현 규칙
- 금액·영업시간·거리·이동시간은 DB 조회나 계산 코드에서 만든다. LLM 응답에서 숫자를 받아 그대로 저장하거나 반환하지 않는다
- 동선(일자 배정·순서·이동시간)은 계산 로직이 정한다. LLM은 조건 파싱과 설명 생성만 맡는다
- 경비 응답은 항목별 범위(최소~최대)와 근거 데이터 출처를 함께 내려준다
- 엔티티·API 필드 이름은 서비스 용어(Trip, TripDay, TripItem, Leg, Place)를 그대로 쓴다
- 장소 추천·검색 쿼리는 `place_classes.excluded = true`인 분류의 장소를 뺀다
- 한글 이름순 정렬은 `ORDER BY name_ko COLLATE "C"`(코드 포인트 순 = 가나다순)로 한다. DB 기본 정렬(en_US.utf8)은 한글을 가나다순으로 두지 않는다

## 공통 기반 (`global` 패키지)
API를 만들 때 따로 만들지 말고 이것을 쓴다.
- 오류: `throw new ApiException(ErrorCode.X)`. 응답 `{code, message, fields}`는 `GlobalExceptionHandler`가 만든다. 서비스에서 입력을 검사할 때는 `FieldErrors`로 모아 한 번에 `400 INVALID_REQUEST`를 던진다
- 성공 응답은 감싸지 않는다. 목록은 `@Valid PageQuery`로 받고 `PageResponse`로 준다
- 이름·주소 검색어: `SearchText.strip(query, field, maxLength, errors)`가 앞뒤 공백(전각 공백 포함)을 빼고, 공백만이거나 길이(코드 포인트)를 넘으면 `FieldErrors`에 넣는다. ILIKE 패턴은 `SearchText.containsPattern(text)` (`%`·`_`를 글자 그대로 찾는다)
- 요청 본문의 정수 필드(인원·ID 등)에 소수(2.7)가 오면 잘라 받지 않고 `400 INVALID_REQUEST`다 (`spring.jackson.deserialization.accept-float-as-int=false`). 자동 구성된 `RestClient`로 받는 외부 API 응답에도 같이 걸리므로, 소수로 올 수 있는 값(ODsay 거리 등)은 `double`·`BigDecimal` 필드로 받는다
- 로그인 사용자: 컨트롤러 파라미터 `@LoginUserId Long userId` (없으면 `401 UNAUTHORIZED`). 인증 필터는 요청 속성 `LoginUserIdArgumentResolver.REQUEST_ATTRIBUTE`에 사용자 ID(Long)를 넣는다
- 요청 언어: 컨트롤러 파라미터 `Language language`. 표시 텍스트는 `language.pick(ko, en)` (영문이 없으면 국문)
- "오늘"·"지금"은 `Clock` 빈(서울)으로 정한다 (`LocalDate.now(clock)`). 기록 시각은 `SeoulTime.of(instant)`로 `+09:00`을 붙여 준다
- 공통 객체: `Code`(코드·표시 이름), `LatLng`, `Money`(금액·통화)
- 엔티티의 `created_at`·`updated_at`은 `@CreationTimestamp`·`@UpdateTimestamp`로 채운다
- 설정값은 `application.properties`의 `koready.*`에 두고 `@ConfigurationProperties` 레코드로 읽는다. 레코드는 그 도메인의 `config` 패키지에 둔다 (예: `domain.trip.config.TripProperties`)

## 데이터 적재
- TourAPI: Open API → 배치 적재
- 한국관광 데이터랩 카드 소비 통계: CSV 수동 다운로드 → 배치 적재 (크롤링 금지)
- 교통 운임: 요금 규칙을 따로 적재하지 않는다. ODsay 대중교통 길찾기로 확정한 방문 순서의 연속 구간(Leg)만 조회해 이동시간·운임을 얻는다. 방문 순서를 정하는 데는 쓰지 않는다
- 마스터 데이터는 월 1회 배치, 사용자 일정은 실시간
- 적재 코드는 `loader` 패키지에 있고 `loader` 프로필로 켤 때만 돈다. JdbcTemplate 배치로 자연 키(`content_id` 등)에 upsert하고, 하위 테이블(운영시간·요금·인기도 신호)은 지우고 다시 넣어 다시 돌려도 결과가 같다. 원본·덤프 파일은 저장소에 올리지 않는다
- 운영시간·요금은 원문(`*_text_ko`)을 규칙으로 나누고, 확실히 읽히지 않으면 `UNKNOWN`으로 둔다. 인기도 신호 중 이름 매칭이 필요한 것은 원본 폴더의 매칭 결과 파일(`signals/*.json`)을 읽는다
