# mini_mes

Java 17 / Spring Boot / MyBatis 기반 Mini MES의 초기 Backend 구성입니다. 업무 CRUD는 아직 구현하지 않았습니다.

## 개발 도구와 버전

| 항목 | 버전·기준 |
|---|---|
| Java | JDK 17, JAVA_HOME 설정 필요 |
| Spring Boot | 3.5.16 |
| Gradle | Wrapper 8.14.3 |
| MyBatis Starter | 3.0.5 |
| Connector/J | Boot BOM 관리, 9.7.0 |
| JUnit Jupiter | Boot BOM 관리, 5.12.2 |
| Thymeleaf | Boot BOM 관리, 3.1.5.RELEASE |
| MySQL | Docker 공식 이미지 mysql:8.4, InnoDB |

Spring MVC·Thymeleaf·테스트 의존성은 Spring Boot BOM의 호환 버전을 사용합니다. Wrapper 실행에 전역 Gradle 설치는 필요하지 않습니다. 첫 Gradle 사용 시 네트워크로 배포본과 의존성을 다운로드합니다. MySQL의 8.4 태그는 패치 버전이 갱신될 수 있으므로 실제 배포 시 패치·이미지 digest를 별도로 확인해야 합니다.

버전 근거: [Boot Java·Gradle 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Boot 의존성 관리](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [MyBatis 호환 범위](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/).

## 최소 디렉터리 구조

```text
db/schema.sql                         # 스키마 단일 원본
gradle/wrapper/                       # Gradle 고정 버전·체크섬·Wrapper JAR
src/main/java/com/taehyung/mes/        # MesApplication
src/main/resources/                   # application.yml, application-dev.yml
src/main/resources/mappers/           # 향후 Mapper XML
src/main/resources/templates/         # 향후 Thymeleaf 화면
src/test/java/com/taehyung/mes/        # 컨텍스트·MyBatis DB 연결 검증
src/test/resources/                   # 향후 테스트 전용 리소스
```

업무 구현 시 기본 패키지 아래 item, bom, process, routing, production, workorder, lot, quality, trace, common을 필요한 순서로 추가합니다. 빈 도메인 클래스·공통 CRUD·JPA는 도입하지 않았습니다. 향후 Mapper 인터페이스에는 `@Mapper`를 사용하고 XML은 `mappers/**/*.xml` 아래에 둡니다.

## 환경변수 설정

다음 명령은 향후 개발자가 실행할 절차이며 이번 구성 작업에서 실행하지 않았습니다.

PowerShell에서 예시 파일을 복사한 후 로컬 편집기로 비밀번호를 채웁니다.

```powershell
Copy-Item .env.example .env
```

`.env`의 DB_NAME, DB_USER, DB_PASSWORD, MYSQL_ROOT_PASSWORD를 확인합니다. DB_PASSWORD와 MYSQL_ROOT_PASSWORD는 서로 다른 로컬 비밀값을 사용합니다. 빈 비밀번호는 Compose가 거부합니다. 실제 `.env`는 Git에서 제외하며 `.env.example`에는 비밀번호를 넣지 않습니다.

**Compose는 `.env`를 읽지만 Spring Boot는 자동으로 읽지 않습니다.** 애플리케이션 실행과 테스트 프로세스에도 DB_NAME·DB_USER·DB_PASSWORD를 같은 값으로 설정합니다. DB_HOST 기본값은 127.0.0.1, DB_PORT 기본값은 3306입니다. IDE의 실행·Gradle 테스트 환경변수 설정을 이용할 수 있습니다. root 비밀번호는 애플리케이션에 전달할 필요가 없습니다.

PowerShell 터미널에서 직접 실행하려면 아래와 같이 필요한 값을 프로세스 환경변수로 설정합니다. DB_PORT를 바꿨다면 `.env`와 동일하게 맞춥니다. 프롬프트에는 로컬 `.env`에 입력한 애플리케이션 DB 비밀번호를 입력합니다.

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'mini_mes'
$env:DB_USER = 'mes_app'
$mesDbPassword = Read-Host '로컬 DB_PASSWORD' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $mesDbPassword).Password
```

위 이름·계정은 예시이며 `.env`에서 변경했다면 모두 같은 값으로 맞춥니다. Compose의 환경변수 우선순위상 터미널에 이미 설정한 값이 `.env`보다 우선할 수 있으므로 서로 다른 환경을 섞지 않습니다. 비밀번호·접속정보를 로그나 공유된 명령 기록에 출력하지 않습니다.

## MySQL 개발 환경

Docker Engine/Desktop과 Compose v2가 필요합니다. 먼저 환경변수 값·3306 포트 사용 여부·Docker Volume의 기존 데이터 여부를 확인합니다. 포트 충돌이 있으면 DB_PORT를 변경하고 애플리케이션 환경변수도 맞춥니다. 다른 서비스를 종료하는 절차는 포함하지 않습니다.

구성 확인 및 기동 명령은 다음과 같습니다. 각각 실행 승인을 받은 후 사용합니다.

```powershell
docker compose config --quiet
docker compose up -d mysql
docker compose ps
```

MySQL은 호스트 `127.0.0.1:DB_PORT`에만 바인딩되고 데이터는 `mysql_data` 명명 Volume에 보존됩니다. 서버 기본값은 utf8mb4 / utf8mb4_0900_ai_ci / UTC / InnoDB입니다. healthcheck는 애플리케이션 계정의 `SELECT 1` 연결만 확인하며 업무 테이블의 존재·FK·CHECK는 검증하지 않습니다.

첫 MySQL 컨테이너 기동 시 공식 이미지가 MYSQL_DATABASE·MYSQL_USER 설정으로 개발용 DB와 계정을 생성합니다. **그 첫 기동도 실제 DB 생성 작업이므로 별도 승인 전에는 실행하지 않습니다.** 기존 Volume이 있으면 초기 계정 환경변수를 바꿔도 비밀번호·DB가 자동 재설정되지 않습니다. Volume 삭제로 기존 데이터를 임의 초기화하지 않습니다. 개발 계정의 초기 권한을 운영 환경에 그대로 사용하지 않습니다.

Compose에는 `docker-entrypoint-initdb.d` SQL 마운트가 없으며 schema.sql을 자동 적용하지 않습니다. Spring Boot Docker Compose 자동 실행 의존성도 없습니다.

## 스키마 단일 원본과 적용 전 확인

스키마 원본은 `db/schema.sql`입니다. `src/main/resources`에 복제하지 않습니다. `spring.sql.init.mode=never`로 Spring SQL 초기화를 비활성화했고 JPA·Flyway·Liquibase를 추가하지 않았습니다. 애플리케이션 실행·테스트에서 테이블을 생성·삭제하지 않습니다.

DDL 적용은 별도 승인 작업입니다. 적용 전 대상 DB·MySQL 8.4 패치·빈 스키마 여부·기존 데이터 보존·세션 UTC·strict SQL mode·18개 테이블 및 FK/CHECK를 확인해야 합니다. DDL은 DROP이나 IF NOT EXISTS로 기존 구조를 우회하지 않습니다. 승인 없이 실행하지 않도록 이번 README에는 DDL 실행 명령을 포함하지 않습니다.

DDL 적용 없이도 미리 생성된 개발용 DB에 대한 `SELECT 1` 연결 검증은 가능합니다. 업무 FK·CHECK 검증에는 스키마 적용과 별도의 테스트 승인이 필요합니다. 생산 EA 정수·원자재 수량의 입력 정밀도 검증은 후속 Service 책임도 있으므로 DB CHECK만으로 완료된 것으로 보지 않습니다.

## 향후 실행과 검증

기본 Profile은 dev입니다. dev 서버는 127.0.0.1:8080, 로그는 애플리케이션 패키지 DEBUG, Thymeleaf cache는 비활성화합니다. DB명·계정·비밀번호 누락은 임의 기본값으로 대체하지 않습니다. JDBC는 connectionTimeZone=UTC와 forceConnectionTimeZoneToSession=true를 사용하고 Hikari 트랜잭션 격리는 READ COMMITTED를 추천 기본값으로 설정했습니다. 업무별 부모 잠금·원자성 검증은 아직 구현하지 않았습니다.

환경변수와 DB 준비 후 다음 명령을 각각 승인된 범위에서 실행합니다.

```powershell
.\gradlew.bat bootRun
.\gradlew.bat test --tests com.taehyung.mes.MesApplicationTests
.\gradlew.bat build
```

macOS/Linux에서는 같은 환경변수를 설정한 뒤 `bash ./gradlew bootRun`, `bash ./gradlew test --tests com.taehyung.mes.MesApplicationTests`, `bash ./gradlew build`를 사용합니다. 애플리케이션·테스트 환경변수는 해당 터미널/IDE 프로세스에 전달되어야 합니다.

`MesApplicationTests`는 test Profile에서 웹 서버를 띄우지 않고 컨텍스트와 MyBatis SqlSessionFactory를 구성한 뒤 실제 MySQL 연결로 `SELECT 1`을 확인합니다. H2 대체 DB나 자동 스키마 생성은 사용하지 않습니다. 아직 FK·CHECK·트랜잭션·동시 소비·실적/LOT 원자 생성 테스트를 구현한 것은 아닙니다.

현재 업무 Controller·화면이 없으므로 `/`의 404는 예상 동작입니다. 초기 Mapper가 없다는 스캔 경고가 발생할 수 있지만 업무 Mapper를 형식적으로 생성해 숨기지 않습니다. 버전·설정은 정적으로 검토했으며 빌드·컨텍스트·DB 연결·Docker 실행 성공은 아직 확인하지 않았습니다.
