package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmApiException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * OSM import service that fetches real data from the OpenStreetMap API.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {

    private static final String OSM_API_BASE_URL = "https://www.openstreetmap.org/api/0.6/node/";
    private final RestTemplate restTemplate;

    public OsmDataServiceImpl() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node {} from OpenStreetMap API", nodeId);

        String url = OSM_API_BASE_URL + nodeId;

        try {
            // Fetch XML response from OSM API
            // Set Accept header to ensure we get XML, not JSON
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Accept", "application/xml");
            org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("Unexpected response from OSM API for node {}: status={}", nodeId, response.getStatusCode());
                throw new OsmNodeNotFoundException(nodeId);
            }

            String xmlContent = response.getBody();
            log.info("Successfully fetched OSM XML for node {} (length: {} chars)", nodeId, 
                    xmlContent != null ? xmlContent.length() : 0);
            if (log.isDebugEnabled() && xmlContent != null) {
                log.debug("OSM XML content (first 500 chars): {}", 
                        xmlContent.length() > 500 ? xmlContent.substring(0, 500) : xmlContent);
            }

            // Parse XML and extract node data
            return parseOsmXml(xmlContent, nodeId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("OSM node {} not found (404)", nodeId);
            throw new OsmNodeNotFoundException(nodeId);
        } catch (HttpClientErrorException e) {
            log.error("HTTP client error fetching OSM node {}: status={}, message={}", 
                    nodeId, e.getStatusCode(), e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        } catch (OsmNodeNotFoundException e) {
            // Re-throw OsmNodeNotFoundException as-is (from parseOsmXml)
            throw e;
        } catch (HttpServerErrorException e) {
            log.error("HTTP server error fetching OSM node {}: status={}, message={}", 
                    nodeId, e.getStatusCode(), e.getMessage());
            // 5xx errors from OSM API indicate server problems, not missing nodes
            throw new OsmApiException(nodeId, "OSM API returned " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Network error fetching OSM node {}: {}", nodeId, e.getMessage(), e);
            // Network errors indicate the API is unavailable
            throw new OsmApiException(nodeId, "Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error fetching or parsing OSM node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmApiException(nodeId, "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Parses OSM XML content and extracts node information.
     *
     * @param xmlContent the XML content from OSM API
     * @param expectedNodeId the expected node ID (for validation)
     * @return an OsmNode with all extracted data
     * @throws OsmNodeNotFoundException if the XML cannot be parsed or node is not found
     */
    private @NonNull OsmNode parseOsmXml(@NonNull String xmlContent, @NonNull Long expectedNodeId) 
            throws OsmNodeNotFoundException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity resolution for security
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (Exception e) {
                log.warn("Could not set some XML security features, continuing anyway: {}", e.getMessage());
            }
            factory.setExpandEntityReferences(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            log.debug("Parsing OSM XML for node {}", expectedNodeId);
            Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
            log.debug("XML document parsed successfully");

            // Find the node element
            NodeList nodeList = doc.getElementsByTagName("node");
            log.debug("Found {} node element(s) in XML", nodeList.getLength());
            if (nodeList.getLength() == 0) {
                log.error("No node element found in OSM XML for node {}", expectedNodeId);
                // Log the root element to help debug
                if (doc.getDocumentElement() != null) {
                    log.error("Root element: {}", doc.getDocumentElement().getNodeName());
                }
                throw new OsmNodeNotFoundException(expectedNodeId);
            }

            Element nodeElement = (Element) nodeList.item(0);

            // Extract node ID
            String idStr = nodeElement.getAttribute("id");
            Long nodeId = Long.parseLong(idStr);
            if (!nodeId.equals(expectedNodeId)) {
                log.warn("Node ID mismatch: expected {}, got {}", expectedNodeId, nodeId);
            }

            // Extract latitude and longitude
            Double latitude = null;
            Double longitude = null;
            String latStr = nodeElement.getAttribute("lat");
            String lonStr = nodeElement.getAttribute("lon");
            if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                try {
                    latitude = Double.parseDouble(latStr);
                    longitude = Double.parseDouble(lonStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid lat/lon values for node {}: lat={}, lon={}", nodeId, latStr, lonStr);
                }
            }

            // Extract all tags
            Map<String, String> tags = new HashMap<>();
            NodeList tagList = nodeElement.getElementsByTagName("tag");
            for (int i = 0; i < tagList.getLength(); i++) {
                Element tagElement = (Element) tagList.item(i);
                String key = tagElement.getAttribute("k");
                String value = tagElement.getAttribute("v");
                if (!key.isEmpty() && !value.isEmpty()) {
                    tags.put(key, value);
                }
            }

            log.debug("Parsed OSM node {} with {} tags", nodeId, tags.size());

            return OsmNode.builder()
                    .nodeId(nodeId)
                    .tags(tags)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();

        } catch (OsmNodeNotFoundException e) {
            // Re-throw as-is
            throw e;
        } catch (Exception e) {
            log.error("Error parsing OSM XML for node {}: {} (class: {})", 
                    expectedNodeId, e.getMessage(), e.getClass().getName(), e);
            // If it's a parsing error, provide more context
            if (xmlContent != null && xmlContent.length() > 0) {
                log.error("XML content preview (first 200 chars): {}", 
                        xmlContent.length() > 200 ? xmlContent.substring(0, 200) : xmlContent);
            }
            throw new OsmNodeNotFoundException(expectedNodeId);
        }
    }
}
