package com.bdmage.mage_backend.exception;

public class ModeratorUserNotFoundException extends RuntimeException {
    public ModeratorUserNotFoundException() { super("Account not found."); }
}
