package otocloud.servermanager.resource;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractResource extends AbstractVerticle {

	protected Logger log = LoggerFactory.getLogger(this.getClass());

	private HttpClient httpClient;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		initOnStart(startFuture);
	}

	protected void initOnStart(Future<Void> initFuture) {
		this.registerServices();
		initFuture.complete();
	}

	protected void registerServices() {
		registerOwnService("get", this::get);
		registerOwnService("post", this::post);
		registerOwnService("put", this::put);
		registerOwnService("delete", this::delete);
	}

	private void registerOwnService(String method, Handler<Message<JsonObject>> handler) {
		List<String> supportedMethodList = Arrays.asList(this.supportedMethods());
		if (supportedMethodList.contains(method)) {
			String serviceAddress = address() + "." + method;
			vertx.eventBus().consumer(serviceAddress, handler);
			registerRoute(method, serviceAddress);

		}
	}

	private void registerRoute(String method, String address) {
		String routeRegisterAddress = config().getString("routeRegisterAddress");
		String[] uris = uriMap().get(method);
		for (String uri : uris) {
			JsonObject aRoute = new JsonObject();
			aRoute.put("uri", uri);
			aRoute.put("method", method);
			aRoute.put("address", address);
			vertx.eventBus().publish(routeRegisterAddress, aRoute);
		}
	}

	protected String[] supportedMethods() {
		return uriMap().keySet().toArray(new String[0]);
	}

	protected abstract Map<String, String[]> uriMap();

	protected abstract String address();

	// protected void registerMessageConsumer(String address,
	// Handler<Message<JsonObject>> handler) {
	// messageConsumers.add(vertx.eventBus().consumer(address, handler));
	// }

	protected void get(Message<JsonObject> message) {

	}

	protected void post(Message<JsonObject> message) {

	}

	protected void put(Message<JsonObject> message) {

	}

	protected void delete(Message<JsonObject> message) {

	}

	@Override
	public void stop(Future<Void> stopFuture) throws Exception {
		unRegisterServices();
		if (httpClient != null) {
			httpClient.close();
		}
		stopFuture.complete();
	}

	protected void unRegisterServices() {
		unRegisterRoute();
		// unRegisterMessageConsumer();
	}

	// private void unRegisterMessageConsumer() {
	// for (MessageConsumer<?> consumer : messageConsumers) {
	// consumer.unregister();
	// }
	// }

	private void unRegisterRoute() {
		JsonArray routes = new JsonArray();
		for (String method : this.supportedMethods()) {
			JsonObject aRoute = new JsonObject();
			String serviceAddress = address() + "." + method;
			aRoute.put("uri", uriMap().get(method));
			aRoute.put("method", method);
			aRoute.put("address", serviceAddress);
			routes.add(aRoute);
		}
		String routeUnregisterAddress = config().getString("routeUnregisterAddress");
		vertx.eventBus().publish(routeUnregisterAddress, routes);
	}

	// protected List<MessageConsumer<?>> getMessageConsumers() {
	// return messageConsumers;
	// }
	//
	// protected void setMessageConsumers(List<MessageConsumer<?>>
	// messageConsumers) {
	// this.messageConsumers = messageConsumers;
	// }

	public HttpClient getHttpClient() {
		if (httpClient == null) {
			httpClient = vertx.createHttpClient();
		}
		return httpClient;
	}

	public HttpClient getHttpClient(HttpClientOptions options) {
		if (httpClient == null) {
			httpClient = vertx.createHttpClient(options);
		}
		return httpClient;
	}
}
