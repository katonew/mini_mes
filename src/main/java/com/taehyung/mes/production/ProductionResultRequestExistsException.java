package com.taehyung.mes.production;

class ProductionResultRequestExistsException extends RuntimeException {
    ProductionResultRequestExistsException(Throwable cause) { super("Production result receipt already exists.", cause); }
}
