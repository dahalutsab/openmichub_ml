package com.brogrammers.open_mic_hub_service.exception.custom;

public class InvalidPhoneFormat extends RuntimeException{
    public InvalidPhoneFormat(String message){
        super(message);
    }
}
