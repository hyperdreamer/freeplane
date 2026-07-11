package org.freeplane.plugin.ai.model.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.accessibility.Accessible;
import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.ComboPopup;

import org.freeplane.plugin.ai.model.AIModelDescriptor;
import org.freeplane.plugin.ai.model.AIProviderConfiguration;
import org.junit.Test;

public class AIModelSelectorTest {
    private final AIModelDescriptor gemini = descriptor(
        "gemini", "gemini-2.5-flash", "Gemini: gemini-2.5-flash");
    private final AIModelDescriptor openRouter = descriptor(
        "openrouter", "openai/gpt-4.1-mini", "OpenRouter: openai/gpt-4.1-mini");

    @Test
    public void normalState_displaysSelectedModel() {
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.emptyList());

        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());

        assertThat(uut.isFiltering()).isFalse();
        assertThat(uut.getModelSelectionComboBox().getSelectedItem()).isEqualTo(gemini);
        assertThat(editor(uut).getText()).isEqualTo(gemini.getDisplayName());
    }

    @Test
    public void normalState_withoutSelectionDisplaysTranslatedPlaceholder() {
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.emptyList());

        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), null);

        assertThat(uut.getModelSelectionComboBox().getSelectedItem()).isNull();
        assertThat(editor(uut).getText()).isEqualTo("No model selected");
    }

    @Test
    public void openingPopup_restoresAndSelectsSharedFilter() throws Exception {
        AIModelFilterState filterState = new AIModelFilterState();
        filterState.setFilterText("gpt");
        AIModelSelector uut = selector(filterState, Collections.emptyList());
        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());

        openPopup(uut);

        JTextField editor = editor(uut);
        assertThat(uut.isFiltering()).isTrue();
        assertThat(editor.getText()).isEqualTo("gpt");
        assertThat(selectionBounds(editor)).containsExactly(0, 3);
        assertThat(displayedModels(uut)).containsExactly(openRouter);
    }

    @Test
    public void typingInNormalEditor_startsFiltering() throws Exception {
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.emptyList());
        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());
        ComboBoxModel<AIModelDescriptor> originalModel = uut.getModelSelectionComboBox().getModel();

        setEditorText(uut, "gpt");

        assertThat(uut.getModelSelectionComboBox().getModel()).isSameAs(originalModel);
        assertThat(uut.isFiltering()).isTrue();
        assertThat(editor(uut).getText()).isEqualTo("gpt");
        assertThat(displayedModels(uut)).containsExactly(gemini, openRouter);

        applyPendingFilter(uut);

        assertThat(uut.getModelSelectionComboBox().getModel()).isSameAs(originalModel);
        assertThat(displayedModels(uut)).containsExactly(openRouter);
    }

    @Test
    public void filtering_matchesCompleteDisplayNameIgnoringCase() throws Exception {
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.emptyList());
        uut.setAvailableModelDescriptors(Arrays.asList(openRouter, gemini), gemini.getSelectionValue());
        openPopup(uut);

        setEditorText(uut, "ROUTER: OPENAI/");
        applyPendingFilter(uut);
        assertThat(displayedModels(uut)).containsExactly(openRouter);

        setEditorText(uut, "");
        applyPendingFilter(uut);
        assertThat(displayedModels(uut)).containsExactly(gemini, openRouter);

        setEditorText(uut, "no match");
        applyPendingFilter(uut);
        assertThat(displayedModels(uut)).isEmpty();
    }

    @Test
    public void filtering_preservesAlwaysVisibleOptions() throws Exception {
        AIModelDescriptor currentModel = AIModelDescriptor.useCurrentModelOption("Use current model");
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.singletonList(currentModel));
        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), "");
        openPopup(uut);

        setEditorText(uut, "no match");
        applyPendingFilter(uut);

        assertThat(displayedModels(uut)).containsExactly(currentModel);
    }

    @Test
    public void closingPopup_restoresSelectedModelWithoutChangingFilter() throws Exception {
        AIModelFilterState filterState = new AIModelFilterState();
        AIModelSelector uut = selector(filterState, Collections.emptyList());
        AtomicInteger notifications = new AtomicInteger();
        uut.setExplicitModelSelectionListener(descriptor -> notifications.incrementAndGet());
        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());
        openPopup(uut);
        setEditorText(uut, "gpt");

        closePopup(uut);
        applyPendingFilter(uut);

        assertThat(displayedModels(uut)).containsExactly(gemini, openRouter);
        assertThat(uut.getSelectedModel()).isEqualTo(gemini);
        assertThat(editor(uut).getText()).isEqualTo(gemini.getDisplayName());
        assertThat(filterState.getFilterText()).isEqualTo("gpt");
        assertThat(notifications).hasValue(0);
    }

    @Test
    public void instances_shareInjectedFilterState() throws Exception {
        AIModelFilterState filterState = new AIModelFilterState();
        AIModelSelector first = selector(filterState, Collections.emptyList());
        AIModelSelector second = selector(filterState, Collections.emptyList());
        first.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());
        second.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());
        openPopup(first);
        setEditorText(first, "openrouter");

        openPopup(second);

        assertThat(editor(second).getText()).isEqualTo("openrouter");
        assertThat(displayedModels(second)).containsExactly(openRouter);
    }

    @Test
    public void arrowKeysHighlightEveryFilteredRowAndEnterCommitsOnlyHighlightedModel() throws Exception {
        AIModelDescriptor alpha = descriptor("provider", "alpha", "Provider: match alpha");
        AIModelDescriptor beta = descriptor("provider", "beta", "Provider: match beta");
        AIModelDescriptor gamma = descriptor("provider", "gamma", "Provider: match gamma");
        AIModelSelector uut = selector(new AIModelFilterState(), Collections.emptyList());
        AtomicReference<AIModelDescriptor> selected = new AtomicReference<AIModelDescriptor>();
        AtomicInteger notifications = new AtomicInteger();
        uut.setExplicitModelSelectionListener(descriptor -> {
            selected.set(descriptor);
            notifications.incrementAndGet();
        });
        uut.setAvailableModelDescriptors(
            Arrays.asList(gemini, alpha, beta, gamma),
            gemini.getSelectionValue());
        openPopup(uut);
        setEditorText(uut, "match");

        performEditorAction(uut, KeyEvent.VK_DOWN);
        assertThat(highlightedPopupModel(uut)).isEqualTo(alpha);
        performEditorAction(uut, KeyEvent.VK_DOWN);
        assertThat(highlightedPopupModel(uut)).isEqualTo(beta);
        performEditorAction(uut, KeyEvent.VK_DOWN);
        assertThat(highlightedPopupModel(uut)).isEqualTo(gamma);
        performEditorAction(uut, KeyEvent.VK_UP);
        assertThat(highlightedPopupModel(uut)).isEqualTo(beta);
        performEditorAction(uut, KeyEvent.VK_UP);
        assertThat(highlightedPopupModel(uut)).isEqualTo(alpha);
        performEditorAction(uut, KeyEvent.VK_DOWN);
        performEditorAction(uut, KeyEvent.VK_DOWN);
        assertThat(highlightedPopupModel(uut)).isEqualTo(gamma);

        assertThat(uut.getSelectedModel()).isEqualTo(gemini);
        assertThat(selected.get()).isNull();
        assertThat(notifications).hasValue(0);
        assertThat(editor(uut).getText()).isEqualTo("match");

        performEditorAction(uut, KeyEvent.VK_ENTER);

        assertThat(uut.getSelectedModel()).isEqualTo(gamma);
        assertThat(selected).hasValue(gamma);
        assertThat(notifications).hasValue(1);
        assertThat(editor(uut).getText()).isEqualTo(gamma.getDisplayName());
    }

    @Test
    public void explicitFilteredSelection_notifiesOwnerAndRetainsFilter() throws Exception {
        AIModelFilterState filterState = new AIModelFilterState();
        AIModelSelector uut = selector(filterState, Collections.emptyList());
        AtomicReference<AIModelDescriptor> selected = new AtomicReference<AIModelDescriptor>();
        AtomicInteger notifications = new AtomicInteger();
        uut.setExplicitModelSelectionListener(descriptor -> {
            selected.set(descriptor);
            notifications.incrementAndGet();
        });
        uut.setAvailableModelDescriptors(Arrays.asList(gemini, openRouter), gemini.getSelectionValue());
        openPopup(uut);
        setEditorText(uut, "gpt");
        applyPendingFilter(uut);
        assertThat(uut.getModelSelectionComboBox().getSelectedItem()).isNull();

        SwingUtilities.invokeAndWait(
            () -> uut.getModelSelectionComboBox().setSelectedItem(openRouter));

        assertThat(uut.getModelSelectionComboBox().getSelectedItem()).isEqualTo(openRouter);
        assertThat(selected).hasValue(openRouter);
        assertThat(notifications).hasValue(1);
        assertThat(filterState.getFilterText()).isEqualTo("gpt");
    }

    private AIModelSelector selector(AIModelFilterState filterState,
                                     List<AIModelDescriptor> alwaysVisibleOptions) {
        AIProviderConfiguration configuration = mock(AIProviderConfiguration.class);
        when(configuration.hasConfiguredProvider()).thenReturn(true);
        return new AIModelSelector(
            configuration,
            filterState,
            alwaysVisibleOptions,
            AIModelDescriptor::getDisplayName,
            () -> "No model selected");
    }

    private void openPopup(AIModelSelector selector) {
        JComboBox<AIModelDescriptor> comboBox = selector.getModelSelectionComboBox();
        PopupMenuEvent event = new PopupMenuEvent(comboBox);
        for (PopupMenuListener listener : comboBox.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(event);
        }
    }

    private void setEditorText(AIModelSelector selector, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> editor(selector).setText(text));
    }

    private void applyPendingFilter(AIModelSelector selector) throws Exception {
        SwingUtilities.invokeAndWait(selector::applyPendingFilter);
    }

    private void closePopup(AIModelSelector selector) {
        JComboBox<AIModelDescriptor> comboBox = selector.getModelSelectionComboBox();
        PopupMenuEvent event = new PopupMenuEvent(comboBox);
        for (PopupMenuListener listener : comboBox.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeInvisible(event);
        }
    }

    private JTextField editor(AIModelSelector selector) {
        return (JTextField) selector.getModelSelectionComboBox().getEditor().getEditorComponent();
    }

    private List<AIModelDescriptor> displayedModels(AIModelSelector selector) {
        JComboBox<AIModelDescriptor> comboBox = selector.getModelSelectionComboBox();
        return IntStream.range(0, comboBox.getItemCount())
            .mapToObj(comboBox::getItemAt)
            .collect(Collectors.toList());
    }

    private int[] selectionBounds(JTextField editor) throws Exception {
        int[] bounds = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            bounds[0] = editor.getSelectionStart();
            bounds[1] = editor.getSelectionEnd();
        });
        return bounds;
    }

    private void performEditorAction(AIModelSelector selector, int keyCode) throws Exception {
        JTextField editor = editor(selector);
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0);
        Object actionKey = editor.getInputMap().get(keyStroke);
        Action action = editor.getActionMap().get(actionKey);
        SwingUtilities.invokeAndWait(
            () -> action.actionPerformed(
                new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, actionKey.toString())));
    }

    private Object highlightedPopupModel(AIModelSelector selector) {
        JComboBox<AIModelDescriptor> comboBox = selector.getModelSelectionComboBox();
        Accessible popup = comboBox.getUI().getAccessibleChild(comboBox, 0);
        JList<Object> popupList = ((ComboPopup) popup).getList();
        return popupList.getSelectedValue();
    }

    private AIModelDescriptor descriptor(String provider, String model, String displayName) {
        return new AIModelDescriptor(provider, model, displayName, false);
    }
}
