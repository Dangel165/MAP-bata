# Minecraft Auth Plugin

Minecraft 1.21.1 서버를 위한 보안 인증 플러그인입니다.  
bcrypt 비밀번호 해싱, 세션 관리, SQLite/MySQL 지원, 관리자 명령어를 제공합니다.

## 기능

- `/register <비밀번호> <확인>` — 계정 등록 (자동 로그인)
- `/login <비밀번호>` — 로그인
- `/logout` — 로그아웃
- 관리자 명령어: `/authreload`, `/authunregister`, `/authchangepass`, `/authinfo`, `/authstats`
- IP 기반 레이트 리미팅 및 계정 잠금
- 비인증 플레이어 이동/상호작용 제한
- 인증 타임아웃 카운트다운 표시
- 비밀번호 강도 표시
- 한국어/영어 메시지 지원
- 자동 DB 백업 (최대 7개 보관)
- 감사 로그 (`plugins/AuthPlugin/audit.log`)

## 설치

1. `./gradlew shadowJar` 실행
2. `build/libs/minecraft-auth-plugin-1.0.0.jar` 를 서버 `plugins/` 폴더에 복사
3. 서버 재시작
4. `plugins/AuthPlugin/config.yml` 설정 후 `/authreload`

## 설정 (config.yml 주요 항목)

```yaml
database:
  type: sqlite          # sqlite 또는 mysql

security:
  min-password-length: 6
  max-failed-attempts: 3
  lockout-duration-seconds: 300
  auth-timeout-seconds: 300

messages:
  language: ko          # ko 또는 en
```

## 빌드

```bash
./gradlew shadowJar      # 플러그인 JAR 생성
./gradlew test           # 테스트 실행
```

## 권한

| 권한 | 설명 |
|------|------|
| `authplugin.admin` | 관리자 명령어 사용 |
| `authplugin.bypass` | 인증 우회 (OP 전용) |

## 라이선스

MIT
