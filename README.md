# ⛷️SSING
> ### 스키 강습의 새로운 기준을 세우다
<img width="3840" height="2160" alt="SSING 표지" src="https://github.com/user-attachments/assets/9babfed7-7592-4114-b6cf-4a8a204bbb70" />

## ✨ About SSING

SSING은 스키장, 일정, 종목, 실력, 인원 조건을 바탕으로 강습생에게 알맞은 강사를 연결하는 서비스입니다.</br>
강습생의 매칭 요청부터 강사의 제안·수락, 강습까지의 흐름을 안전하게 관리합니다.


## 🛠 Tech Stack

| 영역 | 기술 |
| :--- | :--- |
| Backend | Java 21, Spring Boot 4, Spring MVC, Spring Security, Spring Data JPA |
| Data | MySQL 8.4, Hibernate, Flyway |
| API & Realtime | REST API, Swagger / OpenAPI, WebSocket, JWT |
| Infrastructure | Docker Compose, AWS EC2 · RDS, Caddy |
| DevOps & Monitoring | GitHub Actions, Docker Hub, Sentry, Firebase Cloud Messaging |

## 🏗 Architecture
<img width="1274" height="530" alt="ssing_architecture_v1 drawio" src="https://github.com/user-attachments/assets/8a724871-f1ef-4d28-b1dc-06f32eb45ce2" />

## 📁 Project Structure

```text
src/main/java/org/sopt/ssingserver
├── domain/                  # 도메인별 Controller · Service · Repository · Entity
│   ├── auth/                # 소셜 로그인, 토큰 갱신·로그아웃
│   ├── instructor/          # 강사 정보와 매칭 노출 조건
│   ├── lesson/              # 강습 정보
│   ├── matching/            # 매칭 요청, 탐색, 제안, 확정
│   ├── notification/        # FCM 토큰과 알림
│   └── payment/             # 가격 스냅샷과 수수료 정책
└── global/                  # 보안, 예외 처리, 응답, 설정, 모니터링

src/main/resources/db/migration/  # Flyway migration
db/seed/                          # base · scenario QA seed
```


## 👩‍💻 Developers

<table>
  <tr>
    <td align="center" width="50%">
      <img height="400" alt="노성빈" src="https://github.com/user-attachments/assets/c5a73939-ba3b-46f6-8774-28b35187f1fe" /><br />
      <b>노성빈</b><br />
      <a href="https://github.com/SungKong00">@SungKong00</a><br />
      <sub>Server Developer · Lead</sub>
    </td>
    <td align="center" width="50%">
      <img height="400" alt="박유정" src="https://github.com/user-attachments/assets/f88f9cea-0bf9-4c64-bab4-1b21beea9542" /><br />
      <b>박유정</b><br />
      <a href="https://github.com/yujeong430">@yujeong430</a><br />
      <sub>Server Developer</sub>
    </td>
  </tr>
</table>
