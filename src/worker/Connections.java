
package worker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import worker.MyPort;

public class Connections {

    Map<MyPort, Set<MyPort>> conn = null;

    public Connections(){
        conn = new HashMap<>();
    }

    public void addConnection(String driver_port, String sink_port){
        MyPort from = new MyPort(driver_port);
        MyPort to = new MyPort(sink_port);

        if(!conn.containsKey(from))
            conn.put(from, new HashSet<>());
        conn.get(from).add(to);
    }
    public boolean removeConnection(String driver_port, String sink_port){
        MyPort from = new MyPort(driver_port);
        MyPort to = new MyPort(sink_port);

        if(!conn.containsKey(from))
            return false;
        return conn.get(from).remove(to);
    }
}