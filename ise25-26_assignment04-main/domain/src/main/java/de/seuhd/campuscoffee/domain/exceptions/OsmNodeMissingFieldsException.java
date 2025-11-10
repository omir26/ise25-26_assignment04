package de.seuhd.campuscoffee.domain.exceptions;

/**
 * Exception thrown when an OpenStreetMap node does not contain the fields required to create a POS.
 */
public class OsmNodeMissingFieldsException extends RuntimeException {
    public OsmNodeMissingFieldsException(Long posId) {
        super("The OpenStreetMap node with ID " + posId + " does not have the required fields.");
    }
}
