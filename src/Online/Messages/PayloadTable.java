package Online.Messages.MessagePayloadObjects;

import Online.Messages.ReadFunctions;

import java.util.HashMap;

import static Online.Messages.ReadFunctions.fromClass;

public class PayloadTable {
    public static final HashMap<Class<? extends MessagePayload>, ReadFunctions> payloadFunctionsMap = new HashMap<>();

    static {
        try {
            payloadFunctionsMap.put(PayloadInvalid.class, fromClass(PayloadInvalid.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

}
