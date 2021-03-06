/*
 * Copyright 2014 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.console.charts

import com.jcraft.jsch.Session
import grails.converters.JSON
import grails.util.Holders
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.mapping.LinkGenerator
import org.springframework.beans.factory.annotation.Autowired

import java.sql.Connection
import java.sql.SQLException

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class ConsoleChartsController {

    static defaultAction = 'data'

    def chartsEncryprionService
    def consoleChartsService

    @Autowired
    LinkGenerator linkGenerator

    def connect(String data) {
        if (!data) {
            render([connected: false, error: 'empty_data'] as JSON)
            return
        }
        def json = null

        if (!data.contains('{')) {
            try {
                data = chartsEncryprionService.decrypt(data)
            } catch (e) {
                render([connected: false, error: 'wrong_connection_string'] as JSON)
                return
            }
        }

        try {
            json = JSON.parse(data)
        } catch (ConverterException e) {
            render([connected: false, error: 'wrong_json'] as JSON)
            return
        }

        String mysqlHostname = json.mysqlHostname ?: 'localhost'
        Integer mysqlPort = json.mysqlPort ?: 3306

        Session sshSession = null

        if (json.sshToggle) {
            mysqlPort = 3368
            mysqlHostname = 'localhost'

            if (!json.sshHostname) {
                render([connected: false, error: 'ssh_hostname_empty'] as JSON)
                return
            }

            if (!json.sshUsername) {
                render([connected: false, error: 'ssh_username_empty'] as JSON)
                return
            }

            sshSession = consoleChartsService.doSshTunnel(json.sshHostname, json.sshPort ?: 22, json.sshUsername,
                    json.sshPassword, json.mysqlHostname, mysqlPort, json.mysqlPort ?: 3306)
        }

        if (!json.mysqlHostname) {
            sshSession?.disconnect()

            render([connected: false, error: 'mysql_hostname_empty'] as JSON)
            return
        }

        if (!json.mysqlUsername) {
            sshSession?.disconnect()

            render([connected: false, error: 'mysql_username_empty'] as JSON)
            return
        }

        try {
            Connection connection = consoleChartsService
                    .connectToMySql(mysqlHostname, mysqlPort, json.mysqlUsername, json.mysqlPassword)

            connection.close()
            sshSession?.disconnect()

            String encodedString = chartsEncryprionService.encrypt(json.toString())

            String status = "${json.sshToggle ? "${json.mysqlHostname}:${json.mysqlPort}" : "${mysqlHostname}:${mysqlPort}"}${json.sshToggle ? " through ${json.sshHostname}:${json.sshPort}" : ''}"

            render([connected       : true,
                    connectionString: encodedString,
                    status          : "Connected to ${status}"] as JSON)
        } catch (IOException | SQLException e) {
            sshSession?.disconnect()

            render([connected: false, error: "${e.message ?: ''} ${e.cause?.message ?: ''}", exception: e.class.canonicalName] as JSON)
        }
    }

    def data(String query, String connectionString, String appearance) {
        try {
            if (!query) {
                render([error: true, text: 'Query is empty'] as JSON)
                return
            }

            // decode parameters
            query = chartsEncryprionService.decodeBase64(query)
            connectionString = chartsEncryprionService.decrypt(connectionString)
            appearance = chartsEncryprionService.decodeBase64(appearance)

            render(consoleChartsService.getData(query, connectionString, appearance, request) as JSON)
        } catch (e) {
            render([error: true, text: "${e.message ?: ''} ${e.cause?.message ?: ''}", exception: e.class.canonicalName] as JSON)
        }
    }

    def link() {
        def json = request.JSON as JSON
        String data = json.toString()
        String encodedData = chartsEncryprionService.encrypt(data)
        String link =
                createLink(controller: 'consoleCharts', action: 'view', params: [q: encodedData], absolute: true)

        render([link: link] as JSON)
    }

    def view(String q) {
        if (!q) {
            return [error: true, text: 'Q is empty']
        }

        String decoded = null
        def json = null
        String connectionString = null
        def data = null

        try {
            decoded = chartsEncryprionService.decrypt(q)
        } catch (e) {
            return [error: true, exception: e, text: "Can't decode data", q: q]
        }

        try {
            json = JSON.parse(decoded)
        } catch (e) {
            return [error: true, exception: e, text: "Can't parse JSON", q: q, decoded: decoded]
        }

        try {
            connectionString = chartsEncryprionService.decrypt(json.connectionString)
        } catch (e) {
            return [error: true, exception: e, text: "Can't decode connection string", q: q]
        }

        String query = json.query
        String appearance = json.appearance
        Boolean editable = json.editable
        String editLink = null

        if (editable || Holders.config.grails.plugin.console.charts.editable)
            editLink = linkGenerator.link(uri: '/console/charts', absolute: true) +
                    "#home;connectionString=${chartsEncryprionService.encodePathSegment(chartsEncryprionService.encodePathSegment(json.connectionString)).encodeAsURL()};" +
                    "appearance=${appearance ? chartsEncryprionService.encodePathSegment(chartsEncryprionService.encodePathSegment(chartsEncryprionService.encodeBase64(appearance))).encodeAsURL() : ''};" +
                    "query=${chartsEncryprionService.encodePathSegment(chartsEncryprionService.encodePathSegment(chartsEncryprionService.encodeBase64(query))).encodeAsURL()}"

        try {
            data = consoleChartsService.getData(query, connectionString, appearance, request)
        } catch (e) {
            return [error: true, exception: e, text: "Can't get data", q: q, decoded: decoded, editLink: editLink]
        }

        data.title = json.title
        data.width = json.width
        data.height = json.height
        data.query = query
        data.connectionString = connectionString
        data.appearance = appearance
        data.editLink = editLink

        data
    }

}
