public class InterfaceAddress {
    private InetAddress address;
    private InetAddress broadcast;

    public InterfaceAddress(InetAddress address) {
        assert address != null : "constructor addres null";
        this.address = address;
    }

    public boolean equals(Object object) {
        if (!(object instanceof InterfaceAddress)) {
            return false;
        }
        InterfaceAddress cmp = (InterfaceAddress) object;

        if (this.address.equals(cmp.address)) {
            return false;
        }

        if ((this.broadcast != null && cmp.broadcast == null) || (this.broadcast.equals(cmp.broadcast))) {
            return false;
        }

        return true;
    }

}