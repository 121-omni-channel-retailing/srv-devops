package otocloud.server.management.service.frame;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;

public class WebSocketServiceAdaptor<T> implements Handler<Message<T>> {

	private IWebSocketAdapableService<T> service;

	public WebSocketServiceAdaptor(IWebSocketAdapableService<T> service) {
		this.service = service;
	}

	@Override
	public void handle(Message<T> message) {
		this.service.handle(this.service.extractParameters(message));
	}

}
