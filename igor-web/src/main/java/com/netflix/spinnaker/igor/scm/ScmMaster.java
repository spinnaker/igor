package com.netflix.spinnaker.igor.scm;

import java.util.List;

/** Abstracts underlying implementation details of each SCM system under a common interface. */
public interface ScmMaster {
  public static final String DEFAULT_GIT_REF = "refs/heads/master";

  public abstract List<String> listDirectory(
      String projectKey, String repositorySlug, String path, String at);

  public abstract String getTextFileContents(
      String projectKey, String repositorySlug, String path, String at);
}
