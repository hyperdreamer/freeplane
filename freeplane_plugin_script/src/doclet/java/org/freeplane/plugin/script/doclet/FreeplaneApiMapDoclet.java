package org.freeplane.plugin.script.doclet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;

import javax.tools.Diagnostic;

import com.sun.source.util.DocTrees;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

public class FreeplaneApiMapDoclet implements Doclet {
    private static final String OUTPUT_OPTION_NAME = "-freeplaneApiMapOutput";
    private static final String OUTPUT_DIRECTORY_OPTION_NAME = "-d";
    private static final String DOC_TITLE_OPTION_NAME = "-doctitle";
    private static final String WINDOW_TITLE_OPTION_NAME = "-windowtitle";
    private static final String NO_TIMESTAMP_OPTION_NAME = "-notimestamp";

    private Reporter reporter;
    private File outputFile;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "FreeplaneApiMapDoclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        Set<Option> options = new LinkedHashSet<Option>();
        options.add(new OutputFileOption());
        options.add(new NoOpOption(OUTPUT_DIRECTORY_OPTION_NAME, 1, "<directory>"));
        options.add(new NoOpOption(DOC_TITLE_OPTION_NAME, 1, "<html-code>"));
        options.add(new NoOpOption(WINDOW_TITLE_OPTION_NAME, 1, "<text>"));
        options.add(new NoOpOption(NO_TIMESTAMP_OPTION_NAME, 0, ""));
        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        if (outputFile == null) {
            reporter.print(Diagnostic.Kind.ERROR,
                "Missing required doclet option " + OUTPUT_OPTION_NAME + ".");
            return false;
        }
        try {
            DocTrees docTrees = environment.getDocTrees();
            ApiModelBuilder modelBuilder = new ApiModelBuilder(environment, docTrees);
            ApiMapNode rootNode = modelBuilder.build();
            new FreeplaneMindMapWriter().write(rootNode, outputFile);
            reporter.print(Diagnostic.Kind.NOTE,
                "Generated Freeplane API map at " + outputFile.getAbsolutePath());
            return true;
        }
        catch (IOException error) {
            reporter.print(Diagnostic.Kind.ERROR,
                "Failed to generate Freeplane API map: " + error.getMessage());
            return false;
        }
        catch (RuntimeException error) {
            reporter.print(Diagnostic.Kind.ERROR,
                "Failed to generate Freeplane API map: " + error.getMessage());
            return false;
        }
    }

    private static final class NoOpOption implements Option {
        private final String name;
        private final int argumentCount;
        private final String parameters;

        private NoOpOption(String name, int argumentCount, String parameters) {
            this.name = name;
            this.argumentCount = argumentCount;
            this.parameters = parameters;
        }

        @Override
        public int getArgumentCount() {
            return argumentCount;
        }

        @Override
        public String getDescription() {
            return "No-op compatibility option for Gradle Javadoc invocation.";
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Arrays.asList(name);
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            return true;
        }
    }

    private final class OutputFileOption implements Option {
        @Override
        public int getArgumentCount() {
            return 1;
        }

        @Override
        public String getDescription() {
            return "Absolute output file path for the generated Freeplane API .mm map.";
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Arrays.asList(OUTPUT_OPTION_NAME);
        }

        @Override
        public String getParameters() {
            return "<file>";
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            outputFile = new File(arguments.get(0));
            return true;
        }
    }
}
