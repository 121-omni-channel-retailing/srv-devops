package otocloud.servermanager;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import otocloud.common.MultipleFutures;
import otocloud.servermanager.dao.ContainerDAO;
import otocloud.servermanager.dao.ModuleDeploymentDAO;
import otocloud.servermanager.resource.DefaultConfigOfServiceInMaven;
import otocloud.servermanager.resource.server.Container;
import otocloud.servermanager.resource.server.ContainerConnection;
import otocloud.servermanager.resource.server.ContainerStatus;
import otocloud.servermanager.resource.server.ContainerSummaryInfo;
import otocloud.servermanager.resource.server.ModuleDeployment;
import otocloud.servermanager.server.container.ContainerManager;

/**
 * 运维服务器主Verticle
 * 
 * @author caojj1
 *
 */
public class ServerManagerVerticle extends AbstractVerticle {

	private static final String PROP_NEED_START_WEBSERVER = "needStartWebServer";
	private static final String PROP_WEBSERVER_PORT = "portOfWebServer";
	private static final String PROP_WEBSERVER_NAME = "nameOfWebServer";

	private static final boolean DEFAULT_NEED_START_WEBSERVER = true;
	private static final int DEFAULT_WEBSERVER_PORT = 8901;
	private static final String DEFAULT_WEBSERVER_NAME = "otocloud.servermanager.webserver";
	private static final String DEFUALT_ROUTE_REG_ADDRESS = "platform.register.rest.to.webserver";
	private static final String DEFUALT_ROUTE_UNREG_ADDRESS = "platform.unregister.rest.to.webserver";

	Logger log = LoggerFactory.getLogger(this.getClass());

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		if (config().getBoolean(PROP_NEED_START_WEBSERVER, DEFAULT_NEED_START_WEBSERVER)) {
			startWebServer(startFuture, this::afterWebServerStarted);
		} else {
			afterWebServerStarted(startFuture);
		}
	}

	/**
	 * 启动webservice服务器
	 * 
	 * @param startFuture
	 * @param after
	 */
	private void startWebServer(Future<Void> startFuture, Handler<Future<Void>> after) {
		Consumer<Vertx> runner = vertx -> {
			vertx.deployVerticle("otocloud.webserver.WebServerVerticle", getWebServerDeploymentOptions(), ares -> {
				if (ares.succeeded()) {
					after.handle(startFuture);
				} else {
					log.error("Start web server failed!", ares.cause());
					startFuture.fail(ares.cause());
				}
			});
		};
		runner.accept(vertx);
	}

	private void afterWebServerStarted(Future<Void> startFuture) {
		// 部署DAO
		Future<Void> daoFuture = Future.future();
		deployDAOs(daoFuture);
		daoFuture.setHandler(daoAres -> {
			if (daoAres.succeeded()) {
				// 部署系统组件
				Future<Void> sysCompFuture = Future.future();
				deploySystemComponents(sysCompFuture);
				sysCompFuture.setHandler(sysCompAres -> {
					if (sysCompAres.succeeded()) {
						// 部署系统资源
						Future<Void> sysResFuture = Future.future();
						deploySystemResources(sysResFuture);
						sysResFuture.setHandler(sysResAres -> {
							if (sysResAres.succeeded()) {
								startFuture.complete();
							} else {
								startFuture.fail(sysResAres.cause());
							}
						});
					} else {
						startFuture.fail(sysCompAres.cause());
					}
				});
			} else {
				startFuture.fail(daoAres.cause());
			}
		});
	}

	/**
	 * 部署系统Rest资源
	 * 
	 * @param future
	 */
	private void deploySystemResources(Future<Void> future) {
		List<String> systemResources = loadSystemResources();
		DeploymentOptions options = new DeploymentOptions();
		JsonObject config = new JsonObject();
		String webServerName = config().getString(PROP_WEBSERVER_NAME, DEFAULT_WEBSERVER_NAME);
		config.put("routeRegisterAddress", webServerName + "." + DEFUALT_ROUTE_REG_ADDRESS);
		config.put("routeUnregisterAddress", webServerName + "." + DEFUALT_ROUTE_UNREG_ADDRESS);
		options.setConfig(config);
		MultipleFutures multifutures = new MultipleFutures(future);
		for (String systemResource : systemResources) {
			multifutures.add(deployFuture -> {
				vertx.deployVerticle(systemResource, options, res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						deployFuture.fail(res.cause());
					} else {
						deployFuture.complete();
					}
				});
			});
		}
		multifutures.start();
	}

	private List<String> loadSystemResources() {
		List<String> resources = new ArrayList<String>();
		resources.add(Container.class.getName());
		resources.add(ContainerStatus.class.getName());
		resources.add(ContainerConnection.class.getName());
		resources.add(ContainerSummaryInfo.class.getName());
		resources.add(ModuleDeployment.class.getName());
		resources.add(DefaultConfigOfServiceInMaven.class.getName());
		return resources;
	}

	/**
	 * 部署临时DAO
	 * 
	 * @param future
	 */
	private void deployDAOs(Future<Void> future) {
		String[] daos = new String[] { ContainerDAO.class.getName(), ModuleDeploymentDAO.class.getName() };
		DeploymentOptions options = new DeploymentOptions();
		MultipleFutures multifutures = new MultipleFutures(future);
		for (String dao : daos) {
			multifutures.add(daoFuture -> {
				vertx.deployVerticle(dao, options, res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						daoFuture.fail(res.cause());
					} else {
						daoFuture.complete();
					}
				});
			});
		}
		multifutures.start();
	}

	/**
	 * 部署系统组件
	 * 
	 * @param future
	 */
	private void deploySystemComponents(Future<Void> future) {
		String[] components = new String[] { ContainerManager.class.getName() };
		DeploymentOptions options = new DeploymentOptions();
		MultipleFutures multifutures = new MultipleFutures(future);
		for (String component : components) {
			multifutures.add(sysCompFuture -> {
				vertx.deployVerticle(component, options, res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						sysCompFuture.fail(res.cause());
					} else {
						sysCompFuture.complete();
					}
				});
			});
		}
		multifutures.start();
	}

	private DeploymentOptions getWebServerDeploymentOptions() {
		JsonObject config = new JsonObject();
		config.put("http.port", config().getInteger(PROP_WEBSERVER_PORT, DEFAULT_WEBSERVER_PORT));
		config.put("webserver_name", config().getString(PROP_WEBSERVER_NAME, DEFAULT_WEBSERVER_NAME));
		DeploymentOptions options = new DeploymentOptions().setConfig(config);
		return options;
	}

}
