/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 The Play Remote Configuration Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.playrconf.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import io.playrconf.sdk.AbstractProvider;
import io.playrconf.sdk.FileCfgObject;
import io.playrconf.sdk.KeyValueCfgObject;
import io.playrconf.sdk.exception.ProviderException;
import io.playrconf.sdk.exception.RemoteConfException;
import org.apache.commons.lang3.RegExUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Retrieves configuration from CoreOS etcd.
 *
 * @author Thibault Meyer
 * @since 18.04.01
 */
public class EtcdProvider extends AbstractProvider {

    /**
     * Contains the provider version.
     */
    private static String providerVersion;

    /**
     * Explore the Json document to retrieve valid data.
     *
     * @param prefix          The key prefix to remove
     * @param jsonNode        The Json node to explore
     * @param kvObjConsumer   The Key/Value object consumer
     * @param fileObjConsumer The File object consumer
     */
    private void exploreJsonNode(final String prefix,
                                 final JsonNode jsonNode,
                                 final Consumer<KeyValueCfgObject> kvObjConsumer,
                                 final Consumer<FileCfgObject> fileObjConsumer) {
        for (final JsonNode entry : jsonNode) {

            // Check if current node is a directory
            if (entry.hasNonNull("dir") && entry.get("dir").asBoolean()) {
                this.exploreJsonNode(prefix, entry.get("nodes"), kvObjConsumer, fileObjConsumer);
            } else if (entry.hasNonNull("value")) {

                // Process current configuration object
                final String cfgKey;
                if (prefix.isEmpty()) {
                    cfgKey = RegExUtils.removeFirst(
                        entry.get("key").asText(),
                        "/"
                    ).replace("/", ".");
                } else {
                    cfgKey = entry.get("key")
                        .asText()
                        .replace("/" + prefix + "/", "")
                        .replace("/", ".");
                }

                // Check if current configuration object is a file
                if (isFile(entry.get("value").asText())) {
                    fileObjConsumer.accept(
                        new FileCfgObject(cfgKey, entry.get("value").asText())
                    );
                } else {

                    // Standard configuration value
                    kvObjConsumer.accept(
                        new KeyValueCfgObject(cfgKey, entry.get("value").asText())
                    );
                }
            }
        }
    }

    @Override
    public String getName() {
        return "CoreOS etcd";
    }

    @Override
    public String getVersion() {
        if (EtcdProvider.providerVersion == null) {
            synchronized (EtcdProvider.class) {
                final Properties properties = new Properties();
                final InputStream is = EtcdProvider.class.getClassLoader()
                    .getResourceAsStream("playrconf-etcd.properties");
                try {
                    properties.load(is);
                    EtcdProvider.providerVersion = properties.getProperty("playrconf.etcd.version", "unknown");
                    properties.clear();
                    is.close();
                } catch (final IOException ignore) {
                }
            }
        }
        return EtcdProvider.providerVersion;
    }

    @Override
    public String getConfigurationObjectName() {
        return "etcd";
    }

    @Override
    public void loadData(final Config config,
                         final Consumer<KeyValueCfgObject> kvObjConsumer,
                         final Consumer<FileCfgObject> fileObjConsumer) throws ConfigException, RemoteConfException {
        String etcdEndpoint = config.getString("endpoint");
        String etcdPrefix = config.getString("prefix");

        // Quick check of vital configuration keys
        if (etcdEndpoint == null) {
            throw new ConfigException.BadValue(config.origin(), "endpoint", "Could not be null");
        } else if (!etcdEndpoint.startsWith("http")) {
            throw new ConfigException.BadValue(config.origin(), "endpoint", "Must start with http:// or https://");
        } else if (etcdPrefix == null) {
            throw new ConfigException.BadValue(config.origin(), "prefix", "Could not be null");
        }

        // Normalize configuration values
        if (!etcdEndpoint.endsWith("/")) {
            etcdEndpoint += "/";
        }
        if (etcdPrefix.endsWith("/")) {
            etcdPrefix = etcdPrefix.substring(0, etcdPrefix.length() - 1);
        }
        if (etcdPrefix.startsWith("/")) {
            etcdPrefix = etcdPrefix.substring(1, etcdPrefix.length());
        }

        // Get data from etcd
        InputStream is = null;
        try {
            final URL consulUrl = new URL(
                String.format(
                    "%sv2/keys/%s/?recursive=true",
                    etcdEndpoint,
                    etcdPrefix
                )
            );
            final HttpURLConnection conn = (HttpURLConnection) consulUrl.openConnection();
            if (config.hasPath("username")
                && config.hasPath("password")) {
                final String username = config.getString("username");
                final String password = config.getString("password");
                if (!username.isEmpty()) {
                    final String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes()
                    );
                    conn.setRequestProperty("Authorization", basicAuth);
                }
            }
            conn.setConnectTimeout(1500);
            if (conn.getResponseCode() / 100 == 2) {
                is = conn.getInputStream();
                final ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(is).get("node");
                if (jsonNode.get("dir").asBoolean()) {
                    jsonNode = jsonNode.get("nodes");

                    // Explore Json
                    this.exploreJsonNode(etcdPrefix, jsonNode, kvObjConsumer, fileObjConsumer);
                } else {
                    throw new ConfigException.BadValue("prefix", "Must reference a directory");
                }
            } else {
                throw new ProviderException("Return non 200 status: " + conn.getResponseCode());
            }
        } catch (final MalformedURLException ex) {
            throw new ConfigException.BadValue("endpoint", ex.getMessage());
        } catch (final IOException ex) {
            throw new ProviderException("Can't connect to the provider", ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException ignore) {
                }
            }
        }
    }
}
