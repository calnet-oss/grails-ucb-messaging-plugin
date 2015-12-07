package edu.berkeley.messaging.route

import edu.berkeley.sql.SqlService
import grails.transaction.Transactional
import groovy.sql.Sql
import groovy.util.logging.Log4j
import org.apache.camel.Endpoint
import org.apache.camel.Exchange
import org.apache.camel.ExchangePattern
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultExchange
import org.codehaus.groovy.grails.web.json.JSONObject
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation

@Log4j
@Transactional(propagation = Propagation.NEVER, rollbackFor = Exception)
class TestService {
    Endpoint routeEndpoint
    ProducerTemplate producerTemplate
    SqlService sqlService
    PlatformTransactionManager regTransactionManager

    Closure receivePreparationClosure

    void sendMessage(JSONObject json, Closure successClosure, Closure failureClosure) {

        DefaultExchange _exchange = new DefaultExchange(routeEndpoint, ExchangePattern.InOut)
        _exchange.in.body = json
        _exchange.setProperty("successClosure", successClosure)
        _exchange.setProperty("failureClosure", failureClosure)
        producerTemplate.asyncCallback(routeEndpoint, _exchange, null)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception)
    Map receiveMessage(Exchange exchange) {
        Map inparam = (Map) exchange.in.body
        Sql sql = getRegSqlInstance()
        if (receivePreparationClosure) {
            receivePreparationClosure(exchange, sql)
        }
        return ["result": "success"]
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Sql getRegSqlInstance() {
        assert regTransactionManager
        return sqlService.getCurrentTransactionSql(regTransactionManager)
    }
}
