package com.paladincloud.assetstate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AssetStateEvaluatorTests {
    @Test
    void unmanagedAreUpdated() {
        var primaryAssets = List.of(
            primary("1", AssetState.MANAGED, ""),
            primary("2", AssetState.MANAGED, ""),
            primary("3", AssetState.UNMANAGED, ""),
            primary("4", AssetState.UNMANAGED, ""));
        var opinionAssets = List.of(
            opinion("1"), opinion("3"));
        var evaluator = AssetStateEvaluator.builder()
            .isManaged(true)
            .primaryAssets(toMap(primaryAssets))
            .opinions(toMap(opinionAssets))
            .build();
        evaluator.run();

        var updated = toMap(evaluator.getUpdated());
        assertEquals(Set.of("3", "4"), updated.keySet());
        assertEquals(AssetState.MANAGED, updated.get("3").getAssetState());
        assertEquals(AssetState.MANAGED, updated.get("4").getAssetState());
    }

    @Test
    void managedAreUpdated() {
        var primaryAssets = List.of(
            primary("1", AssetState.MANAGED, ""),
            primary("2", AssetState.MANAGED, ""),
            primary("3", AssetState.UNMANAGED, ""),
            primary("4", AssetState.UNMANAGED, ""));
        var opinionAssets = List.of(
            opinion("1"), opinion("3"));
        var evaluator = AssetStateEvaluator.builder()
            .isManaged(false)
            .primaryAssets(toMap(primaryAssets))
            .opinions(toMap(opinionAssets))
            .build();
        evaluator.run();

        var updated = toMap(evaluator.getUpdated());
        assertEquals(Set.of("1", "2"), updated.keySet());
        assertEquals(AssetState.UNMANAGED, updated.get("1").getAssetState());
        assertEquals(AssetState.UNMANAGED, updated.get("2").getAssetState());
    }

    @Test
    void newlySuspiciousAreUpdated() {
        var primaryAssets = List.of(
            primary("1", AssetState.SUSPICIOUS, ""),
            primary("2", AssetState.MANAGED, ""),
            primary("3", AssetState.MANAGED, null),
            primary("4", AssetState.UNMANAGED, ""));
        var opinionAssets = List.of(
            opinion("1"), opinion("3"));
        var evaluator = AssetStateEvaluator.builder()
            .isManaged(false)
            .primaryAssets(toMap(primaryAssets))
            .opinions(toMap(opinionAssets))
            .build();
        evaluator.run();

        var updated = toMap(evaluator.getUpdated());
        assertEquals(Set.of("1", "2", "3"), updated.keySet());
        assertEquals(AssetState.UNMANAGED, updated.get("1").getAssetState());
        assertEquals(AssetState.UNMANAGED, updated.get("2").getAssetState());
        assertEquals(AssetState.SUSPICIOUS, updated.get("3").getAssetState());
    }

    @Test
    void noLongerSuspiciousAreUpdated() {
        var primaryAssets = List.of(
            primary("1", AssetState.SUSPICIOUS, ""),
            primary("2", AssetState.MANAGED, ""),
            primary("3", AssetState.SUSPICIOUS, null),
            primary("4", AssetState.UNMANAGED, ""));
        var opinionAssets = List.of(
            opinion("1"), opinion("3"));
        var evaluator = AssetStateEvaluator.builder()
            .isManaged(false)
            .primaryAssets(toMap(primaryAssets))
            .opinions(toMap(opinionAssets))
            .build();
        evaluator.run();

        var updated = toMap(evaluator.getUpdated());
        assertEquals(Set.of("1", "2"), updated.keySet());
        assertEquals(AssetState.UNMANAGED, updated.get("1").getAssetState());
        assertEquals(AssetState.UNMANAGED, updated.get("2").getAssetState());
    }

    Map<String, PartialAssetDTO> toMap(Collection<PartialAssetDTO> assets) {
        return assets.stream()
            .collect(Collectors.toMap(PartialAssetDTO::getDocId, Function.identity()));
    }

    PartialAssetDTO primary(String id, AssetState assetState, String primaryProvider) {
        return PartialAssetDTO.builder()
            .docId(id)
            .assetState(assetState)
            .primaryProvider(primaryProvider)
            .build();
    }

    PartialAssetDTO opinion(String id) {
        return PartialAssetDTO.builder()
            .docId(id)
            .build();
    }
}
