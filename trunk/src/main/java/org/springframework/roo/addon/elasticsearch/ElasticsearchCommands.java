package org.springframework.roo.addon.elasticsearch;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CliOption;
import org.springframework.roo.shell.CommandMarker;

/**
 * Sample of a command class. The command class is registered by the Roo shell following an
 * automatic classpath scan. You can provide simple user presentation-related logic in this
 * class. You can return any objects from each method, or use the logger directly if you'd
 * like to emit messages of different severity (and therefore different colours on 
 * non-Windows systems).
 * 
 * @since 1.1
 */
@Component // Use these Apache Felix annotations to register your commands class in the Roo container
@Service
public class ElasticsearchCommands implements CommandMarker { // All command types must implement the CommandMarker interface
	
	/**
	 * Get a reference to the ElasticsearchOperations from the underlying OSGi container
	 */
	@Reference private ElasticsearchOperations searchOperations;
	
	@CliAvailabilityIndicator({"elasticsearch setup"})
	public boolean setupCommandAvailable() {
		return searchOperations.isInstallSearchAvailable();
	}
	
	@CliAvailabilityIndicator({"elasticsearch add","elasticsearch all"})
	public boolean elasticsearchCommandAvailable() {
		return searchOperations.isSearchAvailable() && searchOperations.isJsonAddonAvailable();
	}
	
	@CliCommand(value="elasticsearch setup", help="Install a support for elasticsearch search integration")
	public void setup(
			@CliOption(
				key={"searchHost"},
				mandatory=false,
				unspecifiedDefaultValue="embedded",
				specifiedDefaultValue="embedded",
				help="Hostname of remote elasticsearch search node") String searchHost,
			@CliOption(
				key={"searchPort"},
				mandatory=false,
				unspecifiedDefaultValue="9300",
				specifiedDefaultValue="9300",
				help="Port of remote elasticsearch search node") int searchPort) {
		searchOperations.setupConfig(searchHost, searchPort);
	}
	
	@CliCommand(value="elasticsearch add", help="Make target type searchable")
	public void setup(@CliOption(key="class", mandatory=false, unspecifiedDefaultValue="*", optionContext="update,project", help="The target type which is made searchable") JavaType javaType) {
		searchOperations.addSearch(javaType);
	}
	
	@CliCommand(value="elasticsearch all", help="Make all elegible project types searchable")
	public void setup() {
		searchOperations.addAll();
	}
}