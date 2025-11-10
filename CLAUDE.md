# Project Requirement Proposal (PRP) - OSM Import Feature

You are a senior software engineer working on the CampusCoffee application.
Use the information below to implement the OpenStreetMap (OSM) import feature for Point-of-Sale locations.

## Goal

**Feature Goal**: Enable the CampusCoffee application to automatically import Point-of-Sale (POS) data from OpenStreetMap by providing an OSM node ID, eliminating manual data entry for existing cafés, bakeries, and vending machines.

**Deliverable**:
- A fully functional REST API endpoint `POST /api/pos/import/osm/{nodeId}` that fetches OSM data and creates POS entries
- Complete implementation of OSM XML parsing logic
- Proper mapping between OSM tags and CampusCoffee domain model

**Success Definition**:
- The endpoint successfully imports the Rada Coffee & Rösterei location (OSM node 5589879349)
- All required fields (name, address, amenity type) are correctly mapped from OSM tags
- The system handles missing or invalid OSM data gracefully
- All existing tests pass and new functionality is covered by tests

## User Persona

**Target User**: CampusCoffee administrators and power users who want to quickly populate the database with existing coffee shops and cafés from OpenStreetMap

**Use Case**: An administrator discovers a new café near the university campus that is already documented in OpenStreetMap and wants to add it to the CampusCoffee system without manually entering all the address details.

**User Journey**:
1. User finds a café on OpenStreetMap (e.g., browsing the map)
2. User identifies the OSM node ID (e.g., 5589879349 for Rada Coffee)
3. User sends a POST request to `/api/pos/import/osm/5589879349`
4. System fetches the OSM data, parses it, and creates a new POS entry
5. User receives a 201 Created response with the newly created POS details

**Pain Points Addressed**:
- Eliminates tedious manual data entry for locations that already exist in OpenStreetMap
- Reduces data entry errors by pulling from a verified source
- Speeds up the onboarding of new POS locations

## Why

- **Business value**: Accelerates database population with accurate, community-verified location data
- **Integration with existing features**: Extends the current POS management system with an automated import mechanism
- **Problems this solves**: Reduces administrative burden and ensures data accuracy by leveraging OpenStreetMap's comprehensive database of amenities

## What

Implement a complete OSM import pipeline that:

1. **Accepts OSM node IDs** via the existing REST endpoint `POST /api/pos/import/osm/{nodeId}`
2. **Fetches OSM data** from the OpenStreetMap API (`https://www.openstreetmap.org/api/0.6/node/{nodeId}`)
3. **Parses XML response** to extract node attributes and tags
4. **Maps OSM data** to the CampusCoffee domain model:
   - OSM `name` tag → POS name
   - OSM `amenity` tag → POS type (cafe, bakery, vending_machine → CAFE, BAKERY, VENDING_MACHINE)
   - OSM `description` or derived from `amenity` → POS description
   - OSM `addr:street` → street
   - OSM `addr:housenumber` → houseNumber
   - OSM `addr:postcode` → postalCode (convert string to Integer)
   - OSM `addr:city` → city
   - Campus determination based on postal code or heuristics
5. **Creates POS entry** via the existing upsert mechanism
6. **Handles errors** gracefully (node not found, missing required fields, parsing errors)

### Technical Requirements

#### OSM XML Format
The OpenStreetMap API returns XML in the following format:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<osm version="0.6" generator="openstreetmap-cgimap 2.1.0">
  <node id="5589879349" lat="49.4122362" lon="8.7077883"
        timestamp="2025-05-08T13:35:07Z" version="11"
        changeset="165984125" user="Niiepce" uid="12992079">
    <tag k="addr:city" v="Heidelberg"/>
    <tag k="addr:country" v="DE"/>
    <tag k="addr:housenumber" v="21"/>
    <tag k="addr:postcode" v="69117"/>
    <tag k="addr:street" v="Untere Straße"/>
    <tag k="amenity" v="cafe"/>
    <tag k="name" v="Rada"/>
    <tag k="description" v="Caffé und Rösterei"/>
    <!-- Additional tags omitted for brevity -->
  </node>
</osm>
```

#### Required Changes

1. **Enhance `OsmNode` record** (domain/src/main/java/de/seuhd/campuscoffee/domain/model/OsmNode.java):
   - Add fields for all relevant OSM tags (name, amenity, address components, description)
   - Use Java records with @Builder annotation for immutability

2. **Implement `OsmDataServiceImpl`** (data/src/main/java/de/seuhd/campuscoffee/data/OsmDataServiceImpl.java):
   - Replace stub implementation with real HTTP client
   - Use standard Java HTTP client or Spring's RestTemplate/WebClient
   - Parse XML response to extract node data and tags
   - Handle HTTP errors (404 for non-existent nodes, network errors)
   - Throw `OsmNodeNotFoundException` when node doesn't exist

3. **Implement `convertOsmNodeToPos`** (domain/src/main/java/de/seuhd/campuscoffee/domain/PosServiceImpl.java):
   - Replace hardcoded conversion with dynamic mapping
   - Map OSM amenity values to PosType enum:
     - "cafe" → CAFE
     - "bakery" → BAKERY
     - "vending_machine" → VENDING_MACHINE
     - Default to CAFE for other amenity types
   - Determine campus based on postal code:
     - 69117 → ALTSTADT
     - 69115 → BERGHEIM
     - 69120, 69121 → INF
     - Default to ALTSTADT for unknown codes
   - Convert postcode string to Integer
   - Throw `OsmNodeMissingFieldsException` if required fields (name, amenity, city) are missing

4. **Testing**:
   - Update `PosSystemTests` to test the OSM import endpoint
   - Test successful import with node 5589879349
   - Test error cases (invalid node ID, missing fields)

### Success Criteria

- [x] `OsmNode` record contains all necessary fields from OSM tags
- [x] `OsmDataServiceImpl.fetchNode()` successfully calls OSM API and parses XML response
- [x] OSM amenity types are correctly mapped to PosType enum values
- [x] OSM address components are correctly mapped to POS address fields
- [x] Campus is determined from postal code
- [x] Missing required fields throw `OsmNodeMissingFieldsException`
- [x] Non-existent nodes throw `OsmNodeNotFoundException`
- [x] Existing system tests continue to pass
- [x] The Rada Coffee location (node 5589879349) imports successfully
- [x] HTTP client properly handles network errors and timeouts

## Documentation & References

### Project Structure

This Java Spring Boot application is structured as a multi-module Maven project following the **ports-and-adapters (hexagonal) architectural pattern**.

**Modules**:
- `api` - REST controller layer (PosController, DTOs, mappers)
- `application` - Spring Boot bootstrap, initialization, system tests
- `data` - Data adapters (JPA repositories, external API clients, entity mappers)
- `domain` - Core business logic, domain models, service interfaces (ports)

### Key Files to Modify

1. **domain/src/main/java/de/seuhd/campuscoffee/domain/model/OsmNode.java**
   - Currently only contains `nodeId`
   - Add: name, amenity, description, addrStreet, addrHouseNumber, addrPostcode, addrCity

2. **data/src/main/java/de/seuhd/campuscoffee/data/OsmDataServiceImpl.java**
   - Currently returns hardcoded data
   - Implement: HTTP client, XML parsing, error handling

3. **domain/src/main/java/de/seuhd/campuscoffee/domain/PosServiceImpl.java**
   - Method `convertOsmNodeToPos()` is hardcoded
   - Implement: Dynamic mapping from OsmNode to Pos

4. **application/src/test/java/de/seuhd/campuscoffee/PosSystemTests.java**
   - Add test cases for OSM import functionality

### Architecture Guidelines

- **Dependency Flow**: api → domain ← data, application (hexagonal architecture)
- **Domain Layer**: Contains business logic, defines ports (interfaces), no external dependencies
- **Data Layer**: Implements ports, handles external I/O (database, HTTP clients)
- **Immutability**: Use Java records with builders for domain models
- **Error Handling**: Throw domain exceptions (OsmNodeNotFoundException, OsmNodeMissingFieldsException)
- **Logging**: Use SLF4J logger for debugging and error tracking
- **Testing**: Write integration tests that verify end-to-end functionality

### XML Parsing Options

For parsing OSM XML responses, consider:
1. **DOM Parser** (javax.xml.parsers.DocumentBuilder): Simple, good for small responses
2. **SAX Parser**: More memory-efficient for large responses
3. **JAXB**: Type-safe mapping with annotations (requires schema/classes)
4. **Jackson XML** or **XStream**: Alternative libraries for XML→Object mapping

Recommendation: Use DOM Parser for simplicity, as OSM node responses are small (~1-2 KB).

### OSM API Specification

- **Endpoint**: `https://www.openstreetmap.org/api/0.6/node/{id}`
- **Response Format**: XML (UTF-8)
- **Rate Limiting**: OSM API has rate limits; handle HTTP 429 responses appropriately
- **Error Responses**:
  - 404: Node not found
  - 410: Node deleted
  - 429: Rate limit exceeded

### Dependencies Available

Check `pom.xml` for available dependencies. You may need to add:
- HTTP client (already available via Spring Boot)
- XML parsing libraries (standard Java XML libraries are available)

### Example Test Data

**OSM Node**: 5589879349 (Rada Coffee & Rösterei)
- Name: "Rada"
- Amenity: "cafe"
- Street: "Untere Straße"
- House Number: "21"
- Postal Code: "69117"
- City: "Heidelberg"

**Expected POS Entry**:
- name: "Rada"
- description: "Caffé und Rösterei" (or derived from amenity)
- type: CAFE
- campus: ALTSTADT (based on 69117)
- street: "Untere Straße"
- houseNumber: "21"
- postalCode: 69117
- city: "Heidelberg"

## Implementation Strategy

1. **Start with the data model**: Extend `OsmNode` record to hold all necessary OSM tag data
2. **Implement HTTP client**: Create HTTP GET request to OSM API in `OsmDataServiceImpl`
3. **Parse XML response**: Extract node attributes and tags into `OsmNode` object
4. **Implement mapping logic**: Convert `OsmNode` to `Pos` in `convertOsmNodeToPos()`
5. **Handle edge cases**: Missing fields, invalid data, network errors
6. **Test thoroughly**: Verify with real OSM node 5589879349
7. **Update tests**: Add integration tests for the new functionality

## Additional Context

- The endpoint skeleton already exists in `PosController` (line reference: api/src/main/java/de/seuhd/campuscoffee/api/PosController.java)
- The service interface already declares `importFromOsmNode()` method (domain/src/main/java/de/seuhd/campuscoffee/domain/PosService.java)
- The current implementation is a stub that only works for hardcoded node ID 5589879349
- The goal is to make it work for ANY valid OSM node with appropriate tags

## Notes on Campus Determination

Since OSM data doesn't include campus information, use this heuristic:
- Postal code 69117 → ALTSTADT (old town)
- Postal code 69115 → BERGHEIM
- Postal codes 69120, 69121 → INF (Informatik campus)
- Unknown postal codes → Default to ALTSTADT

This can be refined in future iterations based on geographic coordinates if needed.
