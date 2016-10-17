package otocloud.server.management;

import io.vertx.core.VertxOptions;

public class ServerManagementServerTest {
	public static void main(String[] args) {
		ExampleRunner.runJavaExample("otocloud-devops/src/main/java",
				ServerManagementServer.class, new VertxOptions());
	}
}
