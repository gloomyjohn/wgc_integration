package com.jjy.wgcbackend.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result implements Serializable {
    private String code;
    private String message;
    private Object data;
    public Result(String code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    public Result(String code, String message) {
        this.code = code;
        this.message = message;
    }
    public Result(String code) {
        this.code = code;
    }
    public Result() {
    }

    public static Result success() {
        return new Result("200");
    }

    public static Result fail() {
        return new Result("500");
    }

    public static Result success(Object data) {
        return new Result("200", "success", data);
    }
    public static Result fail(Object data) {
        return new Result("500", "fail", data);
    }
}
