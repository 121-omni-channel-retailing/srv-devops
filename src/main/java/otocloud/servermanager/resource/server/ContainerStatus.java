package otocloud.servermanager.resource.server;

import static otocloud.servermanager.resource.IMethodsConst.GET;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.resource.AbstractResource;
import otocloud.servermanager.server.container.ContainerManager;

public class ContainerStatus extends AbstractResource {

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put(GET, new String[] { "/server/container_status", "/server/containers/:containerId/container_status" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.serverManager.resource.server.node.Container.status";
	}

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject params = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		queryContainers(
				params,
				queryAres -> {
					if (queryAres.succeeded()) {
						JsonArray containers = queryAres.result();
						vertx.eventBus().<JsonArray> send(ContainerManager.ADDRESS_GET_CONTAINER_STATUS, containers,
								getStatusAres -> {
									if (getStatusAres.succeeded()) {
										JsonArray containersWithStatus = getStatusAres.result().body();
										message.reply(containersWithStatus);
									} else {
										log.error("Get container status failed!", getStatusAres.cause());
										message.fail(500, getStatusAres.cause().getMessage());
									}
								});
					} else {
						log.error("Query container failed!", queryAres.cause());
						message.fail(500, queryAres.cause().getMessage());
					}
				});
	}

	private void queryContainers(JsonObject params, Handler<AsyncResult<JsonArray>> next) {
		if (params.containsKey("containerId")) {
			vertx.eventBus().<JsonObject> send(ContainerDAO.ADDRESS_QUERY_BY_ID,
					Integer.parseInt(params.getString("containerId")), queryAres -> {
						if (queryAres.succeeded()) {
							JsonObject container = queryAres.result().body();
							next.handle(Future.succeededFuture(new JsonArray().add(container)));
						} else {
							next.handle(Future.failedFuture(queryAres.cause()));
						}
					});
		} else {
			vertx.eventBus().<JsonArray> send(ContainerDAO.ADDRESS_QUERY_ALL, new JsonObject(), queryAres -> {
				if (queryAres.succeeded()) {
					JsonArray containers = queryAres.result().body();
					next.handle(Future.succeededFuture(containers));
				} else {
					next.handle(Future.failedFuture(queryAres.cause()));
				}
			});
		}
	}

}
