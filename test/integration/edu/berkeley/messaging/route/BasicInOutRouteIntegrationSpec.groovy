package edu.berkeley.messaging.route

import edu.berkeley.sor.query.util.CamelContextUtil
import edu.berkeley.sql.SqlService
import grails.test.spock.IntegrationSpec
import groovy.sql.Sql
import groovy.util.logging.Log4j
import org.apache.camel.CamelContext
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.ProducerTemplate
import org.apache.camel.model.RouteDefinition
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.json.JSONObject
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Shared
import spock.lang.Unroll

import javax.sql.DataSource

@Log4j
class BasicInOutRouteIntegrationSpec extends IntegrationSpec {

    static transactional = false

    TestService testService
    CamelContextUtil camelContextUtil
    Endpoint _sedaRouteEndpoint
    Endpoint _directRouteEndpoint

    // injected
    CamelContext camelContext
    SqlService sqlService
    DataSource dataSource
    ProducerTemplate producerTemplate
    GrailsApplication grailsApplication
    PlatformTransactionManager transactionManager

    @Shared
    int quantityToSend = 10

    def setup() {
        camelContextUtil = new CamelContextUtil(camelContext: camelContext)

        // Set up the testService
        grailsApplication.mainContext.registerBeanDefinition("testService", BeanDefinitionBuilder.genericBeanDefinition(TestService).beanDefinition)
        testService = (TestService) grailsApplication.mainContext.getBean("testService")
        assert producerTemplate
        testService.producerTemplate = producerTemplate
        testService.sqlService = sqlService
        assert transactionManager instanceof DataSourceTransactionManager
        testService.regTransactionManager = transactionManager

        // Launch the test routes
        Endpoint destinationEndpoint = camelContextUtil.getEndpoint("bean:testService?method=receiveMessage")
        // SEDA Route
        camelContext.addRoutes(new TestRoute(sedaRouteEndpoint, destinationEndpoint))
        // Direct Route
        camelContext.addRoutes(new TestRoute(directRouteEndpoint, destinationEndpoint))
        // start routes
        camelContext.startAllRoutes()


        Sql sql = new Sql(dataSource)
        sql.execute("CREATE TABLE Test (timeCreated TIMESTAMP)" as String)
    }

    def cleanup() {
        Sql sql = new Sql(dataSource)
        sql.execute("DROP TABLE IF EXISTS Test" as String)

        // stop the routes
        List<String> idsToRemove = []
        camelContext.routeDefinitions.each { RouteDefinition routeDef ->
            idsToRemove.add(routeDef.id)
        }
        idsToRemove.each { String id ->
            camelContext.stopRoute(id)
            assert camelContext.removeRoute(id)
        }

        // remove the testService
        grailsApplication.mainContext.removeBeanDefinition("testService")
    }

    @Unroll
    void "test Camel success, fault and exception behavior"() {
        given:
            new Sql(dataSource).execute("TRUNCATE TABLE Test" as String)
            assert !countTestRows()
            int successCount = 0
            int successMessageCount = 0
            int failureExceptionCount = 0
            int failureFaultCount = 0
            Map<String, Integer> exceptionMessageCount = [:]
            testService.receivePreparationClosure = receivePreparationClosure
            testService.routeEndpoint = routeEndpoint

        when:
            for (int i = 0; i < quantityToSend; i++) {
                testService.sendMessage(["test": "foobar"] as JSONObject, { def result ->
                    successCount++
                    if (result == ["result": "success"]) {
                        successMessageCount++
                    }
                }, { def input, boolean isFault, Throwable exception ->
                    if (exception) {
                        failureExceptionCount++
                        if (!exceptionMessageCount[exception.message])
                            exceptionMessageCount[exception.message] = 1
                        else
                            exceptionMessageCount[exception.message] = exceptionMessageCount[exception.message] + 1
                    } else {
                        failureFaultCount++
                    }
                })
            }

        then:
            // possibly asynchronous if configured that way, so wait a little bit for completion if necessary
            int i
            for (i = 0; i < 600; i++) {
                if (successCount + failureExceptionCount + failureFaultCount >= quantityToSend) {
                    break
                }
                sleep(100) // sleep 100ms each iteration
            }
            log.info("Received within ${i * 100}ms and successCount = $successCount")
            assert testName && exceptionMessageCount.keySet() != null && countTestRows() == expectedTestRows
            assert testName && exceptionMessageCount.keySet() != null && successCount == expectedSuccessCount
            assert testName && exceptionMessageCount.keySet() != null && successMessageCount == expectedSuccessCount
            assert testName && exceptionMessageCount.keySet() != null && failureExceptionCount == expectedExceptionCount
            assert testName && exceptionMessageCount.keySet() != null && (exceptionMessageCount[expectedExceptionMessage] ?: 0) == expectedExceptionCount
            assert testName && exceptionMessageCount.keySet() != null && failureFaultCount == expectedFaultCount

        where:
            //
            // Test that success messsages, ecceptions thrown and fault messages are all handled correctly.
            // Also test transaction rollbacks on database inserts when an exception or fault occurs.
            //
            testName          | routeEndpoint       | receivePreparationClosure | expectedSuccessCount | expectedTestRows | expectedExceptionCount | expectedExceptionMessage | expectedFaultCount
            "sedaSuccess"     | sedaRouteEndpoint   | successClosure            | quantityToSend       | quantityToSend   | 0                      | null                     | 0
            "sedaException"   | sedaRouteEndpoint   | exceptionClosure          | 0                    | 0                | quantityToSend         | "test exception"         | 0
            "sedaFault"       | sedaRouteEndpoint   | faultClosure              | 0                    | quantityToSend   | quantityToSend         | "{result=success}"       | 0
            "directSuccess"   | directRouteEndpoint | successClosure            | quantityToSend       | quantityToSend   | 0                      | null                     | 0
            "directException" | directRouteEndpoint | exceptionClosure          | 0                    | 0                | quantityToSend         | "test exception"         | 0
            "directFault"     | directRouteEndpoint | faultClosure              | 0                    | quantityToSend   | quantityToSend         | "{result=success}"       | 0
    }

    void performTestTransaction(Sql sql) {
        sql.execute("INSERT INTO Test (timeCreated) VALUES(?)" as String, [new Date()])
    }

    int countTestRows() {
        Sql sql = new Sql(dataSource)
        return sql.firstRow("SELECT count(*) AS count FROM Test" as String).count
    }

    Closure getSuccessClosure() {
        return { Exchange exchange, Sql sql ->
            log.info("PREP")
            performTestTransaction(sql)
        }
    }

    Closure getExceptionClosure() {
        return { Exchange exchange, Sql sql ->
            performTestTransaction(sql)
            throw new RuntimeException("test exception")
        }
    }

    Closure getFaultClosure() {
        return { Exchange exchange, Sql sql ->
            performTestTransaction(sql)
            exchange.out.fault = true
        }
    }

    Endpoint getSedaRouteEndpoint() {
        if (!_sedaRouteEndpoint) {
            _sedaRouteEndpoint = new CamelContextUtil(camelContext: camelContext).getEndpoint("seda:routeEndpoint?multipleConsumers=true&blockWhenFull=true"/*"direct:routeEndpoint"*/)
        }
        return _sedaRouteEndpoint
    }

    Endpoint getDirectRouteEndpoint() {
        if (!_directRouteEndpoint) {
            _directRouteEndpoint = new CamelContextUtil(camelContext: camelContext).getEndpoint("direct:routeEndpoint")
        }
        return _directRouteEndpoint
    }
}