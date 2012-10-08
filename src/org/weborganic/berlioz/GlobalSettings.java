/*
 * This file is part of the Berlioz library.
 *
 * For licensing information please see the file license.txt included in the release.
 * A copy of this licence can also be found at
 *   http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.weborganic.berlioz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.weborganic.berlioz.xml.XMLConfig;
import org.weborganic.berlioz.xml.XMLProperties;

/**
 * Berlioz global settings.
 *
 * <p>This class provides a global access to the settings for the application.
 * It needs to be setup prior to using most of the classes used in this application.
 *
 * <p>This class uses two main access points:
 * <ul>
 *   <li><var>repository</var>: is the root directory that contains all of the configuration
 *   files for the application.</li>
 *   <li><var>configuration</var>: is the file that contains all the global properties used
 *   in this application; it is always located in the <i>/config</i> directory of the
 *   repository; the default name of this file 'config.xml'.</li>
 * </ul>
 *
 * <p>The <var>repository</var> and <var>config</var> may be specified using System properties,
 * respectively <code>berlioz.repository</code> and <code>berlioz.config</code>.
 *
 * @see #load
 * @see #setRepository(File)
 * @see #setConfig(String)
 *
 * @author Christophe Lauret (Weborganic)
 * @version 20 May 2010
 */
public final class GlobalSettings {

  /**
   * The format of configuration used.
   */
  private enum Format {XML_PROPERTIES, XML_CONFIG, PROPERTIES};

// constants ----------------------------------------------------------------------------------

  /**
   * Name of the configuration directory in the repository.
   */
  public static final String CONFIG_DIRECTORY = "config";

  /**
   * Name of the directory in the repository that contains all the schemas / DTD for the XML files
   * used by Berlioz.
   */
  public static final String LIBRARY_DIRECTORY = "library";

  /**
   * Name of the default configuration to use.
   */
  public static final String DEFAULT_MODE = "default";

// static variables ---------------------------------------------------------------------------

  /**
   * The repository.
   */
  private static File repository;
  static {
    if (System.getProperty("berlioz.repository") != null) {
      File r = new File(System.getProperty("berlioz.repository"));
      if (r.isDirectory()) {
        repository = r;
      }
    }
  }

  /**
   * The library.
   */
  private static File library;

  /**
   * The name of the configuration file to use.
   */
  private static String mode;
  static {
    mode = System.getProperty("berlioz.mode");
    if (mode == null) {
      mode = DEFAULT_MODE;
    }
  }

  /**
   * The global properties.
   */
  private static Map<String, String> settings;

  /**
   * Maps properties to nodes that have been processed.
   */
  private static Map<String, Properties> nodes;

// constructor ---------------------------------------------------------------------------------

  /**
   * Prevents the creation of instances.
   */
  private GlobalSettings() {
    // empty constructor
  }

// general static methods ----------------------------------------------------------------------

  /**
   * Returns whether the debug information is displayed or not.
   *
   * @deprecated Use a logging framework instead.
   *
   * @return <code>true</code> to display; <code>false</code> otherwise.
   */
  @Deprecated
  public static boolean debug() {
    return System.getProperty("berlioz.debug") != null;
  }

  /**
   * Returns the main repository or <code>null</code> if it has not been setup.
   *
   * @return The directory used as a repository or <code>null</code>.
   */
  public static File getRepository() {
    return repository;
  }

  /**
   * Returns the build version of Berlioz.
   *
   * @return the Berlioz version.
   */
  public static String getVersion() {
    Package p = Package.getPackage("org.weborganic.berlioz");
    return p != null ? p.getImplementationVersion() : "unknown";
  }

  /**
   * Returns the configuration to use.
   *
   * @return The name of the configuration to use.
   */
  public static String getMode() {
    return mode;
  }

  /**
   * Returns the directory containing the DTDs and schemas for the XML in use in the
   * system.
   *
   * <p>This method will return a file only if the repository has been properly set,
   * and will be the directory defined by {@link #LIBRARY_DIRECTORY} in the repository.
   *
   * @return The directory containing the DTDs and schemas for the XML.
   */
  public static File getLibrary() {
    // set if not set
    if (library == null && repository != null) {
      library = new File(repository, LIBRARY_DIRECTORY);
    }
    // return if already defined.
    return library;
  }

  /**
   * Returns the properties file to use externally.
   *
   * @return The properties file to load or <code>null</code>.
   */
  public static File getPropertiesFile() {
    if (repository == null) return null;
    File dir = new File(repository, CONFIG_DIRECTORY);
    if (!dir.isDirectory()) return null;
    // try as an XML file
    File xml = new File(dir, "config-"+mode+".xml");
    if (xml.canRead()) return xml;
    // otherwise try as a text properties file
    File prp = new File(dir, "config-"+mode+".prp");
    if (prp.canRead()) return prp;
    return null;
  }

// properties methods --------------------------------------------------------------------------

  /**
   * Return the requested property.
   *
   * <p>Returns <code>null</code> if the property is not found or defined.
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}
   * method before returning an <code>Enumeration</code>.
   *
   * @param name  the name of the property
   *
   * @return  the property value or <code>null</code>.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static String get(String name) throws IllegalStateException {
    if (settings == null) {
      load();
    }
    return settings.get(name);
  }

  /**
   * Return the property value for the specified Berlioz option.
   *
   * <p>Returns the <code>default</code> value if the property is not found or defined.
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}
   * method before returning an <code>Enumeration</code>.
   *
   * @param option  the name of the property
   *
   * @return  the property value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static String get(BerliozOption option) throws IllegalStateException {
    return get(option.property(), option.defaultTo().toString());
  }

  /**
   * Indicates whether the property value for the specified Berlioz option is enabled.
   *
   * <p>Returns the <code>default</code> value if the property is not found or defined.
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}
   * method.
   *
   * @param option the name of the property
   *
   * @return whether the specified option is set to <code>true</code> or not.
   *
   * @throws IllegalStateException    If this class has not been setup properly.
   * @throws IllegalArgumentException If this class has not been setup properly.
   * @throws NullPointerException     If the specified option is <code>null</code>.
   */
  public static boolean has(BerliozOption option) {
    if (settings == null) {
      load();
    }
    if (option == null) throw new NullPointerException("No Berlioz option specified");
    String value = settings.get(option.property());
    Object def = option.defaultTo();
    if (option.isBoolean()) return value != null? Boolean.parseBoolean(value) : ((Boolean)def).booleanValue();
    else
      throw new IllegalArgumentException("Trying to get non-boolean option '"+option.property()+"' as boolean.");
  }

  /**
   * Returns the requested property or it default value.
   *
   * <p>The given default value is returned only if the property is not found.
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}
   * method before returning an <code>Enumeration</code>.
   *
   * @param name  The name of the property.
   * @param def   A default value for the property.
   *
   * @return  the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static String get(String name, String def) throws IllegalStateException {
    if (settings == null) {
      load();
    }
    String value = settings.get(name);
    return value == null? def : value;
  }

  /**
   *
   * @param name  The name of the property.
   * @param def   A default value for the property.
   *
   * @return  the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static int get(String name, int def) throws IllegalStateException {
    if (settings == null) {
      load();
    }
    try {
      String value = settings.get(name);
      return value == null? def : Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  /**
   * Returns the requested property or it default value.
   *
   * <p>The given default value is returned only if the property is not found.
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}.
   *
   * @param name  The name of the property.
   * @param def   A default value for the property.
   *
   * @return  the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static boolean get(String name, boolean def) throws IllegalStateException {
    if (settings == null) { load(); }
    String value = settings.get(name);
    if (value == null) return def;
    return def? "false".equals(value) : "true".equals(value);
  }

  /**
   * Returns the requested property as a file or its default file value.
   *
   * <p>The given default value is returned if:
   * <ul>
   *   <li>the property is not found;</li>
   *   <li>the file corresponding to the property does not exist;</li>
   *   <li>there is an error.</li>
   * </ul>
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}.
   *
   * @param name  The name of the property.
   * @param def   A default value for the property.
   *
   * @return  the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static File get(String name, File def) throws IllegalStateException {
    File file = getFileProperty(name);
    return file != null? file : def;
  }

  /**
   * Returns the requested property as a file.
   *
   * <p>The given default value is returned if:
   * <ul>
   *   <li>the property is not found;</li>
   *   <li>the file corresponding to the property does not exist;</li>
   *   <li>there is an error</li>
   * </ul>
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}.
   *
   * @param name  The name of the property.
   *
   * @return  the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static File getDirProperty(String name) throws IllegalStateException {
    File file = getFileProperty(name);
    if (file != null && file.isDirectory())
      return file;
    else return null;
  }

  /**
   * Returns the requested property as a file.
   *
   * <p>The given default value is returned if:
   * <ul>
   *   <li>the property is not found;</li>
   *   <li>the file corresponding to the property does not exist;</li>
   *   <li>there is an error
   * </ul>
   *
   * <p>If the properties file has not been loaded, this method will invoke the {@link #load()}.
   *
   * @param name  The name of the property.
   *
   * @return the property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static File getFileProperty(String name) throws IllegalStateException {
    if (settings == null) {
      load();
    }
    String filepath = settings.get(name);
    if (filepath != null) {
      File file = new File(repository, filepath);
      try {
        if (file.exists()) return file.getCanonicalFile();
      } catch (IOException ex) {
        ex.printStackTrace();
        // TODO: report any error
      }
    }
    return null;
  }

  /**
   * Returns the entries for the specified node as <code>Properties</code>.
   *
   * @param name The name of the database.
   *
   * @return The property value or the default value.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static Properties getNode(String name) {
    if (settings == null) {
      load();
    }
    if (nodes == null) return null;
    // return the node if already processed.
    if (nodes.containsKey(name))
      return nodes.get(name);
    // other process and store
    Properties node = new Properties();
    String prefix = name+'.';
    for (Entry<String, String> e : settings.entrySet()) {
      String key = e.getKey();
      if (key.startsWith(prefix) && key.substring(prefix.length()).indexOf('.') < 0) {
        node.setProperty(key.substring(prefix.length()), e.getValue());
      }
    }
    nodes.put(name, node);
    return node;
  }

  /**
   * Enumerates the properties in the global settings.
   *
   * @return An enumeration of the property names.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static Enumeration<String> propertyNames() throws IllegalStateException {
    if (settings == null) {
      load();
    }
    return Collections.enumeration(settings.keySet());
  }

// setup methods -------------------------------------------------------------------------------

  /**
   * Sets the repository to the specified file if it exists and is a directory.
   *
   * <p>Does nothing if the specified file is <code>null</code>.
   *
   * <p>If the specified file does not exist or is not a directory, the repository will
   * remain unchanged.
   *
   * @param dir The directory to use as the main repository.
   *
   * @throws IllegalArgumentException If the specified file is not a valid repository.
   */
  public static void setRepository(File dir) throws IllegalArgumentException {
    // ignore the case when this is null
    if (dir == null) return;
    // check directory
    if (!dir.exists())
      throw new IllegalArgumentException("The specified repository "+dir+" does not exist.");
    else if (!dir.isDirectory())
      throw new IllegalArgumentException("The specified repository "+dir+" is not a directory.");
    else {
      repository = dir;
      // reset the library, it will be properly set during the next call.
      library = null;
    }
  }

  /**
   * Sets the configuration mode to use.
   *
   * @param name The name of the mode to use.
   *
   * @throws NullPointerException If the name of the mode is <code>null</code>.
   */
  public static void setMode(String name) {
    if (name == null)
      throw new NullPointerException("The configuration mode must be specified.");
    mode = name;
  }

  /**
   * Sets the configuration to use.
   *
   * @deprecated Use {@link #setMode(String)} instead.
   *
   * @param name The name of the configuration to use.
   *
   * @throws IllegalArgumentException If the name of the configuration is <code>null</code>.
   */
  @Deprecated
  public static void setConfig(String name) throws IllegalArgumentException {
    if (name == null)
      throw new IllegalArgumentException("The configuration must be specified.");
    mode = name;
  }

  /**
   * Loads the properties.
   *
   * <p>There are several mechanism to load the properties.
   *
   * <p>First, this method will try to use the properties file that might have been setup with
   * the {@link #setPropertiesFile(File)} method.
   *
   * <p>If all of the above fail, the properties will remain empty, this method will return
   * <code>false</code> to indicate that the properties could not be loaded.
   *
   * <p>Errors will only be reported to the <code>System</code> error output, the complete stack
   * trace will be available.
   *
   * <p>In all cases, the file must be conform to the java properties specifications.
   *
   * @see java.util.Properties
   * @see System#getProperty(java.lang.String)
   * @see ClassLoader#getResourceAsStream(java.lang.String)
   *
   * @return <code>true</code> if the properties were loaded; <code>false</code> otherwise.
   *
   * @throws IllegalStateException If this class has not been setup properly.
   */
  public static synchronized boolean load() throws IllegalStateException {
    // make sure we have a repository
    File file = getPropertiesFile();
    if (file != null) {
      // initialise
      settings = new HashMap<String, String>();
      nodes = new Hashtable<String, Properties>();
      // load
      try {
        Format kind = detect(file);
        switch (kind) {
          case XML_CONFIG:
            return loadConfig(file, settings);
          case XML_PROPERTIES:
            return loadProperties(file, new XMLProperties(), settings);
          case PROPERTIES:
            return loadProperties(file, new Properties(), settings);
        }
      } catch (Exception ex) {
        System.err.println("[BERLIOZ_CONFIG] (!) An error occurred whilst trying to read the properties file.");
        ex.printStackTrace();
      }
    }
    return false;
  }

  /**
   * Detects the kind of config file used.
   *
   * <p>If it does not ends with ".xml", it assumes it is a regular java properties file.
   *
   * <p>If it ends with ".xml", it scans the file to see if it can find a references to
   * the "-//Berlioz//DTD::Properties 1.0//EN" public doctype and assumes an XML properties type.
   *
   * <p>Otherwise, it will attempt to read the file as an XML config.
   *
   * @return The kind of config file used.
   */
  private static Format detect(File file) throws IOException {
    if (!file.getName().endsWith(".xml")) return Format.PROPERTIES;
    BufferedReader b = new BufferedReader(new FileReader(file));
    Format kind = Format.XML_CONFIG;
    try {
      String line = b.readLine();
      while (line != null) {
        if (line.indexOf("-//Berlioz//DTD::Properties 1.0//EN") >= 0) {
          kind = Format.XML_PROPERTIES;
        }
        line = b.readLine();
      }
    } catch (IOException ex) {

    } finally {
      b.close();
    }
    return kind;
  }

  /**
   * Loads the
   *
   */
  private static boolean loadProperties(File file, Properties p, Map<String, String> map) throws IOException {
    // load
    boolean loaded = false;
    InputStream in = null;
    try {
      in = new FileInputStream(file);
      p.load(in);
      loaded = true;
    } catch (Exception ex) {
      System.err.println("[BERLIOZ_CONFIG] (!) An error occurred whilst trying to read the properties file.");
      ex.printStackTrace();
    } finally {
      in.close();
    }
    // Load the values into the map
    for (Entry<Object, Object> e : p.entrySet()) {
      map.put(e.getKey().toString(), e.getValue().toString());
    }
    return loaded;
  }

  /**
   * Detects the
   *
   */
  private static boolean loadConfig(File file, Map<String, String> settings) throws IOException {
    // load
    boolean loaded = false;
    InputStream in = null;
    try {
      XMLConfig config = new XMLConfig(settings);
      in = new FileInputStream(file);
      config.load(in);
      loaded = true;
    } catch (Exception ex) {
      System.err.println("[BERLIOZ_CONFIG] (!) An error occurred whilst trying to read the global config file.");
      ex.printStackTrace();
    } finally {
      in.close();
    }
    return loaded;
  }
}
