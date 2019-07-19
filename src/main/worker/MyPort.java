
package main.worker;

/**
 * @deprecated Only used by other depricated functions and classes.
 * @see Connections
 * @see worker.Merger#connectAll(ModuleInst mi, Connections conns)
 */
class MyPort {
	public static enum Type {
		WIRE, BUS, AXI
	}

	String module_inst = null;
	String port = null;
	Type type = Type.WIRE;
	int left = -100, right = -100;

	public MyPort(String hier) {
		this(hier, Type.WIRE);
		if (port.indexOf('[') >= 0)
			this.type = Type.BUS;
	}

	public MyPort(String hier, Type type) {
		int separator = hier.lastIndexOf("/");
		if (separator >= 0) {
			module_inst = hier.substring(0, separator);
			port = hier.substring(separator + 1);
		} else {
			module_inst = "";
			port = hier;
		}
		this.type = type;
	}

	public MyPort(String module_inst, String port, Type type) {
		this.module_inst = module_inst;
		this.port = port;
		this.type = type;
	}

	public String getModuleInst() {
		return module_inst;
	}

	public String getPort() {
		return port;
	}

	public String getPortBusName() {
		int open = port.indexOf('[');
		if (open < 0)
			return port;
		return port.substring(0, open);
	}

	public int getLeft() {
		if (left == -100)
			setLeftRight();
		return left;
	}
	public int getRight() {
		if (right == -100)
			setLeftRight();
		return right;
	}

	private void setLeftRight() {
		if (type == Type.AXI){
			left = right = -2;
			return;
		}

		int open = port.indexOf('[');
		int close = port.indexOf(']', open + 1);
		if(open < 0 && close < 0){
			left = right = -1;
			return;
		}
		if (open < 0 || close < 0){
			left = right = -3;
			return;
		}

		String indecies = port.substring(open + 1, close);
		int mid = indecies.indexOf(':');
		try {
			if (mid < 0) {
				left = right = Integer.parseInt(indecies);
			} else {
				left = Integer.parseInt(indecies.substring(0, mid));
				right = Integer.parseInt(indecies.substring(mid + 1));
			}
		} catch (NumberFormatException nfe) {
			left = right = -4;
		}
	}

	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		if (module_inst == null || module_inst.equals(""))
			return port;
		if (port == null)
			return null;
		return module_inst + "/" + port;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null || !MyPort.class.isAssignableFrom(obj.getClass()))
			return false;

		final MyPort other = this.getClass().cast(obj);
		if ((this.module_inst == null) ? other.module_inst != null : !this.module_inst.equals(other.module_inst))
			return false;
		if ((this.port == null) ? other.port != null : !this.port.equals(other.port))
			return false;
		if ((this.type == null) ? other.type != null : !this.type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + this.module_inst.hashCode();
		result = 31 * result + this.port.hashCode();
		result = 31 * result + this.type.hashCode();
		return result;
	}
}