package com.bdmage.mage_backend.exception;

public class ModeratorConflictException extends RuntimeException {
    public ModeratorConflictException() { super("Permissions changed or the request was already used. Refresh this account before trying again."); }
    public ModeratorConflictException(String message) { super(message); }
}
