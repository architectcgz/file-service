package com.architectcgz.file.common.context;

/**
 * Thread-local context for storing admin user information during request processing
 * Used by the authentication filter to store admin identity and IP address
 */
public class AdminContext {
    
    private static final ThreadLocal<String> adminUserHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> ipAddressHolder = new ThreadLocal<>();
    
    /**
     * Set the admin user ID for the current thread
     * 
     * @param adminUserId the admin user ID
     */
    public static void setAdminUser(String adminUserId) {
        adminUserHolder.set(adminUserId);
    }
    
    /**
     * Get the admin user ID for the current thread
     * 
     * @return the admin user ID, or null if not set
     */
    public static String getAdminUser() {
        return adminUserHolder.get();
    }
    
    /**
     * Set the IP address for the current thread
     * 
     * @param ipAddress the IP address
     */
    public static void setIpAddress(String ipAddress) {
        ipAddressHolder.set(ipAddress);
    }
    
    /**
     * Get the IP address for the current thread
     * 
     * @return the IP address, or null if not set
     */
    public static String getIpAddress() {
        return ipAddressHolder.get();
    }
    
    /**
     * Clear all context data for the current thread
     * Should be called after request processing is complete
     */
    public static void clear() {
        adminUserHolder.remove();
        ipAddressHolder.remove();
    }
}
