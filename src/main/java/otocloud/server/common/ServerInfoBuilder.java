package otocloud.server.common;

import io.vertx.core.json.JsonObject;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/18/15.
 */
public class ServerInfoBuilder implements MessageBuilder{

    private JsonObject props = new JsonObject();


    public ServerInfoBuilder deploymentId(String deploymentId){
        props.put("deploymentId",deploymentId);
        return this;
    }

    public ServerInfoBuilder name(String name){
        props.put("serverName",name);
        return this;
    }

    public ServerInfoBuilder ip(String ip){
        props.put("serverIp",ip);
        return this;
    }

    @Override public Object create() {
        return props;
    }
}
