package local.codexlimits;

import com.intellij.ml.llm.core.chat.session.ChatSession;
import com.intellij.ml.llm.core.chat.session.ChatSessionHostListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Optional integration with the AI Assistant API shipped in PhpStorm 263. */
public final class ChatRequestListener implements ChatSessionHostListener {
    private final Project project;
    public ChatRequestListener(Project project) { this.project = project; }
    public void chatTimestampUpdated(@NotNull ChatSession session) { LimitWidget.chatActivity(project); }
    public void chatCreated(@NotNull ChatSession session) { LimitWidget.chatActivity(project); }
    public void chatRenamed(@NotNull ChatSession session) { }
    public void chatRemoved(@NotNull ChatSession session) { }
    public void chatsRestoredFromAgentAvailability() { }
}
