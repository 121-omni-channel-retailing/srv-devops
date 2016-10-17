package otocloud.servermanager.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.util.List;

import otocloud.common.MultipleFutures;
import otocloud.servermanager.consts.ModuleDeploymentConsts;
import otocloud.servermanager.util.SQLUtils;

public class ModuleDeploymentDAO extends AbstractDAO {

	public static final String ADDRESS = "otocloud.servermanager.dao.module_deployment";
	public static final String ADDRESS_INSERT = ADDRESS + ".insert";
	public static final String ADDRESS_BATCH_INSERT = ADDRESS + ".batch_insert";
	public static final String ADDRESS_DELETE_BY_ID = ADDRESS + ".delete_by_id";
	public static final String ADDRESS_QUERY_BY_ID = ADDRESS + ".query_by_id";
	public static final String ADDRESS_QUERY = ADDRESS + ".query";

	private Logger log = LoggerFactory.getLogger(this.getClass());

	@Override
	protected void registerConsumer() {
		vertx.eventBus().consumer(ADDRESS_INSERT, this::insert);
		vertx.eventBus().consumer(ADDRESS_BATCH_INSERT, this::batchInsert);
		vertx.eventBus().consumer(ADDRESS_DELETE_BY_ID, this::deleteById);
		vertx.eventBus().consumer(ADDRESS_QUERY, this::query);
		vertx.eventBus().consumer(ADDRESS_QUERY_BY_ID, this::queryById);
	}

	private void batchInsert(Message<JsonArray> message) {
		JsonArray moduleDeployments = message.body();
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				MultipleFutures futures = new MultipleFutures();
				JsonArray results = new JsonArray();
				moduleDeployments.forEach(moduleDeploymentObj -> {
					JsonObject moduleDeployment = (JsonObject) moduleDeploymentObj;
					futures.add(aFuture -> {
						Future<JsonObject> insertFuture = Future.future();
						insertOnConnection(moduleDeployment, connection, insertFuture);
						insertFuture.setHandler(insertAres -> {
							if (insertAres.succeeded()) {
								results.add(insertAres.result());
								aFuture.complete();
							} else {
								aFuture.fail(insertAres.cause());
							}
						});
					});
				});
				futures.setHandler(batchInsertAres -> {
					if (batchInsertAres.succeeded()) {
						message.reply(results);
					} else {
						log.error("Insert failed!", batchInsertAres.cause());
						message.fail(500, batchInsertAres.cause().getMessage());
					}
					connection.close(closeAres -> {
						if (closeAres.failed()) {
							log.error("Connection close failed!", closeAres.cause());
						}
					});
				});
				futures.start();
			} else {
				log.error("Connect database failed!", connectionAres.cause());
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void insert(Message<JsonObject> message) {
		JsonObject moduleDeployment = message.body();
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				Future<JsonObject> insertFuture = Future.future();
				insertOnConnection(moduleDeployment, connection, insertFuture);
				insertFuture.setHandler(insertAres -> {
					if (insertAres.succeeded()) {
						message.reply(insertAres.result());
					} else {
						log.error("Insert failed!", insertAres.cause());
						message.fail(500, insertAres.cause().getMessage());
					}
					connection.close(closeAres -> {
						if (closeAres.failed()) {
							log.error("Connection close failed!", closeAres.cause());
						}
					});
				});
			} else {
				log.error("Connect database failed!", connectionAres.cause());
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void insertOnConnection(JsonObject moduleDeployment, SQLConnection connection,
			Future<JsonObject> insertFuture) {
		connection
				.updateWithParams(
						"insert into ops_module_deployment(app_module_id,app_module_version_id,ops_container_id,srv_name,deploy_config) values (?,?,?,?,?)",
						new JsonArray()
								.add(moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_MODULE_ID))
								.add(moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_MODULE_VERSION_ID))
								.add(moduleDeployment.getInteger(ModuleDeploymentConsts.ATT_CONTAINER_ID))
								.add(moduleDeployment.getString(ModuleDeploymentConsts.ATT_SERVICE_NAME))
								.add(moduleDeployment.getJsonObject(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG)
										.toString()), insertAres -> {
							if (insertAres.succeeded()) {
								log.debug(insertAres.result().getKeys());
								queryByParameters(connection, moduleDeployment, queryAres -> {
									if (queryAres.succeeded()) {
										if (queryAres.result().getNumRows() == 1) {
											insertFuture.complete(queryAres.result().getRows().get(0));
										} else {
											insertFuture.fail("Unexpected module deployment data!");
										}
									} else {
										insertFuture.fail(queryAres.cause());
									}
								});
							} else {
								insertFuture.fail(insertAres.cause());
							}
						});
	}

	private void deleteById(Message<Integer> message) {
		Integer moduleDeploymentId = message.body();
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				connection.updateWithParams("delete from ops_module_deployment where id = ? ",
						new JsonArray().add(moduleDeploymentId), deleteAres -> {
							if (deleteAres.succeeded()) {
								message.reply(deleteAres.result());
							} else {
								log.error("Delete module deployment failed!", connectionAres.cause());
								message.fail(500, deleteAres.cause().getMessage());
							}
							connection.close(closeAres -> {
								if (closeAres.failed()) {
									log.error("Connection close failed!", closeAres.cause());
								}
							});
						});
			} else {
				log.error("Connect database failed!", connectionAres.cause());
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void query(Message<JsonObject> message) {
		JsonObject params = message.body();
		jdbcClient
				.getConnection(connectAres -> {
					SQLConnection connection = connectAres.result();
					queryByParameters(connection, params,
							queryAres -> {
								if (queryAres.succeeded()) {
									List<JsonObject> results = queryAres.result().getRows();
									if (results != null) {
										for (JsonObject result : results) {
											result.put(
													ModuleDeploymentConsts.ATT_DEPLOY_CONFIG,
													new JsonObject(result
															.getString(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG)));
										}
										message.reply(new JsonArray(results));
									} else
										message.reply(new JsonArray());
								} else {
									log.error("Query module deployment failed!", queryAres.cause());
									message.fail(500, queryAres.cause().getMessage());
								}
								connection.close(closeAres -> {
									if (closeAres.failed()) {
										log.error("Connection close failed!", closeAres.cause());
									}
								});
							});
				});
	}

	private void queryById(Message<Integer> message) {
		Integer moduleDeploymentId = message.body();
		jdbcClient.getConnection(connectAres -> {
			SQLConnection connection = connectAres.result();
			queryByParameters(
					connection,
					new JsonObject().put(ModuleDeploymentConsts.ATT_ID, moduleDeploymentId),
					queryAres -> {
						if (queryAres.succeeded()) {
							List<JsonObject> results = queryAres.result().getRows();
							if (results != null && results.size() == 1) {
								JsonObject result = results.get(0);
								result.put(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG,
										new JsonObject(result.getString(ModuleDeploymentConsts.ATT_DEPLOY_CONFIG)));
							} else
								message.fail(500, "Data is dumplicate or deleted!");
						} else {
							log.error("Query module deployment failed!", queryAres.cause());
							message.fail(500, queryAres.cause().getMessage());
						}
						connection.close(closeAres -> {
							if (closeAres.failed()) {
								log.error("Connection close failed!", closeAres.cause());
							}
						});
					});
		});
	}

	private void queryByParameters(SQLConnection connection, JsonObject parameters,
			Handler<AsyncResult<ResultSet>> queryHandler) {
		JsonArray paramValues = new JsonArray();
		String querySql = SQLUtils.getQuerySql("ops_module_deployment", null, parameters, paramValues);
		connection.queryWithParams(querySql, paramValues, queryHandler);
	}

}
