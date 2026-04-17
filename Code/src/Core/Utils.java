package Core;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.Enumeration;

public class Utils {


    /*
     * Calculates the hash of a file
     * The hash is calculated using the SHA-256 algorithm   
     */
    public static byte[] calculateFileHash(String filePath) {
        try {
            byte[] fileContents = java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(filePath)
            );

            return MessageDigest.getInstance("SHA-256").digest(fileContents);

        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to calculate file hash for: " + filePath,
                e
            );
        }
    }

    // Checks if a port is valid
    public static Boolean isValidPort(int port) {
        return port > 8080 && port <= 10000;
    }

    // Checks if a node ID is valid
    public static Boolean isValidID(int id) {
        return id > 0 && id <= 41070;
    }


    // Returns the local IP address of the computer that the program is running on     
    public static InetAddress getLocalIPAddress() throws Exception {
        
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address) { // Get IPv4 address
                        return inetAddress;
                    }
                }
            }
        }
    
        return null; 
    }
}
