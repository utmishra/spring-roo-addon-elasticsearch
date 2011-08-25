package org.springframework.roo.addon.elasticsearch;

import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.annotations.populator.AbstractAnnotationValues;
import org.springframework.roo.classpath.details.annotations.populator.AutoPopulate;
import org.springframework.roo.classpath.details.annotations.populator.AutoPopulationUtils;
import org.springframework.roo.model.JavaType;

/**
 * Represents a parsed {@link RooElasticsearchWebSearchable} annotation.
 * 
 * @author Stefan Schmidt
 * @since 1.1
 *
 */
public class ElasticsearchWebSearchAnnotationValues extends AbstractAnnotationValues {
	
	@AutoPopulate String searchMethod = "search";
	@AutoPopulate String autoCompleteMethod = "autoComplete";
	
	public ElasticsearchWebSearchAnnotationValues(PhysicalTypeMetadata governorPhysicalTypeMetadata) {
		super(governorPhysicalTypeMetadata, new JavaType(RooElasticsearchWebSearchable.class.getName()));
		AutoPopulationUtils.populate(this, annotationMetadata);
	}

	public String getSearchMethod() {
		return searchMethod;
	}

	public String getAutoCompleteMethod() {
		return autoCompleteMethod;
	}
}
