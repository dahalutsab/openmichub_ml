package com.brogrammers.open_mic_hub_service.user_management.user.messages;

public class UserSwaggerDocumentationMessage {
    private UserSwaggerDocumentationMessage() {}

    public static final String ADD_USER_SUMMARY = "Add a new user";
    public static final String ADD_USER_DESCRIPTION = "Submit a request to add a new user";

    public static final String GET_ALL_SCHOOL_USERS_SUMMARY = "Get all school users";
    public static final String GET_ALL_SCHOOL_USERS_DESCRIPTION = "Fetch a paginated list of all school users";

    public static final String GET_USER_BY_ID_SUMMARY = "Get user by ID";
    public static final String GET_USER_BY_ID_DESCRIPTION = "Fetch the details of a user by their ID";

    public static final String UPDATE_USER_SUMMARY = "Update user details";
    public static final String UPDATE_USER_DESCRIPTION = "Submit a request to update user details";

    public static final String DELETE_USER_SUMMARY = "Delete user";
    public static final String DELETE_USER_DESCRIPTION = "Submit a request to delete a user by their ID";

    public static final String CHANGE_PASSWORD_SUMMARY = "Change user password";
    public static final String CHANGE_PASSWORD_DESCRIPTION = "Submit a request to change the user password";

    public static final String GET_LOGGED_IN_USER_SUMMARY = "Get logged in user";
    public static final String GET_LOGGED_IN_USER_DESCRIPTION = "Fetch the details of the logged in user";

}