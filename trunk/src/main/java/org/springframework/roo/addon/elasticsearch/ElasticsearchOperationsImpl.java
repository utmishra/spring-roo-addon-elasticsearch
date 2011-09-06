package org.springframework.roo.addon.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.addon.entity.RooEntity;
import org.springframework.roo.classpath.PhysicalTypeDetails;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.PhysicalTypeMetadataProvider;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MutableClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.addon.json.RooJson;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.project.DependencyScope;
import org.springframework.roo.project.DependencyType;
import org.springframework.roo.project.Repository;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.FileCopyUtils;
import org.springframework.roo.support.util.TemplateUtils;
import org.springframework.roo.support.util.XmlElementBuilder;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Implementation of operations this add-on offers.
 *
 * @since 1.1
 */
@Component // Use these Apache Felix annotations to register your commands class in the Roo container
@Service
public class ElasticsearchOperationsImpl implements ElasticsearchOperations {
	
	private static final Dependency ES = new Dependency("org.elasticsearch", "elasticsearch", "0.17.5");
	private static final Dependency ES_ADDON = new Dependency("org.springframework.roo.addon.elasticsearch", "org.springframework.roo.addon.elasticsearch", "0.1.0.BUILD-SNAPSHOT", DependencyType.JAR, DependencyScope.PROVIDED);
	private static final Dependency JSON_ADDON = new Dependency("org.springframework.roo","org.springframework.roo.addon.json","LATEST", DependencyType.JAR, DependencyScope.PROVIDED);
	
	@Reference private FileManager fileManager;
	@Reference private PhysicalTypeMetadataProvider physicalTypeMetadataProvider;
	@Reference private MetadataService metadataService;
	@Reference private ProjectOperations projectOperations;
	@Reference private TypeLocationService typeLocationService;

	public boolean isInstallSearchAvailable() {
		return projectOperations.isProjectAvailable() && !esPropsInstalled() && fileManager.exists(projectOperations.getPathResolver().getIdentifier(Path.SRC_MAIN_RESOURCES, "META-INF/persistence.xml"));
	}
	
	public boolean isSearchAvailable() {
		return esPropsInstalled();
	}
	
	public boolean isJsonAddonAvailable() {
		return projectOperations.getProjectMetadata().isDependencyRegistered(JSON_ADDON);
	}
	
	private boolean esPropsInstalled() {
		return fileManager.exists(projectOperations.getPathResolver().getIdentifier(Path.SPRING_CONFIG_ROOT, "es.properties"));
	}

	public void setupConfig(String esHost, int esPort) {
		// Install the add-on Google code repository needed to get the annotation 
		projectOperations.addRepository(new Repository("Elasticsearch Roo add-on repository", "Elasticsearch Roo add-on repository", "https://spring-roo-addon-elasticsearch.googlecode.com/svn/repo"));
		
		List<Dependency> dependencies = new ArrayList<Dependency>();
		
		// Install the dependency on the add-on jar (
		dependencies.add(ES_ADDON);
		dependencies.add(ES);
		dependencies.add(JSON_ADDON);
		
		projectOperations.addDependencies(dependencies);

		boolean embedded = esHost == null || 
			"".equalsIgnoreCase(esHost) ||
			"embedded".equalsIgnoreCase(esHost);
		
		installESNodeFactoryBean();
		updateESProperties();
		updateESConfig();

		String contextPath = projectOperations.getPathResolver().getIdentifier(Path.SPRING_CONFIG_ROOT, "applicationContext.xml");
		Document appCtx = XmlUtils.readXml(fileManager.getInputStream(contextPath));
		Element root = appCtx.getDocumentElement();

		if (XmlUtils.findFirstElementByName("task:annotation-driven", root) == null) {
			if (root.getAttribute("xmlns:task").length() == 0) {
				root.setAttribute("xmlns:task", "http://www.springframework.org/schema/task");
				root.setAttribute("xsi:schemaLocation", root.getAttribute("xsi:schemaLocation") + "  http://www.springframework.org/schema/task http://www.springframework.org/schema/task/spring-task-3.0.xsd");
			}
			root.appendChild(new XmlElementBuilder("task:annotation-driven", appCtx).addAttribute("executor", "asyncExecutor").addAttribute("mode", "aspectj").build());
			root.appendChild(new XmlElementBuilder("task:executor", appCtx).addAttribute("id", "asyncExecutor").addAttribute("pool-size", "${executor.poolSize}").build());
		}

		Element esClient = XmlUtils.findFirstElement("/beans/bean[@id='esClient']", root);
		if (esClient != null) {
			return;
		}

		XmlElementBuilder beanBuilder = 
			new XmlElementBuilder("bean", appCtx)
				.addAttribute("id", "esClient")
				.addAttribute("class", getClass().getPackage().getName() + "." + ES_CLIENT_FACTORY_SIMPLE_TYPE)
				.addChild(new XmlElementBuilder("property", appCtx)
					.addAttribute("name", "configLocation")
					.addAttribute("value", "classpath:META-INF/elasticsearch/es.yml")
					.build());
		
		if(!embedded) {
			beanBuilder.addChild(new XmlElementBuilder("property", appCtx)
				.addAttribute("name", "transportAddresses")
				.addChild(new XmlElementBuilder("map", appCtx)
					.addChild(new XmlElementBuilder("entry", appCtx)
						.addAttribute("key", esHost)
						.addAttribute("value", String.valueOf(esPort))
						.build())
					.build())
				.build());

		}
		
		root.appendChild(beanBuilder.build());
		XmlUtils.removeTextNodes(root);
		
		fileManager.createOrUpdateTextFileIfRequired(contextPath, XmlUtils.nodeToString(appCtx), false);
	}
	
	private void installESNodeFactoryBean() {
		JavaType javaType = new JavaType(getClass().getPackage().getName() + "." + ES_CLIENT_FACTORY_SIMPLE_TYPE);
		
		String physicalPath = typeLocationService.getPhysicalLocationCanonicalPath(javaType, Path.SRC_MAIN_JAVA);
		if (fileManager.exists(physicalPath)) {
			return;
		}
		try {
			InputStream template = TemplateUtils.getTemplate(getClass(), ES_CLIENT_FACTORY_SIMPLE_TYPE + "._java");
			String input = FileCopyUtils.copyToString(new InputStreamReader(template));
			MutableFile mutableFile = fileManager.createFile(physicalPath);
			FileCopyUtils.copy(input.getBytes(), mutableFile.getOutputStream());
		} catch (IOException e) {
			throw new IllegalStateException("Unable to create '" + physicalPath + "'", e);
		}
	}
	
	private void updateESProperties() {
		String esPath = projectOperations.getPathResolver().getIdentifier(Path.SPRING_CONFIG_ROOT, "es.properties");
		boolean esExists = fileManager.exists(esPath);
		Properties props = new Properties();
		try {
			if (fileManager.exists(esPath)) {
				props.load(fileManager.getInputStream(esPath));
			}
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}

		props.put("executor.poolSize", "10");

		OutputStream outputStream = null;
		try {
			MutableFile mutableFile = esExists ? fileManager.updateFile(esPath) : fileManager.createFile(esPath);
			outputStream = mutableFile.getOutputStream();
			props.store(outputStream, "Updated at " + new Date());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException ignored) {}
			}
		}
	}

	private void updateESConfig() {
		String esConfigPath = projectOperations.getPathResolver().getIdentifier(Path.SRC_MAIN_RESOURCES, "META-INF/elasticsearch/es.yml");
		boolean esConfigExists = fileManager.exists(esConfigPath);
		
		if(esConfigExists) {
			return;
		}
		
		PrintWriter writer = null;
		try {
			MutableFile mutableFile = fileManager.createFile(esConfigPath);
			writer = new PrintWriter(new OutputStreamWriter(mutableFile.getOutputStream()));
			writer.println("path:");
			writer.println("    logs: /temp/elasticsearch/log");
			writer.println("    data: /temp/elasticsearch/data");
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}
	
	public void addAll() {
		Set<ClassOrInterfaceTypeDetails> cids = typeLocationService.findClassesOrInterfaceDetailsWithAnnotation(new JavaType(RooEntity.class.getName()));
		for (ClassOrInterfaceTypeDetails cid : cids) {
			if (Modifier.isAbstract(cid.getModifier())) {
				continue;
			}
			addElasticsearchSearchableAnnotation(cid);
		}
	}

	public void addSearch(JavaType javaType) {
		Assert.notNull(javaType, "Java type required");

		String id = physicalTypeMetadataProvider.findIdentifier(javaType);
		if (id == null) {
			throw new IllegalArgumentException("Cannot locate source for '" + javaType.getFullyQualifiedTypeName() + "'");
		}

		// Obtain the physical type and itd mutable details
		PhysicalTypeMetadata ptm = (PhysicalTypeMetadata) metadataService.get(id);
		Assert.notNull(ptm, "Java source code unavailable for type " + PhysicalTypeIdentifier.getFriendlyName(id));
		PhysicalTypeDetails ptd = ptm.getMemberHoldingTypeDetails();
		Assert.notNull(ptd, "Java source code details unavailable for type " + PhysicalTypeIdentifier.getFriendlyName(id));
		Assert.isInstanceOf(MutableClassOrInterfaceTypeDetails.class, ptd, "Java source code is immutable for type " + PhysicalTypeIdentifier.getFriendlyName(id));
		MutableClassOrInterfaceTypeDetails mutableTypeDetails = (MutableClassOrInterfaceTypeDetails) ptd;

		if (Modifier.isAbstract(mutableTypeDetails.getModifier())) {
			throw new IllegalStateException("The class specified is an abstract type. Can only add elasticsearch for concrete types.");
		}
		addElasticsearchSearchableAnnotation(mutableTypeDetails);
	}

	private void addElasticsearchSearchableAnnotation(ClassOrInterfaceTypeDetails typeDetails) {
		JavaType RooElasticsearchSearchable = new JavaType(RooElasticsearchSearchable.class.getName());
		JavaType RooJsonAnnotation = new JavaType(RooJson.class.getName());
		
		if (MemberFindingUtils.getTypeAnnotation(typeDetails, RooElasticsearchSearchable) == null) {
			if(MemberFindingUtils.getTypeAnnotation(typeDetails, RooJsonAnnotation) == null) {
				AnnotationMetadataBuilder jsonAnnotationBuilder = new AnnotationMetadataBuilder(RooJsonAnnotation);
				((MutableClassOrInterfaceTypeDetails) typeDetails).addTypeAnnotation(jsonAnnotationBuilder.build());
			}
			
			AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(RooElasticsearchSearchable);
			((MutableClassOrInterfaceTypeDetails) typeDetails).addTypeAnnotation(annotationBuilder.build());
		}
	}
}