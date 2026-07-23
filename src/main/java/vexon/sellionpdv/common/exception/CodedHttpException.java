package vexon.sellionpdv.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CodedHttpException extends BusinessException {

    private final HttpStatus status;
    private final String code;

    public CodedHttpException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
