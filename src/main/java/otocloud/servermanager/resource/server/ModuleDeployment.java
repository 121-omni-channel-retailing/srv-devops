package otocloud.servermanager.resource.server;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.consts.ModuleDeploymentConsts;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.dao.ModuleDeploymentDAO;
import otocloud.servermanager.resource.AbstractResource;
import otocloud.servermanager.server.container.ContainerAgent;

public class ModuleDeployment extends AbstractResource {

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put("get", new String[] { "/server/containers/:containerId/module_deployments" });
		uriMap.put("post", new String[] { "/server/containers/:containerId/module_deployments" });
		uriMap.put("delete", new String[] { "/server/module_deployments/:moduleDeploymentId" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.servermanager.resource.server.module-deployment";
	}

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject queryParams = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		String containerId = queryParams.getString("containerId");
		queryModuleDeployments(containerId, ares -> {
			if (ares.succeeded()) {
				message.reply(ares.result().body());
			} else {
				log.error("Query module deployments failed!", ares.cause());
				message.fail(500, ares.cause().toString());
			}
		});

	}

	/**
	 * 部署
	 */
	@Override
	protected void post(Message<JsonObject> message) {
		JsonObject params = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		JsonArray deployContent = message.body().getJsonArray(CommonConsts.REST_CONTENT);
		String containerId = params.getString("containerId");
		/* 查询容器 */
		queryContainerById(Integer.parseInt(containerId), containerQryAres -> {
			if (containerQryAres.succeeded()) {
				JsonObject container = containerQryAres.result().body();
				JsonArray moduleDeploymentsToInsert = constructModuleDeployments(container, deployContent);
				/* 记录部署信息 */
				insertModuleDeployments(moduleDeploymentsToInsert, ares -> {
					if (ares.succeeded()) {
						JsonArray moduleDeployments = ares.result().body();
						ContainerAgent containerAgent = new ContainerAgent(vertx, container);
						Future<JsonArray> deployFuture = Future.future();
						containerAgent.deploy(moduleDeployments, deployFuture);
						deployFuture.setHandler(deployAres -> {
							if (deployAres.succeeded()) {
								if (deployAres.succeeded()) {
									log.trace("Deploy succeed!");
									JsonArray deployResults = deployAres.result();
									// TODO 更新部署状态
								message.reply(deployResults);
							} else {
								log.error("Deploy failed!", deployAres.cause());
								message.fail(500, deployAres.cause().getMessage());
							}
						}
					})	;
					} else {
						log.error("Insert failed!", ares.cause());
						message.fail(500, ares.cause().getMessage());
					}
				});
			} else {
				log.error("Query Failed!", containerQryAres.cause());
				message.fail(500, containerQryAres.cause().getMessage());
			}
		});
	}

	private void queryContainerById(Integer containerId, Handler<AsyncResult<Message<JsonObject>>> queryHandler) {
		vertx.eventBus().<JsonObject> send(ContainerDAO.ADDRESS_QUERY_BY_ID, containerId, queryHandler);
	}

	/**
	 * 将客户端部署请求转换为部署信息
	 * 
	 * @param container
	 * @param deployContent
	 * @return
	 */
	private JsonArray constructModuleDeployments(JsonObject container, JsonArray deployContent) {
		JsonArray moduleDeployments = new JsonArray();
		Integer containerId = container.getInteger(ContainerConsts.ATT_ID);
		deployContent.forEach(deployInfoObj -> {
			JsonObject deployInfo = (JsonObject) deployInfoObj;
			JsonObject moduleDeployment = new JsonObject();
			moduleDeployment.put(ModuleDeploymentConsts.ATT_CONTAINER_ID, containerId);
			moduleDeployment.put(ModuleDeploymentConsts.ATT_MODULE_ID,
					deployInfo.getInteger(ModuleDeploymentConsts.ATT_MODULE_ID));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_MODULE_VERSION_ID,
					deployInfo.getInteger(ModuleDeploymentConsts.ATT_MODULE_VERSION_ID));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG,
					deployInfo.getJsonObject(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_GROUP_ID,
					deployInfo.getString(ModuleDeploymentConsts.ATT_GROUP_ID));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_ARTIFACT_ID,
					deployInfo.getString(ModuleDeploymentConsts.ATT_ARTIFACT_ID));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_VERSION,
					deployInfo.getString(ModuleDeploymentConsts.ATT_VERSION));
			moduleDeployment.put(ModuleDeploymentConsts.ATT_SERVICE_NAME,
					deployInfo.getString(ModuleDeploymentConsts.ATT_SERVICE_NAME));
			moduleDeployments.add(moduleDeployment);
		});
		return moduleDeployments;
	}

	private void insertModuleDeployments(JsonArray moduleDeployments, Handler<AsyncResult<Message<JsonArray>>> handler) {
		vertx.eventBus().<JsonArray> send(ModuleDeploymentDAO.ADDRESS_BATCH_INSERT, moduleDeployments, handler);
	}

	private void queryModuleDeployments(String containerID, Handler<AsyncResult<Message<Object>>> handler) {
		JsonObject queryParams = new JsonObject().put(ModuleDeploymentConsts.ATT_CONTAINER_ID,
				Integer.parseInt(containerID));
		vertx.eventBus().send(ModuleDeploymentDAO.ADDRESS_QUERY, queryParams, handler);
	}

	@Override
	protected void delete(Message<JsonObject> message) {
		JsonObject queryParams = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		String moduleDeploymentIdStr = queryParams.getString("moduleDeploymentId");
		Integer moduleDeploymentId = Integer.parseInt(moduleDeploymentIdStr);
		vertx.eventBus().<JsonObject> send(ModuleDeploymentDAO.ADDRESS_QUERY_BY_ID, moduleDeploymentId, queryAres -> {
			if (queryAres.succeeded()) {
				JsonObject moduleDeployment = queryAres.result().body();
				Integer containerId = moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_CONTAINER_ID);
				deleteModuleDeployment(moduleDeploymentId, deleteAres -> {
					if (deleteAres.succeeded()) {
						queryContainerById(containerId, containerQryAres -> {
							if (containerQryAres.succeeded()) {
								JsonObject container = containerQryAres.result().body();
								Future<JsonObject> undeployFuture = Future.future();
								sendUndeployInstruction(container, moduleDeployment, undeployFuture);
								undeployFuture.setHandler(undeployAres -> {
									if (undeployAres.succeeded()) {
										message.reply(undeployAres.result());
									} else {
										log.error("Query container failed!", undeployAres.cause());
										message.fail(500, undeployAres.cause().getMessage());
									}
								});
							} else {
								log.error("Query container failed!", containerQryAres.cause());
								message.fail(500, containerQryAres.cause().getMessage());
							}
						});
					} else {
						log.error("Delete module-deployment failed!", deleteAres.cause());
						message.fail(500, deleteAres.cause().getMessage());
					}
				});
			} else {
				log.error("Query module-deployment failed!", queryAres.cause());
				message.fail(500, queryAres.cause().getMessage());
			}
		});
	}

	private void sendUndeployInstruction(JsonObject container, JsonObject moduleDeployment,
			Future<JsonObject> undeployFuture) {
		String containerIp = container.getString(ContainerConsts.ATT_IP);
		Integer containerManagePort = container.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		String serviceName = moduleDeployment.getString(ModuleDeploymentConsts.ATT_SERVICE_NAME);
		HttpClientRequest req = this.getHttpClient().delete(containerManagePort, containerIp,
				"/api/module-deployments/" + serviceName, response -> {
					response.exceptionHandler(t -> {
						undeployFuture.fail(t);
					});
					if (response.statusCode() == 200) {
						response.bodyHandler(body -> {
							JsonObject result = new JsonObject(body.toString());
							if (result.getString(CommonConsts.REST_ERROR_CODE) == null) {
								undeployFuture.complete(new JsonObject(new String(body.getBytes())));
							} else {
								undeployFuture.fail(result.getString(CommonConsts.REST_ERROR_MESSAGE));
							}
						});
					} else {
						undeployFuture.fail(response.statusMessage());
					}
				});
		req.exceptionHandler(t -> {
			log.error("Http request exception!", t);
			undeployFuture.fail(t);
		});
		req.end();
	}

	private void deleteModuleDeployment(Integer moduleDeploymentId, Handler<AsyncResult<Message<UpdateResult>>> handler) {
		vertx.eventBus().send(ModuleDeploymentDAO.ADDRESS_DELETE_BY_ID, moduleDeploymentId, handler);
	}
}
