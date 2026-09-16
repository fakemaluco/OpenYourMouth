package dev.openyourmouth.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Small reusable slider that stores an actual ranged value instead of a normalized one. */
final class ValueSlider extends AbstractSliderButton {
    private final double minimum;
    private final double maximum;
    private final double step;
    private final DoubleFunction<Component> message;
    private final DoubleConsumer changed;

    ValueSlider(int x, int y, int width, double current, double minimum, double maximum, double step,
                DoubleFunction<Component> message, DoubleConsumer changed) {
        super(x, y, width, 20, Component.empty(), normalize(current, minimum, maximum));
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.message = message;
        this.changed = changed;
        updateMessage();
    }

    double actualValue() {
        double raw = minimum + value * (maximum - minimum);
        if (step <= 0.0D) return raw;
        return Math.round(raw / step) * step;
    }

    @Override
    protected void updateMessage() {
        if (message != null) setMessage(message.apply(actualValue()));
    }

    @Override
    protected void applyValue() {
        double actual = Math.max(minimum, Math.min(maximum, actualValue()));
        value = normalize(actual, minimum, maximum);
        changed.accept(actual);
    }

    private static double normalize(double value, double minimum, double maximum) {
        if (maximum <= minimum) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }
}
