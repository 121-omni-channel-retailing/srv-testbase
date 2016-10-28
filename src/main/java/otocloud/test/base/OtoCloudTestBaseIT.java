package otocloud.test.base;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.ValidatableResponse;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * 121Cloud集成测试基类，集成测试类继承该基类，完成121Cloud集成测试。集成测试用例通过WebServer访问Rest
 * 服务，基于REST返回结果进行验证。
 * 同时，该类完成每个testSuit的登录和测试完成之后的注销，并设置登录之后的session参数（sessionId，userOpenId，accessToken）。
 * 
 * <p>集成测试缺省host和port配置为：DEFAULT_HOST = "localhost"， DEFAULT_PORT = 8081
 * 在构建服务器测试环境中，可以向虚拟机传递System property的方法设置此参数。
 * 例如： -Dit.host="10.10.23.19" -Dit.port=8081
 * 
 * @version 1.0
 * @author liuxba, 2015-11-19
 *
 */
public class OtoCloudTestBaseIT {
    static Logger logger = LoggerFactory.getLogger(OtoCloudTestBaseIT.class.getName());	
    static final String DEFAULT_HOST = "localhost";
    static final int DEFAULT_PORT = 8081;
    
    private static final String LOGIN_URI = "/api/otocloud-auth/user-management/users/actions/login";
    private static final String LOGOUT_URI = "/api/otocloud-auth/user-management/users/actions/logout";
    private static final String USER_NAME = "otocloud-test";
    private static final String USER_PWD = "123456";
    
//    protected static String sessionId = null;
    protected static String userOpenId = null;
    protected static String accessToken = null;    

	@BeforeClass
	public final static void setUp() {
		String host = System.getProperty("it.host", DEFAULT_HOST);
		Integer port = Integer.getInteger("it.port", DEFAULT_PORT);
		
		RestAssured.baseURI = "http://" + host;
		RestAssured.port = port;
		
		logger.debug("集成测试host： " + host + ", port: " + port );	
		
		login();
	}
	
	/**
	 * 用缺省的账户登录，并设置登录session相关信息到静态变量，在测试中使用。
	 * 
	 */
    private static void login() {
    	logger.debug("login-->start...");
    	JsonObject loginObj = new JsonObject();
    	loginObj.put("userName", USER_NAME).put("password", USER_PWD);
    	
    	ValidatableResponse resp = 
	    	given()
	    		.contentType("application/json")
	    		.content(loginObj.encode())
	    	.when()
	    		.post(LOGIN_URI)
	    	.then()
	    		.assertThat()
	    		.statusCode(200)
	    		.body("user_name",equalTo(USER_NAME))
	    		.body("access_token", notNullValue())
//	    		.body("set-session", notNullValue())
	    		.body("user_openid", notNullValue())
	    		.log().all(true);
    	
//    	sessionId = resp.extract().path("set-session");
    	userOpenId = resp.extract().path("user_openid");
    	accessToken = resp.extract().path("access_token");

    	logger.debug("sessionId-->" + sessionId + ", userOpenId-->" + userOpenId);
    }
    
	@AfterClass
	public static final void tearDown() {
		logout();
		RestAssured.reset();
	}
	
	/**
	 * 完成测试之后，注销账户。
	 */
	private static void logout() {
		logger.debug("logout-->start...");
		JsonObject logoutObj = new JsonObject();
		logoutObj.put("userOpenId", userOpenId);
		given()
			.content(logoutObj.toString())
		.when()
			.post(LOGOUT_URI + "?token={accessToken}", accessToken)
		.then().assertThat()
			.statusCode(200)
			.log().all(true);
	}
}
