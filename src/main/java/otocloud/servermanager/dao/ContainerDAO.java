package otocloud.servermanager.dao;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.util.List;

import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.util.SQLUtils;

public class ContainerDAO extends AbstractDAO {

	private static final String ADDRESS = "otocloud.servermanager.dao.container";
	public static final String ADDRESS_INSERT = ADDRESS + ".insert";
	public static final String ADDRESS_DELETE = ADDRESS + ".delete";
	public static final String ADDRESS_QUERY_ALL = ADDRESS + ".query_all";
	public static final String ADDRESS_QUERY_BY_ID = ADDRESS + ".query_by_id";
	public static final String ADDRESS_QUERY = ADDRESS + ".query";

	private Logger log = LoggerFactory.getLogger(this.getClass());

	@Override
	protected void registerConsumer() {
		vertx.eventBus().consumer(ADDRESS_INSERT, this::insert);
		vertx.eventBus().consumer(ADDRESS_DELETE, this::delete);
		vertx.eventBus().consumer(ADDRESS_QUERY_ALL, this::queryAll);
		vertx.eventBus().consumer(ADDRESS_QUERY_BY_ID, this::queryById);
		vertx.eventBus().consumer(ADDRESS_QUERY, this::query);
	}

	private void insert(Message<JsonObject> message) {
		JsonObject container = message.body();
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				String shortDiscription = container.getString(ContainerConsts.ATT_SHORT_DISCRIPTION);
				connection.updateWithParams(
						"insert into ops_container(ip,manage_port,monitor_port,short_discription) values (?,?,?,?)",
						new JsonArray().add(container.getString(ContainerConsts.ATT_IP))
								.add(container.getInteger(ContainerConsts.ATT_MANAGE_PORT))
								.add(container.getInteger(ContainerConsts.ATT_MONITOR_PORT))
								.add(shortDiscription == null ? "" : shortDiscription), insertAres -> {
							if (insertAres.succeeded()) {
								log.debug(insertAres.result().getKeys());
								queryByParameters(connection, container, queryAres -> {
									if (queryAres.succeeded()) {
										ResultSet resultSet = queryAres.result();
										if (resultSet.getNumRows() == 1) {
											message.reply(resultSet.getRows().get(0));
										} else {
											message.fail(500, "Unexpected container data!");
										}
									} else {
										log.error("Re Query container failed!", queryAres.cause());
										message.fail(500, queryAres.cause().getMessage());
									}
									connection.close(closeAres -> {
										if (closeAres.failed()) {
											log.error("Connection close failed!", closeAres.cause());
										}
									});
								});
							} else {
								connection.close(closeAres -> {
									if (closeAres.failed()) {
										log.error("Connection close failed!", closeAres.cause());
									}
								});
								log.error("insert container failed!", insertAres.cause());
								message.fail(500, insertAres.cause().getMessage());
							}
						});
			} else {
				log.error("Connect database failed!", connectionAres.cause());
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void delete(Message<JsonObject> message) {
		// TODO
	}

	private void queryAll(Message<JsonObject> message) {
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				connection.query("select * from ops_container where delete_id is null", queryAres -> {
					if (queryAres.succeeded()) {
						List<JsonObject> results = queryAres.result().getRows();
						if (results != null)
							message.reply(new JsonArray(results));
						else
							message.reply(new JsonArray());
					} else {
						message.fail(500, queryAres.cause().getMessage());
					}
					connection.close(closeAres -> {
						if (closeAres.failed()) {
							log.error("Connection close failed!", closeAres.cause());
						}
					});
				});
			} else {
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void queryById(Message<Integer> message) {
		Integer containerId = message.body();
		jdbcClient.getConnection(connectionAres -> {
			if (connectionAres.succeeded()) {
				SQLConnection connection = connectionAres.result();
				connection.queryWithParams("select * from ops_container where id = ?",
						new JsonArray().add(containerId), queryAres -> {
							if (queryAres.succeeded()) {
								List<JsonObject> results = queryAres.result().getRows();
								if (results != null && results.size() == 1)
									message.reply(results.get(0));
								else
									message.fail(500, "Unexpected container data.ID[" + containerId + "]");
							} else {
								message.fail(500, queryAres.cause().getMessage());
							}
							connection.close(closeAres -> {
								if (closeAres.failed()) {
									log.error("Connection close failed!", closeAres.cause());
								}
							});
						});
			} else {
				message.fail(500, connectionAres.cause().getMessage());
			}
		});
	}

	private void query(Message<JsonObject> message) {
		JsonObject params = message.body();
		jdbcClient.getConnection(connectAres -> {
			if (connectAres.succeeded()) {
				SQLConnection connection = connectAres.result();
				queryByParameters(connection, params, queryAres -> {
					if (queryAres.succeeded()) {
						List<JsonObject> results = queryAres.result().getRows();
						if (results != null)
							message.reply(new JsonArray(results));
						else
							message.reply(new JsonArray());
					} else {
						log.error("Query container failed!", queryAres.cause());
						message.fail(500, queryAres.cause().getMessage());
					}
					connection.close(closeAres -> {
						if (closeAres.failed()) {
							log.error("Connection close failed!", closeAres.cause());
						}
					});
				});
			} else {
				log.error("Get SQLConnection failed!", connectAres.cause());
				message.fail(500, connectAres.cause().getMessage());
			}
		});
	}

	private void queryByParameters(SQLConnection connection, JsonObject params,
			Handler<AsyncResult<ResultSet>> queryHandler) {
		JsonArray paramValues = new JsonArray();
		String querySql = SQLUtils.getQuerySql("ops_container", null, params, paramValues);
		connection.queryWithParams(querySql, paramValues, queryHandler);
	}

}
