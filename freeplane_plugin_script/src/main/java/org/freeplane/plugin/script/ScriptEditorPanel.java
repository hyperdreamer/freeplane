/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file author is Christian Foltin
 *  It is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.plugin.script;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.LabelAndMnemonicSetter;
import org.freeplane.features.ai.code.AiChatAttachment;
import org.freeplane.features.ai.code.AiChatAttachmentService;
import org.freeplane.features.ai.code.AiChatRepairRequest;
import org.freeplane.features.ai.code.AiCodeEditor;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CodeStateContent;
import org.freeplane.features.ai.code.CodeStateDiagnostic;
import org.freeplane.features.ai.code.CodeStateDiagnostics;
import org.freeplane.features.ai.code.CodeStateField;
import org.freeplane.features.ai.code.CodeStateToken;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.ScriptRunInitiator;
import org.freeplane.core.ui.UIBuilder;
import org.freeplane.core.ui.components.EmptyIcon;
import org.freeplane.core.ui.components.JRestrictedSizeScrollPane;
import org.freeplane.core.ui.components.OptionalDontShowMeAgainDialog;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.CapturedPrintStream;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.text.mindmapmode.SourceTextEditorUIConfigurator;

import de.sciss.syntaxpane.actions.ActionUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 */
class ScriptEditorPanel extends JDialog implements AiCodeEditor {

	static final String GROOVY_EDITOR_FONT = "groovy_editor_font";
	static final String GROOVY_EDITOR_FONT_SIZE = "groovy_editor_font_size";

	private static final String internalCharset = "UTF-16BE";
	private static final String AI_TAB_ICON_RESOURCE = "/images/panelTabs/aiTab.svg?useAccentColor=true";
	private static final String AI_ATTACHMENT_CONTENT_TYPE = "text/x-freeplane-script-groovy";
	private static final String EDITOR_CONTENT_TYPE = "text/groovy";
	private static final String ATTACHED_SCRIPT_FAILURE_PROMPT =
		"The attached Freeplane script was run manually and failed. Very briefly describe the failure to the user and ask whether they want you to fix it.";
    private static final String INVALID_JSON_SAVE_EXIT_PROPERTY = "script_editor_save_invalid_json";

	final private class AttachToAiAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			if (aiChatAttachment != null) {
				aiChatAttachment.detach();
				updateAiAttachButtonState();
				return;
			}
			AiChatAttachmentService attachmentService = lookupAiChatAttachmentService();
			if (attachmentService == null) {
				LogUtils.severe("AI attachment service is unavailable.");
				updateAiAttachButtonState();
				return;
			}
			setAiChatAttachment(attachmentService.attachEditor(ScriptEditorPanel.this, AI_ATTACHMENT_CONTENT_TYPE));
		}
	}

	final private class CancelAction extends AbstractAction {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private CancelAction(final String pArg0) {
			super(pArg0);
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			disposeDialog(true);
		}
	}

	final private class ExitAction extends AbstractAction {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private ExitAction(final String pArg0) {
			super(pArg0);
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			storeCurrent();
			disposeDialog(false);
		}
	}

	public interface IScriptModel {
		/**
		 * @return the index of the new script.
		 */
		int addNewScript();

		ScriptEditorWindowConfigurationStorage decorateDialog(ScriptEditorPanel pPanel,
		                                                      String pWindow_preference_storage_property);

		void endDialog(boolean pIsCanceled);

        Object executeScript(int pIndex,
                             PrintStream outStream,
                             PrintStream callbackOutputStream,
                             IFreeplaneScriptErrorHandler pErrorHandler);

		int getAmountOfScripts();

		/**
		 * @param pIndex
		 *            zero-based
		 * @return a script
		 */
		ScriptHolder getScript(int pIndex);

		boolean isDirty();

		void setScript(int pIndex, ScriptHolder pScript);

		void storeDialogPositions(ScriptEditorPanel pPanel, ScriptEditorWindowConfigurationStorage pStorage,
		                          String pWindow_preference_storage_property);

		String getTitle();
	}

	final private class NewScriptAction extends AbstractAction {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private NewScriptAction() {
		}

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				storeCurrent();
				mLastSelected = null;
				final int scriptIndex = mScriptModel.addNewScript();
				updateFields();
				select(scriptIndex);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						mScriptTextField.requestFocusInWindow();
					}
				});
			}
		}

	final private class ResultFieldStream extends OutputStream {
		private final byte[] buf = new byte[2];
		private int i = 0;

		@Override
		public void write(final int pByte) throws IOException {
			buf[i++] = (byte) pByte;
			if (i == 2) {
				mScriptResultField.append(new String(buf, internalCharset));
				i = 0;
			}
		}

		@Override
		public void write(final byte b[], int off, int len) throws IOException {
			if (i == 1) {
				write(b[off++]);
				len--;
			}
			if (len <= 0) {
				return;
			}
			final int len2 = len & ~1;
			mScriptResultField.append(new String(b, off, len2, internalCharset));
			if (len2 != len) {
				write(b[len2]);
			}
		}
	}

	final private class RunAction extends AbstractAction {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		private RunAction() {
			super();
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			if (mScriptList.isSelectionEmpty()) {
				return;
			}
			try {
				RunCodeResponse response = runCode(
					new RunCodeRequest(ScriptHost.ATTACHED_EDITOR, null),
					ScriptRunInitiator.USER);
				renderManualRunResult(response);
				ReadCodeResponse codeState = recordAiAttachmentAfterManualRun(response);
				showManualRunFailure(response, codeState);
			}
			catch (Throwable e) {
				LogUtils.warn(e);
				Throwable cause = e.getCause();
				String causeMessage = "";
				if (cause != null && cause.getMessage() != null) {
					causeMessage = cause.getMessage();
				}
				final String message = e.getMessage() != null ? e.getMessage() : "";
				UITools.errorMessage(e.getClass().getName() + ": " + causeMessage
					+ ((causeMessage.length() != 0 && message.length() != 0) ? ", " : "") + message);
			}
		}
	}

	public static class ScriptHolder {
		String mScript;
		String mScriptName;
        String mInputText;

		/**
		 * @param pScriptName
		 *            script name (starting with "script"
		 *            (ScriptingEngine.SCRIPT_PREFIX))
		 * @param pScript
		 *            script content
		 */
		public ScriptHolder(final String pScriptName, final String pScript, final String pInputText) {
			super();
			mScript = pScript;
			mScriptName = pScriptName;
            mInputText = pInputText;
		}

		public String getScript() {
			return mScript;
		}

		public String getScriptName() {
			return mScriptName;
		}

        public String getArgumentsJsonText() {
            return mInputText;
        }

		public ScriptHolder setScript(final String pScript) {
			mScript = pScript;
			return this;
		}

		public ScriptHolder setScriptName(final String pScriptName) {
			mScriptName = pScriptName;
			return this;
		}

        public ScriptHolder setArgumentsJsonText(String pInputText) {
            mInputText = pInputText;
            return this;
        }
	}

	final private class SignAction extends AbstractAction {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
// // 		final private Controller controller;

		private SignAction( final String pArg0) {
			super(pArg0);
//			this.controller = controller;
		}

		@Override
		public void actionPerformed(final ActionEvent arg0) {
			storeCurrent();
			if (!mScriptList.isSelectionEmpty()) {
				final int selectedIndex = mScriptList.getSelectedIndex();
				final ScriptHolder script = mScriptModel.getScript(selectedIndex);
				final String signedScript = new SignedScriptHandler().signScript(script.mScript);
				script.setScript(signedScript);
				mScriptModel.setScript(selectedIndex, script);
				mScriptTextField.setText(signedScript);
			}
		}
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	/**
	 *
	 */
	private static final String WINDOW_PREFERENCE_STORAGE_PROPERTY = "plugins.script.ScriptEditorPanel/window_positions";
	final private JSplitPane mCentralPanel;
	final private JSplitPane mCentralUpperPanel;
	private Integer mLastSelected = null;
	final private DefaultListModel mListModel;
	final private JToggleButton mAttachToAiButton;
	final private AbstractAction mRunAction;
	final private JList mScriptList;
	final private IScriptModel mScriptModel;
	final private JTextArea mScriptResultField;
	final private JTextArea mScriptInputField;
	final private JTextComponent mScriptTextField;
	final private SignAction mSignAction;
	final private JLabel mStatus;
    private final boolean mSavedNodeScriptEditor;
	private AiChatAttachment aiChatAttachment;

	public ScriptEditorPanel( final IScriptModel pScriptModel,
	                         final boolean pHasNewScriptFunctionality) {
		super(UITools.getCurrentFrame(), false /* non modal */);
		mScriptModel = pScriptModel;
        mSavedNodeScriptEditor = pHasNewScriptFunctionality;
		String scriptTitle = pScriptModel.getTitle();
		this.setTitle(TextUtils.getText("plugins/ScriptEditor/window.title") +
				(scriptTitle.isEmpty() ? "" : " [" + scriptTitle + "]"));
		this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                disposeDialog(true);
            }
            @Override
            public void windowOpened(final WindowEvent event) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                    	if(mScriptList.getModel().getSize() > 0)
                    		mScriptList.setSelectedIndex(0);
                    }
                });
            }
        });
		UITools.addEscapeActionToDialog(this, new AbstractAction() {
			/**
			 *
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent arg0) {
				disposeDialog(true);
			}
		});
		final Container contentPane = this.getContentPane();
		contentPane.setLayout(new BorderLayout());
		mListModel = new DefaultListModel();
		mScriptList = new JList(mListModel);
		mScriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		mScriptList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent pEvent) {
				if (pEvent.getValueIsAdjusting()) {
					return;
				}
				select(mScriptList.getSelectedIndex());
			}
		});
		final JEditorPane editorPane = new JEditorPane();
		SourceTextEditorUIConfigurator.configureColors(editorPane);
		mScriptTextField = editorPane;
		mScriptInputField = new JTextArea();
		mScriptTextField.setEnabled(false);
        mScriptInputField.setEnabled(false);
		JScrollPane scriptScrollPane = new JRestrictedSizeScrollPane(mScriptTextField);
        scriptScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_code")));
		UITools.setScrollbarIncrement(scriptScrollPane);
        JScrollPane inputScrollPane = new JRestrictedSizeScrollPane(mScriptInputField);
        inputScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(TextUtils.getText("ai_owned_script_dialog_input_json")));
        UITools.setScrollbarIncrement(inputScrollPane);
        JSplitPane editorFieldsPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scriptScrollPane, inputScrollPane);
        editorFieldsPanel.setResizeWeight(0.75d);
        editorFieldsPanel.setContinuousLayout(true);
		mCentralUpperPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mScriptList, editorFieldsPanel);
		try {
			editorPane.setContentType(EDITOR_CONTENT_TYPE);

			final String fontName = ResourceController.getResourceController().getProperty(GROOVY_EDITOR_FONT);
			final int fontSize = ResourceController.getResourceController().getIntProperty(GROOVY_EDITOR_FONT_SIZE);
			final Font font = UITools.scaleUI(new Font(fontName, Font.PLAIN, fontSize));
			editorPane.setFont(font);
            mScriptInputField.setFont(font);

		} catch (Exception e) {
			LogUtils.severe(e);
			editorPane.setContentType("text/plain");
		}
		mCentralUpperPanel.setContinuousLayout(true);
		mScriptResultField = new JTextArea();
		mScriptResultField.setEditable(false);
		mScriptResultField.setWrapStyleWord(true);
		JScrollPane resultScrollPane = new JScrollPane(mScriptResultField);
		UITools.setScrollbarIncrement(resultScrollPane);
		mCentralPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mCentralUpperPanel, resultScrollPane);
		mCentralPanel.setDividerLocation(0.8);
		mCentralPanel.setContinuousLayout(true);
		contentPane.add(mCentralPanel, BorderLayout.CENTER);
		mStatus = new JLabel();
		contentPane.add(mStatus, BorderLayout.SOUTH);
		mScriptTextField.addCaretListener(new CaretListener() {
			@Override
			public void caretUpdate(final CaretEvent arg0) {
				final int caretPosition = mScriptTextField.getCaretPosition();
				try {
	                final int lineOfOffset = ActionUtils.getLineNumber(mScriptTextField, caretPosition);
	                mStatus.setText("Line: " + (lineOfOffset + 1) + ", Column: "
	                	+ (caretPosition - ActionUtils.getLineNumber(mScriptTextField, lineOfOffset) + 1));
                }
                catch (Exception e) {
	                e.printStackTrace();
                }
			}
		});
		updateFields();
		mScriptTextField.repaint();
		final JMenuBar menuBar = new JMenuBar();
		final JMenu menu = new JMenu();
		LabelAndMnemonicSetter.setLabelAndMnemonic(menu, TextUtils.getRawText("plugins/ScriptEditor.menu_actions"));
		if (pHasNewScriptFunctionality) {
			addButton(menuBar, new NewScriptAction(), "plugins/ScriptEditor.new_script");
		}
		mRunAction = new RunAction();
		mRunAction.setEnabled(false);
		addButton(menuBar, mRunAction, "plugins/ScriptEditor.run");
		final AbstractAction exitAction = new ExitAction(TextUtils.getRawText("plugins/ScriptEditor.exit"));
		addButton(menuBar, exitAction, "plugins/ScriptEditor.exit");
		mAttachToAiButton = TranslatedElementFactory.createToggleButton("plugins/ScriptEditor.ai");
		mAttachToAiButton.setEnabled(false);
		mAttachToAiButton.setIcon(ResourceController.getResourceController().getImageIcon(AI_TAB_ICON_RESOURCE));
		mAttachToAiButton.addActionListener(new AttachToAiAction());
		menuBar.add(mAttachToAiButton);
		mSignAction = new SignAction(TextUtils.getRawText("plugins/ScriptEditor.sign"));
		mSignAction.setEnabled(false);
		addAction(menu, mSignAction);
		final AbstractAction cancelAction = new CancelAction(TextUtils.getRawText("plugins/ScriptEditor.cancel"));
		addAction(menu, cancelAction);
		menuBar.add(menu);
		this.setJMenuBar(menuBar);
		final ScriptEditorWindowConfigurationStorage storage = mScriptModel.decorateDialog(this,
		    ScriptEditorPanel.WINDOW_PREFERENCE_STORAGE_PROPERTY);
		if (storage != null) {
			mCentralUpperPanel.setDividerLocation(storage.getLeftRatio());
			mCentralPanel.setDividerLocation(storage.getTopRatio());
		}
		else {
			mCentralUpperPanel.setDividerLocation(100);
			mCentralPanel.setDividerLocation(240);
		}
		mScriptList.setToolTipText("Use Alt+` to switch between list and editor");
		final KeyStroke keystroke = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, KeyEvent.ALT_DOWN_MASK);
		mScriptList.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keystroke, "focusMe");
		mScriptList.getActionMap().put("focusMe", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (mScriptList.isFocusOwner()) {
					mScriptTextField.requestFocusInWindow();
				} else {
					mScriptList.requestFocusInWindow();
				}
			}
		});
	}

	private void addAction(final JMenu menu, final AbstractAction action) {
		final JMenuItem item = menu.add(action);
		LabelAndMnemonicSetter.setLabelAndMnemonic(item, (String) action.getValue(Action.NAME));
		item.setIcon(new EmptyIcon(UIBuilder.ICON_SIZE));
	}

	private void addButton(final JMenuBar menu, final AbstractAction action, String label) {
		final JButton button = TranslatedElementFactory.createButton(action, label);
		menu.add(button);
	}

	private void setAiChatAttachment(AiChatAttachment attachment) {
		aiChatAttachment = attachment;
		if (aiChatAttachment != null) {
			aiChatAttachment.setDetachHandler(new Runnable() {
				@Override
				public void run() {
					aiChatAttachment = null;
					updateAiAttachButtonState();
				}
			});
		}
		updateAiAttachButtonState();
	}

	private void updateAiAttachButtonState() {
		mAttachToAiButton.setSelected(aiChatAttachment != null);
	}

	/**
	 * @param pIsCanceled
	 */
	private void disposeDialog(final boolean pIsCanceled) {
		if (!mScriptList.isSelectionEmpty()) {
			select(mScriptList.getSelectedIndex());
		}
		if (pIsCanceled && mScriptModel.isDirty()) {
			final int action = JOptionPane.showConfirmDialog(this, TextUtils
			    .getText("ScriptEditorPanel.changed_cancel"), "Freeplane", JOptionPane.OK_CANCEL_OPTION);
			if (action == JOptionPane.CANCEL_OPTION || action == JOptionPane.CLOSED_OPTION) {
				return;
			}
		}
        if (!pIsCanceled && mSavedNodeScriptEditor) {
            ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(mScriptInputField.getText());
            if (!parseResult.isSuccessful()) {
                int action = OptionalDontShowMeAgainDialog.showWithExplanation(
                    "ScriptEditorPanel.save_invalid_json",
                    "ScriptEditorPanel.save_invalid_json.explanation",
                    INVALID_JSON_SAVE_EXIT_PROPERTY,
                    OptionalDontShowMeAgainDialog.MessageType.ONLY_OK_SELECTION_IS_STORED);
                if (action != JOptionPane.OK_OPTION) {
                    return;
                }
            }
        }
		if (aiChatAttachment != null) {
			aiChatAttachment.detach();
		}
		final ScriptEditorWindowConfigurationStorage storage = new ScriptEditorWindowConfigurationStorage();
		storage.setLeftRatio(mCentralUpperPanel.getDividerLocation());
		storage.setTopRatio(mCentralPanel.getDividerLocation());
		mScriptModel.storeDialogPositions(this, storage, ScriptEditorPanel.WINDOW_PREFERENCE_STORAGE_PROPERTY);
		this.setVisible(false);
		this.dispose();
		mScriptModel.endDialog(pIsCanceled);
	}

	IFreeplaneScriptErrorHandler getErrorHandler() {
		return new IFreeplaneScriptErrorHandler() {
			@Override
			public void gotoLine(final int pLineNumber) {
				ActionUtils.setCaretPosition(mScriptTextField, pLineNumber, 1);
			}
		};
	}

	PrintStream getPrintStream() {
		try {
			return new PrintStream(new ResultFieldStream(), false, internalCharset);
		}
		catch (final UnsupportedEncodingException e) {
			return null;
		}
	}

	@Override
	public CodeStateContent getCodeStateContent() {
		return new CodeStateContent(mScriptTextField.getText(), mScriptInputField.getText());
	}

	@Override
	public void replaceCodeStateContent(CodeStateContent content) {
		mScriptTextField.setText(content == null || content.getSourceText() == null ? "" : content.getSourceText());
        mScriptInputField.setText(content == null || content.getArgumentsJsonText() == null ? "" : content.getArgumentsJsonText());
	}

	@Override
	public CompileCodeResponse compileCode(CompileCodeRequest request) {
        CodeStateContent content = getCodeStateContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(
            content.getArgumentsJsonText(),
            stateToken.getArgumentsFingerprint());
        if (!parseResult.isSuccessful()) {
            List<CodeStateDiagnostic> diagnostics = Collections.singletonList(parseResult.getDiagnostic());
            return new CompileCodeResponse(
                ScriptHost.ATTACHED_EDITOR,
                AI_ATTACHMENT_CONTENT_TYPE,
                CodeState.INVALID_ARGUMENTS_JSON,
                stateToken,
                diagnostics,
                ScriptInputJsonSupport.primaryMessage(parseResult.getDiagnostic()));
        }
		ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
			content.getSourceText(),
			ScriptingPermissions.getPermissiveScriptingPermissions());
		return new CompileCodeResponse(
			ScriptHost.ATTACHED_EDITOR,
			AI_ATTACHMENT_CONTENT_TYPE,
			compileResult.isSuccessful() ? CodeState.RUNNABLE : CodeState.INVALID_SCRIPT,
			stateToken,
			CodeStateDiagnostics.sourceDiagnostics(compileResult.getCompilerDiagnostics(), compileResult.getLineNumber()),
			compileResult.getErrorMessage());
	}

	@Override
	public RunCodeResponse runCode(RunCodeRequest request) {
		return runCode(request, ScriptRunInitiator.AI);
	}

	private RunCodeResponse runCode(RunCodeRequest request, ScriptRunInitiator runInitiator) {
		storeCurrent();
		if (mScriptList.isSelectionEmpty()) {
			throw new IllegalStateException("No script is selected.");
		}
        CodeStateContent content = getCodeStateContent();
        CodeStateToken stateToken = CodeStateToken.fromContent(content);
        ScriptInputJsonSupport.ParseResult parseResult = ScriptInputJsonSupport.parseInputText(
            content.getArgumentsJsonText(),
            stateToken.getArgumentsFingerprint());
        if (!parseResult.isSuccessful()) {
            List<CodeStateDiagnostic> diagnostics = Collections.singletonList(parseResult.getDiagnostic());
            return new RunCodeResponse(
                ScriptHost.ATTACHED_EDITOR,
                AI_ATTACHMENT_CONTENT_TYPE,
                CodeState.INVALID_ARGUMENTS_JSON,
                runInitiator,
                stateToken,
                diagnostics,
                ScriptInputJsonSupport.primaryMessage(parseResult.getDiagnostic()),
                null,
                null);
        }
		ScriptingEngine.GroovyCompileResult compileResult = ScriptingEngine.compileGroovyScriptForDiagnostics(
			content.getSourceText(),
			ScriptingPermissions.getPermissiveScriptingPermissions());
		if (!compileResult.isSuccessful()) {
			return new RunCodeResponse(
				ScriptHost.ATTACHED_EDITOR,
				AI_ATTACHMENT_CONTENT_TYPE,
				CodeState.INVALID_SCRIPT,
				runInitiator,
				stateToken,
				CodeStateDiagnostics.sourceDiagnostics(compileResult.getCompilerDiagnostics(), compileResult.getLineNumber()),
				compileResult.getErrorMessage(),
				null,
				null);
		}
		final int[] lineNumber = new int[] { -1 };
        CapturedPrintStream outputCapture = CapturedPrintStream.tee(System.out);
        PrintStream callbackOutputStream = getPrintStream();
        try {
            Object result = mScriptModel.executeScript(
                mScriptList.getSelectedIndex(),
                outputCapture.printStream(),
                callbackOutputStream,
                new IFreeplaneScriptErrorHandler() {
                    @Override
                    public void gotoLine(final int pLineNumber) {
                        lineNumber[0] = pLineNumber;
                        ActionUtils.setCaretPosition(mScriptTextField, pLineNumber, 1);
                    }
                });
			return new RunCodeResponse(
				ScriptHost.ATTACHED_EDITOR,
				AI_ATTACHMENT_CONTENT_TYPE,
				CodeState.RUN_SUCCEEDED,
				runInitiator,
				stateToken,
				null,
				null,
				outputCapture.text(),
				toJsonSafeValue(result));
		}
		catch (ExecuteScriptException e) {
            List<CodeStateDiagnostic> diagnostics = CodeStateDiagnostics.singleton(
                CodeStateField.SOURCE_TEXT,
                e.getMessage(),
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                null);
			return new RunCodeResponse(
				ScriptHost.ATTACHED_EDITOR,
				AI_ATTACHMENT_CONTENT_TYPE,
				CodeState.RUN_FAILED,
				runInitiator,
				stateToken,
				diagnostics,
				e.getMessage(),
				outputCapture.text(),
				null);
		}
		catch (RuntimeException e) {
            List<CodeStateDiagnostic> diagnostics = CodeStateDiagnostics.singleton(
                CodeStateField.SOURCE_TEXT,
                e.getMessage(),
                lineNumber[0] >= 0 ? Integer.valueOf(lineNumber[0]) : null,
                null);
			return new RunCodeResponse(
				ScriptHost.ATTACHED_EDITOR,
				AI_ATTACHMENT_CONTENT_TYPE,
				CodeState.RUN_FAILED,
				runInitiator,
				stateToken,
				diagnostics,
				e.getMessage(),
				outputCapture.text(),
				null);
		}
		catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
		finally {
			outputCapture.close();
		}
	}

	private void renderManualRunResult(RunCodeResponse response) {
		mScriptResultField.setText("");
		if (response != null && response.getStdout() != null) {
			mScriptResultField.append(response.getStdout());
		}
		mScriptResultField.append(TextUtils.getText("plugins/ScriptEditor/window.Result"));
		if (response == null) {
			return;
		}
		if (response.getCodeState() == CodeState.RUN_SUCCEEDED) {
			mScriptResultField.append(String.valueOf(response.getStructuredResult()));
		}
		else if (response.getErrorMessage() != null) {
			mScriptResultField.append(response.getErrorMessage());
		}
	}

	static ReadCodeResponse manualRunCodeState(RunCodeResponse response, CodeStateContent content) {
		if (response == null) {
			return null;
		}
		return new ReadCodeResponse(
			response.getHost(),
			response.getContentType(),
			response.getCodeState(),
			response.getRunInitiator(),
			response.getStateToken(),
			content,
			response.getDiagnostics(),
			response.getErrorMessage(),
			response.getStdout(),
			response.getStructuredResult());
	}

	static ReadCodeResponse recordAttachedManualRunState(AiChatAttachment attachment, RunCodeResponse response, CodeStateContent content) {
		ReadCodeResponse codeState = manualRunCodeState(response, content);
		if (attachment != null && codeState != null) {
			attachment.recordCodeState(codeState);
		}
		return codeState;
	}

    static void requestAttachedManualRepair(AiChatAttachment attachment, ReadCodeResponse codeState) {
        if (attachment == null || codeState == null) {
            return;
        }
        attachment.requestRepair(new AiChatRepairRequest(ATTACHED_SCRIPT_FAILURE_PROMPT, codeState));
    }

    static void requestAttachedManualRepairIfConfirmed(AiChatAttachment attachment,
                                                       ReadCodeResponse codeState,
                                                       RunCodeResponse response,
                                                       ManualRepairConfirmation confirmation) {
        if (attachment == null || codeState == null || !isFailedManualRun(response) || confirmation == null) {
            return;
        }
        int answer = confirmation.confirmRepair(response);
        if (answer == JOptionPane.YES_OPTION) {
            requestAttachedManualRepair(attachment, codeState);
        }
    }

    private static boolean isFailedManualRun(RunCodeResponse response) {
        return response != null
            && response.getCodeState() != CodeState.RUN_SUCCEEDED
            && response.getErrorMessage() != null;
    }

    private ReadCodeResponse recordAiAttachmentAfterManualRun(RunCodeResponse response) {
        return recordAttachedManualRunState(aiChatAttachment, response, getCodeStateContent());
    }

    private void showManualRunFailure(RunCodeResponse response, ReadCodeResponse codeState) {
        if (response == null || response.getCodeState() == CodeState.RUN_SUCCEEDED || response.getErrorMessage() == null) {
            return;
        }
        if (aiChatAttachment != null && codeState != null) {
            requestAttachedManualRepairIfConfirmed(aiChatAttachment, codeState, response, runResponse ->
                JOptionPane.showConfirmDialog(
                    this,
                    buildManualRunFailureDialogMessage(runResponse),
                    "Freeplane",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE));
            return;
        }
        UITools.errorMessage(response.getErrorMessage());
    }

    interface ManualRepairConfirmation {
        int confirmRepair(RunCodeResponse response);
    }

	private Object buildManualRunFailureDialogMessage(RunCodeResponse response) {
		JTextArea messageArea = new JTextArea(response.getErrorMessage());
		messageArea.setEditable(false);
		messageArea.setLineWrap(false);
		messageArea.setWrapStyleWord(false);
		messageArea.setFont(mScriptTextField.getFont());
		messageArea.setCaretPosition(0);
		JScrollPane scrollPane = new JScrollPane(
			messageArea,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		return new Object[] { scrollPane, TextUtils.getText("ScriptEditorPanel.ask_for_ai_repair") };
	}

	private AiChatAttachmentService lookupAiChatAttachmentService() {
		BundleContext bundleContext = Activator.getBundleContext();
		if (bundleContext == null) {
			return null;
		}
		ServiceReference<AiChatAttachmentService> serviceReference =
			bundleContext.getServiceReference(AiChatAttachmentService.class);
		if (serviceReference == null) {
			return null;
		}
		return bundleContext.getService(serviceReference);
	}

	private Object toJsonSafeValue(Object value) {
		if (value == null || value instanceof Boolean || value instanceof Number || value instanceof String) {
			return value;
		}
		if (value instanceof java.util.Map<?, ?>) {
			java.util.Map<?, ?> source = (java.util.Map<?, ?>) value;
			java.util.Map<String, Object> converted = new java.util.LinkedHashMap<String, Object>();
			for (java.util.Map.Entry<?, ?> entry : source.entrySet()) {
				if (!(entry.getKey() instanceof String)) {
					throw unsupportedValue(value);
				}
				converted.put((String) entry.getKey(), toJsonSafeValue(entry.getValue()));
			}
			return converted;
		}
		if (value instanceof Iterable<?>) {
			java.util.List<Object> converted = new java.util.ArrayList<Object>();
			for (Object item : (Iterable<?>) value) {
				converted.add(toJsonSafeValue(item));
			}
			return converted;
		}
		if (value.getClass().isArray()) {
			java.util.List<Object> converted = new java.util.ArrayList<Object>();
			int length = java.lang.reflect.Array.getLength(value);
			for (int index = 0; index < length; index++) {
				converted.add(toJsonSafeValue(java.lang.reflect.Array.get(value, index)));
			}
			return converted;
		}
		throw unsupportedValue(value);
	}

	private IllegalStateException unsupportedValue(Object value) {
		return new IllegalStateException("Unsupported script result type: " + value.getClass().getName());
	}


	private String fingerprint(String text) {
        return CodeStateToken.fingerprint(text);
	}

	private void select(final int pIndex) {
		mScriptTextField.setEnabled(pIndex >= 0);
        mScriptInputField.setEnabled(pIndex >= 0);
		mRunAction.setEnabled(pIndex >= 0);
		mAttachToAiButton.setEnabled(pIndex >= 0);
		mSignAction.setEnabled(pIndex >= 0);
		if (pIndex < 0) {
			mScriptTextField.setText("");
            mScriptInputField.setText("");
			return;
		}
		storeCurrent();
        ScriptHolder scriptHolder = mScriptModel.getScript(pIndex);
		mScriptTextField.setText(scriptHolder.getScript());
        mScriptInputField.setText(scriptHolder.getArgumentsJsonText() == null ? "" : scriptHolder.getArgumentsJsonText());
		mLastSelected = pIndex;
		if (pIndex >= 0 && mScriptList.getSelectedIndex() != pIndex) {
			mScriptList.setSelectedIndex(pIndex);
		}
		mScriptTextField.requestFocusInWindow();
	}

	private void storeCurrent() {
		if (mLastSelected != null) {
			final int oldIndex = mLastSelected;
			ScriptHolder currentScript = mScriptModel.getScript(oldIndex);
			String newScript = mScriptTextField.getText();
			String newArgumentsJsonText = mScriptInputField.getText();
			boolean scriptChanged = !newScript.equals(currentScript.getScript());
			boolean argumentsChanged = !newArgumentsJsonText.equals(
					currentScript.getArgumentsJsonText() == null ? "" : currentScript.getArgumentsJsonText());
			if (scriptChanged || argumentsChanged) {
				currentScript.setScript(newScript)
						.setArgumentsJsonText(newArgumentsJsonText);
				mScriptModel.setScript(oldIndex, currentScript);
			}
		}
	}

	private void updateFields() {
		mListModel.clear();
		for (int i = 0; i < mScriptModel.getAmountOfScripts(); ++i) {
			final ScriptHolder script = mScriptModel.getScript(i);
			mListModel.addElement(script.getScriptName());
		}
	}
}
