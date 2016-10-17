package otocloud.servermanager.server.container;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.shareddata.Lock;

import java.util.Date;

import otocloud.servermanager.consts.ContainerConsts;
import otocloud.servermanager.util.RingQueue;

public class ContainerMonitor implements IContainerMonitor {

	private long statusFetchInterval = 5000;
	// private long statusReportInterval = 60000;
	private int blockCount = 10;
	private int disConnectCount = 100;

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private Vertx vertx;
	private JsonObject container;
	private Lock containerLock;

	private boolean active = false;
	private Handler<ContainerMonitor> disActiveHandler;
	private Handler<JsonObject> statusReportHandler;

	private NetClient monitorClient;
	private RingQueue<JsonObject> metricsQueue;
	private int delayCounter = 0;
	// private JsonObject oldSummaryStatus;

	private long delayCounterTimerID;
	private long statusReportTimerID;

	public ContainerMonitor(Vertx vertx, JsonObject container, Lock containerLock) {
		this.vertx = vertx;
		this.container = container;
		this.containerLock = containerLock;
		init();
	}

	private void init() {
		metricsQueue = new RingQueue<JsonObject>(256);
	}

	public void setStatusReportHandler(Handler<JsonObject> handler) {
		this.statusReportHandler = handler;
	}

	public void start(Future<Void> startFuture) {
		String ip = container.getString(ContainerConsts.ATT_IP);
		int monitorPort = container.getInteger(ContainerConsts.ATT_MONITOR_PORT);
		monitorClient = vertx.createNetClient().connect(monitorPort, ip, netClientAres -> {
			if (netClientAres.succeeded()) {
				NetSocket netSocket = netClientAres.result();
				/* StringBuilder metricsBuffer = new StringBuilder(); */
				RecordParser metricsParser = RecordParser.newDelimited("\n", buffer -> {
					JsonObject metrics = new JsonObject(buffer.toString());
					log.trace("Status received:[" + metrics.toString() + "]");
					metricsQueue.put(metrics);
				});
				netSocket.handler(buffer -> {
					delayCounter = 0;
					metricsParser.handle(buffer);
				});
				netSocket.closeHandler(v -> {
					this.stop();
				});
				netSocket.write(new JsonObject().put("statusInterval", statusFetchInterval).toString());
				/* 状态消息延迟计数，如果太长时间未收到状态消息，则断开连接 */
				delayCounterTimerID = vertx.setPeriodic(statusFetchInterval, l -> {
					++delayCounter;
					if (delayCounter == blockCount) {
						this.reportContainerStatus();
					}
					if (delayCounter >= disConnectCount) {
						ContainerMonitor.this.stop();
					}
				});
				active = true;
				/* 连接容器成功后，报告状态 */
				reportContainerStatus();
				startFuture.complete();
			} else {
				log.debug("Connect [" + ip + ":" + monitorPort + "] failed!", netClientAres.cause());
				// 关闭连接
				if (monitorClient != null) {
					monitorClient.close();
				}
				// 释放锁
				containerLock.release();
				// 报告一次状态
				reportContainerStatus();
				startFuture.fail(netClientAres.cause());
			}
		});
	}

	private void reportContainerStatus() {
		if (statusReportHandler != null) {
			JsonObject summaryStatus = getContainerSummaryStatus();
			statusReportHandler.handle(summaryStatus);
			log.trace("Status reported:[" + summaryStatus + "]");
		}
	}

	private JsonObject getContainerSummaryStatus() {
		JsonObject status = new JsonObject();
		if (this.isActive()) {
			if (this.delayCounter < blockCount)
				status.put(ContainerConsts.ATT_STATUS_CONNECTION, EConnectStatus.CONNECTED);
			else
				status.put(ContainerConsts.ATT_STATUS_CONNECTION, EConnectStatus.BLOCKED);
			// 负载状态
			status.put(ContainerConsts.ATT_STATUS_LOAD, ELoadStatus.FREE);
		} else {
			status.put(ContainerConsts.ATT_STATUS_CONNECTION, EConnectStatus.DISCONNECTED);
		}
		return status;
	}

	public JsonObject getStatus(Date from, Date to) {
		// TODO
		return null;
	}

	public JsonObject getStatusUpToNow(Date from) {
		// TODO
		return null;
	}

	public void stop() {
		if (active == true) {
			// 设置monitor的状态
			this.active = false;
			// 取消计时器
			vertx.cancelTimer(delayCounterTimerID);
			vertx.cancelTimer(statusReportTimerID);
			// 关闭连接
			if (monitorClient != null) {
				monitorClient.close();
			}
			// 释放锁
			containerLock.release();
			// 报告一次状态
			reportContainerStatus();
			// 执行关闭handler
			if (this.disActiveHandler != null) {
				this.disActiveHandler.handle(this);
			}
		}
	}

	public void setDisActiveHander(Handler<ContainerMonitor> disActiveHandler) {
		this.disActiveHandler = disActiveHandler;
	}

	public boolean isActive() {
		return active;
	}
}
