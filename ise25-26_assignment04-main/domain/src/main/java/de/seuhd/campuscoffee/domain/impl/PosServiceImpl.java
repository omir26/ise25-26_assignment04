package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmApiException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object
        Pos posToImport = convertOsmNodeToPos(osmNode);

        // Check if a POS with the same name already exists
        // If it does, update it; otherwise, create a new one
        Pos existingPos = findPosByName(posToImport.name());
        Pos savedPos;
        
        if (existingPos != null) {
            // Update existing POS
            log.info("POS with name '{}' already exists (ID: {}), updating from OSM node {}", 
                    posToImport.name(), existingPos.id(), nodeId);
            savedPos = upsert(posToImport.toBuilder()
                    .id(existingPos.id())
                    .createdAt(existingPos.createdAt()) // Preserve original creation time
                    .build());
        } else {
            // Create new POS
            log.info("Creating new POS '{}' from OSM node {}", posToImport.name(), nodeId);
            savedPos = upsert(posToImport);
        }

        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);
        return savedPos;
    }

    /**
     * Finds a POS by name. Returns null if not found.
     *
     * @param name the name to search for
     * @return the POS with the given name, or null if not found
     */
    private Pos findPosByName(String name) {
        return posDataService.getAll().stream()
                .filter(pos -> pos.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Extracts relevant information from OSM tags and maps them to POS fields.
     *
     * @param osmNode the OSM node to convert
     * @return a POS domain object with data from the OSM node
     * @throws OsmNodeMissingFieldsException if required fields are missing
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        log.debug("Converting OSM node {} to POS", osmNode.nodeId());

        // Extract name (required)
        String name = osmNode.getName();
        if (name == null || name.isBlank()) {
            log.error("OSM node {} missing required field: name", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Extract description (optional - use description or note tag, or empty string)
        String description = osmNode.getDescription();
        if (description == null || description.isBlank()) {
            description = "";
            log.debug("OSM node {} has no description, using empty string", osmNode.nodeId());
        }

        // Determine PosType from amenity tag
        PosType posType = determinePosType(osmNode.getAmenity());

        // Extract address fields (all required)
        String street = osmNode.getStreet();
        String houseNumber = osmNode.getHouseNumber();
        String postcodeStr = osmNode.getPostalCode();
        String city = osmNode.getCity();

        // Validate required address fields
        validateRequiredFields(name, street, houseNumber, postcodeStr, city, osmNode.nodeId());

        // Parse postal code to Integer
        Integer postalCode;
        try {
            postalCode = Integer.parseInt(postcodeStr);
        } catch (NumberFormatException e) {
            log.error("Invalid postal code '{}' for OSM node {}", postcodeStr, osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Determine CampusType from address
        CampusType campus = determineCampusType(postcodeStr, city);

        // Build and return POS object
        return Pos.builder()
                .name(name)
                .description(description)
                .type(posType)
                .campus(campus)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postalCode)
                .city(city)
                .build();
    }

    /**
     * Determines the PosType from the OSM amenity tag.
     *
     * @param amenity the amenity tag value from OSM
     * @return the corresponding PosType, or CAFE as default
     */
    private PosType determinePosType(String amenity) {
        if (amenity == null || amenity.isBlank()) {
            log.debug("No amenity tag found, defaulting to CAFE");
            return PosType.CAFE;
        }

        String amenityLower = amenity.toLowerCase();
        return switch (amenityLower) {
            case "cafe", "coffee_shop" -> PosType.CAFE;
            case "bakery" -> PosType.BAKERY;
            case "cafeteria", "canteen" -> PosType.CAFETERIA;
            case "vending_machine" -> PosType.VENDING_MACHINE;
            default -> {
                log.warn("Unknown amenity type: '{}', defaulting to CAFE", amenity);
                yield PosType.CAFE;
            }
        };
    }

    /**
     * Determines the CampusType from postal code and city.
     * Uses a simple heuristic based on Heidelberg postal codes.
     *
     * @param postcode the postal code string
     * @param city the city name
     * @return the corresponding CampusType, or ALTSTADT as default
     */
    private CampusType determineCampusType(String postcode, String city) {
        if (postcode == null || postcode.isBlank()) {
            log.debug("No postal code found, defaulting to ALTSTADT");
            return CampusType.ALTSTADT;
        }

        try {
            int code = Integer.parseInt(postcode);
            // Heidelberg postal codes: 69115-69126
            // Simple heuristic based on postal code ranges
            if (code >= 69115 && code <= 69126) {
                // More specific mapping could be added here based on actual campus locations
                // For now, use a simple heuristic:
                if (code >= 69120) {
                    // INF campus area
                    return CampusType.INF;
                } else if (code <= 69116) {
                    // Bergheim area
                    return CampusType.BERGHEIM;
                } else {
                    // Altstadt area (default for 69117-69119)
                    return CampusType.ALTSTADT;
                }
            } else {
                log.debug("Postal code {} outside Heidelberg range, defaulting to ALTSTADT", code);
                return CampusType.ALTSTADT;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid postal code format '{}', defaulting to ALTSTADT", postcode);
            return CampusType.ALTSTADT;
        }
    }

    /**
     * Validates that all required fields are present and not blank.
     *
     * @param name the name field
     * @param street the street field
     * @param houseNumber the house number field
     * @param postcode the postal code field
     * @param city the city field
     * @param nodeId the OSM node ID (for error messages)
     * @throws OsmNodeMissingFieldsException if any required field is missing or blank
     */
    private void validateRequiredFields(String name, String street, String houseNumber,
                                        String postcode, String city, Long nodeId) {
        java.util.List<String> missing = new java.util.ArrayList<>();

        if (name == null || name.isBlank()) {
            missing.add("name");
        }
        if (street == null || street.isBlank()) {
            missing.add("addr:street");
        }
        if (houseNumber == null || houseNumber.isBlank()) {
            missing.add("addr:housenumber");
        }
        if (postcode == null || postcode.isBlank()) {
            missing.add("addr:postcode");
        }
        if (city == null || city.isBlank()) {
            missing.add("addr:city");
        }

        if (!missing.isEmpty()) {
            log.error("OSM node {} missing required fields: {}", nodeId, missing);
            throw new OsmNodeMissingFieldsException(nodeId);
        }
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
