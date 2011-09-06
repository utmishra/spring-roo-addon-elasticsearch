package org.springframework.roo.addon.elasticsearch;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.entity.EntityMetadata;
import org.springframework.roo.addon.entity.EntityMetadataProvider;
import org.springframework.roo.addon.plural.PluralMetadata;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.BeanInfoUtils;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.ItdTypeDetails;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.itd.AbstractItdMetadataProvider;
import org.springframework.roo.classpath.itd.ItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;

/**
 * Provides {@link ElasticsearchMetadata}. This type is called by Roo to retrieve the metadata for this add-on.
 * Use this type to reference external types and services needed by the metadata type. Register metadata triggers and
 * dependencies here. Also define the unique add-on ITD identifier.
 * 
 * @since 1.1
 */
@Component
@Service
public final class ElasticsearchMetadataProvider extends AbstractItdMetadataProvider {
	@Reference private EntityMetadataProvider entityMetadataProvider;

	protected void activate(ComponentContext context) {
		metadataDependencyRegistry.registerDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
		entityMetadataProvider.addMetadataTrigger(new JavaType(RooElasticsearchSearchable.class.getName()));
		addMetadataTrigger(new JavaType(RooElasticsearchSearchable.class.getName()));
	}
	
	protected void deactivate(ComponentContext context) {
		metadataDependencyRegistry.deregisterDependency(PhysicalTypeIdentifier.getMetadataIdentiferType(), getProvidesType());
		entityMetadataProvider.removeMetadataTrigger(new JavaType(RooElasticsearchSearchable.class.getName()));
		removeMetadataTrigger(new JavaType(RooElasticsearchSearchable.class.getName()));	
	}

	protected ItdTypeDetailsProvidingMetadataItem getMetadata(String metadataIdentificationString, JavaType aspectName, PhysicalTypeMetadata governorPhysicalTypeMetadata, String itdFilename) {
		// We need to parse the annotation, which we expect to be present
		ElasticsearchAnnotationValues annotationValues = new ElasticsearchAnnotationValues(governorPhysicalTypeMetadata);
		if (!annotationValues.isAnnotationFound() || annotationValues.searchMethod == null) {
			return null;
		}
		
		// Acquire bean info (we need getters details, specifically)
		JavaType javaType = ElasticsearchMetadata.getJavaType(metadataIdentificationString);
		Path path = ElasticsearchMetadata.getPath(metadataIdentificationString);
		String entityMetadataKey = EntityMetadata.createIdentifier(javaType, path);
		
		// We want to be notified if the getter info changes in any way 
		metadataDependencyRegistry.registerDependency(entityMetadataKey, metadataIdentificationString);
		EntityMetadata entityMetadata = (EntityMetadata) metadataService.get(entityMetadataKey);
		
		// Abort if we don't have getter information available
		if (entityMetadata == null || !entityMetadata.isValid()) {
			return null;
		}
		
		// Otherwise go off and create the Elasticsearch metadata
		String beanPlural = javaType.getSimpleTypeName() + "s";
		PluralMetadata pluralMetadata = (PluralMetadata) metadataService.get(PluralMetadata.createIdentifier(javaType, path));
		if (pluralMetadata != null && pluralMetadata.isValid()) {
			beanPlural = pluralMetadata.getPlural();
		}
		
		MemberDetails memberDetails = getMemberDetails(governorPhysicalTypeMetadata);
		Map<MethodMetadata, FieldMetadata> accessorDetails = new LinkedHashMap<MethodMetadata, FieldMetadata>();
		for (MethodMetadata methodMetadata : MemberFindingUtils.getMethods(memberDetails)) {
			if (isMethodOfInterest(methodMetadata)) {
				FieldMetadata fieldMetadata = BeanInfoUtils.getFieldForPropertyName(memberDetails, BeanInfoUtils.getPropertyNameForJavaBeanMethod(methodMetadata));
				if (fieldMetadata != null) {
					accessorDetails.put(methodMetadata, fieldMetadata);
				}
				// Track any changes to that method (eg it goes away)
				metadataDependencyRegistry.registerDependency(methodMetadata.getDeclaredByMetadataId(), metadataIdentificationString);
			}
		}
		return new ElasticsearchMetadata(metadataIdentificationString, aspectName, annotationValues, governorPhysicalTypeMetadata, entityMetadata.getIdentifierAccessor(), entityMetadata.getVersionField(), accessorDetails, beanPlural);
	}
	
	protected String getLocalMidToRequest(ItdTypeDetails itdTypeDetails) {
		// Determine if this ITD presents a method we're interested in (namely accessors)
		for (MethodMetadata method : itdTypeDetails.getDeclaredMethods()) {
			if (isMethodOfInterest(method)) {
				// We care about this ITD, so formally request an update so we can scan for it and process it
				
				// Determine the governor for this ITD, and the Path the ITD is stored within
				JavaType governorType = itdTypeDetails.getName();
				String providesType = MetadataIdentificationUtils.getMetadataClass(itdTypeDetails.getDeclaredByMetadataId());
				Path itdPath = PhysicalTypeIdentifierNamingUtils.getPath(providesType, itdTypeDetails.getDeclaredByMetadataId());
				
				//  Produce the local MID we're going to use to make the request
				String localMid = createLocalIdentifier(governorType, itdPath);
				
				// Request the local MID
				return localMid;
			}
		}
		
		return null;
	}
	
	private boolean isMethodOfInterest(MethodMetadata method) {
		return method.getMethodName().getSymbolName().startsWith("get") && method.getParameterTypes().isEmpty() && Modifier.isPublic(method.getModifier());
	}
	
	public String getItdUniquenessFilenameSuffix() {
		return "Elasticsearch";
	}
	
	protected String getGovernorPhysicalTypeIdentifier(String metadataIdentificationString) {
		JavaType javaType = ElasticsearchMetadata.getJavaType(metadataIdentificationString);
		Path path = ElasticsearchMetadata.getPath(metadataIdentificationString);
		String physicalTypeIdentifier = PhysicalTypeIdentifier.createIdentifier(javaType, path);
		return physicalTypeIdentifier;
	}
	
	protected String createLocalIdentifier(JavaType javaType, Path path) {
		return ElasticsearchMetadata.createIdentifier(javaType, path);
	}

	public String getProvidesType() {
		return ElasticsearchMetadata.getMetadataIdentiferType();
	}
}