package rover.services;
import com.phidget22.*;

public class Connection {
    private String ip;
    private int port;
    private  String password;

    public Connection(String ip, int port, String password){ {
        this.ip = ip;
        this.port = port;
        this.password = "";

    }

    // Methode permettant de se connecter a un rover
    public static Connection seConnecter(){
        try {
            Net.enableServerDiscovery(ServerType.DEVICE);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    // Methode permettant de se deconnecter a un rover
    public static void seDeconnecter(){
        try {
            
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }




    
}
