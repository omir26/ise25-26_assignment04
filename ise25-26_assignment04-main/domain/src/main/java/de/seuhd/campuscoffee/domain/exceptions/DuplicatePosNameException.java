package de.seuhd.campuscoffee.domain.exceptions;

/**
 * Exception thrown when attempting to create or update a POS with a name that already exists.
 * This represents a business rule violation: POS names must be unique.
 */
public class DuplicatePosNameException extends RuntimeException {
    public DuplicatePosNameException(String posName) {
        super("POS with name '" + posName + "' already exists.");
    }
}
