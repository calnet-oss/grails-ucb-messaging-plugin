package edu.berkeley.messaging.plugin

import grails.plugins.Plugin

class UcbMessagingGrailsPlugin extends Plugin {

    def grailsVersion = "3.2.5 > *"
    def pluginExcludes = [
    ]

    def title = "UCB Messaging Plugin"
    def author = "Brian Koehmstedt"
    def authorEmail = "bkoehmstedt@berkeley.edu"
    def description = '''\
Utility classes for message queues/topics and for processing with Camel.
'''
    def profiles = []

    def documentation = "https://github.com/calnet-oss/grails-ucb-messaging-plugin/"

    def license = "BSD"

    def organization = [name: "University of California, Berkeley", url: "http://www.berkeley.edu/"]

    // Any additional developers beyond the author specified above.
    //def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    def issueManagement = [system: "GitHub", url: "https://github.com/calnet-oss/grails-ucb-messaging-plugin/issues"]

    def scm = [url: "https://github.com/calnet-oss/grails-ucb-messaging-plugin"]
}
