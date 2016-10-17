package otocloud.server.management.service.frame;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

import java.util.Map;

public interface IWebSocketAdapableService<T> extends Handler<Map<String, Object>> {
	public Map<String, Object> extractParameters(Message<T> message);
}
