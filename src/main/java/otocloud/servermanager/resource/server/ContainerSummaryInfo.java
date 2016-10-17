package otocloud.servermanager.resource.server;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.resource.AbstractResource;
import otocloud.servermanager.server.container.ContainerManager;
import otocloud.servermanager.server.container.EConnectStatus;

public class ContainerSummaryInfo extends AbstractResource {

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put("get", new String[] { "/server/nodes/:ip/containers/:manage_port/summary_info",
				"/server/containers/:id/summary_info" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.servermanager.resource.server.node.container.summary-info";
	}

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject params = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		queryContainer(params, queryAres -> {
			if (queryAres.succeeded()) {
				Object queryResult = queryAres.result().body();
				JsonObject container = null;
				if (queryResult instanceof JsonArray) {
					container = ((JsonArray) queryResult).getJsonObject(0);
				} else {
					container = (JsonObject) queryResult;
				}
				Future<JsonObject> getFuture = Future.future();
				getSummaryInfo(container, getFuture);
				getFuture.setHandler(getAres -> {
					if (getAres.succeeded()) {
						message.reply(getAres.result());
					} else {
						log.error("Failed to get summary info of container!", getAres.cause());
						message.fail(500, getAres.cause().getMessage());
					}
				});
			} else {
				log.error("Failed when query container info!", queryAres.cause());
				message.fail(500, queryAres.cause().getMessage());
			}
		});
	}

	private void queryContainer(JsonObject params, Handler<AsyncResult<Message<Object>>> next) {
		if (params.containsKey("id")) {
			vertx.eventBus().<Object> send(ContainerDAO.ADDRESS_QUERY_BY_ID, Integer.parseInt(params.getString("id")),
					next);
		} else {
			vertx.eventBus().<Object> send(ContainerDAO.ADDRESS_QUERY, params, next);
		}
	}

	private void getSummaryInfo(JsonObject container, Future<JsonObject> getFuture) {
		String ip = container.getString(ContainerConsts.ATT_IP);
		Integer managePort = container.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		vertx.eventBus().<JsonArray> send(
				ContainerManager.ADDRESS_GET_CONTAINER_STATUS,
				new JsonArray().add(container),
				getAres -> {
					if (getAres.succeeded()) {
						JsonArray containersWithStatus = getAres.result().body();
						if (containersWithStatus != null && containersWithStatus.size() == 1) {
							JsonObject containerWithStatus = containersWithStatus.getJsonObject(0);
							String connectStautsStr = containerWithStatus.getJsonObject(ContainerConsts.ATT_STATUS)
									.getString(ContainerConsts.ATT_STATUS_CONNECTION);
							EConnectStatus connectStatus = EConnectStatus.valueOf(connectStautsStr);
							if (connectStatus.equals(EConnectStatus.DISCONNECTED)
									|| connectStatus.equals(EConnectStatus.UNKNOWN)) {
								getFuture.fail("The container is unconnected.[" + ip + ":" + managePort + "]");
							} else {
								this.getHttpClient().getNow(managePort, ip, "/api/summary-info", response -> {
									response.bodyHandler(body -> {
										getFuture.complete(new JsonObject(new String(body.getBytes())));
									}).exceptionHandler(t -> {
										getFuture.fail(t);
									});
								});
							}
						} else {
							getFuture.fail("Unexpected container status!");
						}
					} else {
						getFuture.fail(getAres.cause());
					}
				});

	}

}
