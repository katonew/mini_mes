package com.taehyung.mes.lot;

/** Receipt INSERT의 UNIQUE 충돌만 트랜잭션 밖으로 전달한다. */
class MaterialReturnRequestExistsException extends RuntimeException {
    MaterialReturnRequestExistsException(Throwable cause) { super("Receipt request already exists.", cause); }
}
