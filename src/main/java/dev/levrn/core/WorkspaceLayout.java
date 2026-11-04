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
        int[] bottoms=new int[columns];
        for(Category category:Category.values()) {
            var group=features.stream().filter(f -> f.category==category).toList();
            for(int first=0;first<group.size();first+=6) {
                var part=List.copyOf(group.subList(first,Math.min(first+6,group.size())));
                int lane=0;for(int i=1;i<columns;i++) if(bottoms[i]<bottoms[lane]) lane=i;
                int h=22+part.size()*18;
                result.add(new Section(category,part,lane,bottoms[lane],h));bottoms[lane]+=h+8;
            }
        }
        return new Result(List.copyOf(result),Math.max(0,java.util.Arrays.stream(bottoms).max().orElse(8)-8));
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
            sections.add(new Section(part.getFirst().category, List.copyOf(part), column, 0, 22 + part.size() * 18));
        }
        return new Result(List.copyOf(sections), 22 + rows * 18);
    }
    private WorkspaceLayout() { }
}
