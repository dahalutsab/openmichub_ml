package com.brogrammers.open_mic_hub_service.exception.custom;

public class InvalidEmailException extends RuntimeException{
    public InvalidEmailException(String message){
        super(message);
    }
}
