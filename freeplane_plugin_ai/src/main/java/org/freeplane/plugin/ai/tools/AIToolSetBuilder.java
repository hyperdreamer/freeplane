package org.freeplane.plugin.ai.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.features.ai.code.AiCodeHostService;
import org.freeplane.features.ai.code.CodeLifecycleStatus;
import org.freeplane.features.ai.code.CompileCodeRequest;
import org.freeplane.features.ai.code.CompileCodeResponse;
import org.freeplane.features.ai.code.ReadCodeRequest;
import org.freeplane.features.ai.code.ReadCodeResponse;
import org.freeplane.features.ai.code.ScriptHost;
import org.freeplane.features.ai.code.WriteCodeRequest;
import org.freeplane.features.ai.code.WriteCodeResponse;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.ai.code.AiCodeToolSet;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.maps.ControllerMapModelProvider;
import org.freeplane.plugin.ai.tools.content.AttributesContentReader;
import org.freeplane.plugin.ai.tools.content.ContentTypeConverter;
import org.freeplane.plugin.ai.tools.content.EditableContentReader;
import org.freeplane.plugin.ai.tools.content.IconDescriptionResolver;
import org.freeplane.plugin.ai.tools.content.IconsContentReader;
import org.freeplane.plugin.ai.tools.content.NodeContentFactories;
import org.freeplane.plugin.ai.tools.content.NodeContentItemReader;
import org.freeplane.plugin.ai.tools.content.NodeContentReader;
import org.freeplane.plugin.ai.tools.content.NodeStyleContentReader;
import org.freeplane.plugin.ai.tools.content.TagsContentReader;
import org.freeplane.plugin.ai.tools.content.TextualContentReader;
import org.freeplane.plugin.ai.tools.documentation.GetApiDocumentationTool;
import org.freeplane.plugin.ai.tools.text.DefaultEnglishTextProvider;
import org.freeplane.plugin.ai.tools.text.EnglishTextProvider;
import org.freeplane.plugin.ai.tools.utilities.ToolCallSummaryHandler;
import org.freeplane.plugin.ai.tools.utilities.ToolCaller;

public class AIToolSetBuilder {
    private ToolCallSummaryHandler toolCallSummaryHandler;
    private AvailableMaps availableMaps;
    private AvailableMaps.MapAccessListener mapAccessListener;
    private TextController textController;
    private AttributeController attributeController;
    private IconController iconController;
    private MMapController mapController;
    private AiCodeHostService codeHostService;
    private ToolCaller toolCaller = ToolCaller.CHAT;

    public AIToolSetBuilder toolCallSummaryHandler(ToolCallSummaryHandler handler) {
        this.toolCallSummaryHandler = handler;
        return this;
    }

    public AIToolSetBuilder toolCaller(ToolCaller toolCaller) {
        this.toolCaller = Objects.requireNonNull(toolCaller, "toolCaller");
        return this;
    }

    public AIToolSetBuilder availableMaps(AvailableMaps availableMaps) {
        this.availableMaps = availableMaps;
        return this;
    }

    public AIToolSetBuilder mapAccessListener(AvailableMaps.MapAccessListener mapAccessListener) {
        this.mapAccessListener = mapAccessListener;
        return this;
    }

    public AIToolSetBuilder textController(TextController textController) {
        this.textController = textController;
        return this;
    }

    public AIToolSetBuilder attributeController(AttributeController attributeController) {
        this.attributeController = attributeController;
        return this;
    }

    public AIToolSetBuilder iconController(IconController iconController) {
        this.iconController = iconController;
        return this;
    }

    public AIToolSetBuilder mapController(MMapController mapController) {
        this.mapController = mapController;
        return this;
    }

    public AIToolSetBuilder codeHostService(AiCodeHostService codeHostService) {
        this.codeHostService = codeHostService;
        return this;
    }

    public AIToolSet build() {
        ResolvedComponents resolvedComponents = resolveComponents();
        return createBaseToolSet(resolvedComponents);
    }

    public List<Object> buildToolObjects() {
        ResolvedComponents resolvedComponents = resolveComponents();
        AIToolSet toolSet = createBaseToolSet(resolvedComponents);
        AiCodeToolSet aiCodeToolSet = new AiCodeToolSet(
            effectiveCodeHostService(),
            toolCallSummaryHandler,
            toolCaller);
        return Collections.<Object>unmodifiableList(Arrays.<Object>asList(toolSet, aiCodeToolSet));
    }

    private ResolvedComponents resolveComponents() {
        AvailableMaps availableMaps = this.availableMaps != null ? this.availableMaps : createAvailableMaps();
        TextController textController = this.textController != null ? this.textController : createTextController();
        AttributeController attributeController = this.attributeController != null ? this.attributeController
            : createAttributeController();
        IconController iconController = this.iconController != null ? this.iconController : createIconController();
        MMapController mapController = this.mapController != null ? this.mapController : createMapController();
        NodeContentFactories nodeContentFactories = createNodeContentFactories(textController, attributeController,
            iconController);
        GetApiDocumentationTool getApiDocumentationTool = new GetApiDocumentationTool(
            availableMaps, mapController, textController);
        return new ResolvedComponents(
            availableMaps,
            textController,
            nodeContentFactories,
            mapController,
            getApiDocumentationTool);
    }

    private AIToolSet createBaseToolSet(ResolvedComponents resolvedComponents) {
        return new AIToolSet(
            toolCallSummaryHandler,
            resolvedComponents.availableMaps,
            mapAccessListener,
            resolvedComponents.textController,
            resolvedComponents.nodeContentFactories,
            resolvedComponents.mapController,
            resolvedComponents.getApiDocumentationTool,
            toolCaller);
    }

    private AiCodeHostService effectiveCodeHostService() {
        if (codeHostService != null) {
            return codeHostService;
        }
        return new AiCodeHostService() {
            @Override
            public ReadCodeResponse readCode(ReadCodeRequest request) {
                ScriptHost host = request == null ? null : request.getHost();
                if (host == null) {
                    throw new IllegalArgumentException("host is required when codeId is absent.");
                }
                if (host != ScriptHost.ATTACHED_EDITOR) {
                    throw new IllegalStateException("AI code host is not implemented yet.");
                }
                return new ReadCodeResponse(
                    null,
                    ScriptHost.ATTACHED_EDITOR,
                    null,
                    CodeLifecycleStatus.NO_CODE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            }

            @Override
            public WriteCodeResponse writeCode(WriteCodeRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }

            @Override
            public CompileCodeResponse compileCode(CompileCodeRequest request) {
                throw new IllegalStateException("No editor is attached.");
            }
        };
    }

    private AvailableMaps createAvailableMaps() {
        return new AvailableMaps(new ControllerMapModelProvider());
    }

    private TextController createTextController() {
        ModeController modeController = requireModeController();
        TextController textController = modeController.getExtension(TextController.class);
        if (textController == null) {
            throw new IllegalStateException("Text controller is not available.");
        }
        return textController;
    }

    private AttributeController createAttributeController() {
        ModeController modeController = requireModeController();
        AttributeController attributeController = modeController.getExtension(AttributeController.class);
        if (attributeController == null) {
            throw new IllegalStateException("Attribute controller is not available.");
        }
        return attributeController;
    }

    private IconController createIconController() {
        ModeController modeController = requireModeController();
        IconController iconController = modeController.getExtension(IconController.class);
        if (iconController == null) {
            throw new IllegalStateException("Icon controller is not available.");
        }
        return iconController;
    }

    private MMapController createMapController() {
        ModeController modeController = requireModeController();
        MapController mapController = modeController.getMapController();
        if (!(mapController instanceof MMapController)) {
            throw new IllegalStateException("Map controller is not available.");
        }
        return (MMapController) mapController;
    }

    private ModeController requireModeController() {
        ModeController modeController = Controller.getCurrentModeController();
        if (modeController == null) {
            throw new IllegalStateException("Current mode controller is not available.");
        }
        return modeController;
    }

    private NodeContentFactories createNodeContentFactories(TextController textController,
                                                            AttributeController attributeController,
                                                            IconController iconController) {
        EnglishTextProvider englishTextProvider = new DefaultEnglishTextProvider();
        IconDescriptionResolver iconDescriptionResolver = new IconDescriptionResolver(englishTextProvider);
        NodeContentItemReader nodeContentItemReader = createNodeContentItemReader(
            textController, attributeController, iconController, iconDescriptionResolver);
        return new NodeContentFactories(nodeContentItemReader, iconDescriptionResolver);
    }

    private NodeContentItemReader createNodeContentItemReader(TextController textController,
                                                              AttributeController attributeController,
                                                              IconController iconController,
                                                              IconDescriptionResolver iconDescriptionResolver) {
        TextualContentReader textualContentReader = new TextualContentReader(textController);
        AttributesContentReader attributesContentReader = new AttributesContentReader(attributeController, textController);
        TagsContentReader tagsContentReader = new TagsContentReader(iconController);
        IconsContentReader iconsContentReader = new IconsContentReader(iconDescriptionResolver, iconController);
        EditableContentReader editableContentReader = new EditableContentReader(
            textController, iconDescriptionResolver, new ContentTypeConverter());
        NodeStyleContentReader nodeStyleContentReader = new NodeStyleContentReader();
        NodeContentReader nodeContentReader = new NodeContentReader(
            textualContentReader, attributesContentReader, tagsContentReader, iconsContentReader,
            nodeStyleContentReader, editableContentReader);
        return new NodeContentItemReader(nodeContentReader);
    }

    private static final class ResolvedComponents {
        private final AvailableMaps availableMaps;
        private final TextController textController;
        private final NodeContentFactories nodeContentFactories;
        private final MMapController mapController;
        private final GetApiDocumentationTool getApiDocumentationTool;

        private ResolvedComponents(AvailableMaps availableMaps,
                                   TextController textController,
                                   NodeContentFactories nodeContentFactories,
                                   MMapController mapController,
                                   GetApiDocumentationTool getApiDocumentationTool) {
            this.availableMaps = availableMaps;
            this.textController = textController;
            this.nodeContentFactories = nodeContentFactories;
            this.mapController = mapController;
            this.getApiDocumentationTool = getApiDocumentationTool;
        }
    }
}
