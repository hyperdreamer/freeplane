/*
 * Freeplane - A Program for creating and viewing MindmapsCopyright (C) 2000-2006
 * Joerg Mueller, Daniel Polansky, Christian Foltin and others.See COPYING for
 * DetailsThis program is free software; you can redistribute it and/ormodify it
 * under the terms of the GNU General Public Licenseas published by the Free
 * Software Foundation; either version 2of the License, or (at your option) any
 * later version.This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty ofMERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See theGNU General Public License for
 * more details.You should have received a copy of the GNU General Public
 * Licensealong with this program; if not, write to the Free SoftwareFoundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA. Created on
 * 02.09.2006
 */
/*
 * $Id: ScriptingEngine.java,v 1.1.2.20 2008/04/18 21:18:26 christianfoltin Exp
 * $
 */
package org.freeplane.plugin.script;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import org.apache.commons.lang.WordUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.LocatedMessage;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.CSTNode;
import org.codehaus.groovy.syntax.SyntaxException;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.map.NodeModel;

import groovy.lang.GroovyRuntimeException;

/**
 * @author foltin
 */
public class ScriptingEngine {
	public static final String SCRIPT_PREFIX = "script";

    public static class GroovyCompilerDiagnostic {
        private final String message;
        private final Integer line;
        private final Integer column;

        GroovyCompilerDiagnostic(String message, Integer line, Integer column) {
            this.message = message;
            this.line = line;
            this.column = column;
        }

        public String getMessage() {
            return message;
        }

        public Integer getLine() {
            return line;
        }

        public Integer getColumn() {
            return column;
        }
    }

    public static class GroovyCompileResult {
        private final boolean successful;
        private final List<GroovyCompilerDiagnostic> compilerDiagnostics;
        private final String errorMessage;

        GroovyCompileResult(boolean successful,
                            List<GroovyCompilerDiagnostic> compilerDiagnostics,
                            String errorMessage) {
            this.successful = successful;
            this.compilerDiagnostics = compilerDiagnostics == null
                ? Collections.<GroovyCompilerDiagnostic>emptyList()
                : Collections.unmodifiableList(new ArrayList<GroovyCompilerDiagnostic>(compilerDiagnostics));
            this.errorMessage = errorMessage;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public List<GroovyCompilerDiagnostic> getCompilerDiagnostics() {
            return compilerDiagnostics;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
	// need a File for caching! Scripts from String have to be cached elsewhere
    private static Map<File, IScript> fileScripts = new ConcurrentHashMap<File, IScript>();
    private static ConcurrentCache<ScriptSpecification, IScript> scripts
    	= new ConcurrentCache(ScriptingEngine::getCompiledScriptCacheSize);
    private static int getCompiledScriptCacheSize() {
        org.freeplane.features.mode.Controller controller = org.freeplane.features.mode.Controller.getCurrentController();
        ResourceController resourceController = controller == null ? null : controller.getResourceController();
        return resourceController == null ? 200 : resourceController.getIntProperty("compiled_script_cache_size", 200);
	}
	/**
	 * @param permissions if null use default scripting permissions.
	 * @return the result of the script, or null, if the user has cancelled.
	 * @throws ExecuteScriptException on errors
	 */
    public static Object executeScript(final NodeModel node, final String script, final IFreeplaneScriptErrorHandler pErrorHandler,
                                final PrintStream pOutStream, final ScriptContext scriptContext,
                                ScriptingPermissions permissions) {
    	return new ScriptRunner(createGroovyScript(script, permissions))
    		.setErrorHandler(pErrorHandler)
    		.setOutStream(pOutStream)
    		.setScriptContext(scriptContext)
    		.execute(node);

    }

    public static int findLineNumberInString(final String resultString, int lineNumber) {
		final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*@ line ([0-9]+).*",
		    java.util.regex.Pattern.DOTALL);
		final Matcher matcher = pattern.matcher(resultString);
		if (matcher.matches()) {
			lineNumber = Integer.parseInt(matcher.group(1));
		}
		return lineNumber;
	}

	public static Object executeScript(final NodeModel node, final String script) {
    	return new ScriptRunner(new GroovyScript(script)).execute(node);
	}

	public static IScript createScript(File scriptFile, ScriptingPermissions permissions, boolean saveForLaterUse) {
	    IScript script = fileScripts.get(scriptFile);
	    if (script == null || ! script.hasPermissions(permissions)) {
	    	if(saveForLaterUse) {
	    		script = compile(scriptFile, permissions);
	    		fileScripts.put(scriptFile, script);
	    	}
	    	else {
	    		script = scripts.computeIfAbsent(new FileScriptSpecification(scriptFile, permissions),
	    			() -> compile(scriptFile, permissions));
	    	}
	    }
	    return script;
    }
	private static IScript compile(File scriptFile, ScriptingPermissions permissions) {
		final boolean isGroovy = scriptFile.getName().endsWith(".groovy");
		IScript script = isGroovy ? new GroovyScript(scriptFile, permissions) : new GenericScript(scriptFile, permissions);
		return script;
	}

	public static IScript createScript(String source, String type, ScriptingPermissions permissions) {
		return scripts.computeIfAbsent(new StringScriptSpecification(source, type, permissions),
				() -> compile(source, type, permissions));
	}

	private static IScript compile(String source, String type, ScriptingPermissions permissions) {
		final boolean isGroovy = type.equals("groovy");
		IScript script = isGroovy ? new GroovyScript(source, permissions) : new GenericScript(source, type, permissions);
	    return script;
	}

	public static IScript createGroovyScript(String script, ScriptingPermissions permissions) {
		return createScript(script, "groovy", permissions);
	}

    public static GroovyCompileResult compileGroovyScriptForDiagnostics(String script,
                                                                        ScriptingPermissions permissions) {
        final int[] lineNumber = new int[] { -1 };
        try (PrintStream outStream = new PrintStream(new ByteArrayOutputStream(), false, "UTF-8")) {
            GroovyScript groovyScript = (GroovyScript) createGroovyScript(script, permissions);
            groovyScript.compile(outStream, new IFreeplaneScriptErrorHandler() {
                @Override
                public void gotoLine(int pLineNumber) {
                    lineNumber[0] = pLineNumber;
                }
            });
            return new GroovyCompileResult(true, Collections.<GroovyCompilerDiagnostic>emptyList(), null);
        } catch (ExecuteScriptException error) {
            List<GroovyCompilerDiagnostic> compilerDiagnostics = compilerDiagnostics(
                error,
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null);
            return new GroovyCompileResult(
                false,
                compilerDiagnostics,
                compilerDiagnostics.isEmpty() ? trimToNull(error.getMessage()) : compilerFailureSummary(compilerDiagnostics.size()));
        } catch (UnsupportedEncodingException error) {
            throw new IllegalStateException("UTF-8 is not available.", error);
        }
    }

    public static Object executeScript(NodeModel node, File scriptFile, ScriptingPermissions permissions) {
        final IScript script = ScriptingEngine.createScript(scriptFile, permissions, false);
        return new ScriptRunner(script).execute(node);
    }

    public static Object executeScript(NodeModel node, String script, ScriptingPermissions permissions) {
     return new ScriptRunner(createGroovyScript(script, permissions)) //
         .execute(node);
 }

    public static Object executeScript(NodeModel node, String script, PrintStream printStream) {
        return new ScriptRunner(createGroovyScript(script, null)) //
            .setOutStream(printStream) //
            .execute(node);
    }

    public static Object executeScript(final NodeModel node, final String script, final ScriptContext scriptContext,
                                       final ScriptingPermissions permissions) {
        return new ScriptRunner(createGroovyScript(script, permissions)) //
            .setScriptContext(scriptContext) //
            .execute(node);
    }

	static void performScriptOperationRecursive(final NodeModel node) {
		for (final NodeModel child : node.getChildren()) {
			performScriptOperationRecursive(child);
		}
		performScriptOperation(node);
	}

	static void performScriptOperation(final NodeModel node) {
		final NodeAttributeTableModel attributes = NodeAttributeTableModel.getModel(node);
		if (attributes == null) {
			return;
		}
        final Map<String, String> savedInputTexts = new ConcurrentHashMap<String, String>();
        for (int row = 0; row < attributes.getRowCount(); ++row) {
            final String attrKey = (String) attributes.getName(row);
            final Object value = attributes.getValue(row);
            if (value instanceof String && ScriptInputJsonSupport.isCompanionAttributeName(attrKey)) {
                savedInputTexts.put(attrKey, (String) value);
            }
        }
		for (int row = 0; row < attributes.getRowCount(); ++row) {
			final String attrKey = (String) attributes.getName(row);
			final Object value = attributes.getValue(row);
			if(value instanceof String && attrKey.startsWith(ScriptingEngine.SCRIPT_PREFIX)){
				final String script = (String) value;
                ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(
                    savedInputTexts.get(ScriptInputJsonSupport.companionAttributeName(attrKey)));
                if (!parseResult.isSuccessful()) {
                    throw ScriptInputJsonSupport.toExecuteScriptException(parseResult.getDiagnostic());
                }
                ScriptContext scriptContext = new ScriptContext(null)
                    .withBoundVariables(ScriptInputJsonSupport.boundVariables(parseResult.getArgsValue()));
				executeScript(node, script, scriptContext, null);
			}
		}
		return;
	}

	/** @deprecated use ScriptResources.getUserScriptDir() instead. */
    @Deprecated
    public static File getUserScriptDir() {
        return ScriptResources.getUserScriptDir();
    }

    private static List<GroovyCompilerDiagnostic> compilerDiagnostics(ExecuteScriptException error,
                                                                   Integer fallbackLine) {
        Throwable compilerFailure = deepestCompilerFailure(error);
        if (compilerFailure instanceof MultipleCompilationErrorsException) {
            List<GroovyCompilerDiagnostic> compilerDiagnostics = structuredDiagnostics(
                (MultipleCompilationErrorsException) compilerFailure);
            if (!compilerDiagnostics.isEmpty()) {
                return compilerDiagnostics;
            }
        }
        GroovyCompilerDiagnostic fallbackDiagnostic = fallbackDiagnostic(compilerFailure, error, fallbackLine);
        if (fallbackDiagnostic == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(fallbackDiagnostic);
    }

    private static Throwable deepestCompilerFailure(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        Throwable multipleCompilationFailure = null;
        Throwable groovyRuntimeFailure = null;
        while (current != null) {
            if (current instanceof MultipleCompilationErrorsException) {
                multipleCompilationFailure = current;
            }
            else if (current instanceof GroovyRuntimeException) {
                groovyRuntimeFailure = current;
            }
            last = current;
            current = current.getCause() == current ? null : current.getCause();
        }
        if (multipleCompilationFailure != null) {
            return multipleCompilationFailure;
        }
        if (groovyRuntimeFailure != null) {
            return groovyRuntimeFailure;
        }
        return last;
    }

    private static List<GroovyCompilerDiagnostic> structuredDiagnostics(MultipleCompilationErrorsException error) {
        ErrorCollector errorCollector = error.getErrorCollector();
        if (errorCollector == null || !errorCollector.hasErrors()) {
            return Collections.emptyList();
        }
        List<GroovyCompilerDiagnostic> compilerDiagnostics = new ArrayList<GroovyCompilerDiagnostic>();
        for (Message message : errorCollector.getErrors()) {
            GroovyCompilerDiagnostic diagnostic = toCompilerDiagnostic(message);
            if (diagnostic != null) {
                compilerDiagnostics.add(diagnostic);
            }
        }
        return compilerDiagnostics;
    }

    private static GroovyCompilerDiagnostic toCompilerDiagnostic(Message message) {
        if (message == null) {
            return null;
        }
        String renderedMessage = trimToNull(renderCompilerMessage(message));
        Integer line = null;
        Integer column = null;
        if (message instanceof SyntaxErrorMessage) {
            SyntaxException cause = ((SyntaxErrorMessage) message).getCause();
            line = positiveOrNull(cause == null ? -1 : cause.getStartLine());
            column = positiveOrNull(cause == null ? -1 : cause.getStartColumn());
        }
        else if (message instanceof LocatedMessage) {
            CSTNode context = ((LocatedMessage) message).getContext();
            line = positiveOrNull(context == null ? -1 : context.getStartLine());
            column = positiveOrNull(context == null ? -1 : context.getStartColumn());
        }
        if (renderedMessage == null && line == null && column == null) {
            return null;
        }
        return new GroovyCompilerDiagnostic(renderedMessage, line, column);
    }

    private static String renderCompilerMessage(Message message) {
        StringWriter writerBuffer = new StringWriter();
        PrintWriter writer = new PrintWriter(writerBuffer);
        Janitor janitor = new Janitor();
        try {
            message.write(writer, janitor);
        }
        finally {
            janitor.cleanup();
            writer.flush();
        }
        return writerBuffer.toString();
    }

    private static GroovyCompilerDiagnostic fallbackDiagnostic(Throwable compilerFailure,
                                                               ExecuteScriptException error,
                                                               Integer fallbackLine) {
        String message = trimToNull(error == null ? null : error.getMessage());
        if (message == null) {
            return null;
        }
        Integer line = fallbackLine;
        Integer column = null;
        if (compilerFailure instanceof GroovyRuntimeException) {
            ASTNode positionNode = positionNode((GroovyRuntimeException) compilerFailure);
            line = positiveOrNull(positionNode == null ? -1 : positionNode.getLineNumber());
            column = positiveOrNull(positionNode == null ? -1 : positionNode.getColumnNumber());
            if (line == null) {
                line = fallbackLine;
            }
        }
        return new GroovyCompilerDiagnostic(message, line, column);
    }

    private static ASTNode positionNode(GroovyRuntimeException error) {
        if (error == null) {
            return null;
        }
        ModuleNode module = error.getModule();
        if (module != null && positiveOrNull(module.getLineNumber()) != null) {
            return module;
        }
        ASTNode node = error.getNode();
        return node != null && positiveOrNull(node.getLineNumber()) != null ? node : null;
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? Integer.valueOf(value) : null;
    }

    private static String compilerFailureSummary(int diagnosticCount) {
        return diagnosticCount == 1
            ? "Groovy compilation failed with 1 diagnostic."
            : "Groovy compilation failed with " + diagnosticCount + " diagnostics.";
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void showScriptExceptionErrorMessage(ExecuteScriptException ex) {
        if (ex.getCause() instanceof SecurityException) {
        	final String message = WordUtils.wrap(ex.getCause().getMessage(), 80, "\n    ", false);
        	UITools.errorMessage(TextUtils.format("ExecuteScriptSecurityError.text", message));
        }
        else {
        	final String message = WordUtils.wrap(ex.getMessage(), 80, "\n    ", false);
        	UITools.errorMessage(TextUtils.format("ExecuteScriptError.text", message));
        }
    }
}
