# Routing 구현 및 검증 안내

## 구현 화면과 구조

| 기능 | GET | POST |
|---|---|---|
| 완제품 코드·명칭 검색/개정 목록 | /routings?code=&name= | 없음 |
| 신규 Routing | /routings/new | /routings |
| Routing 개정 추가 | /routings/{routingId}/revisions/new | /routings/{routingId}/revisions |
| 개정 상세·순서·스냅샷·사용 이력 | /routings/revisions/{revisionId} | 없음 |

- RoutingController → RoutingService → RoutingMapper XML → MySQL. 품목/공정 잠금 조회는 기존 ItemMapper/ProcessMapper, 작성자는 ActorProvider를 재사용한다.
- 공통 레이아웃·CSS·mes.js 저장 버튼/리다이렉트 흐름을 재사용한다. routing.js는 등록 전 공정 추가·제거·위/아래 이동과 화면 검증만 담당한다.
- 목록은 Routing 코드, 제품, 개정, 개정 등록 UTC 시각과 사용 이력을 표시한다. 상세는 저장 스냅샷과 현재 공정명을 구분한다.
- 직전 공정 작업의 미커밋 변경을 보존했다. 이번 작업에서 커밋·push하지 않았다.

## 추가·수정 파일

- 신규 Java: Routing.java, RoutingRevision.java, RoutingStep.java, RoutingForm.java, RoutingStepForm.java, RoutingRuleException.java, RoutingNotFoundException.java, RoutingMapper.java, RoutingService.java, RoutingController.java.
- 신규 화면/SQL: RoutingMapper.xml, Routing용 list.html/form.html/detail.html, routing.js.
- 신규 테스트: RoutingServiceTests.java, RoutingControllerTests.java, RoutingMapperTests.java, RoutingMysqlIntegrationTests.java.
- 공통 수정: layout.html, error.html, PageExceptionHandler.java, messages.properties. 기존 품목·BOM·공정 Service/Mapper 및 CSS·mes.js는 변경하지 않았다.
- 문서: business-rules.md(BR-09), mini_mes_prd.md(FR-04), database-design.md, erd-design.md, 2026-10-26-routing.md, routing-verification.md.
- schema.sql과 의존성은 변경하지 않았다. Routing/Revision/Step의 기존 FK·UNIQUE·CHECK를 그대로 따른다.

## 정책과 트랜잭션

- 활성 FINISHED 제품으로 신규 헤더/개정을 등록한다. Routing에는 제품 수량 단위가 없으므로 BOM의 EA 제한은 복제하지 않는다.
- 코드 전역 유일, 개정은 헤더 내 유일, 순번은 개정 내 유일하다. 개정/순번은 1~4294967295 정수다. 순번의 간격과 같은 공정 참조의 여러 순번 배치는 DB에 없는 추가 제약으로 금지하지 않는다. 반복·병렬 생산 실행 기능은 구현하지 않는다.
- 경로 누락을 막기 위해 공정 1개 이상을 요구한다. 폼 바인딩 한도는 기존과 같은 256행이다. 위/아래 버튼은 이웃 행의 순번을 교환하며 직접 순번 입력도 가능하다.
- 신규 헤더는 Item → Process(PK순), 기존 헤더의 개정 추가는 Routing → Item → Process(PK순)으로 잠근다. 잠금 후 활성 상태를 검사하므로 공정 비활성화와 경쟁하면 먼저 커밋된 상태를 재검증한다.
- 잠근 공정의 현재 명칭을 새 Step의 process_name_snapshot으로 서버에서 기록한다. DTO·바인딩에서 스냅샷·작성자·first_used_at 입력은 받지 않는다.
- 개정별 스냅샷은 현재 공정명 변경 후에도 보존한다. 새 개정은 현재 활성 공정의 현재 명칭을 기록한다. 과거 Step을 재사용하거나 자동 갱신하지 않는다.
- 헤더·개정·공정 구성 INSERT는 Service 한 트랜잭션이다. 신규 ID와 영향 행수를 검사한다. 중복 사전 조회와 DB UNIQUE 충돌 처리를 함께 적용한다. 성공 재전송에 새 행을 추가하지 않으며 중복은 오류로 안내한다.
- 사용 이력은 first_used_at 또는 작업지시 참조 존재로 판단한다. 등록 개정에 별도 발행·활성 상태는 없다.
- 저장된 개정의 UPDATE·DELETE API/SQL은 없다. 사용된 개정의 수정 시도는 HTTP 405로 거부한다. 이는 사용 여부에 따른 Service 수정 검증을 구현한 것이 아니라, 허용되지 않은 쓰기 경로 자체를 제공하지 않은 것이다.

## Business Decision Required — 저장된 미사용 개정의 직접 편집

- 문제: 미사용 개정에서 허용할 추가·제거·교체·순번 변경과 기존 스냅샷 취급이 문서에서 미확정이다.
- 현재 규칙: 사용된 개정 불변, 부모 잠금, 과거 스냅샷 보존, 승인되지 않은 물리 삭제 금지.
- 추천: 현재 제공하는 새 개정 등록을 유지하면서 직접 편집 정책을 별도 확정한다. 기존 데이터/이력을 변경하지 않고 경로 변경을 사용할 수 있다.
- 대안: 미사용 개정에 한정한 편집을 승인하되 제거의 물리 삭제 예외, 구성 PK 보존, 순번 UNIQUE 교환, 오래된 화면 충돌, 기존/새 공정 스냅샷 규칙까지 정한다.
- 영향: 대안은 Service·SQL·수정 DTO·화면·동시성 테스트가 추가로 필요하다. 현재 작업에서 임의 확정하거나 미확정 편집 API를 만들지 않았다. 공정 생략·반복·병렬 실행도 후속 결정이다.

## 작성한 테스트와 검증 한계

- RoutingServiceTests: Mock Mapper와 실제 Service로 등록/개정 추가, 없는·잘못된·미사용 제품, 없는·미사용 공정, 순번 중복/범위, 개정 중복, 스냅샷, 잠금 호출 순서, UNIQUE 예외 변환을 검증하도록 작성했다.
- Mock Spring 트랜잭션 테스트는 뒤 공정 저장 실패 시 TransactionManager.rollback 호출을 확인한다. 실제 MySQL rollback 검증이 아니다.
- RoutingControllerTests: 폼 숫자 변환 오류·입력 모델·성공 리다이렉트·404/503·저장된 사용 개정 POST/DELETE의 405를 검증하도록 작성했다. standalone MockMvc이므로 Thymeleaf 렌더링·브라우저 이벤트는 검증하지 않는다.
- RoutingMapperTests: XML에 저장된 개정/스냅샷 UPDATE·DELETE가 없고 스냅샷 INSERT·순서 조회가 유지되는지 검사하도록 작성했다. 실제 DB 데이터 보존 검증은 아니다.
- RoutingMysqlIntegrationTests: 실제 Mapper/Spring 트랜잭션과 전용 MySQL을 사용해 스냅샷 보존, 두 번째 Step 실패 후 선행 행 롤백, 실제 UNIQUE 충돌, 동시 등록 1건 유지, 공정 비활성화 잠금 경합을 검증하도록 작성했다. 실패 주입에는 Mapper spy를 사용하되 선행 SQL/트랜잭션 및 rollback 결과 조회는 실제 DB를 사용한다.
- **모든 테스트는 미실행이다.** 빌드·린트·브라우저·렌더링·개발서버·DB도 실행하지 않았다. 운영 OCI에 접근하거나 데이터를 변경하지 않았다. 정적 검토와 독립 읽기 전용 리뷰만 수행했다.

## 개발자 직접 검증

격리된 개발/테스트 환경에서 제품과 공정을 준비한 후 다음을 확인한다.

1. /routings에서 빈 목록·코드/명칭 검색·등록·상세·새 개정 이동과 기존 메뉴 활성 표시.
2. 신규 Routing에 순번 10/20의 활성 공정을 등록. 상세에서 서버의 명칭 스냅샷·등록 시각 확인.
3. 등록 폼에서 행 추가·제거·위/아래 이동과 직접 순번 입력. 중복·0·음수·UINT 초과 값의 화면/서버 거부.
4. 공정명 수정 후 기존 상세의 스냅샷은 그대로이고, 새 개정은 현재 명칭을 저장하는지 확인.
5. 미사용 제품·공정은 신규 개정에서 거부하되 기존 경로의 조회가 보존되는지 확인.
6. 코드/개정 중복, 빠른 중복 저장, 없는 ID와 DB 장애에서 성공으로 처리하지 않고 안전한 오류를 제공하는지 확인.
7. 작업지시 사용 이력은 기존 테스트 fixture 또는 Mock 테스트로 확인한다. 이번 화면으로 작업지시를 생성할 수는 없다.

Mock/SQL 계약 테스트만 실행하려는 경우(이번에 실행하지 않음):

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.routing.RoutingServiceTests" --tests "com.taehyung.mes.routing.RoutingControllerTests" --tests "com.taehyung.mes.routing.RoutingMapperTests"
```

실제 MySQL 통합 테스트는 기본 비활성이다. 개발자가 기존 schema.sql을 미리 적용한 **별도 로컬 전용 스키마**와 전용 계정을 준비해야 한다. 자동 DDL/마이그레이션은 없다. 일반 DB_HOST/DB_NAME/DB_USER/DB_PASSWORD나 application.yml로 연결하지 않는다.

- MES_ROUTING_MYSQL_TEST=LOCAL_TEST_ONLY를 명시해야 한다.
- ROUTING_TEST_DB_NAME은 소문자/숫자/밑줄이며 `_routing_test`로 끝나는 별도 스키마여야 한다.
- ROUTING_TEST_DB_USER, ROUTING_TEST_DB_PASSWORD가 필수다. ROUTING_TEST_DB_PORT는 선택이며 기본 3306이다.
- 호스트는 127.0.0.1로 고정한다. 운영 DB로 향하는 터널·프록시를 지정하지 않는다.
- 각 테스트는 고유 UUID 작성자로 fixture를 만들고 그 작성자의 fixture만 FK 역순으로 정리한다. 제품/공정/Routing 테스트 데이터의 쓰기·삭제가 발생한다.

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.routing.RoutingMysqlIntegrationTests"
```

현재 Gradle test 준비 단계는 공통 DB 환경값도 요구한다. 위 통합 테스트 자체는 별도의 ROUTING_TEST_* 변수만 사용한다. 별도 실행 요청·로컬 전용 환경 확인 없이 실행하지 않는다.

## 다음 작업지시 구현

작업지시는 정확한 BOMVersion/RoutingRevision PK와 제품 일치를 검증하고 first_used_at을 같은 트랜잭션에서 설정해야 한다. 상위 헤더 → BOMVersion → RoutingRevision → Item/Process(PK순) 규약을 전체 흐름과 대조하고, 기존 BOM/이번 Routing의 잠금과 역순이 생기지 않도록 결정한다. 현재 공정명을 과거 스냅샷에 덮어쓰거나 최신 개정을 과거 지시에 재연결하지 않는다. 현재 미사용 공정이 포함된 과거 개정의 신규 지시 적용 여부는 작업지시 정책으로 별도 결정한다.
