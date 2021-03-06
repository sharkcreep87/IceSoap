package com.alexgilleran.icesoap.parser.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.modelmbean.XMLParseException;

import com.alexgilleran.icesoap.annotation.XMLField;
import com.alexgilleran.icesoap.annotation.XMLObject;
import com.alexgilleran.icesoap.exception.ClassDefException;
import com.alexgilleran.icesoap.exception.XMLParsingException;
import com.alexgilleran.icesoap.parser.IceSoapParser;
import com.alexgilleran.icesoap.parser.XPathPullParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.BigDecimalParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.BooleanParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.CharacterParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.DateParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.DoubleParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.FloatParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.IntegerParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.LongParser;
import com.alexgilleran.icesoap.parser.impl.stringparsers.StringParser;
import com.alexgilleran.icesoap.parser.processor.Processor;
import com.alexgilleran.icesoap.xpath.XPathRepository;
import com.alexgilleran.icesoap.xpath.XPathRepository.XPathRecord;
import com.alexgilleran.icesoap.xpath.elements.XPathElement;

/**
 * Implementation of {@link IceSoapParser} for parsing an individual object.
 * 
 * This takes in a class, then uses the {@link XMLObject} and {@link XMLField}
 * annotations on it to determine the xpath of this object in SOAP calls, and
 * the xpaths of each field within the object.
 * 
 * Once it has these, every time it gets a call from
 * {@link BaseIceSoapParserImpl#onNewTag(XPathPullParser, Object)}, it looks up
 * the current xpath in its repository of {@link XPathElement}s to see if it has
 * a field that matches it - if it does, it looks at the type of the field.
 * 
 * <li>For basic types (primitives, {@link String}, {@link BigDecimal}, as
 * defined by {@link #TEXT_NODE_CLASSES}), it gets the value from the parser and
 * sets that field to the value - if the value is not an XML Text field an
 * exception is thrown.</li> <li>For complex types annotated by
 * {@link XMLObject}, it will instantiate another parser to parse an instance of
 * this object, then set the instance to that field and continue parsing.</li>
 * <li>If the field is a {@link List}, every time an element is encountered that
 * matches the XPath specified in the {@link List} field's {@link XMLField}
 * annotation, it will parse that object and add it to the list. This will work
 * even if there's elements in between. Note that this is different to how it
 * used to work in 1.0.4 and previous.
 * 
 * @author Alex Gilleran
 * 
 * @param <ReturnType>
 *            The type of the object being parsed.
 */
public class IceSoapParserImpl<ReturnType> extends BaseIceSoapParserImpl<ReturnType> {
	/** Null character **/
	private static final char PRIMITIVE_NULL_CHAR = '\0';
	/** Equivalent to null for primitive number types (0) **/
	private static final int PRIMITIVE_NULL_NUMBER = 0;

	/**
	 * An {@link XPathRepository} that maps xpaths to the fields represented by
	 * them.
	 */
	private XPathRepository<Field> fieldXPaths;

	/**
	 * The class of ReturnType.
	 */
	private Class<ReturnType> targetClass;

	private static final Map<Type, StringParser<?>> parserMap = new HashMap<Type, StringParser<?>>();
	static {
		StringParser<Integer> integerParser = new IntegerParser();
		StringParser<Boolean> booleanParser = new BooleanParser();
		StringParser<Date> dateParser = new DateParser();
		StringParser<Character> characterParser = new CharacterParser();
		StringParser<Double> doubleParser = new DoubleParser();
		StringParser<Float> floatParser = new FloatParser();
		StringParser<Long> longParser = new LongParser();
		StringParser<BigDecimal> bigDecimalParser = new BigDecimalParser();

		parserMap.put(Integer.class, integerParser);
		parserMap.put(int.class, integerParser);
		parserMap.put(boolean.class, booleanParser);
		parserMap.put(Boolean.class, booleanParser);
		parserMap.put(Date.class, dateParser);
		parserMap.put(Character.class, characterParser);
		parserMap.put(char.class, characterParser);
		parserMap.put(double.class, doubleParser);
		parserMap.put(Double.class, doubleParser);
		parserMap.put(Long.class, longParser);
		parserMap.put(long.class, longParser);
		parserMap.put(Float.class, floatParser);
		parserMap.put(float.class, floatParser);
		parserMap.put(BigDecimal.class, bigDecimalParser);
		parserMap.put(String.class, null);
	}

	/** Maintains a cache of instantiated parsers for reuse **/
	private HashMap<XPathElement, BaseIceSoapParserImpl<?>> parserCache = new HashMap<XPathElement, BaseIceSoapParserImpl<?>>();

	/**
	 * Instantiates a new parser.
	 * 
	 * @param targetClass
	 *            The class of the object to parse - note that this must have a
	 *            zero-arg constructor
	 */
	public IceSoapParserImpl(Class<ReturnType> targetClass) {
		this(targetClass, retrieveRootXPaths(targetClass));
	}

	/**
	 * Instantiates a new parser. * @param targetClass The class of the object
	 * to parse.
	 * 
	 * @param targetClass
	 *            The class of the object to parse - note that this must have a
	 *            zero-arg constructor
	 * @param rootXPath
	 *            A root XPath to parse within - the parser will traverse the
	 *            XML document until it finds this XPath and keep parsing until
	 *            it finds the end, then finish. Note that the xml node
	 *            described by this {@link XPathElement} can be outside the node
	 *            specified by the {@link XMLObject} field of targetClass or the
	 *            same, but cannot be within it.
	 */
	public IceSoapParserImpl(Class<ReturnType> targetClass, XPathElement rootXPath) {
		this(targetClass, new XPathRepository<XPathElement>(rootXPath, rootXPath));
	}

	/**
	 * Instantiates a new parser. * @param targetClass The class of the object
	 * to parse.
	 * 
	 * @param targetClass
	 *            The class of the object to parse - note that this must have a
	 *            zero-arg constructor
	 * @param rootXPaths
	 *            The root XPath(s) to parse within - the parser will traverse
	 *            the XML document until it finds one of these and keep parsing
	 *            until it finds the end, then finish.
	 */
	public IceSoapParserImpl(Class<ReturnType> targetClass, XPathRepository<XPathElement> rootXPaths) {
		super(rootXPaths);
		this.targetClass = targetClass;

		fieldXPaths = getFieldXPaths(targetClass);
	}

	/**
	 * Gets the xpaths declared with the {@link XMLField} annotation on a class.
	 * 
	 * @param targetClass
	 *            The class to get xpaths for.
	 * @return An {@link XPathRepository} linking xpaths to fields.
	 */
	private XPathRepository<Field> getFieldXPaths(Class<ReturnType> targetClass) {
		XPathRepository<Field> fieldXPaths = new XPathRepository<Field>();

		Class<?> currentClass = targetClass;

		while (!currentClass.equals(Object.class)) {
			addXPathFieldsToRepo(currentClass, fieldXPaths);
			currentClass = currentClass.getSuperclass();
		}

		return fieldXPaths;
	}

	/**
	 * Adds the fields from the specified class to the passed
	 * {@link XPathRepository}, with the XPaths specified in the
	 * {@link XMLField} annotations.
	 * 
	 * @param targetClass
	 *            The class to draw fields from
	 * @param fieldXPaths
	 *            The repository to add fields too
	 */
	private void addXPathFieldsToRepo(Class<?> targetClass, XPathRepository<Field> fieldXPaths) {
		for (Field field : targetClass.getDeclaredFields()) {
			XMLField xPath = field.getAnnotation(XMLField.class);

			if (xPath != null) {
				// Annotation is not present: do nothing for this field.
				XPathRepository<XPathElement> xpathsFromField;

				if (!xPath.value().equals(XMLField.DEFAULT_XPATH_STRING)) {
					// If the XPath has a value specified, compile it
					xpathsFromField = compileXPath(xPath, field);

					addRootToRelativeXPaths(xpathsFromField);
				} else {
					// XPath has no value - set to the root value
					xpathsFromField = getRootXPaths();
				}

				for (XPathElement element : xpathsFromField.keySet()) {
					fieldXPaths.put(element, field);
				}
			}
		}
	}

	/**
	 * Go through the supplied XPaths and for relative ones, add the root XPaths
	 * to the front of them so they're no longer relative and can be matched
	 * against.
	 * 
	 * @param xpaths
	 *            An {@link XPathRepository} of the xpaths to check and modify
	 *            if necessary.
	 */
	private void addRootToRelativeXPaths(XPathRepository<XPathElement> xpaths) {
		// Make a copy of the repo keyset, as we may need to concurrently remove
		// xpaths from the repo while looping through.
		Set<XPathElement> xpathsSet = new HashSet<XPathElement>(xpaths.keySet());

		for (XPathElement thisXPath : xpathsSet) {
			XPathElement firstXPathElement = thisXPath.getFirstElement();

			if (firstXPathElement.isRelative()) {
				// If the xpath is relative, we want to add the root xpath(s) of
				// the object to the start of it.

				if (getRootXPaths().keySet().size() == 1) {
					thisXPath.getFirstElement().setPreviousElement(getRootXPaths().keySet().iterator().next());
				} else {
					// As there are multiple root xpaths, we need to create an
					// new instance of this field xpath for each root xpath
					// (using the existing field xpath object as a prototype)
					// and add the enclosing xpath to the start of each.

					// Remove the existing xpath from the repo (we'll add a
					// modified clone of it later)
					xpaths.remove(thisXPath);

					for (XPathElement rootXPath : getRootXPaths().keySet()) {
						// Copy the relative xpath for the field
						XPathElement element = thisXPath.clone();

						// Append the new xpath to this root xpath.
						element.getFirstElement().setPreviousElement(rootXPath);

						// Put the new element in the xpaths repository.
						xpaths.put(element, element);
					}
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Uses {@link Class#newInstance()} to instantiate a new instance of
	 * ReturnType.
	 */
	@Override
	public ReturnType initializeParsedObject() {
		try {
			return targetClass.newInstance();
		} catch (InstantiationException e) {
			throwInitializationException(e);
		} catch (IllegalAccessException e) {
			throwInitializationException(e);
		}

		return null;
	}

	/**
	 * Throws a {@link ClassDefException} encountered when initializing the
	 * class.
	 * 
	 * @param cause
	 *            The root exception.
	 */
	private void throwInitializationException(Throwable cause) {
		throw new ClassDefException(
				"An exception was encountered while trying to instantiate a new instance of "
						+ targetClass.getName()
						+ ". This is probably because it doesn't implement a zero-arg constructor. To fix this, either change it so it has a zero-arg constructor, extend "
						+ getClass().getSimpleName()
						+ " and override the initializeParsedObject method, or make sure to always pass an existing object to the parser.",
				cause);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * This method works by getting the current xpath from the
	 * {@link XPathPullParser} and seeing if there are any fields that match it.
	 * If there aren't, it does nothing. If there are, it checks the type of the
	 * field - if it's a type that can be set from a text node value, it gets
	 * the value from the node and sets it to the field. If it's a complex
	 * field, it calls {@link #getParserForField(Type, Class, XPathPullParser)}
	 * to parse it.
	 * 
	 * @throws XMLParsingException
	 */
	@Override
	protected ReturnType onNewTag(XPathPullParser xmlPullParser, ReturnType objectToModify) throws XMLParsingException {
		// Get the field to set and the XPath it was stored against
		XPathRecord<Field> xPathRecord = fieldXPaths.getFullRecord(xmlPullParser.getCurrentElement());

		if (xPathRecord != null) {
			Object valueToSet = null;

			if (xmlPullParser.isCurrentValueXsiNil()) {
				setFieldToNull(objectToModify, xPathRecord.getValue());
			} else if (needsParser(xPathRecord.getValue())) {
				// If a new parser is needed and the value is not nil (null),
				// create the parser and set the value to the parsed value, else
				// set it to the null above.

				valueToSet = getParserForField(xPathRecord.getValue(), xmlPullParser, xPathRecord.getKey()).parse(
						xmlPullParser);
				setField(objectToModify, xPathRecord.getValue(), valueToSet);
			}
		}

		return objectToModify;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ReturnType onText(XPathPullParser pullParser, ReturnType objectToModify) throws XMLParsingException {
		Field fieldToSet = fieldXPaths.get(pullParser.getCurrentElement());

		if (fieldToSet != null) {
			try {
				String textNodeValue = pullParser.getCurrentValue();
				XMLField annotation = fieldToSet.getAnnotation(XMLField.class);
				boolean hasProcessor = hasProcessor(fieldToSet);

				if (!needsParser(fieldToSet)) {
					Object valueToSet;

					if (hasProcessor) {
						Processor<?> processor = annotation.processor().newInstance();
						valueToSet = processor.process(textNodeValue);
					} else {
						valueToSet = convertToFieldType(fieldToSet, textNodeValue);
					}

					setField(objectToModify, fieldToSet, valueToSet);
				}
			} catch (InstantiationException e) {
				throw new XMLParsingException(e);
			} catch (IllegalAccessException e) {
				throw new XMLParsingException(e);
			}
		}

		return objectToModify;
	}

	/**
	 * Checks if a field is annotated with a processor.
	 * 
	 * @param field
	 *            The field to check
	 * @return Whether or not it has a processor.
	 */
	protected boolean hasProcessor(Field field) {
		return !field.getAnnotation(XMLField.class).processor().equals(Processor.class);
	}

	/**
	 * Determines whether a parser needs to be created - this is true if the
	 * field's type is not derived from a text node or a list of types derived
	 * from text nodes and will not be processed with a processor.
	 * 
	 * @param fieldToSet
	 * @return
	 */
	private boolean needsParser(Field fieldToSet) {
		// Is it a text node?
		if (parserMap.containsKey(fieldToSet.getType())) {
			return false;
		}

		// Does it have a processor to deal with it rather than a generic
		// parser?
		if (hasProcessor(fieldToSet)) {
			return false;
		}

		// Is it a list of text nodes?
		if (List.class.isAssignableFrom(fieldToSet.getType())
				&& parserMap.containsKey(getListItemClass(fieldToSet.getGenericType()))) {
			return false;
		}

		// No to all of the above, .'. it needs a parser
		return true;
	}

	/**
	 * Given a class, attempts to find the appropriate parser for the class. If
	 * the class is an implementation of {@link List}, it attempts to get the
	 * class that the item is a list of and instantiate a {@link IceSoapParser}
	 * to parse that class.
	 * 
	 * @param <ObjectType>
	 *            The type of the object to create a parser for
	 * @param typeToParse
	 *            The type to parse (as a {@link Type}
	 * @param classToParse
	 *            The class to parse (as a {@link Class} - this should be the
	 *            same as typeToParse.
	 * @param pullParser
	 *            The pull parser used to do the parsing.
	 * @return A new instance of {@link IceSoapParser}
	 */
	private BaseIceSoapParserImpl<?> getParserForField(Field field, XPathPullParser pullParser, XPathElement fieldXPath) {
		Class<?> classForParser = field.getType();

		if (List.class.isAssignableFrom(field.getType())) {
			// Class to parse is a list - find out the parameterized type of the
			// list and create a parser for that, then wrap a ListParser around
			// it.
			classForParser = getListItemClass(field.getGenericType());
		}

		BaseIceSoapParserImpl<?> parserForClass = parserCache.get(fieldXPath);

		if (parserForClass == null) {
			parserForClass = new IceSoapParserImpl(classForParser, fieldXPath);
			parserCache.put(fieldXPath, parserForClass);
		}

		// The type is not a list - create a parser
		return parserForClass;
	}

	/**
	 * Sets the supplied field of the supplied object to null, substituting
	 * equivalent values if the field's type is a primitive and cannot be null.
	 * 
	 * @param objectToModify
	 *            Object to set the field on.
	 * @param fieldToSet
	 *            Field in the object to set the value of.
	 */
	private void setFieldToNull(ReturnType objectToModify, Field fieldToSet) {
		Class<?> type = fieldToSet.getType();
		Object value = null;

		if (type == int.class || type == long.class || type == double.class || type == float.class) {
			value = PRIMITIVE_NULL_NUMBER;
		} else if (type == boolean.class) {
			value = false;
		} else if (type == char.class) {
			value = PRIMITIVE_NULL_CHAR;
		}

		setField(objectToModify, fieldToSet, value);
	}

	/**
	 * Sets the supplied {@link Field} in the supplied object to the supplied
	 * value, handling setting of accessibility and reflection exceptions.
	 * 
	 * @param objectToModify
	 *            The object to set the value on
	 * @param fieldToSet
	 *            The field (as a {@link Field} to set
	 * @param valueToSet
	 *            The value to set to the field.
	 */
	@SuppressWarnings("unchecked")
	private void setField(ReturnType objectToModify, Field fieldToSet, Object valueToSet) {
		try {
			boolean isAccessibleBefore = fieldToSet.isAccessible();
			fieldToSet.setAccessible(true);

			if (List.class.isAssignableFrom(fieldToSet.getType())) {
				Object valueOfField = fieldToSet.get(objectToModify);

				if (valueOfField == null) {
					List<Object> newList = new ArrayList<Object>();
					newList.add(valueToSet);

					valueToSet = newList;
				} else if (valueOfField instanceof List<?>) {
					((List<Object>) valueOfField).add(valueToSet);

					valueToSet = valueOfField;
				}
			}

			fieldToSet.set(objectToModify, valueToSet);

			fieldToSet.setAccessible(isAccessibleBefore);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Accepts a {@link String} taken from an XML parser and converts it into a
	 * primitive or primitive-esque (e.g. {@link BigDecimal}) type with the
	 * correct method.
	 * 
	 * @param field
	 *            The field to get the appropriate type from.
	 * @param valueString
	 *            The string to parse to the correct type.
	 * @return The string's value as the appropriate type.
	 * @throws XMLParseException
	 */
	private Object convertToFieldType(Field field, String valueString) throws XMLParsingException {
		StringParser<?> objectParser = parserMap.get(field.getType());

		if (objectParser != null) {
			return objectParser.parse(valueString, field.getAnnotation(XMLField.class));
		}

		return valueString;
	}
}
