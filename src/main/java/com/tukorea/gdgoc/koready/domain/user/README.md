# 인증 (user)

회원가입·로그인·소셜 로그인·토큰 재발급·로그아웃·아이디 중복 확인. 근거는 노션 「KOREADY API 명세」 인증·사용자(1~5, 32)와 에러 코드 표다.

## 프론트엔드 참고 (바뀐 점)

### 1. 토큰 응답이 AuthTokens 형태로 바뀌었다
회원가입(1)·로그인(2)·소셜 로그인(3)·토큰 재발급(4)이 모두 같은 형태를 준다.

```json
{
  "accessToken": "eyJ...",
  "accessTokenExpiresAt": "2026-11-21T15:30:00+09:00",
  "refreshToken": "q1w2e3...",
  "refreshTokenExpiresAt": "2026-12-05T15:00:00+09:00",
  "tokenType": "Bearer",
  "user": { "userId": 7, "name": "홍길동", "loginMethod": "ID" }
}
```

- `loginMethod`: `ID` · `GOOGLE` · `APPLE`. 소셜 계정은 `name`이 `null`일 수 있다
- 액세스 토큰 30분, 리프레시 토큰 14일
- **회원가입(1)은 이제 `201` + 위 토큰 묶음**을 준다 (전에는 본문 없음). 가입하면 바로 로그인 상태로 보면 된다
- **소셜 로그인(3)은 `newUser`(boolean)를 더 준다.** 이번에 계정을 만들었으면 `true`. 다른 응답에는 이 필드가 없다

### 2. 오류 code가 명세대로 바뀌었다 (이슈 #23)
| 상황 | 전 | 지금 |
|------|----|------|
| 로그인 실패 (아이디·비밀번호 구분 안 함) | `401 UNAUTHORIZED` | `401 LOGIN_FAILED` |
| 리프레시 토큰 없음·만료·이미 씀 | `401 UNAUTHORIZED` | `401 INVALID_REFRESH_TOKEN` → 로그인 화면으로 |
| 아이디 중복 (가입) | `409 CONFLICT` | `409 LOGIN_ID_TAKEN` |
| 액세스 토큰 만료 (로그인 필요 API 전체) | `401 UNAUTHORIZED` | `401 TOKEN_EXPIRED` → 재발급(4) 후 다시 요청 |
| 토큰 없음·위조 | `401 UNAUTHORIZED` | 그대로 `401 UNAUTHORIZED` → 재발급 없이 로그인 화면으로 |
| 소셜 ID 토큰 검증 실패 | `401 UNAUTHORIZED` | `401 INVALID_ID_TOKEN` |
| Google·Apple 공개키를 받지 못함 | `401 UNAUTHORIZED` | `502 EXTERNAL_API_FAILED` |
| 비밀번호가 너무 김 / 모르는 provider | `400` (fields 없음) | `400 INVALID_REQUEST` + `fields` (`password` / `provider`) |

지금 앱이 "401이면 일단 재발급"으로 처리하고 있다면(frontend #50), `TOKEN_EXPIRED`일 때만 재발급하고 `UNAUTHORIZED`·`INVALID_REFRESH_TOKEN`이면 로그인 화면으로 보내면 된다.

### 3. 아이디 규칙
- **영문 소문자·숫자·`_` 4~30자** (하이픈 `-` 불가). Figma 회원가입 화면·명세와 같다
- **아이디 중복 확인(32)도 형식을 검사한다.** 형식이 틀리면 `available`로 답하지 않고 `400 INVALID_REQUEST`
- 중복 확인 응답에 `loginId`가 추가됐다: `{"loginId": "hong_01", "available": true}`

### 4. 로그아웃 (5) 추가
```http
POST /api/auth/logout
Content-Type: application/json

{"refreshToken": "q1w2e3..."}
```
- `204`. 그 기기의 리프레시 토큰만 지운다. 이미 없어도 `204`
- 액세스 토큰은 만료(30분)까지 남는다. 앱은 응답과 상관없이 저장한 토큰을 지우고 로그인 화면으로 간다
- 로그아웃 요청은 401 재발급 처리(인터셉터)에서 빼 둔다

## 서버 동작 메모
- 로그인·소셜 로그인 때 그 사용자의 **만료된 리프레시 토큰을 지운다** (명세 2)
- 리프레시 토큰은 해시(SHA-256)만 저장하고, 재발급하면 이전 토큰은 지운다 (회전)
- 소셜 로그인으로 처음 가입할 때 제공자가 준 이름이 50글자보다 길면 **50글자(코드 포인트)로 잘라** 저장한다 (`users.name`). 이모지처럼 UTF-16 두 칸인 글자를 반으로 가르지 않는다
- 아직 없는 것: 로그인·아이디 중복 확인의 호출 한도(`429 TOO_MANY_REQUESTS`), 같은 소셜 계정으로 동시에 첫 로그인할 때의 오류 code(지금 `409 CONFLICT`, 명세 표에 없음)

## 코드 정리 내역 (chore/auth-cleanup)

리뷰에서 찾은 정리 거리다. API 요청·응답과 오류 code는 바뀌지 않는다.

| 바뀐 것 | 내용 | 파일 |
|------|------|------|
| 옛 인증 DTO 삭제 | `auth/dto`의 `LoginRequest`·`SignupRequest`는 `domain/user/dto`로 옮긴 뒤 쓰지 않는 사본이었다. 아이디 규칙도 달라(`_` 불가) 잘못 가져다 쓰면 규칙이 어긋나서 지웠다 | `auth/dto/` (삭제) |
| 소셜 이름 자르기 | `substring(0, 50)`은 UTF-16 단위라 50번째 글자가 이모지면 반으로 깨졌다. 글자(코드 포인트) 단위로 자른다 (`Trip`의 `truncate`와 같은 방식) | `AuthService.createSocialUser` |
| 재발급 주석 | "명세에 경로가 없어 임의로 정함"을 API 명세 4(`POST /api/auth/refresh`)에 맞게 고쳤다 | `AuthController.refresh` |
| 오타 | 환율 고시 시각 "11시쥔" → "11시쯤" | `KoreaEximClient`, `ExchangeProperties` |

### 리뷰어 확인

- 전체 테스트 292개가 통과한다. 이름 자르기를 따로 보는 테스트는 넣지 않았다 (소셜 로그인 첫 가입은 ID 토큰 검증을 거쳐 통합 테스트로 만들기 번거롭다)
- 이번에 손대지 않은 것: `security` 패키지(`JwtProvider`·`SecurityConfig`)가 CLAUDE.md 패키지 구조(`global` 아래)와 다르다. 여러 곳의 import를 바꿔야 해서 따로 하는 게 낫다
