package otocloud.servermanager.resource.server;

import static otocloud.servermanager.resource.IMethodsConst.POST;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.resource.AbstractResource;
import otocloud.servermanager.server.container.ContainerManager;

public class ContainerConnection extends AbstractResource {

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put(POST, new String[] { "/server/container_connections" });
		// uriMap.put(DELETE, new String[] { "/server/containerConnections" });
		return uriMap;
	}

	@Override
	protected void post(Message<JsonObject> message) {
		JsonObject connectionInfo = message.body().getJsonObject(CommonConsts.REST_CONTENT);
		vertx.eventBus().send(ContainerManager.ADDRESS_PROCESS_CONNECT, connectionInfo);
		message.reply(new JsonObject().put("status", "processing"));
	}

	@Override
	protected String address() {
		return "otocloud.servermanager.resource.server.container-connection";
	}

}
