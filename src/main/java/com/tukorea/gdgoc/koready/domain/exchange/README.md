# 환율 (exchange)

예상 경비 금액을 사용자가 고른 표시 통화(KRW·JPY·USD·EUR)로 보여 주려고, 한국수출입은행 환율을 받아 저장하고 환산한다.
금액은 원화로 저장·계산하고, **응답할 때만** 표시 통화로 바꾼다.

## 동작

1. **수집**: 평일 11:30·16:30(서울)에 그날 고시 환율(매매기준율)을 받아 `exchange_rates`에 저장한다. 같은 날을 다시 받으면 값을 갱신한다
2. **시작 시 채우기**: 서버를 켤 때 최근 7일 중 비어 있는 날을 채운다. 별도 스레드에서 돌아서 서버 시작을 붙잡지 않는다
3. **환산**: 오늘(서울) 이전 가장 최근 고시 환율을 쓴다. 주말·공휴일·고시 전(오전 11시쥔 전)에는 직전 영업일 값이다
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
└── service/ExchangeService.java         # krwPerUnit · fromKrw · toKrw
```

예상 경비(`domain/expense/service/ExpenseService`)는 `ExchangeService.krwPerUnit`으로 환율을 받아 `MoneyConverter`로 바꾼다.
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
