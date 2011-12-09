package com.alexgilleran.icesoap.request;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.xmlpull.v1.XmlPullParserException;

import com.alexgilleran.icesoap.envelope.SOAPEnv;
import com.alexgilleran.icesoap.exception.SOAPException;
import com.alexgilleran.icesoap.observer.ObserverRegistry;
import com.alexgilleran.icesoap.observer.SOAPObserver;
import com.alexgilleran.icesoap.parser.Parser;
import com.alexgilleran.icesoap.parser.XPathXmlPullParser;
import com.alexgilleran.icesoap.requester.SOAPRequesterImpl;

import android.os.AsyncTask;
import android.os.Debug;
import android.util.Log;


public class RequestImpl<T> implements Request<T> {
	private ObserverRegistry<T> registry = new ObserverRegistry<T>();
	private Parser<T> parser;
	private String url;
	private SOAPEnv soapEnv;
	private RequestTask<?> currentTask = null;

	public RequestImpl(String url, Parser<T> aparser, SOAPEnv soapEnv) {
		this.parser = aparser;
		this.url = url;
		this.soapEnv = soapEnv;
	}

	protected AsyncTask<Void, ?, T> createTask() {
		RequestTask<?> currentTask = new RequestTask<Void>();
		return currentTask;
	}

	@Override
	public void cancel() {
		if (currentTask != null) {
			currentTask.cancel(true);
		}
	}

	protected InputStream getResponse() throws IOException {
		return SOAPRequesterImpl.getInstance().doSoapRequest(soapEnv, url);
	}

	protected Parser<T> getParser() {
		return parser;
	}

	@Override
	public void execute() throws SOAPException {
		createTask().execute();
	}

	@Override
	public T get() throws ExecutionException {
		try {
			return createTask().get(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new SOAPException(e);
		} catch (ExecutionException e) {
			throw new SOAPException(e);
		} catch (TimeoutException e) {
			throw new SOAPException(e);
		}
	}

	public void addListener(SOAPObserver<T> listener) {
		registry.addListener(listener);
	}

	public void removeListener(SOAPObserver<T> listener) {
		registry.removeListener(listener);
	}

	protected class RequestTask<E> extends AsyncTask<Void, E, T> {
		@Override
		protected void onPostExecute(T result) {
			registry.notifyListeners(result);
			currentTask = null;
		}

		@Override
		protected T doInBackground(Void... arg0) {
			Log.d("debug", "doInBackground started");
			XPathXmlPullParser xmlParser = new XPathXmlPullParser();

			// auto-detect the encoding from the stream
			try {
				if (!isCancelled()) {
					xmlParser.setInput(getResponse(), null);

					Log.d("debug", "doInBackground finished");
					return getParser().parse(xmlParser);
				}
			} catch (XmlPullParserException e) {
				throw new SOAPException(e);
			} catch (IOException e) {
				throw new SOAPException(e);
			}

			Log.d("debug", "doInBackground finished");
			return null;
		}
	}
}