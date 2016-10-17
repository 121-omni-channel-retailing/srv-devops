package otocloud.server.common;

import io.vertx.core.json.JsonObject;

import java.net.UnknownHostException;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/17/15.
 */
public class MessageUtils {


    public static String getServerDeployId(JsonObject serverRegisterInfo){
        JsonObject serverInfo = serverRegisterInfo.getJsonObject("serverInfo");
        String deploymentId = serverInfo.getString("deploymentId");
        return deploymentId;
    }

    public static String getServerDeployIdOfApp(JsonObject appRegisterInfo){
        JsonObject serverInfo = appRegisterInfo.getJsonObject("serverInfo");
        String deploymentId = serverInfo.getString("deploymentId");
        return deploymentId;
    }

    public static JsonObject createRegisterServerInfo(String containerDeploymentId){
        RegisterServerInfoBuilder registerInfoBuilder = new RegisterServerInfoBuilder();
        try {
            registerInfoBuilder.serverInfo().deploymentId(containerDeploymentId).ip(InetAddressUtils.getLocalHostName());

        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        registerInfoBuilder.serverStatus().action("deploy").status("success");

        return registerInfoBuilder.create();
    }

    public static JsonObject createContainerDeployment(String containerDeploymentId) {
        return createContainerDeployment(containerDeploymentId, "success");
    }

    public static JsonObject createContainerDeployment(String containerDeploymentId,
        String status) {
        JsonObject deploymentResult = new JsonObject();
        deploymentResult.put("action", "deploy");
        deploymentResult.put("containerId", containerDeploymentId);
        deploymentResult.put("status", status);
        return deploymentResult;
    }

    public static JsonObject createServiceDeployment(String containerDeploymentId, String serviceId,
        String status) {
        JsonObject deploymentResult = new JsonObject();
        deploymentResult.put("action", "deploy");
        deploymentResult.put("containerId", containerDeploymentId);
        deploymentResult.put("serviceId", serviceId);
        deploymentResult.put("status", status);
        return deploymentResult;
    }

    public static JsonObject createWebServer() {
        JsonObject gav =
            new JsonObject().put("groupId", "otocloud").put("artifactId", "otocloud-webserver")
                .put("version", "1.0-SNAPSHOT");
        JsonObject deployWebServer = new JsonObject().put("action", "deploy").put("gav", gav)
            .put("main", "otocloud.webserver.verticle.web-server-verticle");

        return deployWebServer;
    }

    public static JsonObject createTestWebServer() {
        JsonObject gav =
            new JsonObject().put("groupId", "otocloud.server").put("artifactId", "otocloud-service-emulator")
                .put("version", "1.0-SNAPSHOT");
        JsonObject deployWebServer = new JsonObject().put("action", "deploy").put("gav", gav)
            .put("main", "my-verticle");

        return deployWebServer;
    }



}
