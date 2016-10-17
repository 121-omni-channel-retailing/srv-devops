package otocloud.server.management;

import static otocloud.server.common.MessageBusConstants.OTOCLOUD_SERVER_LIST_APPS;
import static otocloud.server.common.MessageBusConstants.OTOCLOUD_SERVER_LIST_SERVERS;
import static otocloud.server.common.MessageBusConstants.OTOCLOUD_SERVER_REGISTER_APP;
import static otocloud.server.common.MessageBusConstants.OTOCLOUD_SERVER_REGISTER_SERVER;
import static otocloud.server.common.MessageUtils.getServerDeployId;
import static otocloud.server.common.MessageUtils.getServerDeployIdOfApp;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.util.logging.Logger;

import otocloud.server.management.service.ServerSumaryInfoService;
import otocloud.server.management.service.frame.HttpServiceAdaptor;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/16/15.
 */
public class ServerManagementServer extends AbstractVerticle {

	private final static Logger LOGGER = Logger.getLogger(ServerManagementServer.class.getName());

	public static final int DEF_SERVER_MANAGEMENT_PORT = 9001;

	public JsonObject serverInfoRoot = new JsonObject();

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);
		loadServices(router);
		HttpServer httpServer = vertx.createHttpServer();
		httpServer.requestHandler(router::accept).listen(DEF_SERVER_MANAGEMENT_PORT);
	}

	private void loadServices(Router router) {
		loadWebSocketServices(router);
		loadHttpServices(router);
	}

	private void loadWebSocketServices(Router router) {
		BridgeOptions options = new BridgeOptions()
		// service for web client
				.addInboundPermitted(new PermittedOptions().setAddress(OTOCLOUD_SERVER_LIST_SERVERS))

				// all outbound messages are permitted
				.addOutboundPermitted(new PermittedOptions());

		router.route("/otocloud-server-eventbus/*").handler(SockJSHandler.create(vertx).bridge(options));
		EventBus eventBus = vertx.eventBus();

		// for query
		eventBus.<JsonObject> consumer(OTOCLOUD_SERVER_LIST_SERVERS, this::listServers);
		eventBus.<JsonObject> consumer(OTOCLOUD_SERVER_LIST_APPS, this::listApps);

		// for register
		eventBus.<JsonObject> consumer(OTOCLOUD_SERVER_REGISTER_SERVER, this::registerServer);
		eventBus.<JsonObject> consumer(OTOCLOUD_SERVER_REGISTER_APP, this::registerApp);
	}

	private void loadHttpServices(Router router) {
		// ServerSumaryInfoService
		router.routeWithRegex(ServerSumaryInfoService.HTTP_PATH_REGEX).handler(
				new HttpServiceAdaptor(new ServerSumaryInfoService(this)));
	}

	private void listApps(Message<JsonObject> msg) {

	}

	private void listServers(Message<JsonObject> msg) {
		msg.reply(serverInfoRoot);
	}

	private void registerApp(Message<JsonObject> msg) {
		JsonObject appRegisterInfo = msg.body();
		String serverDeployIdOfApp = getServerDeployIdOfApp(appRegisterInfo);

		JsonObject serverInfo = serverInfoRoot.getJsonObject(serverDeployIdOfApp);

		if (serverInfo != null) {
			JsonArray appList = serverInfo.getJsonArray("appRegisterInfos");
			if (appList == null) {
				appList = new JsonArray();
				serverInfo.put("appRegisterInfos", appList);
			}
			appList.add(appRegisterInfo);
		}
	}

	private void registerServer(Message<JsonObject> msg) {
		JsonObject serverRegisterInfo = msg.body();
		String serverDeployId = getServerDeployId(serverRegisterInfo);

		JsonObject serverInfo = new JsonObject();
		serverInfo.put("serverRegisterInfo", serverRegisterInfo);

		serverInfoRoot.put(serverDeployId, serverInfo);
	}

}