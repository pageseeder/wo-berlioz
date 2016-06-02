/*
 * Copyright 2015 Allette Systems (Australia)
 * http://www.allette.com.au
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pageseeder.berlioz.xml;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.pageseeder.berlioz.util.ISO8601;
import org.pageseeder.xmlwriter.XMLWritable;
import org.pageseeder.xmlwriter.XMLWriter;
import org.pageseeder.xmlwriter.XMLWriterImpl;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An XML version of the <code>Properties</code> class.
 *
 * @deprecated This format is no longer supported for global settings.
 *
 * @author Christophe Lauret
 *
 * @version Berlioz 0.9.9 - 10 October 2012
 * @since Berlioz 0.6
 */
@Deprecated
public final class XMLProperties extends Properties implements XMLWritable {

  /**
   * As per requirement for the Serializable interface.
   */
  private static final long serialVersionUID = 20060123256100001L;

  /**
   * The separator between properties.
   */
  private static final String DOT = ".";

  /**
   * Creates an empty property list with no default values.
   */
  public XMLProperties() {
    super(null);
  }

  /**
   * Reads a XML property list from the input stream.
   *
   * @param inStream The XML input stream to parse.
   *
   * @throws IOException If an error occurred when reading from the input stream.
   */
  @Override
  public synchronized void load(InputStream inStream) throws IOException {
    try {
      // use the SAX parser factory to ensure validation
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setValidating(false);
      factory.setNamespaceAware(true);
      // get the parser
      XMLReader reader = factory.newSAXParser().getXMLReader();
      // configure the reader
      Handler handler = new Handler(this);
      reader.setContentHandler(handler);
      reader.setEntityResolver(BerliozEntityResolver.getInstance());
      // parse
      reader.parse(new InputSource(inStream));
    } catch (ParserConfigurationException ex) {
      throw new IOException("Could not configure SAX parser.");
    } catch (SAXException ex) {
      throw new IOException("Error while parsing: "+ex.getMessage());
    }
  }

  /**
   * Stores these XML properties in an XML file.
   *
   * <p>Since Berlioz 0.9.9, this method also prepends the doctype.
   *
   * @param out An output stream.
   *
   * @param header A description of the property list.
   *
   * @throws IOException if writing this property list to the specified
   *             output stream throws an <tt>IOException</tt>.
   *
   * @throws ClassCastException If this <code>Properties</code> object
   *             contains any keys or values that are not <code>Strings</code>.
   *
   * @throws NullPointerException If <code>out</code> is null.
   */
  @Override
  public synchronized void store(OutputStream out, String header) throws IOException, ClassCastException {
    // create the writer
    BufferedWriter awriter = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
    XMLWriterImpl xml = new XMLWriterImpl(awriter, true);
    xml.xmlDecl();
    xml.writeXML("<!DOCTYPE properties PUBLIC \"-//Berlioz//DTD::Properties 1.0//EN\"" +
        " \"http://www.pageseeder.org/schema/berlioz/properties-1.0.dtd\">");
    // write the header if one is required
    if (header != null) {
      xml.writeComment(header);
    }
    xml.writeComment(ISO8601.DATETIME.format(System.currentTimeMillis()));
    toXML(xml);
    xml.close();
  }

  /**
   *
   * @param xml The XML writer receiving data.
   *
   * @throws IOException Should an error occur with the XML writer.
   */
  @Override
  public void toXML(XMLWriter xml) throws IOException {
    xml.openElement("properties", true);
    xml.openElement("root", true);
    nodeToXML("", xml);
    xml.closeElement();
    xml.closeElement();
  }

  /**
   * Recursive XML method that prints the XML for each node.
   *
   * @param prefix The prefix for the node.
   * @param xml    The XML writer receiving data.
   *
   * @throws IOException Should an error occur with the XML writer.
   */
  private void nodeToXML(String prefix, XMLWriter xml) throws IOException {
    Set<String> nodes = new HashSet<String>();
    // get all the entries
    xml.openElement("map", true);
    for (Enumeration<?> e = keys(); e.hasMoreElements();) {
      String key = (String)e.nextElement();
      if (key.startsWith(prefix) && key.length() > prefix.length()) {
        String suffix = (prefix.length() > 0)? key.substring(prefix.length()) : key;
        // identify and serialise entries now
        boolean isEntry = suffix.indexOf(DOT) < 0;
        if (isEntry) {
          xml.openElement("entry", false);
          xml.attribute("key", suffix);
          xml.attribute("value", getProperty(key));
          xml.closeElement();
        // identify the nodes to process recursively later
        } else {
          String name = suffix.substring(0, suffix.indexOf(DOT));
          nodes.add(name);
        }
      }
    }
    xml.closeElement();
    // process each node
    for (String name : nodes) {
      xml.openElement("node", true);
      xml.attribute("name", name);
      String nodePrefix = ((prefix.length() > 0)? prefix : "") + name+DOT;
      nodeToXML(nodePrefix, xml);
      xml.closeElement();
    }
  }

// a handler for the properties file in XML ----------------------------------------------------

  /**
   * Parses the properties file as XML.
   *
   * @author Christophe Lauret (Weborganic)
   * @version 10 October 2009
   */
  static final class Handler extends DefaultHandler {

    /**
     * The properties to load.
     */
    private final Properties _properties;

    /**
     * The prefix used for the entries.
     */
    private final StringBuffer prefix = new StringBuffer();

    /**
     * Creates a new handler.
     *
     * @param properties The properties to load.
     *
     * @throws IllegalArgumentException If the properties are <code>null</code>.
     */
    public Handler(Properties properties) throws IllegalArgumentException {
      if (properties == null) throw new IllegalArgumentException("Properties must be specified.");
      this._properties = properties;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) {
      if ("node".equals(localName)) {
        this.prefix.append(atts.getValue("name")).append(DOT);
      } else if ("entry".equals(localName)) {
        String key = this.prefix.toString()+atts.getValue("key");
        this._properties.setProperty(key, atts.getValue("value"));
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if ("node".equals(localName)) {
        this.prefix.setLength(this.prefix.length() - 1);
        this.prefix.setLength(this.prefix.lastIndexOf(DOT)+1);
      }
    }
  }

}
