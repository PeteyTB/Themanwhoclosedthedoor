package dev.levrn.core;

import java.util.ArrayList;
import java.util.List;

/** Category lanes use compact rows and one continuous scroll surface instead of pagination. */
public final class WorkspaceLayout {
    public record Section(Category category, List<Feature> features, int column, int top, int height) { }
    public record Result(List<Section> sections, int height) { }
    public static Result arrange(List<Feature> features, int columns) {
        if (columns < 1) throw new IllegalArgumentException("columns must be positive");
        List<Section> result = new ArrayList<>();
        int column = 0, top = 0, rowHeight = 0;
        for (Category category : Category.values()) {
            List<Feature> group = features.stream().filter(f -> f.category == category).toList();
            if (group.isEmpty()) continue;
            int height = 27 + group.size() * 25;
            result.add(new Section(category, group, column, top, height));
            rowHeight = Math.max(rowHeight, height);
            if (++column == columns) { top += rowHeight + 12; column = 0; rowHeight = 0; }
        }
        return new Result(List.copyOf(result), top + rowHeight);
    }
    public static Result spreadCategory(List<Feature> features, int columns) {
        if (columns < 1) throw new IllegalArgumentException("columns must be positive");
        if (features.isEmpty()) return new Result(List.of(), 0);
        int rows = (features.size() + columns - 1) / columns;
        List<Section> sections = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            int start = column * rows;
            if (start >= features.size()) break;
            var part = features.subList(start, Math.min(features.size(), start + rows));
            sections.add(new Section(part.getFirst().category, List.copyOf(part), column, 0, 27 + part.size() * 25));
        }
        return new Result(List.copyOf(sections), 27 + rows * 25);
    }
    private WorkspaceLayout() { }
}
