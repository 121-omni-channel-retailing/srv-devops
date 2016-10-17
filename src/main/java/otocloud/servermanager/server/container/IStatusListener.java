package otocloud.servermanager.server.container;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface IStatusListener {
	void statusChanged(JsonObject status, Future<Void> future);
}
