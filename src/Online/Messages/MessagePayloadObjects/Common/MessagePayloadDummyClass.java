package Online.Messages.MessagePayloadObjects;

import Online.ClientRoot;
import Online.Messages.MessagePayload;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MessagePayloadDummyClass extends MessagePayload {
    public int data;

    public MessagePayloadDummyClass() {
        data = 0;
        // some invalid value
    }

    public MessagePayloadDummyClass(int data) {
        this.data = data;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(data);
        out.flush();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        data = in.readInt();
    }
    // TODO: 02.05.2023 EQUALS AND HASH CODE
}
