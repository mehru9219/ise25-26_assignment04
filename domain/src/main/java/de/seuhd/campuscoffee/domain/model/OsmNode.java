package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId The OpenStreetMap node ID.
 * @param name The name of the POI from the "name" tag.
 * @param amenity The amenity type from the "amenity" tag (e.g., "cafe", "bakery", "vending_machine").
 * @param description Optional description from the "description" tag.
 * @param addrStreet Street name from the "addr:street" tag.
 * @param addrHouseNumber House number from the "addr:housenumber" tag.
 * @param addrPostcode Postal code from the "addr:postcode" tag.
 * @param addrCity City name from the "addr:city" tag.
 */
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
) {
}
