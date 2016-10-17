package otocloud.servermanager.server.container;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.consts.ModuleDeploymentConsts;

public class ContainerAgent {

	private static HttpClient httpClient;

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private Vertx vertx;
	private JsonObject container;

	public ContainerAgent(Vertx vertx, JsonObject container) {
		this.vertx = vertx;
		this.container = container;
	}

	private static void setHttpClient0(HttpClient httpClient) {
		if (ContainerAgent.httpClient == null) {
			synchronized (ContainerAgent.class) {
				if (ContainerAgent.httpClient == null) {
					ContainerAgent.httpClient = httpClient;
				}
			}
		}
	}

	private static HttpClient getHttpClient0() {
		if(httpClient == null) {
			synchronized (ContainerAgent.class) {
				if(httpClient == null) {
					return null;
				}
			}
		}
		return ContainerAgent.httpClient;
	}

	private HttpClient getHttpClient(HttpClientOptions options) {
		if (ContainerAgent.getHttpClient0() == null) {
			if (options == null) {
				ContainerAgent.setHttpClient0(vertx.createHttpClient());
			} else {
				ContainerAgent.setHttpClient0(vertx.createHttpClient(options));
			}
		}
		return ContainerAgent.getHttpClient0();
	}

	public void deploy(JsonArray moduleDeployments, Future<JsonArray> deployFuture) {
		/* 构造部署指令 */
		Future<JsonArray> constructFuture = Future.future();
		constructDeployInstructions(moduleDeployments, constructFuture);
		constructFuture.setHandler(constructAres -> {
			if (constructAres.succeeded()) {
				/* 发送部署指令 */
				JsonArray deployInstructions = constructAres.result();
				Future<JsonArray> sendFuture = Future.future();
				sendDeployInstructions(container, deployInstructions, sendFuture);
				sendFuture.setHandler(sendAres -> {
					if (sendAres.succeeded()) {
						log.trace("Deploy succeed!");
						JsonArray deployResults = sendAres.result();
						deployFuture.complete(deployResults);
					} else {
						deployFuture.fail(sendAres.cause());
					}
				});
			} else {
				deployFuture.fail(constructAres.cause());
			}
		});
	}

	/**
	 * 将部署信息转换为对服务器的部署指令
	 * 
	 * @param moduleDeployments
	 * @return
	 */
	private void constructDeployInstructions(JsonArray moduleDeployments, Future<JsonArray> constructFuture) {
		JsonArray deployInstructions = new JsonArray();
		moduleDeployments.forEach(moduleDeploymentObj -> {
			JsonObject moduleDeployment = (JsonObject) moduleDeploymentObj;
			JsonObject deployInstruction = new JsonObject();
			JsonObject moduleDeploymentInInstruction = new JsonObject();
			Integer moduleId = moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_MODULE_ID);
			Integer moduleVerisonId = moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_MODULE_VERSION_ID);
			moduleDeploymentInInstruction.put("module_id", moduleId);
			moduleDeploymentInInstruction.put("module_version_id", moduleVerisonId);
			moduleDeploymentInInstruction.put("name", constructServiceIdentifier(moduleDeployment));
			deployInstruction.put("module_deployment", moduleDeploymentInInstruction);
			deployInstruction.put("module_config",
					moduleDeployment.getJsonObject(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG));
			deployInstructions.add(deployInstruction);
		});
		constructFuture.complete(deployInstructions);
	}

	private String constructServiceIdentifier(JsonObject moduleDeployment) {
		String groupId = moduleDeployment.getString(ModuleDeploymentConsts.ATT_GROUP_ID);
		String artifactId = moduleDeployment.getString(ModuleDeploymentConsts.ATT_ARTIFACT_ID);
		String version = moduleDeployment.getString(ModuleDeploymentConsts.ATT_VERSION);
		String serviceName = moduleDeployment.getString(ModuleDeploymentConsts.ATT_SERVICE_NAME);
		return "otocloud_maven:" + groupId + ":" + artifactId + ":" + version + "::" + serviceName;
	}

	private void sendDeployInstructions(JsonObject container, JsonArray deployInstructions,
			Future<JsonArray> deployFuture) {
		String containerIp = container.getString(ContainerConsts.ATT_IP);
		Integer containerManagePort = container.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		HttpClientRequest req = this.getHttpClient(null).post(containerManagePort, containerIp,
				"/api/module-deployments", response -> {
					response.exceptionHandler(t -> {
						deployFuture.fail(t);
					});
					if (response.statusCode() == 200) {
						response.bodyHandler(body -> {
							JsonObject result = new JsonObject(body.toString());
							if (result.getString(CommonConsts.REST_ERROR_CODE) == null) {
								deployFuture.complete(new JsonArray(body.toString()));
							} else {
								deployFuture.fail(result.getString(CommonConsts.REST_ERROR_MESSAGE));
							}
						});
					} else {
						deployFuture.fail(response.statusMessage());
					}
				});
		req.exceptionHandler(t -> {
			log.error("Http request exception!", t);
			deployFuture.fail(t);
		});
		req.end(deployInstructions.toString());
	}
}
