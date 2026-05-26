package org.freeplane.plugin.script.doclet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;

final class JavadocCommentExtractor {
    private final DocTrees docTrees;

    JavadocCommentExtractor(DocTrees docTrees) {
        this.docTrees = docTrees;
    }

    public DocumentationComment extract(Element element) {
        DocCommentTree docCommentTree = docTrees.getDocCommentTree(element);
        if (docCommentTree == null) {
            return DocumentationComment.empty();
        }
        String summary = normalizeWhitespace(render(docCommentTree.getFirstSentence()));
        String body = normalizeBody(render(docCommentTree.getFullBody()));
        List<ParameterDocumentation> parameters = new ArrayList<ParameterDocumentation>();
        List<ThrowsDocumentation> throwsDocs = new ArrayList<ThrowsDocumentation>();
        String returnDescription = null;
        String deprecatedDescription = null;
        String sinceDescription = null;
        for (DocTree blockTag : docCommentTree.getBlockTags()) {
            if (blockTag instanceof ParamTree) {
                ParamTree paramTree = (ParamTree) blockTag;
                if (!paramTree.isTypeParameter()) {
                    parameters.add(new ParameterDocumentation(
                        paramTree.getName().getName().toString(),
                        normalizeWhitespace(render(paramTree.getDescription()))));
                }
            }
            else if (blockTag instanceof ReturnTree) {
                ReturnTree returnTree = (ReturnTree) blockTag;
                returnDescription = normalizeWhitespace(render(returnTree.getDescription()));
            }
            else if (blockTag instanceof ThrowsTree) {
                ThrowsTree throwsTree = (ThrowsTree) blockTag;
                throwsDocs.add(new ThrowsDocumentation(
                    render(throwsTree.getExceptionName()),
                    normalizeWhitespace(render(throwsTree.getDescription()))));
            }
            else if (blockTag instanceof DeprecatedTree) {
                DeprecatedTree deprecatedTree = (DeprecatedTree) blockTag;
                deprecatedDescription = normalizeWhitespace(render(deprecatedTree.getBody()));
            }
            else if (blockTag instanceof SinceTree) {
                SinceTree sinceTree = (SinceTree) blockTag;
                sinceDescription = normalizeWhitespace(render(sinceTree.getBody()));
            }
        }
        List<String> examples = extractExamples(docCommentTree);
        if (summary.isEmpty() && !body.isEmpty()) {
            summary = firstNonEmptyLine(body);
        }
        return new DocumentationComment(summary, body, parameters, returnDescription,
            throwsDocs, deprecatedDescription, sinceDescription, examples);
    }

    private List<String> extractExamples(DocCommentTree docCommentTree) {
        ExampleCollector collector = new ExampleCollector();
        collector.scan(docCommentTree.getFullBody(), null);
        return collector.getExamples();
    }

    private String render(List<? extends DocTree> trees) {
        StringBuilder builder = new StringBuilder();
        if (trees != null) {
            PlainTextRenderer renderer = new PlainTextRenderer();
            renderer.scan(trees, builder);
        }
        return builder.toString();
    }

    private String render(DocTree tree) {
        if (tree == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        new PlainTextRenderer().scan(tree, builder);
        return builder.toString();
    }

    private String firstNonEmptyLine(String text) {
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String normalizeBody(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> cleanedParagraphs = new ArrayList<String>();
        for (String paragraph : paragraphs) {
            String cleanedParagraph = normalizeWhitespace(paragraph);
            if (!cleanedParagraph.isEmpty()) {
                cleanedParagraphs.add(cleanedParagraph);
            }
        }
        return join(cleanedParagraphs, "\n\n");
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String join(List<String> values, String delimiter) {
        if (values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index += 1) {
            if (index > 0) {
                builder.append(delimiter);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    static final class DocumentationComment {
        private final String summary;
        private final String body;
        private final List<ParameterDocumentation> parameters;
        private final String returnDescription;
        private final List<ThrowsDocumentation> throwsDocs;
        private final String deprecatedDescription;
        private final String sinceDescription;
        private final List<String> examples;

        DocumentationComment(String summary, String body, List<ParameterDocumentation> parameters,
                             String returnDescription, List<ThrowsDocumentation> throwsDocs,
                             String deprecatedDescription, String sinceDescription,
                             List<String> examples) {
            this.summary = summary;
            this.body = body;
            this.parameters = Collections.unmodifiableList(new ArrayList<ParameterDocumentation>(parameters));
            this.returnDescription = returnDescription;
            this.throwsDocs = Collections.unmodifiableList(new ArrayList<ThrowsDocumentation>(throwsDocs));
            this.deprecatedDescription = deprecatedDescription;
            this.sinceDescription = sinceDescription;
            this.examples = Collections.unmodifiableList(new ArrayList<String>(examples));
        }

        static DocumentationComment empty() {
            return new DocumentationComment("", "", Collections.<ParameterDocumentation>emptyList(), null,
                Collections.<ThrowsDocumentation>emptyList(), null, null, Collections.<String>emptyList());
        }

        public String getSummary() {
            return summary;
        }

        public String getBody() {
            return body;
        }

        public List<ParameterDocumentation> getParameters() {
            return parameters;
        }

        public String getReturnDescription() {
            return returnDescription;
        }

        public List<ThrowsDocumentation> getThrowsDocs() {
            return throwsDocs;
        }

        public String getDeprecatedDescription() {
            return deprecatedDescription;
        }

        public String getSinceDescription() {
            return sinceDescription;
        }

        public List<String> getExamples() {
            return examples;
        }
    }

    static final class ParameterDocumentation {
        private final String name;
        private final String description;

        ParameterDocumentation(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    static final class ThrowsDocumentation {
        private final String type;
        private final String description;

        ThrowsDocumentation(String type, String description) {
            this.type = type;
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final class PlainTextRenderer extends DocTreeScanner<Void, StringBuilder> {
        @Override
        public Void visitText(TextTree node, StringBuilder builder) {
            builder.append(node.getBody());
            return null;
        }

        @Override
        public Void visitLiteral(LiteralTree node, StringBuilder builder) {
            builder.append(node.getBody().getBody());
            return null;
        }

        @Override
        public Void visitStartElement(StartElementTree node, StringBuilder builder) {
            String name = node.getName().toString().toLowerCase(Locale.ROOT);
            if ("p".equals(name) || "br".equals(name) || "li".equals(name) || "pre".equals(name)) {
                builder.append('\n');
            }
            return super.visitStartElement(node, builder);
        }

        @Override
        public Void visitEndElement(EndElementTree node, StringBuilder builder) {
            String name = node.getName().toString().toLowerCase(Locale.ROOT);
            if ("p".equals(name) || "li".equals(name) || "pre".equals(name)) {
                builder.append('\n');
            }
            return super.visitEndElement(node, builder);
        }

        @Override
        public Void visitLink(LinkTree node, StringBuilder builder) {
            if (node.getLabel() != null && !node.getLabel().isEmpty()) {
                scan(node.getLabel(), builder);
            }
            else if (node.getReference() != null) {
                scan(node.getReference(), builder);
            }
            return null;
        }

        @Override
        public Void visitReference(ReferenceTree node, StringBuilder builder) {
            builder.append(node.getSignature());
            return null;
        }

        @Override
        public Void visitSee(SeeTree node, StringBuilder builder) {
            if (node.getReference() != null) {
                scan(node.getReference(), builder);
            }
            return null;
        }

        @Override
        public Void visitValue(ValueTree node, StringBuilder builder) {
            if (node.getReference() != null) {
                scan(node.getReference(), builder);
            }
            return null;
        }

        @Override
        public Void visitEntity(EntityTree node, StringBuilder builder) {
            builder.append('&').append(node.getName()).append(';');
            return null;
        }

        @Override
        public Void visitUnknownBlockTag(UnknownBlockTagTree node, StringBuilder builder) {
            scan(node.getContent(), builder);
            return null;
        }

        @Override
        public Void visitUnknownInlineTag(UnknownInlineTagTree node, StringBuilder builder) {
            scan(node.getContent(), builder);
            return null;
        }

    }

    private static final class ExampleCollector extends DocTreeScanner<Void, Void> {
        private final List<String> examples = new ArrayList<String>();
        private boolean insidePre;
        private StringBuilder currentExample;

        public List<String> getExamples() {
            return examples;
        }

        @Override
        public Void visitStartElement(StartElementTree node, Void unused) {
            if ("pre".equalsIgnoreCase(node.getName().toString())) {
                insidePre = true;
                currentExample = new StringBuilder();
            }
            return super.visitStartElement(node, unused);
        }

        @Override
        public Void visitEndElement(EndElementTree node, Void unused) {
            if ("pre".equalsIgnoreCase(node.getName().toString()) && insidePre) {
                String example = normalizeExample(currentExample.toString());
                if (!example.isEmpty()) {
                    examples.add(example);
                }
                insidePre = false;
                currentExample = null;
            }
            return super.visitEndElement(node, unused);
        }

        @Override
        public Void visitText(TextTree node, Void unused) {
            if (insidePre && currentExample != null) {
                currentExample.append(node.getBody());
            }
            return null;
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void unused) {
            if (insidePre && currentExample != null) {
                currentExample.append(node.getBody().getBody());
            }
            return null;
        }

        @Override
        public Void visitEntity(EntityTree node, Void unused) {
            if (insidePre && currentExample != null) {
                currentExample.append('&').append(node.getName()).append(';');
            }
            return null;
        }

        private String normalizeExample(String example) {
            String normalized = example.replace("\r\n", "\n").replace('\r', '\n');
            while (normalized.startsWith("\n")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("\n")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
