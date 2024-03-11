package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.IOException;
import java.nio.channels.AlreadyConnectedException;
import java.util.HashMap;

public class TftpConnections<T> implements Connections<T> {

    HashMap<Integer, ConnectionHandler<T>> activeUserHashMap = new HashMap<>();

    public boolean canConnect(int connectionId) {
        return !activeUserHashMap.containsKey(connectionId);
    }

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        if (!canConnect(connectionId))
            throw new AlreadyConnectedException();
        activeUserHashMap.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if (!activeUserHashMap.containsKey(connectionId))
            return false;
        activeUserHashMap.get(connectionId).send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId) {
        activeUserHashMap.remove(connectionId);
    }

    //maybe illigal
    public void bCast(T msg, int ID) {
        for (int connectionID : activeUserHashMap.keySet()) {
            if (connectionID == ID) continue;
            activeUserHashMap.get(connectionID).send(msg);
        }
    }
}
