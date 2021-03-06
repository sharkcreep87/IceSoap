package com.alexgilleran.icesoap.request.impl;

import java.util.List;

import android.os.AsyncTask;

import com.alexgilleran.icesoap.envelope.SOAPEnvelope;
import com.alexgilleran.icesoap.observer.SOAPListObserver;
import com.alexgilleran.icesoap.observer.registry.ListObserverRegistry;
import com.alexgilleran.icesoap.parser.IceSoapListParser;
import com.alexgilleran.icesoap.parser.ItemObserver;
import com.alexgilleran.icesoap.parser.impl.IceSoapListParserImpl;
import com.alexgilleran.icesoap.request.ListRequest;
import com.alexgilleran.icesoap.request.SOAPRequester;

/**
 * Implementation of {@link ListRequest}.
 * 
 * @author Alex Gilleran
 * 
 * @param <ResultType>
 *            The type of the contents of the list to retrieve.
 */
public class ListRequestImpl<ResultType, SOAPFaultType> extends RequestImpl<List<ResultType>, SOAPFaultType> implements
		ListRequest<ResultType, SOAPFaultType> {
	/** The parser to use to parse the result. */
	private IceSoapListParser<ResultType> parser;
	/** The registry to use to dispatch item-related events. */
	private ListObserverRegistry<ResultType, SOAPFaultType> itemRegistry = new ListObserverRegistry<ResultType, SOAPFaultType>();

	/**
	 * Creates a new request, automatically creating the parser.
	 * 
	 * @param url
	 *            The URL to post the request to.
	 * @param soapEnv
	 *            The SOAP envelope to send, as a {@link SOAPEnvelope}.
	 * @param soapAction
	 *            The SOAP Action to pass in the HTTP header - can be null.
	 * @param resultClass
	 *            The class of the type to return from the request.
	 * @param soapFaultClass
	 *            The class of the SOAPFault that will be returned if one is
	 *            encountered.
	 * @param requester
	 *            The implementation of {@link SOAPRequester} to use for
	 *            requests.
	 */
	protected ListRequestImpl(String url, SOAPEnvelope soapEnv, String soapAction, Class<ResultType> resultClass,
			Class<SOAPFaultType> soapFaultClass, SOAPRequester requester) {
		this(url, soapEnv, new IceSoapListParserImpl<ResultType>(resultClass), soapAction, soapFaultClass, requester);
	}

	/**
	 * Creates a new list request.
	 * 
	 * @param url
	 *            The URL to post the request to
	 * @param soapEnv
	 *            The SOAP envelope to send, as a {@link SOAPEnvelope}.
	 * @param parser
	 *            The {@link IceSoapListParser} to use to parse the response.
	 * @param soapAction
	 *            The SOAP Action to pass in the HTTP header - can be null.
	 * @param requester
	 *            The implementation of {@link SOAPRequester} to use for
	 *            requests.
	 */
	protected ListRequestImpl(String url, SOAPEnvelope soapEnv, IceSoapListParser<ResultType> parser,
			String soapAction, Class<SOAPFaultType> soapFaultClass, SOAPRequester requester) {
		super(url, soapEnv, soapAction, parser, soapFaultClass, requester);

		this.parser = parser;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute(SOAPListObserver<ResultType, SOAPFaultType> observer) {
		registerObserver(observer);
		execute();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerObserver(SOAPListObserver<ResultType, SOAPFaultType> observer) {
		super.registerObserver(observer);

		itemRegistry.registerObserver(observer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deregisterObserver(SOAPListObserver<ResultType, SOAPFaultType> observer) {
		super.deregisterObserver(observer);

		itemRegistry.deregisterObserver(observer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected AsyncTask<Void, ResultType, List<ResultType>> createTask() {
		return new ListRequestTask();
	}

	/**
	 * Subclass of {@link RequestImpl} RequestTask that caters for mid-request
	 * events on the UI thread through the use of progress updates.
	 */
	private class ListRequestTask extends RequestTask<ResultType> {
		/**
		 * {@inheritDoc}
		 * 
		 * Adds an item observer to the list parser so we can take the new item
		 * events from the parser, then re-broadcast them to the request's
		 * observers on the UI thread.
		 */
		@Override
		protected void onPreExecute() {
			parser.registerItemObserver(itemObserver);
		}

		/**
		 * Sends notifications about new items on the UI thread.
		 * 
		 * @param item
		 *            The item that's just been parsed, on index 0 - all other
		 *            indexes are unused.
		 */
		@Override
		protected void onProgressUpdate(ResultType... item) {
			itemRegistry.notifyNewItem(ListRequestImpl.this, item[0]);
		}

		/**
		 * Parser observer used to catch new items from the parser, then use the
		 * {@link AsyncTask#publishProgress(Object...))} to re-broadcast on the
		 * UI thread.
		 */
		private ItemObserver<ResultType> itemObserver = new ItemObserver<ResultType>() {
			@SuppressWarnings("unchecked")
			@Override
			public void onNewItem(ResultType item) {
				publishProgress(item);
			}
		};
	}
}
