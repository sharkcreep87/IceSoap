package com.alexgilleran.icesoap.envelope.impl;

import com.alexgilleran.icesoap.envelope.SOAPEnvelope;

/**
 * Concrete implementation of a SOAP 1.1 {@link SOAPEnvelope}. Automatically
 * sets up the basic namespaces, <envelope> tags etc, as well as creating a head
 * and body tag to be manipulated by decorators.
 * 
 * Note that this is <i>not</i> an abstract class - when you're creating a new
 * envelope, you can either extend this class to keep your envelope's logic
 * contained within its own class, or instantiate a new instance of this class
 * and build it up using public methods.
 * 
 * @author Alex Gilleran
 * 
 */
public class BaseSOAP11Envelope extends BaseSOAPEnvelope {
	/** URI for the Soap 1.1 Envelope Namespace. */
	public static final String NS_URI_SOAPENV = "http://schemas.xmlsoap.org/soap/envelope/";
	/** URI for the Soap 1.1 Encoding Namespace. */
	public static final String NS_URI_SOAPENC = "http://schemas.xmlsoap.org/soap/encoding/";

	/**
	 * Initialises the class - sets up the basic "soapenv", "soapenc", "xsd" and
	 * "xsi" namespaces present in all SOAP messages.
	 */
	public BaseSOAP11Envelope() {
		super(NS_URI_SOAPENV, NS_URI_SOAPENC);
	}
}
