package otocloud.servermanager;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Consumer;

public class ServerManagerStarter {

	private static Logger log = LoggerFactory.getLogger(ServerManagerStarter.class);

	public static void main(String[] args) {
		Consumer<Vertx> runner = vertx -> {
			vertx.deployVerticle("otocloud.servermanager.ServerManagerVerticle", getDeploymentOptions());
		};
		VertxOptions vertxOptions = getVertxOptions();
		if (vertxOptions.isClustered()) {
			Vertx.clusteredVertx(vertxOptions, res -> {
				if (res.succeeded()) {
					Vertx vertx = res.result();
					runner.accept(vertx);
				} else {
					res.cause().printStackTrace();
				}
			});
		} else {
			Vertx vertx = Vertx.vertx(vertxOptions);
			runner.accept(vertx);
		}

	}

	private static DeploymentOptions getDeploymentOptions() {
		InputStream inputSteam = ServerManagerStarter.class.getResourceAsStream("/otocloud.servermanager.json");
		BufferedInputStream bufferedInputStream = new BufferedInputStream(inputSteam);
		Buffer optionsBuffer = Buffer.buffer();
		int size = -1;
		byte[] block = new byte[100];
		do {
			try {
				size = bufferedInputStream.read(block);
				if (size != -1) {
					if (size == 100) {
						optionsBuffer.appendBytes(block);
					} else {
						optionsBuffer.appendBytes(Arrays.copyOf(block, size));
					}
				}
			} catch (IOException e) {
				log.error(e.getMessage(), e);
				try {
					bufferedInputStream.close();
				} catch (IOException e1) {
					log.error(e.getMessage(), e);
				}
			}
		} while (size == 100);
		try {
			bufferedInputStream.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		JsonObject optionsJson = new JsonObject(new String(optionsBuffer.getBytes()));
		DeploymentOptions options = new DeploymentOptions(optionsJson.getJsonObject("options"));
		return options;
	}

	private static VertxOptions getVertxOptions() {
		return new VertxOptions();
	}
}
