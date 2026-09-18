# Chat System Enhancements — Implementation Plan

This plan details the architecture, data models, endpoints, and frontend components to transform GitBot's chat pipeline into a full-featured, resilient product feature.

---

## User Review Required

> [!IMPORTANT]
> **Retry Data Model Decision**:
> When retrying an assistant response, we will:
> 1. Locate the existing user question corresponding to the target assistant turn.
> 2. **Preserve the original user message** (no duplicate user questions created).
> 3. If a failed or incomplete assistant message already exists for that turn, **replace it** (update its content and status to `COMPLETE` upon successful generation, or delete the failed predecessor before persisting the new one). This keeps conversation history linear, clean, and avoids messy orphaned retry clutter.

> [!IMPORTANT]
> **SSE Stream Cancellation & Disconnect Handling**:
> Project Reactor's `Flux<String>` subscription returned by Spring AI's `stream().content()` will be tracked in a thread-safe registry keyed by `sessionId`.
> * Calling `stop` (from the UI or via `POST /api/chat/sessions/{id}/stop`) or client disconnects captured by `SseEmitter.onError` / `SseEmitter.onTimeout` will trigger `subscription.dispose()`.
> * This closes the upstream HTTP stream to OpenAI and stops token generation immediately.
> * If stopped mid-stream, the assistant message is marked with `status = INTERRUPTED` rather than `COMPLETE`.
> * *Limitation Note*: As with all HTTP streaming APIs, tokens already transmitted in-flight across network buffers before the cancellation signal completes cannot be clawed back, but further OpenAI billing/generation stops.

---

## Proposed Changes

### 1. Data Model & Database Schema

#### [MODIFY] [ChatSession.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/entity/ChatSession.java)
Add fields for branching and sharing:
* `parentSessionId` (`UUID`, nullable): Original session ID if this session was branched.
* `branchMessageId` (`UUID`, nullable): Message ID from which the branch was created.
* `shareToken` (`String`, length 64, nullable, unique): Unguessable token for public viewing.
* `isShared` (`boolean`, default false): Opt-in sharing toggle.
* `sharedAt` (`Instant`, nullable): Timestamp when the session was made public.

#### [NEW] [MessageStatus.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/enums/MessageStatus.java)
Enum representing message lifecycle:
* `COMPLETE`: Successfully finished generation.
* `INTERRUPTED`: Stopped by user or client disconnect.
* `FAILED`: Errored during generation.

#### [MODIFY] [ChatMessage.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/entity/ChatMessage.java)
* Add `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MessageStatus status = MessageStatus.COMPLETE;`

#### [NEW] [ReportReason.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/enums/ReportReason.java)
Enum for user feedback:
* `INCORRECT`, `IRRELEVANT`, `UNSAFE`, `CITATION_ISSUE`, `OTHER`.

#### [NEW] [MessageReport.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/entity/MessageReport.java)
Entity for storing reported AI responses:
* `id` (`UUID`, PK)
* `messageId` (`UUID`, not null)
* `sessionId` (`UUID`, not null)
* `userId` (`UUID`, not null)
* `reason` (`ReportReason`, not null)
* `details` (`TEXT`, nullable)
* `createdAt` (`Instant`, not null)
* Unique constraint on `(user_id, message_id)` to prevent duplicate submissions.

#### [NEW] [02-chat-enhancements.sql](file:///home/flux_capacitor/IdeaProjects/GitBot/docker/postgres/migrations/02-chat-enhancements.sql)
Explicit migration script for Flyway/production readiness:
```sql
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS parent_session_id UUID;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS branch_message_id UUID;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS share_token VARCHAR(64) UNIQUE;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS is_shared BOOLEAN DEFAULT FALSE;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS shared_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'COMPLETE';

CREATE TABLE IF NOT EXISTS message_reports (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reason VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_message_report UNIQUE (user_id, message_id)
);
```

---

### 2. Backend Logic & Services

#### [MODIFY] [ChatPromptBuilder.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/service/ai/ChatPromptBuilder.java)
* Accept historical messages `List<ChatMessage>` (bounded to the last `maxHistoryMessages`, default 10).
* Construct native Spring AI `Message` sequence:
  1. `SystemMessage`: Persona and ground-truth constraint on retrieved code.
  2. History turns: alternating `UserMessage` and `AssistantMessage` for prior turns.
  3. Current turn `UserMessage`: clearly separates retrieved code context from current user question:
     ```
     Code context from repository:
     {codeContext}

     Current user question:
     {question}
     ```

#### [MODIFY] [ChatStreamHandler.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/service/ai/ChatStreamHandler.java)
* Active stream tracking: `ConcurrentHashMap<UUID, ActiveChatStream>`.
* On cancel / stop / disconnect: dispose Reactor subscription and persist assistant message with `MessageStatus.INTERRUPTED`.
* Handle retry stream by accepting an optional `targetAssistantMessageId` to replace or update.

#### [MODIFY] [ChatService.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/service/ChatService.java)
Implement core business operations:
1. **Conversation History**: Load prior messages `findBySessionIdOrderByCreatedAtAsc(sessionId)`, slice the last `N` messages before the current prompt.
2. **Retry**: `streamRetry(UUID userId, UUID sessionId, UUID messageId)`
   - Verify session ownership.
   - Identify user question and clean up failed/target assistant response.
   - Stream new completion via SSE.
3. **Delete Session**: `deleteSession(UUID userId, UUID sessionId)`
   - Verify ownership.
   - Delete session and cascade delete all messages and reports.
4. **Rename Session**: `renameSession(UUID userId, UUID sessionId, String newTitle)`
   - Validate and trim title (1 to 200 chars).
5. **Branch Session**: `branchSession(UUID userId, UUID sessionId, UUID cutoffMessageId, String newTitle)`
   - Verify ownership of parent session.
   - Create new session linked with `parentSessionId` and `branchMessageId`.
   - Copy all messages up to and including `cutoffMessageId` into the new session.
6. **Share Session & Revoke**:
   - `createShare(UUID userId, UUID sessionId)`: Generate secure token, set `isShared = true`, return share token.
   - `revokeShare(UUID userId, UUID sessionId)`: Set `isShared = false`, `shareToken = null`.
7. **Report AI Response**: `reportMessage(UUID userId, UUID sessionId, UUID messageId, ReportReason reason, String details)`
   - Verify session ownership.
   - Persist `MessageReport`.
8. **Stop Stream**: `stopStream(UUID userId, UUID sessionId)`
   - Signal `ChatStreamHandler` to dispose active generation.

#### [MODIFY] [ChatController.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/controller/ChatController.java)
Expose authenticated endpoints:
* `DELETE /api/chat/sessions/{id}` -> 204 No Content
* `PATCH /api/chat/sessions/{id}` -> `RenameSessionRequest(String title)` -> `ChatSessionResponse`
* `POST /api/chat/sessions/{id}/retry` -> `RetryMessageRequest(UUID messageId)` -> SSE Emitter
* `POST /api/chat/sessions/{id}/branch` -> `BranchSessionRequest(UUID messageId, String title)` -> `ChatSessionResponse`
* `POST /api/chat/sessions/{id}/share` -> `ShareResponse(String shareToken, String shareUrl)`
* `DELETE /api/chat/sessions/{id}/share` -> 204 No Content
* `POST /api/chat/sessions/{id}/messages/{messageId}/report` -> `ReportRequest(ReportReason reason, String details)` -> 201 Created
* `POST /api/chat/sessions/{id}/stop` -> 200 OK

#### [NEW] [PublicShareController.java](file:///home/flux_capacitor/IdeaProjects/GitBot/backend/src/main/java/com/example/gitbot/controller/PublicShareController.java)
* `GET /api/public/shares/{shareToken}`: Public endpoint returning read-only `SharedChatResponse` (session title, repo name, messages with citations). Does not require auth; permitted in `SecurityConfig`. Returns 404 if not found or share is revoked.

---

### 3. Frontend Implementation

#### [MODIFY] [api.ts](file:///home/flux_capacitor/IdeaProjects/GitBot/client/lib/api.ts)
* Add types: `MessageStatus`, `ReportReason`, `ShareResponse`, `PublicSharedChat`, `RenameRequest`, `BranchRequest`, `ReportRequest`.
* Add API methods for retry, delete, rename, branch, share, revoke share, report, stop, and get public share.

#### [MODIFY] [use-chat.ts](file:///home/flux_capacitor/IdeaProjects/GitBot/client/hooks/use-chat.ts)
* Add hooks: `useDeleteChatSession`, `useRenameChatSession`, `useBranchChatSession`, `useShareChatSession`, `useReportMessage`.
* Update `useStreamChat` to support `retry(messageId)`.

#### [MODIFY] [chat-messages.tsx](file:///home/flux_capacitor/IdeaProjects/GitBot/client/components/chat/chat-messages.tsx)
* Add message action toolbar on assistant messages:
  * **Copy**: Copies rendered markdown with temporary check icon and clipboard fallback.
  * **Retry**: Triggers retry for that turn. Disabled while streaming.
  * **Branch**: Opens branch dialog to fork conversation from this point.
  * **Report**: Opens modal with report reasons and details textarea.
* Visual indicators for `INTERRUPTED` and `FAILED` status messages with prominent Retry button.

#### [MODIFY] [chat-sidebar.tsx](file:///home/flux_capacitor/IdeaProjects/GitBot/client/components/chat/chat-sidebar.tsx)
* Session item options dropdown:
  * **Rename**: Inline edit or modal.
  * **Delete**: Confirmation dialog preventing accidental removal.
* Branch badge/icon display for sessions with `parentSessionId`.

#### [MODIFY] [chat-view.tsx](file:///home/flux_capacitor/IdeaProjects/GitBot/client/components/chat/chat-view.tsx)
* Header action: **Share** button opening Share dialog (enable share, copy public link, revoke share).
* Navigation handling when current session is deleted or branched.

#### [NEW] [share/[shareToken]/page.tsx](file:///home/flux_capacitor/IdeaProjects/GitBot/client/app/share/[shareToken]/page.tsx)
* Public read-only page with clean message view and banner: "Shared GitBot conversation (read-only)".

---

## Verification Plan

### Automated Tests
1. **Backend Tests**:
   * `ChatServiceTest`:
     - Conversation history slicing (verifies max history limit and ordering).
     - Delete session verifies ownership check and message deletion.
     - Rename session verifies trimming and validation.
     - Branch session copies correct message slice and links parent.
     - Report message enforces session ownership and rejects duplicate reports.
     - Share creation & revocation.
   * `PublicShareControllerTest`:
     - Valid share token returns 200 and messages without user secrets.
     - Revoked or invalid share token returns 404.
   * `ChatControllerTest`:
     - Test endpoints with mock authentication.
2. **Frontend Checks**:
   * Run `npm run build` in `client` to verify Next.js builds with new routes.
   * Run `npm run lint` in `client`.
