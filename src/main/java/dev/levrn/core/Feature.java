package dev.levrn.core;

import java.util.Objects;

public final class Feature {
    public final String id, name, description;
    public final Category category;
    public final boolean defaultEnabled;
    private boolean enabled;

    public Feature(String id, String name, String description, Category category, boolean enabled) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.category = Objects.requireNonNull(category);
        this.defaultEnabled = enabled;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public void toggle() { enabled = !enabled; }
}
