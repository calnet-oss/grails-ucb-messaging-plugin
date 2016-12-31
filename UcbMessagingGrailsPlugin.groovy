class UcbMessagingGrailsPlugin {
    def group = "edu.berkeley.calnet.grails.plugins"

    // the plugin version
    def version = "1.0.4-SNAPSHOT" // !!! Change in build.gradle too
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.4 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "UCB Messaging Plugin" // Headline display name of the plugin
    def author = "Brian Koehmstedt"
    def authorEmail = "bkoehmstedt@berkeley.edu"
    def description = '''\
Utility classes for message queues/topics and for processing with Camel.
'''

    // URL to the plugin's documentation
    def documentation = "https://github.com/calnet-oss/grails-ucb-messaging-plugin/"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "BSD"

    // Details of company behind the plugin (if there is one)
    def organization = [name: "University of California, Berkeley", url: "http://www.berkeley.edu/"]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
    def issueManagement = [system: "GitHub", url: "https://github.com/calnet-oss/grails-ucb-messaging-plugin/issues"]

    // Online location of the plugin's browseable source code.
    def scm = [url: "https://github.com/calnet-oss/grails-ucb-messaging-plugin"]

    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
    }

    def doWithDynamicMethods = { ctx ->
    }

    def doWithApplicationContext = { ctx ->
    }

    def onChange = { event ->
    }

    def onConfigChange = { event ->
    }

    def onShutdown = { event ->
    }
}
