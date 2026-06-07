package org.freeplane.plugin.ai.chat.session;

import java.util.Arrays;
import java.util.List;
import org.freeplane.plugin.ai.chat.history.MapRootShortTextCount;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapRootShortTextCountsMergerTest {
    @Test
    public void mergeByMax_keepsAllKeysAndUsesMaxCounts() {
        MapRootShortTextCountsMerger uut = new MapRootShortTextCountsMerger();
        List<MapRootShortTextCount> existing = Arrays.asList(
            new MapRootShortTextCount("Map A", 2),
            new MapRootShortTextCount("Map B", 1));
        List<MapRootShortTextCount> current = Arrays.asList(
            new MapRootShortTextCount("Map A", 1),
            new MapRootShortTextCount("Map C", 4));

        List<MapRootShortTextCount> merged = uut.mergeByMax(existing, current);

        assertThat(merged).hasSize(3);
        assertThat(merged).anySatisfy(entry -> {
            assertThat(entry.getText()).isEqualTo("Map A");
            assertThat(entry.getCount()).isEqualTo(2);
        });
        assertThat(merged).anySatisfy(entry -> {
            assertThat(entry.getText()).isEqualTo("Map B");
            assertThat(entry.getCount()).isEqualTo(1);
        });
        assertThat(merged).anySatisfy(entry -> {
            assertThat(entry.getText()).isEqualTo("Map C");
            assertThat(entry.getCount()).isEqualTo(4);
        });
    }
}
