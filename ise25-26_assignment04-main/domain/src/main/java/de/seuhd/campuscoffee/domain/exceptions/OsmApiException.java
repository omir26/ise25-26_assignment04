package de.seuhd.campuscoffee.domain.exceptions;

/**
 * Exception thrown when the OpenStreetMap API returns a server error (5xx) or is unavailable.
 * This indicates a problem with the OSM API service, not with the requested node.
 */
public class OsmApiException extends RuntimeException {
    public OsmApiException(Long nodeId, String message) {
        super("Error accessing OpenStreetMap API for node " + nodeId + ": " + message);
    }

    public OsmApiException(Long nodeId, String message, Throwable cause) {
        super("Error accessing OpenStreetMap API for node " + nodeId + ": " + message, cause);
    }
}

