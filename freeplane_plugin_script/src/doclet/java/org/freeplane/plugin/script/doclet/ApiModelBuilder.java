package org.freeplane.plugin.script.doclet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import com.sun.source.util.DocTrees;

import jdk.javadoc.doclet.DocletEnvironment;

final class ApiModelBuilder {
    private static final String ROOT_TITLE = "Freeplane scripting API";
    private static final String HOW_TO_USE_LABEL = "How to use this map";
    private static final String API_GROUPS_SECTION_LABEL = "API groups";
    private static final String PACKAGES_SECTION_LABEL = "Packages";
    private static final String TYPE_LABEL = "Type";
    private static final String TYPES_LABEL = "Types";
    private static final String PROPERTIES_LABEL = "Properties";
    private static final String METHODS_LABEL = "Methods";
    private static final String CONSTANTS_LABEL = "Constants";
    private static final String NESTED_TYPES_LABEL = "Nested types";
    private static final String GETTER_AVAILABLE_ON_LABEL = "Getter available on";
    private static final String SETTER_AVAILABLE_ON_LABEL = "Setter available on";
    private static final String AVAILABLE_ON_LABEL = "Available on";
    private static final String DESCRIPTION_LABEL = "Description";
    private static final String PROXY_TOP_LEVEL_TYPE = "org.freeplane.plugin.script.proxy.Proxy";
    private static final String PROXY_MIND_MAP_TYPE = "org.freeplane.plugin.script.proxy.Proxy.MindMap";
    private static final Set<String> EXCLUDED_EXACT_TYPES =
        Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "org.freeplane.plugin.script.proxy.Proxy.MapRO",
            "org.freeplane.plugin.script.proxy.Proxy.Map",
            "org.freeplane.api.MapRO",
            "org.freeplane.api.Map")));

    private final DocletEnvironment environment;
    private final Elements elementUtils;
    private final JavadocCommentExtractor commentExtractor;
    private final List<TypeElement> includedTopLevelTypes = new ArrayList<TypeElement>();
    private final SortedMap<String, TypeElement> includedTypesByQualifiedName = new TreeMap<String, TypeElement>();
    private final Map<String, String> canonicalExactTypeLogicalKeys = new LinkedHashMap<String, String>();

    ApiModelBuilder(DocletEnvironment environment, DocTrees docTrees) {
        this.environment = environment;
        this.elementUtils = environment.getElementUtils();
        this.commentExtractor = new JavadocCommentExtractor(docTrees);
        collectIncludedTypes();
    }

    public ApiMapNode build() {
        ApiMapNode rootNode = new ApiMapNode("root", ROOT_TITLE, "index.html", false);
        List<DocumentationFamily> families = buildDocumentationFamilies();
        rootNode.addChild(buildHowToUseSection());
        rootNode.addChild(buildPackagesSection().setPosition("left"));
        rootNode.addChild(buildApiGroupsSection(families).setPosition("right"));
        return rootNode;
    }

    private void collectIncludedTypes() {
        List<TypeElement> topLevelTypes = new ArrayList<TypeElement>();
        for (Element element : environment.getIncludedElements()) {
            if (!(element instanceof TypeElement)) {
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            if (typeElement.getNestingKind().isNested()) {
                continue;
            }
            if (!isVisibleType(typeElement)) {
                continue;
            }
            topLevelTypes.add(typeElement);
        }
        Collections.sort(topLevelTypes, new Comparator<TypeElement>() {
            @Override
            public int compare(TypeElement left, TypeElement right) {
                return qualifiedName(left).compareTo(qualifiedName(right));
            }
        });
        for (TypeElement typeElement : topLevelTypes) {
            includedTopLevelTypes.add(typeElement);
            collectNestedTypes(typeElement);
        }
    }

    private void collectNestedTypes(TypeElement typeElement) {
        includedTypesByQualifiedName.put(qualifiedName(typeElement), typeElement);
        for (TypeElement nestedType : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            if (isVisibleType(nestedType)) {
                collectNestedTypes(nestedType);
            }
        }
    }

    private ApiMapNode buildHowToUseSection() {
        ApiMapNode section = new ApiMapNode("section:how-to-use", HOW_TO_USE_LABEL, false);
        section.addChild(new ApiMapNode("section:how-to-use:guide", buildGuideText(), false));
        return section;
    }

    private String buildGuideText() {
        return String.join("\n",
            "Use API groups for the full merged documentation and Packages for the exact package/type index.",
            "This mind map is large. Search before reading any branch in depth so you only read relevant parts.",
            "Scanning API-group labels for orientation is fine before reading details.",
            "Search under API groups when you want the primary member documentation.",
            "Search under Packages when you want exact package placement or exact type names.",
            "Exact containing types can appear in multiple branches because later Packages appearances clone earlier API-groups type nodes.",
            "If clone duplicates make broad search noisy, restrict search to the subtree root for API groups or Packages.",
            "Property markers use getter/setter semantics: [read], [write], [read-write].",
            "Method markers use read/write surface semantics only: [read] or [write].",
            "Within each API group, children appear in this order when present: Properties, Methods, Constants, Nested types.",
            "Members are ordered alphabetically inside each group.",
            "Packages shows package structure down to exact classes, interfaces, enums, and inner types, but no member documentation.");
    }

    private ApiMapNode buildApiGroupsSection(List<DocumentationFamily> families) {
        ApiMapNode section = new ApiMapNode("section:api-groups", API_GROUPS_SECTION_LABEL, false);
        for (DocumentationFamily family : families) {
            section.addChild(buildApiGroupNode(family));
        }
        return section;
    }

    private ApiMapNode buildPackagesSection() {
        ApiMapNode section = new ApiMapNode("section:packages", PACKAGES_SECTION_LABEL, false);
        PackageHierarchy rootHierarchy = new PackageHierarchy();
        for (TypeElement typeElement : includedTopLevelTypes) {
            if (isExcludedExactType(typeElement)) {
                continue;
            }
            rootHierarchy.add(typeElement);
        }
        appendPackageHierarchy(section, rootHierarchy, "section:packages");
        return section;
    }

    private void appendPackageHierarchy(ApiMapNode parent, PackageHierarchy hierarchy, String logicalKeyPrefix) {
        for (Map.Entry<String, PackageHierarchy> entry : hierarchy.children.entrySet()) {
            ApiMapNode packageNode = new ApiMapNode(logicalKeyPrefix + ":package:" + entry.getKey(), entry.getKey(), true);
            appendPackageHierarchy(packageNode, entry.getValue(), logicalKeyPrefix + ":package:" + entry.getKey());
            List<TypeElement> packageTypes = new ArrayList<TypeElement>(entry.getValue().topLevelTypes);
            Collections.sort(packageTypes, new Comparator<TypeElement>() {
                @Override
                public int compare(TypeElement left, TypeElement right) {
                    return left.getSimpleName().toString().compareTo(right.getSimpleName().toString());
                }
            });
            for (TypeElement typeElement : packageTypes) {
                packageNode.addChild(buildPackageTypeNode(typeElement,
                    logicalKeyPrefix + ":package:" + entry.getKey() + ":type:" + qualifiedName(typeElement)));
            }
            parent.addChild(packageNode);
        }
    }

    private ApiMapNode buildPackageTypeNode(TypeElement typeElement, String logicalKey) {
        List<TypeElement> nestedTypes = filteredNestedTypes(typeElement);
        ApiMapNode typeNode = createOrCloneExactTypeNode(logicalKey, typeElement, !nestedTypes.isEmpty());
        for (TypeElement nestedType : nestedTypes) {
            typeNode.addChild(buildPackageTypeNode(nestedType, logicalKey + ":nested:" + qualifiedName(nestedType)));
        }
        return typeNode;
    }

    private List<DocumentationFamily> buildDocumentationFamilies() {
        Map<String, DocumentationFamily> familiesByKey = new LinkedHashMap<String, DocumentationFamily>();
        Set<String> mirroredTopLevelTypeNames = new LinkedHashSet<String>();
        for (TypeElement typeElement : includedTypesByQualifiedName.values()) {
            if (!isMirroredFamilyExactType(typeElement)) {
                continue;
            }
            String baseLabel = familyBaseLabel(typeElement);
            DocumentationFamily family = familiesByKey.get("mirror:" + baseLabel);
            if (family == null) {
                family = new DocumentationFamily(baseLabel);
                familiesByKey.put("mirror:" + baseLabel, family);
            }
            family.addExactType(typeElement);
            if (!typeElement.getNestingKind().isNested()) {
                mirroredTopLevelTypeNames.add(qualifiedName(typeElement));
            }
        }
        for (TypeElement topLevelType : includedTopLevelTypes) {
            if (isExcludedExactType(topLevelType)) {
                continue;
            }
            String qualifiedName = qualifiedName(topLevelType);
            if (PROXY_TOP_LEVEL_TYPE.equals(qualifiedName)) {
                continue;
            }
            if (mirroredTopLevelTypeNames.contains(qualifiedName)) {
                continue;
            }
            DocumentationFamily family = new DocumentationFamily(topLevelType.getSimpleName().toString());
            family.addExactType(topLevelType);
            familiesByKey.put("singleton:" + qualifiedName, family);
        }
        Map<String, Integer> labelCounts = new LinkedHashMap<String, Integer>();
        for (DocumentationFamily family : familiesByKey.values()) {
            Integer count = labelCounts.get(family.getCandidateLabel());
            labelCounts.put(family.getCandidateLabel(), count == null ? 1 : count + 1);
        }
        List<DocumentationFamily> families = new ArrayList<DocumentationFamily>(familiesByKey.values());
        for (DocumentationFamily family : families) {
            family.sortExactTypes(this);
            boolean collides = labelCounts.get(family.getCandidateLabel()) > 1;
            family.setDisplayLabel(collides
                ? family.getCandidateLabel() + " (" + packageName(family.getPrimaryType()) + ")"
                : family.getCandidateLabel());
        }
        Collections.sort(families, new Comparator<DocumentationFamily>() {
            @Override
            public int compare(DocumentationFamily left, DocumentationFamily right) {
                return left.getDisplayLabel().compareTo(right.getDisplayLabel());
            }
        });
        return families;
    }

    private boolean isMirroredFamilyExactType(TypeElement typeElement) {
        if (isExcludedExactType(typeElement)) {
            return false;
        }
        String qualifiedName = qualifiedName(typeElement);
        if (qualifiedName.startsWith(PROXY_TOP_LEVEL_TYPE + ".")) {
            return true;
        }
        return packageName(typeElement).startsWith("org.freeplane.api") && !typeElement.getNestingKind().isNested();
    }

    private String familyBaseLabel(TypeElement typeElement) {
        String simpleName = typeElement.getSimpleName().toString();
        if (simpleName.endsWith("RO") && simpleName.length() > 2) {
            return simpleName.substring(0, simpleName.length() - 2);
        }
        return PROXY_MIND_MAP_TYPE.equals(qualifiedName(typeElement)) ? "MindMap" : simpleName;
    }

    private ApiMapNode buildApiGroupNode(DocumentationFamily family) {
        ApiMapNode groupNode = new ApiMapNode(
            "api-group:" + family.getDisplayLabel(),
            appendSummary(family.getDisplayLabel(), family.summary(this)),
            true);

        SortedMap<String, GroupProperty> properties = new TreeMap<String, GroupProperty>();
        SortedMap<String, GroupMethod> methods = new TreeMap<String, GroupMethod>();
        SortedMap<String, GroupConstant> constants = new TreeMap<String, GroupConstant>();
        SortedMap<String, GroupNestedType> nestedTypes = new TreeMap<String, GroupNestedType>();

        for (TypeElement typeElement : family.getExactTypes()) {
            SurfaceProjection surface = projectSurface(typeElement);
            for (PropertyProjection property : surface.properties.values()) {
                GroupProperty groupProperty = properties.get(property.getName());
                if (groupProperty == null) {
                    groupProperty = new GroupProperty(property.getName());
                    properties.put(property.getName(), groupProperty);
                }
                groupProperty.addGetters(property.getSortedGetters());
                groupProperty.addSetters(property.getSortedSetters());
            }
            for (MethodProjection projection : surface.methods.values()) {
                GroupMethod groupMethod = methods.get(projection.getSignatureKey());
                if (groupMethod == null) {
                    groupMethod = new GroupMethod(projection.getSignatureKey());
                    methods.put(projection.getSignatureKey(), groupMethod);
                }
                groupMethod.addSourceMethods(projection.getSourceMethods());
            }
            for (VariableElement field : declaredConstants(typeElement)) {
                String constantKey = field.getSimpleName().toString();
                GroupConstant groupConstant = constants.get(constantKey);
                if (groupConstant == null) {
                    groupConstant = new GroupConstant(constantKey);
                    constants.put(constantKey, groupConstant);
                }
                groupConstant.addField(field);
            }
            for (TypeElement nestedType : filteredNestedTypes(typeElement)) {
                String nestedKey = displayedExactTypeName(nestedType);
                GroupNestedType groupNestedType = nestedTypes.get(nestedKey);
                if (groupNestedType == null) {
                    groupNestedType = new GroupNestedType(nestedKey);
                    nestedTypes.put(nestedKey, groupNestedType);
                }
                groupNestedType.addNestedType(nestedType);
            }
        }

        ApiMapNode typesNode = new ApiMapNode(groupNode.getLogicalKey() + ":types",
            family.hasMultipleExactTypes() ? TYPES_LABEL : TYPE_LABEL, true);
        for (TypeElement exactType : family.getExactTypes()) {
            typesNode.addChild(createOrCloneExactTypeNode(
                groupNode.getLogicalKey() + ":types:type:" + qualifiedName(exactType), exactType, false));
        }
        groupNode.addChild(typesNode);

        List<ApiMapNode> propertyNodes = new ArrayList<ApiMapNode>();
        for (GroupProperty property : properties.values()) {
            propertyNodes.add(buildApiGroupPropertyNode(family, property));
        }
        addGroup(groupNode, PROPERTIES_LABEL, propertyNodes);

        List<ApiMapNode> methodNodes = new ArrayList<ApiMapNode>();
        for (GroupMethod method : methods.values()) {
            methodNodes.add(buildApiGroupMethodNode(family, method));
        }
        addGroup(groupNode, METHODS_LABEL, methodNodes);

        List<ApiMapNode> constantNodes = new ArrayList<ApiMapNode>();
        for (GroupConstant constant : constants.values()) {
            constantNodes.add(buildApiGroupConstantNode(family, constant));
        }
        addGroup(groupNode, CONSTANTS_LABEL, constantNodes);

        List<ApiMapNode> nestedTypeNodes = new ArrayList<ApiMapNode>();
        for (GroupNestedType nestedType : nestedTypes.values()) {
            nestedTypeNodes.add(buildApiGroupNestedTypeNode(family, nestedType));
        }
        addGroup(groupNode, NESTED_TYPES_LABEL, nestedTypeNodes);
        return groupNode;
    }

    private ApiMapNode buildApiGroupPropertyNode(DocumentationFamily family, GroupProperty property) {
        CapabilityMarker capability = capabilityForProperty(property.getGetters(), property.getSetters());
        PropertyTypeDescription typeDescription = describePropertyTypes(property.getGetters(), property.getSetters());
        String logicalKey = "api-group:" + family.getDisplayLabel() + ":property:" + property.getName();
        ApiMapNode propertyNode = new ApiMapNode(
            logicalKey,
            property.getName() + ": " + typeDescription.getDescription() + " " + capability.getMarker(),
            true);
        ExecutableElement representativeMethod = property.representativeMethod(family, this);
        if (representativeMethod != null) {
            addDescriptionNode(propertyNode, logicalKey, commentExtractor.extract(representativeMethod));
        }
        if (family.hasMultipleExactTypes()) {
            addExactTypeAvailability(propertyNode, logicalKey + ":getters", GETTER_AVAILABLE_ON_LABEL,
                property.getGetterOwnerTypes(), false);
            addExactTypeAvailability(propertyNode, logicalKey + ":setters", SETTER_AVAILABLE_ON_LABEL,
                property.getSetterOwnerTypes(), false);
        }
        return propertyNode;
    }

    private ApiMapNode buildApiGroupMethodNode(DocumentationFamily family, GroupMethod method) {
        ExecutableElement representativeMethod = method.getRepresentativeMethod(family, this);
        String logicalKey = "api-group:" + family.getDisplayLabel() + ":method:" + method.getSignatureKey();
        ApiMapNode methodNode = new ApiMapNode(
            logicalKey,
            appendCapabilityMarker(
                formatMethodSignature(representativeMethod),
                capabilityForGroupMethod(family, method.getSourceMethods())),
            true);
        appendMethodDocumentation(methodNode, logicalKey, representativeMethod);
        if (family.hasMultipleExactTypes()) {
            addExactTypeAvailability(methodNode, logicalKey + ":available-on", AVAILABLE_ON_LABEL,
                ownerTypesOf(method.getSourceMethods()), false);
        }
        return methodNode;
    }

    private ApiMapNode buildApiGroupConstantNode(DocumentationFamily family, GroupConstant constant) {
        VariableElement representativeField = constant.getRepresentativeField(family, this);
        String logicalKey = "api-group:" + family.getDisplayLabel() + ":constant:" + constant.getName();
        ApiMapNode constantNode = new ApiMapNode(
            logicalKey,
            representativeField.getSimpleName() + ": " + formatType(representativeField.asType()),
            true);
        JavadocCommentExtractor.DocumentationComment comment = commentExtractor.extract(representativeField);
        String description = documentationText(comment);
        if (hasText(description)) {
            constantNode.addChild(createWrappedTextGroup(logicalKey + ":description", DESCRIPTION_LABEL, description));
        }
        if (family.hasMultipleExactTypes()) {
            addExactTypeAvailability(constantNode, logicalKey + ":available-on", AVAILABLE_ON_LABEL,
                constant.getOwnerTypes(), false);
        }
        return constantNode;
    }

    private ApiMapNode buildApiGroupNestedTypeNode(DocumentationFamily family, GroupNestedType nestedType) {
        TypeElement representativeType = nestedType.getRepresentativeType(family, this);
        String logicalKey = "api-group:" + family.getDisplayLabel() + ":nested-type:" + nestedType.getDisplayName();
        ApiMapNode nestedTypeNode = new ApiMapNode(
            logicalKey,
            appendSummary(nestedType.getDisplayName(), shortenSummary(commentExtractor.extract(representativeType).getSummary())),
            true);
        String description = documentationText(commentExtractor.extract(representativeType));
        if (hasText(description)) {
            nestedTypeNode.addChild(createWrappedTextGroup(logicalKey + ":description", DESCRIPTION_LABEL, description));
        }
        if (family.hasMultipleExactTypes()) {
            addExactTypeAvailability(nestedTypeNode, logicalKey + ":available-on", AVAILABLE_ON_LABEL,
                nestedType.getTypes(), false);
        }
        return nestedTypeNode;
    }

    private void appendMethodDocumentation(ApiMapNode methodNode, String logicalKey, ExecutableElement method) {
        JavadocCommentExtractor.DocumentationComment comment = commentExtractor.extract(method);
        String description = methodDescriptionText(comment);
        if (hasText(description)) {
            methodNode.addChild(createWrappedTextGroup(logicalKey + ":description", DESCRIPTION_LABEL, description));
        }
        if (hasText(comment.getSinceDescription())) {
            ApiMapNode sinceNode = new ApiMapNode(logicalKey + ":since", "Since", false);
            sinceNode.addChild(new ApiMapNode(logicalKey + ":since:value", comment.getSinceDescription(), false));
            methodNode.addChild(sinceNode);
        }
        if (isDeprecated(method) || hasText(comment.getDeprecatedDescription())) {
            ApiMapNode deprecatedNode = new ApiMapNode(logicalKey + ":deprecated", "Deprecated", false);
            deprecatedNode.addChild(new ApiMapNode(logicalKey + ":deprecated:value",
                hasText(comment.getDeprecatedDescription()) ? comment.getDeprecatedDescription() : "Deprecated.", false));
            methodNode.addChild(deprecatedNode);
        }
        if (!comment.getExamples().isEmpty()) {
            ApiMapNode examplesNode = new ApiMapNode(logicalKey + ":examples", "Examples", true);
            int exampleIndex = 1;
            for (String example : comment.getExamples()) {
                ApiMapNode exampleNode = new ApiMapNode(logicalKey + ":example:" + exampleIndex,
                    "Example " + exampleIndex, false);
                int lineIndex = 1;
                for (String line : example.split("\\n", -1)) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    exampleNode.addChild(new ApiMapNode(
                        logicalKey + ":example:" + exampleIndex + ":line:" + lineIndex,
                        line,
                        false));
                    lineIndex += 1;
                }
                examplesNode.addChild(exampleNode);
                exampleIndex += 1;
            }
            methodNode.addChild(examplesNode);
        }
    }

    private String methodDescriptionText(JavadocCommentExtractor.DocumentationComment comment) {
        List<String> sections = new ArrayList<String>();
        String baseDescription = documentationText(comment);
        if (hasText(baseDescription)) {
            sections.add(baseDescription);
        }
        for (JavadocCommentExtractor.ParameterDocumentation parameter : comment.getParameters()) {
            if (hasText(parameter.getDescription())) {
                sections.add("Parameter " + parameter.getName() + " — " + parameter.getDescription());
            }
        }
        if (hasText(comment.getReturnDescription())) {
            sections.add("Returns — " + comment.getReturnDescription());
        }
        for (JavadocCommentExtractor.ThrowsDocumentation throwsDocumentation : comment.getThrowsDocs()) {
            if (hasText(throwsDocumentation.getDescription())) {
                sections.add("Throws " + compactTypeDisplay(throwsDocumentation.getType()) + " — "
                    + throwsDocumentation.getDescription());
            }
        }
        return joinParagraphs(sections);
    }

    private void addDescriptionNode(ApiMapNode parent, String logicalKey,
                                    JavadocCommentExtractor.DocumentationComment comment) {
        String description = documentationText(comment);
        if (hasText(description)) {
            parent.addChild(createWrappedTextGroup(logicalKey + ":description", DESCRIPTION_LABEL, description));
        }
    }

    private String documentationText(JavadocCommentExtractor.DocumentationComment comment) {
        if (comment == null) {
            return "";
        }
        if (hasText(comment.getBody())) {
            return comment.getBody();
        }
        return comment.getSummary();
    }

    private String joinParagraphs(List<String> sections) {
        StringBuilder builder = new StringBuilder();
        for (String section : sections) {
            if (!hasText(section)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(section);
        }
        return builder.toString();
    }

    private void addExactTypeAvailability(ApiMapNode parent, String logicalKey, String label,
                                          Collection<TypeElement> types, boolean folded) {
        if (types == null || types.isEmpty()) {
            return;
        }
        ApiMapNode availabilityNode = new ApiMapNode(logicalKey, label, folded);
        List<TypeElement> sortedTypes = new ArrayList<TypeElement>(types);
        Collections.sort(sortedTypes, new Comparator<TypeElement>() {
            @Override
            public int compare(TypeElement left, TypeElement right) {
                return exactTypeSortKey(left).compareTo(exactTypeSortKey(right));
            }
        });
        for (TypeElement typeElement : sortedTypes) {
            availabilityNode.addChild(createOrCloneExactTypeNode(
                logicalKey + ":type:" + qualifiedName(typeElement), typeElement, false));
        }
        parent.addChild(availabilityNode);
    }

    private ApiMapNode createOrCloneExactTypeNode(String logicalKey, TypeElement typeElement, boolean folded) {
        String qualifiedName = qualifiedName(typeElement);
        String canonicalLogicalKey = canonicalExactTypeLogicalKeys.get(qualifiedName);
        if (canonicalLogicalKey == null) {
            canonicalExactTypeLogicalKeys.put(qualifiedName, logicalKey);
            return new ApiMapNode(logicalKey, displayedExactTypeName(typeElement) + " [" + typeKindLabel(typeElement) + "]", folded);
        }
        return ApiMapNode.contentClone(logicalKey, canonicalLogicalKey, folded);
    }

    private String displayedExactTypeName(TypeElement typeElement) {
        TypeElement topLevelType = topLevelEnclosingType(typeElement);
        if (!typeElement.getNestingKind().isNested()) {
            return typeElement.getSimpleName().toString();
        }
        String topLevelQualifiedName = qualifiedName(topLevelType);
        String qualifiedName = qualifiedName(typeElement);
        String relativeName = qualifiedName.substring(topLevelQualifiedName.length() + 1);
        if (PROXY_TOP_LEVEL_TYPE.equals(topLevelQualifiedName)) {
            return "Proxy." + relativeName;
        }
        return topLevelType.getSimpleName().toString() + "." + relativeName;
    }

    private String typeKindLabel(TypeElement typeElement) {
        if (typeElement.getKind() == ElementKind.INTERFACE) {
            return "interface";
        }
        if (typeElement.getKind() == ElementKind.ENUM) {
            return "enum";
        }
        return "class";
    }

    private TypeElement topLevelEnclosingType(TypeElement typeElement) {
        TypeElement current = typeElement;
        while (current.getNestingKind().isNested()) {
            current = (TypeElement) current.getEnclosingElement();
        }
        return current;
    }

    private String exactTypeSortKey(TypeElement typeElement) {
        return displayedExactTypeName(typeElement) + "|" + qualifiedName(typeElement);
    }

    private List<TypeElement> filteredNestedTypes(TypeElement typeElement) {
        List<TypeElement> nestedTypes = new ArrayList<TypeElement>();
        for (TypeElement nestedType : declaredNestedTypes(typeElement)) {
            if (!isExcludedExactType(nestedType)) {
                nestedTypes.add(nestedType);
            }
        }
        return nestedTypes;
    }

    private boolean isExcludedExactType(TypeElement typeElement) {
        return EXCLUDED_EXACT_TYPES.contains(qualifiedName(typeElement));
    }

    private Collection<TypeElement> ownerTypesOf(Collection<ExecutableElement> methods) {
        SortedMap<String, TypeElement> ownerTypes = new TreeMap<String, TypeElement>();
        for (ExecutableElement method : methods) {
            TypeElement ownerType = enclosingType(method);
            if (ownerType != null) {
                ownerTypes.put(qualifiedName(ownerType), ownerType);
            }
        }
        return ownerTypes.values();
    }

    private CapabilityMarker capabilityForGroupMethod(DocumentationFamily family,
                                                      Collection<ExecutableElement> sourceMethods) {
        boolean hasRead = false;
        boolean hasWrite = false;
        for (ExecutableElement method : sourceMethods) {
            TypeRole role = family.typeRole(enclosingType(method));
            if (role == TypeRole.READ_ONLY) {
                hasRead = true;
            }
            else if (role == TypeRole.WRITE_ONLY) {
                hasWrite = true;
            }
        }
        if (hasRead) {
            return CapabilityMarker.READ;
        }
        if (hasWrite) {
            return CapabilityMarker.WRITE;
        }
        return null;
    }

    private SurfaceProjection projectSurface(TypeElement typeElement) {
        List<ExecutableElement> surfaceMethods = visibleSurfaceMethods(typeElement);
        PropertyProjection properties = projectProperties(surfaceMethods);
        SortedMap<String, MethodProjection> methods = new TreeMap<String, MethodProjection>();
        for (ExecutableElement method : surfaceMethods) {
            if (properties.isPropertyMethod(method)) {
                continue;
            }
            String signatureKey = surfaceMethodSignatureKey(method);
            MethodProjection projection = methods.get(signatureKey);
            if (projection == null) {
                projection = new MethodProjection(signatureKey);
                methods.put(signatureKey, projection);
            }
            projection.addSourceMethod(method);
        }
        return new SurfaceProjection(properties.getPropertiesByName(), methods);
    }

    private PropertyTypeDescription describePropertyTypes(List<ExecutableElement> getters, List<ExecutableElement> setters) {
        SortedSet<String> getterTypes = new TreeSet<String>();
        SortedSet<String> setterTypes = new TreeSet<String>();
        for (ExecutableElement getter : getters) {
            getterTypes.add(formatType(getter.getReturnType()));
        }
        for (ExecutableElement setter : setters) {
            if (!setter.getParameters().isEmpty()) {
                setterTypes.add(formatType(setter.getParameters().get(0).asType()));
            }
        }
        return PropertyTypeDescription.from(getterTypes, setterTypes);
    }

    private CapabilityMarker capabilityForProperty(List<ExecutableElement> getters, List<ExecutableElement> setters) {
        boolean hasRead = getters != null && !getters.isEmpty();
        boolean hasWrite = setters != null && !setters.isEmpty();
        if (hasRead && hasWrite) {
            return CapabilityMarker.READ_WRITE;
        }
        if (hasRead) {
            return CapabilityMarker.READ;
        }
        return CapabilityMarker.WRITE;
    }

    private PropertyProjection projectProperties(List<ExecutableElement> methods) {
        SortedMap<String, PropertyProjection> properties = new TreeMap<String, PropertyProjection>();
        Set<String> propertyMethodKeys = new LinkedHashSet<String>();
        for (ExecutableElement method : methods) {
            if (isGetter(method)) {
                String propertyName = propertyName(method);
                if (propertyName != null && !"class".equals(propertyName)) {
                    PropertyProjection projection = getOrCreateProperty(properties, propertyName);
                    projection.addGetter(method);
                    propertyMethodKeys.add(methodLogicalKey(method));
                }
            }
            else if (isSetter(method)) {
                String propertyName = propertyName(method);
                if (propertyName != null && !"class".equals(propertyName)) {
                    PropertyProjection projection = getOrCreateProperty(properties, propertyName);
                    projection.addSetter(method);
                    propertyMethodKeys.add(methodLogicalKey(method));
                }
            }
        }
        return new PropertyProjection(properties, propertyMethodKeys);
    }

    private PropertyProjection getOrCreateProperty(SortedMap<String, PropertyProjection> properties, String propertyName) {
        PropertyProjection property = properties.get(propertyName);
        if (property == null) {
            property = new PropertyProjection(propertyName);
            properties.put(propertyName, property);
        }
        return property;
    }

    private List<ExecutableElement> declaredMethods(TypeElement typeElement) {
        List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (isVisibleMember(method)) {
                methods.add(method);
            }
        }
        Collections.sort(methods, new Comparator<ExecutableElement>() {
            @Override
            public int compare(ExecutableElement left, ExecutableElement right) {
                return methodLogicalKey(left).compareTo(methodLogicalKey(right));
            }
        });
        return methods;
    }

    private List<ExecutableElement> visibleSurfaceMethods(TypeElement typeElement) {
        List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
        Map<String, ExecutableElement> methodsByKey = new LinkedHashMap<String, ExecutableElement>();
        for (Element member : elementUtils.getAllMembers(typeElement)) {
            if (!(member instanceof ExecutableElement) || !isVisibleMember(member)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            TypeElement ownerType = enclosingType(method);
            if (ownerType == null) {
                continue;
            }
            if (!includedTypesByQualifiedName.containsKey(qualifiedName(ownerType))) {
                continue;
            }
            methodsByKey.put(methodLogicalKey(method), method);
        }
        methods.addAll(methodsByKey.values());
        Collections.sort(methods, new Comparator<ExecutableElement>() {
            @Override
            public int compare(ExecutableElement left, ExecutableElement right) {
                return methodLogicalKey(left).compareTo(methodLogicalKey(right));
            }
        });
        return methods;
    }

    private List<VariableElement> declaredConstants(TypeElement typeElement) {
        List<VariableElement> fields = new ArrayList<VariableElement>();
        for (VariableElement field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (!isVisibleMember(field)) {
                continue;
            }
            if (field.getKind() == ElementKind.ENUM_CONSTANT || isConstantField(field)) {
                fields.add(field);
            }
        }
        Collections.sort(fields, new Comparator<VariableElement>() {
            @Override
            public int compare(VariableElement left, VariableElement right) {
                return left.getSimpleName().toString().compareTo(right.getSimpleName().toString());
            }
        });
        return fields;
    }

    private List<TypeElement> declaredNestedTypes(TypeElement typeElement) {
        List<TypeElement> nestedTypes = new ArrayList<TypeElement>();
        for (TypeElement nestedType : ElementFilter.typesIn(typeElement.getEnclosedElements())) {
            if (isVisibleType(nestedType)) {
                nestedTypes.add(nestedType);
            }
        }
        Collections.sort(nestedTypes, new Comparator<TypeElement>() {
            @Override
            public int compare(TypeElement left, TypeElement right) {
                return left.getSimpleName().toString().compareTo(right.getSimpleName().toString());
            }
        });
        return nestedTypes;
    }

    private ApiMapNode createWrappedTextGroup(String logicalKey, String title, String text) {
        ApiMapNode group = new ApiMapNode(logicalKey, title, false);
        int lineIndex = 1;
        for (String line : wrapText(text, 96)) {
            group.addChild(new ApiMapNode(logicalKey + ":line:" + lineIndex, line, false));
            lineIndex += 1;
        }
        return group;
    }

    private List<String> wrapText(String text, int width) {
        if (!hasText(text)) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        String[] paragraphs = text.split("\\n\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (currentLine.length() == 0) {
                    currentLine.append(word);
                }
                else if (currentLine.length() + 1 + word.length() <= width) {
                    currentLine.append(' ').append(word);
                }
                else {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentLine.append(word);
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    private void addGroup(ApiMapNode parent, String label, List<ApiMapNode> children) {
        if (children == null || children.isEmpty()) {
            return;
        }
        ApiMapNode groupNode = new ApiMapNode(parent.getLogicalKey() + ":group:" + label, label, true);
        for (ApiMapNode child : children) {
            groupNode.addChild(child);
        }
        parent.addChild(groupNode);
    }

    private void addLine(ApiMapNode parent, String text) {
        parent.addChild(new ApiMapNode(
            parent.getLogicalKey() + ":line:" + (parent.getChildren().size() + 1), text, false));
    }

    private String appendSummary(String label, String summary) {
        if (!hasText(summary)) {
            return label;
        }
        return label + " — " + summary;
    }

    private String shortenSummary(String summary) {
        if (!hasText(summary)) {
            return "";
        }
        if (summary.length() <= 120) {
            return summary;
        }
        return summary.substring(0, 117).trim() + "...";
    }

    private CapabilityMarker capabilityForActualMethod(TypeElement ownerType, ExecutableElement method) {
        TypeRole role = typeRole(ownerType);
        switch (role) {
            case READ_ONLY:
                return CapabilityMarker.READ;
            case WRITE_ONLY:
                TypeElement readOnlyCounterpart = readOnlyCounterpartOf(ownerType);
                return readOnlyCounterpart != null && hasEquivalentSurfaceMethod(readOnlyCounterpart, method)
                    ? CapabilityMarker.READ
                    : CapabilityMarker.WRITE;
            default:
                return null;
        }
    }

    private TypeElement readOnlyCounterpartOf(TypeElement typeElement) {
        String qualifiedName = qualifiedName(typeElement);
        if (qualifiedName.endsWith("RO")) {
            return typeElement;
        }
        return includedTypesByQualifiedName.get(qualifiedName + "RO");
    }

    private boolean hasEquivalentSurfaceMethod(TypeElement typeElement, ExecutableElement method) {
        if (typeElement == null) {
            return false;
        }
        String signature = signatureWithoutReturn(method);
        for (ExecutableElement candidate : visibleSurfaceMethods(typeElement)) {
            if (signature.equals(signatureWithoutReturn(candidate))) {
                return true;
            }
        }
        return false;
    }

    private TypeRole typeRole(TypeElement typeElement) {
        String qualifiedName = qualifiedName(typeElement);
        if (qualifiedName.endsWith("RO") && includedTypesByQualifiedName.containsKey(stripReadOnlySuffix(qualifiedName))) {
            return TypeRole.READ_ONLY;
        }
        if (!qualifiedName.endsWith("RO") && includedTypesByQualifiedName.containsKey(qualifiedName + "RO")) {
            return TypeRole.WRITE_ONLY;
        }
        return TypeRole.UNPAIRED;
    }

    private boolean isGetter(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return method.getReturnType().getKind() != TypeKind.VOID;
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return isBooleanType(method.getReturnType());
        }
        return false;
    }

    private boolean isSetter(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        return methodName.startsWith("set")
            && methodName.length() > 3
            && method.getParameters().size() == 1;
    }

    private boolean isBooleanType(TypeMirror typeMirror) {
        return typeMirror.getKind() == TypeKind.BOOLEAN
            || Boolean.class.getName().equals(typeMirror.toString());
    }

    private String propertyName(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        if (methodName.startsWith("get") || methodName.startsWith("set")) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is")) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        if (value.length() > 1 && Character.isUpperCase(value.charAt(1))) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private String formatMethodSignature(ExecutableElement method) {
        StringBuilder builder = new StringBuilder();
        builder.append(displayMethodName(method));
        builder.append('(');
        List<? extends VariableElement> parameters = method.getParameters();
        for (int index = 0; index < parameters.size(); index += 1) {
            if (index > 0) {
                builder.append(", ");
            }
            VariableElement parameter = parameters.get(index);
            builder.append(parameter.getSimpleName());
            builder.append(": ");
            builder.append(formatType(parameter.asType()));
        }
        builder.append(")");
        if (method.getKind() != ElementKind.CONSTRUCTOR) {
            builder.append(": ");
            builder.append(formatType(method.getReturnType()));
        }
        if (!method.getThrownTypes().isEmpty()) {
            builder.append(" throws ");
            for (int index = 0; index < method.getThrownTypes().size(); index += 1) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(formatType(method.getThrownTypes().get(index)));
            }
        }
        return builder.toString();
    }

    private String displayMethodName(ExecutableElement method) {
        if (method.getKind() == ElementKind.CONSTRUCTOR) {
            return enclosingType(method).getSimpleName().toString();
        }
        return method.getSimpleName().toString();
    }

    private String appendCapabilityMarker(String label, CapabilityMarker capabilityMarker) {
        return capabilityMarker == null ? label : label + " " + capabilityMarker.getMarker();
    }

    private String formatType(TypeMirror typeMirror) {
        return compactTypeDisplay(typeMirror.toString());
    }

    private static String compactTypeDisplay(String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return rawType;
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < rawType.length()) {
            char currentCharacter = rawType.charAt(index);
            if (Character.isJavaIdentifierStart(currentCharacter)) {
                int endIndex = index + 1;
                while (endIndex < rawType.length()) {
                    char nextCharacter = rawType.charAt(endIndex);
                    if (Character.isJavaIdentifierPart(nextCharacter) || nextCharacter == '.') {
                        endIndex += 1;
                    }
                    else {
                        break;
                    }
                }
                builder.append(compactQualifiedToken(rawType.substring(index, endIndex)));
                index = endIndex;
            }
            else {
                builder.append(currentCharacter);
                index += 1;
            }
        }
        return builder.toString();
    }

    private static String compactQualifiedToken(String token) {
        if (token.indexOf('.') < 0) {
            return token;
        }
        String[] segments = token.split("\\.");
        int firstTypeSegmentIndex = -1;
        for (int index = 0; index < segments.length; index += 1) {
            if (!segments[index].isEmpty() && Character.isUpperCase(segments[index].charAt(0))) {
                firstTypeSegmentIndex = index;
                break;
            }
        }
        if (firstTypeSegmentIndex < 0) {
            return token;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = firstTypeSegmentIndex; index < segments.length; index += 1) {
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(segments[index]);
        }
        return builder.toString();
    }

    private String typeLogicalKey(TypeElement typeElement) {
        return "type:" + qualifiedName(typeElement);
    }

    private String propertyLogicalKey(TypeElement ownerType, String propertyName) {
        return "property:" + qualifiedName(ownerType) + "#" + propertyName;
    }

    private String fieldLogicalKey(TypeElement ownerType, VariableElement field) {
        return "field:" + qualifiedName(ownerType) + "#" + field.getSimpleName();
    }

    private String methodLogicalKey(ExecutableElement method) {
        return "method:" + qualifiedName(enclosingType(method)) + "#" + signatureWithoutReturn(method);
    }

    private String surfaceMethodSignatureKey(ExecutableElement method) {
        return signatureWithoutReturn(method);
    }

    private String signatureWithoutReturn(ExecutableElement method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getSimpleName());
        builder.append('(');
        List<? extends VariableElement> parameters = method.getParameters();
        for (int index = 0; index < parameters.size(); index += 1) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(parameters.get(index).asType().toString());
        }
        builder.append(')');
        return builder.toString();
    }

    private boolean isConstantField(VariableElement field) {
        Set<Modifier> modifiers = field.getModifiers();
        return modifiers.contains(Modifier.STATIC) && modifiers.contains(Modifier.FINAL);
    }

    private boolean isVisibleType(TypeElement typeElement) {
        Set<Modifier> modifiers = typeElement.getModifiers();
        return modifiers.contains(Modifier.PUBLIC)
            || modifiers.contains(Modifier.PROTECTED)
            || typeElement.getEnclosingElement().getKind().isInterface();
    }

    private boolean isVisibleMember(Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return modifiers.contains(Modifier.PUBLIC)
            || modifiers.contains(Modifier.PROTECTED)
            || element.getEnclosingElement().getKind().isInterface();
    }

    private boolean isDeprecated(Element element) {
        if (element.getAnnotation(Deprecated.class) != null) {
            return true;
        }
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            if (Deprecated.class.getName().equals(annotation.getAnnotationType().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private String packageName(TypeElement typeElement) {
        PackageElement packageElement = elementUtils.getPackageOf(typeElement);
        return packageElement.getQualifiedName().toString();
    }

    private String qualifiedName(TypeElement typeElement) {
        return typeElement.getQualifiedName().toString();
    }

    private TypeElement enclosingType(ExecutableElement method) {
        Element enclosingElement = method.getEnclosingElement();
        return enclosingElement instanceof TypeElement ? (TypeElement) enclosingElement : null;
    }

    private String stripReadOnlySuffix(String qualifiedName) {
        return qualifiedName.substring(0, qualifiedName.length() - 2);
    }

    private String simpleName(String qualifiedName) {
        int separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? qualifiedName.substring(separatorIndex + 1) : qualifiedName;
    }

    private static final class DocumentationFamily {
        private final String candidateLabel;
        private final List<TypeElement> exactTypes = new ArrayList<TypeElement>();
        private String displayLabel;

        private DocumentationFamily(String candidateLabel) {
            this.candidateLabel = candidateLabel;
            this.displayLabel = candidateLabel;
        }

        public void addExactType(TypeElement typeElement) {
            for (TypeElement existing : exactTypes) {
                if (existing.getQualifiedName().contentEquals(typeElement.getQualifiedName())) {
                    return;
                }
            }
            exactTypes.add(typeElement);
        }

        public void sortExactTypes(final ApiModelBuilder builder) {
            Collections.sort(exactTypes, new Comparator<TypeElement>() {
                @Override
                public int compare(TypeElement left, TypeElement right) {
                    return Integer.compare(typeOrder(builder, left), typeOrder(builder, right));
                }
            });
        }

        private int typeOrder(ApiModelBuilder builder, TypeElement typeElement) {
            String qualifiedName = builder.qualifiedName(typeElement);
            if (qualifiedName.startsWith(PROXY_TOP_LEVEL_TYPE + ".") && qualifiedName.endsWith("RO")) {
                return 0;
            }
            if (qualifiedName.startsWith(PROXY_TOP_LEVEL_TYPE + ".")) {
                return 1;
            }
            if (qualifiedName.startsWith("org.freeplane.api") && qualifiedName.endsWith("RO")) {
                return 2;
            }
            if (qualifiedName.startsWith("org.freeplane.api")) {
                return 3;
            }
            return 4;
        }

        public String summary(ApiModelBuilder builder) {
            for (TypeElement typeElement : exactTypes) {
                String summary = builder.shortenSummary(builder.commentExtractor.extract(typeElement).getSummary());
                if (builder.hasText(summary)) {
                    return summary;
                }
            }
            return "";
        }

        public String getCandidateLabel() {
            return candidateLabel;
        }

        public boolean hasMultipleExactTypes() {
            return exactTypes.size() > 1;
        }

        public void setDisplayLabel(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public List<TypeElement> getExactTypes() {
            return exactTypes;
        }

        public TypeElement getPrimaryType() {
            return exactTypes.get(0);
        }

        public TypeRole typeRole(TypeElement typeElement) {
            if (typeElement == null) {
                return TypeRole.UNPAIRED;
            }
            String qualifiedName = typeElement.getQualifiedName().toString();
            for (TypeElement exactType : exactTypes) {
                String exactTypeName = exactType.getQualifiedName().toString();
                if (exactTypeName.equals(qualifiedName) && exactTypeName.endsWith("RO")
                    && containsQualifiedName(exactTypeName.substring(0, exactTypeName.length() - 2))) {
                    return TypeRole.READ_ONLY;
                }
                if (exactTypeName.equals(qualifiedName) && !exactTypeName.endsWith("RO")
                    && containsQualifiedName(exactTypeName + "RO")) {
                    return TypeRole.WRITE_ONLY;
                }
            }
            return TypeRole.UNPAIRED;
        }

        private boolean containsQualifiedName(String qualifiedName) {
            for (TypeElement exactType : exactTypes) {
                if (exactType.getQualifiedName().contentEquals(qualifiedName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class GroupProperty {
        private final String name;
        private final List<ExecutableElement> getters = new ArrayList<ExecutableElement>();
        private final List<ExecutableElement> setters = new ArrayList<ExecutableElement>();

        private GroupProperty(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void addGetters(Collection<ExecutableElement> methods) {
            merge(methods, getters);
        }

        public void addSetters(Collection<ExecutableElement> methods) {
            merge(methods, setters);
        }

        public List<ExecutableElement> getGetters() {
            return getters;
        }

        public List<ExecutableElement> getSetters() {
            return setters;
        }

        public ExecutableElement representativeMethod(DocumentationFamily family, ApiModelBuilder builder) {
            List<ExecutableElement> orderedGetters = sortMethodsByFamily(getters, family, builder);
            if (!orderedGetters.isEmpty()) {
                return orderedGetters.get(0);
            }
            List<ExecutableElement> orderedSetters = sortMethodsByFamily(setters, family, builder);
            return orderedSetters.isEmpty() ? null : orderedSetters.get(0);
        }

        public Collection<TypeElement> getGetterOwnerTypes() {
            return ownerTypes(getters);
        }

        public Collection<TypeElement> getSetterOwnerTypes() {
            return ownerTypes(setters);
        }

        private Collection<TypeElement> ownerTypes(Collection<ExecutableElement> methods) {
            SortedMap<String, TypeElement> ownerTypes = new TreeMap<String, TypeElement>();
            for (ExecutableElement method : methods) {
                TypeElement ownerType = (TypeElement) method.getEnclosingElement();
                ownerTypes.put(ownerType.getQualifiedName().toString(), ownerType);
            }
            return ownerTypes.values();
        }

        private void merge(Collection<ExecutableElement> source, List<ExecutableElement> target) {
            for (ExecutableElement method : source) {
                String methodKey = PropertyProjection.methodLogicalKeyStatic(method);
                boolean exists = false;
                for (ExecutableElement existing : target) {
                    if (methodKey.equals(PropertyProjection.methodLogicalKeyStatic(existing))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    target.add(method);
                }
            }
        }
    }

    private static final class GroupMethod {
        private final String signatureKey;
        private final List<ExecutableElement> sourceMethods = new ArrayList<ExecutableElement>();

        private GroupMethod(String signatureKey) {
            this.signatureKey = signatureKey;
        }

        public String getSignatureKey() {
            return signatureKey;
        }

        public void addSourceMethods(Collection<ExecutableElement> methods) {
            for (ExecutableElement method : methods) {
                String methodKey = PropertyProjection.methodLogicalKeyStatic(method);
                boolean exists = false;
                for (ExecutableElement existing : sourceMethods) {
                    if (methodKey.equals(PropertyProjection.methodLogicalKeyStatic(existing))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    sourceMethods.add(method);
                }
            }
        }

        public ExecutableElement getRepresentativeMethod(DocumentationFamily family, ApiModelBuilder builder) {
            return sortMethodsByFamily(sourceMethods, family, builder).get(0);
        }

        public List<ExecutableElement> getSourceMethods() {
            return sourceMethods;
        }
    }

    private static final class GroupConstant {
        private final String name;
        private final List<VariableElement> fields = new ArrayList<VariableElement>();

        private GroupConstant(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void addField(VariableElement field) {
            for (VariableElement existing : fields) {
                if (existing.getEnclosingElement().toString().equals(field.getEnclosingElement().toString())
                    && existing.getSimpleName().contentEquals(field.getSimpleName())) {
                    return;
                }
            }
            fields.add(field);
        }

        public VariableElement getRepresentativeField(DocumentationFamily family, ApiModelBuilder builder) {
            List<VariableElement> sortedFields = new ArrayList<VariableElement>(fields);
            Collections.sort(sortedFields, new Comparator<VariableElement>() {
                @Override
                public int compare(VariableElement left, VariableElement right) {
                    TypeElement leftOwner = (TypeElement) left.getEnclosingElement();
                    TypeElement rightOwner = (TypeElement) right.getEnclosingElement();
                    int ownerCompare = Integer.compare(family.typeOrder(builder, leftOwner), family.typeOrder(builder, rightOwner));
                    if (ownerCompare != 0) {
                        return ownerCompare;
                    }
                    return left.getSimpleName().toString().compareTo(right.getSimpleName().toString());
                }
            });
            return sortedFields.get(0);
        }

        public Collection<TypeElement> getOwnerTypes() {
            SortedMap<String, TypeElement> ownerTypes = new TreeMap<String, TypeElement>();
            for (VariableElement field : fields) {
                TypeElement ownerType = (TypeElement) field.getEnclosingElement();
                ownerTypes.put(ownerType.getQualifiedName().toString(), ownerType);
            }
            return ownerTypes.values();
        }
    }

    private static final class GroupNestedType {
        private final String displayName;
        private final List<TypeElement> types = new ArrayList<TypeElement>();

        private GroupNestedType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void addNestedType(TypeElement typeElement) {
            for (TypeElement existing : types) {
                if (existing.getQualifiedName().contentEquals(typeElement.getQualifiedName())) {
                    return;
                }
            }
            types.add(typeElement);
        }

        public TypeElement getRepresentativeType(DocumentationFamily family, ApiModelBuilder builder) {
            List<TypeElement> sortedTypes = new ArrayList<TypeElement>(types);
            Collections.sort(sortedTypes, new Comparator<TypeElement>() {
                @Override
                public int compare(TypeElement left, TypeElement right) {
                    return Integer.compare(family.typeOrder(builder, builder.topLevelEnclosingType(left)),
                        family.typeOrder(builder, builder.topLevelEnclosingType(right)));
                }
            });
            return sortedTypes.get(0);
        }

        public Collection<TypeElement> getTypes() {
            return types;
        }
    }

    private static final class PackageHierarchy {
        private final SortedMap<String, PackageHierarchy> children = new TreeMap<String, PackageHierarchy>();
        private final List<TypeElement> topLevelTypes = new ArrayList<TypeElement>();

        public void add(TypeElement topLevelType) {
            String packageName = ((PackageElement) topLevelType.getEnclosingElement()).getQualifiedName().toString();
            PackageHierarchy current = this;
            if (!packageName.isEmpty()) {
                for (String segment : packageName.split("\\.")) {
                    PackageHierarchy child = current.children.get(segment);
                    if (child == null) {
                        child = new PackageHierarchy();
                        current.children.put(segment, child);
                    }
                    current = child;
                }
            }
            current.topLevelTypes.add(topLevelType);
        }
    }

    private static List<ExecutableElement> sortMethodsByFamily(Collection<ExecutableElement> methods,
                                                               DocumentationFamily family,
                                                               ApiModelBuilder builder) {
        List<ExecutableElement> sortedMethods = new ArrayList<ExecutableElement>(methods);
        Collections.sort(sortedMethods, new Comparator<ExecutableElement>() {
            @Override
            public int compare(ExecutableElement left, ExecutableElement right) {
                int ownerCompare = Integer.compare(
                    family.typeOrder(builder, (TypeElement) left.getEnclosingElement()),
                    family.typeOrder(builder, (TypeElement) right.getEnclosingElement()));
                if (ownerCompare != 0) {
                    return ownerCompare;
                }
                return PropertyProjection.methodLogicalKeyStatic(left)
                    .compareTo(PropertyProjection.methodLogicalKeyStatic(right));
            }
        });
        return sortedMethods;
    }

    private enum TypeRole {
        READ_ONLY,
        WRITE_ONLY,
        UNPAIRED
    }

    private enum CapabilityMarker {
        READ("[read]"),
        WRITE("[write]"),
        READ_WRITE("[read-write]");

        private final String marker;

        CapabilityMarker(String marker) {
            this.marker = marker;
        }

        public String getMarker() {
            return marker;
        }
    }

    private static final class PropertyProjection {
        private final SortedMap<String, PropertyProjection> propertiesByName;
        private final Set<String> propertyMethodKeys;
        private final String name;
        private final List<ExecutableElement> getters;
        private final List<ExecutableElement> setters;

        PropertyProjection(SortedMap<String, PropertyProjection> propertiesByName, Set<String> propertyMethodKeys) {
            this.propertiesByName = propertiesByName;
            this.propertyMethodKeys = propertyMethodKeys;
            this.name = null;
            this.getters = null;
            this.setters = null;
        }

        PropertyProjection(String name) {
            this.propertiesByName = null;
            this.propertyMethodKeys = null;
            this.name = name;
            this.getters = new ArrayList<ExecutableElement>();
            this.setters = new ArrayList<ExecutableElement>();
        }

        public String getName() {
            return name;
        }

        public void addGetter(ExecutableElement getter) {
            addUniqueMethod(getters, getter);
        }

        public void addSetter(ExecutableElement setter) {
            addUniqueMethod(setters, setter);
        }

        public List<ExecutableElement> getSortedGetters() {
            return sortMethods(getters);
        }

        public List<ExecutableElement> getSortedSetters() {
            return sortMethods(setters);
        }

        public CapabilityMarker getCapability() {
            boolean hasRead = getters != null && !getters.isEmpty();
            boolean hasWrite = setters != null && !setters.isEmpty();
            if (hasRead && hasWrite) {
                return CapabilityMarker.READ_WRITE;
            }
            if (hasRead) {
                return CapabilityMarker.READ;
            }
            return CapabilityMarker.WRITE;
        }

        public String describeType() {
            return PropertyTypeDescription.from(typeNames(getters, true), typeNames(setters, false)).getDescription();
        }

        public boolean isPropertyMethod(ExecutableElement method) {
            return propertyMethodKeys != null && propertyMethodKeys.contains(methodLogicalKeyStatic(method));
        }

        public SortedMap<String, PropertyProjection> getPropertiesByName() {
            return propertiesByName == null ? Collections.<String, PropertyProjection>emptySortedMap() : propertiesByName;
        }

        public List<PropertyProjection> getPropertiesInDisplayOrder() {
            if (propertiesByName == null) {
                return Collections.emptyList();
            }
            return new ArrayList<PropertyProjection>(propertiesByName.values());
        }

        private SortedSet<String> typeNames(List<ExecutableElement> methods, boolean getters) {
            SortedSet<String> typeNames = new TreeSet<String>();
            if (methods == null) {
                return typeNames;
            }
            for (ExecutableElement method : methods) {
                if (getters) {
                    typeNames.add(method.getReturnType().toString());
                }
                else if (!method.getParameters().isEmpty()) {
                    typeNames.add(method.getParameters().get(0).asType().toString());
                }
            }
            return typeNames;
        }

        private void addUniqueMethod(List<ExecutableElement> methods, ExecutableElement method) {
            String methodKey = methodLogicalKeyStatic(method);
            for (ExecutableElement existingMethod : methods) {
                if (methodKey.equals(methodLogicalKeyStatic(existingMethod))) {
                    return;
                }
            }
            methods.add(method);
        }

        private List<ExecutableElement> sortMethods(List<ExecutableElement> methods) {
            List<ExecutableElement> sortedMethods = new ArrayList<ExecutableElement>(methods);
            Collections.sort(sortedMethods, new Comparator<ExecutableElement>() {
                @Override
                public int compare(ExecutableElement left, ExecutableElement right) {
                    return methodLogicalKeyStatic(left).compareTo(methodLogicalKeyStatic(right));
                }
            });
            return sortedMethods;
        }

        private static String methodLogicalKeyStatic(ExecutableElement method) {
            TypeElement ownerType = (TypeElement) method.getEnclosingElement();
            StringBuilder builder = new StringBuilder();
            builder.append("method:");
            builder.append(ownerType.getQualifiedName());
            builder.append('#');
            builder.append(method.getSimpleName());
            builder.append('(');
            List<? extends VariableElement> parameters = method.getParameters();
            for (int index = 0; index < parameters.size(); index += 1) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(parameters.get(index).asType().toString());
            }
            builder.append(')');
            return builder.toString();
        }
    }

    private static final class PropertyTypeDescription {
        private final String description;

        private PropertyTypeDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        static PropertyTypeDescription from(SortedSet<String> getterTypes, SortedSet<String> setterTypes) {
            if (!getterTypes.isEmpty() && getterTypes.equals(setterTypes)) {
                return new PropertyTypeDescription(join(getterTypes));
            }
            if (!getterTypes.isEmpty() && setterTypes.isEmpty()) {
                return new PropertyTypeDescription(join(getterTypes));
            }
            if (getterTypes.isEmpty() && !setterTypes.isEmpty()) {
                return new PropertyTypeDescription(join(setterTypes));
            }
            if (getterTypes.isEmpty() && setterTypes.isEmpty()) {
                return new PropertyTypeDescription("Object");
            }
            return new PropertyTypeDescription("read " + join(getterTypes) + "; write " + join(setterTypes));
        }

        private static String join(SortedSet<String> values) {
            StringBuilder builder = new StringBuilder();
            int index = 0;
            for (String value : values) {
                if (index > 0) {
                    builder.append(" | ");
                }
                builder.append(compactTypeDisplay(value));
                index += 1;
            }
            return builder.toString();
        }
    }

    private static final class MethodProjection {
        private final String signatureKey;
        private final List<ExecutableElement> sourceMethods = new ArrayList<ExecutableElement>();

        private MethodProjection(String signatureKey) {
            this.signatureKey = signatureKey;
        }

        public String getSignatureKey() {
            return signatureKey;
        }

        public void addSourceMethod(ExecutableElement method) {
            for (ExecutableElement existingMethod : sourceMethods) {
                if (PropertyProjection.methodLogicalKeyStatic(existingMethod)
                    .equals(PropertyProjection.methodLogicalKeyStatic(method))) {
                    return;
                }
            }
            sourceMethods.add(method);
            Collections.sort(sourceMethods, new Comparator<ExecutableElement>() {
                @Override
                public int compare(ExecutableElement left, ExecutableElement right) {
                    return PropertyProjection.methodLogicalKeyStatic(left)
                        .compareTo(PropertyProjection.methodLogicalKeyStatic(right));
                }
            });
        }

        public ExecutableElement getRepresentativeMethod() {
            return sourceMethods.get(0);
        }

        public List<ExecutableElement> getSourceMethods() {
            return sourceMethods;
        }
    }

    private static final class SurfaceProjection {
        private final SortedMap<String, PropertyProjection> properties;
        private final SortedMap<String, MethodProjection> methods;

        private SurfaceProjection(SortedMap<String, PropertyProjection> properties,
                                  SortedMap<String, MethodProjection> methods) {
            this.properties = properties;
            this.methods = methods;
        }
    }
}
