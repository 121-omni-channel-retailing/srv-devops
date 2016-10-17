package otocloud.server.management.service.frame;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class HttpServiceAdaptor implements Handler<RoutingContext> {

	private IHttpAdapableService service;

	public HttpServiceAdaptor(IHttpAdapableService service) {
		this.service = service;
	}

	@Override
	public void handle(RoutingContext routingContext) {
		this.service.handle(this.service.extractParameters(routingContext));
	}

}
