package com.taehyung.mes.quality;

class QualityInspectionRequestExistsException extends RuntimeException {
    QualityInspectionRequestExistsException(Throwable cause) { super("Quality inspection receipt already exists.", cause); }
}
