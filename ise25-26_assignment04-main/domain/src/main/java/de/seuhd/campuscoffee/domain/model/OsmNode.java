package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId     The OpenStreetMap node ID.
 * @param tags       All OSM tags as key-value pairs (e.g., "name" -> "Rada Coffee", "addr:street" -> "Untere Straße").
 * @param latitude   The latitude coordinate of the OSM node (optional).
 * @param longitude  The longitude coordinate of the OSM node (optional).
 */
@Builder
public record OsmNode(
        @NonNull Long nodeId,
        @NonNull Map<String, String> tags,
        @Nullable Double latitude,
        @Nullable Double longitude
) {
    /**
     * Helper method to get the name tag value.
     *
     * @return the name tag value, or null if not present
     */
    public @Nullable String getName() {
        return tags.get("name");
    }

    /**
     * Helper method to get the street address tag value.
     *
     * @return the addr:street tag value, or null if not present
     */
    public @Nullable String getStreet() {
        return tags.get("addr:street");
    }

    /**
     * Helper method to get the house number tag value.
     *
     * @return the addr:housenumber tag value, or null if not present
     */
    public @Nullable String getHouseNumber() {
        return tags.get("addr:housenumber");
    }

    /**
     * Helper method to get the postal code tag value.
     *
     * @return the addr:postcode tag value, or null if not present
     */
    public @Nullable String getPostalCode() {
        return tags.get("addr:postcode");
    }

    /**
     * Helper method to get the city tag value.
     *
     * @return the addr:city tag value, or null if not present
     */
    public @Nullable String getCity() {
        return tags.get("addr:city");
    }

    /**
     * Helper method to get the amenity tag value.
     *
     * @return the amenity tag value, or null if not present
     */
    public @Nullable String getAmenity() {
        return tags.get("amenity");
    }

    /**
     * Helper method to get the description tag value (checks both "description" and "note" tags).
     *
     * @return the description or note tag value, or null if not present
     */
    public @Nullable String getDescription() {
        String description = tags.get("description");
        if (description != null && !description.isBlank()) {
            return description;
        }
        return tags.get("note");
    }
}
