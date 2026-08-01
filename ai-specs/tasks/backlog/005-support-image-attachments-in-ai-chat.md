# Task: Support image attachments in AI chat
- **Task Identifier:** 2026-08-01-chat-images
- **Scope:**
  Let users attach multiple images to an AI chat message by pasting
  clipboard image data, pasting copied image files, or selecting image
  files. Send ordered image attachments with optional message text as a
  multimodal user message. Show and remove pending attachments, and
  preserve attached images when chat transcripts are saved and restored.
  Keep non-image attachments, drag-and-drop, and MCP input outside this
  task.
- **Motivation:**
  Users currently have no direct way to provide screenshots or other
  images to Freeplane's AI chat. Image attachments let users supply visual
  evidence that text and AI tools cannot provide, while retaining normal
  chat history and follow-up behavior.
- **Constraints:**
  - Represent pending and persisted attachments as an ordered collection,
    not as one image-specific input slot.
  - Accept only image data or user-selected image files that Freeplane can
    decode. Reject non-image and undecodable files.
  - Do not send local file paths to the model. Normalize accepted image
    content to a supported encoded representation before submission and
    persistence.
  - Bound image count, per-image dimensions and pixels, per-image encoded
    size, and total message payload.
  - Preserve existing chat request, cancellation, retry, session-switching,
    and transcript-restoration behavior for messages with attachments.
  - Do not silently replace image content with filenames, base64 text, or
    generated descriptions when the selected model cannot consume images.
