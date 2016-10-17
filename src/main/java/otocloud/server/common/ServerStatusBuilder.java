package otocloud.server.common;

import io.vertx.core.json.JsonObject;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/18/15.
 */
public class ServerStatusBuilder implements MessageBuilder<JsonObject>{

    private JsonObject props = new JsonObject();

    /**
     *
     * @param action should be deplpy ,undeploy or redeploy
     * @return
     */
    public ServerStatusBuilder action(String action){
        props.put(ACTION,action);
        return this;
    }

    /**
     *
     * @param status should be success or fail
     * @return
     */
    public ServerStatusBuilder status(String status){
        props.put(STATUS,status);
        return this;
    }

    @Override public JsonObject create() {
        return props;
    }
}
