package otocloud.server.management.service;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import otocloud.server.management.ServerManagementServer;
import otocloud.server.management.service.frame.IHttpAdapableService;

public class ServerSumaryInfoService implements IHttpAdapableService {

	public static final String HTTP_PATH_REGEX = "/api/servers/([^/]*?)/summaryinfo[/]?";
	public static final String WEBSOCKET_ADDESS_REGEX = "otocloud.server.listServers";

	private static final String PARAM_KEY_REQUEST = "request";
	private static final String PARAM_KEY_SERVER_ID = "serverId";

	private ServerManagementServer serverManager;

	public ServerSumaryInfoService(ServerManagementServer serverManager) {
		this.serverManager = serverManager;
	}

	@Override
	public void handle(Map<String, Object> paramters) {
		HttpServerRequest req = (HttpServerRequest) paramters.get(PARAM_KEY_REQUEST);
		String serverId = (String) paramters.get(PARAM_KEY_SERVER_ID);
		JsonObject serverInfo = serverManager.serverInfoRoot.getJsonObject(serverId);
		if (serverInfo != null) {
			int appAmount = 0;
			JsonArray jsonArray = serverInfo.getJsonArray("appRegisterInfos");
			if (jsonArray != null)
				appAmount = jsonArray.size();

			JsonObject vertxInfo = new JsonObject();
			vertxInfo.put("id", "master");
			vertxInfo.put("name", "Master Vertx");
			vertxInfo.put("eventLoop", 0);
			vertxInfo.put("systemModules", 0);
			vertxInfo.put("appModules", appAmount);
			vertxInfo.put("eventBusConsumers", 0);

			JsonArray vertxInfoList = new JsonArray();
			vertxInfoList.add(vertxInfo);

			JsonObject serverSummaryInfo = new JsonObject();
			serverSummaryInfo.put("id", serverId);
			serverSummaryInfo.put("vertxInfoList", vertxInfoList);
			req.response().putHeader("content-type", "application/json").end(serverSummaryInfo.toString());
		} else {
//			req.response().setStatusCode(404).end("Data not found!-_-");
			// TODO just for test
			JsonObject vertxInfo = new JsonObject();
			vertxInfo.put("id", "master");
			vertxInfo.put("name", "Master Vertx");
			vertxInfo.put("eventLoop", 9999);
			vertxInfo.put("systemModules", 9999);
			vertxInfo.put("appModules", 9999);
			vertxInfo.put("eventBusConsumers", 9999);

			JsonArray vertxInfoList = new JsonArray();
			vertxInfoList.add(vertxInfo);

			JsonObject serverSummaryInfo = new JsonObject();
			serverSummaryInfo.put("id", serverId);
			serverSummaryInfo.put("vertxInfoList", vertxInfoList);
			req.response().putHeader("content-type", "application/json").end(serverSummaryInfo.toString());
		}
	}

	@Override
	public Map<String, Object> extractParameters(RoutingContext routingContext) {
		Map<String, Object> parameterMap = new HashMap<String, Object>();

		parameterMap.put(PARAM_KEY_REQUEST, routingContext.request());

		String reqPath = routingContext.request().path();
		Matcher matcher = Pattern.compile(HTTP_PATH_REGEX).matcher(reqPath);
		if (matcher.matches()) {
			String serverId = matcher.group(1);
			parameterMap.put(PARAM_KEY_SERVER_ID, serverId);
		}
		return parameterMap;
	}

}
