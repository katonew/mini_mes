# BOM 관리 구현 및 직접 검증 안내

## 구현 범위

2026-10-26 승인 정책은 business-rules.md의 BR-07을 따른다. Controller → Service → MyBatis XML → MySQL 구조, 기존 Thymeleaf 폼 제출·공통 관리자 디자인을 사용한다.

| 화면 | GET URL | 저장 POST URL |
|---|---|---|
| BOM 목록/완제품 검색 | /boms?code=&name= | 없음 |
| 신규 BOM | /boms/new | /boms |
| 기존 BOM의 새 버전 | /boms/{bomId}/versions/new | /boms/{bomId}/versions |
| 버전 상세 | /boms/versions/{versionId} | 없음 |
| 미사용 버전 수정 | /boms/versions/{versionId}/edit | /boms/versions/{versionId} |

사용 이력 표시는 first_used_at 또는 작업지시 참조에 따른 조회 정보다. 별도 발행·활성 상태 전이는 없다. Routing·작업지시 등록·투입·실적·품질 기능은 포함하지 않는다.

## 추가·수정 파일

- 신규 Java: Bom.java, BomVersion.java, BomComponent.java, BomForm.java, BomComponentForm.java, BomRuleException.java, BomNotFoundException.java, BomMapper.java, BomService.java, BomController.java
- 신규 SQL/UI: BomMapper.xml, BOM용 list.html·form.html·detail.html, bom.js
- 신규 테스트: BomServiceTests.java, BomControllerTests.java
- 공통 수정: PageExceptionHandler.java, layout.html, error.html, mes.css, mes.js, messages.properties
- 문서: mini_mes_prd.md, business-rules.md, database-design.md, erd-design.md, 2026-10-26-bom.md, bom-verification.md
- schema.sql, 의존성, 품목 Controller/Service/Mapper는 변경하지 않았다.

## 검증 기록과 한계

- 사용자 지시로 빌드·테스트·린트·브라우저·렌더링·DB·개발서버를 실행하지 않았다. 운영 OCI DB에 접속하거나 테스트 데이터를 저장하지 않았다.
- 소스와 SQL·템플릿·테스트를 정적으로 대조했다. 런타임 성공과 실제 MySQL 동시성/롤백은 확인되지 않았다.
- 작성된 Service 테스트는 Mockito로 실제 Service 검증을 호출한다. 트랜잭션 테스트는 Spring 프록시와 모의 TransactionManager의 롤백 호출을 검증하며 실제 DB 롤백 검증이 아니다.
- Controller 테스트는 standalone MockMvc로 바인딩·리다이렉트·오류 모델을 확인하도록 작성했다. Thymeleaf 렌더링과 실제 브라우저 조작은 검증 범위에 포함되지 않는다.
- 기존 스키마에는 (bom_version_id,material_item_id) UNIQUE가 없다. 같은 버전의 모든 쓰기 경로가 Service 부모 잠금을 지켜야 한다. 직접 SQL 입력까지 중복 방지를 보장하지 않는다.
- 향후 작업지시 생성은 동일한 헤더 → 버전 → 품목 PK 순서로 잠그고 first_used_at 설정 및 지시 저장을 하나의 트랜잭션으로 처리해야 한다. 이번에는 그 기능을 구현하지 않았다.

## 개발자가 직접 확인하는 방법

운영 OCI가 아닌 격리된 개발/테스트 환경에서 기존 스키마와 MES_ACTOR_ID를 준비하고 아래 순서로 확인한다. 이 문서에 실행 결과를 기록한 것은 아니다.

1. /items에서 활성 FINISHED/EA 완제품, RAW/EA·RAW/M·RAW/G 원자재가 있는지 확인한다.
2. /boms/new에서 고유 BOM 코드와 버전 1, EA 소요량 2·M 소요량 0.125·G 소요량 0.000001을 등록한다. 상세에서 기준수량 1 EA와 각각의 보존 단위를 확인한다.
3. /boms에서 완제품 코드·명칭 검색, 빈 검색 결과, 상세 이동을 확인한다. 품목 메뉴 및 나머지 준비 중 메뉴도 확인한다.
4. 미사용 버전의 소요량 변경·신규 원자재 추가를 저장한다. 기존 행 제거·품목 교체 버튼은 제공하지 않으며 새 버전으로 대체 구성을 입력한다.
5. 같은 BOM에 같은 버전 번호 또는 동일 BOM 코드를 재등록하여 오류와 기존 데이터 유지를 확인한다.
6. 0·음수·EA 0.5·소수 7자리·정수부 13자리·중복 원자재를 입력하여 화면 검증과 서버 검증을 각각 확인한다. 화면만 통과시키는 변조 요청도 서버가 거부해야 한다.
7. 품목을 미사용으로 변경한 뒤 신규 BOM/새 버전의 선택 목록에서 제외되는지 확인한다. 기존 BOM 상세와 미사용 버전의 기존 구성은 남아 있어야 한다.
8. 같은 버전 수정 화면을 두 개 열고 첫 화면에서 변경 후 두 번째 화면을 저장하면 오래된 화면 오류가 발생하는지 확인한다. 빠른 중복 저장도 구성행을 중복 생성하지 않아야 한다.
9. 사용 후 변경 차단은 BomServiceTests.java의 first_used_at/작업지시 참조 사례로 확인한다. 이번 화면에는 작업지시 생성 기능이 없으므로 화면으로 해당 이력을 새로 만들 수 없다. 필요 시 격리된 테스트 DB의 적법한 기존 fixture로 확인한다.
10. DB 장애 시 안전한 오류, 등록·수정 실패 시 입력값 유지, MES_ACTOR_ID 누락 시 신규 INSERT 차단을 확인한다.

작성된 DB 비연결 테스트만 실행하려는 경우의 명령(이번 작업에서는 실행하지 않음):

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.bom.BomServiceTests" --tests "com.taehyung.mes.bom.BomControllerTests"
```

현재 build.gradle의 공통 test 준비 단계는 DB_NAME/DB_USER/DB_PASSWORD 환경값을 요구한다. 위 두 테스트 자체는 DB에 연결하지 않으며 MesApplicationTests는 필터에 포함하지 않는다. 전체 테스트나 앱을 실행하기 전에는 운영 OCI가 아닌 별도 환경임을 확인한다.

## 남은 업무 결정

이번 기능 구현을 막는 미확정 항목은 없다. 다단계 BOM, 손실률, 발행·활성기간은 후속 결정 범위다. 현재 화면의 폼 바인딩 보호 한도는 256행이며 확장이 필요하면 서버 요청·화면 편집 구조를 함께 검토한다.
