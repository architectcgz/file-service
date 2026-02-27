package com.architectcgz.file.common.context;

/**
 * 用户上下文
 * 用于在请求处理过程中传递用户信息
 */
public class UserContext {
    
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> APP_ID = new ThreadLocal<>();
    
    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }
    
    public static String getUserId() {
        return USER_ID.get();
    }
    
    public static void setAppId(String appId) {
        APP_ID.set(appId);
    }
    
    public static String getAppId() {
        return APP_ID.get();
    }
    
    public static void clear() {
        USER_ID.remove();
        APP_ID.remove();
    }
}
