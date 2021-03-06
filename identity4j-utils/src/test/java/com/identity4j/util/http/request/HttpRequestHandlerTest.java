package com.identity4j.util.http.request;

import java.net.URI;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.identity4j.util.http.request.HttpRequestHandler.HTTPHook;
import com.identity4j.util.http.response.HttpResponse;

public class HttpRequestHandlerTest {

	private static final HttpRequestHandler HTTP_REQUEST_HANDLER = new HttpRequestHandler();
	
	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	@Test
	public void itShouldFetchDataWithStatusCode200ForValidContent() throws Exception {
		HttpResponse httpResponse = HTTP_REQUEST_HANDLER.handleRequestGet(new URI("https://www.google.com"), HTTPHook.EMPTY_HOOK);
		Assert.assertEquals("Should be HTTP status OK", 200,httpResponse.getHttpStatusCodes().getStatusCode().intValue());
	}
	
	@Test
	public void itShouldThrowHttpResponseExceptionWithStatusCode404ForNonExistingContent() throws Exception {
		HttpResponse httpResponse = HTTP_REQUEST_HANDLER.handleRequestGet(new URI("https://www.google.com/abc"), HTTPHook.EMPTY_HOOK);
		Assert.assertEquals("Should be HTTP status OK", 404,httpResponse.getHttpStatusCodes().getStatusCode().intValue());
	}
}
