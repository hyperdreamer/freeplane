# Task: Design and implement a unified chat filtering algorithm
- **Task Identifier:** 2026-06-07-unified-chat-filtering-algorithm

- **Scope:** Define a new unified algorithm for chat filtering across
  all consumers of filtered chat in Freeplane AI chat, then implement
  that algorithm in the same task after the design and test
  specification are accepted. The task file itself must first contain
  the algorithm as connected prose and diagrams, then a text-form test
  suite derived from that algorithm, and only then proceed to code
  replacement and executable tests.

- **Motivation:** Freeplane needs a stable, code-independent filteringFilteredChatMessages
  contract that can be reviewed and discussed without reading Java.
  The current filtering has already produced at least one real bug:
  MCP tool-call summaries were hidden even though no compaction should
  have hidden them. That makes a clean redesign and reimplementation
  preferable to another local repair. The contract should be precise
  enough to support a clean reimplementation and an executable test
  suite in the same governed work item.

- **Scenario:**
  - A user has a chat with Freeplane AI.
  - The chat may contain:
    - user messages
    - assistant messages
    - chat-owned tool activity
    - MCP messages
  - Freeplane keeps one running chat, but does not show or reuse all of
    it at once.
  - Freeplane uses a **chat window** over the chat.
  - The chat window moves only at semantic-turn boundaries.
  - Messages before the chat window stay in the chat but fall outside
    the current window.
  - Chat-owned tool activity may appear as grouped request, result, and
    summary messages inside the chat.
  - Freeplane also uses a **tool window** for chat-owned tool activity
    inside the chat window.
  - Chat-owned tool messages outside the tool window may be hidden even
    when nearby non-tool messages remain inside the chat window.
  - MCP messages are governed only by the chat window:
    - if an MCP message is inside the chat window, it is shown;
    - the tool window does not narrow it further.
  - Freeplane derives 3 projections from the same chat:
    - panel projection
    - model projection
    - transcript projection
  - The 3 projections start from the same chat-window and tool-window
    rules, then may differ in their final kept content.
  - While Freeplane stays open:
    - the user may switch to another chat and back;
    - the same chat keeps its current window state for that running
      session.
  - Tool activity shown in the chat is live observability, not durable
    truth about the current map state.
  - After Freeplane restart:
    - the restored chat contains only durable transcript content;
    - live tool activity from the earlier session is not
      reconstructed.

- **Glossary:**
  - **Chat:** one running AI interaction in Freeplane.
    - A chat contains messages and related activity.
    - Panel projection, model projection, and transcript projection all
      come from the same chat.
  - **ChatMessage:** one `dev.langchain4j.data.message.ChatMessage`
    value from LangChain4j.
    - Freeplane stores chat history as `ChatMessage` objects.
    - In LangChain4j 1.15.1, `ChatMessage` is an interface with 5
      direct library implementations by type:
      `SystemMessage`, `UserMessage`, `AiMessage`,
      `ToolExecutionResultMessage`, and `CustomMessage`.
    - Freeplane AI currently uses the first 4 of those and also uses
      Freeplane-specific subclasses of `SystemMessage`,
      `UserMessage`, and `AiMessage`.
    - Filtering rules classify these technical message objects into
      the domain categories used by this task.
  - **Semantic turn:** a user/assistant turn boundary used to move the
    chat window.
    - The chat window moves only at semantic-turn boundaries.
    - A semantic turn is part of ordinary chat behavior, not tool-only
      detail.
  - **Chat window:** the currently used part of the chat.
    - Messages before the chat window stay in the chat but fall outside
      the current window.
    - MCP messages inside the chat window remain shown.
  - **Chat-owned tool activity:** tool-related messages produced by the
    chat itself.
    - Chat-owned tool activity may appear as grouped request, result,
      and summary messages.
    - Chat-owned tool activity is live observability, not durable truth
      about the current map state.
  - **Chat-owned tool activity group:** one chat-owned tool request,
    its following tool results, and its following chat-owned tool
    summaries until the next non-tool entry in that flow.
    - The tool window keeps or hides chat-owned tool activity in these
      grouped units.
    - MCP messages are not part of chat-owned tool activity groups.
  - **Tool window:** the currently used part of chat-owned tool
    activity inside the chat window.
    - Chat-owned tool messages outside the tool window may be hidden
      even when nearby non-tool messages remain inside the chat window.
    - The tool window does not narrow MCP messages.
  - **MCP message:** a chat message that shows MCP activity.
    - An MCP message is shown when it is inside the chat window.
    - An MCP message is not narrowed further by the tool window.
  - **Projection:** one consumer-specific view derived from the same
    chat.
    - Panel projection, model projection, and transcript projection are
      the 3 projections.
    - The 3 projections start from the same chat-window and tool-window
      rules, then may differ in their final kept content.
  - **Panel projection:** the projection shown in the chat panel.
    - The user reads this projection directly in the UI.
  - **Model projection:** the projection reused as model-visible
    context for later AI requests.
    - This projection may omit messages that stay visible in the panel
      projection.
  - **Transcript projection:** the projection persisted as durable
    transcript content.
    - A restored chat is reconstructed from transcript projection, not
      from live tool activity.

  ```mermaid
  flowchart LR
      subgraph chat[Chat concepts]
          C[Chat]
          ST[Semantic turn]
          CW[Chat window]
          CTA[Chat-owned tool activity]
          CTAG[Chat-owned tool activity group]
          TW[Tool window]
          MCP[MCP message]
      end

      subgraph projections[Projections]
          PP[Panel projection]
          MP[Model projection]
          TP[Transcript projection]
      end

      C -->|is filtered by| CW
      ST -->|moves| CW
      CW -->|contains| CTA
      CTA -->|is grouped as| CTAG
      TW -->|keeps or hides| CTAG
      CW -->|shows when inside window| MCP
      TW -->|does not narrow| MCP
      CW -->|constrains| PP
      CW -->|constrains| MP
      CW -->|constrains| TP
      TW -->|further constrains chat-owned tool activity in| PP
      TW -->|further constrains chat-owned tool activity in| MP
      TW -->|further constrains chat-owned tool activity in| TP
  ```

  ```mermaid
  flowchart LR
      subgraph technical[ChatMessage hierarchy]
          CM[ChatMessage]
          SM[SystemMessage]
          UM[UserMessage]
          AM[AiMessage]
          TRM[ToolExecutionResultMessage]
          CUM[CustomMessage]
      end

      subgraph task[Task filtering concepts]
          CTA[Chat-owned tool activity]
          MCP[MCP message]
      end

      CM -->|may be implemented by| SM
      CM -->|may be implemented by| UM
      CM -->|may be implemented by| AM
      CM -->|may be implemented by| TRM
      CM -->|may be implemented by| CUM
      CM -->|is classified into task concepts such as| CTA
      CM -->|is classified into task concepts such as| MCP
  ```

- **Constraints:**
  - Do not describe or analyze any old or current implementation state
    in the canonical design sections.
  - Do not treat current code structure, method names, or local control
    flow as design anchors.
  - The artifact must define one unified filtering contract for all of
    these consumers:
    - chat-panel rendered history,
    - model-visible chat memory,
    - transcript persistence.
  - The algorithm description must be understandable without reading
    code.
  - The design and text-form test specification must live in this task
    file, not in a separate document.
  - The text-form test suite must be derived from the algorithm, not
    from implementation knowledge.
  - New abstractions in Design must be grounded in Scenario, Glossary,
    and the Mermaid visual glossary in this task file.
  - Tool activity is live observability, not durable persisted truth
    about current map state.
  - Tool activity must not be persisted to disk across Freeplane
    restart.
  - Tool activity may remain visible within the same running Freeplane
    session when the user switches between chats.
  - Restored sessions should not reconstruct tool activity or any
    dedicated persisted tool-activity metadata.
  - If the model needs restore-time transparency, use only a generic
    restored-transcript note, not tool-specific restoration.
  - Implementation belongs to this same task, not to a separate later
    task, unless the user explicitly changes that decision later.
  - No production-code changes before the design and text-form test
    suite are reviewed and accepted.

- **Briefing:** Relevant domain concepts already visible in the
  Freeplane AI chat problem space include ordinary user and assistant
  messages, chat-owned tool activity, MCP messages, chat windows, tool
  windows, boundary markers, and projections with different visibility
  needs. Relevant current ownership points for research include
  `AssistantProfileChatMemory`, `VisibleContextSelector`,
  `ChatMemoryProjectionBuilder`, `ChatTurnTracker`,
  `ChatMemoryViewState`, `ChatRequestFlow`, `AIChatPanel`, and
  `TranscriptMemoryMapper`. This task should define a clean filtering
  model for those concepts in domain terms only, then implement that
  model.

- **Research:**
  - Freeplane AI chat has multiple history consumers with different
    needs: user-facing rendered history, model-visible memory, and
    persisted transcript state.
  - Current chat memory is centered on `AssistantProfileChatMemory`,
    which stores an ordered `conversationMessages` list plus view-state
    information such as the active window start and hidden historical
    tool cycles.
  - LangChain4j core 1.15.1 defines `ChatMessage` as an interface with
    `type()` returning `ChatMessageType`. The direct library message
    implementations are `SystemMessage`, `UserMessage`, `AiMessage`,
    `ToolExecutionResultMessage`, and `CustomMessage`.
  - Freeplane AI currently uses `SystemMessage`, `UserMessage`,
    `AiMessage`, and `ToolExecutionResultMessage`, plus these
    Freeplane-specific subclasses:
    - `AssistantProfileSwitchMessage extends UserMessage`
    - `AssistantProfileInstructionMessage extends UserMessage`
    - `AutomaticCodeStatusMessage extends UserMessage`
    - `InstructionAckMessage extends AiMessage`
    - `GeneralSystemMessage extends SystemMessage`
    - `RemovedForSpaceSystemMessage extends SystemMessage`
    - `TranscriptHiddenSystemMessage extends SystemMessage`
    - `ToolCallSummaryMessage extends SystemMessage`
  - Current turn tracking is handled separately from rendering. In the
    current code, `ChatTurnTracker` ends turns on completed assistant
    responses and uses those boundaries for compaction, undo/redo, and
    active-range calculations.
  - Current filtering state is split into at least three layers:
    - active-range selection (`activeStartIndex`, current turn range,
      conversation end),
    - inside-window inclusion masking for hidden historical tool cycles,
      and
    - projection-specific suppression when building panel, model, or
      transcript outputs.
  - Current projection building is centralized in
    `ChatMemoryProjectionBuilder`, which already has separate output
    paths for rendered history, model-visible messages, and transcript
    entries.
  - Current rendered-history output can include tool summaries and a
    removed-for-space boundary marker, and prepends
    `GeneralSystemMessage` when present.
  - Current model-visible chat memory prepends
    `GeneralSystemMessage` when present, excludes tool summaries,
    rewrites some control or system items into model-facing user
    instructions, and replaces raw `AssistantProfileSwitchMessage`
    visibility with at most one derived latest profile instruction.
  - Current transcript persistence excludes
    `GeneralSystemMessage` and tool summaries entirely and persists
    only selected roles such as user, assistant, assistant profile
    system, automatic code status, and removed-for-space.
  - Current external MCP summaries enter the system through
    `AIChatPanel.appendExternalToolSummary(...)`. They either join the
    active chat request flow or are appended to a dedicated MCP-summary
    chat session.
  - Current chat-owned tool summaries enter through
    `ChatRequestFlow.onToolCallSummary(...)` and are appended to chat
    memory separately from tool request and tool result messages.
  - Current tool-detail hiding is based on historical tool cycles:
    a tool-request assistant message, its following tool results, and
    following tool summaries are treated as one hideable cycle.
  - Current post-response compaction first tries to hide historical
    tool cycles before moving the active chat window. It computes a new
    visibility selection against `maxTokens / 4` and only advances the
    window by whole completed turns if the selected content still
    exceeds that reset target.
  - Current tool-cycle hiding is token-budget based but session-state
    driven after selection:
    - `VisibleContextSelector.selectVisibleContext(...)` protects the
      newest `protectedRecentTurnCount` turns (default `1`),
      computes a historical tool-token cap as the remaining historical
      budget times `historicalToolTokenShare` (default `0.5`), and
      hides the oldest historical tool cycles until the visible
      historical tool tokens fit that cap.
    - The chosen hidden cycles are then stored in
      `ChatMemoryViewState.hiddenHistoricalToolCycles` and reused by
      all projections until later state changes clear or replace them.
  - Current post-response window movement has a whole-turn retention
    floor:
    - `minimumTurnBlocksToKeep(maxTokens)` keeps at least two recent
      turns when their combined removable-token load fits under full
      `maxTokens`.
    - Otherwise it keeps at least one recent turn even when the
      `maxTokens / 4` reset target cannot be reached.
  - Current start alignment applies an extra summary-specific trim
    after the active window has already been chosen:
    `VisibleContextSelector.alignVisibleStartIndex(...)` skips leading
    `ToolCallSummaryMessage` entries while a later visible entry still
    exists. This can hide MCP summaries or chat-owned tool summaries at
    the front of the visible range without moving the active window.
  - The user has observed this as a real bug: MCP tool-call summaries
    were hidden even though no compaction should have hidden them.
  - Current MCP summaries are stored as `ToolCallSummaryMessage` with
    `ToolCaller.MCP`. They are rendered distinctly in the panel, are
    excluded from model-visible messages and transcript persistence,
    are not counted as removable token load, and are not used as the
    start of a hideable historical tool cycle.
  - Current filtering therefore cannot rely only on LangChain4j
    `ChatMessageType`. Important distinctions such as MCP summary,
    chat-owned tool summary, removed-for-space boundary, transcript
    hidden note, automatic code status, and assistant-profile switch
    are encoded as concrete subclasses or subtype-specific data inside
    the broader `ChatMessage` hierarchy.
  - Current compaction can both move the active window start and hide
    historical tool cycles inside the active window, so the new design
    must define both mechanisms explicitly instead of relying on
    implicit interactions.
  - History contains at least these conceptual message kinds:
    user messages, assistant messages, tool requests, tool results,
    tool summaries, MCP-originated tool summaries, profile or control
    messages, automatic status messages, general system messages, and
    boundary markers.
  - Sequence-sensitive examples that the design must cover include:
    - a summary-only MCP session such as `MCP1`, `MCP2`, `MCP3`;
    - a mixed sequence such as `MCP1`, `MCP2`, `USER1`,
      `ASSISTANT1`, `MCP3`, `USER2`, `ASSISTANT2`, `MCP4`,
      `USER3`, `ASSISTANT3`;
    - the same mixed sequence before and after compaction moves the
      active window forward;
    - cases where panel, model, and transcript outputs are
      intentionally different.
  - The user wants the replacement to be designed top-down, without
    using implementation behavior as the conceptual model.

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.chat" {
    package memory {
      class AssistantProfileChatMemory {
        +messages() : List<ChatMessage>
        +activeConversationRenderEntries() : List<ChatMemoryRenderEntry>
        +transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
        +onResponseTokenUsage() : boolean
      }

      class VisibleContextSelector {
        +currentSelection() : VisibleContextSelection
        +selectVisibleContext() : VisibleContextSelection
      }

      class ChatMemoryProjectionBuilder {
        +buildMessages() : List<ChatMessage>
        +buildRenderEntries() : List<ChatMemoryRenderEntry>
        +buildTranscriptEntries() : List<ChatTranscriptEntry>
      }

      class ChatTurnTracker {
        +rebuildTurnEndIndexes() : List<Integer>
        +activeConversationEndIndex() : int
        +activeTurnRanges() : List<ActiveTurnRange>
      }

      class ChatMemoryViewState {
        +activeStartIndex : int
        +currentTurnCount : int
        +hiddenHistoricalToolCycles : List<HistoricalToolCycle>
      }

      class HistoricalToolCycle {
        +startIndex : int
        +endIndex : int
        +tokenCount : long
      }
    }

    package request {
      class ChatRequestFlow {
        +onToolCallSummary()
      }
    }

    package session {
      class TranscriptMemoryMapper {
        +seedTranscriptWithHiddenExchange()
        +toTranscriptEntries() : List<ChatTranscriptEntry>
      }
    }

    package ui {
      class AIChatPanel {
        +appendExternalToolSummary()
      }
    }

    AssistantProfileChatMemory --> ChatTurnTracker
    AssistantProfileChatMemory --> ChatMemoryViewState
    AssistantProfileChatMemory --> VisibleContextSelector
    AssistantProfileChatMemory --> ChatMemoryProjectionBuilder
    ChatMemoryViewState --> HistoricalToolCycle
    ChatRequestFlow --> AssistantProfileChatMemory
    AIChatPanel --> ChatRequestFlow
    AIChatPanel --> AssistantProfileChatMemory
    TranscriptMemoryMapper --> AssistantProfileChatMemory
  }
  @enduml
  ```

- **Analysis:**
  - Use one unified filtering contract for panel projection, model
    projection, and transcript projection because separate implicit
    rules across consumers are the root source of fragility.
  - Keep the design and text-form test specification in this task file
    because the user explicitly rejected a separate design document.
  - Keep design and implementation in the same governed task because
    the user explicitly rejected splitting implementation into a later
    task.
  - Keep Scenario and Glossary close because domain terms are
    understood through behavior rather than through isolated
    definitions.
  - Ground new abstractions in Scenario, Glossary, and the visual
    glossary because the user wants design names and relations to be
    checked before implementation.
  - Specify the replacement top-down in domain terms because the user
    explicitly rejected implementation-led repair as the design model.
  - Prefer clean replacement over localized repair because the user has
    already hit a real filtering bug and the current logic is too hard
    to reason about safely.
  - Model filtering as a chat window plus a tool window because the
    user explicitly rejected ad hoc message-type trimming as the main
    conceptual model.
  - Define the tool window as an explicit oldest-first token-budget
    rule over historical chat-owned tool activity groups because the
    user chose to preserve the current substantive compaction behavior
    while making it reviewable.
  - Keep MCP messages outside the tool-window budget because they do
    not contribute model-visible context and the user wants them shown
    whenever they remain inside the chat window.
  - Advance the chat window only by whole semantic turns when
    tool-window hiding is still insufficient because the user rejected
    mid-turn window starts.
  - Use one quarter of the configured maximum chat token limit as the
    post-response compaction target because the user chose to preserve
    the current reset-threshold behavior.
  - Keep the current minimum-retained-turn floor because the user
    chose to preserve the current fallback that keeps at least two
    recent turns when their combined compaction token load fits under
    full `maxTokens`, and otherwise keeps at least one recent turn.
  - Express filtering as explicit message-level decisions because the
    user explicitly rejected start-alignment reasoning as the primary
    logic model.
  - Include sequence examples as first-class design material because
    the required behavior depends on order, boundaries, and compaction,
    not only on message category.
  - Treat the text-form suite as the contract from which executable
    tests will later be derived because if the design cannot generate
    precise text cases, it is not ready for implementation.
  - Keep tool activity only as session-local observability because the
    user explicitly rejected persisting it across Freeplane restart as
    stale and misleading map-derived history.
  - Allow tool activity to remain visible when switching chats inside
    the same running Freeplane session because the user explicitly
    expects session-local activity history to remain useful there.
  - Do not persist dedicated tool-activity metadata for restart because
    the user explicitly rejected restore-time tool-specific signaling as
    not meaningful enough.
  - If restore-time model transparency is needed, use only a generic
    restored-transcript warning about possible map drift and prior
    session context.
  - Build one filtered-chat result first, then let each projection
    apply only a final projection-specific mapping step.
  - Track the work as two implementation subtasks within the same
    overall task because the user approved splitting the replacement
    along implementation parts while keeping design finalization inside
    those subtasks.

## Subtask: Implement unified filtering core and projectors
- **Status:** review
- **Scope:** Implement the normalized filtering model, the unified
  filtering path, and the explicit panel/model/transcript projectors,
  while finalizing the class-level design details needed for that
  implementation part.
- **Motivation:** The replacement needs one explicit filtering core and
  one explicit projection layer before later consumer integration can
  be simplified safely.
- **Constraints:**
  - Use the task-level constraints.
  - Do not change the agreed MCP handling, tool-window rule,
    whole-turn movement rule, `maxTokens / 4` target, or minimum
    retained semantic turn count while finishing and implementing this
    subtask's design.
  - Remove obsolete filtering and projection logic instead of keeping
    parallel old and new filtering paths.
- **Briefing:** Use the task-level Scenario, Glossary, Constraints,
  Research, and Analysis as shared context. This subtask owns the
  normalized filtering model, filtering algorithm, projector contracts,
  target class diagram, and executable coverage for those behaviors.
- **Research:** See the task-level Research and current-state class
  diagram.
- **Design:**
  - Keep the design and text-form test specification in this task file.
    Do not split them into a separate design document.
  - Produce the work in this order:
    1. glossary-grounded design-only terms and message categories;
    2. filtering goals and invariants;
    3. one ordered filtering algorithm in connected prose;
    4. diagrams showing stages, decisions, and outputs;
    5. projection-specific output rules for panel, model, and
       transcript;
    6. a text-form test suite derived from the algorithm;
    7. after approval, implementation of the replacement and its
       executable tests.
  - **Glossary grounding and implementation-level terms:**
    - Scenario and Glossary are the authoritative source for domain
      terms used in this Design. Do not redefine Glossary terms here.
    - Filtering operates over the ordered `ChatMessage` sequence for
      the chat. Undo/redo or transcript restore may make the last
      currently eligible chat position earlier than the physical end of
      that sequence.
    - Post-response compaction aims for one quarter of the configured
      maximum chat token limit.
    - For that compaction accounting, count ordinary user and
      assistant text, automatic code status, and selected chat-owned
      tool request/result detail. Do not count MCP messages,
      chat-owned tool summaries, assistant-profile switch markers, or
      projection-only boundary or restore notes.
    - The newest one semantic turn always keeps its chat-owned tool
      activity. If hiding older chat-owned tool activity is still
      insufficient, move the chat window forward by whole semantic
      turns only.
    - If that one-quarter target still cannot be met, preserve the
      newest 2 semantic turns when they fit under full configured
      `maxTokens`; otherwise preserve the newest 1 semantic turn.
    - Content that survives restart is limited to transcript content:
      user text, assistant text, assistant-profile switch state,
      automatic code status, and omitted-earlier-chat boundary state.
      Chat-owned tool activity and MCP messages do not survive restart.
    - Out-of-scope implementation artifacts such as low-level
      acknowledgements may exist in the implementation, but they must
      not drive filtering semantics.
    - **Normalization ownership and timing:**
      `ChatMessageFilter` performs source-chat classification as the
      first step of each filtered-chat recomputation by classifying the
      raw `ChatMessage` sequence and deriving the per-message metadata
      needed for filtering. A recomputation occurs when a caller needs
      fresh filtered chat after chat content or filtering-driving
      state changed. Panel, model, and transcript projectors consume
      that one filtered-chat result and must not reclassify the chat
      independently. Implementations may cache message
      classifications or filtered results between changes, but that
      cache is an optimization, not part of the contract.
    - **Lazy reevaluation and invalidation:** derived filtering state is
      lazy. When user input, assistant output, tool output, MCP
      summary injection, undo/redo, transcript restore, or compaction
      changes canonical chat state or filtering-driving state,
      `AssistantProfileChatMemory` must update that canonical state and
      mark derived filtering state dirty. The next request for
      filtered chat, panel history, model-visible messages,
      transcript entries, or filtering-dependent token accounting must
      recompute from the full relevant `ChatMessage` sequence plus the
      current filtering-driving state. Considering only newly appended
      messages is not sufficient as the semantic contract.
  - **Filtering goals and invariants:**
    - Preserve original chat order for every message that survives
      filtering.
    - Express filtering as explicit per-entry decisions: `SHOW`,
      `DROP_BY_CHAT_WINDOW`, or `SKIP_OUTSIDE_TOOL_WINDOW`.
    - Never use entry category to move the chat window after the chat
      window has already been chosen.
    - Let front-chat removal happen only through window movement.
    - When tool-window hiding is insufficient, advance the chat
      window oldest first by whole semantic turns only.
    - The one-quarter post-response compaction target is best-effort
      and must not force retention below the fallback rule that keeps
      the newest 2 semantic turns when they fit under full configured
      `maxTokens`, and otherwise the newest 1 semantic turn.
    - Allow inside-window skipping only for chat-owned tool activity
      outside the tool window.
    - Hide historical chat-owned tool activity groups oldest first
      when tool-window compaction is needed.
    - Treat MCP messages as standalone session-local observability
      entries. They are never hidden by special start-trimming logic,
      never consume tool-window budget, and disappear only if they
      fall before the chat window start.
    - Build one filtered-chat result first. Panel, model, and
      transcript projections may map or omit messages from that
      result, but they must not each perform separate front-chat
      filtering.
    - Filtering semantics are defined as if recomputed from the full
      relevant `ChatMessage` sequence and current filtering-driving
      state whenever derived state is requested after invalidation.
      Incremental reuse is allowed only as an optimization that
      produces the same result.
    - Do not persist chat-owned tool activity or MCP messages across
      restart.
    - If restored-session model transparency is needed, provide only a
      generic restored-transcript warning. Do not restore tool
      summaries or dedicated tool-activity metadata.
  - **Unified filtering algorithm in connected prose:**
    - Step 1: classify the running `ChatMessage` sequence.
      `ChatMessageFilter` owns this step and runs it once per
      filtered-chat recomputation, before any projection-specific work.
      `ChatEntryCategory` is a filtering classification over
      `ChatMessage` values; it is not the same thing as LangChain4j
      `ChatMessageType`. For each message, classification must derive
      at least category, original order, whether the message belongs to
      a semantic turn, whether it belongs to a chat-owned tool
      activity group, whether it is counted for post-response
      compaction, and whether it survives restart.
    - Step 2: determine the last currently eligible chat position.
      Undo/redo or transcript restore may make this earlier than the
      physical end of the chat.
    - Step 3: compute the post-response compaction target as one
      quarter of the configured maximum chat token limit.
    - Step 4: determine the chat window start.
      1. Start from the earliest semantic-turn boundary currently in
         the chat window.
      2. Apply the Step 5 tool-window rule to the candidate chat
         window from that start through the last currently eligible
         chat position.
      3. If the resulting filtered chat fits the post-response
         compaction target, keep that candidate start.
      4. Otherwise advance the candidate start to the next semantic
         turn and repeat while doing so still preserves either the
         newest 2 semantic turns when they fit under full configured
         `maxTokens`, or otherwise the newest 1 semantic turn.
      5. If no allowed candidate fits the one-quarter target, stop at
         the newest candidate permitted by that fallback rule.
      Chat-window movement therefore drops the oldest retained whole
      semantic turn first.
      Every chat message before the chosen start is outside the chat
      window and therefore receives the decision
      `DROP_BY_CHAT_WINDOW`.
    - Step 5: determine the tool window for chat-owned tool activity
      inside a candidate chat window.
      1. Always keep the chat-owned tool activity that belongs to the
         newest one semantic turn in that candidate chat window.
      2. Among older chat-owned tool activity groups that remain
         inside the candidate chat window, hide the oldest groups
         first.
      3. Stop hiding once the remaining older visible chat-owned tool
         activity uses at most half of what remains from the
         one-quarter target after reserving the newest one semantic
         turn against the counted token types of this algorithm.
      4. MCP messages stay governed only by the chat window, never
         consume this chat-owned-tool budget, and are never skipped by
         the tool window.
      Chat-owned tool activity groups outside the resulting tool
      window are skipped for space.
    - Step 6: walk the chat from the beginning to the last currently
      eligible chat position and assign one filtering decision per
      entry. The decision order is strict:
      1. if the entry index is before the chat window start, assign
         `DROP_BY_CHAT_WINDOW`;
      2. else if the entry belongs to chat-owned tool activity outside
         the tool window, assign `SKIP_OUTSIDE_TOOL_WINDOW`;
      3. else assign `SHOW`.
      No further category-specific front-trimming rule is allowed. In
      particular, MCP messages do not influence the chosen start and do
      not become hidden merely because a later user message exists.
    - Step 7: produce the filtered-chat result from the ordered `SHOW`
      entries plus common metadata:
      - whether any earlier entry was dropped by chat-window movement;
      - which chat-owned tool activity groups were skipped by the tool
        window;
      - whether the session is a restored transcript session.
    - Step 8: let each projection build its own final output from the
      same filtered-chat result by applying only projection-specific
      mapping and omission rules.
  - **Selected-chat representation:**
    - Structure name: `FilteredChatMessages`.
    - Required fields:
      - ordered selected `ChatMessage` values;
      - `hasOmittedEarlierChat`;
      - `skippedToolWindowGroupCount`;
      - `isRestoredTranscriptSession`.
    - The selected-message list is the single authoritative answer to
      the question "which chat messages survived filtering?"
      Projection builders must start from that selected-chat result
      rather than from the raw chat.
  - **Projection-specific final mapping rules:**
    - **Panel projection:**
      - If a stored general system message is present, render it once
        before any boundary marker or selected chat entry.
      - If `hasOmittedEarlierChat` is true, prepend one visible
        boundary marker before the first rendered selected chat entry.
      - Render selected MCP messages directly.
      - Render selected assistant-profile switch state directly.
      - Render selected automatic code status directly.
      - Render selected chat-owned tool summaries directly.
      - If a selected chat-owned tool activity group contains one or
        more selected chat-owned tool summaries, suppress raw rendering
        of that same group's tool request/result detail in the panel
        and show only the summary representation for that group.
      - If a selected chat-owned tool activity group has no selected
        summary, render its selected tool request/result detail
        directly.
      - Render selected user and assistant text in order.
      - Do not render the generic restored-transcript model note.
      - Do not render any derived latest profile instruction.
    - **Model projection:**
      - If a stored general system message is present, prepend it
        before any other model output.
      - If the session was restored from persisted transcript, prepend
        one generic restored-transcript note before the restored chat
        context. That note warns that the transcript came from a prior
        session and that current map state may differ.
      - The model projection never emits raw assistant-profile switch
        state.
      - Derive exactly one latest assistant-profile control-message
        pair from the latest assistant-profile switch state at or
        before the active chat end when such a switch exists:
        - one derived latest profile instruction as a control user
          message; and
        - one synthetic assistant acknowledgement message with text
          `ok` immediately after that derived instruction.
      - If that latest assistant-profile switch state survives
        filtering, replace that selected switch entry with the derived
        control-message pair in the same relative position.
      - If that latest assistant-profile switch state falls before the
        first selected chat entry, prepend the derived control-message
        pair before the boundary instruction and before the selected
        chat content.
      - Omit any other selected assistant-profile switch state.
      - The model projection must always emit that derived latest
        assistant-profile control-message pair for the latest relevant
        assistant-profile switch state. It must not depend on a raw
        stored `InstructionAckMessage` remaining inside the filtered
        chat.
      - If `hasOmittedEarlierChat` is true, include one model boundary
        instruction explaining that earlier chat was removed for space.
      - Include selected user text, assistant text, and automatic code
        status in order.
      - Include selected chat-owned tool request/result detail because
        it is live model context when still retained.
      - Exclude all tool-summary observability messages, including MCP
        messages and chat-owned tool summaries.
      - Selected tool request/result detail is the only retained live
        tool-observability in the model projection.
    - **Transcript projection:**
      - Persist only user text, assistant text,
        assistant-profile switch state, automatic code status, and the
        omitted-earlier-chat boundary state.
      - Persist no stored general system message.
      - Persist no derived latest profile instruction.
      - Persist no tool request detail, no tool result detail, no
        chat-owned tool summary, and no MCP message.
      - Persist no generic restored-transcript model note.
      - On later restore, rebuild only the durable transcript state and
        reintroduce the generic restored-transcript note only for the
        model projection.
  - **Same-session and restart semantics:**
    - Switching away from a chat and back within the same running
      Freeplane session reuses the in-memory chat for that chat.
      Therefore tool activity may remain visible there.
    - Restarting Freeplane restores only the durable transcript
      projection. Chat-owned tool activity and MCP messages are not
      reconstructed.
  - **Implementation structure:**
    - Keep `AssistantProfileChatMemory` as the orchestration boundary
      for session memory, canonical filtering state, derived
      filtering-cache invalidation, and projection requests.
    - Use raw `ChatMessage` values, `ChatEntryCategory`
      classification, and `ChatOwnedToolActivityGroup` message spans as
      the filtering model.
    - Keep canonical mutable state in `AssistantProfileChatMemory` as:
      - `generalSystemMessage`;
      - the ordered `ChatMessage` list; and
      - a narrowed `ChatMemoryViewState`.
    - Narrow `ChatMemoryViewState` to only:
      - `chatWindowStartIndex`;
      - `activeConversationTurnCount`; and
      - `isRestoredTranscriptSession`.
    - Do not keep hidden historical chat-owned tool groups in
      `ChatMemoryViewState`; recompute them as derived filtering data.
    - Keep turn-end indexes, `ActiveTurnRange` values, message
      classifications, hidden historical chat-owned tool groups,
      `FilteredChatMessages`, and projection outputs as derived state.
    - On message append/injection, undo/redo, transcript restore, or
      compaction state change, mutate canonical state first and only
      invalidate derived filtering state; do not eagerly rebuild every
      projection.
    - On the next call that needs filtered results,
      `AssistantProfileChatMemory` must rebuild derived turn data,
      request fresh `FilteredChatMessages` from `ChatMessageFilter`,
      and then hand that result to the relevant projector unless the
      cached derived state is still valid.
    - Keep `ChatTurnTracker` and narrow its responsibility to
      completed-turn boundary derivation plus `ActiveTurnRange`
      queries over a message list and `ChatMemoryViewState`.
    - Introduce `ToolWindowSelector` for oldest-first hiding of
      historical `ChatOwnedToolActivityGroup` values against the
      historical tool-window budget.
    - `ChatMessageFilter` consumes `ChatMessage` values,
      `ChatMemoryViewState`, derived turn boundaries, and current
      `maxTokens`, and returns one `FilteredChatMessages` result.
    - Replace `ChatMemoryProjectionBuilder` with:
      - `PanelProjector`, which takes `GeneralSystemMessage` plus
        `FilteredChatMessages` and returns panel render entries;
      - `ModelProjector`, which takes `GeneralSystemMessage`,
        `FilteredChatMessages`, and the latest profile instruction and
        returns model-visible `ChatMessage` values;
      - `TranscriptProjector`, which takes `FilteredChatMessages` and
        returns persistent transcript entries.
    - `GeneralSystemMessage` and the derived latest profile
      instruction are projector-only inputs. They are not part of
      `FilteredChatMessages`, and transcript projection persists
      neither of them.
    - `AIChatPanel`, request flow, and transcript restore code should
      consume those projectors rather than re-encoding visibility
      rules.

  ```plantuml
  @startuml
  set separator none
  package "org.freeplane.plugin.ai.chat.memory" {
    package filtering {
      class AssistantProfileChatMemory {
        -generalSystemMessage : GeneralSystemMessage
        -conversationMessages : List<ChatMessage>
        -viewState : ChatMemoryViewState
        -cachedTurnEndIndexes : List<Integer>
        -cachedFilteredMessages : FilteredChatMessages
        -derivedFilteringDirty : boolean
        +messages() : List<ChatMessage>
        +activeConversationRenderEntries() : List<ChatMemoryRenderEntry>
        +transcriptEntriesForPersistence() : List<ChatTranscriptEntry>
        +filteredChatMessages() : FilteredChatMessages
        +invalidateDerivedFiltering() : void
        +onResponseTokenUsage() : boolean
      }

      class ChatMemoryViewState {
        +chatWindowStartIndex : int
        +activeConversationTurnCount : int
        +isRestoredTranscriptSession : boolean
      }

      class ChatTurnTracker {
        +rebuildTurnEndIndexes(messages) : List<Integer>
        +activeConversationEndIndex(turnEndIndexes, viewState, conversationSize) : int
        +activeTurnRanges(turnEndIndexes, viewState, conversationEndIndex) : List<ActiveTurnRange>
      }

      class ActiveTurnRange {
        +startIndex : int
        +endExclusive : int
      }

      class ChatMessageFilter {
        +filterMessages(messages, viewState, turnEndIndexes, maxTokens) : FilteredChatMessages
      }

      class ToolWindowSelector {
        +selectHiddenGroups(groups, historicalToolWindowBudget) : List<ChatOwnedToolActivityGroup>
      }

      class FilteredChatMessages {
        +messages : List<ChatMessage>
        +hasOmittedEarlierChat : boolean
        +skippedToolWindowGroupCount : int
        +isRestoredTranscriptSession : boolean
      }

      class ChatOwnedToolActivityGroup {
        +startIndex : int
        +endExclusive : int
        +tokenCount : long
      }

      enum ChatEntryCategory {
        USER_TEXT
        ASSISTANT_TEXT
        CHAT_TOOL_REQUEST
        CHAT_TOOL_RESULT
        CHAT_TOOL_SUMMARY
        MCP_MESSAGE
        ASSISTANT_PROFILE_SWITCH
        AUTOMATIC_CODE_STATUS
      }
    }

    package projection {
      class PanelProjector {
        +buildRenderEntries(generalSystemMessage, selectedEntries) : List<ChatMemoryRenderEntry>
      }

      class ModelProjector {
        +buildMessages(generalSystemMessage, selectedEntries, latestProfileInstruction) : List<ChatMessage>
      }

      class TranscriptProjector {
        +buildTranscriptEntries(selectedEntries) : List<ChatTranscriptEntry>
      }
    }

    AssistantProfileChatMemory *-- ChatMemoryViewState
    AssistantProfileChatMemory --> ChatTurnTracker
    AssistantProfileChatMemory --> ChatMessageFilter
    AssistantProfileChatMemory --> PanelProjector
    AssistantProfileChatMemory --> ModelProjector
    AssistantProfileChatMemory --> TranscriptProjector
    ChatTurnTracker --> ActiveTurnRange
    ChatMessageFilter --> ChatMemoryViewState
    ChatMessageFilter --> ActiveTurnRange
    ChatMessageFilter --> FilteredChatMessages
    ChatMessageFilter --> ToolWindowSelector
    ChatMessageFilter --> ChatEntryCategory
    ToolWindowSelector --> ChatOwnedToolActivityGroup
    PanelProjector --> FilteredChatMessages
    ModelProjector --> FilteredChatMessages
    TranscriptProjector --> FilteredChatMessages
  }
  @enduml
  ```

  ```plantuml
  @startuml
  start
  :Message append/injection,
  undo/redo,
  transcript restore,
  or compaction state change;
  :Mutate canonical state
  (generalSystemMessage,
  conversationMessages,
  or ChatMemoryViewState);
  :derivedFilteringDirty = true;
  stop
  @enduml
  ```

  ```plantuml
  @startuml
  start
  :Projection request arrives;
  if (derivedFilteringDirty?) then (yes)
    :Rebuild turn-end indexes
    from conversationMessages;
    :Determine active chat end
    from ChatMemoryViewState;
    :ChatMessageFilter.filterMessages(
    conversationMessages,
    ChatMemoryViewState,
    turnEndIndexes,
    maxTokens);
    :Cache FilteredChatMessages;
    :derivedFilteringDirty = false;
  else (no)
    :Reuse cached FilteredChatMessages;
  endif
  fork
    :PanelProjector.buildRenderEntries(...);
  fork again
    :ModelProjector.buildMessages(...);
  fork again
    :TranscriptProjector.buildTranscriptEntries(...);
  end fork
  stop
  @enduml
  ```

  ```plantuml
  @startuml
  object before as "Before chat-window movement\n---\n[0] MCP1\n[1] MCP2\n[2] USER1\n[3] ASSISTANT1\n[4] MCP3\n[5] USER2\n[6] ASSISTANT2\n[7] MCP4\n[8] USER3\n[9] ASSISTANT3"
  object after as "After chat window moves to USER2\n---\nhasOmittedEarlierChat = true\n[5] USER2\n[6] ASSISTANT2\n[7] MCP4\n[8] USER3\n[9] ASSISTANT3"
  before --> after
  @enduml
  ```

  ```plantuml
  @startuml
  object selected as "FilteredChatMessages\n---\nSHOW messages only\nhasOmittedEarlierChat\nskippedToolWindowGroupCount\nisRestoredTranscriptSession"
  object panel as "Panel output\n---\nstored general system message when present\nvisible boundary marker\nMCP messages shown\nchat-owned summaries shown\nprofile switches shown raw"
  object model as "Model output\n---\nstored general system message when present\nrestored-transcript note\nlatest profile instruction\nboundary instruction\ntool summaries omitted"
  object transcript as "Transcript output\n---\ntranscript content only\nno stored general system message\nno tool activity or MCP messages"
  selected --> panel
  selected --> model
  selected --> transcript
  @enduml
  ```

- **Test specification:**
  - **Text-form suite derived from the algorithm:**
    - **T01. Summary-only MCP sequence in one running session**
      - Chat: `MCP1`, `MCP2`, `MCP3`.
      - Chat window: full chat.
      - Tool window: no chat-owned tool activity is present.
      - Selection decisions: all three entries = `SHOW`.
      - Panel output: `MCP1`, `MCP2`, `MCP3`.
      - Model output: no retained entries from these three MCP
        messages because MCP observability is excluded there.
      - Transcript output: no persisted entries from these three MCP
        messages.
    - **T02. Mixed MCP/dialog sequence before compaction**
      - Chat: `MCP1`, `MCP2`, `USER1`, `ASSISTANT1`, `MCP3`,
        `USER2`, `ASSISTANT2`, `MCP4`, `USER3`, `ASSISTANT3`.
      - Chat window: full chat.
      - Tool window: no chat-owned tool activity is present.
      - Selection decisions: every entry = `SHOW`.
      - Panel output: all ten entries in order.
      - Model output: `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Transcript output: `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`, `USER3`, `ASSISTANT3`.
    - **T03. Mixed MCP/dialog sequence after compaction moves the chat
      window to `USER2`**
      - Chat: same as T02.
      - Chat window starts at `USER2`.
      - Tool window: no chat-owned tool activity is present.
      - Selection decisions:
        - `MCP1`, `MCP2`, `USER1`, `ASSISTANT1`, `MCP3`
          = `DROP_BY_CHAT_WINDOW`;
        - `USER2`, `ASSISTANT2`, `MCP4`, `USER3`, `ASSISTANT3`
          = `SHOW`.
      - Panel output: boundary marker, `USER2`, `ASSISTANT2`, `MCP4`,
        `USER3`, `ASSISTANT3`.
      - Model output: boundary instruction, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Transcript output: boundary state, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
    - **T04. Same-running-session chat switch**
      - Starting point: T02 after all ten entries have been recorded.
      - The user switches to another chat and then back without
        restarting Freeplane.
      - Expected result: the same in-memory chat is reused and the
        panel output for the original chat is still the T02 panel
        output.
    - **T05. Restart/restore after a mixed sequence with MCP messages**
      - Starting point: T02 persisted to transcript.
      - Persisted transcript contains only transcript content from
        T02.
      - On restore, no MCP message or other chat-owned tool activity
        is reconstructed.
      - Restored panel output: `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`, `USER3`, `ASSISTANT3`.
      - Restored model output: generic restored-transcript note,
        `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`, `USER3`,
        `ASSISTANT3`.
      - Restored transcript output: unchanged transcript content only.
    - **T06. Historical chat-owned tool activity hidden inside the chat
      window**
      - Chat: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `TOOL_SUMMARY1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`.
      - Chat window: full chat.
      - Tool window excludes the group containing `TOOL_REQUEST1`,
        `TOOL_RESULT1`, `TOOL_SUMMARY1`.
      - Selection decisions:
        - `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2` = `SHOW`;
        - `TOOL_REQUEST1`, `TOOL_RESULT1`, `TOOL_SUMMARY1`
          = `SKIP_OUTSIDE_TOOL_WINDOW`.
      - Panel output: `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`.
      - Model output: `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`.
      - Transcript output: `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`.
      - No boundary marker appears because the chat window did not
        move.
    - **T07. Selected chat-owned tool activity group with a selected
      summary**
      - Chat: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `TOOL_SUMMARY1`, `ASSISTANT1`.
      - Chat window: full chat.
      - Tool window keeps the full chat-owned tool activity group.
      - Selection decisions: every entry = `SHOW`.
      - Panel output: `USER1`, `TOOL_SUMMARY1`, `ASSISTANT1`.
      - Model output: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `ASSISTANT1`.
      - Transcript output: `USER1`, `ASSISTANT1`.
    - **T08. Selected chat-owned tool activity group without a
      summary**
      - Chat: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `ASSISTANT1`.
      - Chat window: full chat.
      - Tool window keeps the full chat-owned tool activity group.
      - Selection decisions: every entry = `SHOW`.
      - Panel output: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `ASSISTANT1`.
      - Model output: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `ASSISTANT1`.
      - Transcript output: `USER1`, `ASSISTANT1`.
    - **T09. One historical chat-owned tool activity group hidden while
      a later group is kept**
      - Chat: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `TOOL_SUMMARY1`, `ASSISTANT1`, `USER2`, `TOOL_REQUEST2`,
        `TOOL_RESULT2`, `TOOL_SUMMARY2`, `ASSISTANT2`.
      - Chat window: full chat.
      - Tool window excludes the first chat-owned tool activity group
        only.
      - Selection decisions:
        - `USER1`, `ASSISTANT1`, `USER2`, `TOOL_REQUEST2`,
          `TOOL_RESULT2`, `TOOL_SUMMARY2`, `ASSISTANT2` = `SHOW`;
        - `TOOL_REQUEST1`, `TOOL_RESULT1`, `TOOL_SUMMARY1`
          = `SKIP_OUTSIDE_TOOL_WINDOW`.
      - Panel output: `USER1`, `ASSISTANT1`, `USER2`, `TOOL_SUMMARY2`,
        `ASSISTANT2`.
      - Model output: `USER1`, `ASSISTANT1`, `USER2`, `TOOL_REQUEST2`,
        `TOOL_RESULT2`, `ASSISTANT2`.
      - Transcript output: `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`.
    - **T10. MCP message stays visible while a historical chat-owned
      tool activity group is hidden**
      - Chat: `USER1`, `MCP1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `TOOL_SUMMARY1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`.
      - Chat window: full chat.
      - Tool window hides the chat-owned tool activity group because
        it falls outside the historical tool-window budget.
      - Selection decisions:
        - `USER1`, `MCP1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`
          = `SHOW`;
        - `TOOL_REQUEST1`, `TOOL_RESULT1`, `TOOL_SUMMARY1`
          = `SKIP_OUTSIDE_TOOL_WINDOW`.
      - Panel output: `USER1`, `MCP1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`.
      - Model output: `USER1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`.
      - Transcript output: `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`.
      - `MCP1` remains shown because MCP is governed only by the chat
        window.
    - **T11. Tool-window hiding can still fall back to whole-turn chat
      window movement**
      - Chat: `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`,
        `TOOL_SUMMARY1`, `ASSISTANT1`, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Configured maximum token limit: small enough that, after
        hiding the historical chat-owned tool activity group, all 3
        user/assistant turns still cannot be kept within the
        one-quarter post-response compaction target.
      - The remaining `USER2` / `ASSISTANT2` and `USER3` /
        `ASSISTANT3` turns still exceed that one-quarter target, but
        together still fit under full configured `maxTokens`.
      - Chat window starts at `USER2` because the older `USER1` /
        `ASSISTANT1` turn must be dropped as a whole turn after
        tool-window hiding proves insufficient, and the fallback rule
        still requires keeping the newest 2 semantic turns.
      - Selection decisions:
        - `USER1`, `TOOL_REQUEST1`, `TOOL_RESULT1`, `TOOL_SUMMARY1`,
          `ASSISTANT1` = `DROP_BY_CHAT_WINDOW`;
        - `USER2`, `ASSISTANT2`, `USER3`, `ASSISTANT3` = `SHOW`.
      - Panel output: boundary marker, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Model output: boundary instruction, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Transcript output: boundary state, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
    - **T12. Whole-turn fallback keeps only the newest one semantic
      turn when the newest two do not fit under full `maxTokens`**
      - Chat: `USER1`, `ASSISTANT1`, `USER2_LONG`,
        `ASSISTANT2_LONG`, `USER3`, `ASSISTANT3`.
      - Configured maximum token limit: small enough that all 3 turns
        cannot be kept within the one-quarter post-response compaction
        target.
      - The newest 2 semantic turns do not fit together under full
        configured `maxTokens`.
      - Chat window starts at `USER3` after whole-turn movement drops
        the older turns.
      - Selection decisions:
        - `USER1`, `ASSISTANT1`, `USER2_LONG`, `ASSISTANT2_LONG`
          = `DROP_BY_CHAT_WINDOW`;
        - `USER3`, `ASSISTANT3` = `SHOW`.
      - Panel output: boundary marker, `USER3`, `ASSISTANT3`.
      - Model output: boundary instruction, `USER3`, `ASSISTANT3`.
      - Transcript output: boundary state, `USER3`, `ASSISTANT3`.
    - **T13. Restart/restore after omitted earlier chat**
      - Starting point: T03 persisted to transcript.
      - Persisted transcript contains boundary state plus transcript
        content after compaction.
      - Restored panel output: boundary marker, `USER2`, `ASSISTANT2`,
        `USER3`, `ASSISTANT3`.
      - Restored model output: generic restored-transcript note,
        boundary instruction, `USER2`, `ASSISTANT2`, `USER3`,
        `ASSISTANT3`.
      - Restored transcript output: unchanged boundary state plus
        transcript content.
    - **T14. Pure MCP session after restart**
      - Starting point: T01 and then Freeplane restart.
      - Persisted transcript contains no transcript content from that
        session.
      - Expected result: no MCP-message history or other tool activity
        is reconstructed from disk.
    - **T15. Stored general system message is projector input, not
      selected chat content**
      - Separate canonical state: stored general system message
        `GENERAL_SYSTEM` is present.
      - Chat: `USER1`, `ASSISTANT1`.
      - Chat window: full chat.
      - Selection decisions: `USER1`, `ASSISTANT1` = `SHOW`.
      - Panel output: `GENERAL_SYSTEM`, `USER1`, `ASSISTANT1`.
      - Model output: `GENERAL_SYSTEM`, `USER1`, `ASSISTANT1`.
      - Transcript output: `USER1`, `ASSISTANT1`.
    - **T16. Model projection replaces a selected assistant-profile
      switch state with the derived latest assistant-profile control-
      message pair**
      - Chat: `PROFILE_SWITCH1`, `USER1`, `ASSISTANT1`.
      - `PROFILE_INSTRUCTION1` means the derived latest profile
        instruction from `PROFILE_SWITCH1`.
      - `PROFILE_ACK1` means the synthetic assistant acknowledgement
        `ok` paired with `PROFILE_INSTRUCTION1`.
      - Chat window: full chat.
      - Selection decisions: every entry = `SHOW`.
      - Panel output: `PROFILE_SWITCH1`, `USER1`, `ASSISTANT1`.
      - Model output: `PROFILE_INSTRUCTION1`, `PROFILE_ACK1`,
        `USER1`, `ASSISTANT1`.
      - Transcript output: `PROFILE_SWITCH1`, `USER1`, `ASSISTANT1`.
    - **T17. Model projection prepends the derived latest assistant-
      profile control-message pair when the latest assistant-profile
      switch state falls before the chat window**
      - Chat: `PROFILE_SWITCH1`, `USER1`, `ASSISTANT1`, `USER2`,
        `ASSISTANT2`.
      - `PROFILE_INSTRUCTION1` means the derived latest profile
        instruction from `PROFILE_SWITCH1`.
      - `PROFILE_ACK1` means the synthetic assistant acknowledgement
        `ok` paired with `PROFILE_INSTRUCTION1`.
      - Chat window starts at `USER2` after whole-turn movement.
      - Selection decisions:
        - `PROFILE_SWITCH1`, `USER1`, `ASSISTANT1`
          = `DROP_BY_CHAT_WINDOW`;
        - `USER2`, `ASSISTANT2` = `SHOW`.
      - Panel output: boundary marker, `USER2`, `ASSISTANT2`.
      - Model output: `PROFILE_INSTRUCTION1`, `PROFILE_ACK1`,
        boundary instruction, `USER2`, `ASSISTANT2`.
      - Transcript output: boundary state, `USER2`, `ASSISTANT2`.
  - **Automated tests:**
    - During the design stage: N/A.
    - During implementation: add executable tests derived directly from
      T01 through T17. Keep test names and fixture structure close
      enough to those text cases that each executable test can be
      traced back to one or more explicit text-form cases.
  - **Manual tests:**
    - Review the algorithm text and verify it defines one coherent
      filtering contract without reference to implementation state.
    - Review the glossary terms and Mermaid visual glossary and verify
      they match the Scenario and Design exactly.
    - Review the diagrams and verify they match the prose exactly.
    - Review T01 through T17 and verify every case is implied by the
      stated algorithm rather than by implementation knowledge.
    - After implementation, verify the executable tests match T01
      through T17 and that no filtering semantics had to be
      rediscovered from code during implementation.
  - **Implementation notes:**
    - **Interpretations:**
      - Treated `TranscriptHiddenSystemMessage` as a restore-session
        control signal that sets restored-session state rather than as
        selected chat content, because the approved design makes the
        restored-transcript note projector-only.
      - Kept historical chat-owned tool hiding gated by post-response
        compaction reaching full configured `maxTokens`, because the
        approved task explicitly preserves current substantive
        compaction behavior and existing undo/redo behavior depends on
        that trigger even though the final design prose did not restate
        it explicitly.
    - **Tradeoffs:**
      - Replaced `VisibleContextSelector` and
        `ChatMemoryProjectionBuilder` with explicit filtering and
        projector classes while keeping `AssistantProfileChatMemory`
        entry points stable, so the integration subtask can reroute the
        remaining consumers without a second filtering algorithm.

## Subtask: Integrate unified filtering replacement
- **Status:** review
- **Scope:** Correct the remaining visible-history consumer
  divergence, remove stale filtering-surface API, and align the task
  record with the implementation architecture that this task now keeps.
- **Motivation:** The branch already has the right filtering
  architecture in `ChatMessageFilter` plus the 3 projectors. The
  remaining defects are focused: renderer-side post-projection hiding,
  live summary updates bypassing the projector-backed history path,
  stale builder knobs, and stale tests that still encode the old
  global summary-hiding rule.
- **Constraints:**
  - Use the task-level constraints and the approved design from
    `Implement unified filtering core and projectors`.
  - Keep `ChatMessageFilter`, `PanelProjector`, `ModelProjector`,
    `TranscriptProjector`, and `AssistantProfileChatMemory` as the
    implementation base. Do not replace them with a clean-room
    rewrite.
  - Do not re-open filtering semantics or introduce a second
    filtering/projector path.
  - Remove remaining dead integration artifacts instead of retaining
    compatibility layers.
  - Do not broaden this subtask into UI-behavior redesign unless
    executable evidence shows a real contract mismatch.
- **Briefing:** The focused touch points for this follow-up are
  `ChatMemoryHistoryRenderer`, `AIChatPanel.appendToolSummaryToSession`,
  `ChatRequestFlow.onToolCallSummary`, and the stale builder surface in
  `AssistantProfileChatMemory.Builder`, plus the corresponding tests in
  `ChatMemoryHistoryRendererTest`, `ChatRequestFlowTest`, and
  `AIChatPanelScriptRequestTest`.
- **Research:**
  - See the task-level Research and `Implement unified filtering core
    and projectors` Design.
- **Analysis:**
  - Keep the current filtering architecture because review found
    focused consumer-divergence bugs, not a need for replacement.
  - Make `ChatMemoryHistoryRenderer` formatting-only because
    visibility decisions belong to `PanelProjector`, not to the
    renderer after projection.
  - Keep MCP summary updates incremental because they do not
    substitute raw tool detail from a chat-owned tool block.
  - Use one shared visible-history rebuild counter for the single
    visible chat-history surface, even when the user switches between
    chats.
  - Keep chat-owned summary updates incremental only while that shared
    visible-history rebuild counter remains unchanged since the current
    visible request started; otherwise rebuild visible history from the
    same projector-backed path used by normal history rebuilds because
    the panel contract must not depend on whether content arrived
    through a live append or a full rebuild.
  - Remove stale builder knobs because the approved contract no longer
    exposes configurable protected-turn or historical-tool-share
    semantics and the current code does not use those knobs.
  - Narrow the task narrative to one shared filtered-chat result,
    local summary substitution, and rebuild/live-update equivalence
    because the broader earlier wording now hides the real remaining
    contract.
- **Design:**
  - Keep `AssistantProfileChatMemory` as the sole owner of
    filtered-chat derivation plus panel/model/transcript projection
    decisions.
  - `ChatMemoryHistoryRenderer` must be formatting-only. It may render
    the `ChatMemoryRenderEntry` sequence it receives, but it must not
    hide raw tool request/result detail because some summary appears
    elsewhere in the same history.
  - Local summary substitution belongs only to `PanelProjector`:
    - a chat-owned tool summary hides raw tool request/result detail
      only for the same retained chat-owned tool activity block;
    - a different retained tool block without a summary still renders
      its raw tool request/result detail; and
    - an MCP summary never hides unrelated raw tool detail.
  - MCP summaries may update visible history incrementally because
    they do not substitute raw tool detail from a chat-owned tool
    block.
  - `AIChatPanel` must maintain one shared visible-history rebuild
    counter for the single visible chat-history surface. Switching
    between chats affects that same counter because it rebuilds that
    same visible surface.
  - Each visible request must compare its request-start value of that
    shared visible-history rebuild counter with the current value when
    a chat-owned tool summary arrives.
  - Chat-owned tool summaries may update visible history incrementally
    only when that shared visible-history rebuild counter is unchanged
    since the current visible request started.
  - Under that unchanged-counter condition, the UI may append exactly
    one rendered summary history entry through the incremental path
    instead of rebuilding the whole visible history.
  - If that counter changed, `AIChatPanel` or one helper on its behalf
    must rebuild current visible history from
    `AssistantProfileChatMemory.panelConversationRenderEntries()` or
    from one helper that is guaranteed to produce exactly the same
    output.
  - `ChatRequestFlow` must gate chat-owned summary append-vs-rebuild
    behavior using that shared visible-history rebuild counter plus a
    request-start baseline value. The task does not require a specific
    field name or callback shape.
  - Do not keep a separate unconditional summary-append visibility
    path that bypasses projector-backed history derivation.
  - Remove `protectedRecentTurnCount` and
    `historicalToolTokenShare` from
    `AssistantProfileChatMemory.Builder` unless they are reintroduced
    as real behavior. For this increment, remove them.
  - Align the task record with the implementation form kept on this
    branch:
    - keep the shared filtered-chat-result contract;
    - do not require a retained explicit SHOW/DROP/SKIP structure if
      the implementation keeps filtered messages plus the needed
      metadata instead; and
    - trim glossary and design detail that is not needed to understand
      or verify the remaining contract.
- **Test specification:**
  - **Automated tests:**
    - `ChatMemoryHistoryRendererTest`:
      - remove or rewrite tests that encode the old global
        summary-hides-all rule, especially
        `rebuildFromMessages_hidesRawToolMessagesWhenAnySummaryExistsIncludingMcp`;
      - add a test where one summarized tool block hides only its own
        raw tool request/result detail;
      - add a test where a different unsummarized tool block still
        renders its raw request/result detail; and
      - add a test where an MCP-only summary remains visible and does
        not hide unrelated raw tool detail.
    - `ChatRequestFlowTest`:
      - replace unconditional direct-summary-append expectations with
        conditional expectations:
        - chat-owned summary appends incrementally when the shared
          visible-history rebuild counter is unchanged relative to the
          request-start baseline;
        - chat-owned summary requests a visible-history rebuild when
          that shared counter changed after request start; and
        - MCP summary still appends incrementally;
      - keep coverage that the stored summary remains present in the
        memory-backed panel projection.
    - `AIChatPanelScriptRequestTest`:
      - add append-vs-rebuild equivalence coverage for summary updates;
      - verify that visible history immediately after an unchanged-
        counter incremental summary update equals visible history after
        a full rebuild of the same session;
      - cover the visible-request-flow path for both unchanged-shared-
        counter incremental append and changed-shared-counter rebuild
        fallback; and
      - cover the dedicated MCP-summary-session path as applicable.
    - `AssistantProfileChatMemoryTest` or projector-focused tests:
      - keep or add coverage proving local summary substitution still
        happens in the panel projection after renderer-side global
        hiding is removed.
    - Keep the existing integration coverage around transcript
      persistence, restored-transcript behavior, boundary behavior,
      profile-switch behavior, and compaction.
  - **Manual tests:** N/A
  - **Implementation notes:**
    - **Interpretations:**
      - Treated the absence of production references to
        `LiveTranscriptAdapter` and
        `AssistantProfileChatMemory.activeConversationMessagesForRendering()`
        as sufficient evidence that both were obsolete integration
        remnants within this subtask's approved cleanup scope.
    - **Tradeoffs:**
      - Kept ordinary user/assistant visible updates on the existing
        incremental append path and narrowed the new append-vs-rebuild
        gate to chat-owned tool summaries only.
      - Used one shared visible-history rebuild counter plus a
        request-start baseline instead of finer per-tool-block
        tracking because the guarded object is the single visible
        history surface and the coarser rule was sufficient for the
        approved contract.

## Subtask: Restore assistant-profile control-message pair injection
- **Status:** review
- **Scope:** Repair assistant-profile switch handling so model
  projection and compaction reinsert the hidden control-message pair at
  the correct position while panel and transcript behavior remain
  aligned with the approved visible-summary contract.
- **Motivation:** The current branch injects only the derived profile
  control user message and omits the paired synthetic assistant `ok`.
  That breaks the intended assistant-profile control-message semantics
  and can cause the next real user message to be answered as if the
  synthetic acknowledgement were still pending.
- **Constraints:**
  - Use the task-level constraints and the approved design from
    `Implement unified filtering core and projectors`, including the
    updated model-projection rules for derived assistant-profile
    control-message pairs.
  - Keep `ChatMessageFilter`, `PanelProjector`, `ModelProjector`,
    `TranscriptProjector`, and `AssistantProfileChatMemory` as the
    implementation base.
  - Keep panel behavior as a visible assistant-profile summary rather
    than exposing the raw hidden control-message pair.
  - Keep transcript persistence on assistant-profile switch state; do
    not persist the synthetic assistant acknowledgement as transcript
    content.
  - Do not broaden this subtask into unrelated filtering or visible-
    history routing changes.
- **Briefing:** The likely touch points are
  `ModelProjector`, `AssistantProfileChatMemory`, and the profile-
  switch tests in `AssistantProfileChatMemoryTest`, with possible
  projector-focused tests if that yields clearer coverage.
- **Research:**
  - Current code stores `AssistantProfileSwitchMessage` plus
    `InstructionAckMessage` in `conversationMessages`, suppresses the
    raw acknowledgement from panel and transcript rendering, and uses
    `AssistantProfileSwitchMessage` transcript state for persistence.
  - Current model projection derives only the latest profile control
    user message and drops the paired synthetic assistant `ok`, which
    no longer matches the approved assistant-profile control-message
    behavior.
  - The correct behavior is to always emit the derived control-message
    pair for the latest relevant assistant-profile switch state in
    model projection, regardless of whether the raw stored
    `InstructionAckMessage` remains inside the filtered chat.
- **Analysis:**
  - The approved assistant-profile behavior is a hidden control-
    message pair, not a lone derived user instruction.
  - Compaction and chat-window movement must preserve the semantic
    position of that pair even when the raw switch state falls before
    the current chat window.
  - Model projection must derive and place that pair from the latest
    relevant assistant-profile switch state instead of depending on a
    raw stored `InstructionAckMessage` surviving inside the filtered
    chat.
- **Design:**
  - Derive exactly one latest assistant-profile control-message pair
    from the latest assistant-profile switch state at or before the
    active chat end when such a switch exists:
    - one control user instruction; and
    - one synthetic assistant acknowledgement `ok` immediately after
      that control instruction.
  - If the latest assistant-profile switch state survives filtering,
    replace that selected switch entry with the derived control-
    message pair in the same relative position.
  - If the latest assistant-profile switch state falls before the
    first selected chat entry, prepend the derived control-message
    pair before the boundary instruction and before the selected chat
    content.
  - Always emit that derived control-message pair for the latest
    relevant assistant-profile switch state in model projection.
  - Omit any other assistant-profile switch state from model output.
  - Keep panel output driven by assistant-profile switch summary
    rendering rather than the hidden derived pair.
  - Keep transcript output driven by assistant-profile switch state
    rather than the hidden derived pair.

  ```plantuml
  @startuml
  participant "AssistantProfileChatMemory" as memory
  participant "ChatMessageFilter" as filter
  participant "ModelProjector" as model
  participant "PanelProjector" as panel
  participant "TranscriptProjector" as transcript

  memory -> filter : filterMessages(conversationMessages,...)
  filter --> memory : FilteredChatMessages
  memory -> model : buildMessages(filteredChatMessages, latest profile switch state)
  model -> model : derive PROFILE_INSTRUCTION + synthetic ok
  model -> model : replace selected switch or prepend pair
  model --> memory : model output
  memory -> panel : buildRenderEntries(filteredChatMessages)
  panel -> panel : render visible profile summary only
  panel --> memory : panel output
  memory -> transcript : buildTranscriptEntries(filteredChatMessages)
  transcript -> transcript : persist switch state only
  transcript --> memory : transcript output
  @enduml
  ```
- **Test specification:**
  - **Automated tests:**
    - Update the executable tests derived from T16 and T17 so model
      projection expects both `PROFILE_INSTRUCTION1` and
      `PROFILE_ACK1` in the correct order.
    - Add a regression test where a latest assistant-profile switch is
      followed by a real user message and model projection emits the
      derived control-message pair before that real user message.
    - Keep or add coverage proving panel output still hides the raw
      synthetic acknowledgement and transcript output still omits it.
    - Keep or add coverage proving compaction or chat-window movement
      still reinserts the derived control-message pair at the correct
      model position when the latest assistant-profile switch state
      falls before the visible chat window.
  - **Manual tests:** N/A
  - **Implementation notes:**
    - **Tradeoffs:**
      - Kept raw stored `InstructionAckMessage` in canonical chat
        state for compatibility with existing turn tracking and hidden
        panel/transcript behavior, but made model projection derive
        the synthetic assistant `ok` from the latest relevant
        assistant-profile switch state instead of relying on raw ack
        survival inside the filtered chat.
