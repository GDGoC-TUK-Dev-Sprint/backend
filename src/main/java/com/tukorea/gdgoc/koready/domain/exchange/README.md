# 환율 (exchange)

예상 경비 금액을 사용자가 고른 표시 통화(KRW·JPY·USD·EUR)로 보여 주려고, 한국수출입은행 환율을 받아 저장하고 환산한다.
금액은 원화로 저장·계산하고, **응답할 때만** 표시 통화로 바꾼다.

## 동작

1. **수집**: 평일 11:30·16:30(서울)에 그날 고시 환율(매매기준율)을 받아 `exchange_rates`에 저장한다. 같은 날을 다시 받으면 값을 갱신한다
2. **시작 시 채우기**: 서버를 켤 때 최근 7일 중 통화가 다 차지 않은 날(한 통화만 빠진 날 포함)을 채운다. 별도 스레드에서 돌아서 서버 시작을 붙잡지 않는다
3. **환산**: 오늘(서울) 이전 가장 최근 고시 환율을 쓴다. 주말·공휴일·고시 전(오전 11시 전)에는 직전 영업일 값이다
4. **표시 통화**: 요청 헤더 `X-Currency`(KRW·JPY·USD·EUR)로 받는다. 없으면 `KRW`, 다른 값이면 `400 INVALID_REQUEST` (API 명세 공통 규칙). 예상 경비 API(`GET /api/trips/{tripId}/cost-estimate`)가 금액을 그 통화로 바꿔 준다

수집이나 저장에 실패해도 서버는 계속 돌고 로그만 남는다. 마지막으로 저장한 환율로 계속 환산한다.

## 설정

인증키는 한국수출입은행 Open API(현재환율, `data=AP01`)에서 받아 환경변수로만 넣는다. 저장소에 올리지 않는다.

| 환경변수 | 설명 |
|------|------|
| `KOREAEXIM_API_KEY` | 한국수출입은행 인증키. 없으면 서버는 뜨지만 환율을 수집하지 않고 경고 로그만 남긴다 |

| `application.properties` | 기본값 | 설명 |
|------|------|------|
| `koready.koreaexim.base-url` | `https://oapi.koreaexim.go.kr` | API 주소 |
| `koready.koreaexim.connect-timeout` · `read-timeout` | `3s` · `5s` | 시간 제한 |
| `koready.exchange.collect-cron` | `0 30 11,16 * * MON-FRI` | 수집 시각 (서울) |
| `koready.exchange.backfill-days` | `7` | 서버 시작 때 채우는 일수 (0이면 안 채움) |

적재 모드(`loader` 프로필)에서는 수집하지 않는다.

## 환산 규칙

| 항목 | 규칙 |
|------|------|
| 소수 자리 | KRW·JPY 0자리, USD·EUR 2자리 |
| 범위(최소~최대) | min은 내림·max는 올림해 KRW 1,000원·JPY 100엔·USD·EUR 1 단위로 맞춘다. 응답은 `MoneyRange` `{min, max, currency}` |
| 금액 하나 | 반올림 |
| 비교·비율 | 예산 초과 여부·비중은 **환산 전 원화로** 계산하고 결과만 바꾼다 |
| JPY | API가 100엔 기준(`JPY(100)`)으로 주므로 100으로 나눠 1엔 기준으로 저장한다 |

## 오류

| 코드 | 언제 |
|------|------|
| `503 EXCHANGE_RATE_UNAVAILABLE` | 외화로 요청했는데 저장된 환율이 하나도 없을 때. KRW 요청은 환율을 안 써서 항상 된다 |
| `502 EXTERNAL_API_FAILED` | 수집할 때 API 호출이 실패하면 안에서 쓰고 로그로만 남는다 (사용자 응답에는 나가지 않는다) |

## 파일

```
infra/koreaexim/
├── KoreaEximClient.java         # API 호출·응답 읽기 (JPY(100) → 1엔, 쉼표 제거)
└── KoreaEximProperties.java     # koready.koreaexim.* 설정
domain/exchange/
├── config/ExchangeConfig.java       # 스케줄 켜기 (loader 제외)
├── config/ExchangeProperties.java   # koready.exchange.* 설정
├── entity/ExchangeRate.java         # 고시일 × 통화 1행, 1단위당 원화
├── repository/ExchangeRateRepository.java
├── service/ExchangeRateCollector.java   # 수집·시작 시 채우기
└── service/ExchangeService.java         # rate · krwPerUnit · fromKrw · toKrw
```

예상 경비(`domain/expense/service/ExpenseService`)는 `ExchangeService.rate`로 환율·고시일을 받아 `MoneyConverter`로 바꾼다.
표시 통화는 `global/web/DisplayCurrencyArgumentResolver`가 헤더에서 읽어 컨트롤러 파라미터 `DisplayCurrency currency`로 넣는다.
다른 화면(희망 예산·운임·입장 요금·지출)도 응답은 `fromKrw`, 입력은 `toKrw`를 쓰면 된다.

## 확인 방법

1. `./gradlew test`: 인증키 없이도 통과한다 (수집은 건너뛴다). `KoreaEximClientTest`가 응답 읽기를 확인한다
2. 인증키를 넣고 실행한다

   ```bash
   KOREAEXIM_API_KEY=발급받은키 ./gradlew bootRun
   ```

   로그에 `yyyy-MM-dd 환율 3개 저장`이 찍히면 된다. DB에서도 확인한다

   ```bash
   docker compose exec db psql -U koready -d koready -c "SELECT rate_date, currency, krw_per_unit FROM exchange_rates ORDER BY rate_date DESC LIMIT 6;"
   ```

3. 예상 경비를 헤더 `X-Currency: USD`로 불러 금액이 달러로 오는지 본다. `X-Currency: GBP`면 `400`, `exchange_rates`가 비어 있으면 `503`이 와야 한다

## 프론트엔드 참고 (예상 경비 응답이 바뀌었어요)

API 명세 25·26에 맞춰 예상 경비 응답 구조를 바꿨어요. 예전 필드(`perPerson`이 금액 범위, `budget.perPerson`, `items[].source`, `days[].peak` 등)는 더 이상 오지 않아요.

### 공통

- 표시 통화는 요청 헤더 `X-Currency: KRW|JPY|USD|EUR`로 보내 주세요. 없으면 KRW, 다른 값이면 `400 INVALID_REQUEST`
- 금액 범위는 모두 `MoneyRange` `{ "min": 12000, "max": 18000, "currency": "KRW" }`. min은 내림·max는 올림해서 KRW 1,000원·JPY 100엔·USD·EUR 1 단위예요
- 금액 하나(희망 예산)는 `Money` `{ "amount": 300000, "currency": "KRW" }`. USD·EUR는 소수 둘째 자리까지
- 문장(`basis`, `guidance`, `limitations[].message`, `sources[].name`)은 `Accept-Language`(ko/en)에 맞춰 와요. 화면에 그대로 쓰면 돼요
- 여행 요약(`trip.budgetPerPerson`)도 `X-Currency`로 환산해서 와요 (여행 만들기·조회·조건 다시 저장·동선 만들기·확정·예상 경비 응답 모두). KRW·JPY는 정수, USD·EUR는 소수 둘째 자리로 반올림

### 희망 예산 입력 (여행 만들기 `POST /api/trips` · 조건 다시 저장 `PUT /api/trips/{tripId}`)

- `budgetPerPerson`은 이제 KRW·JPY·USD·EUR 모두 보낼 수 있어요. 예전에는 KRW가 아니면 `400` ("지금은 KRW만 받습니다")이었어요
- 서버는 오늘(서울) 환율로 원화로 바꿔 저장해요. 그래서 나중에 같은 통화로 다시 보면 환율 변동만큼 금액이 조금 달라질 수 있어요
- 자릿수: KRW·JPY는 정수, USD·EUR는 소수 둘째 자리까지. 넘으면 `400 INVALID_REQUEST` (`budgetPerPerson.amount`)
- 외화인데 환율 데이터가 없으면 `503 EXCHANGE_RATE_UNAVAILABLE`. KRW는 항상 돼요
- 응답의 여행 요약 `trip.budgetPerPerson`은 `X-Currency`로 환산해서 와요. 외화로 보면서 환율 데이터가 없으면 이 API들도 `503 EXCHANGE_RATE_UNAVAILABLE`

### `GET /api/trips/{tripId}/cost-estimate` (25)

```json
{
  "trip": { "...": "여행 요약 (TripResponse)" },
  "perPerson": {
    "amount": { "min": 210000, "max": 260000, "currency": "KRW" },
    "budget": {
      "amount": { "amount": 250000, "currency": "KRW" },
      "status": "MAY_EXCEED",
      "usagePct": { "min": 84.0, "max": 104.0 },
      "overBy": { "min": 0, "max": 10000, "currency": "KRW" },
      "leftOver": { "min": 0, "max": 40000, "currency": "KRW" }
    },
    "items": [
      { "item": "FOOD", "amount": { "min": 90000, "max": 120000, "currency": "KRW" }, "sharePct": 44.7, "basis": "한국관광 데이터랩 ..." }
    ],
    "days": [
      { "dayNumber": 1, "date": "2026-12-01", "amount": { "...": "MoneyRange" }, "items": [ { "item": "FOOD", "amount": { "...": "MoneyRange" } } ], "highest": true }
    ]
  },
  "total": { "...": "perPerson과 같은 구조, 인원 합계 기준" },
  "guidance": "일정에 따라 희망 예산을 1인 최대 10,000 KRW 넘을 수 있어요. ...",
  "limitations": [ { "code": "FEE_UNKNOWN", "message": "요금 정보가 없는 장소 2곳은 입장료를 0원으로 계산했어요." } ],
  "sources": [ { "name": "한국관광 데이터랩 방한 외국인 카드 소비 통계", "detail": "2025-01 ~ 2025-06" } ],
  "exchangeRate": null
}
```

| 필드 | 설명 |
|------|------|
| `perPerson` / `total` | 1인 기준 / 인원 합계 기준. 둘 다 오니까 1인·전체 전환(04-03)은 다시 요청하지 않아도 돼요 |
| `*.budget` | 희망 예산이 없으면 `null`. `total.budget.amount`는 1인 예산 × 인원이에요 |
| `budget.status` | `WITHIN_BUDGET`(최대도 예산 이하) · `MAY_EXCEED`(최소는 이하, 최대는 초과) · `OVER_BUDGET`(최소도 초과) |
| `budget.usagePct` | 예산 대비 % (소수 첫째 자리). 비교 막대에 쓰면 돼요. 예산이 0원이면 `null` |
| `budget.overBy` | 초과액 범위. `WITHIN_BUDGET`이면 `null` |
| `budget.leftOver` | 남는 금액 범위. `OVER_BUDGET`이면 `null` |
| `items[]` | 항목 순서 FOOD · SIGHTSEEING · TRANSPORT · SHOPPING · OTHER. `item`은 코드만 오니 화면 이름은 프론트에서 매핑해 주세요. `sharePct`는 범위 가운데값 기준 비중(%) |
| `days[].highest` | 가장 많이 쓰는 날 (한 날만 `true`) |
| `guidance` | 예산 조정 안내 (1인 기준). 지금은 상태별 템플릿 문장이고, 희망 예산이 없으면 `null` |
| `limitations[].code` | `CARD_SPENDING_ONLY`(카드 결제만 집계) · `NO_CARD_STATS`(국적 통계 없음) · `FEE_UNKNOWN`(요금 정보 없는 장소 N곳) · `FARE_UNAVAILABLE`(운임 없는 이동 구간 N곳). 해당될 때만 와요 |
| `sources[]` | 근거 출처. `detail`은 통계 기간·환율 고시일 (없으면 `null`) |
| `exchangeRate` | `{ "currency": "USD", "krwPerUnit": 1385.5, "rateDate": "2026-11-20" }`. KRW면 `null`. JPY는 1엔 기준 |

숙박·항공은 들어 있지 않아요.

### `POST /api/trips/{tripId}/confirm` (26)

- 확정하면 여행 요약(`TripResponse`)을 돌려줘요. 이미 확정한 여행이면 **그대로 200** (다시 눌러도 오류가 아니에요)
- 끝나지 않은 다른 확정 여행이 있으면 `409 ACTIVE_TRIP_EXISTS` (`activeTripId` 포함)

### 오류

| 코드 | 언제 |
|------|------|
| `409 ROUTE_NOT_CREATED` | 동선을 만들기 전에 예상 경비를 부르거나 확정할 때 |
| `503 EXCHANGE_RATE_UNAVAILABLE` | 외화로 요청했는데 환율 데이터가 없을 때 (예상 경비, 외화 희망 예산을 넣은 여행 만들기·조건 저장). KRW로 다시 부르면 돼요 |
