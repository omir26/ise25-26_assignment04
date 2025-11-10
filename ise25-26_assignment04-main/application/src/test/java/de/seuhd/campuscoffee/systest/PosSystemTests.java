package de.seuhd.campuscoffee.systest;

import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.Test;
import java.util.List;

import de.seuhd.campuscoffee.TestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for the operations related to POS (Point of Sale).
 */
public class PosSystemTests extends AbstractSysTest {

    @Test
    void createPos() {
        Pos posToCreate = TestFixtures.getPosFixturesForInsertion().getFirst();
        Pos createdPos = posDtoMapper.toDomain(TestUtils.createPos(List.of(posDtoMapper.fromDomain(posToCreate))).getFirst());

        assertThat(createdPos)
                .usingRecursiveComparison()
                .ignoringFields("id", "createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .isEqualTo(posToCreate);
    }

    @Test
    void getAllCreatedPos() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);

        List<Pos> retrievedPos = TestUtils.retrievePos()
                .stream()
                .map(posDtoMapper::toDomain)
                .toList();

        assertThat(retrievedPos)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .containsExactlyInAnyOrderElementsOf(createdPosList);
    }

    @Test
    void getPosById() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);
        Pos createdPos = createdPosList.getFirst();

        Pos retrievedPos = posDtoMapper.toDomain(
                TestUtils.retrievePosById(createdPos.id())
        );

        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt") // prevent issues due to differing timestamps after conversions
                .isEqualTo(createdPos);
    }

    @Test
    void updatePos() {
        List<Pos> createdPosList = TestFixtures.createPosFixtures(posService);
        Pos posToUpdate = createdPosList.getFirst();

        // Update fields using toBuilder() pattern (records are immutable)
        posToUpdate = posToUpdate.toBuilder()
                .name(posToUpdate.name() + " (Updated)")
                .description("Updated description")
                .build();

        Pos updatedPos = posDtoMapper.toDomain(TestUtils.updatePos(List.of(posDtoMapper.fromDomain(posToUpdate))).getFirst());

        assertThat(updatedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(posToUpdate);

        // Verify changes persist
        Pos retrievedPos = posDtoMapper.toDomain(TestUtils.retrievePosById(posToUpdate.id()));

        assertThat(retrievedPos)
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(posToUpdate);
    }
}