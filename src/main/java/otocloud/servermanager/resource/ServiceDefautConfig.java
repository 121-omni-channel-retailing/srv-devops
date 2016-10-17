package otocloud.servermanager.resource;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.server.container.MavenRepositoryService;
import static otocloud.servermanager.resource.IMethodsConst.*;

public class ServiceDefautConfig extends AbstractResource {
	
	private MavenRepositoryService repositoryService;

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put(GET, new String[] { "/services/:serviceIdentifier/default_config" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.servermanager.resource.service.defaut_config";
	}

	@Override
	protected void initOnStart(io.vertx.core.Future<Void> initFuture) {
		repositoryService = new MavenRepositoryService();
		
	};

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject params = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		String serviceIdentifier = params.getString("serviceIdentifier");

	}

}
