package org.freeplane.plugin.ai.chat.ui;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.output.TokenUsage;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.html.HTMLEditorKit;
import org.freeplane.api.ai.AiRequestHandle;
import org.freeplane.api.ai.AiRequestResult;
import org.freeplane.api.ai.AiRequestStatus;
import org.freeplane.api.ai.AiSelectionOverride;
import org.freeplane.api.ai.AiToolAvailability;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.SetBooleanPropertyAction;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.LabelAndMnemonicSetter;
import org.freeplane.core.ui.components.JAutoCheckBoxMenuItem;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.components.html.ScaledEditorKit;
import org.freeplane.core.ui.textchanger.TranslatedElement;
import org.freeplane.core.ui.textchanger.TranslatedElementFactory;
import org.freeplane.core.util.MenuUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.ai.code.AiChatCodeOperationResult;
import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.AiCodeRunListener;
import org.freeplane.features.ai.code.CodeState;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.EvaluateFormulaRequest;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.RunCodeRequest;
import org.freeplane.features.ai.code.RunCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileChatMemory;
import org.freeplane.plugin.ai.chat.memory.AssistantProfileSwitchMessage;
import org.freeplane.plugin.ai.chat.memory.AutomaticCodeStatusMessage;
import org.freeplane.plugin.ai.chat.memory.ChatMemoryRenderEntry;
import org.freeplane.plugin.ai.chat.memory.ChatMemorySettings;
import org.freeplane.plugin.ai.chat.memory.ChatTokenCounterMode;
import org.freeplane.plugin.ai.chat.memory.ChatTokenCounterSettings;
import org.freeplane.plugin.ai.chat.memory.ChatTokenUsageTracker;
import org.freeplane.plugin.ai.chat.memory.ChatUsageTotals;
import org.freeplane.plugin.ai.chat.memory.GeneralSystemMessage;
import org.freeplane.plugin.ai.chat.memory.PromptReferenceUserMessage;
import org.freeplane.plugin.ai.chat.profile.AssistantProfilePaneBuilder;
import org.freeplane.plugin.ai.chat.profile.AssistantProfileSelectionModel;
import org.freeplane.plugin.ai.chat.profile.AssistantProfileSelectionSync;
import org.freeplane.plugin.ai.chat.profile.AssistantProfileSelectionSync.ProfileRequestResolution;
import org.freeplane.plugin.ai.chat.request.AIChatService;
import org.freeplane.plugin.ai.chat.request.AIChatServiceFactory;
import org.freeplane.plugin.ai.chat.request.AddToChatDispatchJobFactory;
import org.freeplane.plugin.ai.chat.request.AiRequestConfigurationResolver;
import org.freeplane.plugin.ai.chat.request.AiRequestExecutionCoordinator;
import org.freeplane.plugin.ai.chat.request.AiRequestHandleImpl;
import org.freeplane.plugin.ai.chat.request.AiRequestMappings;
import org.freeplane.plugin.ai.chat.request.AiRequestStatusMapper;
import org.freeplane.plugin.ai.chat.request.AiRequestTimeoutController;
import org.freeplane.plugin.ai.chat.request.AiRequestTimeoutControllerFactory;
import org.freeplane.plugin.ai.chat.request.AiSelectionOverrideResolver;
import org.freeplane.plugin.ai.chat.request.ChatPromptRunner;
import org.freeplane.plugin.ai.chat.request.ChatPromptRunnerFactory;
import org.freeplane.plugin.ai.chat.request.ChatRequestFlow;
import org.freeplane.plugin.ai.chat.request.ChatRequestFlowFactory;
import org.freeplane.plugin.ai.chat.request.HiddenAiRequestObserverBridge;
import org.freeplane.plugin.ai.chat.request.HiddenAiRequestObserverFactory;
import org.freeplane.plugin.ai.chat.request.HiddenPromptRequestRunner;
import org.freeplane.plugin.ai.chat.request.HiddenPromptRequestRunnerFactory;
import org.freeplane.plugin.ai.chat.request.PromptToolSelectionResolver;
import org.freeplane.plugin.ai.chat.request.ResolvedAiRequest;
import org.freeplane.plugin.ai.chat.request.RequestVisibility;
import org.freeplane.plugin.ai.chat.request.SystemInstructionComposer;
import org.freeplane.plugin.ai.chat.request.SystemInstructionContext;
import org.freeplane.plugin.ai.chat.request.VisibleAiRequestCallbacksFactory;
import org.freeplane.plugin.ai.chat.session.LiveChatController;
import org.freeplane.plugin.ai.chat.session.LiveChatSessionId;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevel;
import org.freeplane.plugin.ai.tools.availability.ToolAvailabilityLevelSettings;
import org.freeplane.plugin.ai.tools.code.AiCodeOperationAuthorizer;
import org.freeplane.plugin.ai.tools.code.AiCodeToolSet;
import org.freeplane.plugin.ai.tools.formula.FormulaEditingSettings;
import org.freeplane.plugin.ai.edits.AiEditsSettings;
import org.freeplane.plugin.ai.edits.ClearAiMarkersInMapAction;
import org.freeplane.plugin.ai.edits.ClearAiMarkersInSelectionAction;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.maps.ControllerMapModelProvider;
import org.freeplane.plugin.ai.model.AIChatModelFactory;
import org.freeplane.plugin.ai.model.AIModelCatalog;
import org.freeplane.plugin.ai.model.AIModelSelection;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.freeplane.plugin.ai.prompt.AiPromptActionRegistry;
import org.freeplane.plugin.ai.prompt.AiPromptProgressDialogFactory;
import org.freeplane.plugin.ai.prompt.AiPromptRequestComposer;
import org.freeplane.plugin.ai.tools.AIToolSetBuilder;
import org.freeplane.plugin.ai.tools.MessageBuilder;
import org.freeplane.plugin.ai.tools.selection.SelectionIdentifiersResponse;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummary;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AIChatPanel extends JPanel {
    private static final int TOP_BAR_HORIZONTAL_GAP = 2;
    private static final String AI_TAB_ICON_RESOURCE = "/images/panelTabs/aiTab.svg?useAccentColor=true";
    private static final String AI_TAB_ATTACHED_ICON_RESOURCE = "/images/panelTabs/aiTabAttached.svg?useAccentColor=true";

    /**
	 * Comment for <code>serialVersionUID</code>
	 */
	private static final long serialVersionUID = 1L;
    private final JEditorPane messageHistoryPane;
    private final HTMLEditorKit messageHistoryEditorKit;
    private final JScrollPane scrollPane;
    private final JTextArea inputArea;
    private final JButton undoButton;
    private final JButton redoButton;
    private final JButton sendButton;
    private final Icon sendIcon;
    private final Icon stopIcon;
    private final Icon preferencesIcon;
    private final Icon assistantProfileIcon;
    private final Icon aiTabIcon;
    private final Icon attachedAiTabIcon;
    private String sendTooltipText;
    private String cancelTooltipText;
    private String undoTooltipText;
    private String redoTooltipText;
    private String preferencesTooltipText;
    private String noProviderConfiguredText;
    private final JPopupMenu menuPopup;
    private final AIProviderConfiguration configuration;
    private final ToolAvailabilityLevelSettings chatToolAvailabilitySettings;
    private final ChatDisplaySettings chatDisplaySettings;
    private final ChatModelSelector modelSelectionController;
    private final PromptToolSelectionResolver promptToolSelectionResolver;
    private final ToolAvailabilityLevelMenu chatToolAvailabilityMenu;
    private final ChatOutputView chatOutputView;
    private final ChatInputControls chatInputControls;
    private final AiPromptRequestComposer aiPromptRequestComposer;
    private final PromptReferenceResolver promptReferenceResolver;
    private final SlashPromptCompletionController slashPromptCompletionController;
    private AiPromptActionRegistry promptActionRegistry;
    private final AiRequestConfigurationResolver aiRequestConfigurationResolver;
    private final AiSelectionOverrideResolver aiSelectionOverrideResolver;
    private ChatMemory chatMemory;
    private final ChatTokenUsageTracker chatTokenUsageTracker;
    private final JLabel tokenUsageLabel;
    private final ChatMessageHistory messageHistory;
    private final AvailableMaps availableMaps;
    private final DateTimeFormatter chatNameFormatter;
    private final LiveChatController liveChatController;
    private final ChatRequestFlowFactory chatRequestFlowFactory;
    private final ChatPromptRunnerFactory chatPromptRunnerFactory;
    private final VisibleAiRequestCallbacksFactory visibleAiRequestCallbacksFactory;
    private final HiddenAiRequestObserverFactory hiddenAiRequestObserverFactory;
    private final HiddenPromptRequestRunnerFactory hiddenPromptRequestRunnerFactory;
    private final AiPromptProgressDialogFactory aiPromptProgressDialogFactory;
    private final AiRequestTimeoutControllerFactory aiRequestTimeoutControllerFactory;
    private final AddToChatDispatchJobFactory addToChatDispatchJobFactory;
    private final AiRequestExecutionCoordinator aiRequestExecutionCoordinator;
    private final Map<LiveChatSessionId, ChatRequestFlow> activeVisibleRequestFlows =
        new HashMap<LiveChatSessionId, ChatRequestFlow>();
    private final Map<LiveChatSessionId, ChatTokenUsageTracker> activeVisibleRequestTrackers =
        new HashMap<LiveChatSessionId, ChatTokenUsageTracker>();
    private final AssistantProfileSelectionSync assistantProfileSelectionSync;
    private final AssistantProfilePaneBuilder assistantProfilePaneBuilder;
    private LiveChatSessionId pendingAiOwnedUserRunFollowupSessionId;
    private long visibleHistoryRebuildCounter;
    private final AiCodeRunListener aiCodeRunListener = new AiCodeRunListener() {
        @Override
        public void runFinished(RunCodeResponse response) {
            handleCodeRunFinished(response);
        }
    };
    private boolean currentSessionUsesAssistantProfile = true;
    private boolean showInstructionMessages;
    private boolean showNextRequestInstructionPreview;
    private final SystemInstructionComposer systemInstructionComposer = new SystemInstructionComposer();
    private NextRequestInstructionPreviewView nextRequestInstructionPreviewView;
    private AiCodeHostService codeHostService;

    public AIChatPanel() {
        setLayout(new BorderLayout());
        messageHistoryPane = new JEditorPane();
        messageHistoryPane.setContentType("text/html");
        messageHistoryEditorKit = ScaledEditorKit.create();
        messageHistoryPane.setEditorKit(messageHistoryEditorKit);
        messageHistoryPane.setEditable(false);
        messageHistoryPane.setOpaque(true);
        messageHistoryPane.setBackground(Color.WHITE);
        inputArea = new JTextArea(3, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        nextRequestInstructionPreviewView = new NextRequestInstructionPreviewView(new ChatMessageRenderer());
        applyChatMessageStyles();
        resetMessageHistory();
        messageHistory = new ChatMessageHistory(messageHistoryPane, messageHistoryEditorKit);
        messageHistoryPane.setTransferHandler(new ChatMessageTransferHandler(messageHistoryPane, messageHistory));
        messageHistoryPane.setDragEnabled(true);
        messageHistoryPane.addHyperlinkListener(
            new ChatHistoryHyperlinkHandler(
                ChatHistoryHyperlinkHandler.defaultLinkControllerAdapter()).createListener());
        configureEmptyHistoryFocusTransfer();
        scrollPane = new JScrollPane(messageHistoryPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        undoButton = new JButton("\u21B6");
        redoButton = new JButton("\u21B7");
        sendButton = new JButton();
        sendButton.setIcon(ResourceController.getResourceController()
            .getImageIcon("/images/ai_send_arrow_up.svg?useAccentColor=true"));
        sendIcon = sendButton.getIcon();
        stopIcon = ResourceController.getResourceController()
            .getImageIcon("/images/ai_stop.svg?useAccentColor=true");
        preferencesIcon = ResourceController.getResourceController()
            .getImageIcon("/images/generic_settings.svg?useAccentColor=true");
        assistantProfileIcon = ResourceController.getResourceController()
            .getImageIcon("/images/EggheadCB.svg?useAccentColor=true");
        aiTabIcon = ResourceController.getResourceController().getImageIcon(AI_TAB_ICON_RESOURCE);
        attachedAiTabIcon = ResourceController.getResourceController().getImageIcon(AI_TAB_ATTACHED_ICON_RESOURCE);
        Dimension sendButtonSize = sendButton.getPreferredSize();
        Dimension sideButtonSize = new Dimension(sendButtonSize.width, Math.max(1, sendButtonSize.height / 2));
        Dimension tallSendButtonSize = new Dimension(sendButtonSize.width, sideButtonSize.height * 2);
        sendButton.setPreferredSize(tallSendButtonSize);
        sendButton.setMinimumSize(tallSendButtonSize);
        sendButton.setMaximumSize(tallSendButtonSize);
        undoButton.setPreferredSize(sideButtonSize);
        undoButton.setMinimumSize(sideButtonSize);
        undoButton.setMaximumSize(sideButtonSize);
        redoButton.setPreferredSize(sideButtonSize);
        redoButton.setMinimumSize(sideButtonSize);
        redoButton.setMaximumSize(sideButtonSize);
        chatToolAvailabilitySettings = new ToolAvailabilityLevelSettings();
        configuration = new AIProviderConfiguration();
        aiRequestConfigurationResolver = new AiRequestConfigurationResolver(configuration);
        chatDisplaySettings = new ChatDisplaySettings();
        modelSelectionController = new ChatModelSelector(configuration, new AIModelCatalog(configuration));
        modelSelectionController.setModelSelectionChangeListener(modelDescriptor -> {
        });
        AssistantProfileSelectionModel assistantProfileSelectionModel = new AssistantProfileSelectionModel();
        chatMemory = createChatMemory();
        tokenUsageLabel = new JLabel();
        tokenUsageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
        chatTokenUsageTracker = new ChatTokenUsageTracker(this::updateTokenUsageLabel);
        availableMaps = new AvailableMaps(new ControllerMapModelProvider());
        TextController textController = requireTextController();
        aiPromptRequestComposer = new AiPromptRequestComposer(availableMaps, textController);
        promptReferenceResolver = new PromptReferenceResolver();
        slashPromptCompletionController = new SlashPromptCompletionController(
            inputArea,
            () -> promptActionRegistry == null
                ? Collections.<AiPrompt>emptyList()
                : promptActionRegistry.prompts(),
            promptReferenceResolver,
            this::refreshInstructionPreview);
        slashPromptCompletionController.install();
        aiSelectionOverrideResolver = new AiSelectionOverrideResolver(availableMaps, textController);
        chatNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        liveChatController = new LiveChatController(
            this,
            availableMaps,
            requireTextController(),
            chatNameFormatter,
            this::syncUiToActivatedSession,
            chatTokenUsageTracker::snapshotState
        );
        promptToolSelectionResolver = new PromptToolSelectionResolver(chatToolAvailabilitySettings);
        chatToolAvailabilityMenu = new ToolAvailabilityLevelMenu(
            this::currentEffectiveToolAvailability,
            this::applyUserSelectedToolAvailability);
        chatOutputView = new ChatOutputView(messageHistory, liveChatController, tokenUsageLabel);
        modelSelectionController.setExplicitUserModelSelectionChangeListener(modelDescriptor ->
            liveChatController.clearCurrentSessionSelectedModelOverride());
        assistantProfileSelectionSync = new AssistantProfileSelectionSync(
            assistantProfileSelectionModel,
            liveChatController);
        assistantProfileSelectionSync.setChatMemory(chatMemory);
        assistantProfileSelectionSync.setProfileMessageConsumer(this::appendProfileMessage);
        assistantProfileSelectionSync.setPreviewRefreshListener(this::refreshInstructionPreview);
        assistantProfilePaneBuilder = new AssistantProfilePaneBuilder(
            assistantProfileSelectionModel,
            assistantProfileSelectionSync,
            assistantProfileIcon);
        menuPopup = buildMenuPopup();
        messageHistoryPane.setComponentPopupMenu(menuPopup);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        JPanel actionButtonsPanel = new JPanel(new BorderLayout(4, 0));
        JPanel undoRedoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        undoRedoPanel.add(undoButton);
        undoRedoPanel.add(redoButton);
        actionButtonsPanel.add(sendButton, BorderLayout.WEST);
        actionButtonsPanel.add(undoRedoPanel, BorderLayout.EAST);
        inputPanel.add(actionButtonsPanel, BorderLayout.EAST);
        JPanel inputContainer = new JPanel(new BorderLayout());
        inputContainer.add(assistantProfilePaneBuilder.buildPanel(), BorderLayout.NORTH);
        JPanel inputAndPreviewPanel = new JPanel(new BorderLayout());
        inputAndPreviewPanel.add(nextRequestInstructionPreviewView, BorderLayout.NORTH);
        inputAndPreviewPanel.add(inputPanel, BorderLayout.CENTER);
        inputContainer.add(inputAndPreviewPanel, BorderLayout.CENTER);
        JPanel tokenUsagePanel = new JPanel(new BorderLayout());
        tokenUsagePanel.add(tokenUsageLabel, BorderLayout.EAST);
        inputContainer.add(tokenUsagePanel, BorderLayout.SOUTH);

        JPanel topBarContainer = buildTopBarPanel();

        add(scrollPane, BorderLayout.CENTER);
        add(inputContainer, BorderLayout.SOUTH);
        add(topBarContainer, BorderLayout.NORTH);

        sendButton.addActionListener(event -> {
            if (isRequestActive()) {
                cancelActiveRequest();
            } else if (!isProviderConfigured()) {
                openPreferences();
            } else {
                sendMessage();
            }
        });
        undoButton.addActionListener(event -> undoLastTurn());
        redoButton.addActionListener(event -> redoLastTurn());
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke sendKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, shortcutMask);
        KeyStroke undoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_UP, shortcutMask);
        KeyStroke redoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, shortcutMask);
        sendTooltipText = TextUtils.format("ai_chat_send.tooltip", MenuUtils.formatKeyStroke(sendKeyStroke));
        KeyStroke cancelKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        cancelTooltipText = TextUtils.format("ai_chat_cancel.tooltip", MenuUtils.formatKeyStroke(cancelKeyStroke));
        undoTooltipText = TextUtils.getText("simplyhtml.undoLabel")
            + " (" + MenuUtils.formatKeyStroke(undoKeyStroke) + ")";
        redoTooltipText = TextUtils.getText("simplyhtml.redoLabel")
            + " (" + MenuUtils.formatKeyStroke(redoKeyStroke) + ")";
        preferencesTooltipText = TextUtils.getText("preferences");
        noProviderConfiguredText = TextUtils.getText("ai_chat_no_provider_configured");
        sendButton.setToolTipText(sendTooltipText);
        undoButton.setToolTipText(undoTooltipText);
        redoButton.setToolTipText(redoTooltipText);
        messageHistoryPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutMask), "selectAllMessages");
        messageHistoryPane.getActionMap().put("selectAllMessages", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                messageHistoryPane.selectAll();
            }
        });
        inputArea.getInputMap().put(sendKeyStroke, "sendMessage");
        inputArea.getInputMap().put(undoKeyStroke, "undoTurn");
        inputArea.getInputMap().put(redoKeyStroke, "redoTurn");
        inputArea.getActionMap().put("sendMessage", new AbstractAction() {
            /**
			 * Comment for <code>serialVersionUID</code>
			 */
			private static final long serialVersionUID = 1L;

			@Override
            public void actionPerformed(ActionEvent event) {
                if (isRequestActive()) {
                    cancelActiveRequest();
                } else if (!isProviderConfigured()) {
                    return;
                } else {
                    sendMessage();
                }
            }
        });
        inputContainer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(cancelKeyStroke, "cancelRequest");
        inputContainer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(undoKeyStroke, "undoTurn");
        inputContainer.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(redoKeyStroke, "redoTurn");
        inputContainer.getActionMap().put("cancelRequest", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                cancelActiveRequest();
            }
        });
        inputContainer.getActionMap().put("undoTurn", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                undoLastTurn();
            }
        });
        inputContainer.getActionMap().put("redoTurn", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                redoLastTurn();
            }
        });
        chatInputControls = new ChatInputControls(
            inputArea,
            sendButton,
            sendIcon,
            stopIcon,
            preferencesIcon,
            sendTooltipText,
            cancelTooltipText,
            preferencesTooltipText,
            noProviderConfiguredText,
            this::updateUndoRedoButtonState);
        chatRequestFlowFactory = new ChatRequestFlowFactory();
        visibleAiRequestCallbacksFactory = new VisibleAiRequestCallbacksFactory();
        hiddenAiRequestObserverFactory = new HiddenAiRequestObserverFactory();
        hiddenPromptRequestRunnerFactory = new HiddenPromptRequestRunnerFactory();
        aiPromptProgressDialogFactory = new AiPromptProgressDialogFactory();
        aiRequestTimeoutControllerFactory = new AiRequestTimeoutControllerFactory();
        addToChatDispatchJobFactory = new AddToChatDispatchJobFactory(this);
        chatPromptRunnerFactory = new ChatPromptRunnerFactory(
            aiTabIcon,
            stopIcon,
            cancelTooltipText,
            availableMaps,
            aiPromptRequestComposer,
            this::openPromptChat,
            this::currentCodeHostService,
            this::sessionAwareCodeHostService,
            chatToolAvailabilitySettings::getToolAvailability,
            liveChatController::sessionToolAvailabilityOverride,
            hiddenPromptRequestRunnerFactory,
            aiPromptProgressDialogFactory);
        aiRequestExecutionCoordinator = new AiRequestExecutionCoordinator(
            this,
            addToChatDispatchJobFactory,
            aiRequestTimeoutControllerFactory);
        assistantProfilePaneBuilder.initialize();
        liveChatController.initialize(chatMemory);
        modelSelectionController.loadInitialModelSelectionList();
        registerProviderConfigurationListener();
        registerModelSelectionRefreshListener();
        registerTokenCounterModeListener();
        registerChatMemoryMaximumTokenCountListener();
        registerChatFontScalingListener();
        registerInstructionPreviewInputListener();
        refreshTokenCounterMode();
        updateInputState();
    }

    private JPanel buildTopBarPanel() {
        JPanel topBar = new JPanel(new BorderLayout(TOP_BAR_HORIZONTAL_GAP, 0));
        JButton menuButton = new JButton("\u2261");
        TranslatedElementFactory.createTooltip(menuButton, "preferences");
        menuButton.addActionListener(event -> menuPopup.show(menuButton, 0, menuButton.getHeight()));
        topBar.add(menuButton, BorderLayout.WEST);
        topBar.add(modelSelectionController.getModelSelectionComboBox(), BorderLayout.CENTER);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, TOP_BAR_HORIZONTAL_GAP, 0));
        String historyIconPath = "/images/ai_history.svg?useAccentColor=true";
        JButton chatsButton = TranslatedElementFactory.createButtonWithIcon(historyIconPath, "ai_chat_chats");
        chatsButton.addActionListener(event -> {
            cancelActiveRequest();
            liveChatController.openLiveChats();
        });
        rightButtons.add(chatsButton);
        String clearIconPath = "/images/ai_new_chat.svg?useAccentColor=true";
        JButton newChatButton = TranslatedElementFactory.createButtonWithIcon(clearIconPath, "ai_chat_new_chat");
        newChatButton.addActionListener(event -> {
            cancelActiveRequest();
            liveChatController.startNewChat();
        });
        rightButtons.add(newChatButton);
        topBar.add(rightButtons, BorderLayout.EAST);
        return topBar;
    }

    private JPopupMenu buildMenuPopup() {
        JPopupMenu menuPopup = new JPopupMenu();
        Action openPreferencesAction = new AbstractAction("Preferences") {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                openPreferences();
            }
        };
        JMenuItem preferencesMenuItem = TranslatedElementFactory.createMenuItem(openPreferencesAction, "preferences");
        preferencesMenuItem.setIcon(preferencesIcon);
        menuPopup.add(preferencesMenuItem);
        addToolAvailabilityLevelMenu(menuPopup);
        Action manageProfilesAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                assistantProfilePaneBuilder.openAssistantProfileManager();
            }
        };
        JMenuItem manageProfilesMenuItem = TranslatedElementFactory.createMenuItem(
            manageProfilesAction,
            AssistantProfilePaneBuilder.MANAGE_PROFILES_TEXT_KEY);
        manageProfilesMenuItem.setIcon(assistantProfileIcon);
        menuPopup.add(manageProfilesMenuItem);
        Action reopenAiOwnedScriptAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                reopenAiOwnedCode();
            }
        };
        JMenuItem reopenAiOwnedScriptMenuItem = TranslatedElementFactory.createMenuItem(
            reopenAiOwnedScriptAction,
            "ai_chat_reopen_ai_owned_script");
        reopenAiOwnedScriptMenuItem.setEnabled(false);
        menuPopup.add(reopenAiOwnedScriptMenuItem);
        Action copyMarkdownAction = new ChatMarkdownCopyAction(messageHistoryPane, messageHistory);
        JMenuItem copyMarkdownMenuItem = TranslatedElementFactory.createMenuItem(
            copyMarkdownAction,
            "ai_chat_copy_markdown");
        menuPopup.add(copyMarkdownMenuItem);
        JCheckBoxMenuItem showInstructionMessagesItem = new JCheckBoxMenuItem("Show instruction history");
        showInstructionMessagesItem.setToolTipText("Show full committed system and profile messages in the chat");
        showInstructionMessagesItem.addActionListener(event -> {
            showInstructionMessages = showInstructionMessagesItem.isSelected();
            chatOutputView.setInstructionHistoryRenderingMode(showInstructionMessages
                ? InstructionHistoryRenderingMode.FULL
                : InstructionHistoryRenderingMode.BRIEF);
            rebuildHistoryFromMemory();
        });
        menuPopup.add(showInstructionMessagesItem);
        JCheckBoxMenuItem previewInstructionMessagesItem = new JCheckBoxMenuItem("Preview next request instructions");
        previewInstructionMessagesItem.setToolTipText("Show the system and profile messages for the next chat request");
        previewInstructionMessagesItem.addActionListener(event -> {
            showNextRequestInstructionPreview = previewInstructionMessagesItem.isSelected();
            refreshInstructionPreview();
        });
        menuPopup.add(previewInstructionMessagesItem);
        addAiEditsMenuItems(menuPopup);
        menuPopup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                updateToolAvailabilityMenuSelection();
                showInstructionMessagesItem.setSelected(showInstructionMessages);
                previewInstructionMessagesItem.setSelected(showNextRequestInstructionPreview);
                reopenAiOwnedScriptMenuItem.setEnabled(canReopenAiOwnedCode());
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
        return menuPopup;
    }

    private void addToolAvailabilityLevelMenu(JPopupMenu menuPopup) {
        chatToolAvailabilityMenu.addTo(menuPopup);
    }

    private void addAiEditsMenuItems(JPopupMenu menuPopup) {
        if (Controller.getCurrentModeController() == null) {
            return;
        }
        AFreeplaneAction clearMapAction = Controller.getCurrentModeController()
            .getAction(ClearAiMarkersInMapAction.ACTION_KEY);
        AFreeplaneAction clearSelectionAction = Controller.getCurrentModeController()
            .getAction(ClearAiMarkersInSelectionAction.ACTION_KEY);
        AFreeplaneAction showIconAction = Controller.getCurrentModeController()
            .getAction(SetBooleanPropertyAction.actionKey(AiEditsSettings.AI_EDITS_STATE_ICON_VISIBLE_PROPERTY));
        if (clearMapAction == null && clearSelectionAction == null && showIconAction == null) {
            return;
        }
        menuPopup.addSeparator();
        addMenuItem(menuPopup, clearMapAction);
        addMenuItem(menuPopup, clearSelectionAction);
        addToggleMenuItem(menuPopup, showIconAction);
    }

    private void addMenuItem(JPopupMenu menuPopup, AFreeplaneAction action) {
        if (action == null) {
            return;
        }
        menuPopup.add(TranslatedElementFactory.createMenuItem(action, action.getTextKey()));
    }

    private void addToggleMenuItem(JPopupMenu menuPopup, AFreeplaneAction action) {
        if (action == null) {
            return;
        }
        String labelKey = action.getTextKey();
        JCheckBoxMenuItem menuItem = new JAutoCheckBoxMenuItem(action);
        LabelAndMnemonicSetter.setLabelAndMnemonic(menuItem, TextUtils.getRawText(labelKey));
        TranslatedElement.TEXT.setKey(menuItem, labelKey);
        TranslatedElementFactory.createTooltip(menuItem, action.getTooltipKey());
        menuPopup.add(menuItem);
    }

    private void openPreferences() {
        Controller controller = Controller.getCurrentController();
        MModeController modeController = (MModeController) controller.getModeController(MModeController.MODENAME);
        modeController.showPreferences("plugins", "ai");
    }

    private void registerModelSelectionRefreshListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!isModelSelectionRefreshProperty(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> modelSelectionController.loadInitialModelSelectionList());
                }
            });
    }

    private void registerProviderConfigurationListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!isProviderConfigurationProperty(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> updateInputState());
                }
            });
    }

    private void registerTokenCounterModeListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!ChatTokenCounterSettings.CHAT_TOKEN_COUNTER_MODE_PROPERTY.equals(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> refreshTokenCounterMode());
                }
            });
    }

    private void registerChatMemoryMaximumTokenCountListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!ChatMemorySettings.CHAT_MEMORY_MAXIMUM_TOKEN_COUNT_PROPERTY.equals(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> refreshChatMemoryMaximumTokenCount());
                }
            });
    }

    private void registerChatFontScalingListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!AIChatMessageStyleSettings.CHAT_FONT_SCALING_PROPERTY.equals(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> refreshChatMessageStyles());
                }
            });
    }

    private void registerInstructionPreviewInputListener() {
        ResourceController.getResourceController().addPropertyChangeListener(
            new IFreeplanePropertyListener() {
                @Override
                public void propertyChanged(String propertyName, String newValue, String oldValue) {
                    if (!MessageBuilder.SYSTEM_MESSAGE_PROPERTY.equals(propertyName)
                        && !ToolAvailabilityLevelSettings.TOOL_AVAILABILITY_PROPERTY.equals(propertyName)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> refreshInstructionPreview());
                }
            });
    }

    private void refreshChatMessageStyles() {
        applyChatMessageStyles();
        rebuildHistoryFromMemory();
    }

    private void applyChatMessageStyles() {
        AIChatMessageStyleSettings aiChatMessageStyleSettings = new AIChatMessageStyleSettings();
        Font font = inputArea.getFont();
		float baseFontSize = font != null ? font.getSize2D() / UITools.FONT_SCALE_FACTOR : 10;
		new ChatMessageStyleApplier().apply(
            messageHistoryPane,
            messageHistoryEditorKit,
            baseFontSize,
            aiChatMessageStyleSettings.getChatFontScaling());
        if (nextRequestInstructionPreviewView != null) {
            nextRequestInstructionPreviewView.applyStyles(
                baseFontSize,
                aiChatMessageStyleSettings.getChatFontScaling());
        }
    }

    private boolean isModelSelectionRefreshProperty(String propertyName) {
        return "ai_openrouter_model_allowlist".equals(propertyName)
            || "ai_gemini_model_list".equals(propertyName)
            || "ai_ollama_model_allowlist".equals(propertyName)
            || "ai_provider_name".equals(propertyName)
            || "ai_model_name".equals(propertyName)
            || "ai_selected_model".equals(propertyName)
            || "ai_openrouter_key".equals(propertyName)
            || "ai_openrouter_service_address".equals(propertyName)
            || "ai_gemini_key".equals(propertyName)
            || "ai_gemini_service_address".equals(propertyName)
            || "ai_ollama_api_key".equals(propertyName)
            || "ai_ollama_service_address".equals(propertyName);
    }

    private boolean isProviderConfigurationProperty(String propertyName) {
        return "ai_openrouter_key".equals(propertyName)
            || "ai_gemini_key".equals(propertyName)
            || "ai_ollama_service_address".equals(propertyName);
    }

    private void sendMessage() {
        String userMessage = inputArea.getText().trim();
        if (userMessage.isEmpty()) {
            return;
        }
        LiveChatSessionId sessionId = liveChatController.currentSessionId();
        if (sessionId == null) {
            return;
        }
        ChatTokenUsageTracker requestTokenUsageTracker = createRequestTokenUsageTracker(sessionId);
        ChatRequestFlow requestFlow = createVisibleRequestFlow(sessionId, requestTokenUsageTracker, null);
        AIChatService requestService = createVisibleRequestService(sessionId, requestFlow, requestTokenUsageTracker);
        if (requestService == null) {
            notifyVisibleConfigurationError(sessionId);
            return;
        }
        PromptReferenceMatch promptReferenceMatch = resolvePromptReference(userMessage);
        startVisibleRequest(sessionId, userMessage, requestService, requestFlow, requestTokenUsageTracker,
            true, true, promptReferenceMatch);
    }

    public void setPromptActionRegistry(AiPromptActionRegistry promptActionRegistry) {
        this.promptActionRegistry = promptActionRegistry;
        refreshPromptCompletion();
    }

    public void refreshPromptCompletion() {
        if (slashPromptCompletionController != null) {
            slashPromptCompletionController.refreshFromPromptListChange();
        }
    }

    public void runPrompt(AiPrompt prompt) {
        runPrompt(prompt, null);
    }

    public void runPrompt(AiPrompt prompt, Component owner) {
        if (prompt == null) {
            return;
        }
        String selectedModelOverride = normalizeSelectionValue(prompt.getModelSelectionValue());
        ToolAvailabilityLevel resolvedToolAvailability =
            promptToolSelectionResolver.resolveEffectiveToolAvailability(prompt.getToolAvailabilitySelectionValue());
        ToolAvailabilityLevel toolAvailabilityOverride =
            promptToolSelectionResolver.resolveShownChatOverride(prompt.getToolAvailabilitySelectionValue());
        AiRequestConfigurationResolver.Issue configurationIssue =
            aiRequestConfigurationResolver.resolve(selectedModelOverride);
        if (configurationIssue != null) {
            notifyUser(configurationIssue.getDetail(), true);
            return;
        }
        if (prompt.isShowInChat()) {
            ChatMemory promptChatMemory = createChatMemory();
            LiveChatSessionId sessionId = liveChatController.startNewPromptChat(
                promptChatMemory,
                promptDisplayName(prompt.getName()),
                selectedModelOverride,
                toolAvailabilityOverride);
            ChatTokenUsageTracker requestTokenUsageTracker = createRequestTokenUsageTracker(sessionId);
            ChatRequestFlow requestFlow = createVisibleRequestFlow(sessionId, requestTokenUsageTracker, null);
            ChatPromptRunner chatPromptRunner = chatPromptRunnerFactory.createShown(
                promptChatMemory,
                liveChatController.mapAccessListener(sessionId),
                requestFlow,
                requestTokenUsageTracker,
                sessionId);
            if (!chatPromptRunner.startShownPrompt(
                    prompt.getPrompt(),
                    selectedModelOverride,
                    resolvedToolAvailability,
                    null,
                    null)) {
                notifyVisibleConfigurationError(sessionId);
            }
            return;
        }
        ChatPromptRunner chatPromptRunner =
            chatPromptRunnerFactory.createHidden(liveChatController.currentSessionId());
        if (!chatPromptRunner.submitHiddenRequest(
                prompt.getName(),
                prompt.getPrompt(),
                selectedModelOverride,
                resolvedToolAvailability,
                null,
                owner,
                true,
                null,
                null,
                false,
                true,
                null)) {
            notifyUser(configurationErrorMessage(selectedModelOverride), true);
        }
    }

    public AiRequestHandle askAi(ResolvedAiRequest request, AiRequestHandleImpl handle) {
        return aiRequestExecutionCoordinator.askAi(request, handle);
    }

    public void startShownAiRequest(ResolvedAiRequest request,
                             AiRequestHandleImpl handle,
                             AiRequestTimeoutController timeoutController) {
        if (handle.isDone()) {
            return;
        }
        String selectedModelOverride = AiRequestMappings.toSelectedModelOverride(request.getModelSelection());
        AiRequestConfigurationResolver.Issue configurationIssue =
            aiRequestConfigurationResolver.resolve(selectedModelOverride);
        if (configurationIssue != null) {
            handle.complete(new AiRequestResult(configurationIssue.getStatus(), null,
                configurationIssue.getDetail()));
            return;
        }
        ProfileRequestResolution profileResolution = resolveRequestProfile(request);
        if (profileResolution.getConfigurationErrorDetail() != null) {
            handle.complete(new AiRequestResult(AiRequestStatus.CONFIGURATION_ERROR, null,
                profileResolution.getConfigurationErrorDetail()));
            return;
        }
        ToolAvailabilityLevel resolvedToolAvailability =
            resolvePromptStyleToolAvailability(request.getToolAvailability());
        ToolAvailabilityLevel toolAvailabilityOverride =
            explicitToolAvailabilityOverride(request.getToolAvailability());
        ChatMemory promptChatMemory = createChatMemory(
            capturedSystemMessageFor(request),
            isSystemMessageExactFor(request));
        LiveChatSessionId sessionId = liveChatController.startNewPromptChat(
            promptChatMemory,
            promptDisplayName(request.getPromptDisplayName()),
            selectedModelOverride,
            toolAvailabilityOverride);
        ChatTokenUsageTracker requestTokenUsageTracker = createRequestTokenUsageTracker(sessionId);
        ChatPromptRunner.VisiblePromptRequestCallbacks requestCallbacks =
            visibleAiRequestCallbacksFactory.create(handle);
        ChatRequestFlow requestFlow = createVisibleRequestFlow(
            sessionId,
            requestTokenUsageTracker,
            requestCallbacks);
        handle.setCancelAction(new Runnable() {
            @Override
            public void run() {
                if (requestFlow.isRequestActive()) {
                    requestFlow.cancelActiveRequest();
                }
                else {
                    completeCancelledRequest(handle);
                }
            }
        });
        if (handle.isDone()) {
            return;
        }
        ChatPromptRunner chatPromptRunner = chatPromptRunnerFactory.createShown(
            promptChatMemory,
            liveChatController.mapAccessListener(sessionId),
            requestFlow,
            requestTokenUsageTracker,
            sessionId);
        boolean started = chatPromptRunner.startShownPrompt(
            request.getPromptText(),
            selectedModelOverride,
            resolvedToolAvailability,
            resolveSelectionOverride(request.getSelectionOverride()),
            requestCallbacks,
            profileResolution.getMessage());
        if (!started) {
            handle.complete(configurationErrorOrFailedResult(selectedModelOverride, null));
            return;
        }
        timeoutController.armAfterStart();
    }

    public ChatRequestFlow startAddToChatAiRequestAtDispatch(ResolvedAiRequest request, AiRequestHandleImpl handle) {
        if (handle.isDone()) {
            return null;
        }
        ProfileRequestResolution profileResolution = resolveRequestProfile(request);
        if (profileResolution.getConfigurationErrorDetail() != null) {
            handle.complete(new AiRequestResult(AiRequestStatus.CONFIGURATION_ERROR, null,
                profileResolution.getConfigurationErrorDetail()));
            return null;
        }
        LiveChatSessionId selectedSessionId = isChatTabSelected()
            ? liveChatController.currentSessionId()
            : null;
        boolean appendToSelectedSession = selectedSessionId != null
            && isAddToChatSystemMessageCompatible(selectedSessionId, request);
        String selectedModelOverride = request.getModelSelection().isCurrent()
            ? (appendToSelectedSession ? liveChatController.sessionSelectedModelOverride(selectedSessionId) : null)
            : AiRequestMappings.toSelectedModelOverride(request.getModelSelection());
        AiRequestConfigurationResolver.Issue configurationIssue =
            aiRequestConfigurationResolver.resolve(selectedModelOverride);
        if (configurationIssue != null) {
            handle.complete(new AiRequestResult(configurationIssue.getStatus(), null,
                configurationIssue.getDetail()));
            return null;
        }
        LiveChatSessionId sessionId = appendToSelectedSession
            ? selectedSessionId
            : startNewAddToChatSession(request, selectedModelOverride);
        if (sessionId == null || handle.isDone()) {
            return null;
        }
        applyAddToChatSessionOverrides(sessionId, request, selectedModelOverride);
        ToolAvailabilityLevel resolvedToolAvailability = resolveAddToToolAvailabilityLevel(
            sessionId,
            request.getToolAvailability());
        showChatTab();
        ChatTokenUsageTracker requestTokenUsageTracker = createRequestTokenUsageTracker(sessionId);
        ChatRequestFlow requestFlow = createVisibleRequestFlow(
            sessionId,
            requestTokenUsageTracker,
            visibleAiRequestCallbacksFactory.create(handle));
        AIChatService requestService = createVisibleRequestService(sessionId, requestFlow, requestTokenUsageTracker);
        if (requestService == null) {
            handle.complete(configurationErrorOrFailedResult(
                liveChatController.sessionSelectedModelOverride(sessionId),
                null));
            return null;
        }
        if (handle.isDone()) {
            return null;
        }
        boolean started = startVisibleRequest(
            sessionId,
            request.getPromptText(),
            requestService,
            requestFlow,
            requestTokenUsageTracker,
            false,
            false,
            resolvedToolAvailability,
            request.getSelectionOverride(),
            profileResolution.getMessage(),
            null);
        return started ? requestFlow : null;
    }

    public void startHiddenAiRequest(ResolvedAiRequest request,
                              AiRequestHandleImpl handle,
                              boolean showProgressDialog,
                              AiRequestTimeoutController timeoutController) {
        if (handle.isDone()) {
            return;
        }
        String selectedModelOverride = AiRequestMappings.toSelectedModelOverride(request.getModelSelection());
        AiRequestConfigurationResolver.Issue configurationIssue =
            aiRequestConfigurationResolver.resolve(selectedModelOverride);
        if (configurationIssue != null) {
            handle.complete(new AiRequestResult(configurationIssue.getStatus(), null,
                configurationIssue.getDetail()));
            return;
        }
        ProfileRequestResolution profileResolution = resolveRequestProfile(request);
        if (profileResolution.getConfigurationErrorDetail() != null) {
            handle.complete(new AiRequestResult(AiRequestStatus.CONFIGURATION_ERROR, null,
                profileResolution.getConfigurationErrorDetail()));
            return;
        }
        ToolAvailabilityLevel resolvedToolAvailability =
            resolvePromptStyleToolAvailability(request.getToolAvailability());
        ChatPromptRunner chatPromptRunner =
            chatPromptRunnerFactory.createHidden(liveChatController.currentSessionId());
        HiddenPromptRequestRunner hiddenPromptRequestRunner = chatPromptRunner.hiddenPromptRequestRunner();
        handle.setCancelAction(new Runnable() {
            @Override
            public void run() {
                if (hiddenPromptRequestRunner.isRequestActive()) {
                    hiddenPromptRequestRunner.cancelActiveRequest();
                }
                else {
                    completeCancelledRequest(handle);
                }
            }
        });
        if (handle.isDone()) {
            return;
        }
        HiddenAiRequestObserverBridge hiddenRequestObserver = hiddenAiRequestObserverFactory.create(handle);
        boolean started = chatPromptRunner.submitHiddenRequest(
            request.getPromptDisplayName(),
            request.getPromptText(),
            selectedModelOverride,
            resolvedToolAvailability,
            resolveSelectionOverride(request.getSelectionOverride()),
            null,
            showProgressDialog,
            hiddenRequestObserver,
            request.getSystemMessage(),
            request.isSystemMessageExact(),
            true,
            profileResolution.getMessage());
        if (!started) {
            handle.complete(configurationErrorOrFailedResult(selectedModelOverride, null));
            return;
        }
        timeoutController.armAfterStart();
    }

    public void completeCancelledRequest(AiRequestHandleImpl handle) {
        AiRequestStatus status = handle.isTimedOut() ? AiRequestStatus.TIMED_OUT : AiRequestStatus.CANCELLED;
        handle.complete(new AiRequestResult(status, null, null));
    }

    AiRequestResult failedResult(Throwable error) {
        return new AiRequestResult(AiRequestStatus.FAILED, null,
            AiRequestStatusMapper.detailMessage(error));
    }

    private AiRequestResult configurationErrorOrFailedResult(String selectedModelOverride,
                                                             RuntimeException runtimeException) {
        String configurationError = configurationErrorMessage(selectedModelOverride);
        if (configurationError != null) {
            return new AiRequestResult(AiRequestStatus.CONFIGURATION_ERROR, null, configurationError);
        }
        return failedResult(runtimeException == null
            ? new RuntimeException("Failed to start AI request.")
            : runtimeException);
    }

    private ProfileRequestResolution resolveRequestProfile(ResolvedAiRequest request) {
        if (request == null || !request.hasProfileRequest()) {
            return ProfileRequestResolution.none();
        }
        return assistantProfileSelectionSync.resolveRequestProfile(
            request.getProfileName(),
            request.getProfileMessage());
    }

    private String capturedSystemMessageFor(ResolvedAiRequest request) {
        if (request != null && request.getSystemMessage() != null) {
            return request.getSystemMessage();
        }
        return MessageBuilder.configuredSystemMessage();
    }

    private boolean isSystemMessageExactFor(ResolvedAiRequest request) {
        return request != null && request.isSystemMessageExact();
    }

    private String composeSystemInstruction(LiveChatSessionId sessionId,
                                            String baseSystemMessage,
                                            boolean isSystemMessageExact,
                                            ToolAvailabilityLevel toolAvailability,
                                            RequestVisibility visibility,
                                            boolean hasProfileInstruction) {
        return systemInstructionComposer.compose(new SystemInstructionContext(
            baseSystemMessage,
            isSystemMessageExact,
            toolAvailability,
            visibility,
            hasProfileInstruction,
            codeHostGuidanceForSession(sessionId)));
    }

    private String codeHostGuidanceForSession(LiveChatSessionId sessionId) {
        AiCodeHostService sessionCodeHostService = sessionAwareCodeHostService(sessionId);
        if (sessionCodeHostService == null) {
            return null;
        }
        AiCodeToolSet codeToolSet = new AiCodeToolSet(
            sessionCodeHostService,
            new AiCodeOperationAuthorizer(
                ToolCaller.CHAT,
                chatToolAvailabilitySettings::getToolAvailability,
                () -> liveChatController.sessionToolAvailabilityOverride(sessionId),
                () -> Boolean.valueOf(new FormulaEditingSettings().isEnabled()),
                sessionCodeHostService),
            null,
            ToolCaller.CHAT);
        return codeToolSet.systemMessageForChat(null);
    }

    private void updateCommittedSystemInstruction(LiveChatSessionId sessionId,
                                                  ToolAvailabilityLevel toolAvailability,
                                                  AssistantProfileSwitchMessage pendingProfileMessage) {
        String baseSystemMessage = liveChatController.sessionCapturedSystemMessage(sessionId);
        boolean isSystemMessageExact = liveChatController.isSessionSystemMessageExact(sessionId);
        boolean hasProfileInstruction = liveChatController.sessionHasProfileInstruction(sessionId)
            || pendingProfileMessage != null;
        String composedSystemMessage = composeSystemInstruction(
            sessionId,
            baseSystemMessage,
            isSystemMessageExact,
            toolAvailability,
            RequestVisibility.VISIBLE,
            hasProfileInstruction);
        liveChatController.updateSessionSystemMessage(
            sessionId,
            baseSystemMessage,
            composedSystemMessage,
            isSystemMessageExact);
    }

    private SelectionIdentifiersResponse resolveSelectionOverride(AiSelectionOverride selectionOverride) {
        return aiSelectionOverrideResolver.resolve(selectionOverride);
    }

    private ToolAvailabilityLevel resolvePromptStyleToolAvailability(AiToolAvailability toolAvailability) {
        ToolAvailabilityLevel mappedAvailability = AiRequestMappings.toToolAvailabilityLevel(toolAvailability);
        return mappedAvailability == null ? chatToolAvailabilitySettings.getToolAvailability() : mappedAvailability;
    }

    private ToolAvailabilityLevel explicitToolAvailabilityOverride(AiToolAvailability toolAvailability) {
        return toolAvailability == AiToolAvailability.CURRENT
            ? null
            : AiRequestMappings.toToolAvailabilityLevel(toolAvailability);
    }

    private ToolAvailabilityLevel resolveAddToToolAvailabilityLevel(LiveChatSessionId sessionId,
                                                                  AiToolAvailability toolAvailability) {
        ToolAvailabilityLevel mappedAvailability = AiRequestMappings.toToolAvailabilityLevel(toolAvailability);
        return mappedAvailability == null ? resolveEffectiveToolAvailability(sessionId) : mappedAvailability;
    }

    private void applyAddToChatSessionOverrides(LiveChatSessionId sessionId,
                                                ResolvedAiRequest request,
                                                String selectedModelOverride) {
        if (!request.getModelSelection().isCurrent()) {
            liveChatController.setSessionSelectedModelOverride(sessionId, selectedModelOverride);
        }
        ToolAvailabilityLevel explicitToolAvailability =
            explicitToolAvailabilityOverride(request.getToolAvailability());
        if (explicitToolAvailability != null) {
            liveChatController.setSessionToolAvailabilityOverride(sessionId, explicitToolAvailability);
        }
        if (liveChatController.isCurrentSession(sessionId)) {
            modelSelectionController.setDisplayedSelectionValueOverride(
                liveChatController.sessionSelectedModelOverride(sessionId));
            updateToolAvailabilityMenuSelection();
        }
    }

    private String promptDisplayName(String requestName) {
        String safeRequestName = requestName == null ? "" : requestName.trim();
        if (safeRequestName.isEmpty()) {
            safeRequestName = TextUtils.getText("ai_prompt_untitled");
        }
        return TextUtils.getText("ai_prompt_session_prefix") + safeRequestName;
    }

    boolean isChatTabSelected() {
        return UITools.getFreeplaneTabbedPanel() != null
            && UITools.getFreeplaneTabbedPanel().getSelectedComponent() == this;
    }

    private LiveChatSessionId startNewAddToChatSession(ResolvedAiRequest request,
                                                       String selectedModelOverride) {
        ChatMemory promptChatMemory = createChatMemory(
            capturedSystemMessageFor(request),
            isSystemMessageExactFor(request));
        return liveChatController.startNewScriptChat(
            promptChatMemory,
            request.getPromptDisplayName(),
            request.getModelSelection().isCurrent() ? null : selectedModelOverride,
            explicitToolAvailabilityOverride(request.getToolAvailability()));
    }

    private boolean isAddToChatSystemMessageCompatible(LiveChatSessionId sessionId, ResolvedAiRequest request) {
        if (request.getSystemMessage() == null) {
            return true;
        }
        return request.getSystemMessage().equals(liveChatController.sessionCapturedSystemMessage(sessionId))
            && request.isSystemMessageExact() == liveChatController.isSessionSystemMessageExact(sessionId);
    }

    private PromptReferenceMatch resolvePromptReference(String userMessage) {
        if (promptActionRegistry == null) {
            return null;
        }
        return promptReferenceResolver.resolveLeadingReference(userMessage, promptActionRegistry.prompts());
    }

    private boolean startVisibleRequest(LiveChatSessionId sessionId,
                                        String userMessage,
                                        AIChatService requestService,
                                        ChatRequestFlow requestFlow,
                                        ChatTokenUsageTracker requestTokenUsageTracker,
                                        boolean injectAssistantProfile,
                                        boolean updateSessionName) {
        return startVisibleRequest(sessionId, userMessage, requestService, requestFlow,
            requestTokenUsageTracker, injectAssistantProfile, updateSessionName,
            null, null, null, null);
    }

    private boolean startVisibleRequest(LiveChatSessionId sessionId,
                                        String userMessage,
                                        AIChatService requestService,
                                        ChatRequestFlow requestFlow,
                                        ChatTokenUsageTracker requestTokenUsageTracker,
                                        boolean injectAssistantProfile,
                                        boolean updateSessionName,
                                        PromptReferenceMatch promptReferenceMatch) {
        return startVisibleRequest(sessionId, userMessage, requestService, requestFlow,
            requestTokenUsageTracker, injectAssistantProfile, updateSessionName,
            null, null, null, promptReferenceMatch);
    }

    private boolean startVisibleRequest(LiveChatSessionId sessionId,
                                        String userMessage,
                                        AIChatService requestService,
                                        ChatRequestFlow requestFlow,
                                        ChatTokenUsageTracker requestTokenUsageTracker,
                                        boolean injectAssistantProfile,
                                        boolean updateSessionName,
                                        ToolAvailabilityLevel resolvedToolAvailability,
                                        AiSelectionOverride selectionOverride,
                                        AssistantProfileSwitchMessage requestedProfileMessage,
                                        PromptReferenceMatch promptReferenceMatch) {
        if (sessionId == null || requestService == null) {
            return false;
        }
        String visibleMessage = userMessage;
        String preparedMessage = promptReferenceMatch == null
            ? userMessage
            : promptReferenceMatch.getModelFacingText();
        if (resolvedToolAvailability != null) {
            try {
                preparedMessage = aiPromptRequestComposer.compose(
                    preparedMessage,
                    resolvedToolAvailability,
                    resolveSelectionOverride(selectionOverride));
            } catch (RuntimeException error) {
                return false;
            }
        }
        PromptReferenceUserMessage promptReferenceUserMessage = createPromptReferenceUserMessage(
            promptReferenceMatch,
            preparedMessage);
        registerVisibleRequest(sessionId, requestFlow, requestTokenUsageTracker);
        requestFlow.updateChatMemory(liveChatController.chatMemory(sessionId));
        AssistantProfileSwitchMessage pendingProfileMessage = requestedProfileMessage;
        if (pendingProfileMessage == null && injectAssistantProfile && currentSessionUsesAssistantProfile
            && liveChatController.isCurrentSession(sessionId)) {
            pendingProfileMessage = assistantProfileSelectionSync.pendingProfileMessageIfDifferent();
        }
        ToolAvailabilityLevel effectiveToolAvailability = resolvedToolAvailability == null
            ? resolveEffectiveToolAvailability(sessionId)
            : resolvedToolAvailability;
        updateCommittedSystemInstruction(sessionId, effectiveToolAvailability, pendingProfileMessage);
        installNextPromptReference(sessionId, promptReferenceUserMessage);
        requestFlow.beginRequest(visibleMessage, preparedMessage);
        boolean profileMessageChanged = false;
        if (requestedProfileMessage != null && liveChatController.isCurrentSession(sessionId)) {
            profileMessageChanged = assistantProfileSelectionSync.addProfileMessageIfDifferent(requestedProfileMessage);
        }
        else if (injectAssistantProfile && currentSessionUsesAssistantProfile
            && liveChatController.isCurrentSession(sessionId)) {
            assistantProfileSelectionSync.maybeInjectBeforeUserMessage();
            profileMessageChanged = pendingProfileMessage != null;
        }
        if ((profileMessageChanged || showInstructionMessages) && liveChatController.isCurrentSession(sessionId)) {
            rebuildHistoryFromMemory();
        }
        requestFlow.captureChatSnapshot();
        if (liveChatController.isCurrentSession(sessionId)) {
            if (promptReferenceUserMessage == null) {
                appendUserMessage(preparedMessage);
            }
            else {
                chatOutputView.appendPromptReferenceUserMessage(promptReferenceUserMessage);
            }
            inputArea.setText("");
        }
        if (updateSessionName && liveChatController.isCurrentSession(sessionId)) {
            liveChatController.updateSessionNameFromFirstUserMessage(userMessage);
        }
        refreshRequestTokenCounters(sessionId, requestTokenUsageTracker);
        requestFlow.submitRequest(requestService);
        return true;
    }

    private ChatRequestFlow createVisibleRequestFlow(LiveChatSessionId sessionId,
                                                     ChatTokenUsageTracker requestTokenUsageTracker,
                                                     ChatPromptRunner.VisiblePromptRequestCallbacks requestCallbacks) {
        return chatRequestFlowFactory.create(new ChatRequestFlow.RequestCallbacks() {
            @Override
            public void onRequestStarted() {
                if (liveChatController.isCurrentSession(sessionId)) {
                    inputArea.setEditable(false);
                    chatInputControls.setRequestActiveState();
                    updateUndoRedoButtonState();
                    refreshInstructionPreview();
                }
            }

            @Override
            public void onRequestFinished() {
                liveChatController.synchronizeTranscriptWithMemory(sessionId);
                syncRequestTrackerState(sessionId, requestTokenUsageTracker);
                unregisterVisibleRequest(sessionId);
                if (liveChatController.isCurrentSession(sessionId)) {
                    refreshTokenCounters();
                    updateInputState();
                    refreshInstructionPreview();
                }
            }

            @Override
            public void onUserTextRestored(String userText) {
                if (!liveChatController.isCurrentSession(sessionId)) {
                    return;
                }
                inputArea.setText(userText == null ? "" : userText);
                inputArea.setCaretPosition(inputArea.getText().length());
            }

            @Override
            public void onRequestFailed(String userText, String errorMessage) {
                if (liveChatController.isCurrentSession(sessionId)) {
                    appendFailureMessages(userText, errorMessage);
                }
                if (requestCallbacks != null) {
                    requestCallbacks.onFailed(userText, errorMessage);
                }
            }

            @Override
            public void onRequestCancelled() {
                if (requestCallbacks != null) {
                    requestCallbacks.onCancelled();
                }
            }

            @Override
            public void onAssistantResponse(String text) {
                if (liveChatController.isCurrentSession(sessionId)) {
                    appendAssistantMessage(text);
                }
                refreshRequestTokenCounters(sessionId, requestTokenUsageTracker);
                if (requestCallbacks != null) {
                    requestCallbacks.onResponseAppended(text);
                }
            }

            @Override
            public void onAssistantError(String text) {
            }

            @Override
            public void synchronizeTranscriptWithMemory() {
                liveChatController.synchronizeTranscriptWithMemory(sessionId);
            }

            @Override
            public void rebuildVisibleHistoryFromMemory() {
                if (liveChatController.isCurrentSession(sessionId)) {
                    AIChatPanel.this.rebuildHistoryFromMemory();
                }
            }

            @Override
            public void onPostResponseEviction() {
                liveChatController.synchronizeTranscriptWithMemory(sessionId);
                if (liveChatController.isCurrentSession(sessionId)) {
                    rebuildHistoryFromMemory();
                    updateInputState();
                }
            }

            @Override
            public void refreshTokenCounters() {
                refreshRequestTokenCounters(sessionId, requestTokenUsageTracker);
            }

            @Override
            public boolean isToolCallHistoryVisible() {
                return chatDisplaySettings.isToolCallHistoryVisible();
            }

            @Override
            public long currentVisibleHistoryRebuildCounter() {
                return AIChatPanel.this.currentVisibleHistoryRebuildCounter();
            }

            @Override
            public void onToolSummaryAppended(ChatMemoryRenderEntry entry) {
                if (liveChatController.isCurrentSession(sessionId)) {
                    AIChatPanel.this.appendHistoryEntry(entry);
                }
            }
        }, requestTokenUsageTracker);
    }

    private AIChatService createVisibleRequestService(LiveChatSessionId sessionId,
                                                      ChatRequestFlow requestFlow,
                                                      ChatTokenUsageTracker requestTokenUsageTracker) {
        String selectedModelOverride = liveChatController.sessionSelectedModelOverride(sessionId);
        if (configurationErrorMessage(selectedModelOverride) != null) {
            return null;
        }
        ToolAvailabilityLevel toolAvailabilityOverride =
            liveChatController.sessionToolAvailabilityOverride(sessionId);
        AiCodeHostService sessionCodeHostService = sessionAwareCodeHostService(sessionId);
        AIToolSetBuilder toolSetBuilder = new AIToolSetBuilder()
            .toolCallSummaryHandler(requestFlow::onToolCallSummary)
            .availableMaps(availableMaps)
            .mapAccessListener(liveChatController.mapAccessListener(sessionId))
            .codeHostService(sessionCodeHostService)
            .aiCodeOperationAuthorizer(new AiCodeOperationAuthorizer(
                ToolCaller.CHAT,
                chatToolAvailabilitySettings::getToolAvailability,
                () -> liveChatController.sessionToolAvailabilityOverride(sessionId),
                () -> Boolean.valueOf(new FormulaEditingSettings().isEnabled()),
                sessionCodeHostService));
        List<Object> toolObjects = toolSetBuilder.buildToolObjects();
        String baseSystemMessage = liveChatController.sessionCapturedSystemMessage(sessionId);
        return AIChatServiceFactory.createService(
            (org.freeplane.plugin.ai.tools.AIToolSet) toolObjects.get(0),
            toolObjects,
            liveChatController.chatMemory(sessionId),
            requestTokenUsageTracker,
            requestFlow::onToolCallSummary,
            requestFlow.cancellationSupplier(),
            requestFlow::onProviderUsage,
            toolAvailabilityOverride == null ? null : () -> toolAvailabilityOverride,
            selectedModelOverride,
            baseSystemMessage,
            liveChatController.isSessionSystemMessageExact(sessionId),
            false);
    }

    private AiCodeHostService currentCodeHostService() {
        return codeHostService;
    }

    private AiCodeHostService sessionAwareCodeHostService(LiveChatSessionId sessionId) {
        AiCodeHostService delegate = currentCodeHostService();
        if (delegate == null || sessionId == null) {
            return delegate;
        }
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                return delegate.readCode(request);
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                WriteCodeResponse response = delegate.writeCode(request);
                clearPendingAiOwnedUserRunFollowup(response == null ? null : response.getHost());
                return response;
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                return delegate.compileCode(request);
            }

            @Override
            public RunCodeResponse runCode(RunCodeRequest request) {
                RunCodeResponse response = delegate.runCode(request);
                rememberAiOwnedUserRunFollowup(response, sessionId);
                return response;
            }

            @Override
            public AiChatCodeOperationResult evaluateFormula(EvaluateFormulaRequest request) {
                return delegate.evaluateFormula(request);
            }

            @Override
            public void addRunListener(AiCodeRunListener listener) {
                delegate.addRunListener(listener);
            }

            @Override
            public void removeRunListener(AiCodeRunListener listener) {
                delegate.removeRunListener(listener);
            }
        };
    }

    private ChatTokenUsageTracker createRequestTokenUsageTracker(LiveChatSessionId sessionId) {
        ChatTokenUsageTracker requestTokenUsageTracker = new ChatTokenUsageTracker(totals -> {
        });
        applyTokenCounterMode(requestTokenUsageTracker);
        requestTokenUsageTracker.restoreState(liveChatController.getTokenUsageState(sessionId));
        return requestTokenUsageTracker;
    }

    private void syncRequestTrackerState(LiveChatSessionId sessionId,
                                         ChatTokenUsageTracker requestTokenUsageTracker) {
        liveChatController.setTokenUsageState(sessionId, requestTokenUsageTracker.snapshotState());
        if (liveChatController.isCurrentSession(sessionId)) {
            chatTokenUsageTracker.restoreState(requestTokenUsageTracker.snapshotState());
        }
    }

    private void refreshRequestTokenCounters(LiveChatSessionId sessionId,
                                             ChatTokenUsageTracker requestTokenUsageTracker) {
        syncRequestTrackerState(sessionId, requestTokenUsageTracker);
        if (liveChatController.isCurrentSession(sessionId)) {
            refreshTokenCounters();
        }
    }

    private void registerVisibleRequest(LiveChatSessionId sessionId,
                                        ChatRequestFlow requestFlow,
                                        ChatTokenUsageTracker requestTokenUsageTracker) {
        activeVisibleRequestFlows.put(sessionId, requestFlow);
        activeVisibleRequestTrackers.put(sessionId, requestTokenUsageTracker);
    }

    private void unregisterVisibleRequest(LiveChatSessionId sessionId) {
        activeVisibleRequestFlows.remove(sessionId);
        activeVisibleRequestTrackers.remove(sessionId);
    }

    private ChatRequestFlow currentVisibleRequestFlow() {
        LiveChatSessionId sessionId = liveChatController.currentSessionId();
        return sessionId == null ? null : activeVisibleRequestFlows.get(sessionId);
    }

    private boolean isRequestActive() {
        ChatRequestFlow currentRequestFlow = currentVisibleRequestFlow();
        return currentRequestFlow != null && currentRequestFlow.isRequestActive();
    }

    private boolean isChatAiRequestActive() {
        return !activeVisibleRequestFlows.isEmpty();
    }

    private void notifyVisibleConfigurationError(LiveChatSessionId sessionId) {
        String message = configurationErrorMessage(liveChatController.sessionSelectedModelOverride(sessionId));
        if (message != null) {
            notifyUser(message, true);
        }
    }

    private String normalizeSelectionValue(String selectionValue) {
        if (selectionValue == null) {
            return null;
        }
        String normalized = selectionValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AssistantProfileChatMemory activeAssistantProfileChatMemory() {
        if (chatMemory instanceof AssistantProfileChatMemory) {
            return (AssistantProfileChatMemory) chatMemory;
        }
        return null;
    }

    private PromptReferenceUserMessage createPromptReferenceUserMessage(PromptReferenceMatch promptReferenceMatch,
                                                                        String modelFacingText) {
        if (promptReferenceMatch == null) {
            return null;
        }
        return new PromptReferenceUserMessage(
            promptReferenceMatch.getVisibleText(),
            promptReferenceMatch.getPromptName(),
            promptReferenceMatch.getPromptText(),
            modelFacingText,
            promptReferenceMatch.getReferenceEndOffset());
    }

    private void installNextPromptReference(LiveChatSessionId sessionId,
                                            PromptReferenceUserMessage promptReferenceUserMessage) {
        ChatMemory sessionMemory = liveChatController.chatMemory(sessionId);
        if (sessionMemory instanceof AssistantProfileChatMemory) {
            ((AssistantProfileChatMemory) sessionMemory).useNextPromptReference(promptReferenceUserMessage);
        }
    }

    private void openPromptChat(LiveChatSessionId sessionId,
                                AIChatService promptService,
                                String preparedMessage,
                                ChatRequestFlow requestFlow,
                                ChatTokenUsageTracker requestTokenUsageTracker,
                                ChatPromptRunner.VisiblePromptRequestCallbacks requestCallbacks,
                                AssistantProfileSwitchMessage requestedProfileMessage) {
        showChatTab();
        startVisibleRequest(sessionId, preparedMessage, promptService, requestFlow,
            requestTokenUsageTracker, false, false, null, null, requestedProfileMessage, null);
    }

    private void cancelActiveRequest() {
        ChatRequestFlow currentRequestFlow = currentVisibleRequestFlow();
        if (currentRequestFlow != null) {
            currentRequestFlow.cancelActiveRequest();
        }
    }

    private void updateInputState() {
        chatInputControls.update(
            isRequestActive(),
            isProviderConfigured());
    }

    private void configureEmptyHistoryFocusTransfer() {
        messageHistoryPane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if (messageHistory.size() != 0) {
                    return;
                }
                SwingUtilities.invokeLater(inputArea::requestFocusInWindow);
            }
        });
    }

    public boolean isAiProviderConfigured() {
        return isProviderConfigured();
    }

    private boolean isProviderConfigured() {
        return isNonEmptyText(configuration.getOpenRouterKey())
            || isNonEmptyText(configuration.getGeminiKey())
            || configuration.hasOllamaServiceAddress();
    }

    private boolean isNonEmptyText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private ToolAvailabilityLevel currentEffectiveToolAvailability() {
        return resolveEffectiveToolAvailability(liveChatController.currentSessionId());
    }

    private ToolAvailabilityLevel resolveEffectiveToolAvailability(LiveChatSessionId sessionId) {
        ToolAvailabilityLevel toolAvailabilityOverride =
            liveChatController.sessionToolAvailabilityOverride(sessionId);
        return toolAvailabilityOverride == null
            ? chatToolAvailabilitySettings.getToolAvailability()
            : toolAvailabilityOverride;
    }

    private void applyUserSelectedToolAvailability(ToolAvailabilityLevel toolAvailability) {
        if (toolAvailability == null) {
            return;
        }
        ResourceController.getResourceController().setProperty(
            ToolAvailabilityLevelSettings.TOOL_AVAILABILITY_PROPERTY,
            toolAvailability.name());
        liveChatController.clearCurrentSessionToolAvailabilityOverride();
        refreshInstructionPreview();
    }

    private void updateToolAvailabilityMenuSelection() {
        chatToolAvailabilityMenu.refreshSelection();
    }

    private String configurationErrorMessage(String selectedModelOverride) {
        AiRequestConfigurationResolver.Issue issue = aiRequestConfigurationResolver.resolve(selectedModelOverride);
        return issue == null ? null : issue.getDetail();
    }

    private void appendUserMessage(String text) {
        chatOutputView.appendUserMessage(text);
    }

    private void appendAssistantMessage(String text) {
        chatOutputView.appendAssistantMessage(text);
    }

    private void appendProfileMessage(String profileName) {
        chatOutputView.appendProfileMessage(profileName);
    }

    private void appendFailureMessages(String userText, String errorMessage) {
        chatOutputView.appendFailureMessages(userText, errorMessage);
    }

    private void notifyUser(String message, boolean error) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        Controller controller = Controller.getCurrentController();
        if (controller != null && controller.getViewController() != null) {
            controller.getViewController().out(message);
            return;
        }
        if (error) {
            UITools.errorMessage(message);
        } else {
            UITools.informationMessage(message);
        }
    }

    private void showChatTab() {
        if (UITools.getFreeplaneTabbedPanel() != null) {
            UITools.getFreeplaneTabbedPanel().setSelectedComponent(this);
        }
    }

    boolean canReopenAiOwnedCode() {
        AiCodeHostService activeCodeHostService = currentCodeHostService();
        if (activeCodeHostService == null) {
            return false;
        }
        try {
            ReadCodeResponse response = activeCodeHostService.readCode(new ReadCodeRequest(ScriptHost.AI));
            return response != null && response.getCodeState() != CodeState.NO_CODE;
        } catch (RuntimeException error) {
            return false;
        }
    }

    boolean reopenAiOwnedCode() {
        if (!canReopenAiOwnedCode()) {
            return false;
        }
        AiCodeHostService activeCodeHostService = currentCodeHostService();
        if (!(activeCodeHostService instanceof org.freeplane.plugin.ai.code.RoutingAiCodeHostService)) {
            return false;
        }
        return ((org.freeplane.plugin.ai.code.RoutingAiCodeHostService) activeCodeHostService).showCurrentAiOwnedCode();
    }

    private void clearPendingAiOwnedUserRunFollowup(ScriptHost host) {
        if (host == ScriptHost.AI) {
            pendingAiOwnedUserRunFollowupSessionId = null;
            refreshInstructionPreview();
        }
    }

    private void rememberAiOwnedUserRunFollowup(RunCodeResponse response, LiveChatSessionId sessionId) {
        if (response == null || response.getHost() != ScriptHost.AI) {
            return;
        }
        if (response.getCodeState() == CodeState.WAITING_FOR_USER_RUN && sessionId != null) {
            pendingAiOwnedUserRunFollowupSessionId = sessionId;
            refreshInstructionPreview();
            return;
        }
        pendingAiOwnedUserRunFollowupSessionId = null;
        refreshInstructionPreview();
    }

    void handleCodeRunFinished(RunCodeResponse response) {
        if (response == null || response.getHost() != ScriptHost.AI || response.getRunInitiator() != org.freeplane.features.ai.code.ScriptRunInitiator.USER) {
            return;
        }
        LiveChatSessionId pendingSessionId = pendingAiOwnedUserRunFollowupSessionId;
        pendingAiOwnedUserRunFollowupSessionId = null;
        if (pendingSessionId == null) {
            return;
        }
        submitMessageToSession(
            pendingSessionId,
            AutomaticCodeStatusMessage.forRunResponse(response).singleText());
    }

    public void setCodeHostService(AiCodeHostService codeHostService) {
        if (this.codeHostService != null) {
            this.codeHostService.removeRunListener(aiCodeRunListener);
        }
        this.codeHostService = codeHostService;
        if (this.codeHostService != null) {
            this.codeHostService.addRunListener(aiCodeRunListener);
        }
        refreshInstructionPreview();
    }

    public LiveChatSessionId currentSessionId() {
        return liveChatController.currentSessionId();
    }

    public LiveChatSessionId startNewChat() {
        return liveChatController.startNewChat();
    }

    public ToolAvailabilityLevel effectiveToolAvailability(LiveChatSessionId sessionId) {
        return resolveEffectiveToolAvailability(sessionId);
    }

    public void setSessionToolAvailabilityOverride(LiveChatSessionId sessionId,
                                                   ToolAvailabilityLevel toolAvailabilityOverride) {
        liveChatController.setSessionToolAvailabilityOverride(sessionId, toolAvailabilityOverride);
        refreshInstructionPreview();
    }

    public void switchToSession(LiveChatSessionId sessionId) {
        liveChatController.switchToSession(sessionId);
    }

    public void showAndFocusInput() {
        showChatTab();
        SwingUtilities.invokeLater(inputArea::requestFocusInWindow);
    }

    public void setAttachedEditorIndicatorVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> updateTabIcon(visible));
    }

    private void updateTabIcon(boolean attached) {
        JTabbedPane tabbedPane = UITools.getFreeplaneTabbedPanel();
        if (tabbedPane == null) {
            return;
        }
        int tabIndex = tabbedPane.indexOfComponent(this);
        if (tabIndex >= 0) {
            tabbedPane.setIconAt(tabIndex, attached ? attachedAiTabIcon : aiTabIcon);
        }
    }

    public boolean submitMessageToSession(LiveChatSessionId sessionId, String userMessage) {
        if (sessionId == null || userMessage == null || userMessage.trim().isEmpty()) {
            return false;
        }
        switchToSession(sessionId);
        showAndFocusInput();
        ChatTokenUsageTracker requestTokenUsageTracker = createRequestTokenUsageTracker(sessionId);
        ChatRequestFlow requestFlow = createVisibleRequestFlow(sessionId, requestTokenUsageTracker, null);
        AIChatService requestService = createVisibleRequestService(sessionId, requestFlow, requestTokenUsageTracker);
        if (requestService == null) {
            notifyVisibleConfigurationError(sessionId);
            return false;
        }
        return startVisibleRequest(sessionId, userMessage, requestService, requestFlow, requestTokenUsageTracker,
            false, false);
    }

    public void clearPendingAiOwnedUserRunFollowup() {
        pendingAiOwnedUserRunFollowupSessionId = null;
        refreshInstructionPreview();
    }

    public ToolCallSummaryHandler toolCallSummaryHandler() {
        return summary -> {
            if (SwingUtilities.isEventDispatchThread()) {
                appendExternalToolSummary(summary);
            }
            else {
                SwingUtilities.invokeLater(() -> appendExternalToolSummary(summary));
            }
        };
    }

    private void appendExternalToolSummary(ToolCallSummary summary) {
        if (summary == null || summary.getSummaryText() == null || summary.getSummaryText().trim().isEmpty()) {
            return;
        }
        if (!chatDisplaySettings.isToolCallHistoryVisible()) {
            return;
        }
        ChatRequestFlow currentRequestFlow = currentVisibleRequestFlow();
        if (currentRequestFlow != null) {
            currentRequestFlow.onToolCallSummary(summary);
            return;
        }
        LiveChatSessionId sessionId = currentSessionOrNewChat();
        if (sessionId == null) {
            return;
        }
        appendToolSummaryToSession(sessionId, summary);
    }

    private LiveChatSessionId currentSessionOrNewChat() {
        LiveChatSessionId sessionId = liveChatController.currentSessionId();
        if (sessionId != null && liveChatController.chatMemory(sessionId) != null) {
            return sessionId;
        }
        sessionId = liveChatController.startNewChat();
        if (sessionId != null) {
            showAndFocusInput();
        }
        return sessionId;
    }

    private void appendToolSummaryToSession(LiveChatSessionId sessionId, ToolCallSummary summary) {
        ChatMemory sessionChatMemory = liveChatController.chatMemory(sessionId);
        if (!(sessionChatMemory instanceof AssistantProfileChatMemory)) {
            return;
        }
        AssistantProfileChatMemory assistantProfileChatMemory = (AssistantProfileChatMemory) sessionChatMemory;
        assistantProfileChatMemory.addToolCallSummary(summary.getSummaryText(), summary.getToolCaller());
        liveChatController.synchronizeTranscriptWithMemory(sessionId);
        if (liveChatController.isCurrentSession(sessionId)) {
            if (summary.getToolCaller() == ToolCaller.CHAT) {
                rebuildHistoryFromMemory();
            }
            else {
                appendHistoryEntry(ChatMemoryRenderEntry.forToolSummary(summary.getSummaryText(), summary.getToolCaller()));
            }
            refreshTokenCounters();
        }
    }

    public void persistCurrentChatIfNeeded() {
        liveChatController.persistCurrentSessionIfNeeded();
    }

    private void resetMessageHistory() {
        messageHistoryPane.setText("<html><body></body></html>");
        messageHistoryPane.setCaretPosition(0);
    }

    private void updateTokenUsageLabel(ChatUsageTotals totals) {
        chatOutputView.updateTokenUsageLabel(totals);
    }

    private void syncUiToActivatedSession(ChatMemory sessionChatMemory, boolean fromTranscriptRestore) {
        chatMemory = sessionChatMemory;
        refreshActiveChatMemoryMaximumTokenCount();
        currentSessionUsesAssistantProfile = liveChatController.currentSessionUsesAssistantProfile();
        modelSelectionController.setDisplayedSelectionValueOverride(
            liveChatController.currentSessionSelectedModelOverride());
        updateToolAvailabilityMenuSelection();
        LiveChatSessionId sessionId = liveChatController.currentSessionId();
        ChatTokenUsageTracker activeRequestTracker = sessionId == null
            ? null
            : activeVisibleRequestTrackers.get(sessionId);
        if (activeRequestTracker != null) {
            chatTokenUsageTracker.restoreState(activeRequestTracker.snapshotState());
        }
        else {
            chatTokenUsageTracker.restoreState(liveChatController.getCurrentTokenUsageState());
        }
        assistantProfileSelectionSync.setChatMemory(chatMemory);
        assistantProfilePaneBuilder.setSelectionEnabled(currentSessionUsesAssistantProfile);
        if (currentSessionUsesAssistantProfile) {
            assistantProfilePaneBuilder.syncSelection(fromTranscriptRestore);
        }
        rebuildHistoryFromMemory();
        refreshTokenCounters();
        updateInputState();
    }

    private void updateUndoRedoButtonState() {
        boolean enabled = !isChatAiRequestActive();
        undoButton.setEnabled(enabled && liveChatController.canUndo());
        redoButton.setEnabled(enabled && liveChatController.canRedo());
    }

    private void undoLastTurn() {
        if (isChatAiRequestActive()) {
            return;
        }
        boolean canUndo = liveChatController.canUndo();
        String userMessage = liveChatController.undoLastTurn();
        if (canUndo) {
            chatTokenUsageTracker.undoLastResponse();
        }
        if (!liveChatController.canRedo() && userMessage.isEmpty()) {
            updateUndoRedoButtonState();
            return;
        }
        rebuildHistoryFromMemory();
        refreshTokenCounters();
        inputArea.setText(userMessage);
        inputArea.setCaretPosition(inputArea.getText().length());
        updateInputState();
    }

    private void redoLastTurn() {
        if (isChatAiRequestActive()) {
            return;
        }
        if (!liveChatController.canRedo()) {
            updateUndoRedoButtonState();
            return;
        }
        liveChatController.redoLastTurn();
        chatTokenUsageTracker.redoLastResponse();
        rebuildHistoryFromMemory();
        refreshTokenCounters();
        inputArea.setText("");
        inputArea.setCaretPosition(0);
        updateInputState();
    }

    private long currentVisibleHistoryRebuildCounter() {
        return visibleHistoryRebuildCounter;
    }

    private void rebuildHistoryFromMemory() {
        chatOutputView.rebuildHistory(historyMessages());
        visibleHistoryRebuildCounter++;
        refreshInstructionPreview();
    }

    private void appendHistoryEntry(ChatMemoryRenderEntry entry) {
        chatOutputView.appendHistoryEntry(entry);
        refreshInstructionPreview();
    }

    private void refreshInstructionPreview() {
        if (nextRequestInstructionPreviewView == null) {
            return;
        }
        if (!showNextRequestInstructionPreview || isChatAiRequestActive()) {
            nextRequestInstructionPreviewView.hidePreview();
            return;
        }
        List<PreviewInstructionBlock> previewBlocks = buildInstructionPreviewBlocks();
        if (previewBlocks.isEmpty()) {
            nextRequestInstructionPreviewView.hidePreview();
            return;
        }
        nextRequestInstructionPreviewView.showPreview(previewBlocks);
    }

    private List<PreviewInstructionBlock> buildInstructionPreviewBlocks() {
        LiveChatSessionId sessionId = liveChatController.currentSessionId();
        if (sessionId == null) {
            return Collections.emptyList();
        }
        String baseSystemMessage = liveChatController.sessionCapturedSystemMessage(sessionId);
        AssistantProfileSwitchMessage pendingProfileMessage = currentSessionUsesAssistantProfile
            ? assistantProfileSelectionSync.pendingProfileMessageIfDifferent()
            : null;
        AssistantProfileSwitchMessage previewProfileMessage = pendingProfileMessage != null
            ? pendingProfileMessage
            : liveChatController.sessionLatestProfileSwitchMessage(sessionId);
        boolean hasProfileInstruction = liveChatController.sessionHasProfileInstruction(sessionId)
            || pendingProfileMessage != null;
        String systemText = composeSystemInstruction(
            sessionId,
            baseSystemMessage,
            liveChatController.isSessionSystemMessageExact(sessionId),
            resolveEffectiveToolAvailability(sessionId),
            RequestVisibility.VISIBLE,
            hasProfileInstruction);
        List<PreviewInstructionBlock> blocks = new ArrayList<PreviewInstructionBlock>();
        if (systemText != null && !systemText.trim().isEmpty()) {
            blocks.add(new PreviewInstructionBlock(
                previewLabel("ai_chat_instruction_preview_system_message", "System message"),
                systemText,
                PreviewInstructionKind.SYSTEM));
        }
        if (previewProfileMessage != null) {
            blocks.add(new PreviewInstructionBlock(
                profilePreviewLabel(previewProfileMessage),
                previewProfileMessage.getProfileMessage(),
                PreviewInstructionKind.PROFILE));
        }
        PromptReferenceMatch promptReferenceMatch = resolvePromptReference(inputArea.getText().trim());
        if (promptReferenceMatch != null && !promptReferenceMatch.getPromptText().trim().isEmpty()) {
            blocks.add(new PreviewInstructionBlock(
                promptPreviewLabel(promptReferenceMatch),
                promptReferenceMatch.getPromptText(),
                PreviewInstructionKind.PROMPT));
        }
        return blocks;
    }

    private String promptPreviewLabel(PromptReferenceMatch promptReferenceMatch) {
        String promptName = promptReferenceMatch == null ? "" : promptReferenceMatch.getPromptName();
        return promptName == null || promptName.trim().isEmpty()
            ? "Prompt"
            : java.text.MessageFormat.format("Prompt: {0}", promptName.trim());
    }

    private String profilePreviewLabel(AssistantProfileSwitchMessage profileMessage) {
        String profileName = profileMessage == null ? "" : profileMessage.getProfileName();
        if (profileName == null || profileName.trim().isEmpty()) {
            return previewLabel("ai_chat_instruction_preview_profile_message", "Profile message");
        }
        return formattedPreviewLabel(
            "ai_chat_instruction_preview_profile_message_named",
            "Profile message: {0}",
            profileName.trim());
    }

    private String previewLabel(String key, String fallback) {
        try {
            String text = TextUtils.getText(key, fallback);
            return text == null ? fallback : text;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String formattedPreviewLabel(String key, String fallback, String argument) {
        try {
            String text = TextUtils.format(key, argument);
            if (text != null) {
                return text;
            }
        } catch (Exception ignored) {
        }
        return java.text.MessageFormat.format(fallback, argument);
    }

    private List<ChatMemoryRenderEntry> historyMessages() {
        AssistantProfileChatMemory memory = activeAssistantProfileChatMemory();
        if (memory != null) {
            return adjustedInitialHistoryMessages(memory.panelConversationRenderEntries());
        }
        if (chatMemory == null) {
            return adjustedInitialHistoryMessages(Collections.<ChatMemoryRenderEntry>emptyList());
        }
        List<ChatMessage> messages = chatMemory.messages();
        List<ChatMemoryRenderEntry> entries = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            entries.add(ChatMemoryRenderEntry.forMessage(messages.get(index)));
        }
        return adjustedInitialHistoryMessages(entries);
    }

    private List<ChatMemoryRenderEntry> adjustedInitialHistoryMessages(List<ChatMemoryRenderEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMemoryRenderEntry> mcpToolSummaryEntries = new ArrayList<ChatMemoryRenderEntry>();
        for (ChatMemoryRenderEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (isGeneralSystemEntry(entry)) {
                continue;
            }
            if (isMcpToolSummaryEntry(entry)) {
                mcpToolSummaryEntries.add(entry);
                continue;
            }
            return entries;
        }
        return mcpToolSummaryEntries;
    }

    private boolean isGeneralSystemEntry(ChatMemoryRenderEntry entry) {
        return entry != null && !entry.isToolSummary() && entry.chatMessage() instanceof GeneralSystemMessage;
    }

    private boolean isMcpToolSummaryEntry(ChatMemoryRenderEntry entry) {
        return entry != null && entry.isToolSummary() && entry.toolCaller() == ToolCaller.MCP;
    }

    private ChatMemory createChatMemory() {
        return createChatMemory(MessageBuilder.configuredSystemMessage(), false);
    }

    private ChatMemory createChatMemory(String capturedSystemMessage, boolean isSystemMessageExact) {
        ChatMemorySettings chatMemorySettings = new ChatMemorySettings();
        ChatMemory memory = AssistantProfileChatMemory.builder()
            .dynamicMaxTokens(ignored -> chatMemorySettings.getMaximumTokenCount())
            .tokenEstimatorModelNameProvider(this::currentModelNameForTokenEstimator)
            .build();
        String baseSystemMessage = capturedSystemMessage == null ? "" : capturedSystemMessage.trim();
        memory.add(new GeneralSystemMessage(baseSystemMessage, baseSystemMessage, isSystemMessageExact));
        return memory;
    }

    private void refreshTokenCounterMode() {
        applyTokenCounterMode(chatTokenUsageTracker);
        for (ChatTokenUsageTracker requestTokenUsageTracker : activeVisibleRequestTrackers.values()) {
            applyTokenCounterMode(requestTokenUsageTracker);
        }
        refreshTokenCounters();
    }

    private String tokenCounterModeLabel(ChatTokenCounterMode counterMode) {
        if (counterMode == null) {
            return null;
        }
        String key = "OptionPanel.ai_chat_token_counter_mode." + counterMode.getPreferenceValue();
        return TextUtils.getOptionalText(key);
    }

    private void refreshTokenCounters() {
        chatTokenUsageTracker.refreshTotals(activeAssistantProfileChatMemory(),
            TextUtils.getOptionalText("ai_chat_token_counter.input"),
            TextUtils.getOptionalText("ai_chat_token_counter.output"));
    }

    private void refreshChatMemoryMaximumTokenCount() {
        refreshActiveChatMemoryMaximumTokenCount();
        liveChatController.synchronizeTranscriptWithMemory();
        rebuildHistoryFromMemory();
        refreshTokenCounters();
    }

    private void refreshActiveChatMemoryMaximumTokenCount() {
        AssistantProfileChatMemory memory = activeAssistantProfileChatMemory();
        if (memory != null) {
            memory.refreshCompactionForCurrentMaxTokens();
        }
    }

    private void applyTokenCounterMode(ChatTokenUsageTracker tokenUsageTracker) {
        ChatTokenCounterMode counterMode = new ChatTokenCounterSettings().getCounterMode();
        tokenUsageTracker.setCounterMode(counterMode, tokenCounterModeLabel(counterMode));
    }

    private String currentModelNameForTokenEstimator() {
        String effectiveSelectionValue = liveChatController.currentSessionSelectedModelOverride();
        if (effectiveSelectionValue == null || effectiveSelectionValue.trim().isEmpty()) {
            effectiveSelectionValue = configuration.getSelectedModelValue();
        }
        AIModelSelection selection = AIModelSelection.fromSelectionValue(effectiveSelectionValue);
        if (selection == null) {
            return null;
        }
        return selection.getModelName();
    }

    private TextController requireTextController() {
        ModeController modeController = Controller.getCurrentModeController();
        if (modeController == null) {
            throw new IllegalStateException("Current mode controller is not available.");
        }
        TextController textController = modeController.getExtension(TextController.class);
        if (textController == null) {
            throw new IllegalStateException("Text controller is not available.");
        }
        return textController;
    }

}
