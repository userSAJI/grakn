/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.client;

import ai.grakn.GraknConfigKey;
import ai.grakn.GraknSystemProperty;
import ai.grakn.util.CommonUtil;
import ai.grakn.util.REST;
import ai.grakn.util.SimpleURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Properties;

/**
 * Providing useful methods for the user of the GraknEngine client
 *
 * @author alexandraorth
 */
public class Client {

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private enum EngineStatus {
        Running(0),
        NotRunning(1),
        Error(2);

        private final int exitCode;

        EngineStatus(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    public static void main(String[] args) {
        EngineStatus engineStatus;
        try {
            engineStatus = checkServerRunning();
        } catch (Exception e) {
            LOG.error("An unexpected error occurred", e);
            engineStatus = EngineStatus.Error;
        }
        System.exit(engineStatus.exitCode);
    }

    private static EngineStatus checkServerRunning() throws IOException {
        String confPath = GraknSystemProperty.CONFIGURATION_FILE.value();

        if (confPath == null) {
            String msg = "System property `" + GraknSystemProperty.CONFIGURATION_FILE.key() + "` has not been set";
            LOG.error(msg);
            return EngineStatus.Error;
        }

        Properties properties = new Properties();
        try (FileInputStream stream = new FileInputStream(confPath)) {
            properties.load(stream);
        }

        String host = properties.getProperty(GraknConfigKey.SERVER_HOST_NAME.name());
        int port = Integer.parseInt(properties.getProperty(GraknConfigKey.SERVER_PORT.name()));
        if (serverIsRunning(new SimpleURI(host, port))) {
            LOG.info("Server " + host + ":" + port + " is running");
            return EngineStatus.Running;
        } else {
            LOG.info("Server " + host + ":" + port + " is not running");
            return EngineStatus.NotRunning;
        }
    }

    /**
     * Check if Grakn Engine has been started
     *
     * @return true if Grakn Engine running, false otherwise
     */
    public static boolean serverIsRunning(SimpleURI uri) {
        URL url;
        try {
            url = UriBuilder.fromUri(uri.toURI()).path(REST.WebPath.KB).build().toURL();
        } catch (MalformedURLException e) {
            throw CommonUtil.unreachableStatement(
                    "This will never throw because we're appending a known path to a valid URI", e
            );
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) mapQuadZeroRouteToLocalhost(url).openConnection();
        } catch (IOException e) {
            // If this fails, then the server is not reachable
            return false;
        }

        try {
            connection.setRequestMethod("GET");
        } catch (ProtocolException e) {
            throw CommonUtil.unreachableStatement(
                    "This will never throw because 'GET' is correct and the connection is not open yet", e
            );
        }

        int available;

        try {
            connection.connect();
            InputStream inputStream = connection.getInputStream();
            available = inputStream.available();
        } catch (IOException e) {
            // If this fails, then the server is not reachable
            return false;
        }

        return available != 0;
    }

    private static URL mapQuadZeroRouteToLocalhost(URL originalUrl) {
        final String QUAD_ZERO_ROUTE = "http://0.0.0.0";

        URL mappedUrl;
        if ((originalUrl.getProtocol() + originalUrl.getHost()).equals(QUAD_ZERO_ROUTE)) {
            try {
                mappedUrl = new URL(originalUrl.getProtocol(), "localhost", originalUrl.getPort(), REST.WebPath.KB);
            } catch (MalformedURLException e) {
                throw CommonUtil.unreachableStatement(
                        "This will never throw because the protocol is valid (because it came from another URL)", e
                );
            }
        } else {
            mappedUrl = originalUrl;
        }

        return mappedUrl;
    }

}
