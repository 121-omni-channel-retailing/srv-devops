package otocloud.servermanager.dao;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

public abstract class AbstractDAO extends AbstractVerticle {

	protected JDBCClient jdbcClient;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		Future<Void> initFuture = Future.<Void> future();
		this.initOnStart(initFuture);
		initFuture.setHandler(initAres -> {
			if (initAres.succeeded()) {
				registerConsumer();
				startFuture.complete();
			} else {
				startFuture.fail(initAres.cause());
			}
		});
	}

	protected void initOnStart(Future<Void> initFuture) {
		prepareJDBCClient();
		initFuture.complete();
	}

	protected void prepareJDBCClient() {
		jdbcClient = JDBCClient.createShared(
				vertx,
				new JsonObject().put("url", "jdbc:mysql://10.10.23.112:3306/121cmdb?user=test&password=test")
						.put("driver_class", "com.mysql.jdbc.Driver").put("max_pool_size", 30));
	}

	protected abstract void registerConsumer();

	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		if (jdbcClient != null) {
			jdbcClient.close();
		}
		stopFuture.complete();
	}
}
