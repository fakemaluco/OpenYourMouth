package dev.openyourmouth.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** Vanilla edit box that commits on Enter, focus loss, or screen close. */
final class CommitEditBox extends EditBox {
    private final Consumer<String> commitAction;
    private String committedValue;

    CommitEditBox(Font font, int x, int y, int width, Component label, String value, int maximum,
                  Consumer<String> commitAction) {
        super(font, x, y, width, 20, label);
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
    public boolean keyPressed(KeyEvent event) {
        boolean handled = super.keyPressed(event);
        if (event.key() == 257 || event.key() == 335) commitNow();
        return handled;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (wasFocused && !focused) commitNow();
    }
}
