package otocloud.servermanager.util;

import io.vertx.core.json.JsonObject;
import otocloud.servermanager.consts.ContainerConsts;

public class ContainerUtils {
	public static String getLockKeyOfContainer(JsonObject connectionInfo) {
		String ip = connectionInfo.getString(ContainerConsts.ATT_IP);
		int managePort = connectionInfo.getInteger(ContainerConsts.ATT_MANAGE_PORT);
		return "otocloud.servermanager.container." + ip + "." + managePort;
	}

	//
	// public static String getOrCreateContainerId(JsonObject container) {
	// if (container.containsKey(ContainerConsts.ATT_ID)) {
	// return container.getString(ContainerConsts.ATT_ID);
	// } else {
	// String ip = container.getString(ContainerConsts.ATT_IP);
	// int managePort = container.getInteger(ContainerConsts.ATT_MANAGE_PORT);
	// String id = ip + CommonConsts.CONNECTOR_COMBINED_ID + managePort;
	// container.put(ContainerConsts.ATT_ID, id);
	// return id;
	// }
	// }
}
