package com.brogrammers.open_mic_hub_service.util.messages;

public class ResponseMessageUtil {
    private ResponseMessageUtil() {}

    public static String createdSuccessfully(String entity) {
        return entity + " created successfully";
    }

    public static String allFetchedSuccessfully(String entity) {
        return "All " + entity + " fetched successfully";
    }

    public static String fetchedSuccessfully(String entity) {
        return entity + " fetched successfully";
    }

    public static String updatedSuccessfully(String entity) {
        return entity + " updated successfully";
    }

    public static String deletedSuccessfully(String entity) {
        return entity + " deleted successfully";
    }
}