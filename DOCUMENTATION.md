# Assignment 4 Documentation - OSM Import Feature Implementation

**Student Name:** [Your Name Here]
**Matrikelnummer:** [Your Matriculation Number Here]
**Date:** 10.11.2025
**Repository:** https://github.com/mehru9219/ise25-26_assignment04

---

## Task 4.1(b): GenAI Tool Selection (0.5 Points)

### Selected Tool: Claude Code (Sonnet 4.5)

**Justification:**
I chose Claude Code for this assignment because:

1. **Integrated Development Experience**: Claude Code is a command-line interface tool that integrates directly with the development workflow, providing file reading, editing, and git operations without switching contexts.

2. **Context Understanding**: Claude Code has excellent context window management and can understand large codebases by exploring the project structure systematically. This is crucial for understanding the hexagonal architecture used in CampusCoffee.

3. **Code Quality**: Sonnet 4.5 model produces high-quality, production-ready code with proper error handling, logging, and documentation comments.

4. **Architecture Awareness**: The tool understands software architecture patterns (like ports-and-adapters used in this project) and can maintain consistency with existing code patterns.

5. **No Account Required**: Unlike Cursor Pro or GitHub Copilot, I could use Claude Code immediately without account setup or credit card verification.

---

## Task 4.1(c): Prompt Development (1.5 Points)

### Approach

I used a structured approach combining the PRP (Project Requirement Proposal) template from the repository with additional context about the OSM XML format and the CampusCoffee architecture.

### Initial Prompt

My initial prompt to Claude Code was:

```
read @assignment.md and do all the work
```

This intentionally broad prompt allowed Claude Code to:
1. Analyze the assignment requirements
2. Explore the codebase structure
3. Determine the necessary steps
4. Create a detailed implementation plan

### Context Provided by Claude Code (Automatic)

Claude Code automatically included the following context:
- Working directory: `C:\Users\DELL\Desktop\ass4`
- Platform information (Windows)
- Current date for context

Claude Code then autonomously:
1. Read the assignment file
2. Cloned the GitHub repository
3. Read README.md and CHANGELOG.md
4. Launched an exploration agent to understand the codebase structure

### Generated Documentation File: CLAUDE.md

Instead of manually crafting prompts, I let Claude Code create a comprehensive specification document (`CLAUDE.md`) that serves as both:
- A prompt template for implementing the feature
- Documentation of the requirements and architecture

**Structure of CLAUDE.md:**

```markdown
# Project Requirement Proposal (PRP) - OSM Import Feature

## Goal
- Feature Goal: Enable OSM import by node ID
- Deliverable: Functional REST endpoint with XML parsing
- Success Definition: Import works for any valid OSM node

## User Persona
- Target User: CampusCoffee administrators
- Use Case: Quick import of existing cafés from OSM
- User Journey: 5-step process from finding OSM node to creation

## Why
- Business value and integration with existing features
- Problem solving: eliminates manual data entry

## What
- Technical requirements and OSM XML format specification
- Field mapping documentation
- Error handling requirements

## Documentation & References
- Project structure explanation
- Key files to modify with line references
- Architecture guidelines (hexagonal pattern)
- OSM API specification
- Campus determination heuristics
```

**Key Context Elements in CLAUDE.md:**

1. **OSM XML Format Specification:**
   - Documented the actual XML structure from the OSM API
   - Included example for node 5589879349 (Rada Coffee)
   - Explained tag structure with k/v attributes

2. **Mapping Requirements:**
   - OSM amenity → PosType mapping (cafe→CAFE, bakery→BAKERY, etc.)
   - OSM address tags → POS address fields
   - Postal code → Campus determination logic
   - Required vs optional fields

3. **Architecture Context:**
   - Hexagonal (ports-and-adapters) pattern explanation
   - Module responsibilities (api, domain, data, application)
   - Dependency flow rules
   - Immutability requirements (Java records)

4. **Files to Modify:**
   - `OsmNode.java` - extend with OSM tag fields
   - `OsmDataServiceImpl.java` - implement HTTP client and XML parsing
   - `PosServiceImpl.java` - implement dynamic conversion logic

### Why This Approach Worked

**Advantages over traditional prompting:**
- Claude Code systematically explored the codebase before making changes
- The CLAUDE.md file serves as both specification and documentation
- Context was gathered incrementally (codebase structure, OSM API format, existing patterns)
- The approach was iterative: understand → document → implement

**Context Sources:**
1. **Assignment requirements** (assignment.md)
2. **Existing codebase structure** (explored via Task agent)
3. **OSM API documentation** (fetched live from openstreetmap.org)
4. **PRP template** (doc/prp/0_template.md in repository)

---

## Task 4.1(d): Implementation (1.5 Points)

### Implementation Process

The implementation followed a systematic approach:

#### Step 1: Codebase Exploration
Claude Code launched an "Explore" agent with medium thoroughness to understand:
- Module structure (api, application, data, domain)
- Existing domain models (Pos, OsmNode, PosType, CampusType)
- Service layer architecture (PosService, PosDataService, OsmDataService)
- Controller structure and existing endpoints
- Current stub implementation limitations

#### Step 2: OSM API Analysis
- Fetched real OSM data for node 5589879349
- Analyzed XML structure and tag format
- Identified required fields: name, amenity, city
- Documented optional fields: description, address components

#### Step 3: Implementation in Layers

**3.1 Domain Model Extension (OsmNode.java)**

Before:
```java
@Builder
public record OsmNode(@NonNull Long nodeId) {
    // TODO: extend with OSM fields
}
```

After:
```java
@Builder
public record OsmNode(
    @NonNull Long nodeId,
    @Nullable String name,
    @Nullable String amenity,
    @Nullable String description,
    @Nullable String addrStreet,
    @Nullable String addrHouseNumber,
    @Nullable String addrPostcode,
    @Nullable String addrCity
) {}
```

**3.2 Data Service Implementation (OsmDataServiceImpl.java)**

Replaced stub implementation with:
- Java 11+ HttpClient for OSM API calls
- 10-second timeout configuration
- HTTP error handling (404, 410 for missing nodes)
- DOM-based XML parser
- Tag extraction into HashMap
- OsmNode builder population

Key implementation details:
```java
// HTTP Client
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// XML Parsing
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
DocumentBuilder builder = factory.newDocumentBuilder();
Document document = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));

// Tag extraction
NodeList tagElements = nodeElement.getElementsByTagName("tag");
for (int i = 0; i < tagElements.getLength(); i++) {
    Element tag = (Element) tagElements.item(i);
    tags.put(tag.getAttribute("k"), tag.getAttribute("v"));
}
```

**3.3 Business Logic Implementation (PosServiceImpl.java)**

Replaced hardcoded conversion with dynamic mapping:

**Amenity Type Mapping:**
```java
private PosType mapAmenityToPosType(String amenity) {
    return switch (amenity.toLowerCase()) {
        case "cafe", "coffee_shop" -> PosType.CAFE;
        case "bakery" -> PosType.BAKERY;
        case "vending_machine" -> PosType.VENDING_MACHINE;
        case "cafeteria", "restaurant", "fast_food", "bar", "pub" -> PosType.CAFETERIA;
        default -> PosType.CAFE; // reasonable default
    };
}
```

**Campus Determination:**
```java
private CampusType determineCampus(Integer postalCode) {
    return switch (postalCode) {
        case 69117 -> CampusType.ALTSTADT;
        case 69115 -> CampusType.BERGHEIM;
        case 69120, 69121 -> CampusType.INF;
        default -> CampusType.ALTSTADT; // default for unknown
    };
}
```

**Validation:**
```java
if (osmNode.name() == null || osmNode.name().isBlank()) {
    throw new OsmNodeMissingFieldsException(osmNode.nodeId());
}
// Similar checks for amenity and city
```

#### Step 4: Code Quality Enhancements

Claude Code automatically added:
- Comprehensive JavaDoc comments
- SLF4J logging at appropriate levels (info, debug, warn, error)
- Error messages with context
- Defensive programming (null checks, trim, toLowerCase)
- Smart defaults (description generation, default campus)

### Files Modified

| File | Lines Changed | Purpose |
|------|---------------|---------|
| `domain/model/OsmNode.java` | +22, -3 | Extended with OSM tag fields |
| `data/impl/OsmDataServiceImpl.java` | +115, -14 | HTTP client + XML parser |
| `domain/impl/PosServiceImpl.java` | +139, -15 | Dynamic conversion logic |
| `CLAUDE.md` | +273 | Feature specification |

**Total:** 4 files changed, 499 insertions(+), 25 deletions(-)

### Git Commit

```bash
git commit -m "Implement OSM import feature for Point-of-Sale locations

- Extended OsmNode record to include all relevant OSM tags
- Implemented HTTP client in OsmDataServiceImpl
- Added XML parsing logic
- Implemented dynamic conversion from OsmNode to Pos
- Added amenity type mapping
- Added campus determination based on postal codes
- Added validation for required fields
- Added CLAUDE.md documentation

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Testing Strategy (Not Executed Due to Build Tool Limitations)

**Intended Test Cases:**

1. **Successful Import:**
   ```bash
   curl -X POST http://localhost:8080/api/pos/import/osm/5589879349
   ```
   Expected: 201 Created with Rada Coffee data

2. **Missing Node:**
   ```bash
   curl -X POST http://localhost:8080/api/pos/import/osm/9999999999
   ```
   Expected: 404 with OsmNodeNotFoundException

3. **Node with Missing Fields:**
   Test with an OSM node that lacks name/amenity/city tags
   Expected: 400 with OsmNodeMissingFieldsException

**Note:** Actual testing was not performed because Maven is not installed in the current environment and Java version is 1.8 instead of required JDK 21.

---

## Task 4.1(e): Reflection (1.5 Points)

### Tool-Auswahl: Claude Code

Ich habe Claude caude verwendet, da es sehr einfach zu impklemenetieren ist, und kostenlos im vs code editor. es kann alle möglichen Befehle handhaben sowei Dateioperqationen.

### Warum Claude Code?

1. funktioniert in vs code
2. versthet den kontext über mehrere Datein hinweg
3. kostenlos

### Was ich erwartet habe vs. Was tatsächlich passiert ist

die errklärungen des AI tools waren für meinen gesachmack etwas zu ausführlich und ich habe etwas kürzere erhofft.

ich hatte ausserdem direktere Lösungen im kopf, während das tool mir schwerere multi step prozesse vor geschlagen hat.

### Learnings

Die Arbeit mit Claude Code hat mir gezeigt, dass es wirklich wichtig ist, bei den Prompts spezifisch zu sein. Das Tool ist leistungsstark, aber manchmal over-engineert es Dinge, wenn man nicht klar sagt, was man will. Es ist auch überraschend wortreich - man muss es oft bitten, sich kurz zu fassen.

Insgesamt war es nützlich für diese Aufgabe, besonders beim Verstehen von bestehendem Code und bei der Planung von Implementierungen. Man muss es nur ein bisschen leiten, um die Ausgabe zu bekommen, die man wirklich haben will.

---

## Repository Link

**GitHub Fork:** https://github.com/mehru9219/ise25-26_assignment04
**Commit Hash:** 42b88da

---

## Appendix: Key Implementation Details

### OSM Tag to POS Field Mapping

| OSM Tag | POS Field | Transformation |
|---------|-----------|----------------|
| `name` | name | Direct copy (required) |
| `amenity` | type | Mapped via switch (required) |
| `description` | description | Direct copy or generated |
| `addr:street` | street | Direct copy (optional) |
| `addr:housenumber` | houseNumber | Direct copy (optional) |
| `addr:postcode` | postalCode | Parse to Integer (optional) |
| `addr:city` | city | Direct copy (required) |
| (derived from postcode) | campus | Mapped via postal code switch |

### Postal Code to Campus Mapping

- **69117** → ALTSTADT (Altstadt/Old Town)
- **69115** → BERGHEIM (Bergheim district)
- **69120, 69121** → INF (Informatik campus, Neuenheimer Feld)
- **Unknown** → ALTSTADT (default)

### Error Handling

- **HTTP 404/410** → OsmNodeNotFoundException
- **Missing name/amenity/city** → OsmNodeMissingFieldsException
- **Network errors** → OsmNodeNotFoundException (wrapped)
- **XML parsing errors** → OsmNodeNotFoundException (wrapped)

---

**End of Documentation**
