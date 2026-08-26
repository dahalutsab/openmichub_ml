package com.brogrammers.open_mic_hub_service.exception.custom;

public class InvalidPasswordException extends RuntimeException{
    public InvalidPasswordException(String message){
        super(message);
    }
}
