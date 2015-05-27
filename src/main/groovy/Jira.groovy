interface Jira {
  Map<String, String> getProperties( String issueId )
  
  class NoSuchIssueException extends RuntimeException {}
}