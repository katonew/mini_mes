# AI Failure - 작업지시 폼의 선택값과 버튼 상태 보존

## AI Suggestion

작업지시 등록 오류 화면의 BOM/Routing select를 조회된 후보만으로 렌더링하고, 업무상 disabled인 저장 버튼에도 공통 mes.js pageshow 복원 처리를 그대로 적용했다.

## Problem

저장 및 선택 조회가 함께 실패하면 제출 버전 ID가 DTO에는 있어도 화면 option에서 사라졌다. 공통 pageshow는 모든 저장 버튼을 활성화하므로 비활성 제품의 업무상 disabled 표시도 해제했다.

## Risk

사용자가 선택값을 잃거나 잘못 활성화된 버튼으로 재시도할 수 있다. Service 재검증이 있어 활성 검증을 우회하는 DB 저장 결함으로 확인된 것은 아니다.

## Verification

독립 읽기 전용 리뷰에서 Controller 모델·Thymeleaf option·mes.js pageshow 코드를 대조하여 확인했다. 실제 브라우저·테스트·DB 실행으로 재현한 결과는 아니다.

## Fix

조회되지 않는 제출 ID를 대체 option으로 보존하고 오류 화면 모델 테스트를 추가했다. 작업지시 폼의 registrationAllowed 값을 별도로 보존하여 workorder.js가 pageshow 후 저장 가능 상태를 복원하도록 했다. 기존 공통 mes.js는 변경하지 않았다. 모든 테스트는 미실행이다.
