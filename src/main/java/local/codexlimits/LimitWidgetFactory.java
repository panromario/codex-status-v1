package local.codexlimits;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import org.jetbrains.annotations.NotNull;

public final class LimitWidgetFactory implements StatusBarWidgetFactory {
    public @NotNull String getId() { return "CodexLimitStatus"; }
    public @NotNull String getDisplayName() { return PluginInfo.NAME + ": остаток лимита"; }
    public boolean isAvailable(@NotNull Project project) { return true; }
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) { return new LimitWidget(project); }
    public void disposeWidget(@NotNull StatusBarWidget widget) { Disposer.dispose(widget); }
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) { return true; }
    public boolean isEnabledByDefault() { return true; }
}
