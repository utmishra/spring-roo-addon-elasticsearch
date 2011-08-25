package org.springframework.roo.addon.elasticsearch;

import org.springframework.roo.model.JavaType;

/**
 * Interface of operations this add-on offers. Typically used by a command type or an external add-on.
 *
 * @since 1.1
 */
public interface ElasticsearchOperations {

	public static final String ES_CLIENT_FACTORY_SIMPLE_TYPE = 
		"ElasticsearchClientFactoryBean";
	
	public boolean isInstallSearchAvailable();
	
	public boolean isSearchAvailable();
	
	public boolean isJsonAddonAvailable();
	
	public void setupConfig(String searchHost, int searchPort);
	
	public void addSearch(JavaType javaType);
	
	public void addAll();
}