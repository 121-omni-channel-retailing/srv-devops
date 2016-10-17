package otocloud.servermanager.server.container;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;

import java.util.HashMap;
import java.util.Map;

import otocloud.common.MultipleFutures;
import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.consts.ModuleDeploymentConsts;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.dao.ModuleDeploymentDAO;
import otocloud.servermanager.util.ContainerUtils;


/**
 * Server容器管理器
 * 
 * TODO stop方法
 * 
 * @author caojj1
 *
 */
public class ContainerManager extends AbstractVerticle {

	public static final String ADDRESS = "otocloud.servermanager.container_manager";
	public static final String ADDRESS_PROCESS_CONNECT = ADDRESS + ".processConnect";
	public static final String ADDRESS_GET_CONTAINER_STATUS = ADDRESS + ".container.status.get";

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private Map<String, IContainerMonitor> monitors = new HashMap<String, IContainerMonitor>();

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		reConnectContainers(ares -> {
			if (ares.succeeded()) {
				log.info("Connect to Containers successed!");
			} else {
				log.warn("Connection to at least on container failed!", ares.cause());
			}
		});
		registerHandler();
		startFuture.complete();
	}

	/**
	 * 注册事件监听
	 */
	private void registerHandler() {
		vertx.eventBus().consumer(ADDRESS_PROCESS_CONNECT, this::processContainerConnect);
		vertx.eventBus().consumer(ADDRESS_GET_CONTAINER_STATUS, this::handleGetContainerStatus);
	}

	/**
	 * 运维服务重启后，重新连接应用服务器
	 * 
	 * @param resultHandler
	 */
	private void reConnectContainers(Handler<AsyncResult<Void>> resultHandler) {
		// TODO 对查询操作加锁，以避免集群启动时对资源的浪费
		vertx.eventBus().send(
				ContainerDAO.ADDRESS_QUERY_ALL,
				new JsonObject(),
				queryAres -> {
					if (queryAres.succeeded()) {
						JsonArray containers = (JsonArray) queryAres.result().body();
						if (containers != null && containers.size() > 0) {
							MultipleFutures multiFutures = new MultipleFutures(resultHandler);
							for (Object containerObj : containers.getList()) {
								multiFutures.add(aFuture -> {
									JsonObject container = (JsonObject) containerObj;
									/* 加锁并连接容器 */
									Future<JsonObject> lockAndConnectFuture = Future.future();
									lockAndConnectContainer(container, lockAndConnectFuture);
									lockAndConnectFuture.setHandler(lockAndConnectAres -> {
										if (lockAndConnectAres.succeeded()) {
											/* TODO 同步应用服务器状态 */
											log.trace("Successful connected to container["
													+ container.getString(ContainerConsts.ATT_IP) + ":"
													+ container.getInteger(ContainerConsts.ATT_MONITOR_PORT) + "]");
											aFuture.complete();
										} else {
											aFuture.fail(lockAndConnectAres.cause());
										}
									});
								});
							}
							multiFutures.start();
						}
					} else {
						resultHandler.handle(Future.failedFuture(queryAres.cause()));
					}
				});
	}

	/**
	 * 容器重启后重新部署
	 * 
	 * @param container
	 * @param deployFuture
	 */
	private void deployContainer(JsonObject container, Future<JsonArray> deployFuture) {
		vertx.eventBus().<JsonArray> send(
				ModuleDeploymentDAO.ADDRESS_QUERY,
				new JsonObject().put(ModuleDeploymentConsts.ATT_CONTAINER_ID,
						container.getInteger(ContainerConsts.ATT_ID)), queryAres -> {
					if (queryAres.succeeded()) {
						JsonArray moduleDeployments = queryAres.result().body();
						ContainerAgent containerAgent = new ContainerAgent(vertx, container);
						containerAgent.deploy(moduleDeployments, deployFuture);
					} else {
						deployFuture.fail(queryAres.cause());
					}
				});

	}

	/**
	 * 处理容器连接
	 * 
	 * @param message
	 */
	private void processContainerConnect(Message<JsonObject> message) {
		JsonObject connectionInfo = message.body();
		Future<JsonObject> lockAndConnectFuture = Future.future();
		lockAndConnectContainer(connectionInfo, lockAndConnectFuture);
		lockAndConnectFuture.setHandler(ares -> {
			if (ares.succeeded()) {
				JsonObject container = ares.result();
				Future<JsonArray> deployFuture = Future.future();
				deployContainer(container, deployFuture);
				deployFuture.setHandler(deployAres -> {
					if (deployAres.succeeded()) {
						JsonArray deployResult = deployAres.result();
						// TODO 更新部署状态
						log.trace("Deployed succeed:" + deployResult);
					} else {
						log.error("Redeploy failed!", deployAres.cause());
					}
				});
				message.reply(new JsonObject().put("status", "connected"));
			} else {
				ares.cause().printStackTrace();
				message.fail(500, "Connect failed:" + ares.cause().toString());
			}
		});
	}

	// private JsonObject getContainer(JsonObject connectionInfo) {
	// // TODO
	// String ip = connectionInfo.getString(ContainerConsts.ATT_IP);
	// int managePort =
	// connectionInfo.getInteger(ContainerConsts.ATT_MANAGE_PORT);
	// String containerID = ContainerUtils.getOrCreateContainerId(new
	// JsonObject().put(ContainerConsts.ATT_IP, ip)
	// .put(ContainerConsts.ATT_MANAGE_PORT, managePort));
	// connectionInfo.put(ContainerConsts.ATT_ID, containerID);
	// return connectionInfo;
	// }

	/**
	 * 尝试加锁并连接容器
	 * 
	 * @param container
	 * @param future
	 */
	private void lockAndConnectContainer(JsonObject connectionInfo, Future<JsonObject> future) {
		this.lockContainer(connectionInfo, lockAres -> {
			if (lockAres.succeeded()) {
				Lock containerLock = lockAres.result();
				Future<IContainerMonitor> connectFuture = Future.future();
				connectContainer(connectionInfo, containerLock, connectFuture);
				connectFuture.setHandler(ares -> {
					if (ares.succeeded()) {
						getOrCreateContainer(connectionInfo, future);
					} else {
						containerLock.release();
						future.fail(ares.cause());
					}
				});
			} else {
				future.fail(lockAres.cause());
			}
		});
	}

	private void getOrCreateContainer(JsonObject connectionInfo, Future<JsonObject> future) {
		Integer containerId = connectionInfo.getInteger(ContainerConsts.ATT_ID);
		if (containerId != null) {
			future.complete(connectionInfo);
		} else {
			String ip = connectionInfo.getString(ContainerConsts.ATT_IP);
			int managePort = connectionInfo.getInteger(ContainerConsts.ATT_MANAGE_PORT);
			// 查询
			vertx.eventBus().<JsonArray> send(
					ContainerDAO.ADDRESS_QUERY,
					new JsonObject().put(ContainerConsts.ATT_IP, ip).put(ContainerConsts.ATT_MANAGE_PORT, managePort),
					containerQueryAres -> {
						if (containerQueryAres.succeeded()) {
							JsonArray result = containerQueryAres.result().body();
							if (result == null || result.size() == 0) {
								vertx.eventBus().<JsonObject> send(ContainerDAO.ADDRESS_INSERT,
										generateContainer(connectionInfo), insertAres -> {
											if (insertAres.succeeded()) {
												JsonObject container = insertAres.result().body();
												future.complete(container);
											} else {
												insertAres.cause().printStackTrace();
												future.fail(insertAres.cause());
											}
										});
							} else {
								future.complete(result.getJsonObject(0));
							}
						} else {
							future.fail(containerQueryAres.cause());
						}
					});
		}
	}

	private JsonObject generateContainer(JsonObject connectionInfo) {
		return connectionInfo;
	}

	/**
	 * 对容器加锁
	 * 
	 * @param container
	 * @param resultHandler
	 */
	private void lockContainer(JsonObject connectionInfo, Handler<AsyncResult<Lock>> resultHandler) {
		String containerLockKey = ContainerUtils.getLockKeyOfContainer(connectionInfo);
		vertx.sharedData().getLock(containerLockKey, resultHandler);
	}

	/**
	 * 创建容器监控器
	 * 
	 * @param connectionInfo
	 * @param containerLock
	 * @return
	 */
	protected IContainerMonitor createMonitor(JsonObject connectionInfo, Lock containerLock) {
		IContainerMonitor monitor = new ContainerMonitor(vertx, connectionInfo, containerLock);
		String containerKey = getContainerKey(connectionInfo);
		monitors.put(containerKey, monitor);
		monitor.setStatusReportHandler(status -> {
			if (vertx.isClustered()) {
				vertx.sharedData().getClusterWideMap(ContainerConsts.SD_KEY_CONTAINER_STATUS, getStatusMapAres -> {
					if (getStatusMapAres.succeeded()) {
						AsyncMap<Object, Object> statusMap = getStatusMapAres.result();
						statusMap.put(containerKey, status, putAres -> {
							if (putAres.failed()) {
								putAres.cause().printStackTrace();
							}
						});
					} else {
						getStatusMapAres.cause().printStackTrace();
					}
				});
			} else {
				LocalMap<Object, Object> statusMap = vertx.sharedData().getLocalMap(
						ContainerConsts.SD_KEY_CONTAINER_STATUS);
				statusMap.put(containerKey, status);
			}
		});
		monitor.setDisActiveHander(disActivedMonitor -> {
			monitors.remove(containerKey);
		});
		return monitor;
	}

	private String getContainerKey(JsonObject connectionInfo) {
		String containerIp = connectionInfo.getString(ContainerConsts.ATT_IP);
		Integer containerManagePort = connectionInfo.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		String containerKey = containerIp + ":" + containerManagePort;
		return containerKey;
	}

	/**
	 * 连接新容器
	 * 
	 * @param ip
	 * @param managePort
	 * @param message
	 */
	private void connectContainer(JsonObject connectionInfo, Lock containerLock, Future<IContainerMonitor> future) {
		String ip = connectionInfo.getString(ContainerConsts.ATT_IP);
		int managePort = connectionInfo.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		// 连接容器
		IContainerMonitor monitor = createMonitor(connectionInfo, containerLock);
		Future<Void> startFuture = Future.future();
		monitor.start(startFuture);
		startFuture.setHandler(monitorStartAres -> {
			if (monitorStartAres.succeeded()) {
				log.debug("Start monitor [" + ip + ":" + managePort + "]");
				future.complete(monitor);
			} else {
				future.fail(monitorStartAres.cause());
			}
		});
	}

	private void handleGetContainerStatus(Message<JsonArray> message) {
		JsonArray containers = message.body();
		Future<JsonArray> fetchStatusFuture = Future.<JsonArray> future();
		fetchContainerStatus(containers, fetchStatusFuture);
		fetchStatusFuture.setHandler(fetchAres -> {
			if (fetchAres.succeeded()) {
				message.reply(fetchAres.result());
			} else {
				log.error("Get container status failed!", fetchAres.cause());
				message.fail(500, fetchAres.cause().getMessage());
			}
		});
	}

	private void fetchContainerStatus(JsonArray containers, Future<JsonArray> future) {
		if (vertx.isClustered()) {
			vertx.sharedData()
					.<String, JsonObject> getClusterWideMap(
							ContainerConsts.SD_KEY_CONTAINER_STATUS,
							getContainerStatusMapAres -> {
								if (getContainerStatusMapAres.succeeded()) {
									AsyncMap<String, JsonObject> statusMap = getContainerStatusMapAres.result();
									if (statusMap != null) {
										MultipleFutures futures = new MultipleFutures();
										for (Object containerObj : containers.getList()) {
											futures.add(aFuture -> {
												JsonObject container = (JsonObject) containerObj;
												String containerKey = getContainerKey(container);
												statusMap.get(
														containerKey,
														getAres -> {
															if (getAres.succeeded()) {
																JsonObject status = getAres.result();
																if (status != null)
																	container.put(ContainerConsts.ATT_STATUS,
																			getAres.result());
																else
																	container
																			.put(ContainerConsts.ATT_STATUS,
																					new JsonObject()
																							.put(ContainerConsts.ATT_STATUS_CONNECTION,
																									EConnectStatus.UNKNOWN));
																aFuture.complete();
															} else {
																aFuture.fail(getAres.cause());
															}
														});
											});
										}
										futures.setHandler(ares -> {
											if (ares.succeeded()) {
												future.complete(containers);
											} else {
												future.fail(ares.cause());
											}
										});
										futures.start();
									} else {
										future.complete(containers);
									}
								} else {
									future.fail(getContainerStatusMapAres.cause());
								}
							});
		} else {
			LocalMap<String, JsonObject> statusMap = vertx.sharedData().getLocalMap(
					ContainerConsts.SD_KEY_CONTAINER_STATUS);
			if (statusMap != null && statusMap.size() > 0) {
				for (Object containerObj : containers.getList()) {
					JsonObject container = (JsonObject) containerObj;
					String containerKey = getContainerKey(container);
					JsonObject status = statusMap.get(containerKey);
					if (status != null)
						container.put(ContainerConsts.ATT_STATUS, status);
				}
			}
			future.complete(containers);
		}

	}
}
