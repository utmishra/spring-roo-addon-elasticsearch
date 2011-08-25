package org.springframework.roo.addon.elasticsearch;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.support.style.ToStringCreator;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;

/**
 * This type produces metadata for a new ITD. It uses an {@link ItdTypeDetailsBuilder} provided by 
 * {@link AbstractItdTypeDetailsProvidingMetadataItem} to register a field in the ITD and a new method.
 * 
 * @since 1.1.0
 */
public class ElasticsearchMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {
	private static final String PROVIDES_TYPE_STRING = ElasticsearchMetadata.class.getName();
	private static final String PROVIDES_TYPE = MetadataIdentificationUtils.create(PROVIDES_TYPE_STRING);
	private ElasticsearchAnnotationValues annotationValues;
	private String beanPlural;
	private String javaBeanFieldName;

	public ElasticsearchMetadata(String identifier, JavaType aspectName, ElasticsearchAnnotationValues annotationValues, PhysicalTypeMetadata governorPhysicalTypeMetadata, MethodMetadata identifierAccessor, FieldMetadata versionField, Map<MethodMetadata, FieldMetadata> accessorDetails, String javaTypePlural) {
		super(identifier, aspectName, governorPhysicalTypeMetadata);
		Assert.notNull(annotationValues, "Elasticsearch annotation values required");
		Assert.isTrue(isValid(identifier), "Metadata identification string '" + identifier + "' does not appear to be a valid");
		Assert.notNull(identifierAccessor, "Persistence identifier method metadata required");
		Assert.notNull(accessorDetails, "Metadata for public accessors requred");
		Assert.hasText(javaTypePlural, "Plural representation of java type required");

		if (!isValid()) {
			return;
		}
		this.javaBeanFieldName = JavaSymbolName.getReservedWordSaveName(destination).getSymbolName();
		this.annotationValues = annotationValues;
		this.beanPlural = javaTypePlural;
		
		if (Modifier.isAbstract(governorTypeDetails.getModifier())) {
			// TODO Do something with supertype
			return;
		}
		
		builder.addField(getEsClientField());
		if (StringUtils.hasText(annotationValues.getSimpleSearchMethod())) {
			builder.addMethod(getSimpleSearchMethod());
		}
		if (StringUtils.hasText(annotationValues.getSearchMethod())) {
			builder.addMethod(getSearchMethod());
		}
		if (StringUtils.hasText(annotationValues.getIndexMethod())) {
			builder.addMethod(getIndexEntityMethod());
			builder.addMethod(getIndexEntitiesMethod(accessorDetails, identifierAccessor, versionField));
		}
		if (StringUtils.hasText(annotationValues.getDeleteIndexMethod())) {
			builder.addMethod(getDeleteIndexMethod(identifierAccessor));
		}
		if (StringUtils.hasText(annotationValues.getPostPersistOrUpdateMethod())) {
			builder.addMethod(getPostPersistOrUpdateMethod());
		}
		if (StringUtils.hasText(annotationValues.getPreRemoveMethod())) {
			builder.addMethod(getPreRemoveMethod());
		}

		builder.addMethod(getEsNodeMethod());

		// Create a representation of the desired output ITD
		itdTypeDetails = builder.build();
	}

	public ElasticsearchAnnotationValues getAnnotationValues() {
		return annotationValues;
	}

	private FieldMetadata getEsClientField() {
		JavaSymbolName fieldName = new JavaSymbolName("esClient");
		List<AnnotationMetadataBuilder> autowired = new ArrayList<AnnotationMetadataBuilder>();
		autowired.add(new AnnotationMetadataBuilder(new JavaType("org.springframework.beans.factory.annotation.Autowired")));
		FieldMetadata fieldMd = MemberFindingUtils.getDeclaredField(governorTypeDetails, fieldName);
		if (fieldMd != null) return fieldMd;
		return new FieldMetadataBuilder(getId(), Modifier.TRANSIENT, autowired, fieldName, new JavaType("org.elasticsearch.client.Client")).build();
	}

	private MethodMetadata getPostPersistOrUpdateMethod() {
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getPostPersistOrUpdateMethod());
		MethodMetadata postPersistOrUpdate = MemberFindingUtils.getMethod(governorTypeDetails, methodName, new ArrayList<JavaType>());
		if (postPersistOrUpdate != null) return postPersistOrUpdate;

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		annotations.add(new AnnotationMetadataBuilder(new JavaType("javax.persistence.PostUpdate")));
		annotations.add(new AnnotationMetadataBuilder(new JavaType("javax.persistence.PostPersist")));
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(annotationValues.getIndexMethod() + destination.getSimpleTypeName() + "(this);");

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PRIVATE, methodName, JavaType.VOID_PRIMITIVE, bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	private MethodMetadata getIndexEntityMethod() {
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getIndexMethod() + destination.getSimpleTypeName());
		List<AnnotatedJavaType> paramTypes = new ArrayList<AnnotatedJavaType>();
		paramTypes.add(new AnnotatedJavaType(destination, new ArrayList<AnnotationMetadata>()));
		MethodMetadata indexEntityMethod = MemberFindingUtils.getMethod(governorTypeDetails, methodName, AnnotatedJavaType.convertFromAnnotatedJavaTypes(paramTypes));
		if (indexEntityMethod != null) return indexEntityMethod;

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		List<JavaType> typeParams = new ArrayList<JavaType>();
		typeParams.add(destination);
		bodyBuilder.appendFormalLine(getSimpleName(new JavaType(List.class.getName(), 0, DataType.TYPE, null, typeParams)) + " " + beanPlural.toLowerCase() + " = new " + getSimpleName(new JavaType(ArrayList.class.getName(), 0, DataType.TYPE, null, typeParams)) + "();");
		bodyBuilder.appendFormalLine(beanPlural.toLowerCase() + ".add(" + javaBeanFieldName + ");");
		bodyBuilder.appendFormalLine(annotationValues.getIndexMethod() + beanPlural + "(" + beanPlural.toLowerCase() + ");");

		List<JavaSymbolName> paramNames = new ArrayList<JavaSymbolName>();
		paramNames.add(new JavaSymbolName(javaBeanFieldName));

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, methodName, JavaType.VOID_PRIMITIVE, paramTypes, paramNames, bodyBuilder);
		return methodBuilder.build();
	}

	private MethodMetadata getIndexEntitiesMethod(Map<MethodMetadata, FieldMetadata> accessorDetails, MethodMetadata identifierAccessor, FieldMetadata versionField) {
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getIndexMethod() + beanPlural);
		List<JavaType> typeParams = new ArrayList<JavaType>();
		typeParams.add(destination);
		List<AnnotatedJavaType> paramTypes = new ArrayList<AnnotatedJavaType>();
		paramTypes.add(new AnnotatedJavaType(new JavaType("java.util.Collection", 0, DataType.TYPE, null, typeParams), new ArrayList<AnnotationMetadata>()));
		MethodMetadata indexEntitiesMethod = MemberFindingUtils.getMethod(governorTypeDetails, methodName, AnnotatedJavaType.convertFromAnnotatedJavaTypes(paramTypes));
		if (indexEntitiesMethod != null) return indexEntitiesMethod;

		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		JavaType bulkBuilder = new JavaType("org.elasticsearch.client.action.bulk.BulkRequestBuilder");
		JavaType indexBuilder = new JavaType("org.elasticsearch.client.action.index.IndexRequestBuilder");
		JavaType esClient = new JavaType("org.elasticsearch.client.Client");
		
		String sBulkId = getSimpleName(bulkBuilder);
		String sIndexId = getSimpleName(indexBuilder);
		String sClientId = getSimpleName(esClient);
		
		bodyBuilder.appendFormalLine(sClientId + " client = esClient();");
		bodyBuilder.appendFormalLine(sBulkId + " bulkBuilder = new " + sBulkId + "(client);");
		
		bodyBuilder.appendFormalLine("for (" + destination.getSimpleTypeName() + " " + javaBeanFieldName + " : " + beanPlural.toLowerCase() + ") {");
		bodyBuilder.indent();
		
		// TODO: handle per-type vs per-app indices
		bodyBuilder.appendFormalLine(sIndexId + " indexBuilder = new " + sIndexId + "(client,\"" + destination.getSimpleTypeName().toLowerCase() + "\");");
		
		bodyBuilder.appendFormalLine("indexBuilder.setType(\"" + destination.getSimpleTypeName().toLowerCase() + "\");");
		bodyBuilder.appendFormalLine("indexBuilder.setId(\"\" + " + javaBeanFieldName + "." + identifierAccessor.getMethodName() + "());");
		bodyBuilder.appendFormalLine("indexBuilder.setSource(" + javaBeanFieldName + ".toJson());");
		bodyBuilder.appendFormalLine("bulkBuilder.add(indexBuilder);");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");
		bodyBuilder.appendFormalLine("try {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("bulkBuilder.execute();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("} catch (Exception e) {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("e.printStackTrace();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");

		List<JavaSymbolName> paramNames = new ArrayList<JavaSymbolName>();
		paramNames.add(new JavaSymbolName(beanPlural.toLowerCase()));
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, methodName, JavaType.VOID_PRIMITIVE, paramTypes, paramNames, bodyBuilder);
		methodBuilder.addAnnotation(new AnnotationMetadataBuilder(new JavaType("org.springframework.scheduling.annotation.Async")));
		return methodBuilder.build();
	}

	private MethodMetadata getDeleteIndexMethod(MethodMetadata identifierAccessor) {
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getDeleteIndexMethod());
		List<AnnotatedJavaType> paramTypes = new ArrayList<AnnotatedJavaType>();
		paramTypes.add(new AnnotatedJavaType(destination, new ArrayList<AnnotationMetadata>()));
		MethodMetadata deleteIndex = MemberFindingUtils.getMethod(governorTypeDetails, methodName, AnnotatedJavaType.convertFromAnnotatedJavaTypes(paramTypes));
		if (deleteIndex != null) return deleteIndex;

		JavaType deleteBuilder = new JavaType("org.elasticsearch.client.action.delete.DeleteRequestBuilder");
		JavaType esClient = new JavaType("org.elasticsearch.client.Client");
		
		String sDeleteId = getSimpleName(deleteBuilder);
		String sClientId = getSimpleName(esClient);
		
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(sClientId + " client = esClient();");
		
		// TODO: handle per-app vs per-type indices
		bodyBuilder.appendFormalLine(sDeleteId + " deleteBuilder = new " + sDeleteId + "(client,\"" + destination.getSimpleTypeName().toLowerCase() + "\");");
		bodyBuilder.appendFormalLine("deleteBuilder.setType(\"" + destination.getSimpleTypeName().toLowerCase() + "\");");
		bodyBuilder.appendFormalLine("deleteBuilder.setId(\"\" + " + javaBeanFieldName + "." + identifierAccessor.getMethodName() + "());");
		
		bodyBuilder.appendFormalLine("try {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("deleteBuilder.execute();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("} catch (Exception e) {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("e.printStackTrace();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");

		List<JavaSymbolName> paramNames = new ArrayList<JavaSymbolName>();
		paramNames.add(new JavaSymbolName(javaBeanFieldName));

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, methodName, JavaType.VOID_PRIMITIVE, paramTypes, paramNames, bodyBuilder);
		methodBuilder.addAnnotation(new AnnotationMetadataBuilder(new JavaType("org.springframework.scheduling.annotation.Async")));
		return methodBuilder.build();
	}

	private MethodMetadata getPreRemoveMethod() {
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getPreRemoveMethod());
		MethodMetadata preDelete = MemberFindingUtils.getMethod(governorTypeDetails, methodName, new ArrayList<JavaType>());
		if (preDelete != null) return preDelete;

		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		// annotations.add(new AnnotationMetadataBuilder(new JavaType("org.springframework.scheduling.annotation.Async")));
		annotations.add(new AnnotationMetadataBuilder(new JavaType("javax.persistence.PreRemove")));
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(annotationValues.getDeleteIndexMethod() + "(this);");

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PRIVATE, methodName, JavaType.VOID_PRIMITIVE, bodyBuilder);
		methodBuilder.setAnnotations(annotations);
		return methodBuilder.build();
	}

	private MethodMetadata getSimpleSearchMethod() {
		List<JavaType> paramTypes = new ArrayList<JavaType>();
		paramTypes.add(JavaType.STRING_OBJECT);

		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getSimpleSearchMethod());
		MethodMetadata searchMethod = MemberFindingUtils.getMethod(governorTypeDetails, methodName, paramTypes);
		if (searchMethod != null) return searchMethod;

		JavaType searchResponse = new JavaType("org.elasticsearch.action.search.SearchResponse");
		List<JavaType> typeParams = new ArrayList<JavaType>();
		typeParams.add(searchResponse);
		JavaType listenFuture = new JavaType("org.elasticsearch.action.ListenableActionFuture", 0, DataType.TYPE, null, typeParams);
		
		JavaType queryStringBuilder = new JavaType("org.elasticsearch.index.query.QueryStringQueryBuilder");
		String sQueryId = getSimpleName(queryStringBuilder);
		
		List<JavaSymbolName> paramNames = new ArrayList<JavaSymbolName>();
		paramNames.add(new JavaSymbolName("queryString"));
		
		typeParams = new ArrayList<JavaType>();
		typeParams.add(destination);
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(sQueryId + " queryBuilder = new " + sQueryId + "(queryString);");
		bodyBuilder.appendFormalLine("return search(queryBuilder);");

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, methodName, listenFuture, AnnotatedJavaType.convertFromJavaTypes(paramTypes), paramNames, bodyBuilder);
		return methodBuilder.build();
	}

	private MethodMetadata getSearchMethod() {
		JavaType searchResponse = new JavaType("org.elasticsearch.action.search.SearchResponse");
		List<JavaType> typeParams = new ArrayList<JavaType>();
		typeParams.add(searchResponse);

		JavaType listenFuture = new JavaType("org.elasticsearch.action.ListenableActionFuture", 0, DataType.TYPE, null, typeParams);
		JavaSymbolName methodName = new JavaSymbolName(annotationValues.getSearchMethod());
		List<JavaType> paramTypes = new ArrayList<JavaType>();
		paramTypes.add(new JavaType("org.elasticsearch.index.query.QueryBuilder"));
		MethodMetadata searchMethod = MemberFindingUtils.getMethod(governorTypeDetails, methodName, paramTypes);
		if (searchMethod != null) return searchMethod;

		List<JavaSymbolName> paramNames = new ArrayList<JavaSymbolName>();
		paramNames.add(new JavaSymbolName("queryBuilder"));
		typeParams = new ArrayList<JavaType>();
		typeParams.add(destination);
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		
		JavaType esClient = new JavaType("org.elasticsearch.client.Client");
		JavaType searchBuilder = new JavaType("org.elasticsearch.client.action.search.SearchRequestBuilder");
		String sSearchId = getSimpleName(searchBuilder);
		
		bodyBuilder.appendFormalLine(getSimpleName(esClient) + " client = esClient();");
		bodyBuilder.appendFormalLine(sSearchId + " searchBuilder = new " + sSearchId + "(client);");
		bodyBuilder.appendFormalLine("searchBuilder.setQuery(queryBuilder);");
		bodyBuilder.appendFormalLine("searchBuilder.setTypes(\"" + destination.getSimpleTypeName().toLowerCase() + "\");");
		
		// TODO: handle per-app vs per-type indices
		bodyBuilder.appendFormalLine("searchBuilder.setIndices(\"" + destination.getSimpleTypeName().toLowerCase() + "\");");

		bodyBuilder.appendFormalLine("try {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("return searchBuilder.execute();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("} catch (Exception e) {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("e.printStackTrace();");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");
		bodyBuilder.appendFormalLine("return null;");
		
		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC, methodName, listenFuture, AnnotatedJavaType.convertFromJavaTypes(paramTypes), paramNames, bodyBuilder);
		return methodBuilder.build();
	}

	private MethodMetadata getEsNodeMethod() {
		JavaSymbolName methodName = new JavaSymbolName("esClient");
		MethodMetadata esClientMethod = MemberFindingUtils.getMethod(governorTypeDetails, methodName, new ArrayList<JavaType>());
		if (esClientMethod != null) return esClientMethod;

		JavaType esClient = new JavaType("org.elasticsearch.client.Client");
		InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine(getSimpleName(esClient) + " _esClient = new " + destination.getSimpleTypeName() + "().esClient;");
		bodyBuilder.appendFormalLine("if (_esClient == null) throw new IllegalStateException(\"Elasticsearch node has not been injected (is the Spring Aspects JAR configured as an AJC/AJDT aspects library?)\");");
		bodyBuilder.appendFormalLine("return _esClient;");

		MethodMetadataBuilder methodBuilder = new MethodMetadataBuilder(getId(), Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, methodName, esClient, bodyBuilder);
		return methodBuilder.build();
	}

	private String getSimpleName(JavaType type) {
		return type.getNameIncludingTypeParameters(false, builder.getImportRegistrationResolver());
	}

	public String toString() {
		ToStringCreator tsc = new ToStringCreator(this);
		tsc.append("identifier", getId());
		tsc.append("valid", valid);
		tsc.append("aspectName", aspectName);
		tsc.append("destinationType", destination);
		tsc.append("governor", governorPhysicalTypeMetadata.getId());
		tsc.append("itdTypeDetails", itdTypeDetails);
		return tsc.toString();
	}

	public static final String getMetadataIdentiferType() {
		return PROVIDES_TYPE;
	}

	public static final String createIdentifier(JavaType javaType, Path path) {
		return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
	}

	public static final JavaType getJavaType(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static final Path getPath(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static boolean isValid(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}
}
