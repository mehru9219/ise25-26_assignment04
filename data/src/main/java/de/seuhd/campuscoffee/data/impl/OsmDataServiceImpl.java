package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OSM import service that fetches node data from the OpenStreetMap API.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {

    private static final String OSM_API_BASE_URL = "https://www.openstreetmap.org/api/0.6/node/";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;

    public OsmDataServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node data for node ID: {}", nodeId);

        try {
            // Build the API request
            String apiUrl = OSM_API_BASE_URL + nodeId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();

            // Execute the request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Check response status
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                log.warn("OSM node {} not found (HTTP {})", nodeId, response.statusCode());
                throw new OsmNodeNotFoundException(nodeId);
            }

            if (response.statusCode() != 200) {
                log.error("OSM API returned unexpected status code {} for node {}", response.statusCode(), nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }

            // Parse the XML response
            String xmlContent = response.body();
            log.debug("Received OSM XML response for node {}: {}", nodeId, xmlContent);

            return parseOsmXml(nodeId, xmlContent);

        } catch (OsmNodeNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching OSM node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    /**
     * Parses the OSM XML response and extracts node data.
     *
     * @param nodeId The node ID being parsed
     * @param xmlContent The XML content from the OSM API
     * @return An OsmNode object with extracted data
     * @throws Exception if parsing fails
     */
    private OsmNode parseOsmXml(Long nodeId, String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));

        // Get the node element
        NodeList nodeElements = document.getElementsByTagName("node");
        if (nodeElements.getLength() == 0) {
            log.error("No <node> element found in OSM XML response");
            throw new OsmNodeNotFoundException(nodeId);
        }

        Element nodeElement = (Element) nodeElements.item(0);

        // Extract all tags into a map
        Map<String, String> tags = new HashMap<>();
        NodeList tagElements = nodeElement.getElementsByTagName("tag");
        for (int i = 0; i < tagElements.getLength(); i++) {
            Element tagElement = (Element) tagElements.item(i);
            String key = tagElement.getAttribute("k");
            String value = tagElement.getAttribute("v");
            tags.put(key, value);
        }

        log.info("Extracted {} tags from OSM node {}", tags.size(), nodeId);
        log.debug("Tags: {}", tags);

        // Build the OsmNode object from the extracted tags
        return OsmNode.builder()
                .nodeId(nodeId)
                .name(tags.get("name"))
                .amenity(tags.get("amenity"))
                .description(tags.get("description"))
                .addrStreet(tags.get("addr:street"))
                .addrHouseNumber(tags.get("addr:housenumber"))
                .addrPostcode(tags.get("addr:postcode"))
                .addrCity(tags.get("addr:city"))
                .build();
    }
}
