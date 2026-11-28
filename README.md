# SSING 서버: 로깅·WebSocket·알림 검토 작업 복원

보관 전용. 팀 저장소 TEAM-SSING/SSING-SERVER의 main에 적용하거나 배포하지 않는다. 세 패치는 서로 다른 과거 기준을 쓰므로 각각 깨끗한 작업 폴더에 복원한다.

| 패치 | 용도 |
|---|---|
| json-log-tests.patch | 팀 main과 동일하게 들어간 앞선 로깅 작업은 재보관하지 않고, 끝 커밋의 Python JSON 로그 검증 테스트만 보존 |
| websocket-hardening.patch | STOMP 인증·이벤트 복구·매칭 스케줄링 실험의 앱, 테스트, 문서. 실제 배포용 파일·설정과 배포 전용 계약 테스트 제외 |
| notification-cursor-review.patch | PR #150에 앞선 알림 목록·커서·마이그레이션 검토판. 실제 병합판과 섞지 않는다 |

팀 원본을 새로 전체 clone한다. bases.txt에서 패치에 맞는 정확한 기준을 선택해 체크아웃하고 git apply --check, git apply <패치 경로>를 실행한다. json-log-tests의 기준은 현재 원격에서 접근할 수 있고 패치 적용을 검증한 main 커밋으로 기록했다. 원래 끝 커밋은 식별용이며 원격 팀 브랜치에 없는 값이다. 맥의 SSING 보관 폴더에 원래 Git 번들이 따로 있다.

팀 main의 CI는 공유 개발 DB 재설정을 시작할 수 있으므로 main에 푸시하거나 병합하지 않는다. 이 보관 브랜치에는 실행할 워크플로가 없다. 환경값, 계정 정보, 실데이터, stash는 보관하지 않는다.
