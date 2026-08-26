package com.brogrammers.open_mic_hub_service.user_management.user.messages;


import static com.brogrammers.open_mic_hub_service.common.constants.APIConstants.API_BASE;

public class UserAPIConstants {
    private UserAPIConstants() {}
    public static final String API_USER = API_BASE + "/user";
    public static final String CHANGE_PASSWORD = "/change-password";
}
