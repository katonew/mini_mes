# 공정 관리 구현 및 검증 안내

## 화면과 구조

- GET /processes: 공정코드·공정명 검색, 사용 여부, 빈 결과 및 등록·상세/수정 이동.
- GET /processes/new, POST /processes: 신규 등록.
- GET /processes/{processId}/edit, POST /processes/{processId}: 상세 겸 수정. 중복 상세 화면을 만들지 않는다.
- ProcessController → ProcessService → ProcessMapper/ProcessMapper.xml → manufacturing_process.
- ManufacturingProcess는 DB 조회/저장 모델, ProcessForm은 HTTP 입력 DTO다. 품목·BOM과 같은 폼 제출, 오류 모델, 성공 후 리다이렉트, 공통 CSS/mes.js를 사용한다.

## 추가·수정 파일

- 신규: ManufacturingProcess.java, ProcessForm.java, ProcessRuleException.java, ProcessNotFoundException.java, ProcessService.java, ProcessMapper.java, ProcessController.java, ProcessMapper.xml, 공정용 list.html/form.html.
- 신규 테스트: ProcessServiceTests.java, ProcessControllerTests.java, ProcessMapperTests.java.
- 공통 수정: layout.html, error.html, PageExceptionHandler.java, messages.properties. CSS/JS와 기존 품목·BOM 업무 코드는 변경하지 않는다.
- 문서: mini_mes_prd.md, business-rules.md(BR-08), database-design.md, erd-design.md, 2026-10-26-process.md, process-verification.md.
- DB 스키마와 의존성은 변경하지 않는다.

## 등록·수정 및 이력 정책

- 코드: ASCII 최대 50자, 필수·전역 유일·등록 후 고정. 명칭: 필수 200자. 설명: 선택 500자. 사용 여부: 0/1.
- 작성자는 기존 ActorProvider의 MES_ACTOR_ID로 신규 INSERT에만 저장한다. 수정 대상은 명칭·설명·사용 여부·updated_at뿐이다.
- 공정 ID·코드·생성 메타데이터와 Routing FK·순서·process_name_snapshot은 유지한다. 참조 여부와 무관하게 물리 삭제는 제공하지 않는다.
- 명칭·미사용 수정 허용 대신 참조 공정의 모든 수정을 차단하는 대안도 있으나, 현재 스키마가 명칭 스냅샷을 별도로 보존하므로 현재 기준정보만 갱신하는 방식을 적용했다.
- 설명의 과거 버전은 현재 DB에 보존 필드가 없다. 현재 설명으로 과거 설명을 주장하지 않는다.
- 현재 기능 구현에 필수적인 미확정 사항은 없다. 공정별 실적·검사, Routing 개정의 활성·발행 등은 후속 결정이며 이번에 추가하지 않는다.

## 테스트와 실행 여부

- Service: 정상 등록, 중복 사전 조회·UNIQUE 경합, 필수값/문자셋/길이, 코드 변경, 명칭·설명·사용여부 수정, 작성자, 검색/상세/미존재, 저장 실패를 검증하도록 작성했다.
- Controller: 입력 변환 오류, 성공 리다이렉트, 오류 모델, 목록/등록/수정, 404/DB 장애 503을 검증하도록 작성했다. standalone MockMvc이므로 실제 Thymeleaf HTML 렌더링 검증은 아니다.
- Mapper: 실제 Mapper XML의 UPDATE가 공정의 허용 컬럼만 변경하고 Routing/스냅샷·삭제 SQL을 포함하지 않는지 검증하도록 작성했다. 실제 DB 데이터 보존·FK·동시성·롤백 검증을 대체하지 않는다.
- 사용자 지시로 빌드·테스트·린트·브라우저·렌더링·개발서버·DB 실행은 하지 않았다. 운영 OCI DB에 접근하거나 테스트 데이터를 저장하지 않았다. 정적 검토만 수행했으며 동작/테스트 통과는 확인되지 않았다.

## 개발자가 직접 검증할 순서

격리된 개발/테스트 환경에서 수행한다. 운영 OCI 환경은 사용하지 않는다.

1. 공정 메뉴를 열고 /processes 목록·빈 결과·코드/명칭 검색을 확인한다. 품목/BOM 메뉴의 활성 표시도 함께 확인한다.
2. 고유 코드 ASSEMBLY, 명칭 모듈 조립, 선택 설명, 사용 여부로 등록한다. 설명 없이도 등록되어야 한다.
3. 같은 코드 중복, 필수값 누락, 51자 코드·201자 명칭·501자 설명, 미정의 사용 여부를 서버에서도 거절하는지 확인한다.
4. 수정 화면에서 명칭·설명·사용 여부를 바꾸고, 코드 변조 요청을 거절하는지 확인한다. 작성자가 변경되지 않아야 한다.
5. 존재하지 않는 ID에서 404 안내, DB 장애 시 안전한 오류, 저장 오류 시 입력 유지 여부를 확인한다.
6. 별도 테스트 DB에 유효한 Routing fixture가 있다면 공정 수정 전후 Step의 process_id/process_name_snapshot/step_no가 그대로인지 확인한다. 이번 기능으로 Routing fixture를 새로 만들 수는 없다.
7. 신규 Routing 구성은 후속 구현에서 미사용 공정 선택을 서버에서 차단해야 한다. 기존 개정의 조회·사용을 공정 현재 활성값만으로 임의 차단하지 않는다.

실행을 별도로 요청할 경우 사용할 공정 관련 DB 비연결 테스트 명령(이번 작업에서 실행하지 않음):

```powershell
.\gradlew.bat test --tests "com.taehyung.mes.process.ProcessServiceTests" --tests "com.taehyung.mes.process.ProcessControllerTests" --tests "com.taehyung.mes.process.ProcessMapperTests"
```

현재 Gradle test 준비 단계는 DB 환경변수를 요구한다. 위 세 테스트는 DB에 연결하지 않으며 전체 SpringBootTest를 실행하는 명령이 아니다.
