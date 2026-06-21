package org.freeplane.plugin.ai.chat.ui;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.InputMap;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.freeplane.plugin.ai.prompt.AiPrompt;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SlashPromptCompletionControllerTest {

    @Test
    public void refresh_showsAllCandidatesForEmptyLeadingSlashAndFiltersEditedQuery() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();

            fixture.inputArea.setText("/");
            fixture.inputArea.setCaretPosition(1);
            fixture.uut.refresh();
            assertThat(fixture.uut.candidateCount()).isEqualTo(2);
            assertThat(fixture.uut.selectedCandidateName()).isEqualTo("Summarize branch");

            fixture.inputArea.setText("/node");
            fixture.inputArea.setCaretPosition(5);
            fixture.uut.refresh();
            assertThat(fixture.uut.candidateCount()).isEqualTo(1);
            assertThat(fixture.uut.selectedCandidateName()).isEqualTo("Rewrite node");

            fixture.inputArea.setText("/t");
            fixture.inputArea.setCaretPosition(2);
            fixture.uut.refresh();
            assertThat(fixture.uut.candidateCount()).isZero();
        });
    }

    @Test
    public void acceptSelectedCandidate_replacesLeadingQueryAndPreservesSuffix() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.inputArea.setText("/sum later");
            fixture.inputArea.setCaretPosition(4);
            fixture.uut.refresh();

            fixture.uut.acceptSelectedCandidate();

            assertThat(fixture.inputArea.getText()).isEqualTo("/Summarize branch later");
            assertThat(fixture.inputArea.getCaretPosition()).isEqualTo("/Summarize branch".length());
        });
    }

    @Test
    public void popupBoundsForCaret_placesPopupAboveInputAndClampsHorizontally() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.inputArea.setSize(160, 40);
            fixture.inputArea.setText("/");
            fixture.inputArea.setCaretPosition(1);
            fixture.uut.refresh();

            Rectangle bounds = fixture.uut.popupBoundsForCaret(new Rectangle2D.Double(500, 20, 1, 16));

            assertThat(bounds.height).isGreaterThan(0);
            assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(0);
            assertThat(bounds.x).isEqualTo(Math.max(0, fixture.inputArea.getWidth() - bounds.width));
        });
    }

    @Test
    public void popupControlKeysNavigateAcceptAndCloseWithoutActionMapRebinding() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.inputArea.setText("/");
            fixture.inputArea.setCaretPosition(1);
            fixture.uut.refresh();

            KeyEvent down = keyPressed(fixture.inputArea, KeyEvent.VK_DOWN, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(down)).isTrue();
            assertThat(down.isConsumed()).isTrue();
            assertThat(fixture.uut.selectedCandidateIndex()).isEqualTo(1);

            KeyEvent up = keyPressed(fixture.inputArea, KeyEvent.VK_UP, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(up)).isTrue();
            assertThat(up.isConsumed()).isTrue();
            assertThat(fixture.uut.selectedCandidateIndex()).isZero();

            KeyEvent tab = keyPressed(fixture.inputArea, KeyEvent.VK_TAB, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(tab)).isTrue();
            assertThat(tab.isConsumed()).isTrue();
            assertThat(fixture.inputArea.getText()).isEqualTo("/Summarize branch ");

            KeyEvent typedTab = keyTyped(fixture.inputArea, '\t');
            assertThat(fixture.uut.interceptKeyTyped(typedTab)).isTrue();
            assertThat(typedTab.isConsumed()).isTrue();

            fixture.inputArea.setText("/");
            fixture.inputArea.setCaretPosition(1);
            fixture.uut.refresh();
            KeyEvent escape = keyPressed(fixture.inputArea, KeyEvent.VK_ESCAPE, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(escape)).isTrue();
            assertThat(escape.isConsumed()).isTrue();
            assertThat(fixture.inputArea.getText()).isEqualTo("/");
        });
    }

    @Test
    public void enterAcceptsSelectedCandidateAndConsumesFollowingTypedNewline() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.inputArea.setText("/node");
            fixture.inputArea.setCaretPosition(5);
            fixture.uut.refresh();

            KeyEvent enter = keyPressed(fixture.inputArea, KeyEvent.VK_ENTER, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(enter)).isTrue();
            assertThat(enter.isConsumed()).isTrue();
            assertThat(fixture.inputArea.getText()).isEqualTo("/Rewrite node ");

            KeyEvent typedEnter = keyTyped(fixture.inputArea, '\n');
            assertThat(fixture.uut.interceptKeyTyped(typedEnter)).isTrue();
            assertThat(typedEnter.isConsumed()).isTrue();
        });
    }

    @Test
    public void popupKeyInterceptorLeavesHiddenPopupAndEditingKeysToInputArea() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();
            fixture.inputArea.setText("/");
            fixture.inputArea.setCaretPosition(1);
            fixture.uut.refresh();

            KeyEvent hiddenEnter = keyPressed(fixture.inputArea, KeyEvent.VK_ENTER, 0);
            assertThat(fixture.uut.interceptKeyPressed(hiddenEnter)).isFalse();
            assertThat(hiddenEnter.isConsumed()).isFalse();

            KeyEvent modifiedEnter = keyPressed(fixture.inputArea, KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(modifiedEnter)).isFalse();
            assertThat(modifiedEnter.isConsumed()).isFalse();

            KeyEvent left = keyPressed(fixture.inputArea, KeyEvent.VK_LEFT, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(left)).isFalse();
            assertThat(left.isConsumed()).isFalse();

            KeyEvent backspace = keyPressed(fixture.inputArea, KeyEvent.VK_BACK_SPACE, 0);
            assertThat(fixture.uut.handleVisiblePopupKeyPressed(backspace)).isFalse();
            assertThat(backspace.isConsumed()).isFalse();
        });
    }

    @Test
    public void installDoesNotAddPopupSpecificInputOrActionMapEntries() throws Exception {
        runOnEdt(() -> {
            JTextArea inputArea = new JTextArea();
            InputMap inputMap = inputArea.getInputMap();
            KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
            KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            KeyStroke up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
            KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
            KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
            Object enterAction = inputMap.get(enter);
            Object escapeAction = inputMap.get(escape);
            Object upAction = inputMap.get(up);
            Object downAction = inputMap.get(down);
            Object tabAction = inputMap.get(tab);

            new SlashPromptCompletionController(
                inputArea,
                () -> Arrays.<AiPrompt>asList(),
                new PromptReferenceResolver()).install();

            assertThat(inputMap.get(enter)).isEqualTo(enterAction);
            assertThat(inputMap.get(escape)).isEqualTo(escapeAction);
            assertThat(inputMap.get(up)).isEqualTo(upAction);
            assertThat(inputMap.get(down)).isEqualTo(downAction);
            assertThat(inputMap.get(tab)).isEqualTo(tabAction);
            assertThat(inputArea.getActionMap().get("acceptPromptCompletion")).isNull();
            assertThat(inputArea.getActionMap().get("closePromptCompletion")).isNull();
            assertThat(inputArea.getActionMap().get("selectNextPromptCompletion")).isNull();
            assertThat(inputArea.getActionMap().get("selectPreviousPromptCompletion")).isNull();
        });
    }

    @Test
    public void promptRelevantEditNotifiesPreviewRefreshListener() throws Exception {
        AtomicInteger refreshCount = new AtomicInteger();
        Fixture fixture = createFixture(refreshCount);

        SwingUtilities.invokeAndWait(() -> fixture.inputArea.setText("/Summarize branch"));
        flushEdt();

        assertThat(refreshCount.get()).isGreaterThan(0);
    }

    @Test
    public void underlineReflectsCurrentPromptRecognition() throws Exception {
        runOnEdt(() -> {
            Fixture fixture = new Fixture();

            fixture.inputArea.setText("/Summarize branch rest");
            fixture.inputArea.setCaretPosition(fixture.inputArea.getText().length());
            fixture.uut.refresh();
            assertThat(fixture.uut.underlineCount()).isEqualTo(1);

            fixture.inputArea.setText("/Summarizes branch rest");
            fixture.inputArea.setCaretPosition(fixture.inputArea.getText().length());
            fixture.uut.refresh();
            assertThat(fixture.uut.underlineCount()).isZero();
        });
    }

    private static KeyEvent keyPressed(JTextArea inputArea, int keyCode, int modifiersEx) {
        return new KeyEvent(
            inputArea,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            modifiersEx,
            keyCode,
            KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent keyTyped(JTextArea inputArea, char keyChar) {
        return new KeyEvent(
            inputArea,
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_UNDEFINED,
            keyChar);
    }

    private static Fixture createFixture(AtomicInteger refreshCount) throws Exception {
        AtomicReference<Fixture> fixture = new AtomicReference<Fixture>();
        SwingUtilities.invokeAndWait(() -> fixture.set(new Fixture(refreshCount)));
        return fixture.get();
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static void runOnEdt(ThrowingRunnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    runnable.run();
                }
                catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException && cause.getCause() instanceof Exception) {
                throw (Exception) cause.getCause();
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw error;
        }
    }

    private static class Fixture {
        private final JTextArea inputArea = new JTextArea();
        private final List<AiPrompt> prompts = Arrays.asList(
            new AiPrompt("Summarize branch", "Prompt", false),
            new AiPrompt("Rewrite node", "Other", false));
        private final SlashPromptCompletionController uut;

        private Fixture() {
            this(new AtomicInteger());
        }

        private Fixture(AtomicInteger refreshCount) {
            uut = new SlashPromptCompletionController(
                inputArea,
                () -> prompts,
                new PromptReferenceResolver(),
                refreshCount::incrementAndGet);
            uut.install();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
