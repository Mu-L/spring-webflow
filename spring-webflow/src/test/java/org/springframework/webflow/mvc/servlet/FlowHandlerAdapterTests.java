package org.springframework.webflow.mvc.servlet;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.core.FlowException;
import org.springframework.webflow.core.collection.LocalAttributeMap;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.execution.FlowExecutionOutcome;
import org.springframework.webflow.execution.repository.NoSuchFlowExecutionException;
import org.springframework.webflow.executor.FlowExecutionResult;
import org.springframework.webflow.executor.FlowExecutor;
import org.springframework.webflow.test.MockFlowExecutionKey;

public class FlowHandlerAdapterTests extends TestCase {
	private FlowHandlerAdapter flowHandlerAdapter;
	private FlowExecutor flowExecutor;
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private ServletExternalContext context;
	private FlowHandler flowHandler;
	private LocalAttributeMap<Object> flowInput = new LocalAttributeMap<Object>();
	private boolean handleException;
	private boolean handleExecutionOutcome;
	private MockFlashMapManager flashMapManager = new MockFlashMapManager();

	protected void setUp() throws Exception {
		flowExecutor = EasyMock.createMock(FlowExecutor.class);
		flowHandlerAdapter = new FlowHandlerAdapter() {
			protected ServletExternalContext createServletExternalContext(HttpServletRequest request,
					HttpServletResponse response) {
				return context;
			}
		};
		flowHandlerAdapter.setFlowExecutor(flowExecutor);
		MockServletContext servletContext = new MockServletContext();
		StaticWebApplicationContext applicationContext = new StaticWebApplicationContext();
		applicationContext.setServletContext(servletContext);
		flowHandlerAdapter.setApplicationContext(applicationContext);
		flowHandlerAdapter.afterPropertiesSet();

		flowHandler = new FlowHandler() {
			public MutableAttributeMap<Object> createExecutionInputMap(HttpServletRequest request) {
				assertEquals(FlowHandlerAdapterTests.this.request, request);
				return flowInput;
			}

			public String getFlowId() {
				return "foo";
			}

			public String handleExecutionOutcome(FlowExecutionOutcome outcome, HttpServletRequest request,
					HttpServletResponse response) {
				if (handleExecutionOutcome) {
					return "/home";
				} else {
					return null;
				}
			}

			public String handleException(FlowException e, HttpServletRequest request, HttpServletResponse response) {
				if (handleException) {
					return "error";
				} else {
					return null;
				}
			}
		};
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		context = new ServletExternalContext(servletContext, request, response, flowHandlerAdapter.getFlowUrlHandler());
		request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, flashMapManager);
	}

	public void testLaunchFlowRequest() throws Exception {
		setupRequest("/springtravel", "/app", "/whatever", "GET");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowRequestEndsAfterProcessing() throws Exception {
		setupRequest("/springtravel", "/app", "/whatever", "GET");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		flowExecutor.launchExecution("foo", flowInput, context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		assertEquals("/springtravel/app/foo?bar=baz", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowRequestEndsAfterProcessingAjaxRequest() throws Exception {
		setupRequest("/springtravel", "/app", "/whatever", "GET");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		context.setAjaxRequest(true);
		flowExecutor.launchExecution("foo", flowInput, context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		request.addHeader("Accept", "text/html;type=ajax");
		flowHandlerAdapter.handle(request, response, flowHandler);
		assertEquals("/springtravel/app/foo?bar=baz", response.getHeader("Spring-Redirect-URL"));
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testResumeFlowRequest() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "POST");
		request.addParameter("execution", "12345");
		flowExecutor.resumeExecution("12345", context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "123456");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testResumeFlowRequestEndsAfterProcessing() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "POST");
		request.addParameter("execution", "12345");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		flowExecutor.resumeExecution("12345", context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		ModelAndView mv = flowHandlerAdapter.handle(request, response, flowHandler);
		assertNull(mv);
		assertEquals("/springtravel/app/foo?bar=baz", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testResumeFlowRequestEndsAfterProcessingFlowCommittedResponse() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "POST");
		request.addParameter("execution", "12345");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		flowExecutor.resumeExecution("12345", context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		context.recordResponseComplete();
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		ModelAndView mv = flowHandlerAdapter.handle(request, response, flowHandler);
		assertNull(mv);
		assertEquals(null, response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExecutionRedirect() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestFlowExecutionRedirect();
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/app/foo?execution=12345", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithDefinitionRedirect() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		LocalAttributeMap<Object> input = new LocalAttributeMap<Object>();
		input.put("baz", "boop");
		context.requestFlowDefinitionRedirect("bar", input);
		flowExecutor.launchExecution("foo", flowInput, context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		ModelAndView mv = flowHandlerAdapter.handle(request, response, flowHandler);
		assertNull(mv);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/app/bar?baz=boop", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalHttpRedirect() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("https://www.paypal.com");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("https://www.paypal.com", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalHttpsRedirect() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("https://www.paypal.com");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("https://www.paypal.com", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectServletRelative() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("servletRelative:bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/app/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectServletRelativeWithSlash() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("servletRelative:/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/app/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectContextRelative() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("contextRelative:bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectContextRelativeWithSlash() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("contextRelative:/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectServerRelative() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("serverRelative:bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectServerRelativeWithSlash() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("serverRelative:/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testLaunchFlowWithExternalRedirectNotHttp10Compatible() throws Exception {
		flowHandlerAdapter.setRedirectHttp10Compatible(false);
		setupRequest("/springtravel", "/app", "/foo", "GET");
		context.requestExternalRedirect("serverRelative:/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals(303, response.getStatus());
		assertEquals("/bar", response.getHeader("Location"));
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testSwf1385DefaultServletExternalRedirect() throws Exception {
		setupRequest("/springtravel", "/foo", null, "GET");
		context.requestExternalRedirect("/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testSwf1385DefaultServletExternalRedirectDeviation() throws Exception {
		// Deviation from the default case:
		// In some containers the default behavior can be switched so that the contents of the URI after
		// the context path is in the path info while the servlet path is empty.
		setupRequest("/springtravel", "", "/foo", "GET");
		context.requestExternalRedirect("/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testSwf1385DefaultServletExternalRedirectServletRelative() throws Exception {
		setupRequest("/springtravel", "/foo", null, "GET");
		context.requestExternalRedirect("/bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testExternalRedirectServletRelativeWithDefaultServletMapping() throws Exception {
		setupRequest("/springtravel", "/foo", null, "GET");
		context.requestExternalRedirect("servletRelative:bar");
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowExecutionResult result = FlowExecutionResult.createPausedResult("foo", "12345");
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
		assertEquals("/springtravel/foo/bar", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testRemoteHost() throws Exception {
		assertFalse(flowHandlerAdapter.isRemoteHost("https://url.somewhere.com"));
		assertFalse(flowHandlerAdapter.isRemoteHost("/path"));
		assertFalse(flowHandlerAdapter.isRemoteHost("http://url.somewhereelse.com"));

		flowHandlerAdapter.setHosts(new String[] {"url.somewhere.com"});

		assertFalse(flowHandlerAdapter.isRemoteHost("https://url.somewhere.com"));
		assertFalse(flowHandlerAdapter.isRemoteHost("/path"));
		assertTrue(flowHandlerAdapter.isRemoteHost("http://url.somewhereelse.com"));

	}

	public void testDefaultHandleFlowException() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		Map<String, String> parameters = new HashMap<String, String>();
		request.setParameters(parameters);
		flowExecutor.launchExecution("foo", flowInput, context);
		FlowException flowException = new FlowException("Error") {
		};
		EasyMock.expectLastCall().andThrow(flowException);
		EasyMock.replay(new Object[] { flowExecutor });
		try {
			flowHandlerAdapter.handle(request, response, flowHandler);
			fail("Should have thrown exception");
		} catch (FlowException e) {
			assertEquals(flowException, e);
		}
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testDefaultHandleNoSuchFlowExecutionException() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		request.addParameter("execution", "12345");
		flowExecutor.resumeExecution("12345", context);
		FlowException flowException = new NoSuchFlowExecutionException(new MockFlowExecutionKey("12345"), null);
		EasyMock.expectLastCall().andThrow(flowException);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		assertEquals("/springtravel/app/foo", response.getRedirectedUrl());
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testDefaultHandleNoSuchFlowExecutionExceptionAjaxRequest() throws Exception {
		setupRequest("/springtravel", "/app", "/foo", "GET");
		request.addParameter("execution", "12345");
		flowExecutor.resumeExecution("12345", context);
		FlowException flowException = new NoSuchFlowExecutionException(new MockFlowExecutionKey("12345"), null);
		EasyMock.expectLastCall().andThrow(flowException);
		EasyMock.replay(new Object[] { flowExecutor });
		context.setAjaxRequest(true);
		request.addHeader("Accept", "text/html;type=ajax");
		flowHandlerAdapter.handle(request, response, flowHandler);
		assertEquals("/springtravel/app/foo", response.getHeader("Spring-Redirect-URL"));
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testHandleFlowOutcomeCustomFlowHandler() throws Exception {
		doHandleFlowServletRedirectOutcome();
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testHandleFlowExceptionCustomFlowHandler() throws Exception {
		handleException = true;
		final FlowException flowException = new FlowException("Error") {
		};
		setupRequest("/springtravel", "/app", "/foo", "GET");
		flowExecutor.launchExecution("foo", flowInput, context);
		EasyMock.expectLastCall().andThrow(flowException);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
	}

	public void testHandleFlowServletRedirectOutcomeWithoutFlash() throws Exception {
		doHandleFlowServletRedirectOutcome();
		assertNull(flashMapManager.getFlashMap());
	}

	public void testHandleFlowServletRedirectOutcomeWithFlash() throws Exception {
		flowHandlerAdapter.setSaveOutputToFlashScopeOnRedirect(true);
		doHandleFlowServletRedirectOutcome();
		assertEquals("baz", flashMapManager.getFlashMap().get("bar"));
		assertEquals("/springtravel/app/home", flashMapManager.getFlashMap().getTargetRequestPath());
	}

	private void doHandleFlowServletRedirectOutcome() throws Exception {
		handleExecutionOutcome = true;
		setupRequest("/springtravel", "/app", "/foo", "GET");
		flowExecutor.launchExecution("foo", flowInput, context);
		LocalAttributeMap<Object> output = new LocalAttributeMap<Object>();
		output.put("bar", "baz");
		FlowExecutionOutcome outcome = new FlowExecutionOutcome("finish", output);
		FlowExecutionResult result = FlowExecutionResult.createEndedResult("foo", outcome);
		EasyMock.expectLastCall().andReturn(result);
		EasyMock.replay(new Object[] { flowExecutor });
		flowHandlerAdapter.handle(request, response, flowHandler);
		EasyMock.verify(new Object[] { flowExecutor });
	}

	private void setupRequest(String contextPath, String servletPath, String pathInfo, String method) {
		request.setContextPath(contextPath);
		request.setServletPath(servletPath);
		request.setPathInfo(pathInfo);
		request.setRequestURI(contextPath + servletPath + (pathInfo == null ? "" : pathInfo));
		request.setMethod(method);
	}

	private static class MockFlashMapManager implements FlashMapManager {

		private FlashMap flashMap;

		public FlashMap retrieveAndUpdate(HttpServletRequest request, HttpServletResponse response) {
			throw new UnsupportedOperationException();
		}

		public void saveOutputFlashMap(FlashMap flashMap, HttpServletRequest request, HttpServletResponse response) {
			this.flashMap = flashMap;
		}

		public FlashMap getFlashMap() {
			return flashMap;
		}
	}
}
