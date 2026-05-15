package com.snail.snail_race.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResultNotFoundException extends RuntimeException {
    public ResultNotFoundException(Long videoId) {
        super("Result not found for video: " + videoId);
    }
}
