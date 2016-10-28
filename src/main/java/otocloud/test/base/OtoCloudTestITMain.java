package otocloud.test.base;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import otocloud.auth.verticle.AuthService;
import otocloud.framework.core.OtoCloudServiceDepOptions;
import otocloud.webserver.WebServerVerticle;

/**
 * 集成测试基类，完成webserver和当前开发service部署，在开发环境中启动集成测试环境。<p>
 * 在测试环境中，可以向虚拟机传递System property的方法设置此参数。
 * 例如：-Dit.webserver.name="121webserver-N02" -Dit.port=8081
 * 缺省DEFAULT_PORT = 8081，DEFAULT_WEBSERVERNAME = "121webserver-N01"<p>
 * 
 * 在实际应用中，只需要实现该类的两个抽象函数和写一个main函数即可，示例如下：<br>
 * <pre>{@code
 * public class AppCatalogOrderServiceTestITMain extends OtoCloudTestITMain {
 * 
 * public static void main(String[] args) {
 *   new AppCatalogOrderServiceTestITMain().run();
 * }
 * 
 * Override
 * public String getServiceConfigFileName() {
 *   return "otocloud-app-shopping.json";
 * }
 * 
 *  Override
 *  public String getServiceClassName() {
 *   return AppCatalogOrderService.class.getName();
 *  }
 * }
 * }</pre>
 * 
 * @version 1.0
 * @author liuxba, 2015-11-19
 *
 */

public abstract class OtoCloudTestITMain {
	static Vertx vertx;
    static String webServerDeployId = null;
    static String authServiceDeployId = null;
    static String appServiceDeployId = null;
    
    static final int DEFAULT_PORT = 8081;
    static final String DEFAULT_WEBSERVERNAME = "121webserver-N01";
    
    static Logger logger = LoggerFactory.getLogger(OtoCloudTestITMain.class.getName());	
    
    /**
     * 发布WebServer并部署Service，在测试环境中启动121Cloud运行环境，完成集成测试。
     * <p>
     * 在子类中可以在main函数中调用此函数完成集成测试环境准备，示例代码如下：
     * <pre>
     * public static void main(String[] args) {
     *   new AppCatalogOrderServiceTestITMain().run();
     * }</pre>
     */
	public final void run() {
		vertx = Vertx.vertx();
        Future<Void> future = Future.future();

        startDeployWebServer(future);
        
        future.setHandler(result -> {
            if (result.succeeded()) {
				startDeployAuthService(ret -> {
					if (ret.succeeded()) {
						authServiceDeployId = ret.result();
						logger.debug("部署Auth Service成功！ authServiceDeployId：" + authServiceDeployId);
						startDeployService(fut -> {
							if (fut.succeeded()) {
								appServiceDeployId = fut.result();
								logger.debug("部署App Service成功！ appServiceDeployId：" + appServiceDeployId);
							}
						});
					}
				});
            }
        });
	}
    
	/**
	 * 部署webServer。
	 * 
	 * @param future
	 */
	private void startDeployWebServer(Future<Void> future) {
        JsonObject deployConfig = new JsonObject();
        deployConfig.put("http.port", Integer.getInteger("it.port", DEFAULT_PORT));
        deployConfig.put("webserver_name", System.getProperty("it.webserver.name", DEFAULT_WEBSERVERNAME));
        logger.debug("deployConfig-->" + deployConfig);
        
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(deployConfig);

        vertx.deployVerticle(WebServerVerticle.class.getName(), deploymentOptions, result -> {
            String deployId = result.result();
            webServerDeployId = deployId;
            logger.debug("WebService 部署成功 [" + deployId + "]");
            future.complete();
        });
    }
	
	/**
	 * 部署用户认证服务
	 * 
	 * @param handler
	 */
    private static void startDeployAuthService(Handler<AsyncResult<String>> handler) {

        Future<JsonObject> future = Future.future();

        //TODO 需要考虑在部署环境下的测试，尤其是对于配置文件的读取。
        String cfgFilePath = System.getProperty("user.dir") + "/target/config/" + "otocloud-auth.json";
        logger.debug("cfgFilePath-->" + cfgFilePath);
        
        Vertx.vertx().fileSystem().readFile(cfgFilePath, result -> {
            if (result.succeeded()) {
                String fileContent = result.result().toString();
                System.out.println(fileContent);
                JsonObject srvCfg = new JsonObject(fileContent);

                future.complete(srvCfg);
            }
        });

        future.setHandler(result -> {
            JsonObject deployConfig = result.result();
            JsonObject options = deployConfig.getJsonObject("options");
            OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions();
            deploymentOptions.fromJson(options);
            vertx.deployVerticle(AuthService.class.getName(), deploymentOptions, handler);
        });

    }
    
	/**
	 * 部署service组件。
	 * 部署service组件之前必须在子类中覆写{@link #getServiceConfigFileName()}和{@link #getServiceClassName()}函数。
	 * 
	 * @param handler 回调函数处理器。
	 */
    private void startDeployService(Handler<AsyncResult<String>> handler) {

        Future<JsonObject> future = Future.future();
       
        String cfgFilePath = System.getProperty("user.dir") + "/target/config/" + getServiceConfigFileName();
        logger.debug("cfgFilePath-->" + cfgFilePath);

        vertx.fileSystem().readFile(cfgFilePath, result -> {
            if (result.succeeded()) {
                String fileContent = result.result().toString();
                logger.debug("serviceConfigFileName:" + cfgFilePath + "-->" + fileContent);
                JsonObject srvCfg = new JsonObject(fileContent);
                future.complete(srvCfg);
            }
        });

        future.setHandler(result -> {
            JsonObject deployConfig = result.result();
            JsonObject options = deployConfig.getJsonObject("options");
            OtoCloudServiceDepOptions deploymentOptions = new OtoCloudServiceDepOptions();
            deploymentOptions.fromJson(options);
			vertx.deployVerticle(getServiceClassName(), deploymentOptions, handler);
        });
    }
    
    /**
     * 这个函数应该在子类中覆写。
     * 例如：如果src/main/config下的配置文件名为“otocloud-app-shopping.json”，则该函数实现为：
     * <pre>
     * return "otocloud-app-shopping.json";
     * </pre>
     * 
     * @return src/main/config中的服务配置文件名称。
     */
    public abstract String getServiceConfigFileName();
    
    /**
     * 返回Service类的class名称，子类必须覆写该函数。
     * 例如，若service类名为“AppCatalogOrderService”，则该函数实现为：
     * <pre>
     * return AppCatalogOrderService.class.getName();
     * </pre>
     * 
     * @return Service类的class名称
     */
    public abstract String getServiceClassName();
    
}
