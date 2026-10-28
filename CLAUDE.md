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
- 실행: `./gradlew bootRun`
- 테스트: `./gradlew test`

## 패키지
- 루트 패키지: `com.tukorea.gdgoc.koready`

## 구현 규칙
- 금액·영업시간·거리·이동시간은 DB 조회나 계산 코드에서 만든다. LLM 응답에서 숫자를 받아 그대로 저장하거나 반환하지 않는다
- 동선(일자 배정·순서·이동시간)은 계산 로직이 정한다. LLM은 조건 파싱과 설명 생성만 맡는다
- 경비 응답은 항목별 범위(최소~최대)와 근거 데이터 출처를 함께 내려준다
- 엔티티·API 필드 이름은 서비스 용어(Trip, TripDay, TripItem, Leg, Place)를 그대로 쓴다

## 데이터 적재
- TourAPI: Open API → 배치 적재
- 한국관광 데이터랩 카드 소비 통계: CSV 수동 다운로드 → 배치 적재 (크롤링 금지)
- 교통 운임: 요금 규칙을 따로 적재하지 않는다. ODsay 대중교통 길찾기로 확정한 방문 순서의 연속 구간(Leg)만 조회해 이동시간·운임을 얻는다. 방문 순서를 정하는 데는 쓰지 않는다
- 마스터 데이터는 월 1회 배치, 사용자 일정은 실시간
