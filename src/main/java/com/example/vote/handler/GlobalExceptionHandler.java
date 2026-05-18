package com.example.vote.handler;

import com.example.vote.exception.BusinessException;
import com.example.vote.util.BoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handleBusinessException(BusinessException ex) {
        log.warn("Business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        BoUtil bo = BoUtil.getDefaultFalseBo();
        bo.setCode(ex.getErrorCode());
        bo.setMsg(ex.getMessage());
        return ResponseEntity.badRequest().body(bo.toString());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException ex) {
        BoUtil bo = BoUtil.getDefaultFalseBo();
        bo.setCode("INVALID_CREDENTIALS");
        bo.setMsg("Invalid email or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(bo.toString());
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<String> handleUsernameNotFound(UsernameNotFoundException ex) {
        BoUtil bo = BoUtil.getDefaultFalseBo();
        bo.setCode("USER_NOT_FOUND");
        bo.setMsg(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(bo.toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        BoUtil bo = BoUtil.getDefaultFalseBo();
        bo.setCode("INVALID_ARGUMENT");
        bo.setMsg(ex.getMessage());
        return ResponseEntity.badRequest().body(bo.toString());
    }
}
