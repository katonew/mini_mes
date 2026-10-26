package com.taehyung.mes.process;

public class ProcessNotFoundException extends RuntimeException {
    public ProcessNotFoundException() { super("요청한 공정을 찾을 수 없습니다."); }
}
