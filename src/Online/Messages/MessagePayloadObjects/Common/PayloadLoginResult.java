package Online.Messages.MessagePayloadObjects.ServerMessagesPayloadObjects;

import Online.ClientRoot;
import Online.Messages.MessagePayload;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PayloadLoginResult extends MessagePayload {
    public Result result;
    public int loginId;

    public PayloadLoginResult() {
        this.result = null;
        this.loginId = 0;
    }

    public PayloadLoginResult(Result result, int loginId) {
        this.result = result;
        this.loginId = loginId;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        assert result != null;
        out.writeInt(result.ordinal());
        out.writeInt(loginId);
        out.flush();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        int resNum = in.readInt();
        if (resNum > Result.values().length)
            throw new IllegalArgumentException("Received login result with enum index: " + resNum);
        result = Result.values()[resNum];
        loginId = in.readInt();
    }

    public enum Result {
        REG_FAILED_EXISTS,
        LOG_FAILED_ONLINE,
        LOG_FAILED_FREE,
        REG_SUCCESS,
        LOG_SUCCESS,
    }
}
