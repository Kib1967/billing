def config = new ConfigSlurper().parse(new File(args[0]).toURL())

// First we have to authenticate with JIRA
jira = new Jira3p4p2(config.jira.rootUrl, config.jira.user, config.jira.pwd)

def issueProperties = jira.getProperties 'COMP-6410'
issueProperties.each { k, v ->
  println "${k} = ${v}"
}
