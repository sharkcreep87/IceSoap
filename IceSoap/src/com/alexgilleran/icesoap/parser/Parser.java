package com.alexgilleran.icesoap.parser;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;


/**
 * 
 * @author Alex Gilleran
 * 
 * @param <T>
 *            The class of the object to return. For instance, if I wanted to
 *            return a "Product" object from this parser, I would specify
 *            <Product> and override the resulting methods
 */
public interface Parser<T> {
	void addObserver(ParserObserver<T> observer);

	void removeObserver(ParserObserver<T> observer);

	/**
	 * Parses an object by looping through every child tag, calling parseTag()
	 * on each START_TAG event. Stops at the end of the parent tag and returns
	 * the object that has been parsed.
	 * 
	 * @return The object created by parsing the tag
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	T parse(XPathXmlPullParser parser) throws XmlPullParserException,
			IOException;

	T parse(XPathXmlPullParser parser, T objectToModify)
			throws XmlPullParserException, IOException;

	/**
	 * Initialises a new instance of a parsed object - called at the start of
	 * parseChildren().
	 * 
	 * Use this to initialise the object that will be passed into parseTag()
	 */
	public abstract T initializeParsedObject();
}