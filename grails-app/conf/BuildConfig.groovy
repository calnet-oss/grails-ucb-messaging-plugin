grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolver = "maven" // or ivy
grails.project.repos.default = "calnet-plugins"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        mavenLocal()
        mavenRepo id: "calnet-repo", url: "https://maven.calnet.berkeley.edu/artifactory/all/"
    }
    dependencies {
        def activeMQversion = '5.12.1'
        compile "org.apache.activemq:activemq-client:${activeMQversion}"
        compile "org.apache.activemq:activemq-pool:${activeMQversion}"
        compile "org.apache.activemq:activemq-camel:${activeMQversion}"
        test "org.apache.activemq:activemq-broker:${activeMQversion}"
        test "org.apache.activemq:activemq-kahadb-store:${activeMQversion}"
        
        // Extra dependencies for the Camel plugin
        runtime "org.springframework:spring-beans:4.1.8.RELEASE" // workaround for <=2.4.4 - try taking out for Grails 2.4.5 or greater
        
        // Should match the Camel version that the Camel routing plugin is
        // using.
        def camelVersion = "2.16.1"
        compile "org.apache.camel:camel-spring:${camelVersion}"
        compile "org.apache.camel:camel-jms:${camelVersion}"
        compile "org.apache.camel:camel-xmljson:${camelVersion}"
    }

    plugins {
        build(":release:3.1.1",
              ":rest-client-builder:2.1.1") {
            export = false
        }
        
        // Camel plugin
        compile ":routing:1.4.1-UCB2-SNAPSHOT"
    }
}
