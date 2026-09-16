package dev.openyourmouth.integration.plasmo;

import su.plo.lib.mod.client.gui.components.TextFieldWidget;
import su.plo.slib.api.chat.component.McTextComponent;

import java.util.Objects;
import java.util.function.Consumer;

/** Plasmo text field that commits on Enter, focus loss, or tab removal. */
final class CommitTextFieldWidget extends TextFieldWidget {
    private final Consumer<String> commitAction;
    private String committedValue;

    CommitTextFieldWidget(int width, McTextComponent label, String value, int maximum, Consumer<String> commitAction) {
        super(0, 0, width, 20, label);
        this.commitAction = commitAction;
        setMaxLength(maximum);
        setValue(value == null ? "" : value);
        committedValue = getValue();
    }

    void commitNow() {
        String value = getValue();
        if (Objects.equals(committedValue, value)) return;
        committedValue = value;
        commitAction.accept(value);
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, modifiers);
        if (keyCode == 257 || keyCode == 335) commitNow();
        return handled;
    }

    @Override
    public void applyFocus(boolean focused) {
        super.applyFocus(focused);
        if (!focused) commitNow();
    }
}
