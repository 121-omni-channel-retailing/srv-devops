package otocloud.servermanager.server.container;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * 容器监控器
 * 
 * @author caojj1
 *
 */
public interface IContainerMonitor {
	/**
	 * 设置状态报告处理器,监控器会通过该处理器定时报告容器状态
	 * 
	 * @param handler
	 */
	void setStatusReportHandler(Handler<JsonObject> handler);

	/**
	 * 当监控器停止时，通知该处理器
	 * 
	 * @param disActiveHandler
	 */
	void setDisActiveHander(Handler<ContainerMonitor> disActiveHandler);

	/**
	 * 启动监控器
	 * 
	 * @param startFuture
	 */
	void start(Future<Void> startFuture);

	/**
	 * 停止监控器
	 */
	void stop();

	/**
	 * 检查是否在运行
	 * 
	 * @return
	 */
	public boolean isActive();
}
