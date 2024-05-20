package bgu.spl.net.srv;

import javax.imageio.IIOException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl <T> implements Connections <T>{
    static ConcurrentHashMap<Integer, ConnectionHandler> activeClients = new ConcurrentHashMap<>();
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        activeClients.put(connectionId,handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        for (Integer id : activeClients.keySet()) {
            if(id==connectionId){
                activeClients.get(connectionId).send(msg);
                return true;
            }
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        for (Integer id : activeClients.keySet()) {
            if(id == connectionId) {
                try {
                    activeClients.get(id).close();
                    activeClients.remove(id);
                } catch (IOException e) {}
            }
        }
    }
}
