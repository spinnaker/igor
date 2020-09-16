package com.netflix.spinnaker.igor.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.ACCEPTED)
public class BuildOperationPendingException extends RuntimeException {
  public BuildOperationPendingException(String master, String job) {
    super(
        "A build request with same parameters for job "
            + job
            + " on "
            + master
            + " is already pending");
  }
}
