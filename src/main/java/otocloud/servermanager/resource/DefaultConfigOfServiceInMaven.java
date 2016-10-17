package otocloud.servermanager.resource;

import static otocloud.servermanager.resource.IMethodsConst.GET;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

import org.eclipse.aether.resolution.ArtifactResult;

import otocloud.servermanager.consts.CommonConsts;
import otocloud.servermanager.server.container.MavenRepositoryService;

public class DefaultConfigOfServiceInMaven extends AbstractResource {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private MavenRepositoryService repositoryService;

	@Override
	protected void initOnStart(Future<Void> future) {
		repositoryService = new MavenRepositoryService();
		this.registerServices();
		future.complete();
	}

	@Override
	protected Map<String, String[]> uriMap() {
		Map<String, String[]> uriMap = new HashMap<String, String[]>();
		uriMap.put(GET, new String[] {
				"/maven/groups/:groupId/artifacts/:artifactId/versions/:version/services/:serviceName/default_config",
				"/maven/services/:coords/default_config" });
		return uriMap;
	}

	@Override
	protected String address() {
		return "otocloud.servermanager.resource.default-config-of-service-in-maven";
	}

	@Override
	protected void get(Message<JsonObject> message) {
		JsonObject queryParams = message.body().getJsonObject(CommonConsts.REST_PARAMETERS);
		vertx.<Buffer> executeBlocking(
				blockingFuture -> {
					ArtifactResult artifactResult = null;
					String coords = queryParams.getString("coords");
					String serviceName = null;
					if (coords != null) {
						int pos = coords.indexOf("::");
						String artifactCoords = coords.substring(0, pos);
						serviceName = coords.substring(pos + 2);
						artifactResult = repositoryService.resolveArtifact(artifactCoords);
					} else {
						String groupId = queryParams.getString("groupId");
						String artifactId = queryParams.getString("artifactId");
						String version = queryParams.getString("version");
						serviceName = queryParams.getString("serviceName");
						artifactResult = repositoryService.resolveArtifact(groupId, artifactId, "jar", version);
					}
					if (artifactResult != null && artifactResult.isResolved()) {
						File artifactFile = artifactResult.getArtifact().getFile();
						JarFile artifactJarFile = null;
						BufferedInputStream configFileInputStream = null;
						Buffer buffer = null;
						try {
							artifactJarFile = new JarFile(artifactFile);
							configFileInputStream = new BufferedInputStream(artifactJarFile
									.getInputStream(artifactJarFile.getJarEntry(serviceName + ".json")));
							buffer = Buffer.buffer();
							int data;
							while ((data = configFileInputStream.read()) != -1) {
								buffer.appendByte(((Integer) data).byteValue());
							}
							blockingFuture.complete(buffer);
						} catch (IOException e) {
							logger.error(e.getMessage(), e);
							blockingFuture.fail(e);
						} finally {
							try {
								if (configFileInputStream != null) {
									configFileInputStream.close();
								}
								if (artifactJarFile != null) {
									artifactJarFile.close();
								}
							} catch (IOException e) {
								logger.error(e.getMessage(), e);
							}
						}
					} else {
						if (artifactResult == null) {
							blockingFuture.fail("Fetch artifact failed!");
						} else {
							blockingFuture.fail(artifactResult.getExceptions().get(0));
						}
					}
				}, ares -> {
					if (ares.succeeded()) {
						Buffer buffer = ares.result();
						JsonObject result = new JsonObject(buffer.toString());
						message.reply(result);
					} else {
						message.fail(500, ares.cause().getMessage());
					}
				});
	}

}
