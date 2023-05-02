package Online;

import Online.MessagePayloadObjects.MessagePayload;
import Online.MessagePayloadObjects.PayloadInvalid;
import Online.MessagePayloadObjects.PayloadStringData;

public enum MessageType {
    INVALID(PayloadInvalid.class), //for new non-created messages

    ERROR(PayloadStringData.class),       //reaction on invalid message
    INFO(PayloadStringData.class), //some information

    ;


    final Class<? extends MessagePayload> payload;

    MessageType(Class<? extends MessagePayload> payload) {
        this.payload = payload;
    }
}
