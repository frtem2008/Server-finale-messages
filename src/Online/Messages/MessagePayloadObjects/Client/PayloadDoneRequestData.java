package Online.Messages.MessagePayloadObjects.Admin;

import Online.Messages.MessagePayload;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PayloadDoneRequestData extends MessagePayload {
    public PayloadDoneRequestData() {
        // some invalid value
    }
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.flush();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
    }
    // TODO: 02.05.2023 EQUALS AND HASH CODE
}

