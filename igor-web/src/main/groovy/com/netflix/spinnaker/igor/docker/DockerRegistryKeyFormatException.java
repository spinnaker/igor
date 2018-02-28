package com.netflix.spinnaker.igor.docker;

public class DockerRegistryKeyFormatException extends Throwable {
    public DockerRegistryKeyFormatException(String message) {
        super(message);
    }

    public DockerRegistryKeyFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
