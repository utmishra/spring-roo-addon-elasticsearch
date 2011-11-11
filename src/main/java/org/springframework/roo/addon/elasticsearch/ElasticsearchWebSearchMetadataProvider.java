package org.springframework.roo.addon.elasticsearch;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.web.mvc.controller.scaffold.mvc.WebScaffoldMetadata;
import org.springframework.roo.addon.web.mvc.controller.scaffold.mvc.WebScaffoldMetadataProvider;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.itd.AbstractItdMetadataProvider;
import org.springframework.roo.classpath.itd.ItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.support.util.Assert;

/**
 * Provides {@link ElasticsearchWebSearchMetadata}.
 * 
 * @author Stefan Schmidt
 * @since 1.1
 *
 */
@Component(immediate = true)
@Service
public final class ElasticsearchWebSearchMetadataProvider extends AbstractItdMetadataProvider {
	//@Reference private WebScaffoldMetadataProvider webScaffoldMetadataProvider;

	protected void activate(ComponentContext context) {
		metadataDependencyRegistry.registerDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
		//webScaffoldMetadataProvider.addMetadataTrigger(new JavaType(RooElasticsearchWebSearchable.class.getName()));
		addMetadataTrigger(new JavaType(RooElasticsearchWebSearchable.class.getName()));	
	}
	
	protected ItdTypeDetailsProvidingMetadataItem getMetadata(String metadataIdentificationString, JavaType aspectName, PhysicalTypeMetadata governorPhysicalTypeMetadata, String itdFilename) {
		// We need to parse the annotation, which we expect to be present
		ElasticsearchWebSearchAnnotationValues annotationValues = new ElasticsearchWebSearchAnnotationValues(governorPhysicalTypeMetadata);
		if (!annotationValues.isAnnotationFound() || annotationValues.searchMethod == null) {
			return null;
		}
		
		// Acquire bean info (we need getters details, specifically)
		JavaType javaType = ElasticsearchWebSearchMetadata.getJavaType(metadataIdentificationString);
		Path path = ElasticsearchWebSearchMetadata.getPath(metadataIdentificationString);
		String webScaffoldMetadataKey = WebScaffoldMetadata.createIdentifier(javaType, path);
		
		// We want to be notified if the getter info changes in any way 
		metadataDependencyRegistry.registerDependency(webScaffoldMetadataKey, metadataIdentificationString);
		WebScaffoldMetadata webScaffoldMetadata = (WebScaffoldMetadata) metadataService.get(webScaffoldMetadataKey);
		
		// Abort if we don't have getter information available
		if (webScaffoldMetadata == null || !webScaffoldMetadata.isValid()) {
			return null;
		}
		
		JavaType targetObject = webScaffoldMetadata.getAnnotationValues().getFormBackingObject();
		Assert.notNull(targetObject, "Could not aquire form backing object for the '" + WebScaffoldMetadata.getJavaType(webScaffoldMetadata.getId()).getFullyQualifiedTypeName() + "' controller");
		
		ElasticsearchMetadata esMetadata = (ElasticsearchMetadata) metadataService.get(ElasticsearchMetadata.createIdentifier(targetObject, Path.SRC_MAIN_JAVA));
		Assert.notNull(esMetadata, "Could not determine ElasticsearchMetadata for type '" + targetObject.getFullyQualifiedTypeName() + "'");

		// Otherwise go off and create the to String metadata
		return new ElasticsearchWebSearchMetadata(metadataIdentificationString, aspectName, governorPhysicalTypeMetadata, annotationValues, webScaffoldMetadata.getAnnotationValues(), esMetadata.getAnnotationValues());
	}
	
	public String getItdUniquenessFilenameSuffix() {
		return "ElasticsearchWebSearch";
	}

	protected String getGovernorPhysicalTypeIdentifier(String metadataIdentificationString) {
		JavaType javaType = ElasticsearchWebSearchMetadata.getJavaType(metadataIdentificationString);
		Path path = ElasticsearchWebSearchMetadata.getPath(metadataIdentificationString);
		return PhysicalTypeIdentifier.createIdentifier(javaType, path);
	}
	
	protected String createLocalIdentifier(JavaType javaType, Path path) {
		return ElasticsearchWebSearchMetadata.createIdentifier(javaType, path);
	}

	public String getProvidesType() {
		return ElasticsearchWebSearchMetadata.getMetadataIdentiferType();
	}
}