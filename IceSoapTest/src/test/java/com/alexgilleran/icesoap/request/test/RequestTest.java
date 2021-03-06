package com.alexgilleran.icesoap.request.test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.alexgilleran.icesoap.exception.SOAPException;
import com.alexgilleran.icesoap.exception.XMLParsingException;
import com.alexgilleran.icesoap.observer.SOAP11Observer;
import com.alexgilleran.icesoap.observer.SOAPObserver;
import com.alexgilleran.icesoap.request.Request;
import com.alexgilleran.icesoap.request.SOAP11Request;
import com.alexgilleran.icesoap.request.SOAPRequester;
import com.alexgilleran.icesoap.request.impl.RequestFactoryImpl;
import com.alexgilleran.icesoap.request.test.xmlclasses.CustomSOAP12Fault;
import com.alexgilleran.icesoap.request.test.xmlclasses.Response;
import com.alexgilleran.icesoap.soapfault.SOAP11Fault;
import com.xtremelabs.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RequestTest extends BaseRequestTest<Response> {
	private Response expectedResponse = new Response(1, "Text");
	private SOAP11Fault fault = new SOAP11Fault(SampleResponse.SOAP11_FAULT_CODE, SampleResponse.SOAP11_FAULT_STRING,
			SampleResponse.SOAP11_FAULT_ACTOR);

	@SuppressWarnings("unchecked")
	@Test
	public void testRequest() throws IOException, XMLParsingException {
		// Set up a parser for the response
		SOAP11Request<Response> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(), SOAP_ACTION,
				Response.class);

		// Create a mock observer and put in the expected call (we expect it to
		// come back with the request)
		SOAP11Observer<Response> mockObserver = createMock(SOAP11Observer.class);
		mockObserver.onCompletion(request);
		replay(mockObserver);

		// Register the observer to the request
		request.registerObserver(mockObserver);

		// Do the request
		doRequest(request, SampleResponse.getSingleResponse());

		// Verify that it did what it was supposed to
		verify(mockObserver);

		// Verify the parsed object was correct.
		assertEquals(expectedResponse, request.getResult());
	}

	/**
	 * Ensures a SOAPException is thrown if an IOException occurs when making a
	 * request - including timeouts etc.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testRequestWithException() throws IOException, XMLParsingException {
		// Set up a parser for the response
		SOAP11Request<Response> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(), SOAP_ACTION,
				Response.class);

		// Create a mock observer and put in the expected call (we expect it to
		// come back with the request)
		SOAP11Observer<Response> mockObserver = createMock(SOAP11Observer.class);
		mockObserver.onException(eq(request), isA(SOAPException.class));
		replay(mockObserver);

		// Register the observer to the request
		request.registerObserver(mockObserver);

		// Do the request
		doExceptionRequest(request, SampleResponse.getSingleResponse());

		// Verify that it did what it was supposed to
		verify(mockObserver);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDebugMode() throws IOException, XMLParsingException {
		SOAP11Request<Response> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(), SOAP_ACTION,
				Response.class);

		// Set debug to true
		request.setDebugMode(true);

		// Do a normal response test to make sure that debug mode doesn't ruin
		// it
		SOAP11Observer<Response> mockObserver = createMock(SOAP11Observer.class);
		mockObserver.onCompletion(request);
		replay(mockObserver);
		request.registerObserver(mockObserver);
		doRequest(request, SampleResponse.getSingleResponse());
		verify(mockObserver);
		assertEquals(expectedResponse, request.getResult());

		// Make sure the response/request were correctly collected
		assertEquals(getDummyEnvelope().toString(), request.getRequestXML());
		assertEquals(SampleResponse.SINGLE_RESPONSE, request.getResponseXML());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFailedRequestSOAP11() throws IOException, XMLParsingException {
		// Set up a parser for the response
		SOAP11Request<Response> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(), SOAP_ACTION,
				Response.class);

		// Create a mock observer and put in the expected call (we expect it to
		// come back with the request)
		SOAP11Observer<Response> mockObserver = createMock(SOAP11Observer.class);
		mockObserver.onException(eq(request), isA(SOAPException.class));
		replay(mockObserver);

		// Register the observer to the request
		request.registerObserver(mockObserver);

		// Do the request
		doFailedRequest(request, SampleResponse.getSoap11Fault());

		// Verify that it did what it was supposed to
		verify(mockObserver);

		// Verify the parsed object was correct.
		assertEquals(fault, request.getSOAPFault());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFailedRequestNon500Code() throws IOException, XMLParsingException {

		SOAP11Request<Response> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(), SOAP_ACTION,
				Response.class);

		SOAP11Observer<Response> mockObserver = createMock(SOAP11Observer.class);
		mockObserver.onException(eq(request), isA(SOAPException.class));
		replay(mockObserver);

		// Register the observer to the request
		request.registerObserver(mockObserver);

		// Do the request
		doFailedRequest(request, SampleResponse.getSoap11Fault(), 307);

		// Verify that it did what it was supposed to
		verify(mockObserver);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFailedRequestSOAP12() throws IOException, XMLParsingException {
		// Set up a parser for the response
		Request<Response, CustomSOAP12Fault> request = getRequestFactory().buildRequest(DUMMY_URL, getDummyEnvelope(),
				SOAP_ACTION, Response.class, CustomSOAP12Fault.class);

		// Create a mock observer and put in the expected call (we expect it to
		// come back with the request)
		SOAPObserver<Response, CustomSOAP12Fault> mockObserver = createMock(SOAPObserver.class);
		mockObserver.onException(eq(request), isA(SOAPException.class));
		replay(mockObserver);

		// Register the observer to the request
		request.registerObserver(mockObserver);

		// Do the request
		doFailedRequest(request, SampleResponse.getSoap12Fault());

		// Verify that it did what it was supposed to
		verify(mockObserver);

		// Verify the parsed object was correct.
		CustomSOAP12Fault fault = request.getSOAPFault();

		assertEquals(fault.getCode(), SampleResponse.SOAP12_FAULT_CODE);
		assertEquals(fault.getNode(), SampleResponse.SOAP12_FAULT_NODE);
		assertEquals(fault.getRole(), SampleResponse.SOAP12_FAULT_ROLE);
		assertEquals(fault.getSubCode(), SampleResponse.SOAP12_FAULT_SUBCODE);
		assertEquals(fault.getReasons().get(0).getReason(), SampleResponse.SOAP12_FAULT_REASON);
		assertEquals(fault.getReasons().get(0).getLang(), SampleResponse.SOAP12_FAULT_REASON_LANG);
		assertEquals(fault.getLineNumber(), SampleResponse.SQL_MESSAGE_LINE_NUMBER);
		assertEquals(fault.getMessage(), SampleResponse.SQL_MESSAGE_MESSAGE);
		assertEquals(fault.getNumber(), SampleResponse.SQL_MESSAGE_NUMBER);
		assertEquals(fault.getSource(), SampleResponse.SQL_MESSAGE_SOURCE);
		assertEquals(fault.getState(), SampleResponse.SQL_MESSAGE_STATE);
	}

	@Test
	public void test401InDebugMode() throws IOException {
		// SOAPRequester soapRequester = new ApacheSOAPRequester() {
		// @Override
		// protected HttpClient buildHttpClient() {
		// HttpClient mockClient = createMock(HttpClient.class);
		// HttpResponse mockResponse = createMock(HttpResponse.class);
		//
		// try {
		// HttpPost post = super.buildPostRequest("blah", envelope, "POST");
		//
		// expect(mockClient.execute(post)).andReturn(response);
		// expect(mockResponse.getHttpStatus()).andReturn(401);
		//
		//
		// } catch (Exception e) {
		// throw new RuntimeException(e);
		// }
		//
		// return mockClient;
		// }
		// };

		SOAPRequester mockRequester = createMock(SOAPRequester.class);
		com.alexgilleran.icesoap.request.impl.Response dummyResponse = new com.alexgilleran.icesoap.request.impl.Response(
				new ByteArrayInputStream("".getBytes()), 401);
		expect(mockRequester.doSoapRequest(getDummyEnvelope(), "", "")).andReturn(dummyResponse);
		replay(mockRequester);

		SOAP11Request<Response> request = new RequestFactoryImpl(mockRequester).buildRequest("", getDummyEnvelope(),
				"", Response.class);
		request.setDebugMode(true);
		
		request.execute();
	}
}
