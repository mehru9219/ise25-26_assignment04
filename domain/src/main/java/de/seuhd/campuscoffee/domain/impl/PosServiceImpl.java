package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        // TODO: Implement the actual conversion (the response is currently hard-coded).
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Maps OSM tags to POS fields and performs necessary transformations.
     *
     * @param osmNode The OSM node data to convert
     * @return A POS object ready for persistence
     * @throws OsmNodeMissingFieldsException if required fields are missing
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        log.debug("Converting OSM node {} to POS", osmNode.nodeId());

        // Validate required fields
        if (osmNode.name() == null || osmNode.name().isBlank()) {
            log.error("OSM node {} is missing required field: name", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.amenity() == null || osmNode.amenity().isBlank()) {
            log.error("OSM node {} is missing required field: amenity", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.addrCity() == null || osmNode.addrCity().isBlank()) {
            log.error("OSM node {} is missing required field: addr:city", osmNode.nodeId());
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }

        // Map amenity type to PosType
        PosType posType = mapAmenityToPosType(osmNode.amenity());
        log.debug("Mapped amenity '{}' to PosType.{}", osmNode.amenity(), posType);

        // Parse postal code
        Integer postalCode = parsePostalCode(osmNode.addrPostcode(), osmNode.nodeId());

        // Determine campus from postal code
        CampusType campus = determineCampus(postalCode, osmNode.nodeId());
        log.debug("Determined campus {} from postal code {}", campus, postalCode);

        // Build description: use OSM description if available, otherwise generate from amenity
        String description = osmNode.description() != null && !osmNode.description().isBlank()
                ? osmNode.description()
                : generateDescriptionFromAmenity(osmNode.amenity());

        // Build and return the POS object
        return Pos.builder()
                .name(osmNode.name())
                .description(description)
                .type(posType)
                .campus(campus)
                .street(osmNode.addrStreet())
                .houseNumber(osmNode.addrHouseNumber())
                .postalCode(postalCode)
                .city(osmNode.addrCity())
                .build();
    }

    /**
     * Maps OSM amenity tag values to PosType enum.
     *
     * @param amenity The OSM amenity tag value
     * @return The corresponding PosType
     */
    private @NonNull PosType mapAmenityToPosType(@NonNull String amenity) {
        return switch (amenity.toLowerCase()) {
            case "cafe", "coffee_shop" -> PosType.CAFE;
            case "bakery" -> PosType.BAKERY;
            case "vending_machine" -> PosType.VENDING_MACHINE;
            case "cafeteria", "restaurant", "fast_food", "bar", "pub" -> PosType.CAFETERIA;
            default -> {
                log.warn("Unknown amenity type '{}', defaulting to CAFE", amenity);
                yield PosType.CAFE;
            }
        };
    }

    /**
     * Parses postal code string to Integer.
     *
     * @param postcodeStr The postal code string from OSM
     * @param nodeId The node ID for error reporting
     * @return The postal code as Integer, or null if not parseable
     */
    private Integer parsePostalCode(String postcodeStr, Long nodeId) {
        if (postcodeStr == null || postcodeStr.isBlank()) {
            log.warn("OSM node {} has no postal code", nodeId);
            return null;
        }

        try {
            return Integer.parseInt(postcodeStr.trim());
        } catch (NumberFormatException e) {
            log.warn("OSM node {} has invalid postal code '{}', cannot parse to integer", nodeId, postcodeStr);
            return null;
        }
    }

    /**
     * Determines campus based on postal code.
     * Uses Heidelberg postal code mapping:
     * - 69117 → ALTSTADT (old town)
     * - 69115 → BERGHEIM
     * - 69120, 69121 → INF (Informatik campus)
     *
     * @param postalCode The postal code
     * @param nodeId The node ID for logging
     * @return The determined campus, defaults to ALTSTADT if unknown
     */
    private @NonNull CampusType determineCampus(Integer postalCode, Long nodeId) {
        if (postalCode == null) {
            log.warn("OSM node {} has no postal code, defaulting to ALTSTADT", nodeId);
            return CampusType.ALTSTADT;
        }

        return switch (postalCode) {
            case 69117 -> CampusType.ALTSTADT;
            case 69115 -> CampusType.BERGHEIM;
            case 69120, 69121 -> CampusType.INF;
            default -> {
                log.warn("Unknown postal code {} for node {}, defaulting to ALTSTADT", postalCode, nodeId);
                yield CampusType.ALTSTADT;
            }
        };
    }

    /**
     * Generates a description from the amenity type if no explicit description is provided.
     *
     * @param amenity The amenity type
     * @return A generated description
     */
    private @NonNull String generateDescriptionFromAmenity(@NonNull String amenity) {
        return switch (amenity.toLowerCase()) {
            case "cafe", "coffee_shop" -> "Coffee shop";
            case "bakery" -> "Bakery";
            case "vending_machine" -> "Vending machine";
            case "cafeteria" -> "Cafeteria";
            case "restaurant" -> "Restaurant";
            default -> "Point of sale";
        };
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
