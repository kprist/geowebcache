package org.geowebcache.layer.gwc;

import org.geowebcache.config.XMLConfigurationProvider;

import com.thoughtworks.xstream.XStream;

/**
 * Implementation of the {@link XMLConfigurationProvider} extension point to
 * extend the {@code geowebcache.xml} configuration file with {@code gwcLayer}
 * layers.
 * 
 * @author Kostis Pristouris
 * 
 */
public class GWCLayerXMLConfigurationProvider implements
		XMLConfigurationProvider {

	public XStream getConfiguredXStream(final XStream xs) {
		xs.alias("gwcLayer", GWCCacheLayer.class);
		return xs;
	}

}
