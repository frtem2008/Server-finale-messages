package Online.Messages.MessagePayloadObjects.ClientMessagesPayloadObjects;

import Online.ClientRoot;
import Online.Messages.MessagePayload;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PayloadLoginData extends MessagePayload {
    public int id;
    public ClientRoot root;

    public PayloadLoginData() {
        id = 0;
        root = ClientRoot.UNAUTHORIZED;
    }

    public PayloadLoginData(int id, ClientRoot root) {
        this.id = id;
        this.root = root;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(id);
        out.writeInt(root.ordinal());
        out.flush();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        id = in.readInt();
        root = ClientRoot.values()[in.readInt()];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PayloadLoginData that = (PayloadLoginData) o;

        if (id != that.id) return false;
        return root == that.root;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (root != null ? root.hashCode() : 0);
        return result;
    }
}
