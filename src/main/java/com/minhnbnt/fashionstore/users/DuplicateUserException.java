package com.minhnbnt.fashionstore.users;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String authId) {
        super("User already exists for auth id: " + authId);
    }
}
