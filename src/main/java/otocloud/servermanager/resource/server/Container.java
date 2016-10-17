package otocloud.servermanager.resource.server;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.resource.AbstractResource;

/**
 * 
 * @author caojj1
 *
 */
public class Container extends AbstractResource {

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put("get", new String[] { "/server/containers", "/server/nodes/:ip/containers" });
		uriMap.put("post", new String[] { "/server/nodes/:ip/containers" });
		uriMap.put("put", new String[] { "/server/containers" });
		uriMap.put("delete", new String[] { "/server/containers/:id" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.serverManager.resource.server.node.Container";
	}

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject queryParams = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		if (queryParams == null)
			queryParams = new JsonObject();
		vertx.eventBus().<JsonArray> send(ContainerDAO.ADDRESS_QUERY, queryParams, res -> {
			if (res.succeeded()) {
				JsonArray containers = res.result().body();
				message.reply(containers);
			} else {
				log.error("Query failed!", res.cause());
				message.fail(500, res.cause().getMessage());
			}
		});
	}

	@Override
	protected void post(Message<JsonObject> message) {
		JsonObject container = message.body().getJsonObject(CommonConsts.REST_CONTENT);
		vertx.eventBus().<JsonObject> send(ContainerDAO.ADDRESS_INSERT, container, res -> {
			if (res.succeeded()) {
				message.reply(res.result().body());
			} else {
				log.error("Insert failed!", res.cause());
				message.fail(500, res.cause().getMessage());
			}
		});
	}

	@Override
	protected void delete(Message<JsonObject> message) {
		// TODO
	}

}
