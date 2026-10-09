package com.bdmage.mage_backend.exception;

public class UnsupportedSceneDocumentException extends RuntimeException {
    public UnsupportedSceneDocumentException() {
        super("This scene document format is no longer supported.");
    }
}
