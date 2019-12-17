package com.netflix.spinnaker.igor.scm;

import java.util.List;

public class AbstractScmMaster implements ScmMaster {
  public List<String> listDirectory(
      String projectKey, String repositorySlug, String path, String at) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public String getTextFileContents(
      String projectKey, String repositorySlug, String path, String at) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public String getTextFileContents(String projectKey, String repositorySlug, String path) {
    return getTextFileContents(projectKey, repositorySlug, path, "refs/master/HEAD");
  }
}
