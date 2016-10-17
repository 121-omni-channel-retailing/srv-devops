package otocloud.server.management.service.frame;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public interface IHttpAdapableService extends Handler<Map<String, Object>> {
	public Map<String, Object> extractParameters(RoutingContext routingContext);
}
