package otocloud.test.base;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.concurrent.CountDownLatch;

import org.junit.AfterClass;
import org.junit.Before;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import otocloud.framework.core.OtoCloudServiceDepOptions;
import otocloud.framework.core.OtoCloudServiceForVerticleImpl;

/**
 * 单元测试基类，在测试之前，读取测试配置，并完成Verticle发布，单元测试需要直接集成该类即可。
 * <p/>
 * 本函数完成发布verticle，在verticle成功发布之后，才开始运行测试用例。<br>
 * 1. 在部署OTOCloud Verticle时，需要在/src/test/resources中配置otocloud-it-config.json，设置数据库连接。
 *    在部署verticle时，需要依据这个配置文件中的配置进行部署verticle。<br>
 * 2. 从测试性能上考虑，通过在service单例模式，确保service只部署一次。
 * 
 * @version 1.0
 * @author liuxba, 2015-11-19
 *
 */
public class OtoCloudTestBase<T extends OtoCloudServiceForVerticleImpl> {
	static final Logger logger = LoggerFactory.getLogger(OtoCloudTestBase.class);

	protected static Vertx vertx = null;
	private static OtoCloudServiceForVerticleImpl service = null;
	
	/**
	* 本方法发负责读取verticle配置，并发布verticle。通过service单例模式，虽然每运行测试用例之前需要运行此函数，
	* 从测试性能上考虑，通过service单例模式，确保每个testSuit verticle只部署一次.
	* 
	* @param context the test context.
	*/
	@Before
	public void setUp(TestContext context) throws IOException {
			//logger.debug("setUp--> start");
			if (service == null) {
				logger.debug("开始读取测试配置，并部署verticle");
				final Async async = context.async();
				VertxOptions vertxOption = new VertxOptions();
				vertxOption.setBlockedThreadCheckInterval(900000);
				vertx = Vertx.vertx(vertxOption);
			
				OtoCloudServiceDepOptions options = new OtoCloudServiceDepOptions(null, "");
				String testCfgFile = System.getProperty("user.dir") + "/target/config/"+"otocloud-it-config.json";
				CountDownLatch latch = new CountDownLatch(1);
				vertx.fileSystem().readFile(testCfgFile, result -> {
		    	    if (result.succeeded()) {
		    	    	String fileContent = result.result().toString(); 
		    	        logger.debug(fileContent);
		    	        
		    	        JsonObject testCfg = new JsonObject(fileContent);
		
		    	        options.fromJson(testCfg.getJsonObject("options"));
		    	        latch.countDown();
		    	        
		    	    } else {
		    	        logger.error(testCfgFile + "file not found" + result.cause()); 
		    	        latch.countDown();
		    	    }
				});		
				
				try {
					latch.await();
				} catch (InterruptedException e) {
					logger.error(e);
				}
				
				service = getService();
				vertx.deployVerticle(service, options, ret -> {
					async.complete();
				});
			} else {
				//logger.debug("测试实例已经配置好.");
			}
				
		}
	
	/**
	 * 从性能考虑，Service采用单例模式，必须使用getService()函数得到service，确保在service返回之前已经初始化。
	 * 
	 * @return {@link otocloud.framework.core.OtoCloudServiceForVerticleImpl}
	 */
	protected OtoCloudServiceForVerticleImpl getService() {
		if (service == null) {
			@SuppressWarnings("unchecked")
			Class<T> cls = (Class<T>)((ParameterizedType)this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
			try {
				service = (OtoCloudServiceForVerticleImpl)cls.newInstance();
			} catch (InstantiationException e) {
				logger.error(e);
			} catch (IllegalAccessException e) {
				logger.error(e);
			}
		}
		
		return service;
	}
	
	/**
	 * 测试完成之后，关闭vertx实例,service置空，下一个test suite重新初始化。
	 *
	 * @param context the test context
	 */
	@AfterClass
	public static void tearDown(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
		service = null;
	}
	
}