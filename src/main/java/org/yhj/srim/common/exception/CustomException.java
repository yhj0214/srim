package org.yhj.srim.common.exception;

import lombok.Getter;
import org.yhj.srim.common.exception.code.ErrorCode;

@Getter
public class CustomException extends RuntimeException{

    private final ErrorCode errorCode;
    private final String detail;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public CustomException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
