package de.seuhd.campuscoffee.domain.tests;

import de.seuhd.campuscoffee.domain.model.*;
import de.seuhd.campuscoffee.domain.ports.PosService;
import org.apache.commons.lang3.SerializationUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test fixtures for POS entities.
 */
public class TestFixtures {
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2025, 10, 29, 12, 0, 0);

    private static final List<Pos> POS_LIST = List.of(
            Pos.builder()
                    .id(1L).createdAt(DATE_TIME).updatedAt(DATE_TIME)
                    .name("Schmelzpunkt").description("Great waffles")
                    .type(PosType.CAFE).campus(CampusType.ALTSTADT)
                    .street("Hauptstraße").houseNumber("90").postalCode(69117).city("Heidelberg")
                    .build(),
            Pos.builder()
                    .id(1L).createdAt(DATE_TIME).updatedAt(DATE_TIME)
                    .name("Bäcker Görtz ").description("Walking distance to lecture hall")
                    .type(PosType.BAKERY).campus(CampusType.INF)
                    .street("Berliner Str.").houseNumber("43").postalCode(69120).city("Heidelberg")
                    .build(),
            Pos.builder()
                    .id(1L).createdAt(DATE_TIME).updatedAt(DATE_TIME)
                    .name("Café Botanik").description("Outdoor seating available")
                    .type(PosType.CAFETERIA).campus(CampusType.INF)
                    .street("Im Neuenheimer Feld").houseNumber("304").postalCode(69120).city("Heidelberg")
                    .build(),
            Pos.builder()
                    .id(1L).createdAt(DATE_TIME).updatedAt(DATE_TIME)
                    .name("New Vending Machine").description("Use only in case of emergencies")
                    .type(PosType.VENDING_MACHINE).campus(CampusType.BERGHEIM)
                    .street("Teststraße").houseNumber("99a").postalCode(12345).city("Other City")
                    .build()
    );

    public static List<Pos> getPosList() {
        return POS_LIST.stream()
                .map(SerializationUtils::clone) // prevent issues when tests modify the fixture objects
                .toList();
    }

    public static List<Pos> getPosFixturesForInsertion() {
        return getPosList().stream()
                .map(user -> user.toBuilder().id(null).createdAt(null).updatedAt(null).build())
                .toList();
    }

    public static List<Pos> createPosFixtures(PosService posService) {
        return getPosFixturesForInsertion().stream()
                .map(posService::upsert)
                .collect(Collectors.toList());
    }
}
