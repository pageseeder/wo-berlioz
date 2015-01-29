/*
 * This file is part of the Berlioz library.
 *
 * For licensing information please see the file license.txt included in the release.
 * A copy of this licence can also be found at
 *   http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.weborganic.berlioz.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weborganic.berlioz.BerliozOption;
import org.weborganic.berlioz.Beta;
import org.weborganic.berlioz.GlobalSettings;
import org.weborganic.berlioz.content.Environment;
import org.weborganic.berlioz.content.GeneratorListener;
import org.weborganic.berlioz.content.Service;

/**
 * Defines the configuration uses by a a Berlioz Servlet.
 *
 * @author Christophe Lauret
 *
 * @version Berlioz 0.9.27 - 18 December 2013
 * @since Berlioz 0.8.1
 */
public final class BerliozConfig {

  /** Logger for this class */
  private static final Logger LOGGER = LoggerFactory.getLogger(BerliozConfig.class);

  /**
   * Stores all the berlioz config here.
   */
  private static final Map<String, BerliozConfig> CONFIGS = new ConcurrentHashMap<String, BerliozConfig>();

  /**
   * Used to generate ETag Seeds.
   */
  private static final Random RANDOM = new Random();

  /**
   * At what level is the XML transformer allocated.
   */
  private enum TransformAllocation {
    /** No transformation */
    NIL,

    /** One global transformer */
    GLOBAL,

    /** One transformer per group of services */
    GROUP,

    /** One transformer per service */
    SERVICE
  };

  // Class attributes -----------------------------------------------------------------------------

  /**
   * The Servlet configuration.
   */
  private final ServletConfig _servletConfig;

  /**
   * Set the default content type for this Berlioz instance.
   */
  private String _contentType;

  /**
   * Set the default cache control for this Berlioz instance.
   */
  private final String _cacheControl;

  /**
   * Set the Berlioz control key.
   */
  private final String _controlKey;

  /**
   * The relative path to the XSLT stylesheet to use.
   */
  private final String _stylePath;

  /**
   * The fallback stylesheet (points to a URL)
   */
  private final String _fallbackStyleSheet;

  /**
   * Indicates whether the Berlioz instance should use HTTP compression (when possible)
   */
  private final boolean _compression;

  /**
   * The environment.
   */
  private final Environment _env;

  /**
   * How is the XSLT allocated for this configuration.
   */
  private final TransformAllocation _allocation;

  /**
   * The XSLT Transformers to user.
   *
   * <p>The key depends on how the transformers are allocated.
   */
  private final Map<String, XSLTransformer> _transformers;

  /**
   * A seed to use for the calculation of etags (allows them to be reset)
   */
  private volatile long _etagSeed = 0L;

  /**
   * Create a new Berlioz configuration.
   * @param servletConfig The servlet configuration.
   */
  private BerliozConfig(ServletConfig servletConfig) {
    this._servletConfig = servletConfig;
    // get the WEB-INF directory
    ServletContext context = servletConfig.getServletContext();
    File contextPath = new File(context.getRealPath("/"));
    File webinfPath = new File(contextPath, "WEB-INF");
    this._stylePath = this.getInitParameter("stylesheet", "IDENTITY");
    this._allocation = toAllocation(this._stylePath);
    this._fallbackStyleSheet = this.getInitParameter("fallback-stylesheet", null);
    this._transformers = this._allocation != TransformAllocation.NIL? new ConcurrentHashMap<String, XSLTransformer>() : null;
    this._contentType = this.getInitParameter("content-type", "text/html;charset=utf-8");
    if ("IDENTITY".equals(this._stylePath) && !this._contentType.contains("xml")) {
      LOGGER.warn("Servlet {} specified content type {} but output is XML", servletConfig.getServletName(), this._contentType);
    }
    String maxAge = GlobalSettings.get(BerliozOption.HTTP_MAX_AGE);
    String cacheControl = GlobalSettings.get(BerliozOption.HTTP_CACHE_CONTROL);
    if (cacheControl.isEmpty()) {
      cacheControl = "max-age="+maxAge+", must-revalidate";
    }
    this._cacheControl = this.getInitParameter("cache-control", cacheControl);
    this._controlKey = this.getInitParameter("berlioz-control", GlobalSettings.get(BerliozOption.XML_CONTROL_KEY));
    this._compression = this.getInitParameter("http-compression", GlobalSettings.has(BerliozOption.HTTP_COMPRESSION));
    this._env = new HttpEnvironment(contextPath, webinfPath, this._cacheControl);
    this._etagSeed = getEtagSeed();
  }

  /**
   * Returns the name of this configuration, usually the servlet name.
   * @return the name of this configuration, usually the servlet name.
   */
  public String getName() {
    return this._servletConfig.getServletName();
  }

  /**
   * Returns the environment.
   * @return the environment.
   */
  public Environment getEnvironment() {
    return this._env;
  }

  /**
   * Return the ETag Seed.
   * @return the ETag Seed.
   */
  public long getETagSeed() {
    return this._etagSeed;
  }

  /**
   * Resets the ETag Seed.
   */
  public void resetETagSeed() {
    this._etagSeed = newEtagSeed();
  }

  /**
   * Expiry date is a year from now.
   * @return One year into the future.
   */
  public long getExpiryDate() {
    Calendar calendar = Calendar.getInstance();
    calendar.roll(Calendar.YEAR, 1);
    return calendar.getTimeInMillis();
  }

  /**
   * Returns the default cache control instruction.
   *
   * @return the cache control.
   */
  public String getCacheControl() {
    return this._cacheControl;
  }

  /**
   * Returns the content type.
   *
   * @return the content type.
   */
  public String getContentType() {
    return this._contentType;
  }

  /**
   * Indicates whether HTTP compression is enabled for the Berlioz configuration.
   *
   * @return <code>true</code> to enable HTTP compression;
   *         <code>false</code> otherwise.
   */
  public boolean enableCompression() {
    return this._compression;
  }

  /**
   * Sets the content type.
   * @param contentType the content type.
   */
  public void setContentType(String contentType) {
    this._contentType = contentType;
  }

  /**
   * Indicates whether this configuration can be controlled by the user.
   *
   * @param req the request including the control key is specified as a request parameter
   * @return <code>true</code> if no key has been configured or the <code>berlioz-control</code> matches
   *         the control key; false otherwise.
   */
  public boolean hasControl(HttpServletRequest req) {
    if (this._controlKey == null || "".equals(this._controlKey)) return true;
    if (this._controlKey.equals(req.getParameter("berlioz-control"))) return true;
    // TODO Check if this is appropriate!!
    Enumeration<String> e = req.getHeaders("Authorization");
    while (e.hasMoreElements()) {
      String auth = e.nextElement();
      if (auth.startsWith("Berlioz ") && auth.endsWith(this._controlKey))
        return true;
    }
    return false;
  }

  /**
   * Returns the XSLT transformer for the specified service.
   *
   * @param service the service which requires a transformer.
   * @return the corresponding XSLT transformer.
   */
  public XSLTransformer getTransformer(Service service) {
    switch (this._allocation) {
      case NIL:     return null;
      case GLOBAL:  return getTransformer(service, "global");
      case GROUP:   return getTransformer(service, service.group());
      case SERVICE: return getTransformer(service, service.id());
      // Should never happen, but...
      default: return null;
    }
  }

  /**
   * Creates a new config for a given Servlet config.
   *
   * @param servletConfig The servlet configuration.
   * @return A new Berlioz config.
   */
  public static synchronized BerliozConfig newConfig(ServletConfig servletConfig) {
    BerliozConfig config = new BerliozConfig(servletConfig);
    String name = servletConfig.getServletName();
    CONFIGS.put(name, config);
    return config;
  }

  /**
   * Creates a new config for a given Servlet config.
   *
   * @param config The Berlioz configuration to unregister.
   * @return <code>true</code> if the config was unregistered;
   *         <code>false</code> otherwise.
   */
  public static synchronized boolean unregister(BerliozConfig config) {
    String name = config._servletConfig.getServletName();
    return CONFIGS.remove(name) != null;
  }

  /**
   * @param listener the listener to set
   */
  @Beta
  public static synchronized void setListener(GeneratorListener listener) {
    XMLResponse.setListener(listener);
  }

  /**
   * @return the listener currently in use.
   */
  @Beta
  public static synchronized GeneratorListener getListener() {
    return XMLResponse.getListener();
  }

  // private helpers
  // ----------------------------------------------------------------------------------------------

  /**
   * Returns the value for the specified init parameter name.
   *
   * <p>If <code>null</code> returns the default value.
   *
   * @param name The name of the init parameter.
   * @param def  The default value if the parameter value is <code>null</code>
   *
   * @return The values for the specified init parameter name.
   */
  private String getInitParameter(String name, String def) {
    String value = this._servletConfig.getInitParameter(name);
    return (value != null)? value : def;
  }

  /**
   * Returns the value for the specified init parameter name.
   *
   * <p>If <code>null</code> returns the default value.
   *
   * @param name The name of the init parameter.
   * @param def  The default value if the parameter value is <code>null</code>
   *
   * @return The values for the specified init parameter name.
   */
  private boolean getInitParameter(String name, boolean def) {
    String value = this._servletConfig.getInitParameter(name);
    return (value != null)? "true".equals(value) : def;
  }

  /**
   * Expiry date is a year from now.
   * @return One year into the future.
   */
  private long getEtagSeed() {
    long seed = 0L;
    File f = this._env.getPrivateFile("berlioz.etag");
    if (f.exists() && f.length() < 100) {
      try {
        String etag = new Scanner(f).useDelimiter("\\Z").next();
        etag = etag.replaceAll("[^a-zA-Z0-9-]", "");
        seed = Long.parseLong(etag, 36);
        LOGGER.info("Loading the etag seed {}", etag);
      } catch (IOException ex) {
        LOGGER.warn("Unable to load the etag seed", ex);
      } catch (NumberFormatException ex) {
        LOGGER.warn("Unable to load the etag seed", ex);
      }
    }
    return seed;
  }

  /**
   * Expiry date is a year from now.
   * @return One year into the future.
   */
  private long newEtagSeed() {
    Long seed = RANDOM.nextLong();
    LOGGER.info("Generating new ETag Seed: {}", Long.toString(seed.longValue(), 36));
    File f = this._env.getPrivateFile("berlioz.etag");
    if (f.exists() && f.canWrite() || f.getParentFile().canWrite()) {
      try {
        FileOutputStream os = new FileOutputStream(f);
        for (char c : Long.toString(seed.longValue(), 36).toCharArray()) {
          os.write(c);
        }
        os.close();
      } catch (IOException ex) {
        LOGGER.warn("Unable to save the etag seed", ex);
      }
    }
    return seed;
  }

  /**
   * Returns the XSLT transformer for the specified service.
   *
   * <p>This method will create and cache the transformer if necessary.
   *
   * @param service the service which requires a transformer.
   * @param key the key to use to store the transformer.
   * @return the corresponding XSLT transformer.
   */
  private XSLTransformer getTransformer(Service service, String key) {
    XSLTransformer transformer = this._transformers.get(key);
    if (transformer == null) {
      transformer = newTransformer(service);
      this._transformers.put(key, transformer);
    }
    return transformer;
  }

  /**
   * Returns a new XSLT transformer for the specified service.
   *
   * <p>This method creates a new transform from the style path configuration and replaces the
   * <code>{GROUP}</code> and <code>{SERVICE}</code> tokens by the corresponding service attributes.
   *
   * @param service The service
   * @return a new XSLT transformer from the style path configuration for the service.
   *
   * @throws NullPointerException if the service is <code>null</code>.
   */
  private XSLTransformer newTransformer(Service service) {
    String path = this._stylePath;
    path = path.replaceAll("\\{GROUP\\}", service.group());
    path = path.replaceAll("\\{SERVICE\\}", service.id());
    File styleSheet = this._env.getPrivateFile(path);
    return new XSLTransformer(styleSheet, toURL(this._fallbackStyleSheet));
  }

  /**
   * Returns the value for the specified init parameter name.
   *
   * <p>If <code>null</code> returns the default value.
   *
   * @param stylePath The path to the stylesheet to use.
   *
   * @return The values for the specified init parameter name.
   */
  private static TransformAllocation toAllocation(String stylePath) {
    if ("IDENTITY".equals(stylePath) || stylePath == null) return TransformAllocation.NIL;
    else if (stylePath.contains("{SERVICE}")) return TransformAllocation.SERVICE;
    else if (stylePath.contains("{GROUP}")) return TransformAllocation.GROUP;
    else return TransformAllocation.GLOBAL;
  }

  /**
   * Returns the URL instance from the specified path.
   *
   * If the path starts with "resource:", the XSLT will be loaded from a resource
   * using the same class loader as Berlioz.
   *
   * @param path the path to create the URL
   * @return the corresponding URL.
   */
  private URL toURL(String path) {
    if (path == null) return null;
    URL url = null;
    if (path.startsWith("resource:")) {
      ClassLoader loader = BerliozConfig.class.getClassLoader();
      url = loader.getResource(path.substring("resource:".length()));
      if (url == null) {
        LOGGER.warn("Unable to load {} as fallback templates", path);
      }
    } else {
      File file = this._env.getPrivateFile(path);
      try {
        url = file.toURI().toURL();
      } catch (MalformedURLException ex) {
        LOGGER.warn("Unable to load {} as fallback templates", path, ex);
      }
    }
    return url;
  }

}
