package com.atlas.search.search.exception;

import com.atlas.search.shared.exception.FieldErrorDetail;

import java.util.List;

public class SearchValidationException extends RuntimeException {

    private final List<FieldErrorDetail> errors;

    public SearchValidationException(String message, List<FieldErrorDetail> errors) {
        super(message);
        this.errors = errors;
    }

    public SearchValidationException(String field, String message) {
        super(message);
        this.errors = List.of(new FieldErrorDetail(field, message));
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }
}
