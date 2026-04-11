package org.yhj.srim.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class ViewExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ModelAndView handleCustomException(CustomException e, HttpServletRequest request) {
        log.warn("View CustomException 발생 path={}, message={}", request.getRequestURI(), e.getMessage());

        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("error", e.getMessage());
        modelAndView.addObject("detail", e.getDetail());
        modelAndView.addObject("path", request.getRequestURI());
        return modelAndView;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletRequest request) {
        log.error("View unhandled exception 발생 path={}", request.getRequestURI(), e);

        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("error", "서버 오류가 발생했습니다.");
        modelAndView.addObject("detail", null);
        modelAndView.addObject("path", request.getRequestURI());
        return modelAndView;
    }
}
