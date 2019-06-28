
package worker;


class MyPort {
    public static enum Type {
        WIRE, BUS, AXI
    }

    String module = null;
    String port = null;
    Type type = Type.WIRE;

    public MyPort(String hier){
        this(hier, Type.WIRE);
    }
    public MyPort(String hier, Type type){
        int separator = hier.lastIndexOf("/");
        if(separator >= 0){
            module = hier.substring(0, separator);
            port = hier.substring(separator + 1);
        }
        else {
            module = "";
            port = hier;
        }
        this.type = type;
    }
    public MyPort(String module, String port, Type type){
        this.module = module;
        this.port = port;
        this.type = type;
    }

    public String getModule(){
        return module;
    }
    public String getPort(){
        return port;
    }
    public Type getType(){
        return type;
    }
    @Override
    public String toString(){
        if(module == null || module.equals(""))
            return port;
        if(port == null)
            return null;
        return module + "/" + port;
    }

    @Override
    public boolean equals(Object obj){
        if(this == obj)
            return true;

        if(obj == null || !MyPort.class.isAssignableFrom(obj.getClass()))
            return false;

        final MyPort other = this.getClass().cast(obj);
        if((this.module == null) ? other.module != null : !this.module.equals(other.module))
            return false;
        if((this.port == null) ? other.port != null : !this.port.equals(other.port))
            return false;
        if((this.type == null) ? other.type != null : !this.type.equals(other.type))
            return false;
        return true;
    }

    @Override
    public int hashCode(){
        int result = 17;
        result = 31 * result + this.module.hashCode();
        result = 31 * result + this.port.hashCode();
        result = 31 * result + this.type.hashCode();
        return result;
    }
}