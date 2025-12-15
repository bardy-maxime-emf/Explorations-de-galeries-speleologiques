package rover.services;

import com.phidget22.Net;
import com.phidget22.PhidgetException;

public class Connection {

    private static final String SERVER_NAME = "ROVERG1";

    private String ip;
    private int port;
    private boolean connected;

    public Connection(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.connected = false;
    }

    public void seConnecter() throws PhidgetException {
        if (connected) return;
        Net.addServer(SERVER_NAME, ip, port, "", 0);
        connected = true;
    }

    public void seDeconnecter() throws PhidgetException {
        if (!connected) return;
        Net.removeServer(SERVER_NAME);
        connected = false;
    }

    public boolean isConnected() { return connected; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public void setIp(String ip) { this.ip = ip; }
    public void setPort(int port) { this.port = port; }
}
