# SSING 서버 설계와 기여 근거

이 문서는 개인 작업과 팀 구현을 구분합니다. 공개 서버 README에서 서버 리드 역할을 확인했고, 공개 PR과 서버 코드에서 아래 변경을 대조했습니다.

## 인증과 서비스 세션

Android의 Kakao SDK가 얻은 토큰을 서버에서 확인한 뒤, 서비스 자체 JWT와 갱신 토큰을 사용합니다. Kakao 사용자 정보 조회, 앱 식별 검증, 회원·역할 확인은 로그인 처리의 경계이고, 이후 API가 매번 Kakao로 인증을 확인하는 구조는 아닙니다. 오류는 공통 응답으로 전달하고 요청 로그는 request ID로 연결합니다.

- [인증 보안 기반 #13](https://github.com/TEAM-SSING/SSING-SERVER/pull/13)
- [인증 API #23](https://github.com/TEAM-SSING/SSING-SERVER/pull/23)

## 상대 앱에 보여주는 상태 계약

강습생과 강사 화면은 같은 그룹과 제안을 다른 관점에서 봅니다. 검색 대기나 수락 요청과 같은 중간 상태, 확정 후 강습으로 열린 상태를 같은 표시 규칙으로 처리하되 다음 화면이 참조할 제안 ID와 강습 ID를 분리했습니다. 상태를 추론하려는 클라이언트 코드가 줄어드는 대신, 상태 추가 시 서버와 Android 계약을 함께 검증해야 합니다.

- [강사 홈 식별자와 상태 #148](https://github.com/TEAM-SSING/SSING-SERVER/pull/148)
- [활성 없음과 종료 상태 응답 #151](https://github.com/TEAM-SSING/SSING-SERVER/pull/151)
- [강습생 재진입 데이터 #165](https://github.com/TEAM-SSING/SSING-SERVER/pull/165)
- [강사 재진입 데이터 #169](https://github.com/TEAM-SSING/SSING-SERVER/pull/169)

## 빠른 매칭과 재탐색

요청이 오면 우선 현재 조건과 후보로 탐색하고, 한 번의 탐색으로 확정되지 않은 요청을 후속 탐색에서 다시 확인합니다. 후보가 없는 상태와 실패 혹은 취소를 동일하게 취급하지 않습니다. 매칭 스케줄러에는 60초 고정 지연 설정이 있습니다. 이는 ‘사용자가 60초 안에 매칭된다’는 서비스 수준 보장이 아닙니다.

## 개발용 작업의 제한

QA 콘솔의 조회는 local/dev 전용이고 상태 변경은 별도 플래그가 있어야 합니다. 단일 요청의 허용된 변경은 영향 대상과 기존 관계를 먼저 보여준 뒤 실행합니다. 조회 당시와 현재의 상태가 다르면 재조회를 유도합니다. 개발용 강사 설정 경로는 관련 데이터 잠금을 얻은 뒤 상태를 다시 검증하지만, 이 제약을 모든 매칭 작업의 전반적 동시성 보장으로 확대 해석하지 않습니다.

- [요청관계 조회와 영향 미리보기 #196](https://github.com/TEAM-SSING/SSING-SERVER/pull/196)
- [허용 동작과 stale 상태 처리 #197](https://github.com/TEAM-SSING/SSING-SERVER/pull/197)
- [CI/CD 검증 및 배포 시간 최적화 #178](https://github.com/TEAM-SSING/SSING-SERVER/pull/178)
