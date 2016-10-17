package otocloud.server.common;

import io.vertx.core.json.JsonObject;
import otocloud.server.common.MessageBuilder;
import otocloud.server.common.ServerInfoBuilder;
import otocloud.server.common.ServerStatusBuilder;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/18/15.
 */
public class RegisterServerInfoBuilder implements MessageBuilder<JsonObject> {

    private final ServerInfoBuilder serverInfoBuilder;
    private final ServerStatusBuilder serverStatusBuilder;

    public RegisterServerInfoBuilder(){
        serverInfoBuilder = new ServerInfoBuilder();
        serverStatusBuilder = new ServerStatusBuilder();
    }

    public ServerInfoBuilder serverInfo(){
        return serverInfoBuilder;
    }

    public ServerStatusBuilder serverStatus(){
        return serverStatusBuilder;
    }

    @Override public JsonObject create() {
        return new JsonObject().put("serverInfo",serverInfoBuilder.create())
            .put("statusInfo",serverStatusBuilder.create());
    }
}
